# Icons are available at
https://composables.com/icons

---

# User Experience & Feature Design

## Guest and signed-in user feature parity

Guest and signed-in users should have matching functionality as much as possible.
**Only difference:** signed-in users can create challenges; guests cannot (logistics and policy).

Guest users have full access to:
- Predict on challenges
- View leaderboards and results
- Friends feature (send, accept, decline, unfriend)
- Bet visibility when enabled
- Me screen and profile settings

Why: Core app experience should be identical; create/invite/share restrictions are policy-driven.

## Lazy Firebase account creation

When users first start the app, **do not auto-create a Firebase UID or account**.
Account creation should be deferred until the user performs an action that requires it:
- Join a challenge
- Sign in
- Set a display name (from prompt, edit profile, or future flow)

Why: Minimize dead accounts stored in Firebase. Prioritize core functionality — always.
If unsure whether an action needs an account, ask: "Would a user be upset if this failed offline?"
If yes, create the account on demand. If no, defer.

**Verification:** Check `PredictViewModel` and `EnsureNamedAccount` for the bootstrap pattern.

## Dead users and stale data cleanup

**Dead users and orphaned data must be cleaned up aggressively.** Every deleted user cascades a 10-step cleanup:

1. `handles/{handle}` — claimed handles
2. Friend references — removed from all friends' social docs and head-to-head records
3. `friendships/{pairId}` — all canonical friendship docs involving user
4. `players/{uid}/private/*` — all private subcollections (social, preferences, counters, etc.)
5. `challenges/{challengeId}/players/{uid}` — all challenge membership; leaderboards recomputed if results posted
6. `challenges/{challengeId}/invitations/{uid}` — all stale invitations to user
7. Push tokens — all registered `players/{uid}/pushTokens/*`
8. Public profile — `publicProfiles/{uid}`
9. Root player doc — `players/{uid}`
10. Firebase Auth user (already deleted, trigger fires from this)

**Stale friend requests and invitations are cleaned up with the user**, not orphaned.

**Scheduled cleanup jobs** (functions/src/triggers.ts):
- **`purgeStaleGuests`** (daily): Delete anonymous accounts inactive >90 days → triggers full 10-step cascade
- **`purgeOldChallenges`** (daily): Delete challenges with results posted >90 days old, or created >90 days ago with no results yet
- **`reconcileDeletions`** (hourly): Resume any deletions still pending after 24h (idempotent resumable pattern)

Why: Firestore doesn't cascade-delete subcollections; all cleanup is explicit. Idempotent pattern allows resumable deletions on failure. Aggressive TTLs (90 days) prevent dead data from accumulating. Reference: `functions/src/triggers.ts:253-611` and `functions/src/index.ts:596-626`.

## Bet visibility

Bets are visible to other users when the challenge has the "show all bets"
toggle enabled (`betsVisible`). Without that toggle, bets remain private to
their owner.

**Pre-lock visibility is intended and is NOT cheating.** When `betsVisible` is
enabled, other participants' bets are visible to *anyone who can reach the
challenge* — promoted feed, friend invite, or share link — regardless of whether
they are signed in, a member, or the creator. Bragwise challenges are not only
sports bets — they're also game-night fun and quizzes. Users want to laugh at
and with each other about their guesses before results are posted. No one knows
what's correct yet; it's all guessing, so seeing others' bets early is not
cheating. The unguessable 20-char doc ID and App Check (enforced SDK/callable
side, not Firestore rules) are the abuse guards.

**Implemented:** `ChallengeDetailScreen.kt` uses `canViewBets = participant.uid == myUid || challenge.betsVisible`.
Firestore `players/{uid}` read rule: `isSelf(uid) || challenge.betsVisible == true` — no sign-in or membership gate.

## Friends for guests and signed-in users

**Both guest and signed-in users can add friends.** No restrictions based on auth state.

Guest (anonymous) accounts have the **full friends feature**: they can send, accept,
decline, withdraw, and unfriend. They can also reach the Friends screen from Me.

Guests get a handle via `EnsureNamedAccount` → `claimHandle` (no verified-email gate),
so they are discoverable by username and can receive friend requests from real accounts.

All five friend callables (`sendFriendRequest`, `acceptFriendRequest`, `declineFriendRequest`,
`withdrawFriendRequest`, `unfriend`) in `functions/src/index.ts` use only `requireAuth` +
App Check + rate limits — NOT `requireVerifiedEmail`. Do not add that gate back.

Guests still **cannot** create challenges, invite others, or share challenges. Those
callables keep their `requireVerifiedEmail` gate.

## Display name updates: local-first, eventual consistency

Display name can be changed by the user locally and is stored immediately in local state.
**The server does NOT update the display name directly** to avoid Firebase write spam and costs.

Instead, display name is synced via:
- Local cache (updated instantly on user edit)
- `users/{uid}` document synced on subsequent writes (profile updates, challenge participation, etc.)
- Eventual consistency — if a user changes their name but doesn't interact with the app after, the server won't see the new name until their next action

Why: Minimize Firebase write costs while keeping the UI responsive. Local reads are instant; eventual sync is acceptable because display name isn't mission-critical per-action.

**Verify:** Check `PredictViewModel` and profile edit screen for local state updates vs. server sync pattern.

### How to verify this fix

End-to-end steps on a real device (or emulator):

1. **Setup:** sign in as user A (email-backed). Create a challenge. Have guest B join and
   predict (guest picks a name on first action → gets a handle auto-assigned).
2. **Send from A:** on the reveal screen (ParticipantBetsScreen), tap the add-friend icon
   next to guest B. Confirm A sees "request sent" snackbar.
3. **Guest receives notification:** device/emulator running as guest B should receive an
   FCM push notification "New friend request".
4. **Accept from guest:** as guest B, open Me → Friends (should open without redirecting
   to sign-in). Tap Accept on A's incoming request. Confirm both A and B now list each
   other as friends.
5. **Guest send:** as guest B, tap "Add by username", enter A's handle. Confirm request
   is sent. As A, accept. Both friends lists update.
6. **Cleanup:** delete guest B's account (Settings → Delete account, or simulate
   `purgeStaleGuests` 90-day path). After deletion:
   - A's friends list no longer shows B.
   - Firestore: no `friendships/{pairId}` doc containing B's uid should remain
     (check console or emulator UI).

---

# Challenge Flow & Results

## Challenge visibility rules

Challenge discoverability depends on status and visibility setting:

**Invite-only challenges:**
- Visible only to explicitly invited users
- Visible via direct link (unguessable 20-char doc ID)
- Never appear in promote/feed feeds
- Not visible to non-invited users even if they know the link

**Friends visibility challenges:**
- Visible to all friends of the creator
- Visible via direct link
- Never appear in promote/feed feeds
- Not visible to non-friends

**Promoted challenges:**
- Always visible to all users (signed-in and guest)
- Appear in promoted feed
- Visible via direct link

**Locked challenges (status == LOCKED):**
- **Not shown** in challenge list if user has NOT participated
- **Shown** in challenge list if user HAS participated and challenge has NOT finished
- Once finished (status == RESULTS_POSTED or RESOLVED), always visible to non-participants via direct link

Why: Locked challenges allow only existing participants to see new activity; prevents spam/discovery of locked challenges. Finished challenges show results to anyone with the link.

## Creator-didn't-predict: valid reveal state

A challenge CREATOR who publishes without submitting predictions will have `myRank == null`
on the finished reveal screen. This is intentional — `observeCreatedBy` returns challenges
by `createdBy` field (no players-doc dependency), but `postResults` only includes uids that
have a `players/{uid}` doc (created on first prediction submit). So the creator appears in
the Results list but is absent from the leaderboard → `myRank == null` → the "You didn't
predict" banner in `ResultsRevealScreen.kt` is the correct UI for this case. Keep it.

## Creator can always post results

The creator of a challenge can always post results, regardless of the lock
deadline. Posting results ends the challenge (status → `RESULTS_POSTED`).
There is no "not-yet-locked" gate in `postResults` (functions/src/index.ts),
and the client enables the button for both `OPEN` and `LOCKED` status
(`ChallengeDetailScreen.kt`).

## Head-to-head (H2H) records: friend timing matters

**H2H records are created when a challenge resolves between friends, not retroactively.**

If two users become friends AFTER a resolved challenge they both participated in:
- They **will not** get an H2H record for that past challenge
- H2H only generates for NEW challenges that resolve after they're friends
- No `onFriendshipWritten` trigger recomputes old challenges (keeps change small and avoids expensive retroactive work)

If the gap matters later, the backfill can be re-run to retroactively compute H2H for all past resolved challenges between friends.

Why: Retroactively recomputing H2H for all past challenges on every friendship write is expensive. Users mainly care about ongoing H2H tracking; historical backfill is an opt-in operation.

---

# Platform & Infrastructure

## Firebase environments: prod vs dev

The `bragwise` Firebase project is **prod**. A separate `bragwise-dev` project will be created later for the debug build only (no separate dev project for iOS — Android debug build is the sole dev target).

**Current state:**
- `androidApp/google-services.json` (gitignored, module root) is the prod config for the `bragwise` project. Module root = base config for all build variants — this is correct, do not move it.
- Both release and debug builds use `applicationId` `se.atte.bragwise` (no `.dev` suffix — removed intentionally). Debug app name is "Bragwise Dev" via `resValue`.
- Prod's `google-services.json` registers only `se.atte.bragwise`. Both build types hit the prod `bragwise` Firebase project for now.
- **Side effect:** debug and release cannot be installed side-by-side on the same device (same package name).

**To wire up a real dev environment later:**
1. Add `applicationIdSuffix = ".dev"` back to the debug build type in `androidApp/build.gradle.kts`.
2. Create a `bragwise-dev` Firebase project, register `se.atte.bragwise.dev` in it.
3. Drop its config at `androidApp/src/debug/google-services.json`. The Google Services Gradle plugin auto-overrides the debug build; module-root file stays prod base for release.

**No code changes needed for any of this.** Cloud Functions use `admin.initializeApp()` (`functions/src/lib/admin.ts`) which targets whatever project they're deployed to — deploy the same `functions/` to `bragwise-dev` later. Firestore rules/indexes deploy per project the same way.

**Before launch:** purge any debug-origin test data (challenges, users) sitting in prod `bragwise` Firestore.

## SQLDelight database migrations

After first public release, any change to `Bragwise.sq` that adds/removes/alters tables or columns **requires a migration file** alongside it.

How it works:
1. Bump `schemaVersion` in `shared/build.gradle.kts` (sqldelight block) by 1.
2. Create `shared/src/commonMain/sqldelight/se/atte/bragwise/db/<old_version>.sqm` with the SQL to upgrade from the previous version.
3. SQLDelight will run that migration on existing installs; fresh installs get the full schema from `.sq` directly.

Example: going from version 2 → 3, create `2.sqm`:
```sql
CREATE TABLE NewTable (id TEXT NOT NULL PRIMARY KEY);
ALTER TABLE ExistingTable ADD COLUMN newCol TEXT;
```

**Pre-release note:** No migrations were written before first release. Schema versioning starts at the first public release version. Do not backfill migrations for changes made before that point — just ensure the version counter and migration files stay in sync going forward.

---

# Platform-Specific Notes

## Sign in with Apple not required (iOS)

We do NOT need Sign in with Apple as long as passwordless email (Firebase
email-link) stays our only auth method. App Store Review Guideline 4.8
("Login Services") only forces Sign in with Apple when the app offers a
third-party / social login (Google, Facebook, Apple, etc.) as the primary
sign-in. Our own email-based account system falls under the explicit 4.8
exception: "Your app exclusively uses your company's own account setup and
sign-in systems."

Trigger is the login TYPE, not the count. If we ever add a social login
button (Google/Facebook/etc.) to iOS, guideline 4.8 activates and we'd then
have to add Sign in with Apple alongside it. Pure email = safe.

Two separate iOS submission blockers unrelated to this exception, verify
before submitting:
- In-app account deletion (Guideline 5.1.1(v)) is mandatory for any app with
  account creation.
- Universal Links / associated domains + `handleCodeInApp` + iOS bundle ID in
  Firebase `ActionCodeSettings`, so the magic link reopens the app.

Guideline text: https://developer.apple.com/app-store/review/guidelines/#login-services

