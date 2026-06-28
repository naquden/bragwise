@file:JsModule("firebase/app")
package se.atte.bragwise.firebase
import kotlin.js.JsAny
external fun initializeApp(config: JsAny): JsAny
external fun getApp(): JsAny
