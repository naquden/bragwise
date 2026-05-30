package se.atte.bragwise.data

/**
 * Durable raw-string store for guest predictions. Backed by
 * SharedPreferences on Android and NSUserDefaults on iOS. The value is a
 * JSON blob produced by [LocalPredictionStore]; this layer is intentionally
 * dumb so future migration to SQLDelight (when more tables exist) only
 * needs to swap [LocalPredictionStore]'s internals, not this interface.
 *
 * Mirrors [LocalFriendPersistence] — same shape, separate key/file so the
 * two guest-only stores never collide.
 */
interface LocalPredictionPersistence {
    fun load(): String?
    fun save(json: String?)
}
