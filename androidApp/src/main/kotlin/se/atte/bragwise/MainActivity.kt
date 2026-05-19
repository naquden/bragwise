package se.atte.bragwise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import se.atte.bragwise.data.AuthRepository

class MainActivity : ComponentActivity() {

    private val auth: AuthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAuthLink(intent)
        setContent { App() }
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
