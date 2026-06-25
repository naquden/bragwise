package se.atte.bragwise.crash

interface CrashReporter {
    fun recordException(throwable: Throwable)
    fun log(message: String)
    fun setCustomKey(key: String, value: String)
}

object NoopCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) {}
    override fun log(message: String) {}
    override fun setCustomKey(key: String, value: String) {}
}

expect fun createCrashReporter(): CrashReporter
