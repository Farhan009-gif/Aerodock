# Runway Subagents — Round 2 (NOT pushed, NOT committed)

Base: `origin/main` @ `70b529e` (Subagents v1, on Groups + Admin dashboard).
Round 1 shipped `spawn_subagent` (foreground only) and is live on main. This round
adds two things Farhan asked for after testing v1 on his phone: **unlimited web
searches** and **background subagents** (chat with the main model while a subagent
works). These changes are uncommitted working-tree changes in this staging clone only.

## Change A — Web search limit removed (unlimited)

**What changed:** the "Web search limit reached (N searches per message)" rejection
is gone. The main chat and subagents can search as many times as a turn needs.

**Where the cap lived:** one place — `LiveInfoToolsService.executeWebSearch()`
checked a per-message counter against a limit (default 3, settable 1–20 in Settings;
Farhan had it at 5, which is the "5 searches per message" he hit). Both the main
loop and subagents reach searches through that single function, so removing the
check there removes it everywhere. The counter itself still counts (stats only,
never rejects).

**Also tidied up:**
- The now-pointless limit parameter was removed from `executeWebSearch`, from
  `repository.executeLiveInfoToolAndLog`, and from both call sites in the ViewModel.
- The **"Search limit per message" chips were removed from Settings > Live info** —
  a control that does nothing would just be confusing. The stored setting, its
  state field and its setter remain in the code but are now inert (kept to keep
  this change small; they can be deleted in a later tidy-up).
- The unit test that asserted the old blocking behaviour now asserts the opposite:
  a 4th search in the same message runs fine.

**What did NOT change:** the ~3-second pause between two actual searches stays
(it protects the free DuckDuckGo endpoint from hammering — it's a pacing delay,
not a count cap), and the search cache stays. Accepted risk, as ordered: a
subagent can now search a lot inside one run — but runs are still bounded by the
8-turn cap and the 5-minute timeout.

## Change B — Background subagents

**What changed:** `spawn_subagent` gains a fourth, optional input: **`background`**
(true/false, default false). Foreground behaviour is exactly round 1.

With `background=true`:
- The tool call **returns immediately** — the parent reply finishes normally with
  a tool result like "Background subagent started (job …). Its result will be
  posted into this conversation when it finishes. Do not wait for it; continue
  the conversation." You keep chatting with the main model.
- When the subagent finishes, its answer appears in the **same conversation** as
  a new assistant message: **"Subagent result — \<task\>:"** followed by the
  answer (or **"Subagent failed — \<task\>:"** with the error). If you're looking
  at that chat it pops in live; if you're elsewhere, it's there when you return.
- **At most 3 background subagents can run at once** (app-wide). A 4th background
  spawn gets an error result telling the model the limit is reached; foreground
  spawns are unaffected.
- While any background subagent for the open chat is running, a slim row sits
  directly above the message box: **"◌ N subagent(s) working in background"**
  with a **Cancel** button. Cancel stops those jobs and they post **nothing**
  (no "cancelled" message — chosen so a cancelled job leaves no noise).
- The spawn still writes its tool-call log row (server "Subagent"), and the
  subagent's own tool calls log exactly like round 1. Same caps as foreground:
  8 turns, 5 minutes, 6,000-character result.

**How to use it** — just ask, for example:

> "Start a background subagent to research the best free TTS APIs and keep
> chatting with me meanwhile."

The model sets `background=true` itself; the tool's description tells it to do
so for long tasks or when you may want to keep chatting.

**How it works (short version):** at spawn time the app snapshots everything the
run needs (provider + key + model, the filtered tool list, chat settings, the
conversation id) into a "plan", then launches the run on that snapshot in the
background. That's why switching chats or models mid-run can't disturb it.
Foreground and background share the same execution core — the round-1 runner was
split into *build plan* (spawn time) + *execute plan* (any time), not duplicated.

**Honest limit:** background jobs live in the app process. **If the app is
killed (swiped away / phone restart), running jobs die with it and no result is
posted.** No foreground service or WorkManager in this round — that's the
deliberate v1-of-background trade-off.

## Files changed

- **EDITED `app/src/main/java/com/example/data/mcp/LiveInfoToolsService.kt`** —
  the search-count rejection block and the limit parameter deleted (Change A).
- **EDITED `app/src/main/java/com/example/data/repository/OmniRepository.kt`** —
  `executeLiveInfoToolAndLog` no longer takes/forwards a search limit (A).
- **EDITED `app/src/main/java/com/example/ui/screens/SettingsAndPhasePreviewScreens.kt`** —
  the dead "Search limit per message" chips row removed (A).
- **EDITED `app/src/test/java/com/example/LiveInfoToolsTest.kt`** — the rate-limit
  test updated: the 4th same-message search now succeeds (A).
- **EDITED `app/src/main/java/com/example/data/agent/SubagentTool.kt`** — new
  `background` input in the schema + one description line, `background` field in
  the parsed request, `MAX_BACKGROUND_JOBS = 3`, and a `taskLabel()` helper for
  the short task name used in the row and the posted result (B).
- **EDITED `app/src/main/java/com/example/ui/viewmodel/OmniStudioViewModel.kt`** —
  the bulk of Change B: a `BackgroundSubagentJob` registry in UI state (id, task
  label, conversation, status) + job handles; the round-1 runner split into
  `buildSubagentPlan` (spawn-time snapshot) and `executeSubagentPlan` (shared
  core), with `runSubagent` (foreground) and `startBackgroundSubagent`
  (register → launch → post result message on completion) on top; the tool-call
  pre-pass now routes background calls to start-and-return and foreground calls
  to the round-1 concurrent block; a `cancelBackgroundSubagentsForConversation`
  function for the row's Cancel button. (Also loses the two search-limit call
  arguments, Change A.)
- **EDITED `app/src/main/java/com/example/ui/screens/ChatScreen.kt`** — the
  background-jobs row above the composer (only when this conversation has running
  jobs) + the `onCancelBackgroundSubagents` callback plumbed through
  `ChatWorkspaceScreen` → `MinimalChatCanvas` (B).
- **EDITED `app/src/main/java/com/example/MainActivity.kt`** — one line wiring
  that callback to the ViewModel (B).

No new dependencies. No new screens. No database changes (the posted result is an
ordinary message row).

## Caps and safety (unchanged from round 1, still apply to background runs)

- Depth cap 1: subagents never get `spawn_subagent` or `run_code`.
- Subagents only inherit tools that would auto-run without asking you; no
  approval sheet can ever appear from a subagent (background or foreground).
- 8 turns / 5 minutes / 6,000 characters per run. Fail-soft: errors post as
  "Subagent failed — …" (background) or return as an error tool result
  (foreground); the main chat is never broken by a subagent.
- The turn's Stop button behaves as before — it stops the current reply, not
  background jobs. The row's Cancel is the background stop.

## Deliberately skipped this round

- **No survival across app kill** (see the honest limit above) — no foreground
  service, no WorkManager, no persisted job queue.
- **No job history screen** — finished jobs leave only their posted message;
  the in-app job list keeps at most the 10 most recent finished entries.
- **No per-job cancel** — the row's Cancel stops all background jobs of the
  open conversation at once.
- The inert search-limit setting plumbing (stored value, state field, setter)
  was left in place rather than ripped out — behaviour is unlimited either way.

## What CI must prove (nothing compiled locally — no Android SDK here)

Written and reviewed by reading, **not compiled**. Watch:

1. **It compiles.** Main risks, in order:
   - The ViewModel runner split (`buildSubagentPlan` / `executeSubagentPlan`):
     the execution body was re-pointed from live locals to the plan snapshot
     field by field and re-read in full, but only the compiler can confirm every
     reference landed.
   - The local `SubagentRunPlan` data class inside `executeModelStream` and the
     background launch's message insertion (`dao.upsertMessage` +
     `syncMessageToRunwayTree`, mirroring how the turn's own assistant message
     is inserted).
   - The ChatScreen row: it reuses the tool-status banner's exact styling and
     symbols; the Cancel tap uses the smart-cast conversation inside its
     null-check block.
2. **On-phone smoke test** (only Farhan can do this):
   - Ask for a background research subagent → the reply should finish at once,
     the "◌ 1 subagent(s) working in background" row should show above the
     composer, you can send more messages, and the result should land in the
     chat as "Subagent result — …" when done.
   - Start one, tap **Cancel** → the row disappears and **no** result message
     ever appears.
   - Start background subagents in two different chats → each result lands in
     its own chat; only 3 can run at once.
   - Search test: ask for something needing 6+ web searches in one message →
     no "limit reached" text anywhere (allow for the 3-second pacing between
     searches).
   - Foreground regression: the round-1 Firebase/Supabase parallel prompt
     should behave exactly as before.
