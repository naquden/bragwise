---
name: bragwise-verify
description: |
  End-to-end verification of the Bragwise Android app on a real ADB device. Builds and installs the debug APK with Gradle, signs in via the Gmail magic-link path, walks every reachable screen, exercises every UI-creatable bet variant (Yes/No, Single pick), then verifies the Predict + Leaderboard flow. If any step fails, the agent pauses, captures screenshot + logcat, locates the offending Kotlin (or `functions/src/` TS) code, applies a minimal fix, rebuilds via `./gradlew :androidApp:installDebug`, and retries the failing scenario. Use when the user says "verify Bragwise end-to-end", "QA the app", "run the bragwise verification agent", "test all bet types", "install and test on device", or "verify challenges work".
---

# Bragwise Verify Agent

Autonomous, on-device verification of the Bragwise Android app. Builds on [adb-ui-agent](../adb-ui-agent/SKILL.md) for the underlying ADB observe → act → verify mechanics; this skill adds the Bragwise-specific catalog, the Gmail magic-link routine, the bet/challenge verification matrix, and the bug-pause + fix loop.

## REQUIRED preconditions

Before starting, confirm the following. If any item is missing, stop and ask the user:

1. Exactly one device or emulator is attached. If `adb devices` shows more than one, `ANDROID_SERIAL` must already be exported by the caller. **Do not pass `-s` flags manually** (see [adb-ui-agent](../adb-ui-agent/SKILL.md)).
2. The Gmail app (`com.google.android.gm`) is installed on the device and signed into a real Gmail account that owns the configured test email address.
3. The **test email address** is read from the repo-root `local.properties` file under the key `email`. Parse it with:
   ```bash
   grep -E '^[[:space:]]*email[[:space:]]*=' local.properties | head -n1 | sed -E 's/^[[:space:]]*email[[:space:]]*=[[:space:]]*//' | tr -d '"'\''[:space:]'
   ```
   If the key is missing or the value is empty, stop and ask the user to add a line like `email=tester@example.com` to `local.properties` (file is already gitignored). Never invent an address; never hard-code one in the skill output.
4. The repo builds: `./gradlew :androidApp:installDebug` succeeds against the current source tree.

## Package + activity

The Android `applicationId` is `se.atte.bragwise` for **both** debug and release ([`androidApp/build.gradle.kts:40`](../../../androidApp/build.gradle.kts)) — there is no `.debug` suffix. The launcher / deep-link activity is `se.atte.bragwise.MainActivity` ([`AndroidManifest.xml:13–16`](../../../androidApp/src/main/AndroidManifest.xml)).

| Command | Use |
|---|---|
| `./gradlew :androidApp:installDebug` | Build + install the debug APK |
| `adb shell am start -n se.atte.bragwise/.MainActivity` | Launch the app |
| `adb shell am force-stop se.atte.bragwise` | Stop the app |
| `adb shell pm clear se.atte.bragwise` | Wipe app data (clean run) |
| `adb shell pidof se.atte.bragwise` | Get PID for the logcat watcher |

Note: the existing generic [adb-ui-agent](../adb-ui-agent/SKILL.md) skill mentions `se.atte.bragwise.debug` in a few places — that suffix does not exist in this codebase. Use the names above.

## Setup phase

Run once per verification session, in order:

1. `adb devices` → confirm a single device, capture serial if needed.
2. `./gradlew :androidApp:installDebug` → fresh APK installed.
3. (Optional, for a fully clean run) `adb shell pm clear se.atte.bragwise`.
4. `adb shell am start -n se.atte.bragwise/.MainActivity` → app launches.
5. Sleep 2 s, then start the logcat watcher per [adb-ui-agent → Logcat — Background Watcher](../adb-ui-agent/SKILL.md):
   ```bash
   adb logcat -c && adb logcat --pid=$(adb shell pidof se.atte.bragwise) -v time > /tmp/ast-logcat.txt
   ```
   Run this with `block_until_ms: 0` so it backgrounds.
6. First screenshot for evidence → see **Screenshots** below; Read the resulting PNG to confirm the Sign In screen is showing.

## Screenshots — capture and pull

Two equivalent patterns. Use whichever fits the moment, then **Read** the resulting PNG with the file Read tool (it supports images) to analyse the UI.

**Always downscale to keep context cheap.** Every screenshot you Read becomes an image attachment in the conversation — at full 1080×2400 a PNG is ~1.5 MB, which burns context fast and forces earlier `/compact` calls. Resample to a max long-side of **600 px** using macOS' built-in `sips` (no extra install). This drops the PNG to ~80–120 KB while still keeping screen identity, button states, error dialogs, and snackbars legible. Touch coordinates are unaffected — `input tap` math is based on the device's real resolution (`adb shell wm size`), not the screenshot.

The default cap is `-Z 600`. Use the higher cap below only for failure evidence where you may zoom in to triage later.

### Method A — stream straight to the host (preferred, no on-device file)

```bash
adb exec-out screencap -p > /tmp/ast-screenshot.png \
  && sips -Z 600 /tmp/ast-screenshot.png --out /tmp/ast-screenshot.png > /dev/null
```

One command, no cleanup required. Use this for the standard observe step in the [adb-ui-agent](../adb-ui-agent/SKILL.md) loop.

### Method B — capture on device, then pull (use when you want a kept artifact)

```bash
ts=$(date +%Y%m%d-%H%M%S)
adb shell screencap -p /sdcard/bragwise-verify-$ts.png
adb pull /sdcard/bragwise-verify-$ts.png /tmp/bragwise-verify-$ts.png
adb shell rm /sdcard/bragwise-verify-$ts.png
sips -Z 600 /tmp/bragwise-verify-$ts.png --out /tmp/bragwise-verify-$ts.png > /dev/null
```

Use Method B when:

- You want a timestamped file you'll reference in the final report (e.g. on a failed scenario).
- You want to capture multiple frames in quick succession without overwriting `/tmp/ast-screenshot.png`.
- An action is mid-flight and you want to capture without piping through `exec-out`.

For failure evidence, prefer `/tmp/ast-screenshot-fail-<scenario-id>-<attempt>.png` (Method B with a deterministic name) so the final report can link to specific frames.

### Failure-evidence exception — keep more detail

Inside the **bug-pause + fix loop**, swap the cap up so post-mortem inspection is possible:

```bash
sips -Z 1080 /tmp/ast-screenshot-fail-<scenario-id>-<attempt>.png \
  --out /tmp/ast-screenshot-fail-<scenario-id>-<attempt>.png > /dev/null
```

(Or skip `sips` entirely for full resolution.) Use this only on the failed frame, not on every screenshot during the retry attempt.

### Reading screenshots

After saving, always **Read** the PNG immediately and describe what you see (current screen, error dialogs, snackbars, button states). Cross-reference with a `uiautomator dump` if you also need tap coordinates — the dump is faster than a screenshot for coordinate lookup (see [adb-ui-agent → Prefer hierarchy dumps over screenshots](../adb-ui-agent/SKILL.md)).

### Screenshot budget per scenario

To keep the run within context limits between `/compact` calls, cap to **~3–5 screenshots per scenario** at the canonical checkpoints — not after every tap. Use `uiautomator dump` for everything in between. Canonical per-scenario checkpoints:

- After arriving on the target start screen.
- After the main action (publish, submit, sign in, etc.).
- After landing on the post-action destination.
- One failure capture if anything trips (Method B + the failure-evidence cap).

## Screen catalog

All anchor strings below come directly from the current source. Use them in `text=`/`content-desc=` lookups on `uiautomator dump`.

### Sign In ([`SignInScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/auth/SignInScreen.kt))

| State | Anchors |
|---|---|
| Always | `Bragwise`, `Predict. Compete. Brag.`, `Continue as guest` |
| EnterEmail (default) | `Sign in with email`, field label `Email`, button `Send sign-in link` / `Sending…`, helper `We'll email you a link. Tap it on this device to finish signing in.` |
| CheckYourInbox (after send) | `Check your inbox`, `We sent a sign-in link to:`, shown `<sentTo>`, `Tap the link on this device to finish signing in.`, `Use a different email`, `Resend` |

### Reconcile Friends ([`ReconcileFriendsScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/onboarding/ReconcileFriendsScreen.kt))

Appears after sign-in only when local-only friends exist. Anchors: `Match your local friends to real accounts`, helper `We'll send a friend request to each handle you fill in. Leave blank to keep that friend local.`, field label `@handle (optional)`, buttons `Skip` and `Send requests` / `Sending…`.

For verification runs the agent always taps `Skip` unless the test specifically targets reconcile.

### Tabs + chrome ([`AppNav.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/nav/AppNav.kt))

Bottom nav: tabs `Challenges` (icon `🎯`) and `Me` (icon `👤`). FAB: text `+`. Top bar when off-tabs: `Back`.

### Challenges tab ([`ChallengesScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/challenges/ChallengesScreen.kt))

- Empty: `No challenges yet`, `Create one to start predicting with friends.`, button `Create your first challenge`.
- Ready: section headers `My Challenges`, `Promoted`, `From friends`, `Invites` (only sections with entries render).

### Me tab ([`MeScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/me/MeScreen.kt))

- Guest: header `Guest`, helper `Sign up to save your run, join friends, and appear on leaderboards.`, button `Sign in or sign up`.
- Signed in: header is the player's displayName, subtitle `@<handle>`, and a `Sign out` text button at the bottom.
- Always: list rows `Friends` (leading `👥`) and `Settings` (leading `⚙`).

### Friends ([`FriendsScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/friends/FriendsScreen.kt))

- Empty: `No friends yet`, mode-dependent helper, no list.
- Ready: card titles `Friends (N)` (cloud) and `Local (N)` (local-only).
- Bottom bar: `Reconcile` (outlined) + primary `Add friend` (guest) / `Add by handle` (signed in).
- Local row tap → alert dialog `Local friend` / `Edit or remove this local friend.` with `Edit` and `Remove`.

### Local friend editor ([`LocalFriendEditorScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/friends/LocalFriendEditorScreen.kt))

Card title `Add a local friend` (add mode) or `Edit friend` (edit mode). Fields `Display name`, `Avatar seed`. Bottom bar `Cancel` + `Save` (disabled when name blank). Edit mode has a `Remove friend` text button mid-screen.

### Create Challenge (2-step wizard, [`CreateChallengeScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/create/CreateChallengeScreen.kt))

**Step 1 — Metadata**

- Card titles `Details` and `Who can join`.
- Field label `Title`.
- Visibility chips `Friends` and `Invite only`; helper paragraph swaps between `Auto-invites all your current and future friends.` and `Only people you explicitly invite can join.`
- Primary `Continue to bets` (disabled while `title` is blank).

**Step 2 — Bets**

- Card title `Bets (N)` where N = current bet count; empty-state copy `Add at least one bet to publish.`
- Per-bet row: bet title (body text), kind line from one of:
  - `Yes / No`
  - `Single pick · N options`
  - `Ranking · top N`
  - and a `Remove` text button on the right.
- Add-bet card title `Add bet`, type chips `Yes / No` and `Single pick`, field label `Question`.
- Single-pick only: rows labeled `Option 1`, `Option 2`, …, each with a close icon (contentDescription `Remove option`, enabled only when `options.size > 2`), and a `+ Add option` text button.
- `+ Add bet` outlined button — enabled only when question is non-blank AND (Yes/No OR at least 2 trimmed non-empty options).
- Footer row: `Save draft` (outlined, always enabled while not submitting) and `Publish` (filled, enabled only when at least one bet exists and not submitting).

### Challenge Detail ([`ChallengeDetailScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/detail/ChallengeDetailScreen.kt))

- Title row: challenge title (headline), two-column readout `Your rank` (`RankChip` if joined, otherwise `—— / <count>`) and `Bets` (count).
- Bets list under header `Bets` — each row has a leading index (`1`, `2`, …), bet title, and subtitle from the kind label.
- Action row: `Leaderboard` + `Share` (both outlined).
- Bottom primary: `Make predictions` (not joined) or `Edit predictions` (joined).

### Predict ([`PredictScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/predict/PredictScreen.kt))

One card per bet (title = bet title). Per-bet interaction:

- `Bet.BooleanProp` → chips `Yes` and `No`.
- `Bet.SinglePick` with `OptionType.NONE` → chip per option labeled with the option's `label`.
- `Bet.SinglePick` with `OptionType.COUNTRY` → vertical rows of `flag + label + > / ✓` (selected shows `✓`).
- `Bet.Ranking` → drag-reorder list, rows show `#1`, `#2`, …, label, handle `≡`.

Bottom primary cycles through: `Submitting…` (when submitting), `Predict <drafts>/<bets>` (incomplete), `Save predictions` (complete and enabled).

Loading / failure / empty states render `No bets` for `UiState.Empty`.

### Leaderboard ([`LeaderboardScreen.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/leaderboard/LeaderboardScreen.kt))

Tabs `All` and `Friends` (shown only when `showTabs` is true). Empty state: `No leaderboard yet`. Ready: rows of rank chip + `displayName` + points pill.

## Gmail magic-link sub-routine

Bragwise sign-in is Firebase Auth's email-link flow. The action settings put the **continue URL** at `https://bragwise.firebaseapp.com/auth/finish` and `canHandleCodeInApp = true` with Android `packageName = "se.atte.bragwise"` ([`AuthRemoteDataSource.kt:50–60`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/AuthRemoteDataSource.kt), [`FirebaseConfig.kt:21–25`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/FirebaseConfig.kt)). The manifest verifies App Links for host `bragwise.firebaseapp.com` ([`AndroidManifest.xml:32–38`](../../../androidApp/src/main/AndroidManifest.xml)).

### Step-by-step

1. **In Bragwise — request the link.**
   - From `Sign In`, tap field `Email`, type the test email read from `local.properties` (`adb shell input text` — escape `@` as `%40`, `.` is fine).
   - Tap `Send sign-in link`.
   - Wait ~2 s, dump hierarchy, confirm `Check your inbox` is present and `<sentTo>` text equals the email entered. If `Sending…` is still showing after 8 s, sleep + redump; if not progressing after 15 s, treat as a failure and enter the bug-pause loop (look at the snackbar effect — note `SignInScreen` does not host snackbars yet ([line 53](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/auth/SignInScreen.kt))).
2. **Switch to Gmail.**
   - Primary: `adb shell monkey -p com.google.android.gm -c android.intent.category.LAUNCHER 1`.
   - Fallback: `adb shell am start -n com.google.android.gm/.ConversationListActivityGmail` (activity name varies by Gmail version; if it errors, retry with `monkey`).
   - Sleep 3 s; if Gmail opens to a non-inbox view (Search, Compose drawer, etc.) press back (`adb shell input keyevent KEYCODE_BACK`) until the inbox conversation list is visible.
3. **Find the latest Bragwise email.**
   - The Firebase Auth default sender is usually `noreply@<project>.firebaseapp.com`. Sender display name often contains the project ID. Email body always contains the link host `bragwise.firebaseapp.com`.
   - Strategy: dump hierarchy. Look for the topmost (first) `LinearLayout` / `ViewGroup` that has child text containing `Sign in`, `sign-in`, `Bragwise`, `bragwise`, or `noreply`. Parse its bounds, tap centre.
   - If the inbox needs a refresh, swipe down to refresh: `adb shell input swipe 540 600 540 1600 400` then wait 3 s and redump.
   - If no Bragwise email shows up within ~30 s of repeated polling (max 6 redumps with `sleep 5` between), stop and report `BLOCKED — sign-in email not received`. Do not enter the bug-pause loop; this is an environmental issue.
4. **Tap the sign-in link inside the open email.**
   - Dump hierarchy. The link appears as either:
     - A clickable button-like row whose text is `Sign in` or includes the action verb, **or**
     - A `TextView` whose text starts with `https://` and contains `bragwise.firebaseapp.com` (Firebase action URLs follow `https://<auth-domain>/__/auth/action?...` or `https://bragwise.firebaseapp.com/auth/finish?...`).
   - Tap the first such element. If Gmail shows a "Verify it's you" intermediate page, tap the affirmative option (`Open` / `Continue`).
5. **Confirm Bragwise resumed and signed in.**
   - Sleep 3 s.
   - The PID will change (singleTask + new intent path). Restart the logcat watcher against the new PID per the [adb-ui-agent](../adb-ui-agent/SKILL.md) guidance.
   - Grep `/tmp/ast-logcat.txt` for the tag `BRAGWISE_SIGNIN_DBG_cf7943` to confirm both `MainActivity.onNewIntent` (or `onCreate` if cold) and `completeSignInWithLink.result success=true`. These logs come from [`MainActivity.kt:79–92`](../../../androidApp/src/main/kotlin/se/atte/bragwise/MainActivity.kt) and [`AuthRepository.kt:96–107`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/AuthRepository.kt).
   - Take a screenshot. Expect either the `Challenges` tab or the `Reconcile Friends` screen. If the latter, tap `Skip`.

## Context management — `/compact` between scenarios

A full verification matrix run produces a lot of tool output (hierarchy dumps are large, logcat tails accumulate, every screenshot pulls a fresh image into context). Long runs **will** hit the conversation context limit if left unchecked, which terminates the agent mid-scenario and loses the run state. To prevent that, run the CLI command `/compact` at deterministic checkpoints — it summarises the existing conversation in place and keeps the task going from where you left off.

### When to run `/compact`

- **After every 5 screenshots Read since the last compaction** — see *Screenshot-Read counter* below. This is the hard ceiling and overrides every other rule.
- **After each `S*` scenario completes** (regardless of PASS/FAIL), before starting the next one. Inside `S4` specifically, run it after each of `C1`, `C2`, `C3`, `C4`, `C5`.
- **Immediately after a bug-pause loop iteration finishes** (whether the fix worked or you're moving to the next attempt), but **never mid-iteration** — you need the raw logcat + hierarchy in scope while diagnosing.
- **Before the final report** if the run produced more than ~5 scenarios of output.

Skip `/compact` when:

- You are mid-action inside one scenario (e.g. between taps within `S2`). Tool output across a single scenario is needed to reason about its outcome — **exception**: the 5-screenshot ceiling still fires; if you hit it mid-scenario, finish the current discrete sub-step (e.g. complete the current tap + verify), then compact.
- The very next step is `/compact` again from a prior checkpoint (don't double-compact).

### Screenshot-Read counter

Maintain a running tally of **screenshots Read since the last `/compact`** (whether captured via Method A, Method B, or as failure evidence). Increment on every `Read` call against a `.png` under `/tmp/`. When the counter reaches **5**, immediately:

1. Finish any in-flight discrete sub-step (the tap-and-verify pair you're currently on — do not stop mid-pair).
2. Emit a user-visible line: `[bragwise-verify] screenshot-read counter hit 5 — compacting`.
3. Run `/compact` with the preserve-instruction template below.
4. Reset the counter to 0 and **continue the run from exactly where you left off** without asking the user. Do not re-do the last action.

Rationale: a 600-px PNG attached via Read is the single largest non-text payload the agent ingests during a verification run. Capping the read-count at 5 between compactions keeps context predictable regardless of how chatty individual scenarios are. Combined with the per-scenario 3–5 screenshot budget ([Screenshots → Screenshot budget per scenario](#screenshot-budget-per-scenario)), a typical scenario fits in exactly one compaction cycle.

### What to preserve when running `/compact`

When invoking, pass a brief instruction so the summary keeps the run state intact. Example:

```
/compact Keep: (1) the bragwise-verify run state — list of completed scenarios with PASS/FAIL/BLOCKED, evidence file paths under /tmp/, fixes applied so far with file:line; (2) device serial and email from local.properties; (3) the next scenario to run and any pending bug-pause attempt counter; (4) all entries that will go into the final report. Drop: raw hierarchy XML dumps, full logcat bodies, screenshot byte content, intermediate tool outputs from completed scenarios.
```

After `/compact` returns, immediately resume from the next scenario in the matrix without asking for permission — the user invoked the verification agent expecting a full run.

### Carrying state across compactions

Maintain a running checklist in your assistant messages (visible to the user) so the post-compact state is recoverable from the chat alone, not just from the compacted summary. After every completed scenario, emit a one-line update like:

```
[bragwise-verify] S2 PASS · sign-in via Gmail · evidence /tmp/bragwise-verify-s2-04-signed-in.png · next: S3
```

This way even if a compaction over-summarises, the user-visible trail still shows exactly where the run stands.

## Verification matrix

Run sequentially. After each scenario, take a screenshot to `/tmp/ast-screenshot-<scenario-id>.png` and record PASS/FAIL.

**After every `S*` scenario (and after each `C*` inside S4), run `/compact` as described above before proceeding to the next one.**

### S0 — App boot

1. Launch app, observe `Sign In` screen.
2. Verify all anchors from `Sign In > EnterEmail`.

### S1 — Guest mode tour

1. Tap `Continue as guest` → expect `Challenges` tab.
2. Verify empty state (`No challenges yet`, `Create your first challenge`).
3. Tap `Me` tab → expect `Guest` header.
4. Tap `Friends` row → expect `Friends` screen, empty state.
5. Tap bottom `Add friend` → expect `Add a local friend` editor.
6. Fill `Display name` = `Test Friend A`, tap `Save` → expect to return to `Friends` with `Local (1)` card.
7. Tap the local row → expect `Local friend` alert dialog.
8. Tap `Edit` → expect editor in edit mode (`Edit friend` title, `Remove friend` button present).
9. Tap `Cancel` → back to `Friends`.
10. Press back via `adb shell input keyevent KEYCODE_BACK` until at tabs; tap `Challenges` tab.

### S2 — Email sign-in via Gmail (validates App Check + Auth + App Links)

This scenario validates the full passwordless sign-in path end-to-end. It exercises Firebase **App Check** (debug provider on debuggable builds), **Firebase Auth** (email-link), and **Android App Links** (auto-verified host `bragwise.firebaseapp.com`).

**Pre-condition:** the device must be on the `Sign In` screen (`OB-02`). If not, navigate there first: `Me` tab → `Sign in or sign up` ([`MeScreen.kt:97–102`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/me/MeScreen.kt)). For a fully clean run, `adb shell pm clear se.atte.bragwise` followed by `am start ...` will land on Sign In directly.

**Steps**

1. Take a Method B screenshot tagged `s2-01-signin.png`. Confirm anchors `Bragwise`, `Predict. Compete. Brag.`, field label `Email`, button `Send sign-in link`, `Continue as guest`.
2. Tap the `Email` field, then `adb shell input text` the address from `local.properties` (escape `@` as `%40`).
3. Tap `Send sign-in link`. Wait until the label flips from `Sending…` back to anything else (max 15 s).
4. Take screenshot `s2-02-inbox-prompt.png`. Confirm anchors `Check your inbox`, the literal email under `We sent a sign-in link to:`, plus `Use a different email` and `Resend`.
5. Run the **Gmail magic-link sub-routine** above to open Gmail, find the newest Bragwise / Firebase sign-in email, and tap the link.
6. Wait 3 s. Take screenshot `s2-03-after-link.png`.
7. **Logcat assertions** — read `/tmp/ast-logcat.txt` (re-arm against the new PID first if it changed, per [adb-ui-agent](../adb-ui-agent/SKILL.md)) and confirm **all** of the following are true:
   - `Grep` for `BRAGWISE_SIGNIN_DBG_cf7943` returns at least one line whose text is `handleAuthLink.enter linkPresent=true isSignInLink=true` ([`MainActivity.kt:79–92`](../../../androidApp/src/main/kotlin/se/atte/bragwise/MainActivity.kt)).
   - `Grep` for `completeSignInWithLink.result success=true` returns at least one line ([`AuthRepository.kt:96–107`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/AuthRepository.kt)).
   - `Grep` for `signInWithEmailLink` in lines tagged `FirebaseAuth` shows a success-style entry (no `Exception`, no `error`).
   - **Negative assertion:** `Grep -i "app-check-required|app_check_required|appCheckRequired"` returns **zero** matches. Any match means App Check rejected the request; treat as FAIL and enter the bug-pause loop, looking first at [`MainActivity.kt:94–103`](../../../androidApp/src/main/kotlin/se/atte/bragwise/MainActivity.kt) (`installAppCheck` debug provider wiring).
   - **Negative assertion:** `Grep -E " E |AndroidRuntime"` shows no `FirebaseAuth` or `BRAGWISE_SIGNIN_DBG_cf7943` errors during the link window.
8. **Routing assertion** — the app must have left `OB-02`. Dump UI hierarchy and confirm anchors `Bragwise` (the headline on `SignIn`) and `Continue as guest` are no longer present. Acceptable destinations:
   - `Reconcile Friends` (if S1 created a local friend) — anchors `Match your local friends to real accounts`, `Skip`. Tap `Skip` then continue.
   - `Challenges` tab (no pending reconcile) — anchor `🎯` icon + `No challenges yet` or a populated list.
9. Take screenshot `s2-04-signed-in.png`.
10. Open the `Me` tab. Confirm the header is **no longer** `Guest`; instead it shows the player's displayName and `@<handle>` ([`MeScreen.kt:104–109`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/me/MeScreen.kt)). Confirm a `Sign out` text button is now visible at the bottom (only shown when `player != null`, [`MeScreen.kt:130–137`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/me/MeScreen.kt)).

**PASS criteria (all must hold):** all anchor checks pass, all logcat positive assertions pass, both negative assertions pass, and the routing assertion passes.

**FAIL escalation:** for App-Check failures the fix usually lives in `installAppCheck` ([`MainActivity.kt:94–103`](../../../androidApp/src/main/kotlin/se/atte/bragwise/MainActivity.kt)) or the Firebase console debug-token registration (out-of-band — surface to the user). For Auth `signInWithEmailLink` errors, start at [`AuthRepository.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/AuthRepository.kt) and [`AuthRemoteDataSource.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/AuthRemoteDataSource.kt). For routing failures (`OB-02` still showing after success log), inspect the `Effect.SignedIn` collect in [`SignInScreen.kt:50–55`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/auth/SignInScreen.kt) and the `onSignedIn` branch in [`AppNav.kt:159–177`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/nav/AppNav.kt).

### S3 — Create validation (negative checks)

From `Challenges` tab, tap the `+` FAB → expect `Create Challenge` step 1.

1. Title left blank. Dump hierarchy. Confirm `Continue to bets` button has `enabled="false"` (or visually inactive). Do **not** tap.
2. Type a title (e.g. `Validation Probe`); confirm button enables; tap it → step 2 `Bets (0)`.
3. Leave `Question` blank, type chip `Yes / No`. Confirm `+ Add bet` is disabled.
4. Switch to `Single pick`. Type `Question` = `Pick one`. Leave `Option 1` filled with `A`, `Option 2` blank. Confirm `+ Add bet` is disabled (fewer than 2 non-empty options).
5. Type `Option 2` = `B`. Confirm `+ Add bet` enables.
6. With zero bets persisted, attempt `Publish` — confirm it is disabled (`state.bets.isEmpty()`).
7. Tap top-bar `Back` to return to tabs.

### S4 — Happy paths (each runs Predict + Leaderboard)

For each case below: from `Challenges`, tap `+`, fill `Title`, choose visibility, tap `Continue to bets`, add the listed bets via the `Add bet` card, then tap `Publish`. After publish the agent is routed to `Challenge Detail`. Tap `Make predictions`, set drafts as listed, tap `Save predictions` (label flips through `Predict X/Y` → `Save predictions`). Return to detail, tap `Leaderboard`, verify the screen renders (entries may be empty if only one player joined — `No leaderboard yet` is an acceptable PASS for solo runs).

| ID | Visibility | Bets | Predictions |
|---|---|---|---|
| C1 | `Friends` | 1 × Yes/No: `Will it rain tomorrow?` | Yes |
| C2 | `Friends` | 1 × Single pick (2 opts): `Pick a color` → `Red`, `Blue` | `Blue` |
| C3 | `Invite only` | 1 × Single pick (4 opts): `Favourite season` → `Spring`, `Summer`, `Autumn`, `Winter` | `Autumn` |
| C4 | `Friends` | 1 × Yes/No: `Will I win?` AND 1 × Single pick (3 opts): `Pick a fruit` → `Apple`, `Banana`, `Cherry` | `No` + `Cherry` |
| C5 | `Friends` | 1 × Yes/No: `Save-draft probe` | (skip Publish — tap `Save draft` instead) → return to `Challenges`, verify the new draft appears under `My Challenges` and that opening it shows the same bet |

### Known gaps (record in the final report, do not attempt)

The Create UI currently exposes only `Yes / No` and `Single pick (NONE)` ([`CreateChallengeScreen.kt:188–201`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/create/CreateChallengeScreen.kt)). The following exist in the domain + Predict but are **not creatable from the UI**:

- `Bet.Ranking` ([`Bet.kt:25–31`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/domain/Bet.kt)) — `CreateChallengeViewModel` dispatches `AddRanking`, but the Create screen never calls it.
- `Bet.SinglePick` with `OptionType.COUNTRY` ([`Bet.kt:11`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/domain/Bet.kt)).

Per user direction, these are out of scope — record as `BLOCKED: not creatable from UI` in the report.

## Bug-pause + fix loop

When a step fails (assertion didn't match, app crashed, screen got stuck, log indicates an error), enter this protocol.

### 1. Capture

- Screenshot to a scenario-tagged path: `/tmp/ast-screenshot-fail-<scenario-id>-<attempt>.png`. Use the **failure-evidence cap** (`sips -Z 1080`) per [Screenshots → Failure-evidence exception](#failure-evidence-exception--keep-more-detail) so post-mortem inspection is possible.
- `adb shell uiautomator dump /sdcard/ui_dump.xml && adb shell cat /sdcard/ui_dump.xml` saved to `/tmp/ast-ui-dump-fail-<scenario-id>-<attempt>.xml`.
- Snapshot the watcher tail with `wc -l /tmp/ast-logcat.txt` so subsequent reads can be diff'd.
- Run `Grep` over `/tmp/ast-logcat.txt` for:
  - ` E ` (errors)
  - `AndroidRuntime` (crashes)
  - `BRAGWISE_` (project debug tags — currently `BRAGWISE_SIGNIN_DBG_cf7943` is the only one)
  - exact scenario keywords (e.g. the title used in C2)

### 2. Diagnose

Cross-reference log evidence with the source. Start narrow — `Grep` for the specific log message string before reading whole files. Useful entry points:

| Symptom | Look at |
|---|---|
| Sign-in link sent but never completes after Gmail tap | [`MainActivity.kt:79–92`](../../../androidApp/src/main/kotlin/se/atte/bragwise/MainActivity.kt), [`AuthRepository.kt:96–107`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/AuthRepository.kt), [`AuthRemoteDataSource.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/data/AuthRemoteDataSource.kt) |
| `Send sign-in link` errors / snackbar effect | [`SignInViewModel.kt`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/auth/SignInViewModel.kt) (snackbar effect emitted but `SignInScreen` doesn't host one yet — line 53) |
| Create button disabled when it shouldn't be | [`CreateChallengeScreen.kt:251–268`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/create/CreateChallengeScreen.kt) — confirm validation rules |
| Publish fails / snackbar `Title and at least one bet required` | [`CreateChallengeViewModel.kt:115–120`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/create/CreateChallengeViewModel.kt), backend Zod schema [`functions/src/schemas.ts`](../../../functions/src/schemas.ts) |
| Predict submit blocked / wrong label | [`PredictScreen.kt:115–127`](../../../shared/src/commonMain/kotlin/se/atte/bragwise/ui/screens/predict/PredictScreen.kt) (requires `drafts.size == bets.size`) |
| Backend `auth-required` after sign-in | [`functions/src/lib/middleware.ts:12–16`](../../../functions/src/lib/middleware.ts); confirm `Firebase.auth.currentUser` in `BRAGWISE_SIGNIN_DBG_cf7943 completeSignInWithLink.result` log line |

### 3. Apply a minimal fix

- Prefer the smallest surgical change. Respect the always-applied workspace rules (Kotlin code style — named arguments when > 1 param, full names, etc.) and the `compose-ui` rule (use `Common.kt` composables where available — though Bragwise mostly uses `components/AppButton`, `SectionCard`, `BottomActionBar`).
- For Kotlin: edit the relevant file under `shared/` or `androidApp/`.
- For server callables (Firebase Functions): edit `functions/src/`. **Functions changes require `firebase deploy --only functions`** which is out of band — the skill stops at this point and asks the user to deploy before retrying. Don't try to deploy autonomously.

### 4. Rebuild + reinstall

```bash
./gradlew :androidApp:installDebug
```

If the build fails, fix the build error first (treat it as a new failure within this same attempt — do not advance the attempt counter).

### 5. Relaunch + re-arm watcher

```bash
adb shell am force-stop se.atte.bragwise
adb shell am start -n se.atte.bragwise/.MainActivity
sleep 2
adb logcat -c && adb logcat --pid=$(adb shell pidof se.atte.bragwise) -v time > /tmp/ast-logcat.txt
```

(background the logcat command with `block_until_ms: 0`.)

### 6. Retry the failing scenario only

Replay the scenario steps verbatim from where they begin (most scenarios start either from the `Challenges` tab or fresh launch — get there with `adb shell input keyevent KEYCODE_BACK` loops or `am start`).

### 7. Escalate after MAX_FIX_ATTEMPTS = 3

Per scenario, allow at most **three** capture → diagnose → fix → reinstall → retry cycles. After the third failed retry:

- Stop the matrix.
- Produce the final report (next section) with the failure prominently called out.
- Do not proceed to later scenarios — the user should review before continuing.

### 8. Compact after each fix iteration

Bug-pause iterations are the heaviest single sources of context bloat in a verification run (each one pulls in the failed screenshot, the full hierarchy dump, a logcat tail, and the source files you read to diagnose). After **each completed iteration** — i.e. once the retry has either passed or you've decided to start the next attempt — run `/compact` per the rules in [Context management — /compact between scenarios](#context-management--compact-between-scenarios). Preserve the attempt counter, the file:line of the fix you applied (if any), and the failing scenario id so the next iteration starts with full task awareness. Never compact mid-iteration.

## Final report format

When the matrix finishes (either all scenarios complete or one escalated), emit a single report to the user with this structure:

```
Bragwise verify — <timestamp>

Device: <serial> (<model>)
Email used: <value from local.properties#email>
Build: ./gradlew :androidApp:installDebug (<count> times during run)

Scenario results
  S0  PASS  App boot
  S1  PASS  Guest mode tour
  S2  PASS  Email sign-in via Gmail
  S3  PASS  Create validation
  S4  C1    PASS  Friends / Yes-No
  S4  C2    FAIL  Friends / Single pick 2
       evidence: /tmp/ast-screenshot-fail-c2-3.png
       fix applied: <none | one-line summary + file:line>
  S4  C3    BLOCKED — preceding fix not deployed
  ...
  Known gaps:
    - Bet.Ranking — not creatable in UI today (Bet.kt:25-31)
    - Bet.SinglePick(OptionType.COUNTRY) — not creatable in UI today (Bet.kt:11)

Fixes applied during this run
  1. <file:line> — <one-line why>
  2. ...

Outstanding issues
  - <bug>: <symptom> → <hypothesis> → <next step>
```

## Important reminders

- Always sleep 1–2 s after a navigation tap before redumping hierarchy or screenshotting (Crossfade transitions in `AppNav` use a 220 ms tween).
- Use hierarchy dumps for tap targeting; only screenshot at checkpoints and on failure (per [adb-ui-agent → Prefer hierarchy dumps over screenshots](../adb-ui-agent/SKILL.md)).
- Never edit the `adb-ui-agent` skill from inside this skill — it is the shared foundation.
- Never commit code changes during a verification run unless the user explicitly asks. The bug-pause loop applies the fix to the working tree only.
- If a question-form request from the user appears mid-run, defer fixes and answer it (per the workspace `no-code-changes-on-questions` rule).
