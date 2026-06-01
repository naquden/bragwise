package se.atte.bragwise.data

/**
 * Tracks first-launch onboarding state. Backed by SharedPreferences
 * (Android) and NSUserDefaults (iOS).
 *
 * [guestName] is the on-device display name a guest picks before their
 * first challenge. Guests can't sign up to store friends, but they still
 * need a name to play; `null` means a guest hasn't chosen one yet.
 */
interface OnboardingPrefs {
    var hasSeenWelcome: Boolean
    var guestName: String?
}
