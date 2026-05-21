package se.atte.bragwise.data

import android.content.Context

private const val PREFS = "bragwise_onboarding"
private const val KEY_SEEN_WELCOME = "has_seen_welcome"

class AndroidOnboardingPrefs(context: Context) : OnboardingPrefs {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var hasSeenWelcome: Boolean
        get() = prefs.getBoolean(KEY_SEEN_WELCOME, false)
        set(value) { prefs.edit().putBoolean(KEY_SEEN_WELCOME, value).apply() }
}
