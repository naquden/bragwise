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
 */
internal const val FUNCTIONS_REGION = "europe-west1"

/** Authorised App Link / Universal Link host. Currently the Firebase Hosting default subdomain. */
internal const val APP_LINK_HOST = "bragwise.firebaseapp.com"

/** Full base URL for shareable / deep-link routes (`/c/{id}`). */
internal const val APP_LINK_BASE_URL = "https://$APP_LINK_HOST"

/** Canonical shareable URL for a challenge. Resolves via the landing function. */
fun shareUrlForChallenge(challengeId: String): String = "$APP_LINK_BASE_URL/c/$challengeId"

/** Shareable URL linking directly to the results screen. */
fun shareUrlForResults(challengeId: String): String = "$APP_LINK_BASE_URL/c/$challengeId/results"
