package se.atte.bragwise.data

import kotlinx.coroutines.flow.Flow

interface AuthRemote {
    val currentUser: AuthUser?
    val authStateChanged: Flow<AuthUser?>

    suspend fun sendSignInLink(email: String)
    fun isSignInWithEmailLink(link: String): Boolean
    suspend fun signInAnonymously()
    suspend fun completeSignIn(email: String, link: String)
    suspend fun signInWithEmailLink(email: String, link: String)

    /**
     * Signs in (or, if the current user is anonymous, upgrades the guest via
     * `linkWithCredential`) with an Apple credential. Returns true iff this
     * was a brand-new Firebase account.
     */
    suspend fun signInWithApple(credential: AppleIdCredential): Boolean

    suspend fun signOut()
    suspend fun deleteAccount()
}
