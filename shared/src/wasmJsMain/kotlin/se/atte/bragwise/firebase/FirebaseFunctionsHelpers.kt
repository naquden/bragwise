package se.atte.bragwise.firebase

import kotlin.js.JsAny
import kotlin.js.Promise

// ── Firebase app accessor ─────────────────────────────────────────────────────
// Uses ESM-imported getApp() from @file:JsModule("firebase/app") in FirebaseApp.kt

fun getFirebaseApp(): JsAny = getApp()

// ── Cloud Functions callable helper ──────────────────────────────────────────
/**
 * Calls a Firebase Cloud Function by name with a JSON-encoded payload.
 * Returns a JSON string of the result data, or null if the function returns nothing.
 *
 * Error handling: on rejection, the error message will contain the Firebase
 * error code (e.g. "functions/already-exists") and can be parsed by
 * [mapFunctionErrors].
 */
@JsFun("""(httpsCallable, functions, name, payloadJson) => {
  const callable = httpsCallable(functions, name);
  const payload = payloadJson ? JSON.parse(payloadJson) : {};
  return callable(payload).then(r => {
    const data = r.data;
    if (data === undefined || data === null) return null;
    return JSON.stringify(data);
  });
}""")
private external fun callFunctionJsonInternal(
    httpsCallable: JsAny, functions: JsFunctions, name: String, payloadJson: String?
): Promise<JsAny?>

fun callFunctionJson(functions: JsFunctions, name: String, payloadJson: String?): Promise<JsAny?> =
    callFunctionJsonInternal(httpsCallableFn, functions, name, payloadJson)

// ── Functions imports bootstrap ───────────────────────────────────────────────
// Uses ESM-imported function refs (via @file:JsModule external vals) to avoid require().

@JsFun("(httpsCallable, getFunctions) => { globalThis.__wasmJsFunctionsImports = { httpsCallable, getFunctions }; }")
private external fun installFunctionsImports(httpsCallable: JsAny, getFunctions: JsAny)

fun bootstrapFunctionsImports() {
    installFunctionsImports(httpsCallableFn, getFunctionsFn)
}
