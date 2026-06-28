package se.atte.bragwise.data

private const val KEY_PENDING_EMAIL = "bragwise.auth.pendingEmail"

class WebAuthLocalDataSource : AuthLocalDataSource {
    override var pendingSignInEmail: String?
        get() = lsGet(KEY_PENDING_EMAIL)
        set(value) {
            if (value != null) lsSet(KEY_PENDING_EMAIL, value) else lsRemove(KEY_PENDING_EMAIL)
        }
}
