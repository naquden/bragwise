@file:JsModule("firebase/functions")

package se.atte.bragwise.firebase

import kotlin.js.JsAny
import kotlin.js.Promise

external interface JsFunctions : JsAny

external fun getFunctions(app: JsAny, region: String): JsFunctions

// ── External vals for passing function references to @JsFun installers ────────

@JsName("httpsCallable") external val httpsCallableFn: JsAny
@JsName("getFunctions") external val getFunctionsFn: JsAny
