# Runway — Round 3 (NOT pushed, NOT committed)

Base: `origin/main` @ `46b8f87` (Subagents round 2: background subagents +
unlimited web search). Rounds 1–2 are live on main. This round fixes the two
failure modes Farhan's on-phone subagent tests exposed:

1. **Free models print their tool calls as text** instead of returning proper
   structured calls, so nothing executes.
2. **Subagent runs die instantly on a provider 429** ("Too Many Requests") —
   the free routes rate-limit often and the runner had no retry.

These changes are uncommitted working-tree changes in this staging clone only.

## Fix 1 — Text-format tool calls now execute (main chat AND subagents)

**What changed:** when a model's reply finishes, Runway now scans the reply
text for `<tool_call>...</tool_call>` blocks and recovers them as real tool
calls. Both Qwen dialects the free LiteRouter routes slip into are understood:

- **JSON:** `<tool_call> {"name": "search_code", "arguments": {"query": "..."}} </tool_call>`
  (`arguments` may be an object or a JSON string — both are normalised.)
- **XML:** `<tool_call> <function=web_search> <parameter=query> some text </parameter> </function> </tool_call>`
  (parameter values stay strings, except clean `true`/`false` become booleans
  and clean integers/decimals become numbers.)

**Where it lives and why:** both the main chat loop and the subagent core
assemble their (content, toolCalls) pair in `OmniStudioViewModel` from
`repository.streamChat` events — the repository is a pass-through and cannot
rewrite text the UI has already streamed. So the parser is **one small pure
function** — new file `TextToolCallParser.kt` (no Android dependencies) —
called from exactly those two assembly points, never copied twice:

- **Main chat:** on each successful model response, before the attempt's text
  is committed as final (a 6-line addition to `FailoverStreamSession`,
  `replaceCurrentAttemptText`, keeps the on-screen message in sync).
- **Subagent core:** on each subagent turn, before the "no calls = final
  answer" decision — this is what turns the XML block that used to *become*
  the subagent's answer back into a real search.

**How recovered calls rejoin the normal path:** they are appended after any
structured calls the same response carried (both kinds are handled — decision:
extract text blocks even when structured calls exist) and then flow through
the completely unchanged execution machinery. Main-chat approvals still apply
(a recovered `run_code` still asks first); the subagent inheritance filter and
its execution-time tool lookup still apply (a recovered call to a tool the
subagent doesn't have gets the usual error result, not a run).

**Guardrails:**

- A block is honoured **only if its name matches a tool offered for that
  request**. Unknown names, malformed JSON/XML and unclosed blocks are left in
  the reply as plain text — never executed, never crash.
- At most **8** recovered calls per response.
- Honoured blocks are **removed from the visible reply**; the prose around
  them stays.
- **Streaming caveat:** while a reply is still streaming, the raw
  `<tool_call>` markup can briefly show; it is cleaned the moment the turn
  finalises. No mid-stream scrubbing was built.

## Fix 2 — Subagent runs retry on 429 instead of dying

**What changed:** inside the subagent runner only, if the model request fails
with a rate-limit error, the **same request is retried in place**: wait ~10 s,
retry; wait ~20 s, retry once more (3 attempts total), then give up with the
usual error result.

- **Classification reused, not reinvented:** the stream's error already carries
  the category assigned by the existing `ErrorNormalizer` (the build-#82 work:
  HTTP 429 *or* rate-limit wording in the body → `ErrorCategory.RATE_LIMIT`).
  The retry checks exactly that category; every other error fails immediately,
  as today.
- Only the model-request step retries — tool executions never do.
- The run's **5-minute timeout still bounds everything**; the waits eat into
  it, and if it fires, the timeout result wins. **Stop / background Cancel
  interrupt a pending wait immediately** (the delay is a coroutine delay in
  the run's own scope, not a sleep).
- Nothing new shows in the UI — the spawn simply takes longer while it heals.
- **Main chat is deliberately out of scope** (it already has its own
  context-overflow retry, and the user is watching there and can resend).

## Files changed

- **NEW `app/src/main/java/com/example/data/provider/TextToolCallParser.kt`** —
  the pure text tool-call parser (both dialects, offered-name validation,
  8-call cap) — Fix 1.
- **EDITED `app/src/main/java/com/example/ui/viewmodel/OmniStudioViewModel.kt`** —
  main loop: extraction on the successful attempt before its text is committed,
  recovered calls appended to the turn's calls; subagent core: the model
  request wrapped in the 429 retry loop, and extraction producing the turn's
  effective text/calls — Fixes 1 + 2.
- **EDITED `app/src/main/java/com/example/data/provider/FailoverResolver.kt`** —
  `FailoverStreamSession.replaceCurrentAttemptText`, so the visible streaming
  message can be swapped for the cleaned text at finalisation — Fix 1.
- **NEW `app/src/test/java/com/example/TextToolCallParserTest.kt`** — JVM unit
  tests (Robolectric runner, like the existing tests, so `org.json` behaves as
  on-device): JSON dialect, JSON-string arguments, XML dialect with string /
  integer / decimal / boolean parameters, unknown tool name left as text,
  malformed and unclosed blocks left as text, multiple calls in one response
  with prose preserved, no-tools-offered no-op, and the 8-call cap.

No new dependencies. No new screens. No database changes.

## Deliberately skipped this round

- **No mid-stream scrubbing** of `<tool_call>` markup (see the streaming
  caveat above) — cleaning happens at turn finalisation.
- **No 429 retry in the main chat loop** — subagent runner only, per above.
- **Autopilot engine untouched** — it consumes a provider adapter directly,
  not `repository.streamChat`; the parser is reusable there later if wanted.
- The parser does not try other text formats (e.g. `<function_calls>` with
  `<invoke>` blocks) — only the two Qwen dialects actually observed on the
  phone.

## What CI must prove (nothing compiled locally — no Android SDK here)

CI runs `gradle assembleDebug` only — it proves the main code compiles; the
new unit tests are compiled/run only by `testDebugUnitTest` (e.g. Android
Studio), which CI does not run. Written and reviewed by reading, **not
compiled**. Compile risks, in order:

1. **The ViewModel subagent hunks** — the collect wrapped in the retry loop
   (the per-attempt reset of the text/calls/error accumulators) and the
   effective-text/effective-calls swap in the turn's else-branch.
2. **The main-loop hunk** — extraction references `activeModelDescriptor`,
   `toolDefinitions`, `turnText`, `turnToolCalls`, `initialAssistantMsg` and
   `failoverSession`, all in scope at the success branch, but only the
   compiler can confirm.
3. **`TextToolCallParser.kt`** — plain Kotlin + `org.json`; lowest risk.

## On-phone smoke test

1. **Subagent headlines (the original failure):** fresh chat, tools on,
   qwen3-coder:free → *"Use a subagent to search the web for today's top news
   headlines and summarise them in 3 lines."* If the route emits text-format
   calls, they should now **execute**: real headlines in the answer, and the
   subagent's own Live Info entries in the tool log — no raw `<tool_call>`
   text as the "answer".
2. **429 self-heal:** run subagents on a free route a few times; a run that
   hits a 429 should now pause (~10–30 s) and recover instead of instantly
   returning "Error: subagent failed — Too Many Requests (429)…".
3. **Regression:** an ordinary structured-call chat (the Firebase/Supabase
   parallel prompt, a normal GitHub search) behaves exactly as before.
4. **Fake block stays text:** ask the model something that makes it print an
   example `<tool_call>` block naming a tool that doesn't exist (e.g.
   `delete_everything`) — the block must stay visible as plain text and
   nothing may run.
