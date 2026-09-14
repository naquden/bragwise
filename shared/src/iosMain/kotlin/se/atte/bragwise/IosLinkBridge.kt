package se.atte.bragwise

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import se.atte.bragwise.data.APP_SCHEME_PREFIX
import se.atte.bragwise.push.PushNotifications
import se.atte.bragwise.ui.nav.parseDeepLink

/**
 * Bridge from Swift into Kotlin for every inbound URL the OS hands us:
 * Universal Links (`.onContinueUserActivity`), custom-scheme opens
 * (`.onOpenURL`) and notification taps (`UNUserNotificationCenterDelegate`).
 *
 * Mirrors Android's `MainActivity.handleAuthLink` + `handleDeepLink`, both of
 * which run from `onCreate` **and** `onNewIntent` — that symmetry is exactly
 * why Android already routes shared `/c/{id}` links correctly.
 *
 * Lives in its own file rather than `IosPushBridge.kt` because the generated
 * Obj-C class name derives from the file name, and this is no longer push-only.
 *
 * Koin is started in `iOSApp.init()` before any of these entry points can fire.
 */
private object IosLinkBridge : KoinComponent {
    val push: PushNotifications by inject()
}

/** Hosts we accept deep links for. Mirrors `MainActivity.TRUSTED_HOSTS`. */
private val TRUSTED_HOSTS = setOf("bragwise.firebaseapp.com", "bragwise.app")

/**
 * Tag for on-device verification logs, mirroring `MainActivity.TAG_VERIFY`
 * (androidApp). Ships in release on purpose: a physical iPhone supports neither
 * screenshots nor touch injection from the host, so reading these lines back out
 * of `xcrun devicectl device process launch --console` is the only way to prove
 * link routing on real hardware. `println` writes to stdout, which nothing reads
 * unless a console is attached.
 */
private const val TAG_VERIFY = "BRAGWISE_VERIFY link"

/**
 * Host + path only — **never** the query string. An `/auth/finish` URL carries a
 * single-use `oobCode` that grants sign-in, and device stdout is readable by
 * anything attached to the phone. Dropping the query keeps the log useful for
 * routing without turning it into a credential leak.
 */
private fun redactForLog(url: String): String = url.substringBefore('?').substringBefore('#')

/**
 * Single entry point for URLs delivered by SwiftUI's `.onContinueUserActivity`
 * and `.onOpenURL`.
 *
 * `bragwise://` URLs (emitted by the landing page's iOS CTA, because Safari will
 * not re-trigger a Universal Link for the domain that served the page) are
 * normalised to `https://` and dropped unless the host is trusted. Nothing stops
 * another app from also claiming the scheme, so that host check — plus
 * [parseDeepLink] — is the whole blast-radius cap: worst case the app opens and
 * nothing navigates.
 *
 * Sign-in is attempted first and deep-link routing second, matching
 * `MainActivity.onCreate` (androidApp) and `main()` (webApp). Both callees are
 * no-ops for URLs they don't own, so calling both unconditionally is safe.
 *
 * Deliberately does **no** de-duplication: `AppNav` navigates with
 * `launchSingleTop`, so a repeat is harmless, and dedup would swallow a
 * legitimate second tap of the same link.
 */
fun handleInboundUrlFromIos(url: String) {
    val httpsUrl = when {
        url.startsWith("https://") -> url
        url.startsWith(APP_SCHEME_PREFIX) -> {
            val normalised = "https://" + url.substring(APP_SCHEME_PREFIX.length)
            val host = hostOf(normalised)
            if (host == null || host !in TRUSTED_HOSTS) {
                println("$TAG_VERIFY: rejected untrusted scheme host=$host")
                return
            }
            normalised
        }
        else -> {
            println("$TAG_VERIFY: rejected unsupported scheme")
            return
        }
    }
    println("$TAG_VERIFY: inbound accepted ${redactForLog(httpsUrl)}")
    handleSignInLinkFromIos(httpsUrl)
    handleDeepLinkFromIos(httpsUrl)
}

/**
 * Deep-link routing: trusted https host plus a path the shared parser knows.
 * Called by [handleInboundUrlFromIos] and directly by the notification-tap
 * delegate, whose `deepLink` payload is already an https URL.
 *
 * Moved verbatim from `IosPushBridge.handlePushDeepLinkFromIos`.
 */
fun handleDeepLinkFromIos(url: String) {
    val host = hostOf(url)
    if (host == null || host !in TRUSTED_HOSTS) {
        println("$TAG_VERIFY: deepLink rejected untrusted host=$host")
        return
    }
    val link = parseDeepLink(url)
    if (link == null) {
        println("$TAG_VERIFY: deepLink unparseable ${redactForLog(url)}")
        return
    }
    println("$TAG_VERIFY: deepLink routed $link")
    IosLinkBridge.push.onIncomingDeepLink(url)
}

private fun hostOf(url: String): String? {
    if (!url.startsWith("https://")) return null
    val afterScheme = url.substring("https://".length)
    val end = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val host = if (end < 0) afterScheme else afterScheme.substring(0, end)
    return host.ifEmpty { null }
}
