package se.atte.bragwise.data

import platform.Foundation.NSUserDefaults

private const val KEY_PENDING_EMAIL = "bragwise.auth.pending_sign_in_email"

actual class AuthLocalDataSource actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual var pendingSignInEmail: String?
        get() = defaults.stringForKey(KEY_PENDING_EMAIL)
        set(value) {
            if (value == null) defaults.removeObjectForKey(KEY_PENDING_EMAIL)
            else defaults.setObject(value, KEY_PENDING_EMAIL)
        }
}
