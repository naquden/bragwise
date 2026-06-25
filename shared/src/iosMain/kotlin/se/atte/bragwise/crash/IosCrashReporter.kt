package se.atte.bragwise.crash

internal object IosCrashReporter : CrashReporter {
    var onRecord: ((Throwable) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    var onKey: ((String, String) -> Unit)? = null

    override fun recordException(throwable: Throwable) { onRecord?.invoke(throwable) }
    override fun log(message: String) { onLog?.invoke(message) }
    override fun setCustomKey(key: String, value: String) { onKey?.invoke(key, value) }
}
