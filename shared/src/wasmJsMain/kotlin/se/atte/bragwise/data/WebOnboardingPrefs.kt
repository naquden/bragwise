package se.atte.bragwise.data

private const val KEY_HAS_SEEN_WELCOME = "bragwise.ob.hasSeenWelcome"
private const val KEY_CHOSEN_NAME = "bragwise.ob.chosenName"

class WebOnboardingPrefs : OnboardingPrefs {
    override var hasSeenWelcome: Boolean
        get() = lsGet(KEY_HAS_SEEN_WELCOME) == "true"
        set(value) { lsSet(KEY_HAS_SEEN_WELCOME, if (value) "true" else "false") }

    override var chosenName: String?
        get() = lsGet(KEY_CHOSEN_NAME)
        set(value) {
            if (value != null) lsSet(KEY_CHOSEN_NAME, value) else lsRemove(KEY_CHOSEN_NAME)
        }
}
