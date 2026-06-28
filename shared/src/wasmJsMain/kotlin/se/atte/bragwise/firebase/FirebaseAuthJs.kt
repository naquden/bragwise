@file:JsModule("firebase/auth")
package se.atte.bragwise.firebase
import kotlin.js.JsAny
import kotlin.js.Promise

external interface JsAuth : JsAny { val currentUser: JsFirebaseUser? }
external interface JsFirebaseUser : JsAny {
    val uid: String
    val email: String?
    val isAnonymous: Boolean
}
external interface JsUserCredential : JsAny
external interface JsAuthCredential : JsAny

external fun getAuth(): JsAuth
external fun sendSignInLinkToEmail(auth: JsAuth, email: String, actionCodeSettings: JsAny): Promise<JsAny?>
external fun isSignInWithEmailLink(auth: JsAuth, link: String): Boolean
external fun signInWithEmailLink(auth: JsAuth, email: String, link: String): Promise<JsAny?>
external fun signInAnonymously(auth: JsAuth): Promise<JsAny?>
external fun signOut(auth: JsAuth): Promise<JsAny?>
external fun onAuthStateChanged(auth: JsAuth, callback: (JsFirebaseUser?) -> Unit): () -> Unit
external fun linkWithCredential(user: JsFirebaseUser, credential: JsAuthCredential): Promise<JsAny?>
external fun deleteUser(user: JsFirebaseUser): Promise<JsAny?>

external interface EmailAuthProviderStatic : JsAny {
    fun credentialWithLink(email: String, emailLink: String): JsAuthCredential
}
external val EmailAuthProvider: EmailAuthProviderStatic
