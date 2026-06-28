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
    suspend fun signOut()
    suspend fun deleteAccount()
}
