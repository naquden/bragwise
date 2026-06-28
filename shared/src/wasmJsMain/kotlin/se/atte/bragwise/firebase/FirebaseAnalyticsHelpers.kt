package se.atte.bragwise.firebase

import kotlin.js.JsAny

/**
 * Firebase JS Analytics (GA4) Kotlin helpers.
 * External vals (getAnalyticsFn, logEventFn) live in FirebaseAnalyticsJs.kt
 * which carries @file:JsModule("firebase/analytics") for proper ESM bundling.
 *
 * getAnalytics() must run AFTER initializeApp(); initFirebase() calls
 * bootstrapAnalytics() once at startup.
 */

// ── Installer helper ──────────────────────────────────────────────────────────

@JsFun("(getAnalytics) => { globalThis.__wasmJsAnalytics = getAnalytics(); }")
private external fun installAnalytics(getAnalytics: JsAny)

fun bootstrapAnalytics() {
    installAnalytics(getAnalyticsFn)
}

// ── Log event ─────────────────────────────────────────────────────────────────
/**
 * logEvent(analytics, name, paramsObj). paramsJson is JSON.parsed into the
 * params object. Guarded so a missing/uninitialised analytics handle never
 * throws into app code.
 */
@JsFun(
    """(logEvent, name, paramsJson) => {
        try {
            const a = globalThis.__wasmJsAnalytics;
            if (!a) return;
            logEvent(a, name, paramsJson ? JSON.parse(paramsJson) : {});
        } catch (e) { /* analytics is best-effort; never break the app */ }
    }"""
)
private external fun logAnalyticsEventInternal(logEvent: JsAny, name: String, paramsJson: String?): Unit

fun logAnalyticsEvent(name: String, paramsJson: String?) {
    logAnalyticsEventInternal(logEventFn, name, paramsJson)
}
