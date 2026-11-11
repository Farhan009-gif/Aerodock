# Runway — Round 4 (NOT pushed, NOT committed)

Base: `origin/main` @ `7b7369d` (Round 3: text tool-call parser + subagent 429
retry). Rounds 1–3 are live on main. This round adds the control Farhan asked
for after testing round 3 on his phone:

**The problem:** in Auto behaviour, asked to research something, the main model
did the research *itself* (4 Live Info searches) **and** started a background
subagent for the same research — two overlapping answers, paid for twice. He
asked to be able to say: only the agent, only the model, or both when wanted.

These changes are uncommitted working-tree changes in this staging clone only.

## What changed — Work mode: Auto / Agent only / Model only

**A new app-wide setting with three values:**

- **Auto** (default) — exactly today's behaviour. The model is offered every
  tool including `spawn_subagent`, and decides itself: work, delegate, or both.
- **Agent only** — the main model is offered **only** `spawn_subagent`: no MCP
  tools, no Live Info, no `run_code`, so it physically cannot do tool work
  itself. A short dispatcher instruction is also appended to that turn's system
  prompt ("You are a dispatcher, not the worker… delegate with spawn_subagent,
  background=true unless the result is needed inside this reply, then relay or
  summarise… Answer directly only when no tools are needed."). One task, one
  worker, one answer — no more duplicate research.
- **Model only** — `spawn_subagent` is simply not offered. The model does
  everything itself with the normal tools; subagents can never start.

**Where it is stored:** the same pattern as the Live Info toggles — a DataStore
string key (`runway_work_mode`) in `TinkSecretVault` with a `workModeFlow` and
`setWorkMode`, collected into a new `workMode` field on `OmniUiState`, and set
through `OmniStudioViewModel.setWorkMode` (which also shows a confirmation
toast). No database change, no per-conversation column — it is one app-level
switch, and it survives leaving the chat and restarting the app.

**How it is enforced:** in `executeModelStream`, at the exact point where the
turn's tool list is assembled. The full list is computed as today
(`allToolTriples`), then filtered by mode into the offered list — and because
`toolDefinitions`, the tool lookup, the round-3 text-call parser's offered
names, and the spawn pre-pass all derive from that one offered list, the mode
holds on every path (a text-printed `spawn_subagent` call in Model only mode
is not recovered either; it stays text).

**The one subtlety, handled:** subagents inherit their tools from the *full*
list (`allToolTriples`), not from the mode-filtered offer list — the existing
inheritance filter (no `spawn_subagent`, no `run_code`, approval-gated tools
dropped) is unchanged and now reads the full set. Without this, Agent only
would starve its own subagents: the subagent is the one who needs the real
tools. In Model only this changes nothing observable — no spawn can occur, so
the inheritance list is never used.

**Edge cases:** Compare mode still sends no tools at all in every work mode.
If tools are globally off for a conversation (`mcpToolsEnabled` false) in
Agent only mode, the model simply has no tools — nothing is force-spawned; the
dispatcher instruction still applies to whatever it answers. Background jobs
(round 2) and the text-call parser (round 3) are otherwise untouched.

## The UI — one chip, no new screens

- The chat header's quiet status line ("N skills · M tools ready", under the
  model pill) is now a centred row with a small chip beside the text showing
  the current mode: **Auto**, **Agent only** or **Model only**, with a ▾.
  (The status text keeps its old tap behaviour — it still opens the skills &
  tools sheet.)
- Tapping the chip opens a "Work mode" bottom sheet — the existing
  `MinimalSheet` + `MinimalToolRow` pattern, same as the Tools sheet — with
  the three modes, a one-line description each, and the current one
  highlighted. Tapping a row sets and persists the mode and closes the sheet.

## Files changed

- **EDITED `app/src/main/java/com/example/data/security/TinkSecretVault.kt`** —
  `runway_work_mode` DataStore key, `workModeFlow`, `setWorkMode` (mirrors the
  Live Info settings exactly).
- **EDITED `app/src/main/java/com/example/ui/viewmodel/OmniStudioViewModel.kt`** —
  the `WorkMode` enum + the AGENT_ONLY dispatcher instruction constant, the
  `workMode` UiState field, the vault-flow collector, `setWorkMode`, and the
  enforcement in `executeModelStream` (system-prompt append + offered-list
  filter + the inheritance source switch to `allToolTriples`).
- **EDITED `app/src/main/java/com/example/ui/screens/ChatScreen.kt`** — the
  header chip beside the status line, the "Work mode" `MinimalSheet` with
  three rows, and the `onSetWorkMode` / `onOpenWorkModeSheet` plumbing
  (screen → both chat canvas call sites).
- **EDITED `app/src/main/java/com/example/MainActivity.kt`** — one line wiring
  `onSetWorkMode = viewModel::setWorkMode`.

No new dependencies. No new screens. No database changes.

## Deliberately skipped this round

- **No per-conversation mode** — one app-wide switch, like the Live Info
  toggles. Per-chat modes would need a conversation column and a migration.
- **Autopilot is untouched** — the mode governs the chat agent only; the
  Autopilot command centre assembles its tools separately and does not read
  `workMode`.
- **No change to subagent caps or background behaviour** — rounds 1–3 stand.

## What CI must prove (nothing compiled locally — no Android SDK here)

Written and reviewed by reading, **not compiled**. Watch, in order:

1. **The ViewModel enforcement hunk** — the `when` filter over `WorkMode`, the
   `stableSystemPrefix` rename (all four `effectiveSystemPrompt` uses must
   still resolve), and `buildSubagentPlan` reading `allToolTriples`.
2. **The ChatScreen chip** — the status-line Box became a Row; check the
   `when (uiState.workMode)` label and that `Box` is still used elsewhere in
   the file (it is) so no import goes stale.
3. **The plumbing** — `ChatWorkspaceScreen` is called from exactly one place
   (MainActivity) and both `MinimalChatCanvas` call sites received the new
   callback; both new params have defaults, so nothing else can break.

## On-phone smoke test (only Farhan can do this)

1. **Agent only:** set the chip to "Agent only", then ask the Jensen
   Huang-style research prompt ("research the latest information about X, up
   to today's date"). Pass = the main model makes **no Live Info calls of its
   own** — the only tool activity is the spawn; exactly **one** answer comes
   back (the subagent's, relayed or posted as a background result).
2. **Model only:** set "Model only", ask "use a subagent to research X".
   Pass = no spawn occurs and no Subagent log row appears; the model does the
   work itself with its own tools, or says it cannot delegate in this mode.
3. **Auto:** set "Auto" and repeat the research prompt — the old behaviour
   (model may search itself *and* delegate) is possible again, unchanged.
4. **Persistence:** set any mode, leave the chat (open another chat / restart
   the app), come back — the chip still shows the mode you picked.
