package se.atte.bragwise.data

/**
 * Tracks whether the user has seen the Welcome screen. Backed by
 * SharedPreferences (Android) and NSUserDefaults (iOS).
 */
interface OnboardingPrefs {
    var hasSeenWelcome: Boolean
}
