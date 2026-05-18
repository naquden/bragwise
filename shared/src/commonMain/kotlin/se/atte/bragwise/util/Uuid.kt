package se.atte.bragwise.util

/**
 * UUIDv4 string. Used as the stable `localId` for [LocalFriend] and
 * elsewhere on the local-only path. Cloud writes use Firestore-generated
 * IDs server-side; this helper is for client-only generation.
 */
expect fun randomUuid(): String
