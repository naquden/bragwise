package se.atte.bragwise

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import se.atte.bragwise.BuildFlags
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.di.initKoin
import se.atte.bragwise.firebase.clearAuthQuery
import se.atte.bragwise.firebase.initFirebase
import se.atte.bragwise.firebase.windowHref
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.ui.nav.parseDeepLink

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Compose mounts into this `<div>` (see `wasmJsMain/resources/index.html`), not into
 * `<body>`. ComposeViewport clears its container on composition creation, and the
 * Firebase App Check reCAPTCHA v3 provider appends its hidden placeholder div to
 * `<body>`; mounting on `<body>` wiped that placeholder out from under
 * `grecaptcha.render()`, which then failed with "reCAPTCHA placeholder element must
 * be an element or id" and no App Check token was ever minted on web.
 */
private const val VIEWPORT_CONTAINER_ID = "composeApp"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    if (BuildFlags.USE_MOCK_DATA) {
        initKoin(useMock = true)
        ComposeViewport(VIEWPORT_CONTAINER_ID) { App() }
        return
    }

    // Firebase JS SDK must init before Koin constructs any Js*Remote (getAuth()/getFirestore()).
    initFirebase()
    initKoin()

    val koin = GlobalContext.get()
    val href = windowHref()

    // Email-link completion — web equivalent of Android MainActivity.handleDeepLink /
    // iOS IosAuthBridge. Firebase returns the user to window.location.origin with the
    // sign-in oobCode; finish the sign-in then strip the query so a reload doesn't retry.
    val auth = koin.get<AuthRepository>()
    if (auth.isSignInLink(href)) {
        appScope.launch {
            auth.completeSignInWithLink(href)
            clearAuthQuery()
        }
    } else {
        // Non-auth deep link (e.g. /c/{id}); AppNav collects incomingDeepLinks and routes.
        parseDeepLink(href)?.let { koin.get<PushNotifications>().seedDeepLink(href) }
    }

    ComposeViewport(VIEWPORT_CONTAINER_ID) {
        App()
    }
}
