# Runway Groups — Phase 1 (Firebase) — change summary

Adds an opt-in **Groups** feature to Runway: Google sign-in, group rooms with
invite codes, a real-time discussion thread per group, and an admin-only usage
page. Everything lives behind a guarded doorway (`GroupsFirebase`): if
Firebase cannot start, Groups shows a plain "Groups unavailable" card and the
rest of Runway is byte-for-byte unaffected. User API keys and all existing
Runway data never leave the phone — Firestore only ever sees the fields in
`GroupsModels.kt`.

Backend: Firebase project **runway-c7ed3** (Spark, Firestore in production
mode, asia-south1, Google sign-in enabled — created and verified separately).

## Files added

| File | What it is |
|---|---|
| `app/google-services.json` | Copied verbatim from the verified local copy for project runway-c7ed3 / package `com.aistudio.omniaistudio.rxkqpz`. Not a secret (standard to commit); security comes from `firestore.rules`. |
| `app/src/main/java/com/example/groups/GroupsModels.kt` | `GroupRoom`, `GroupMessage`, `GroupStats` data classes. |
| `app/src/main/java/com/example/groups/GroupsFirebase.kt` | `GroupsFirebase` gate (lazy availability check + nullable Auth/Firestore accessors) and an internal `Task.awaitCompletion()` so no extra coroutines-play-services dependency is needed. |
| `app/src/main/java/com/example/groups/GroupsAuth.kt` | `GroupsAuthRepository`: auth-state Flow, current-user snapshot, Google sign-in via Jetpack Credential Manager (`GetGoogleIdOption` with `R.string.default_web_client_id` → Firebase `GoogleAuthProvider` credential), sign-out. |
| `app/src/main/java/com/example/groups/GroupsRepository.kt` | Firestore: user upsert (bumps `totalUsers` only for a new doc), create group (6-char invite code from an unambiguous alphabet), join by code (`arrayUnion`), groups Flow, messages Flow, send message (batch: message + `lastMessageAt`/`messageCount`), stats Flow, admin-check Flow (`config/admin` `uids`). |
| `app/src/main/java/com/example/groups/GroupsViewModel.kt` | No-arg ViewModel wiring the flows together; busy/status state; sign-in/upsert, create/join (with success callbacks for navigation), open/close group, send. Cancelled Google sheets are swallowed silently. |
| `app/src/main/java/com/example/groups/GroupsRoot.kt` | `GroupsRoot` — self-contained destination with its own back stack (List → Chat → Admin) and BackHandler (internal pops first, then exits to Home). Also contains the group list / sign-in screen, shared `GroupsHeader`, and time-label helpers. |
| `app/src/main/java/com/example/groups/GroupChatScreen.kt` | Discussion thread: bubbles (own = accent, right-aligned), sender names, clock labels, invite-code copy button in the header, slim composer. |
| `app/src/main/java/com/example/groups/AdminScreen.kt` | Admin page: `stats/global` counters (users / groups / messages), the admin's groups with member + message counts, and a note that exact quota lives in the Firebase console. Only reachable when the uid is an admin (the entry card doesn't exist otherwise). |
| `firestore.rules` | Security rules (see below). **Not deployed by this change — must be pasted into the console.** |

## Files changed

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | `googleServices` 4.5.0 → **4.4.2** and `firebaseBom` 34.17.0 → **33.5.1** (the versions pinned for this feature; both entries already existed in the catalog, as did `credentials` 1.5.0 and `googleid` 1.1.1, which match the spec unchanged). |
| `build.gradle.kts` (root) | One line: `alias(libs.plugins.google.services) apply false`. |
| `app/build.gradle.kts` | Applies the google-services plugin; adds Firebase BoM platform + `firebase-auth` + `firebase-firestore` + `androidx-credentials` + `credentials-play-services-auth` + `googleid`, all via the existing catalog. |
| `app/src/main/java/com/example/MainActivity.kt` | Groups hookup only: one state flag `groupsVisible`; one render branch (`GroupsRoot`) between Home/Connectors and the tab content; `!groupsVisible` added to three existing conditions (root BackHandler, section header, tab-swipe gestures) so Groups owns its own chrome and back behaviour; one `onOpenGroups` callback passed to Home. No other behaviour touched. |
| `app/src/main/java/com/example/ui/screens/HomeScreen.kt` | One new parameter `onOpenGroups` and one new "Groups" row in *Studios & tools*, a pixel-for-pixel sibling of the existing "More" row (same surface, icon-chip and chevron; `LucideIcons.MessageSquare`, subtitle "Discuss & build together"). |

## Where the UI hooks in

Home → **Groups** row → `GroupsRoot` (full-screen, replaces tab content like
the Connectors screen does). Inside: group list (sign-in card when signed
out) → tap a group → discussion thread; **Admin** card appears in the list
only for admins → admin page. Back from a thread returns to the list; back
from the list returns Home. Styled entirely with the existing Concept-A
language (`MinimalCard`, `MinimalButton`, `MinimalSheet`, `MinimalSectionHeader`,
theme colours) — no new colours, no gradients.

## Design decisions (and why)

- **Client-side sort of the group list.** `whereArrayContains(memberUids) +
  orderBy(lastMessageAt)` would demand a composite Firestore index; group
  counts are tiny, so the list is sorted in memory instead.
- **No `kotlinx-coroutines-play-services`.** A 15-line `awaitCompletion()`
  helper covers every Task call; one less dependency to resolve.
- **stats/global is only observed when the user is an admin** — the rules
  deny reads to everyone else, and this avoids a guaranteed permission error
  (and wasted read) for every non-admin on every launch.
- **Stat increments are best-effort** (`runCatching`): a failed counter write
  never blocks creating a group, joining, or sending a message.
- **Sending a message is one batch** (message doc + group's
  `lastMessageAt`/`messageCount`), so the list ordering can never drift from
  the thread.
- **Fail-soft everywhere.** All repository calls check the gate first; auth
  and Firestore handles are nullable; sign-out is wrapped. The biometric
  lock, providers, chats etc. are untouched.
- **Admin model:** `config/admin` doc with a `uids` array, observed live;
  missing doc = no admins. Writable by nobody from clients (console only).

## Manual steps still needed at runtime

1. **Paste `firestore.rules`** into Firebase console (project runway-c7ed3)
   → Firestore Database → Rules → Publish. **Until this is done, production
   mode denies every Firestore call** and Groups will only show errors.
2. **Make Farhan an admin:** sign in once inside the app (Home → Groups →
   Sign in with Google), copy the value shown as **"Your ID"** on the Groups
   screen, then in the console create document `config/admin` with a field
   `uids` of type *array* containing that UID string. The Admin card appears
   on the next launch (it is actually live — the doc is observed).
3. Nothing else: the Android app is already registered in the project with
   the CI signing SHA-1 (`86:02:…:C3:08`), Google sign-in is enabled, and
   `google-services.json` is in place.

## Risks / unknowns

- **First CI build is the compile verdict** (no local Android SDK). Imports
  and APIs were audited by hand against the pinned versions (Firebase BoM
  33.5.1, google-services plugin 4.4.2, credentials 1.5.0, googleid 1.1.1),
  but a red build on first push would not be surprising; fix forward from
  the compiler output.
- **Version pins changed from what the catalog carried** (BoM 34.17.0 →
  33.5.1, plugin 4.5.0 → 4.4.2) per spec. All five pinned artifacts were
  verified resolvable on Google's Maven repo while staging (HTTP 200:
  google-services 4.4.2, firebase-bom 33.5.1, credentials 1.5.0,
  credentials-play-services-auth 1.5.0, googleid 1.1.1); BoM 33.5.1 maps
  firebase-auth 23.1.0 and firebase-firestore 25.1.1.
- **Credential Manager needs Google Play services** on the device and the
  registered SHA-1 to match the installed build's signature. CI builds use
  the project's `debugConfig` keystore, whose SHA-1 is the one registered.
  A differently-signed build (e.g. a local debug key) will fail sign-in
  with a "no credentials" style error — expected, not a bug.
- **Rules are pragmatic, not perfect:** any signed-in user could inflate
  `stats/global` by calling the API directly (counters are informational),
  and `senderName` on messages is cosmetic/unverified. Both are commented
  in `firestore.rules` and accepted for Phase 1.
- **APK size grows** (Firebase Auth + Firestore + Play services auth).
  Multidex is native at minSdk 24, so no config change is needed.
- Real-time presence, shared project files, and AI-in-group are **Phase 2/3**
  and intentionally not in this change.
