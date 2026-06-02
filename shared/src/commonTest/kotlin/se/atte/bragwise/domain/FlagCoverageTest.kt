package se.atte.bragwise.domain

import se.atte.bragwise.ui.components.flagDrawable
import kotlin.test.Test
import kotlin.test.assertTrue

class FlagCoverageTest {
    @Test
    fun allCountriesHaveFlagDrawable() {
        val missing = ALL_COUNTRIES.filter { flagDrawable(it.code) == null }
        assertTrue(
            missing.isEmpty(),
            "Missing flag drawables for: ${missing.map { "${it.code} (${it.name})" }}"
        )
    }
}
