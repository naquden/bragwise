package se.atte.bragwise

import se.atte.bragwise.crash.IosCrashReporter

/**
 * Called from Swift AppDelegate after FirebaseApp.configure() to wire native
 * Crashlytics callbacks into the shared CrashReporter. The closures are stored
 * on IosCrashReporter and invoked whenever ErrorReporter forwards a handled error.
 *
 * Kotlin Throwable arrives in Swift as KotlinThrowable — the onRecord lambda
 * converts it to an NSError before passing to Crashlytics.recordError().
 */
fun registerIosCrashReporter(
    onRecord: (Throwable) -> Unit,
    onLog: (String) -> Unit,
    onKey: (String, String) -> Unit,
) {
    IosCrashReporter.onRecord = onRecord
    IosCrashReporter.onLog = onLog
    IosCrashReporter.onKey = onKey
}
