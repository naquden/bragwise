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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState

class MainActivity : ComponentActivity() {

    private val auth: AuthRepository by inject()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: user chose */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAuthLink(intent)
        requestNotificationsOnFirstSignIn()
        setContent { App() }
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
}
