@file:JsModule("firebase/firestore")

package se.atte.bragwise.firebase

import kotlin.js.JsAny
import kotlin.js.Promise

// ── Core Firestore types ──────────────────────────────────────────────────────

external interface JsFirestore : JsAny
external interface JsDocRef : JsAny
external interface JsCollRef : JsAny
external interface JsQuery : JsAny

// ── Module functions ──────────────────────────────────────────────────────────

external fun getFirestore(): JsFirestore
external fun collection(db: JsFirestore, path: String): JsCollRef
external fun collectionGroup(db: JsFirestore, collectionId: String): JsQuery
external fun doc(db: JsFirestore, path: String): JsDocRef
external fun getDoc(ref: JsDocRef): Promise<JsAny?>

// ── External vals for passing function references to @JsFun installers ────────

@JsName("getFirestore") external val getFirestoreFn: JsAny
@JsName("collection") external val collectionFn: JsAny
@JsName("collectionGroup") external val collectionGroupFn: JsAny
@JsName("doc") external val docFn: JsAny
@JsName("getDoc") external val getDocFn: JsAny
@JsName("query") external val queryFn: JsAny
@JsName("where") external val whereFn: JsAny
@JsName("orderBy") external val orderByFn: JsAny
@JsName("onSnapshot") external val onSnapshotFn: JsAny
