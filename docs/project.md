# Icons are available at
https://composables.com/icons

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

## Guests are restricted

Guest accounts cannot create challenges and cannot have friends. These
features require a non-guest (verified) account. Guests also cannot invite
others or share challenges.

## Bet visibility

Bets are visible to other users when the challenge has the "show all bets"
toggle enabled. Without that toggle, bets remain private to their owner.

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

