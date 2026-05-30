package se.atte.bragwise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.push.PushNotifications

class MainActivity : ComponentActivity() {

    private val auth: AuthRepository by inject()
    private val push: PushNotifications by inject()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: user chose */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAuthLink(intent)
        handleDeepLink(intent)
        requestNotificationsOnFirstSignIn()
        fetchCurrentPushToken()
        setContent { App() }
    }

    /**
     * FCM's onNewToken only fires on install / token rotation — an
     * already-installed app on a normal launch never re-delivers. Proactively
     * fetch the current token so PushTokenRegistrar can (re)upload it after
     * sign-in. The registrar de-dupes via distinctUntilChanged.
     */
    private fun fetchCurrentPushToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (token != null) push.onNewToken(token)
        }
    }

    /**
     * POST_NOTIFICATIONS is only useful once signed in — guests never register
     * a push token and can't trigger any server-side notification. Defer the
     * Android 13+ runtime prompt until the first SignedIn transition, matching
     * the gate PushTokenRegistrar uses for token registration.
     */
    private fun requestNotificationsOnFirstSignIn() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            auth.authState
                .filterIsInstance<AuthState.SignedIn>()
                .take(1)
                .collect {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask launchMode: when the email link opens the app while we
        // are still alive, the intent comes through here.
        setIntent(intent)
        handleAuthLink(intent)
        handleDeepLink(intent)
    }

    /**
     * If the incoming intent carries a Firebase email sign-in link, hand it
     * to AuthRepository. The repository pulls the persisted email out of
     * AuthLocalDataSource, calls signInWithEmailLink, and Firebase flips
     * authState to SignedIn — every active VM observing it reacts automatically.
     */
    private fun handleAuthLink(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (!auth.isSignInLink(data)) return
        lifecycleScope.launch { auth.completeSignInWithLink(data) }
    }

    /**
     * Forwards a notification tap deep-link into the shared push flow.
     * BragwiseFirebaseMessagingService sets intent.data = Uri.parse(deepLink)
     * on the tap PendingIntent; we pick it up here and let AppNav navigate.
     * Only trusted hosts with a /c/{id} path are forwarded — all others ignored.
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "https") return
        if (uri.host !in TRUSTED_HOSTS) return
        val path = uri.path ?: return
        if (CHALLENGE_PATH_RE.matches(path)) {
            push.onIncomingDeepLink(uri.toString())
        }
    }

    companion object {
        private val TRUSTED_HOSTS = setOf("bragwise.firebaseapp.com", "bragwise.app")
        private val CHALLENGE_PATH_RE = Regex("^/c/[a-zA-Z0-9_-]+$")
    }
}
