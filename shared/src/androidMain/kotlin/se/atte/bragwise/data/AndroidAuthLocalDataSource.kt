package se.atte.bragwise.data

import android.content.Context
import android.content.SharedPreferences

private const val PREFS = "bragwise_auth"
private const val KEY_PENDING_EMAIL = "pending_sign_in_email"

class AndroidAuthLocalDataSource(context: Context) : AuthLocalDataSource {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var pendingSignInEmail: String?
        get() = prefs.getString(KEY_PENDING_EMAIL, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_PENDING_EMAIL) else putString(KEY_PENDING_EMAIL, value)
            }.apply()
        }
}
