package se.atte.bragwise.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.ActionCodeSettings
import dev.gitlive.firebase.auth.AndroidPackageName
import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Thin wrapper around `Firebase.auth`. Holds the `ActionCodeSettings` we
 * send with every email-link request (the URL the link points to, the
 * Android package name, etc.) so the repository doesn't have to know
 * about the host/package details.
 *
 * `Firebase.auth` is a multiplatform extension provided by GitLive — same
 * instance on Android and iOS — so there is no expect/actual split here.
 *
 * The `actionCodeSettings.url` MUST be on a Firebase Authorized Domain
 * (Console → Authentication → Settings → Authorized domains). For dev we
 * use the project's default Firebase Hosting subdomain
 * `bragwise.firebaseapp.com`; for prod we'll swap to `bragwise.app`.
 */
class AuthRemoteDataSource(
    private val auth: FirebaseAuth = Firebase.auth,
    private val actionCodeSettings: ActionCodeSettings = defaultActionCodeSettings(packageName = "se.atte.bragwise"),
    private val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION),
) : AuthRemote {
    override val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()
    override val authStateChanged: Flow<AuthUser?> get() = auth.authStateChanged.map { it?.toAuthUser() }

    override suspend fun sendSignInLink(email: String) {
        auth.sendSignInLinkToEmail(email = email, actionCodeSettings = actionCodeSettings)
    }

    override fun isSignInWithEmailLink(link: String): Boolean = auth.isSignInWithEmailLink(link)

    override suspend fun signInAnonymously() {
        auth.signInAnonymously()
    }

    /**
     * If the current user is anonymous, link the email credential to preserve
     * the uid (and all cloud predictions/scores). Falls back to a fresh sign-in
     * if linking fails (e.g. email already in use by another account).
     */
    override suspend fun completeSignIn(email: String, link: String) {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isAnonymous) {
            runCatching {
                val credential = EmailAuthProvider.credentialWithLink(email = email, emailLink = link)
                currentUser.linkWithCredential(credential = credential)
            }.onSuccess { return }
        }
        auth.signInWithEmailLink(email = email, link = link)
    }

    override suspend fun signInWithEmailLink(email: String, link: String) {
        auth.signInWithEmailLink(email = email, link = link)
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount() {
        mapErrors {
            functions.httpsCallable("deleteAccount")(emptyMap<String, Any?>())
        }
    }

    companion object {
        // See FirebaseConfig.APP_LINK_HOST — must stay in sync with the
        // hosted assetlinks.json / apple-app-site-association files and the
        // App Links intent-filter / Associated Domains entitlement.
        private const val EMAIL_LINK_RETURN_URL = "$APP_LINK_BASE_URL/auth/finish"

        fun defaultActionCodeSettings(packageName: String): ActionCodeSettings = ActionCodeSettings(
            url = EMAIL_LINK_RETURN_URL,
            canHandleCodeInApp = true,
            androidPackageName = AndroidPackageName(
                packageName = packageName,
                installIfNotAvailable = true,
                minimumVersion = null,
            ),
            // Must match iOS PRODUCT_BUNDLE_IDENTIFIER (Config.xcconfig:
            // se.atte.bragwise.Bragwise). Required so the Firebase email sign-in
            // link opens the iOS app via Universal Link.
            iOSBundleId = "se.atte.bragwise.Bragwise",
        )
    }
}

private fun dev.gitlive.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    email = email,
    isAnonymous = isAnonymous,
)
