package se.atte.bragwise.data

/**
 * Maps JS Firebase callable errors to [AppError].
 *
 * When a Cloud Function call rejects, the error message from the JS SDK typically
 * contains the code in the form "functions/already-exists" or just the code part.
 * We catch any Throwable from the awaited Promise and inspect its message.
 */
internal fun Throwable.toAppError(): AppError {
    val msg = message ?: ""
    val code = when {
        "already-exists" in msg -> AppErrorCode.AlreadyExists
        "not-found" in msg -> AppErrorCode.NotFound
        "invalid-argument" in msg -> AppErrorCode.InvalidArgument
        "permission-denied" in msg -> AppErrorCode.PermissionDenied
        else -> AppErrorCode.Unknown
    }
    return AppError(code = code, message = msg, cause = this)
}

internal suspend fun <T> mapErrors(block: suspend () -> T): T =
    try {
        block()
    } catch (e: AppError) {
        throw e
    } catch (e: Throwable) {
        throw e.toAppError()
    }
