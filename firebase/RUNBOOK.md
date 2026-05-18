# Firebase Operator Runbook

## Promote a challenge

Promotion is **operator-only** (the developer). Clients can never write
`promoted: true` or `trusted: true` — the `createChallenge` and `updateDraft`
callables hard-reject those fields, and the `challenges/*` write rule is
`false` for all SDK paths. The only legitimate writer is the Firebase Console
itself (audited via Cloud Audit Logs against the operator's hardened Google
account).

1. Sign into the in-app account that the operator owns. Create the challenge
   via the normal flow (`CR-01..03`). It lands as `visibility: FRIENDS` or
   `INVITE_ONLY` like any user-created challenge.
2. Publish it (status flips `DRAFT → OPEN`).
3. Open Firebase Console → Firestore → `/challenges/{challengeId}`.
4. Set fields in the Console UI (NOT via service-account key from a laptop):
   - `visibility = "PROMOTED"`
   - `promoted = true`
   - `trusted = true` (only if the challenge should carry the ✓ Verified badge)
5. Verify the challenge now appears in the Promoted section of the Challenges
   screen for any signed-in user (and any guest opening the app).

## Post results for a promoted challenge

Same as user-posted results — open the app signed in as the creator
(operator account), open the published challenge, hit **Post Results**.
Atomic all-or-nothing; once posted, results are immutable and `onResultsPosted`
fires the leaderboard build asynchronously.

## Deploy rules and indexes

```sh
firebase deploy --only firestore:rules,firestore:indexes \
  --project bragwise-prod
```

Always deploy to `bragwise-staging` first; smoke-test the read-ACL with the
emulator suite before promoting to prod.

## Account deletion checklist

`deleteAccount` callable creates `/deletionRequests/{uid}` with each step
`pending`. `onUserDeleted` ticks each step in order; the scheduled
`reconcileDeletions` (hourly) resumes any request with pending steps older
than 24 h. Operator action is only required if a `deletionRequests/{uid}`
doc has been pending for more than ~26 h — at that point inspect the
checklist field, find the failing step, and re-trigger or hand-fix.
