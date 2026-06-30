package se.atte.bragwise

/**
 * Flip USE_MOCK_DATA to `true` to run against the in-memory mock repositories.
 * No sign-in required, no Firestore traffic. Read by all three platform entry points.
 * See docs/project.md § "Mock data build". MUST be `false` on main / any release build.
 */
object BuildFlags {
    const val USE_MOCK_DATA: Boolean = false
}
