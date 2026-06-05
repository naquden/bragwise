package se.atte.bragwise.data

/**
 * Tracks first-launch onboarding state. Backed by SharedPreferences
 * (Android) and NSUserDefaults (iOS).
 *
 * [chosenName] is the on-device display name chosen just-in-time before the
 * user's first participation action (placing bets or creating a challenge).
 * Used to prefill the name gate dialog and as an offline fallback; `null`
 * means the user hasn't named themselves yet.
 */
interface OnboardingPrefs {
    var hasSeenWelcome: Boolean
    var chosenName: String?
}
