# Runway — Round 6 (NOT pushed, NOT committed)

Base: `origin/main` @ `c2ea05e` (Round 5: spawn_subagent_chain — background
step pipelines). Rounds 1–5 are live on main. This round fixes two composer
problems Farhan hit on the phone while testing:

1. **While the model is working, he can't send anything.** He typed "hii"
   during a reply and it just sat in the box — the send button is replaced by
   Stop for the whole turn.
2. **The keyboard's Enter key sends instead of making a new paragraph.** He
   circled the keyboard's send key and asked for "a enter option in keyboard
   instead of a different paragraph one" — then corrected the direction
   himself: he wants the **paragraph (newline) key**, not send-on-Enter.

These changes are uncommitted working-tree changes in this staging clone only.

## Feature A — Send while generating (the message queue)

**What changed:** while a reply is generating, a second button appears in the
composer — an up-arrow next to Stop (testTag `composer_queue_button`). Type a
message, tap it, and the message is **queued**: the composer clears, and a
pill with your text appears in a slim "Queued" row above the composer. When
the current turn ends, the queued message **sends itself** through the normal
send path, exactly as if you had typed and sent it then.

**How it works:**

- The queue lives in the ViewModel's UI state as a list of `QueuedMessage`
  (id, conversationId, text) — in-memory only, per conversation. Tapping the
  queue button runs `queueMessage()`, which appends the trimmed draft for the
  open conversation and clears the composer.
- The pills row sits directly above the composer, **below** the
  background-subagents row (same Surface styling family), and shows only the
  open conversation's queue. Each pill is the queued text on one line
  (truncated) with an ✕ that removes just that entry. No row when the queue
  is empty.
- **Draining:** every turn end shares one signal — the UI state's `isStreaming`
  flag flipping true → false (a reply finishing, erroring, a compare run
  ending, an in-chat image/video finishing, or **Stop**). A small collector in
  the ViewModel watches that edge and calls `drainQueuedMessage()`, which pops
  the open conversation's **first** entry and runs it through the untouched
  `sendMessage()` — same validation, same slash-commands, same everything. One
  entry per turn end: sending starts a new turn, and the remaining entries
  wait for that turn's end. So **Stop + a queued message = steering**: stop a
  reply going the wrong way and your queued correction fires immediately.
  Anything you had already typed for the *next* message while the drained one
  sends is preserved — the drain restores your in-progress draft afterwards.
- **Targeting choice (and why):** sends in Runway are hard-bound to the open
  conversation — `sendMessage()` reads the global draft and
  `activeConversationId`, and there is exactly one stream at a time. So a
  queue entry only drains while **its** conversation is the open one; entries
  for other conversations simply wait (their pills are there when you go back
  to that chat). Retargeting sends at arbitrary conversations would have meant
  rewriting the send path; this way the normal path is reused untouched.
- **Guards:** a queue entry never drains into a deleted conversation — the
  drain checks the conversation still exists and drops that conversation's
  queue silently if it is gone. Draining is also held while you are editing a
  message (an edit is never hijacked by a queued send). If `sendMessage()`
  declines a drained entry because another turn started first, the entry is
  put back at the front of the queue, not lost.
- **Cap: 5 queued messages per conversation.** At the cap the queue button
  hides, and `queueMessage()` refuses further entries.

**Honest limits (same rule as background jobs):** the queue is **in-memory
only**. Kill the app (swipe away, phone restart) and queued messages are gone
— they were never written anywhere. And a drained message is a *text* send:
if you attach files after queueing, the drained send goes through the normal
path with whatever is in the composer at that moment.

## Feature B — Paragraph Enter key in the main composer

**What changed:** in the **main chat composer only**, the text field no longer
declares an IME Send action. Its keyboard options now use `ImeAction.None`
when the composer's `enterToSend` flag is off, and the `onSend` keyboard
handler is only wired when that flag is on — so the on-screen keyboard shows
its **return/paragraph key** and pressing Enter starts a new line in the
multiline field. The on-screen arrow button remains the way to send (and the
queue button from Feature A is how you send while a reply is generating).

**How, exactly:** `MinimalChatCanvas` now passes `enterToSend = false` to the
composer bar, and inside the bar the off-branch uses `ImeAction.None` (it was
`ImeAction.Default` before) with `KeyboardActions.Default` instead of an
`onSend` handler. One honest consequence, left visible in a code comment: the
Settings **"Enter to send" toggle no longer drives the main composer** — the
stored setting, its vault flow and the Settings switches are untouched, but
the main composer always uses paragraph Enter now, per Farhan's corrected
request. Re-wiring the toggle is a two-line change if it is ever wanted back.

**What it does NOT affect:** the separate legacy `ChatScreen` composer (still
passes `enterToSend = true`, untouched), the edit-message flow's fields, the
compare composer, and every Settings/other text field in the app — all
untouched, Enter behaviour there is exactly as before.

## Files changed

- **EDITED `app/src/main/java/com/example/ui/viewmodel/OmniStudioViewModel.kt`** —
  `QueuedMessage` + `MAX_QUEUED_MESSAGES_PER_CONVERSATION = 5`; the
  `queuedMessages` UI-state field; the `isStreaming` true→false collector in
  `init`; `queueMessage()`, `removeQueuedMessage()` and the private
  `drainQueuedMessage()` (A).
- **EDITED `app/src/main/java/com/example/ui/screens/ChatScreen.kt`** —
  `onQueueMessage` / `onRemoveQueuedMessage` plumbed ChatWorkspaceScreen →
  both MinimalChatCanvas call sites → canvas; the "Queued" pills row above the
  composer; the composer gains the queue button and `onQueue`/`queuedCount`
  params; `enterToSend = false` at the main-canvas composer call; the
  composer's off-branch now uses `ImeAction.None` with no `onSend` handler
  (A + B).
- **EDITED `app/src/main/java/com/example/MainActivity.kt`** — two lines wiring
  `onQueueMessage = viewModel::queueMessage` and
  `onRemoveQueuedMessage = viewModel::removeQueuedMessage` (A).

No new dependencies. No new screens. No database changes, nothing persisted.

## Deliberately skipped this round

- **Attachments in the queue** — v1 is text only. With files attached, the
  queue button never appears and today's behaviour is unchanged.
- **A persisted queue** — nothing is written to disk; an app kill loses the
  queue (see the honest limit above).
- **Per-conversation send retargeting** — the drain only fires for the open
  conversation, because that is what the existing send path targets.
- **Removing the now-inert "Enter to send" Settings toggle** — left in place
  (stored value, vault flow, switches) rather than rip out settings plumbing
  in a composer round; the main composer ignores it now.
- **Enter behaviour everywhere else** — edit-message, compare and Settings
  fields keep their current behaviour (Feature B is main-composer only).

## What CI must prove (nothing compiled locally — no Android SDK here)

Written and reviewed by reading, **not compiled**. Brace/paren balance of all
three edited files matches HEAD exactly; the diff was read hunk by hunk.
Watch, in order:

1. **OmniStudioViewModel.kt** — the drain collector in `init` and
   `drainQueuedMessage()` (its requeue-on-decline branch reads the draft back
   after calling `sendMessage()`; the logic is reviewed but only the compiler
   and a phone can prove the flow).
2. **ChatScreen.kt** — the queue button's `canQueue` condition and the pills
   row (new Surface/Row block in the canvas Column), the conditional
   `keyboardActions` expression, and the new defaulted params on
   `ChatWorkspaceScreen` / `MinimalChatCanvas` / `MinimalFloatingComposerBar`.
3. **MainActivity.kt** — the two wiring lines (lowest risk).

**On-phone smoke test** (only Farhan can do this):

1. Start a long reply, type a message, tap the **queue button** → a pill
   appears above the composer; when the turn ends, the message sends itself
   with no further tap.
2. Queue **two** messages during one turn → they send one per turn, in the
   order you queued them.
3. Queue one, then tap **Stop** → the queued message fires immediately after
   the stop (steering).
4. Queue one, tap its pill's **✕** → it never sends.
5. Open the keyboard in the composer → the **return/paragraph key** is shown,
   Enter starts a new line mid-message, and the on-screen arrow still sends.
   In Settings, other text fields behave as before.

## Also in this push: round-5 build fix

Build #90 (the round-5 chains push) failed to compile, so chains never reached
a phone. The cause: round 5 had split one helper out of the background-subagent
code — `postBackgroundResultMessage`, the piece that posts a finished
background run's result into the chat. The code it contains saves the message
to the database, and database saves are *suspend* operations in Kotlin: they
may only run inside a coroutine. The extracted helper was declared as a plain
function, so the compiler rejected those two calls ("Suspension functions can
only be called within coroutine body").

The fix is one word: the helper is now a `suspend fun`. Both places that call
it — a finished background subagent and each step of a finished chain — already
run inside coroutines, so nothing else had to change: the result message is
still saved exactly once per completion, in the same order as before (message
first, then the job's status update). **No behaviour changes** — background
subagents, chains and this round's queue/Enter work exactly as described
above; the code now simply compiles.
