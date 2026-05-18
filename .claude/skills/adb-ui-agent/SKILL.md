---
name: adb-ui-agent
description: |
  Agentic UI testing and verification for Android apps using ADB shell commands, screenshots, and logcat. The agent autonomously navigates the app by taking screenshots, analyzing them, reading the UI hierarchy, and executing touch/key inputs via ADB. Use when the user asks to "test the app", "navigate the app", "verify UI", "take a screenshot", "check the screen", "run the app", "adb agent", "UI agent", or any task involving autonomous Android app interaction and visual verification.
---

# ADB UI Agent

Autonomous Android app navigation, testing, and verification using ADB.

## REQUIRED: Always verify bug fixes on a real ADB device

**After every bug fix, you MUST verify the fix on a connected ADB device before declaring the task done.**

Compile success alone is NOT sufficient verification. A bug fix is only complete when the corrected behaviour has been confirmed visually on device.

### Mandatory steps after any bug fix

1. Run `adb devices` — confirm a device is attached.
2. `./gradlew :androidApp:installDebug` — build and install the fixed APK.
3. Use the core ADB loop (observe → act → verify) to reproduce the original bug scenario on device.
4. Take a screenshot as evidence and confirm the bug no longer occurs.
5. Include the device verification result in the fix summary you present to the user.

If no device is connected, state this explicitly and ask the user to connect one before closing the task.

---

## Tooling Preference: Built-in First

Always prefer built-in `adb` commands and standard Unix utilities (`grep`, `sed`, `awk`, `wc`, `sleep`, `cat`, `ls`, etc.) to accomplish verification tasks. Do not reach for external package managers or CLI downloads — if something can be done with the tools already available on the system, use those instead.

---

## Device Targeting: Honour `ANDROID_SERIAL`

It is normal to have multiple ADB devices connected at once (e.g. an emulator and a physical
phone). Export `ANDROID_SERIAL` so all subsequent `adb` commands target it implicitly.

**Rules for this skill:**
- **Do not** add `-s <serial>` arguments to `adb` commands manually. Rely on `ANDROID_SERIAL`
  being set by the caller.
- If `ANDROID_SERIAL` is not set and `adb devices` shows more than one device, stop and
  ask which serial to target before running anything.

---

## Core Loop

Follow this loop for every interaction cycle:

```
1. OBSERVE  → capture screenshot + UI hierarchy
2. ANALYZE  → read screenshot (vision) + parse hierarchy XML for coordinates
3. DECIDE   → determine next action based on goal
4. ACT      → execute adb input command
5. VERIFY   → screenshot again to confirm result
6. REPEAT   → until goal is achieved or failure detected
```

**Always start with OBSERVE.** Never act blindly.

**Logcat**: Do NOT proactively check logcat after every action. Only read logcat when:
- The user explicitly asks to check logs or verify a log message
- Something went wrong (crash, unexpected screen, error dialog) and you need to debug

---

## ADB Commands Reference

### Observation

```bash
# Screenshot — save locally, then read with the Read tool (supports images)
adb exec-out screencap -p > /tmp/ast-screenshot.png

# UI hierarchy — XML with resource-ids, text, bounds coordinates
adb shell uiautomator dump /sdcard/ui_dump.xml
adb shell cat /sdcard/ui_dump.xml

# Current activity / fragment
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"

# Clear logcat buffer (do this before an action to get clean logs after)
adb logcat -c
```

### Input Actions

```bash
# Tap at coordinates (x, y)
adb shell input tap 360 640

# Long press (tap with duration)
adb shell input swipe 360 640 360 640 1000

# Swipe from (x1,y1) to (x2,y2) over duration_ms
adb shell input swipe 360 900 360 300 300

# Type text (no spaces — use %s for space)
adb shell input text "hello"
adb shell input text "hello%sworld"

# Key events
adb shell input keyevent KEYCODE_BACK          # Back button
adb shell input keyevent KEYCODE_HOME          # Home button
adb shell input keyevent KEYCODE_ENTER         # Enter/confirm
adb shell input keyevent KEYCODE_DEL           # Backspace
adb shell input keyevent KEYCODE_TAB           # Tab to next field
adb shell input keyevent KEYCODE_DPAD_DOWN     # Navigate down
adb shell input keyevent KEYCODE_DPAD_UP       # Navigate up
adb shell input keyevent KEYCODE_MENU          # Menu key
adb shell input keyevent KEYCODE_ESCAPE        # Escape
adb shell input keyevent 111                   # Escape (numeric)
```

### App Management

```bash
# Build and install debug APK to connected device/emulator
./gradlew :androidApp:installDebug

# Launch app (debug and release share applicationId — no .debug suffix)
adb shell am start -n se.atte.bragwise/se.atte.bragwise.MainActivity

# Force stop
adb shell am force-stop se.atte.bragwise

# Clear app data
adb shell pm clear se.atte.bragwise

# Check if app is installed
adb shell pm list packages | grep bragwise
```

---

## Parsing UI Hierarchy for Coordinates

The `uiautomator dump` XML contains nodes like:

```xml
<node index="0" text="Settings" resource-id="com.example:id/settings_btn"
  class="android.widget.Button" bounds="[48,1100][672,1200]" />
```

**Extracting tap coordinates from bounds:**
- `bounds="[left,top][right,bottom]"` → tap center: `x = (left+right)/2`, `y = (top+bottom)/2`
- Example: `bounds="[48,1100][672,1200]"` → tap at `(360, 1150)`

**Strategy:**
1. Dump hierarchy first
2. Find the target element by `text`, `resource-id`, or `content-desc`
3. Parse bounds to get center coordinates
4. Execute `adb shell input tap x y`

Use the UI hierarchy as the **primary** coordinate source. Fall back to screenshot-based visual estimation only for custom views or canvas elements without accessibility nodes.

### Prefer hierarchy dumps over screenshots for element location

**Do NOT take a screenshot just to confirm an element is present before tapping it.** Screenshots are slow (capture + read adds 2–3 seconds per cycle). Use a hierarchy dump instead — it's faster and gives exact coordinates directly.

| Goal | Use |
|---|---|
| Locate an element to tap | `uiautomator dump` + parse bounds |
| Verify visual state / report to user | screenshot + Read |
| Check for crash / error dialog | screenshot + Read |
| Confirm navigation succeeded | screenshot + Read (or hierarchy dump if checking activity title) |

**Pattern for tapping an element after an action (e.g. menu opened):**

```bash
# Short sleep, then dump hierarchy immediately — no intermediate screenshot needed
sleep 0.5 && adb shell uiautomator dump /sdcard/ui_dump.xml && adb shell cat /sdcard/ui_dump.xml
# Parse the target element's bounds, then tap
adb shell input tap <x> <y>
```

Only take a screenshot at key reporting checkpoints (app launched, sign-in complete, final state verification), not between every navigation step.

---

## Screenshot Analysis Guidelines

When analyzing a screenshot with the Read tool:

1. **Identify the current screen** — what screen/route is showing?
2. **List visible UI elements** — buttons, text fields, labels, lists, navigation
3. **Check for error states** — error dialogs, snackbars, empty states, crash screens
4. **Note element positions** — approximate quadrant and relative position
5. **Cross-reference with UI hierarchy** — confirm coordinates before tapping

When reporting results, describe what you see concretely: "The screen shows a list of 3 challenges. The first is 'World Cup 2026' with status 'Open'. There is a floating action button in the bottom-right corner."

---

## Logcat — Background Watcher

**REQUIRED**: At the start of every agent session, start a background logcat watcher filtered to the Bragwise package. This captures all app logs continuously so you can search them at any point.

### Start the watcher

Run this with `block_until_ms: 0` so it backgrounds immediately:

```bash
adb logcat -c && adb logcat --pid=$(adb shell pidof se.atte.bragwise) -v time > /tmp/ast-logcat.txt
```

If the app isn't running yet (pidof returns empty), start the watcher after launching the app.

If the PID changes (app restarted / crashed and relaunched), restart the watcher.

### Read logs

Read the logcat output file to inspect logs at any time:

```bash
# Read the full log file
Read /tmp/ast-logcat.txt

# Or read just the tail for recent output
Read /tmp/ast-logcat.txt (with offset: -100)
```

Use **Grep** on `/tmp/ast-logcat.txt` to search for specific log messages:

```bash
# Search for a specific tag or message
Grep pattern="SomeTag" path="/tmp/ast-logcat.txt"

# Search for errors
Grep pattern=" E " path="/tmp/ast-logcat.txt"

# Search for a specific log message the user asks about
Grep pattern="the exact log message" path="/tmp/ast-logcat.txt"
```

### Snapshot logs around an action

To capture logs for a specific action:

```bash
# Note the current line count before the action
wc -l /tmp/ast-logcat.txt

# ... perform the action ...
# ... wait ...

# Read only the new lines added since the action
Read /tmp/ast-logcat.txt (with offset from the previous line count)
```

### Fallback: one-shot logcat (if watcher not running)

```bash
# Get PID of the running app
adb shell pidof se.atte.bragwise

# Dump recent logs for that PID
adb logcat -d --pid=<pid> -t 200
```

### What to look for

- `E/` lines — errors, especially `AndroidRuntime` for crashes
- `W/` lines — warnings, potential issues
- **Timber tags** — the app uses Timber for logging; look for class/function names as tags
- Network errors, timeout messages, stack traces
- **Specific messages** — when the user asks "verify that X was logged", grep for the exact message

---

## Error Recovery

If something goes wrong:

| Problem | Recovery |
|---|---|
| App crashed | Check `adb logcat -d -t 100 -s AndroidRuntime:E`, restart with `am start` |
| Wrong screen | Press back: `adb shell input keyevent KEYCODE_BACK` |
| Dialog blocking | Try BACK key or tap outside dialog area |
| App not responding | `adb shell am force-stop se.atte.bragwise`, then relaunch |
| Keyboard covering UI | `adb shell input keyevent KEYCODE_BACK` to dismiss |
| Screen is off | `adb shell input keyevent KEYCODE_WAKEUP` |
| Device locked | `adb shell input keyevent KEYCODE_MENU` then swipe up |

---

## Test Reporting

After completing a test scenario, report:

1. **Result**: PASS / FAIL / BLOCKED
2. **Steps executed**: numbered list of what was done
3. **Evidence**: key screenshots and logcat excerpts
4. **Issues found**: any bugs, UI problems, or unexpected behavior
5. **Logcat summary**: errors or warnings observed

---

## Bragwise Sign-In Procedure

The app uses passwordless email-link sign-in. The sign-in screen shows "Bragwise" title, "Predict. Compete. Brag." subtitle, an email input field, a "Send sign-in link" button, and a "Continue as guest" option.

### Sign in as guest (for quick UI testing)

```
1. Launch the app
   → adb shell am start -n se.atte.bragwise/se.atte.bragwise.MainActivity

2. Wait for screen, take screenshot to confirm state
   → sleep 2 && adb exec-out screencap -p > /tmp/ast-screenshot.png

3. If sign-in screen appears, tap "Continue as guest"
   → Dump hierarchy, find text "Continue as guest", tap it

4. Verify main app loads (Challenges tab visible)
   → sleep 1 && adb exec-out screencap -p > /tmp/ast-screenshot.png
```

### Key UI identifiers

| Element | Identifier |
|---|---|
| Email input | label text: "Email" |
| Send link button | text: "Send sign-in link" |
| Guest button | text: "Continue as guest" |
| Challenges tab | text: "Challenges" (bottom nav) |
| Me tab | text: "Me" (bottom nav) |
| Create FAB | text: "+" (floating action button) |

---

## Example: Complete Test Flow

User says: "Verify the challenges screen loads correctly"

```
Agent execution:

1. Clear logcat
   → adb logcat -c

2. Launch app
   → adb shell am start -n se.atte.bragwise/se.atte.bragwise.MainActivity

3. Wait 2 seconds, take screenshot
   → sleep 2
   → adb exec-out screencap -p > /tmp/ast-screenshot.png
   → Read /tmp/ast-screenshot.png → analyze

4. Continue as guest if needed
   → Dump hierarchy, tap "Continue as guest"

5. Get UI hierarchy
   → adb shell uiautomator dump /sdcard/ui_dump.xml
   → adb shell cat /sdcard/ui_dump.xml → parse elements

6. Take screenshot, analyze
   → verify challenges screen visible with expected content

7. Check logcat
   → adb logcat -d -t 100 → scan for errors

8. Report results
```

---

## Important Notes

- **Always sleep 1-2 seconds** after navigation actions before taking screenshots (animations, loading)
- **Use `shell` block_until_ms=0** for `sleep` commands, or chain: `sleep 2 && adb exec-out screencap -p > /tmp/ast-screenshot.png`
- **Screenshot path**: always use `/tmp/ast-screenshot.png` (overwrite each time to avoid clutter)
- **Screen dimensions**: check with `adb shell wm size` if coordinate math seems off
- **The Read tool supports PNG images** — use it directly to analyze screenshots
