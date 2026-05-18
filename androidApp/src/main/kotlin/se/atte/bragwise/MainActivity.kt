package se.atte.bragwise

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.launch
import se.atte.bragwise.platform.AndroidPlatformShareHolder
import se.atte.bragwise.ui.nav.AppDeps

class MainActivity : ComponentActivity() {

    private lateinit var deps: AppDeps

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // App Check MUST be installed before any Firebase API that attaches
        // App Check tokens is used (Auth, Firestore, Functions). Auto-init
        // of FirebaseApp itself happens via the google-services plugin's
        // ContentProvider, which runs before onCreate, so Firebase.appCheck
        // is already available here.
        installAppCheck()

        // Touch Analytics once so the SDK initialises.
        @Suppress("UNUSED_VARIABLE")
        val analytics = Firebase.analytics

        // SharedPreferences-backed AuthLocalDataSource (and PlatformShare)
        // both fish the application Context out of this holder. Must be set
        // before the first Compose recomposition that constructs AppDeps.
        AndroidPlatformShareHolder.appContext = applicationContext

        deps = AppDeps.stub()
        handleAuthLink(intent)

        setContent {
            App(deps = deps)
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
     * authState to SignedIn — every active VM observing it (SignInVM,
     * FriendsVM, etc.) reacts automatically.
     */
    private fun handleAuthLink(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (!deps.auth.isSignInLink(data)) return
        lifecycleScope.launch { deps.auth.completeSignInWithLink(data) }
    }

    private fun installAppCheck() {
        val isDebuggable =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val factory = if (isDebuggable) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        Firebase.appCheck.installAppCheckProviderFactory(factory)
    }
}
