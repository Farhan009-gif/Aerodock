# Runway — Round 5 (NOT pushed, NOT committed)

Base: `origin/main` @ `a80b8e8` (Round 4: work mode — Auto / Agent only /
Model only). Rounds 1–4 are live on main. This round adds the thing Farhan
asked for after his X → Y fact-check test on the phone:

**The problem:** a background subagent's result is just a message in the chat.
Nothing acts on it. So a two-step plan — subagent X gathers claims, subagent Y
fact-checks them — stalls after X unless the main model orchestrates foreground
spawns itself (and the free models often refuse, narrate instead of calling,
or pick background and stop). Farhan chose the app-run fix: a **chain tool**
that runs the steps itself, in order, feeding each result into the next step.

These changes are uncommitted working-tree changes in this staging clone only.

## What changed — `spawn_subagent_chain`

**A new built-in tool, `spawn_subagent_chain`**, registered exactly like
`spawn_subagent` (same synthetic "Subagent" server, same gating: tools on,
function-calling model, not Compare). Work mode treats it like the spawn tool:
offered in **Auto** and **Agent only**, absent in **Model only**.

**Its one input is `steps`** — an array of 1–4 step objects, each with a
required `task` and an optional `role`. The tool's description teaches the
model: use it when a later step needs an earlier step's output; write every
step's task to stand on its own except for referring to "the previous step's
result"; 2–4 steps.

**Chains always run in the background.** There is no `background` option — the
call returns immediately with *"Background subagent chain started (job …, N
steps). Each step's result will be posted into this conversation as it
finishes. Do not wait for it; continue the conversation."* The user keeps
chatting while the pipeline runs.

**Validation happens before anything starts:** fewer than 1 or more than 4
steps, or any step with a blank task, returns an error tool result explaining
the limit (*"spawn_subagent_chain needs 1–4 steps, each with a non-blank
'task' brief. Nothing was started."*) and no job is created. A chain also
counts as **one** job toward the existing 3-background-jobs cap; if 3 jobs are
already running, the call returns the same limit error a background spawn
returns.

## How a chain runs

- **One snapshot, taken once.** At chain start the app builds the usual
  subagent run plan (provider + key + model, inherited tool list, chat
  settings, conversation) exactly as a single spawn does. Steps never re-read
  live state, so switching chats or models mid-chain can't disturb it.
- **Steps run sequentially through the same execution core** a normal spawn
  uses — no second loop. Each step's plan is a copy of the snapshot carrying
  that step's brief: step 1 gets its task as written; step 2 onwards gets its
  task plus a divider and the previous step's result under a **"Result of the
  previous step:"** heading. The previous result is the same text that was
  posted to the chat, already capped at 6,000 characters by the existing
  truncation.
- **Each step keeps its own caps and rules:** 8 turns, 5 minutes, the 429
  retry, the text-tool-call parser, and the same inherited tools as a normal
  spawn. Steps can **never** receive `spawn_subagent` or
  `spawn_subagent_chain` — the depth-cap filter now excludes both names.
- **Each step posts its own message** into the origin conversation, through
  the same insertion path background results use:
  *"Subagent result — Step 2 of 3 · \<step task\>:"* followed by the answer.
  Each step also writes its own tool-call log row (server "Subagent", tool
  name `spawn_subagent_chain`), like a normal spawn.
- **Stop on failure:** if a step's result is an error, it posts as
  *"Subagent failed — Step k of N · …"* and the chain **stops** — later steps
  never run, so nothing builds on a failed input. (A step that hits the
  8-turn cap returns its last message, as a normal spawn does; that is not
  an error and the chain continues.)
- **One job in the registry**, labelled **"Chain: \<first step's task\>"**.
  The composer row counts it once, like any background job. **Cancel works
  as before:** tapping the row's Cancel stops the running step and later
  steps never start; results already posted stay in the chat.

**How to use it** — just ask, for example:

> "Use a subagent chain: step 1 collect today's top world-affairs claims
> with sources; step 2 fact-check the claims from step 1 and flag anything
> wrong."

The model writes both briefs up front in one `spawn_subagent_chain` call;
the app carries step 1's result into step 2's brief by itself. No second
message from you is needed.

## Files changed

- **EDITED `app/src/main/java/com/example/data/agent/SubagentTool.kt`** —
  the chain tool's identity: `SPAWN_SUBAGENT_CHAIN_TOOL_NAME`,
  `MAX_CHAIN_STEPS = 4`, description + input schema, entity/definition
  builders mirroring the spawn ones, `SubagentChainStep`, and
  `parseChainSteps` (malformed input parses to an empty list; the caller
  rejects it with the limit error).
- **EDITED `app/src/main/java/com/example/ui/viewmodel/OmniStudioViewModel.kt`** —
  everything else:
  - the chain triple registered beside the spawn triple in tool assembly;
  - the round-4 work-mode filter now treats the chain like the spawn tool
    (kept in Agent only, removed in Model only);
  - the subagent inheritance (depth-cap) filter now drops
    `spawn_subagent_chain` as well as `spawn_subagent`;
  - `SubagentRunPlan` gained a `logToolName` field (default
    `spawn_subagent`) so each chain step logs its row under the chain tool's
    name;
  - two helpers extracted from `startBackgroundSubagent` —
    `registerBackgroundSubagentJob` and `postBackgroundResultMessage` —
    now shared by single spawns and chains (single-spawn behaviour
    unchanged);
  - new `startBackgroundChain`: validate → background-cap check → one
    plan snapshot → one registered job → sequential step loop
    (copy plan per step, staple previous result, post per step,
    stop on failure/cancel);
  - the tool-call pre-pass routes chain calls to `startBackgroundChain`
    and hands the loop the immediate "started" result, like background
    spawns.

No new dependencies. No new screens. No database changes (step results are
ordinary message rows; step logs are ordinary tool-call log rows).

## Deliberately skipped this round

- **No foreground chains.** A 4-step chain could block a turn for ~20
  minutes. If an in-turn sequence is needed today, the model can still
  orchestrate foreground `spawn_subagent` calls itself.
- **No conditional steps** ("run step 3 only if step 2 says X"), **no
  parallel branches**, **no editing a running chain** — a chain is a fixed
  list of briefs, executed in order.
- **No per-step model choice** — every step uses the chat's current model,
  like a background spawn without a `model` argument.
- The Agent-only dispatcher instruction still names `spawn_subagent`; the
  chain tool is offered alongside it in that mode and its own description
  explains when to use it.

## What CI must prove (nothing compiled locally — no Android SDK here)

Written and reviewed by reading, **not compiled**. The full diff was read
hunk by hunk; brace balance matches HEAD in both files; greps confirm the
chain name is handled at every point the spawn name is (assembly, mode
filter, inheritance filter, pre-pass). Compile risks, in order:

1. **`startBackgroundChain`** — the largest new block: `basePlan.copy(...)`
   on the local `SubagentRunPlan` data class, the `steps.size !in 1..maxSteps`
   check, and the fully-qualified `org.json.JSONObject` for per-step log
   arguments (the file already uses that fully-qualified form elsewhere).
2. **The helper extraction** — `startBackgroundSubagent` now calls
   `registerBackgroundSubagentJob` / `postBackgroundResultMessage`; the moved
   code is verbatim, but only the compiler confirms nothing was dropped.
3. **`SubagentRunPlan.logToolName`** — a defaulted field added at the end of
   the data class; the one construction site (in `buildSubagentPlan`) uses
   named arguments, so it should be unaffected.

## On-phone smoke test (only Farhan can do this)

1. **The X → Y chain:** in a fresh chat (Auto or Agent only), send:
   *"Use a subagent chain: step 1 collect today's top world-affairs claims
   with sources; step 2 fact-check the claims from step 1 and flag anything
   wrong."* Pass = ONE chain starts (one background row entry), **"Subagent
   result — Step 1 of 2 · …"** posts with the claims, then **"Subagent
   result — Step 2 of 2 · …"** posts referencing those same claims — with
   no further message from you.
2. **Cancel mid-chain:** start the same chain, tap **Cancel** on the
   background row after step 1 posts. Pass = step 2 never posts; step 1's
   result stays in the chat.
3. **Model only:** switch the work-mode chip to Model only and ask for the
   same chain. Pass = no chain starts — the chain tool is not offered (the
   model works itself or says it can't).
4. **Too many steps:** ask for a chain of 5 steps. Pass = an error result
   saying the limit is 4 steps, and nothing starts (no background row, no
   posted results).
