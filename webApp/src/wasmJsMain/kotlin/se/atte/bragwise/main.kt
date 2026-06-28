package se.atte.bragwise

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.di.initKoin
import se.atte.bragwise.firebase.clearAuthQuery
import se.atte.bragwise.firebase.initFirebase
import se.atte.bragwise.firebase.windowHref
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.ui.nav.parseDeepLink

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
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

    ComposeViewport(document.body!!) {
        App()
    }
}
