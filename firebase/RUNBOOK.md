# Firebase Operator Runbook

Project: `bragwise` (single Firebase project — no separate staging/prod split in Phase 1).

## Console links

| Resource | URL |
|---|---|
| Overview | https://console.firebase.google.com/project/bragwise/overview |
| Firestore | https://console.firebase.google.com/project/bragwise/firestore |
| Authentication | https://console.firebase.google.com/project/bragwise/authentication/users |
| Cloud Functions | https://console.firebase.google.com/project/bragwise/functions |
| App Check | https://console.firebase.google.com/project/bragwise/appcheck |
| Hosting | https://console.firebase.google.com/project/bragwise/hosting |

## Deploy commands

```sh
# Firestore rules + indexes
firebase deploy --only firestore:rules,firestore:indexes

# Cloud Functions only
firebase deploy --only functions

# Firebase Hosting only (assetlinks.json + AASA + landing stub)
firebase deploy --only hosting

# Everything
firebase deploy
```

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
firebase deploy --only firestore:rules,firestore:indexes
```

Always smoke-test the read-ACL in the Rules Playground before deploying:
- `get /challenges/{any}` as unauthenticated → ALLOW (bearer-capability model)
- `set /players/{any}` as anyone → DENY

## App Check debug tokens

The first debug launch prints a UUID to Logcat:
```
D DebugAppCheckProvider: Enter this debug secret into the allowlist: <UUID>
```

Paste that UUID into:
**Console → App Check → `se.atte.bragwise` → ⋮ → Manage debug tokens → Add**

One token per development device/emulator. Tokens are NOT committed to the
repo and must be re-registered if the app is reinstalled.

## Add a release signing fingerprint

When a real release signing config is added to `androidApp/build.gradle.kts`:

```sh
./gradlew :androidApp:signingReport
# copy SHA-256 from "Variant: release"
```

Then:
1. Console → Project Settings → Your apps → Android → Add fingerprint (SHA-256).
2. Re-download `google-services.json` → replace `androidApp/google-services.json`.
3. Add the new SHA-256 to `firebase/public/.well-known/assetlinks.json`.
4. `firebase deploy --only hosting`.

## Account deletion checklist

`deleteAccount` callable creates `/deletionRequests/{uid}` with each step
`pending`. `onUserDeleted` ticks each step in order; the scheduled
`reconcileDeletions` (hourly) resumes any request with pending steps older
than 24 h. Operator action is only required if a `deletionRequests/{uid}`
doc has been pending for more than ~26 h — at that point inspect the
`steps` map, find the failing step, and re-trigger or hand-fix.

## Audit log retention (TTL)

Every privileged callable writes an `auditLog` doc via `audit()` in
`functions/src/lib/middleware.ts`. Each doc carries an `expireAt` timestamp set
to **90 days** after creation. Firestore TTL deletes expired docs automatically,
but the TTL policy is **not** managed by `firebase deploy` — enable it once per
project with `gcloud`:

```sh
gcloud firestore fields ttls update expireAt \
  --collection-group=auditLog \
  --enable-ttl \
  --project=bragwise
```

Verify / inspect:

```sh
gcloud firestore fields ttls list --project=bragwise
```

Re-run the enable command (with a new `bragwise-prod` `--project`) when the
staging/prod split lands. To change the retention window, edit `AUDIT_TTL_DAYS`
in `functions/src/lib/middleware.ts` — the TTL policy itself stays the same.

## Staging / production split

Phase 1 uses a single `bragwise` project. Before public launch:
1. Create a second Firebase project `bragwise-prod`.
2. Update `.firebaserc` with `"prod": "bragwise-prod"`.
3. Add a second `google-services.json` for the prod app registration.
4. Deploy to staging first (`firebase use bragwise`) then prod (`firebase use bragwise-prod`).
