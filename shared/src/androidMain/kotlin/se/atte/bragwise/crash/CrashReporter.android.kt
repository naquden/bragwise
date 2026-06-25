package se.atte.bragwise.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics

actual fun createCrashReporter(): CrashReporter = AndroidCrashReporter()

private class AndroidCrashReporter : CrashReporter {
    private val crashlytics get() = FirebaseCrashlytics.getInstance()

    override fun recordException(throwable: Throwable) = crashlytics.recordException(throwable)
    override fun log(message: String) = crashlytics.log(message)
    override fun setCustomKey(key: String, value: String) = crashlytics.setCustomKey(key, value)
}
