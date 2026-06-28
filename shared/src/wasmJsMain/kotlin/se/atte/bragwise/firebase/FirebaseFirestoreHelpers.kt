package se.atte.bragwise.firebase

import kotlin.js.JsAny

// ── Query builders via @JsFun ─────────────────────────────────────────────────
// We use inline JS for query construction to avoid vararg spreading issues.
// Each @JsFun imports directly from the module specifier so webpack can tree-shake.

@JsFun("""(db) => {
  const { getFirestore, collection, query, where, orderBy } = globalThis.__wasmJsFirestoreImports;
  return query(
    collection(db, 'challenges'),
    where('visibility', '==', 'PROMOTED'),
    where('status', '==', 'OPEN'),
    orderBy('locksAt', 'asc')
  );
}""")
external fun queryPromoted(db: JsFirestore): JsQuery

@JsFun("""(db, uid) => {
  const { collection, query, where, orderBy } = globalThis.__wasmJsFirestoreImports;
  return query(
    collection(db, 'challenges'),
    where('createdBy', '==', uid),
    orderBy('createdAt', 'desc')
  );
}""")
external fun queryCreatedBy(db: JsFirestore, uid: String): JsQuery

@JsFun("""(db, uid) => {
  const { collectionGroup, query, where } = globalThis.__wasmJsFirestoreImports;
  return query(collectionGroup(db, 'players'), where('uid', '==', uid));
}""")
external fun queryPlayersByUid(db: JsFirestore, uid: String): JsQuery

@JsFun("""(db, uid) => {
  const { collectionGroup, query, where } = globalThis.__wasmJsFirestoreImports;
  return query(collectionGroup(db, 'invitations'), where('invitedUid', '==', uid));
}""")
external fun queryInvitationsByUid(db: JsFirestore, uid: String): JsQuery

@JsFun("""(db, path) => {
  const { collection, query } = globalThis.__wasmJsFirestoreImports;
  return query(collection(db, path));
}""")
external fun queryCollection(db: JsFirestore, path: String): JsQuery

/** friendUidsJson is a JSON array string of up to 30 uids */
@JsFun("""(db, friendUidsJson) => {
  const { collection, query, where } = globalThis.__wasmJsFirestoreImports;
  const uids = JSON.parse(friendUidsJson);
  return query(
    collection(db, 'challenges'),
    where('createdBy', 'in', uids),
    where('status', '==', 'OPEN'),
    where('visibility', '==', 'FRIENDS')
  );
}""")
external fun queryFromFriendsChunk(db: JsFirestore, friendUidsJson: String): JsQuery

// ── onSnapshot helpers ───────────────────────────────────────────────────────

/** Attaches a snapshot listener to a doc ref. Returns the unsubscribe function. */
@JsFun("""(ref, onNext, onError) => {
  const { onSnapshot } = globalThis.__wasmJsFirestoreImports;
  return onSnapshot(ref, (snap) => onNext(snap), (err) => onError(err));
}""")
external fun onDocSnapshot(ref: JsDocRef, onNext: (JsAny) -> Unit, onError: (JsAny) -> Unit): () -> Unit

/** Attaches a snapshot listener to a query. Returns the unsubscribe function. */
@JsFun("""(query, onNext, onError) => {
  const { onSnapshot } = globalThis.__wasmJsFirestoreImports;
  return onSnapshot(query, (snap) => onNext(snap), (err) => onError(err));
}""")
external fun onQuerySnapshot(query: JsQuery, onNext: (JsAny) -> Unit, onError: (JsAny) -> Unit): () -> Unit

// ── Snapshot data extractors ─────────────────────────────────────────────────

/** Returns the JSON string of a doc snapshot's data, or null if doc doesn't exist. */
@JsFun("""(snap) => {
  if (!snap.exists()) return null;
  const d = snap.data();
  return d === undefined || d === null ? null : JSON.stringify(d);
}""")
external fun snapshotDataJson(snap: JsAny): String?

/** Returns the document id from any snapshot. */
@JsFun("(snap) => snap.id")
external fun snapshotId(snap: JsAny): String

/** Returns whether a document snapshot exists. */
@JsFun("(snap) => snap.exists()")
external fun snapshotExists(snap: JsAny): Boolean

/**
 * Returns a JSON array string of all documents in a query snapshot.
 * Each element: { id: string, data: object }
 */
@JsFun("""(qsnap) => JSON.stringify(qsnap.docs.map(d => ({ id: d.id, data: d.data() })))""")
external fun querySnapshotJson(qsnap: JsAny): String

/**
 * For a snapshot from a subcollection (e.g. players/{id} under challenges/{id}),
 * returns the grandparent document id, or null if none.
 */
@JsFun("""(snap) => {
  const parent = snap.ref && snap.ref.parent;
  const grandparent = parent && parent.parent;
  return grandparent ? grandparent.id : null;
}""")
external fun snapshotGrandparentId(snap: JsAny): String?

/** Returns the error code string from a Firestore error (e.g. "permission-denied"). */
@JsFun("""(err) => {
  if (err && typeof err.code === 'string') return err.code;
  if (err && err.message) return err.message;
  return 'unknown';
}""")
external fun firestoreErrorCode(err: JsAny): String

// ── Firestore imports bootstrap ───────────────────────────────────────────────
// Called once at startup to make Firestore SDK functions available to @JsFun helpers.
// The @JsFun helpers above reference globalThis.__wasmJsFirestoreImports.
// Uses ESM-imported function refs (via @file:JsModule external vals) to avoid require().

@JsFun("""(getFirestore, collection, collectionGroup, doc, getDoc, query, where_, orderBy, onSnapshot) => {
  globalThis.__wasmJsFirestoreImports = {
    getFirestore, collection, collectionGroup, doc, getDoc,
    query, where: where_, orderBy, onSnapshot
  };
}""")
private external fun installFirestoreImports(
    getFirestore: JsAny, collection: JsAny, collectionGroup: JsAny,
    doc: JsAny, getDoc: JsAny, query: JsAny, where_: JsAny,
    orderBy: JsAny, onSnapshot: JsAny
)

fun bootstrapFirestoreImports() {
    installFirestoreImports(
        getFirestoreFn, collectionFn, collectionGroupFn,
        docFn, getDocFn, queryFn, whereFn,
        orderByFn, onSnapshotFn
    )
}
