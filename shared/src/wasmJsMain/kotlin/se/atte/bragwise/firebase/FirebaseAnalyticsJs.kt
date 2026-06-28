@file:JsModule("firebase/analytics")

package se.atte.bragwise.firebase

import kotlin.js.JsAny

/**
 * Firebase JS Analytics (GA4) interop — external declarations only.
 * (Files with @file:JsModule may only contain external declarations.)
 * The Kotlin bootstrap/log wrappers live in FirebaseAnalyticsHelpers.kt.
 */

// ── ESM imports (resolved by @file:JsModule) ──────────────────────────────────

@JsName("getAnalytics") external val getAnalyticsFn: JsAny
@JsName("logEvent") external val logEventFn: JsAny
