package se.atte.bragwise.data

/**
 * Persists the small bits of auth state that need to survive process death
 * but aren't owned by Firebase. Right now: the email address the user typed
 * into OB-02 before requesting an email sign-in link. We MUST replay that
 * exact address to `signInWithEmailLink` when the user comes back via the
 * deep link (Firebase enforces this).
 *
 * Backed by SharedPreferences on Android and NSUserDefaults on iOS.
 */
expect class AuthLocalDataSource() {
    var pendingSignInEmail: String?
}
