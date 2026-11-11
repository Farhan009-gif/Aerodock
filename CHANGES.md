# Runway — Round 7 (NOT pushed, NOT committed)

Base: `origin/main` @ `323a0ba` (Round 6: message queueing while generating +
paragraph Enter key, including the round-5 build fix). Rounds 1–6 are live on
main. This round comes straight from Farhan's on-phone test of the
collect-then-fact-check prompt (screenshots, 11 Nov 16:09):

1. **The model doesn't know what day it is.** Asked "what's today's date", it
   answered: "I don't have access to real-time information, so I can't tell
   you today's specific date." Every claim it produced in that session was
   dated March 2024 — it was reasoning from training data with no idea that
   "today" is 11 November 2026.
2. **When a subagent failed, the main model made the answer up.** The first
   research subagent hit its turn limit; instead of saying so, the main model
   staged a fake "Fact-Checking Exercise" with invented claims and
   "fact-checked" its own inventions.
3. **Research subagents die at 8 turns.** The collecting subagent spent all 8
   turns searching and never got to write its answer.

These changes are uncommitted working-tree changes in this staging clone only.

## Change 1 — The live clock in every request

**What changed:** every chat request and every subagent run now carries one
extra line in its system context, computed **fresh at the moment the request
is built** (never cached at app start, never stored in the conversation,
never shown in the UI):

> `Current date and time: Wednesday, 11 November 2026, 16:12 IST (Asia/Kolkata).`

The date format is exactly `EEEE, d MMMM yyyy, HH:mm zzz` (weekday and month
spelled out, English names so free models parse them reliably) in the
**device's own timezone**, followed by the timezone id in brackets. On a phone
in another timezone it prints that zone's abbreviation and id instead.

**How it works:**

- One small helper, `liveContextBlock(liveToolsOffered)`, in
  `OmniStudioViewModel.kt`, builds the clock line plus the Change-2
  instruction block. Both call sites use it — the formatting logic exists
  exactly once.
- **Main chat:** `executeModelStream` appends the block to
  `effectiveSystemPrompt` (after the conversation's own system prompt, the
  skills block, and — in AGENT_ONLY mode — the dispatcher instruction). That
  one value feeds the context trimmer and every request attempt, including
  failover retries. Compare turns go through the same assembly, so they get
  the clock too (but not the live-tools sentence — see Change 2).
- **Subagents:** `executeSubagentPlan` appends the same block to the
  subagent's system prompt, computed when the run executes. Foreground spawns,
  background spawns and **chain steps all run through that one function**, so
  every step of a chain gets a fresh clock with no extra code.
- The block is request-time context only, exactly like the round-4 dispatcher
  instruction: it is not persisted into stored messages and does not appear
  anywhere on screen.

## Change 2 — The standing truth instruction

**What changed:** right under the clock line, the same system context now
carries a short instruction block (63 words total). Verbatim:

> If a tool call or a subagent fails, errors, or hits its turn limit, say so
> plainly in your reply. Never invent results, claims, sources, dates, or a
> demonstration exercise in place of real output.

That first part — **anti-fabrication — is unconditional**: it is in every
chat request (all work modes, Compare included) and every subagent run.

Only when Live Info tools are actually offered in that request, one more
sentence follows:

> You have live web_search and news_headlines tools. For anything
> time-sensitive or current, use them instead of answering from memory, and
> never claim you lack real-time or internet access.

**The condition matters:** claiming tools that don't exist would be its own
lie, so the sentence is included only when the offered tool set really
contains a Live Info tool — for the main chat, when the resolved Live Info
tool list for this request is non-empty (both Live Info toggles off, a model
without function calling, or a Compare turn → the sentence is absent); for a
subagent, when the tools that run was actually offered include `web_search`
or `news_headlines`.

## Change 3 — Subagent turn cap 8 → 12

**What changed:** `SubagentTool.MAX_TURNS` is now **12** (was 8). Everything
that reports the cap already read the constant — the "[Note: subagent stopped
at the 12-turn limit…]" and "Error: subagent used all 12 turns…" strings are
now correct automatically. The two places that had the number hard-coded as
text (the `spawn_subagent` tool description the model reads, and the tool's
status string) now take it from the constant too, so they can't drift again.

**Unchanged:** the **300-second (5-minute) timeout** stays exactly as it was —
a run gets more turns, not more wall-clock time. Chains inherit the new cap
automatically because every chain step executes through
`executeSubagentPlan`, the same loop the cap lives in (verified: foreground
spawn, background spawn and chain steps are its only three callers).

## Files changed

- **EDITED `app/src/main/java/com/example/ui/viewmodel/OmniStudioViewModel.kt`** —
  the `NO_FABRICATION_INSTRUCTION` and `LIVE_TOOLS_INSTRUCTION` constants and
  the `liveContextBlock()` helper; `liveInfoTriples` is now resolved a few
  lines earlier (same code, same result — the system prompt needs to know
  whether Live Info is offered) and `effectiveSystemPrompt` appends the block;
  `executeSubagentPlan` appends the same block to the subagent's system
  prompt (Changes 1 + 2).
- **EDITED `app/src/main/java/com/example/data/agent/SubagentTool.kt`** —
  `MAX_TURNS` 8 → 12; the tool description and status string now derive the
  number from the constant (Change 3).

No new dependencies. No UI changes. No database changes, nothing persisted.

## One deviation from the spec, on purpose

The spec said to format the clock with `java.time`. The code uses
`java.util.TimeZone` + `java.text.SimpleDateFormat` instead. Reason: this app
has **minSdk 24 and no core-library desugaring**, and `java.time` only exists
on Android API 26+ — it would crash on API 24/25 devices, and there is not a
single `java.time` use anywhere in the codebase today, while
`SimpleDateFormat` is the established date pattern (five other files use it).
The output is identical: device timezone, English weekday/month names, same
line format.

## Deliberately skipped this round

- **A `get_current_datetime` tool** — the model would have to *choose* to
  call it, which is exactly the failure in the screenshots (it never occurred
  to the model to check). Injection is always there.
- **Dedicated live-data tools** (weather, scores, prices) — that was option B
  of the real-time request; this round is the clock, the truth instruction
  and the cap only.
- **Any UI trace of the clock** — no chip, no banner, nothing stored. The
  model knows the date; the screen doesn't need to say it.
- **Raising the 300-second timeout** — turns went up, wall-clock didn't.

## What CI must prove (nothing compiled locally — no Android SDK here)

Written and reviewed by reading, **not compiled**. Brace/paren balance of
both edited files is exact; the diff was read hunk by hunk; a repo-wide sweep
confirms no "8 turns" text survives anywhere in the subagent feature. Watch,
in order:

1. **OmniStudioViewModel.kt** — the moved `liveInfoTriples` resolution (it
   must remain the *only* declaration, and everything it used to feed sits
   below its new home) and the parenthesised prompt concatenation; then the
   `liveContextBlock()` helper and its two call sites.
2. **SubagentTool.kt** — the `$MAX_TURNS` string template inside the
   `private const val` tool description (a const template referencing another
   const in the same object — legal constant folding, but if CI disagrees,
   the one-line fallback is the literal text "12 turns").
3. **executeSubagentPlan's prompt append** — plain string concatenation
   (lowest risk).

**On-phone smoke test** (only Farhan can do this):

1. Ask any model **"What's today's date?"** → it answers **Wednesday 11
   November 2026** (the actual current date) directly — no searching, no
   "I don't have access to real-time information" disclaimer.
2. Ask for **today's top news** → it uses web search / headlines and frames
   the results as today's, with today's date attached to the claims.
3. **Agent only:** the two-step collect-then-fact-check prompt ("Step 1:
   spawn a subagent to collect today's top world-affairs claims, with
   sources. Step 2: …fact-check those exact claims…") → the research
   subagent now finishes **inside 12 turns** and returns real, sourced,
   current claims — or honestly reports that it failed. No March 2024, no
   staged "exercise".
4. Turn **Live Info OFF** in Settings, then ask about today's news → the
   model must **not** claim it has live tools (the live-tools sentence is
   absent from its context); it should answer from memory and say so, dated
   against the clock line it still has.
