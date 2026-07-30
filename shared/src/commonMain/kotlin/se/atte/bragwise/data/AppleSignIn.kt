package se.atte.bragwise.data

/**
 * Credential handed back by the native Apple sign-in sheet. [fullName] is
 * populated by Apple only on the FIRST authorization for a given Apple ID —
 * later sign-ins return null there, which is why the caller must persist it
 * immediately rather than re-reading it on every sign-in.
 */
data class AppleIdCredential(
    val identityToken: String,
    val rawNonce: String,
    val fullName: String?,
    val email: String?,
)

/** Thrown when the user dismisses the native Apple sheet without authorizing. */
class AppleSignInCancelledException : Exception("apple-sign-in-cancelled")

/** Presents the native Apple sign-in sheet. Only implemented on iOS. */
interface AppleSignInPresenter {
    suspend fun present(): AppleIdCredential
}
