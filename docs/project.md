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
features require a non-guest (verified) account.

