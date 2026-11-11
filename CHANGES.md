# Runway Subagents — v1 (NOT pushed, NOT committed)

Base: `origin/main` @ `7d06a9f` (Admin dashboard restyle, on top of Groups `eb0effe`).
These changes are uncommitted working-tree changes in this staging clone only.
(This file replaces the Groups CHANGES.md in this staging clone; the Groups version
still exists untouched on origin/main.)

## What the feature does

Your chat agent gets a new built-in tool called **`spawn_subagent`**. When you ask the
main model to delegate work, it can start a **subagent**: a fresh, separate AI conversation
with its own context that works on one self-contained task and reports back only its final
answer. The main chat never sees the subagent's back-and-forth — just the result, like a
tool result.

- The subagent starts **blank**: it gets a short system prompt plus the task brief, never
  your chat history. The brief has to contain everything it needs.
- If the model starts several subagents in one go, they **run at the same time**
  (in parallel) and their answers come back in order.
- The subagent can use tools too (see safety rules below), and it can run on a **different
  model** on the same provider if the main model asks for one (e.g. main chat on the big
  model, subagents on a smaller/faster one).
- In the chat, a spawn looks like any other tool call: it appears in the existing tool-call
  log UI under the name **Subagent / spawn_subagent**, with its runtime and result.

The tool is only offered when the chat's tools are on (same switch as MCP tools), the model
supports function calling, and it's a normal chat (not Compare mode) — same conditions as
the existing built-in tools.

## How to use it

Just ask in a normal Runway chat, for example:

> "Use two subagents in parallel: one summarises the pros of Firebase, the other the pros
> of Supabase. Then compare their answers and give me a verdict."

Or:

> "Spawn a subagent with the role 'strict code reviewer' to check this function for bugs,
> then fix what it finds." *(paste the function into your message — the subagent can't see
> your chat, so the main model includes what matters in the brief.)*

The model decides the brief, the optional role, and the optional model itself — those are
the tool's three inputs: `task` (required), `role` (optional), `model` (optional, defaults
to the chat's current provider + model).

## Files changed

- **NEW `app/src/main/java/com/example/data/agent/SubagentTool.kt`** (112 lines) — the
  tool's name/description/input schema, its synthetic "server" and "tool" records (same
  pattern as the built-in code runner), argument parsing, the subagent's system prompt,
  and the result-truncation helper. No logic that touches the network or the database.
- **EDITED `app/src/main/java/com/example/ui/viewmodel/OmniStudioViewModel.kt`**
  (+265 / −1 lines) — everything else, all inside the existing `executeModelStream`:
  - 3 coroutine imports (`async`, `withTimeoutOrNull`, `CancellationException`).
  - The `spawn_subagent` tool is added to the tool list next to the built-in `run_code`
    tool, so it reaches the model through the exact same path as every other tool.
  - A new `runSubagent(...)` runner (nested next to the existing provider resolver) that
    runs the subagent headlessly through the same `repository.streamChat` + repository
    tool executors the main loop uses. The main loop itself was **not** refactored.
  - Before the normal sequential tool loop, all `spawn_subagent` calls of a turn are run
    **concurrently** (`async`), and the loop then hands out their results in call order.
  - Each spawn writes one tool-call log row (like every other tool), so it shows in the
    existing tool-call UI.

No new dependencies. No new screens. No database changes.

## Caps and safety rules

- **Turn cap:** a subagent gets at most **8 model turns**, then it is stopped and its last
  message (with a note) is returned.
- **Timeout:** a subagent run is cut off after **5 minutes**.
- **Result cap:** results longer than **6,000 characters** are truncated with a note.
- **Depth cap 1 (hard rule):** a subagent never receives the `spawn_subagent` tool, so
  subagents cannot spawn subagents. There is no setting that changes this.
- **No approval dialogs, ever:** a subagent only inherits tools that would run in the main
  chat *without asking you first*. The check is the exact same `canAutoRun` logic the main
  loop uses (per-tool Always/Ask/Never policy + server approval mode + read-only check),
  evaluated at spawn time. Consequences:
  - `run_code` is **never** given to a subagent (it always asks for confirmation).
  - Write/delete MCP tools are **never** given to a subagent (they always ask).
  - Read-only MCP tools are inherited only if your current settings would auto-run them.
  - Live Info tools (`web_search`, `news_headlines`) are inherited when they're enabled —
    they never ask. A subagent's searches count toward the same per-message search limit.
  - If a tool isn't in the subagent's list, it can't run it — it just gets an error text.
- **Fail-soft:** if a subagent errors, times out, or gets a bad brief, the main chat gets an
  "Error: …" tool result and **carries on**. A broken subagent can never break your chat.
  Pressing Stop cancels running subagents too.
- **Keys stay on the device:** the subagent uses the same provider and API key as your
  chat (resolved the same way, including the OmniRoute fallback). Nothing new is stored,
  nothing leaves the phone except the requests to your own chosen provider — same as a
  normal chat.
- The subagent is told in its system prompt that approval-needing tools are unavailable
  and that it cannot ask questions — it has to work with what it has and say so if it can't.

## Deliberately skipped in v1

- **No saved transcripts.** A subagent's conversation is gone when it finishes; only its
  final answer (and the tool-call log rows, like any tool) remain. No database changes.
- **No subagent dashboard or new UI.** Spawns show as normal tool calls; that's all.
- **No provider failover inside a subagent.** The main loop can fail over to a backup
  provider mid-turn; a subagent just reports the error and the main model decides what to
  do. Keeps the runner small.
- **Subagent token usage is not added to the chat's token/cost totals.** The requests still
  cost whatever your provider charges — the in-app counters just don't include them yet.
- **No settings screen / toggles** for caps — 8 turns and 5 minutes are constants in
  `SubagentTool.kt` if you ever want to change them.
- **No unit test added.** The pure helpers (argument parsing, truncation) are tiny, and the
  Android `org.json` stub makes them awkward to test in plain JVM unit tests; the runner
  itself needs the full app. CI + a phone test are the proof for v1 (see below).
- If one of your own MCP servers ever has a tool literally named `spawn_subagent`, the
  built-in wins and shadows it (same behaviour as the existing `run_code` / `web_search`
  built-in names).

## What CI must prove (nothing compiled locally — no Android SDK here)

This code was written and reviewed by reading, **not compiled**. The GitHub Actions build
is the first real check. Things to watch:

1. **It compiles.** Main risks, in order:
   - The new nested `runSubagent` function in `OmniStudioViewModel.kt` (~210 lines) —
     it captures a lot of the surrounding function's state (`resolveProviderRuntime`,
     `enabledToolTriples`, `assistantMsgId`, UI state). Reviewed line-by-line against the
     surrounding code and the approval-policy source, but only the compiler can confirm.
   - `SubagentTool.kt`'s `org.json` parsing (standard Android API, same as used elsewhere
     in the ViewModel).
2. **On-phone smoke test** (only you can do this):
   - In a chat with tools on and a function-calling model, send the Firebase/Supabase
     prompt above → you should see the status line "Running 2 subagent(s)…", then the
     comparison answer, and two **Subagent / spawn_subagent** entries in the tool log.
   - Ask the model to spawn a subagent that runs code → the subagent must report it can't
     (no confirmation sheet should ever appear for a subagent).
   - Kill the network mid-run → the chat should continue with an "Error: subagent failed"
     result instead of dying.
