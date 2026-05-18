---
name: compose-screenshot
description: Take screenshots of Compose composables or screens via an instrumented androidTest. Use when asked to screenshot composables, capture UI states, or visualize Compose layouts.
---

# Compose UI Screenshot Capture

Takes screenshots of one or more Compose composables/screens via an instrumented `androidTest` on a connected emulator or device. Saves PNGs to `temp/screenshots/<subfolder>/` in the project root. The test file is **temporary** — it should not be committed.

---

## What you need from the user

If the user has not specified what to screenshot, ask:
1. **What to screenshot** — composable name(s), screen(s), or the states to capture.
2. **Background** — what theme wrapper to use (`ThemePreview`, `MaterialTheme`, bare `Surface`).

If the user already gave a composable/screen, start immediately without asking.

---

## Step 0 — check prerequisites

```bash
adb devices
```

If no device/emulator is listed, tell the user and stop. Tests require a connected device.

---

## Step 1 — locate the composable

Grep for the composable or screen name in `shared/src/commonMain/kotlin/`. Read the file to understand:
- Parameters it takes
- Any state it needs (`UiState`, `Step`, strings, etc.)
- Which theme wrapper the rest of the project uses (default: `ThemePreview` from `se.atte.bragwise.theme`)

---

## Step 2 — add `ComponentActivity` to the androidTest manifest

File: `androidApp/src/androidTest/AndroidManifest.xml`

Check if it already has `androidx.activity.ComponentActivity` declared. If not, add it:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="androidx.activity.ComponentActivity"
            android:exported="false" />
    </application>
</manifest>
```

If the file doesn't exist, create it with this content. This is required for `createAndroidComposeRule<ComponentActivity>()` — it hosts the composable without a real Activity, so you don't need to replicate full app setup.

---

## Step 3 — write the test file

Create the test file at:
`androidApp/src/androidTest/java/se/atte/bragwise/<feature>/<Name>ScreenshotTest.kt`

Use **the same package** as the composable being tested (or a nearby feature package).

### Test file template

```kotlin
package se.atte.bragwise.<feature>

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import se.atte.bragwise.theme.ThemePreview
import org.junit.Rule
import org.junit.Test
import java.io.File

class <Name>ScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun screenshotDir(subfolder: String): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "screenshots/$subfolder",
        ).also { it.mkdirs() }

    private fun takeScreenshot(dir: File, name: String) {
        composeTestRule.waitForIdle()
        Thread.sleep(300)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /**
     * Renders all frames in a single setContent block to avoid "already set
     * content" crashes. Advances via a hidden "next" button with testTag.
     */
    private fun captureSequence(
        dir: File,
        frames: List<Pair<String, @Composable () -> Unit>>,
    ) {
        composeTestRule.setContent {
            var frameIndex by remember { mutableIntStateOf(0) }
            ThemePreview {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        frames[frameIndex].second()
                        TextButton(
                            onClick = { if (frameIndex < frames.lastIndex) frameIndex++ },
                            modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("next"),
                        ) { Text("") }
                    }
                }
            }
        }

        frames.forEachIndexed { i, (name, _) ->
            takeScreenshot(dir, name)
            if (i < frames.lastIndex)
                composeTestRule.onNodeWithTag("next").performClick()
        }
    }

    @Test
    fun <composable>_screenshots() {
        val dir = screenshotDir("<feature>")

        val frames = listOf<Pair<String, @Composable () -> Unit>>(
            "state_one" to { /* render composable with state 1 */ },
            "state_two" to { /* render composable with state 2 */ },
        )

        captureSequence(dir, frames)
    }
}
```

### Key rules for the test file

- **One `setContent {}` per test** — multiple calls throw `IllegalStateException: already set content`. The `captureSequence` helper solves this by driving frames via state + button click.
- **`targetContext.filesDir`** for the screenshot dir — this is the *target app's* private storage, which the test runner creates. `context.filesDir` is the test APK's dir and is never created.
- **`uiAutomation.takeScreenshot()`** instead of `UiDevice.takeScreenshot()` — avoids Android 14 scoped storage `EPERM` errors. Returns a `Bitmap` directly; save it with `Bitmap.compress`.
- **`Thread.sleep(300)`** before capture — lets enter/exit animations and progress indicators settle.
- **Wrap in `ThemePreview`** (the project's theme wrapper from `se.atte.bragwise.theme`) so Material3 colors resolve correctly.
- **Background**: use `containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)` on the Scaffold to get a real-looking backdrop behind dialogs.

---

## Step 4 — build and install

```bash
./gradlew :androidApp:installDebug :androidApp:installDebugAndroidTest
```

Don't use `connectedDebugAndroidTest` — it uninstalls afterwards. The separate install-then-run approach keeps the APK (and its screenshots) on disk after the tests complete.

---

## Step 5 — run the test

```bash
adb shell am instrument -w \
  -e class se.atte.bragwise.<feature>.<Name>ScreenshotTest \
  se.atte.bragwise.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Watch for `OK (N tests)` at the end. If a test fails, read the logcat output.

---

## Step 6 — pull the screenshots

List what's on the device first:

```bash
adb shell "run-as se.atte.bragwise.debug find files/screenshots -name '*.png'"
```

Then pull using `adb exec-out` (binary-safe — `adb shell cat` corrupts PNGs):

```bash
adb shell "run-as se.atte.bragwise.debug find files/screenshots -name '*.png'" \
  | while read f; do
      name=$(basename "$f")
      folder=$(dirname "$f" | sed 's|files/screenshots/||')
      mkdir -p "/path/to/project/temp/screenshots/$folder"
      adb exec-out "run-as se.atte.bragwise.debug cat $f" \
        > "/path/to/project/temp/screenshots/$folder/$name"
    done
echo "Done"
```

Replace `/path/to/project` with the actual project root (use `pwd` or the known path).

Verify the files arrived and have non-zero size:

```bash
find temp/screenshots -name "*.png" | sort | while read f; do
  echo "$(wc -c < "$f")  $f"
done
```

---

## Step 7 — clean up

**Delete the test file** after pulling. It should not be committed.

```bash
rm androidApp/src/androidTest/java/se/atte/bragwise/<feature>/<Name>ScreenshotTest.kt
```

Leave `temp/screenshots/` alone — those are the deliverable.

Also revert the `AndroidManifest.xml` if you added `ComponentActivity` and it wasn't there before.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `IllegalStateException: already set content` | Multiple `setContent {}` calls in one test | Use `captureSequence` — one `setContent`, state-driven frames |
| `EPERM` writing to `/sdcard/` | Android 14 scoped storage | Use `targetContext.filesDir` + `uiAutomation.takeScreenshot()` |
| `ENOENT` on `context.filesDir` | Using the test APK's context | Use `targetContext` not `context` |
| PNG files are 0 bytes or corrupt | `adb shell cat` corrupts binary | Use `adb exec-out run-as ... cat` |
| `run-as` fails: "not debuggable" | Release APK installed | Install the **debug** variant |
| `adb pull` returns 0 files | Internal storage not accessible to adb without run-as | Use the `run-as ... find | while read` loop above |
| Test passes but no files on device | Test wrote to wrong path | Confirm `targetContext.filesDir`, check `mkdirs()` |
| `ComponentActivity` not found | Missing androidTest manifest | Add `AndroidManifest.xml` as shown in Step 2 |
| Compose animation still running at capture | Not waiting long enough | Increase `Thread.sleep` or call `waitForIdle()` again |
