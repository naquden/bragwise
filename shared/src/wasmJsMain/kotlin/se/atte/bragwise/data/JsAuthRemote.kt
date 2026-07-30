package se.atte.bragwise.data

import kotlin.js.JsAny
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import se.atte.bragwise.firebase.EmailAuthProvider
import se.atte.bragwise.firebase.deleteUser
import se.atte.bragwise.firebase.getAuth
import se.atte.bragwise.firebase.isSignInWithEmailLink
import se.atte.bragwise.firebase.linkWithCredential
import se.atte.bragwise.firebase.makeActionCodeSettings
import se.atte.bragwise.firebase.onAuthStateChanged
import se.atte.bragwise.firebase.sendSignInLinkToEmail
import se.atte.bragwise.firebase.signInAnonymously
import se.atte.bragwise.firebase.signInWithEmailLink
import se.atte.bragwise.firebase.signOut
import se.atte.bragwise.firebase.windowOrigin

class JsAuthRemote : AuthRemote {

    private val auth by lazy { getAuth() }

    override val currentUser: AuthUser?
        get() = auth.currentUser?.let { AuthUser(it.uid, it.email, it.isAnonymous) }

    override val authStateChanged: Flow<AuthUser?> = callbackFlow {
        val unsub = onAuthStateChanged(auth) { user ->
            trySend(user?.let { AuthUser(it.uid, it.email, it.isAnonymous) })
        }
        awaitClose { unsub() }
    }

    override suspend fun sendSignInLink(email: String) {
        sendSignInLinkToEmail(auth, email, makeActionCodeSettings(windowOrigin())).await<JsAny?>()
    }

    override fun isSignInWithEmailLink(link: String): Boolean =
        isSignInWithEmailLink(auth, link)

    override suspend fun signInAnonymously() {
        signInAnonymously(auth).await<JsAny?>()
    }

    override suspend fun completeSignIn(email: String, link: String) {
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            val linked = runCatching {
                val cred = EmailAuthProvider.credentialWithLink(email, link)
                linkWithCredential(current, cred).await<JsAny?>()
                Unit
            }
            if (linked.isSuccess) return
        }
        signInWithEmailLink(auth, email, link).await<JsAny?>()
    }

    override suspend fun signInWithEmailLink(email: String, link: String) {
        signInWithEmailLink(auth, email, link).await<JsAny?>()
    }

    override suspend fun signInWithApple(credential: AppleIdCredential): Boolean {
        throw UnsupportedOperationException("Sign in with Apple is iOS-only")
    }

    override suspend fun signOut() {
        signOut(auth).await<JsAny?>()
    }

    override suspend fun deleteAccount() {
        // TODO: replace with deleteAccount cloud function call (Functions interop — later step)
        // Interim: delete the Firebase Auth user directly
        auth.currentUser?.let { deleteUser(it).await<JsAny?>() }
    }
}
