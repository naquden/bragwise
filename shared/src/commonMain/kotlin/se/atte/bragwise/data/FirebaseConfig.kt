package se.atte.bragwise.data

/**
 * Project-wide Firebase deploy constants. Single source of truth — when
 * migrating from the dev Hosting subdomain to the custom `bragwise.app`
 * domain, change [APP_LINK_HOST] here and re-deploy hosting + functions.
 *
 * Backend mirrors:
 *   - [FUNCTIONS_REGION] is set on the backend via `setGlobalOptions` at
 *     the top of `functions/src/index.ts`. KEEP IN SYNC.
 *   - [APP_LINK_HOST] is reflected in:
 *       * androidApp/src/main/AndroidManifest.xml (intent-filter host)
 *       * iosApp/iosApp/iosApp.entitlements (Associated Domains)
 *       * AuthRemoteDataSource.EMAIL_LINK_RETURN_URL
 *       * firebase/public/.well-known/{assetlinks.json,apple-app-site-association}
 *     because those are platform config files that can't import this Kotlin
 *     constant. When changing the host, update each manually.
 *   - [APP_URL_SCHEME] is reflected in:
 *       * iosApp/iosApp/Info.plist (CFBundleURLTypes -> CFBundleURLSchemes)
 *       * functions/src/landingHtml.ts (APP_URL_SCHEME, used to build the iOS
 *         "Open in Bragwise" CTA on the landing page)
 *     Same story: neither can import this Kotlin constant. KEEP IN SYNC.
 */
internal const val FUNCTIONS_REGION = "europe-west1"

/** Authorised App Link / Universal Link host. Currently the Firebase Hosting default subdomain. */
internal const val APP_LINK_HOST = "bragwise.firebaseapp.com"

/** Full base URL for shareable / deep-link routes (`/c/{id}`). */
internal const val APP_LINK_BASE_URL = "https://$APP_LINK_HOST"

/**
 * Custom URL scheme registered by the iOS app (`CFBundleURLTypes` in
 * `iosApp/iosApp/Info.plist`). Needed because Safari does not re-trigger a
 * Universal Link when the tapped https link points at the domain that served
 * the page, so the landing page's iOS CTA uses this scheme instead. Android is
 * unaffected — it keeps using the `intent://` CTA.
 */
internal const val APP_URL_SCHEME = "bragwise"

/** `bragwise://` — prefix form of [APP_URL_SCHEME], for building/normalising scheme URLs. */
internal const val APP_SCHEME_PREFIX = "$APP_URL_SCHEME://"

/** Canonical shareable URL for a challenge. Resolves via the landing function. */
fun shareUrlForChallenge(challengeId: String): String = "$APP_LINK_BASE_URL/c/$challengeId"

/** Shareable URL linking directly to the results screen. */
fun shareUrlForResults(challengeId: String): String = "$APP_LINK_BASE_URL/c/$challengeId/results"
