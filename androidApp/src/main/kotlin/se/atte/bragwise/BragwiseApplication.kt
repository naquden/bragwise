package se.atte.bragwise

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.analytics
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level
import se.atte.bragwise.di.initKoin

class BragwiseApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.USE_MOCK_DATA) {
            // App Check MUST be installed before any Firebase API that attaches
            // App Check tokens (Auth, Firestore, Functions). Auto-init of FirebaseApp
            // itself happens via the google-services ContentProvider before onCreate.
            installAppCheck()

            // Touch Analytics once so the SDK initialises.
            @Suppress("UNUSED_VARIABLE")
            val analytics = Firebase.analytics
        }

        initKoin(useMock = BuildConfig.USE_MOCK_DATA) {
            androidLogger(if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) Level.DEBUG else Level.NONE)
            androidContext(this@BragwiseApplication)
        }
    }

    private fun installAppCheck() {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val factory = if (isDebuggable) {
            seedFixedDebugSecret()
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        Firebase.appCheck.installAppCheckProviderFactory(factory)
    }

    /**
     * Pre-seeds the App Check debug provider's stored secret with the fixed token from
     * `local.properties`. The debug provider reuses any secret already present in its
     * SharedPreferences instead of generating a fresh UUID, so one console-registered
     * token survives `pm clear`, reinstalls and emulator snapshot resets. No-op when no
     * fixed token is configured (falls back to the SDK's generated-and-stored UUID).
     */
    private fun seedFixedDebugSecret() {
        val fixedToken = BuildConfig.APP_CHECK_DEBUG_TOKEN
        if (fixedToken.isEmpty()) return

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        getSharedPreferences("com.google.firebase.appcheck.debug.store.$persistenceKey", Context.MODE_PRIVATE)
            .edit()
            .putString("com.google.firebase.appcheck.debug.API_KEY", fixedToken)
            .apply()
    }
}
