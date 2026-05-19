package se.atte.bragwise.data

/**
 * Persists the small bits of auth state that need to survive process death
 * but aren't owned by Firebase. Right now: the email address the user typed
 * before requesting an email sign-in link. We MUST replay that exact address
 * to `signInWithEmailLink` when the user comes back via the deep link
 * (Firebase enforces this).
 *
 * Platform implementations are provided via Koin's platformModule:
 * - Android: [AndroidAuthLocalDataSource] backed by SharedPreferences
 * - iOS: [IosAuthLocalDataSource] backed by NSUserDefaults
 */
interface AuthLocalDataSource {
    var pendingSignInEmail: String?
}
