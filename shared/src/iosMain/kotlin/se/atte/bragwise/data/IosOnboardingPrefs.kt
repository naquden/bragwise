package se.atte.bragwise.data

import platform.Foundation.NSUserDefaults

private const val KEY_SEEN_WELCOME = "bragwise.onboarding.has_seen_welcome"

class IosOnboardingPrefs : OnboardingPrefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var hasSeenWelcome: Boolean
        get() = defaults.boolForKey(KEY_SEEN_WELCOME)
        set(value) { defaults.setBool(value, KEY_SEEN_WELCOME) }
}
