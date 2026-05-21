package se.atte.bragwise.data

/**
 * Durable raw-string store for the LocalFriend list. Backed by
 * SharedPreferences on Android and NSUserDefaults on iOS. The value is a
 * JSON blob produced by [LocalFriendStore]; this layer is intentionally
 * dumb so future migration to SQLDelight (when more tables exist) only
 * needs to swap [LocalFriendStore]'s internals, not this interface.
 */
interface LocalFriendPersistence {
    fun load(): String?
    fun save(json: String?)
}
