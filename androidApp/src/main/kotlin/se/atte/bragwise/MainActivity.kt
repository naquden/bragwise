package se.atte.bragwise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.ui.nav.parseDeepLink
import se.atte.bragwise.verify.VerifyAutomation

class MainActivity : ComponentActivity() {

    private val auth: AuthRepository by inject()
    private val push: PushNotifications by inject()
    private val challenges: ChallengeRepository by inject()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: user chose */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAuthLink(intent = intent)
        handleDeepLink(intent = intent)
        handleVerifyIntent(intent = intent)
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
        handleAuthLink(intent = intent)
        handleDeepLink(intent = intent)
        handleVerifyIntent(intent = intent)
    }

    /**
     * Debug-only verification entry point for agents / CI:
     * `adb shell am start -n se.atte.bragwise/.MainActivity --es verify eurovision_ranking`
     *
     * Seeds a published country-ranking challenge and opens the Predict screen.
     */
    private fun handleVerifyIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val scenario = intent?.getStringExtra(EXTRA_VERIFY) ?: return
        intent.removeExtra(EXTRA_VERIFY)
        if (scenario != VERIFY_EUROVISION_RANKING) return
        lifecycleScope.launch {
            val signedIn = withTimeoutOrNull(timeMillis = 15_000) {
                auth.authState.filterIsInstance<AuthState.SignedIn>().first()
            }
            if (signedIn == null) {
                Log.e(TAG_VERIFY, "eurovision_ranking: not signed in within 15s — sign in first")
                return@launch
            }
            VerifyAutomation.seedEurovisionRankingChallenge(challenges = challenges)
                .onSuccess { challengeId ->
                    Log.i(TAG_VERIFY, "eurovision_ranking: seeded challengeId=$challengeId")
                    VerifyAutomation.requestOpenPredict(challengeId = challengeId)
                }
                .onFailure { error ->
                    Log.e(TAG_VERIFY, "eurovision_ranking: seed failed", error)
                }
        }
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
     * Only trusted hosts are forwarded — the shared parser decides which paths are valid.
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "https") return
        if (uri.host !in TRUSTED_HOSTS) return
        if (parseDeepLink(uri.toString()) != null) {
            push.onIncomingDeepLink(uri.toString())
        }
    }

    companion object {
        private const val TAG_VERIFY = "BRAGWISE_VERIFY"
        const val EXTRA_VERIFY = "verify"
        const val VERIFY_EUROVISION_RANKING = "eurovision_ranking"

        private val TRUSTED_HOSTS = setOf("bragwise.firebaseapp.com", "bragwise.app")
    }
}
