package se.atte.bragwise.domain

import se.atte.bragwise.ui.components.allFlagCodes
import se.atte.bragwise.ui.components.emojiAvatars
import se.atte.bragwise.ui.components.flagSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity guard for the avatar seed allowlist.
 *
 * The server validates seeds against `functions/test/fixtures/avatar/seeds.json`.
 * When you add or remove items from `emojiAvatars` or `FLAG_DRAWABLES` in
 * `FlagEmoji.kt`, update that JSON file to stay in sync — this test will fail
 * with the new count to tell you what to change.
 *
 * Expected counts come from the fixture (seeds.json): emoji=42, flagCodes=213.
 */
class AvatarSeedsParityTest {

    @Test
    fun emojiAvatarsCountMatchesFixture() {
        // Fixture seeds.json has 42 emoji. Update both when adding emoji.
        assertEquals(42, emojiAvatars.size, "emojiAvatars size changed — update seeds.json to match")
    }

    @Test
    fun flagCodesCountMatchesFixture() {
        // Fixture seeds.json has 213 flag codes. Update both when adding flags.
        assertEquals(213, allFlagCodes.size, "allFlagCodes size changed — update seeds.json to match")
    }

    @Test
    fun emojiAvatarsContainsNoBlankEntries() {
        assertTrue(emojiAvatars.none { it.isBlank() }, "emojiAvatars must not contain blank entries")
    }

    @Test
    fun flagSeedsAreNonEmpty() {
        assertTrue(allFlagCodes.isNotEmpty())
        // Spot-check known codes (GB-ENG subdivision and standard code)
        assertTrue("SE" in allFlagCodes, "SE must be in allFlagCodes")
        assertTrue("GB-ENG" in allFlagCodes, "GB-ENG must be in allFlagCodes")
        assertTrue("US" in allFlagCodes, "US must be in allFlagCodes")
    }

    @Test
    fun flagSeedFormatMatchesFixtureConvention() {
        // Fixture stores flag seeds as "flag:<CODE>". Verify the Kotlin helper matches.
        assertEquals("flag:SE", flagSeed("SE"))
        assertEquals("flag:GB-ENG", flagSeed("GB-ENG"))
    }
}
