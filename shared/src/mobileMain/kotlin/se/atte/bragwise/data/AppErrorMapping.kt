package se.atte.bragwise.data

import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.code

internal fun FirebaseFunctionsException.toAppError(): AppError {
    val mapped = when (code) {
        FunctionsExceptionCode.ALREADY_EXISTS -> AppErrorCode.AlreadyExists
        FunctionsExceptionCode.NOT_FOUND -> AppErrorCode.NotFound
        FunctionsExceptionCode.INVALID_ARGUMENT -> AppErrorCode.InvalidArgument
        FunctionsExceptionCode.PERMISSION_DENIED -> AppErrorCode.PermissionDenied
        else -> AppErrorCode.Unknown
    }
    return AppError(code = mapped, message = message, cause = this)
}

internal suspend fun <T> mapErrors(block: suspend () -> T): T =
    try { block() } catch (e: FirebaseFunctionsException) { throw e.toAppError() }
