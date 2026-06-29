@file:JsModule("firebase/app-check")

package se.atte.bragwise.firebase

import kotlin.js.JsAny

/**
 * Firebase JS App Check interop — external declarations only.
 * (Files with @file:JsModule may only contain external declarations.)
 * The Kotlin bootstrap wrappers live in FirebaseAppCheckHelpers.kt.
 */

// ── ESM imports (resolved by @file:JsModule) ──────────────────────────────────

@JsName("initializeAppCheck") external val initializeAppCheckFn: JsAny
@JsName("ReCaptchaV3Provider") external val ReCaptchaV3ProviderFn: JsAny
