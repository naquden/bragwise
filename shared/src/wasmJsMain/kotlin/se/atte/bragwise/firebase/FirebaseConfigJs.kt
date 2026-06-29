package se.atte.bragwise.firebase
import kotlin.js.JsAny

@JsFun("(apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId, measurementId) => ({ apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId, measurementId })")
external fun makeFirebaseConfig(apiKey: String, authDomain: String, projectId: String, storageBucket: String, messagingSenderId: String, appId: String, measurementId: String): JsAny

@JsFun("(url) => ({ url: url, handleCodeInApp: true })")
external fun makeActionCodeSettings(url: String): JsAny

@JsFun("() => window.location.origin") external fun windowOrigin(): String
@JsFun("() => window.location.href") external fun windowHref(): String
@JsFun("() => { history.replaceState(null, '', window.location.origin); }") external fun clearAuthQuery()

// TODO(web-config): VERIFY these web Firebase values against the Firebase console web-app registration;
// appId/apiKey/measurementId are web-app-specific and currently sourced from temp/wasmjs.md, not verified.
// measurementId (G-XXXXXXXX) is REQUIRED for GA4 analytics to initialise — fill it in from the
// Firebase console web-app registration to enable web usage tracking.
private const val MEASUREMENT_ID = ""

fun initFirebase() {
    val app = initializeApp(makeFirebaseConfig(
        apiKey = "AIzaSyAJKnsdEiQBPV5kzFA5isbMyTrzk01ebCM",
        authDomain = "bragwise.firebaseapp.com",
        projectId = "bragwise",
        storageBucket = "bragwise.firebasestorage.app",
        messagingSenderId = "984144877933",
        appId = "1:984144877933:web:8c48d6677766cabb412264",
        measurementId = MEASUREMENT_ID,
    ))
    bootstrapAppCheck(app)
    bootstrapFirestoreImports()
    bootstrapFunctionsImports()
    // GA4 needs a measurementId; skip init when absent so getAnalytics() doesn't throw.
    if (MEASUREMENT_ID.isNotEmpty()) bootstrapAnalytics()
}
