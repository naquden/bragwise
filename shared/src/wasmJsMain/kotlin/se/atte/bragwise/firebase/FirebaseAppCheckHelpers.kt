package se.atte.bragwise.firebase

import kotlin.js.JsAny

/**
 * Firebase JS App Check Kotlin helpers.
 * External vals (initializeAppCheckFn, ReCaptchaV3ProviderFn) live in FirebaseAppCheckJs.kt
 * which carries @file:JsModule("firebase/app-check") for proper ESM bundling.
 *
 * bootstrapAppCheck() must run AFTER initializeApp(); initFirebase() calls it once at startup.
 * When RECAPTCHA_SITE_KEY is empty the bootstrap is skipped (safe for local dev).
 */

private const val RECAPTCHA_SITE_KEY = "6LdQujstAAAAAKV97CrT3tW1z3ZbgfdJ5y_rmfOu"

// ── Installer helper ──────────────────────────────────────────────────────────

// All JS function refs must be passed as explicit params — Wasm interop cannot
// capture Kotlin external vals in @JsFun closures.
@JsFun("""(initializeAppCheck, ReCaptchaV3Provider, app, siteKey) => {
    initializeAppCheck(app, {
        provider: new ReCaptchaV3Provider(siteKey),
        isTokenAutoRefreshEnabled: true
    });
}""")
private external fun installAppCheck(
    initializeAppCheck: JsAny,
    ReCaptchaV3Provider: JsAny,
    app: JsAny,
    siteKey: String,
)

fun bootstrapAppCheck(app: JsAny) {
    if (RECAPTCHA_SITE_KEY.isEmpty()) return
    installAppCheck(initializeAppCheckFn, ReCaptchaV3ProviderFn, app, RECAPTCHA_SITE_KEY)
}
