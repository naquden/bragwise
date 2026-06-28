# Bragwise Web (`:webApp`)

Kotlin/Wasm + Compose Multiplatform build of the Bragwise app. Shares
`commonMain` (UI, ViewModels, domain, repositories) with Android/iOS; the
Firebase data layer is implemented per-platform (GitLive on mobile, Firebase
JS SDK v11 on web).

## Run locally

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # http://localhost:8080
```

`localhost` is an authorised Firebase Auth domain by default, so email-link
sign-in works in dev.

## Build a production bundle

```bash
./gradlew :webApp:wasmJsBrowserDistribution
# output: webApp/build/dist/wasmJs/productionExecutable/  (webApp.js + .wasm + index.html)
```

## Architecture (how web reaches feature parity)

- `commonMain` is Firebase-free: data sources sit behind interfaces
  (`AuthRemote`, `ChallengeRemote`, `ProfileRemote`, `SocialRemote`) and errors
  surface as the neutral `AppError`.
- Mobile (`mobileMain`) binds the GitLive-backed `*RemoteDataSource`.
- Web (`wasmJsMain`) binds `Js*Remote` implemented via the Firebase JS SDK:
  - `firebase/auth` — email-link sign-in (anonymous→link upgrade mirrored from mobile)
  - `firebase/firestore` — live `onSnapshot` reads bridged to `Flow` (same query
    topology as mobile), decoded via JSON round-trip into the shared domain types
  - `firebase/functions` — all callables (createChallenge, submitPredictions,
    postResults, friend ops, profile ops, …) → server, so writes are visible to
    other users exactly as on mobile
  - `firebase/analytics` — GA4 `logEvent`, every event tagged `platform: "web"`
- Guest cache (predictions/drafts/seen results) uses `localStorage` on web
  (SQLite on mobile); it migrates to the server via `migrateGuestData` on sign-in.

## ⚠️ Before deploying / before live Firebase works — needs human input

1. **Web Firebase config** (`shared/src/wasmJsMain/.../firebase/FirebaseConfigJs.kt`):
   `apiKey` / `appId` / `measurementId` are **web-app-specific** and were carried
   from `temp/wasmjs.md` — they are NOT verified against a Firebase console
   web-app registration. Register (or open) the Web app in the Firebase console
   (project `bragwise`) and paste the real `apiKey`, `appId`, and `measurementId`.
2. **GA4 analytics** only initialises when `MEASUREMENT_ID` (same file) is set to
   the real `G-XXXXXXXX`. While empty, analytics calls are silent no-ops.
3. **Hosting** — the existing `firebase.json` hosting serves the mobile
   app-links landing (`/c/**`, `/u/**`, `/auth/finish` → `landing` function) and
   must NOT be repurposed. Deploy the web app to a **separate Hosting site**:
   ```bash
   # one-time: create a Hosting site in the console, then map a target
   firebase target:apply hosting web <your-web-site-id>
   ./gradlew :webApp:wasmJsBrowserDistribution
   firebase deploy --only hosting:web --config firebase.web.json
   ```
   Then add the web site's domain to Firebase console → Authentication →
   Settings → Authorized domains so email-link sign-in works in prod.
