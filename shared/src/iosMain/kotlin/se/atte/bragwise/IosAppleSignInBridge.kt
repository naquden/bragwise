package se.atte.bragwise

import kotlinx.coroutines.suspendCancellableCoroutine
import se.atte.bragwise.data.AppleIdCredential
import se.atte.bragwise.data.AppleSignInCancelledException
import se.atte.bragwise.data.AppleSignInPresenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridge from Swift into Kotlin for presenting the native Apple sign-in
 * sheet. Mirrors [registerIosCrashReporter]'s shape: Swift registers a
 * closure at startup, Kotlin invokes it and suspends until the closure's
 * completion fires.
 *
 * The Apple sheet itself (ASAuthorizationController + CryptoKit nonce
 * hashing) has to live in Swift — it needs a UIKit presentation anchor,
 * same reason [IosPushBridge]'s `registerForRemoteNotifications` call does.
 */
private object IosAppleSignInHolder {
    var onPresent: ((completion: (AppleIdCredential?, String?) -> Unit) -> Unit)? = null
}

/**
 * Called once from `iOSApp.swift` after Koin starts. [onPresent] presents
 * the Apple sheet and invokes its own `completion` argument with either a
 * non-null [AppleIdCredential], or a null credential plus an error string
 * ("cancelled" for a user-dismissed sheet, anything else for a real failure).
 */
fun registerAppleSignInPresenter(
    onPresent: (completion: (AppleIdCredential?, String?) -> Unit) -> Unit,
) {
    IosAppleSignInHolder.onPresent = onPresent
}

class IosAppleSignInPresenter : AppleSignInPresenter {
    override suspend fun present(): AppleIdCredential = suspendCancellableCoroutine { cont ->
        val onPresent = IosAppleSignInHolder.onPresent
        if (onPresent == null) {
            cont.resumeWithException(IllegalStateException("apple sign-in presenter not registered"))
            return@suspendCancellableCoroutine
        }
        onPresent { credential, error ->
            if (!cont.isActive) return@onPresent
            when {
                credential != null -> cont.resume(credential)
                error == "cancelled" -> cont.resumeWithException(AppleSignInCancelledException())
                else -> cont.resumeWithException(IllegalStateException(error ?: "apple-sign-in-failed"))
            }
        }
    }
}
