package se.atte.bragwise.data

/**
 * Target-neutral error raised by remote data sources so ViewModels in
 * commonMain don't depend on GitLive's FirebaseFunctionsException /
 * FirestoreExceptionCode. Mobile remotes map GitLive exceptions to this;
 * the web (wasmJs) remote maps Firebase JS SDK errors to this.
 *
 * [message] preserves the original server message (e.g. "handle-taken")
 * because some ViewModels still substring-match on it.
 */
class AppError(
    val code: AppErrorCode,
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class AppErrorCode {
    AlreadyExists,
    NotFound,
    InvalidArgument,
    PermissionDenied,
    Unknown,
}
