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

