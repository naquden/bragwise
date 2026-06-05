package se.atte.bragwise.data

import platform.Foundation.NSUserDefaults

private const val KEY_SEEN_WELCOME = "bragwise.onboarding.has_seen_welcome"
private const val KEY_GUEST_NAME = "bragwise.onboarding.guest_name"

class IosOnboardingPrefs : OnboardingPrefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var hasSeenWelcome: Boolean
        get() = defaults.boolForKey(KEY_SEEN_WELCOME)
        set(value) { defaults.setBool(value, KEY_SEEN_WELCOME) }

    override var chosenName: String?
        get() = defaults.stringForKey(KEY_GUEST_NAME)
        set(value) { defaults.setObject(value, KEY_GUEST_NAME) }
}
