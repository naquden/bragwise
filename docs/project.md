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

## Mock data build

To debug / test UI without signing in or touching Firestore, flip one flag in
`shared/src/commonMain/kotlin/se/atte/bragwise/BuildFlags.kt`:

```kotlin
const val USE_MOCK_DATA: Boolean = true   // ← change this, revert before committing
```

This single flag controls all three platforms:

| Platform | What changes |
|----------|-------------|
| Android | Firebase App Check + Analytics skipped; Koin loads mock repositories |
| iOS | Same — `FirebaseApp.configure()` still runs (needed to avoid a crash in the shared layer) but Analytics/Crashlytics/FCM are skipped |
| wasmJs | `initFirebase()` skipped entirely; mock Koin graph loaded |

**What you get in mock mode:**
- Signed in automatically as "Demo Player" (uid `mock-user-001`) — no email link needed.
- In-memory challenges from `MockData.kt` (Champions League final, Friday Quiz Night, FIFA
  World Cup, etc.).
- All writes (predict, post results, react) mutate the in-memory state only — nothing persists
  across restarts.
- No network calls to Firestore, Functions, or Auth.

**Rule:** `USE_MOCK_DATA` must be `false` on `main` and for any release/production build.
It is not gated by build type — a release APK with `true` would ship broken UI. Flip it
manually, verify your change, revert before pushing.

**Always verify `USE_MOCK_DATA = false` before:**
- Building an IPA for App Store / TestFlight
- Generating an AAB for Play Store (`./gradlew :androidApp:bundleRelease`)
- Deploying wasmJs to Firebase Hosting (`./gradlew :webApp:wasmJsBrowserDistribution` + `firebase deploy`)

---

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

## App Check enforcement (wasmJs web)

App Check with reCAPTCHA v3 is deployed to `bragwise-web.web.app` (site key `6LdQujstAAAAAKV97CrT3tW1z3ZbgfdJ5y_rmfOu`). Enforcement is **not yet enabled** — enabling too early blocks real users before Firebase has seen their tokens.

**When to enable:** After a few days, check the App Check metrics dashboard in Firebase console. When unverified traffic is ~0% of total requests, enable enforcement:

1. Firebase console → App Check → APIs tab
2. Firestore → click **Enforce** → confirm
3. Cloud Functions → click **Enforce** → confirm

If valid-token % is <100% when enforcing, real users/browsers are being blocked — investigate before enforcing.

**Why:** The `apiKey` is visible in the JS bundle; anyone can extract it and hammer Firestore reads or Functions calls, inflating costs. App Check + enforcement rejects un-attested requests at the Firebase SDK layer.

---

# Platform-Specific Notes

## Sign in with Apple (iOS)

iOS ships native Sign in with Apple alongside passwordless email-link and
guest. Android and web stay email-link + guest only.

**Why we added it.** Not a compliance fix — a reviewer-unblock. App Review
reported never receiving the email sign-in link (spam checked), which left
them with no way in. Apple sign-in gives reviewers and users a path that
doesn't depend on email delivery.

**Guideline 4.8 status — this is the part that changed.** Before, email-only
qualified for the explicit 4.8 exception ("Your app exclusively uses your
company's own account setup and sign-in systems"), so Sign in with Apple was
NOT required. That reasoning was correct and still is. But now that iOS
offers a third-party login, 4.8 IS active — and Sign in with Apple being
present is exactly what keeps us compliant.

**Consequence: do NOT add Google sign-in (or any other social login) to iOS
without Sign in with Apple also present.** The trigger is the login TYPE, not
the count. Removing the Apple button while keeping any other social login
would break 4.8.

**Implementation summary:**
- iOS only. `supportsAppleSignIn` (expect/actual) gates the Compose button.
- Swift (`iosApp/iosApp/AppleSignInController.swift`) drives
  `ASAuthorizationController` (needs a UIKit presentation anchor) and hashes
  the nonce with CryptoKit; Kotlin builds the Firebase `OAuthProvider`
  credential and does the sign-in/link.
- Anonymous guests are upgraded via `linkWithCredential` (uid preserved,
  predictions kept), falling back to a fresh `signInWithCredential` if the
  Apple account already exists — same shape as the email-link upgrade.
- Apple returns `fullName` only on the FIRST authorization. We write it via
  `updateProfile` there and then; on later sign-ins (nil name) the existing
  `EnsureNamedAccount` name gate handles it. No new UI.
- Backend needed no changes: `requireVerifiedEmail` already treats
  `sign_in_provider === 'apple.com'` as OAuth and skips the email_verified
  check; `recordActivity` already auto-assigns a handle to non-anonymous users.
- Firebase Console: the Apple provider must be enabled. The Services ID /
  Team ID / Key ID / `.p8` fields are for the web/Android OAuth-redirect flow
  and are not needed for the native iOS flow (unverified against Firebase's
  current docs — confirm if the console insists on them).
- Apple Developer portal: Sign In with Apple enabled on App ID
  se.atte.bragwise.Bragwise, which invalidates the `Bragwise Appstore`
  distribution profile — regenerate it (README § iOS build).

**Known gap: Apple token revocation on account deletion.** Apple's guidance
is to call `revokeToken` when an account is deleted. We don't. `deleteAccount`
does fully delete the Firebase Auth user and scrub all data (Guideline
5.1.1(v) satisfied), which is what review actually tests; revocation only
clears the app from Settings → Apple ID → Sign in with Apple. Implementing it
needs a fresh single-use `authorizationCode` (re-running the Apple sheet
inside the delete flow) plus the `.p8` private key configured in the Firebase
Apple provider. Deferred; revisit if a rejection cites it.

Still mandatory, unrelated to 4.8:
- In-app account deletion (Guideline 5.1.1(v)) — already shipped.
- Universal Links / associated domains + `handleCodeInApp` + iOS bundle ID in
  Firebase `ActionCodeSettings`, so the magic link still reopens the app.

Guideline text: https://developer.apple.com/app-store/review/guidelines/#login-services

---

## Updating web emoji glyphs (wasmJs)

### Why the subset font exists

Compose for Web renders all text through the Skia (Skiko) canvas. Skiko only
knows the fonts Compose bundles — it has no system emoji font. On Android/iOS,
Compose delegates to the OS text stack, which supplies a system emoji font. The
web target has no such fallback, so every emoji codepoint would render as a tofu
box (□) without an explicitly bundled font.

The fix: bundle a subsetted NotoColorEmoji.ttf (Google Noto Emoji, OFL license)
and register it as a **resolver fallback** via `fontFamilyResolver.preload()` in
`App()`. This is the supported path for wasmJs (CMP 1.7.0+, JetBrains issue
CMP-3051): putting an emoji font in a `FontFamily` list is unreliable on web
(behavior depends on font position in the list). Registering via `preload()`
makes the font a global fallback that the resolver consults for every unresolved
glyph — no per-`Text` `fontFamily` override needed.

**CRITICAL — the font must be a byte-loaded `LoadedFont`, not a `ResourceFont`.**
The compose-resources `Font(Res.font.noto_color_emoji)` returns a `ResourceFont`
on wasmJs. Skia's font loader (`SkiaFontLoader.skiko.kt`) only registers
`PlatformFont` subclasses (`LoadedFont` / `SystemFont`) — so calling
`preload()` on a `ResourceFont` is a **silent no-op** and emoji still box. The
working path reads raw bytes via `Res.readBytes("font/noto_color_emoji.ttf")`
and constructs the skiko-only byte Font
`androidx.compose.ui.text.platform.Font(identity = "NotoColorEmoji", data = bytes)`,
which produces a `LoadedFont` that `FontMgr.makeFromData()` actually registers.
This byte constructor exists only in the skiko/web source sets, so it is wired
through an `expect`/`actual` (`theme/EmojiFallback.kt`): the wasmJs actual loads
the bytes; Android/iOS actuals return `null` (those platforms use the OS emoji
font, unchanged).

Skiko already ships a default Latin font, so Latin text renders fine without
bundling Roboto. Only emoji were boxing, so only the emoji font needs to be
bundled.

### Font file location

```
shared/src/commonMain/composeResources/font/noto_color_emoji.ttf
```

The file is a subset of NotoColorEmoji.ttf trimmed to the 42 emoji codepoints
in `shared/src/commonMain/kotlin/se/atte/bragwise/ui/components/AvatarOptions.kt`.

### Regenerating the subset when new emojis are added

If you add new emoji to `AvatarOptions.kt` (or use emoji in any other app
text), you must regenerate the subset so the new codepoints are included.

Prerequisites:
```
pip3 install fonttools brotli
```

Steps:

1. Download the full NotoColorEmoji.ttf:
   ```
   curl -L "https://github.com/googlefonts/noto-emoji/raw/main/fonts/NotoColorEmoji.ttf" \
     -o NotoColorEmoji.ttf
   ```

2. Collect all emoji codepoints used by the app (from `AvatarOptions.kt` and
   any other call sites). Convert to uppercase hex, comma-separated. Example
   command to extract from the current list:
   ```python
   python3 -c "
   emojis = ['😀','😎', ...]  # paste full list from AvatarOptions.kt
   cps = [format(ord(c), 'X') for e in emojis for c in e if ord(c) > 0xFF]
   print(','.join(cps))
   "
   ```

3. Run pyftsubset (CBDT/CBLC color-bitmap tables are subset automatically):
   ```
   pyftsubset NotoColorEmoji.ttf \
     --unicodes="<comma-separated-hex-codepoints>" \
     --output-file=noto_color_emoji.ttf
   ```
   The exact command used for the current subset (42 codepoints):
   ```
   pyftsubset NotoColorEmoji.ttf \
     --unicodes="1F600,1F60E,1F929,1F973,1F60D,1F913,1F608,1F47B,1F916,1F47D,\
   1F602,1F605,1F61C,1F917,1F981,1F42F,1F43B,1F98A,1F43A,1F985,1F98B,1F409,\
   1F984,1F988,1F438,1F427,1F989,1F419,1F525,26A1,1F3C6,1F3AF,1F3AE,1F680,\
   1F48E,1F31F,1F340,1F3B2,26BD,1F3C0,1F3B8,1F308" \
     --output-file=shared/src/commonMain/composeResources/font/noto_color_emoji.ttf
   ```

4. Verify the output: it should be ~100–200 KB (not 10 MB), contain CBDT/CBLC
   tables (color bitmaps), and have the expected codepoint count:
   ```python
   python3 -c "
   from fontTools.ttLib import TTFont
   f = TTFont('noto_color_emoji.ttf')
   print('Tables:', list(f.keys()))
   print('Codepoints:', len(f.getBestCmap() or {}))
   "
   ```

5. Commit the new font file. The `Res.font.noto_color_emoji` accessor is
   auto-generated by the Compose resources plugin from the filename — no
   `build.gradle.kts` changes needed.

### How the fix works in code

`theme/EmojiFallback.kt` (expect) + per-target actuals:

```kotlin
// commonMain — theme/EmojiFallback.kt
expect suspend fun emojiFallbackFamily(): FontFamily?

// wasmJsMain — theme/EmojiFallback.wasmJs.kt
actual suspend fun emojiFallbackFamily(): FontFamily? {
    val bytes = Res.readBytes("font/noto_color_emoji.ttf")
    // androidx.compose.ui.text.platform.Font(identity, data) → LoadedFont
    return FontFamily(Font(identity = "NotoColorEmoji", data = bytes))
}

// mobileMain — theme/EmojiFallback.mobile.kt  (Android + iOS)
actual suspend fun emojiFallbackFamily(): FontFamily? = null
```

`shared/src/commonMain/kotlin/se/atte/bragwise/App.kt`:

```kotlin
val fontFamilyResolver = LocalFontFamilyResolver.current
var fontsLoaded by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
    emojiFallbackFamily()?.let { fontFamilyResolver.preload(it) }
    fontsLoaded = true
}
```

The app gates its first content render on `fontsLoaded` to avoid a first-paint
tofu flash (the preload is fast — font bytes are bundled in the wasm module).

### Color-emoji rendering on wasmJs

Skiko's wasm canvas (CMP 1.11.0) renders NotoColorEmoji's **color-bitmap
(CBDT/CBLC)** glyphs. The subset bundled here contains CBDT/CBLC tables.
Visual verification on a clean **production** web build
(`:webApp:wasmJsBrowserDistribution`), driving the real app to the Edit Profile
→ Avatar picker, confirmed full-color emoji render (not tofu, not monochrome).

### ⚠️ Dev/prod web build-state gotcha — `custom-formatters.js`

`wasmJsBrowserDevelopmentExecutableDistribution` and the production
`wasmJsBrowserDistribution` share the same `build/wasm/packages/Bragwise-webApp`
output dir. The dev build emits `import "./custom-formatters.js"` into
`Bragwise-webApp.mjs` (a Kotlin/Wasm debug feature); the production webpack does
**not** emit that JS file. If you run a dev build then a prod build (or serve a
stale mixed bundle), webpack fails with:

```
Module not found: Error: Can't resolve './custom-formatters.js'
```

and the dev server serves the **previous (stale) bundle behind a red "Compiled
with problems" overlay** — which can look like a fix didn't take. This is not a
source bug. Fix: `rm -rf build/wasm/packages/Bragwise-webApp` then rebuild the
target you actually want. Don't interleave dev and prod web builds without
cleaning between them.
