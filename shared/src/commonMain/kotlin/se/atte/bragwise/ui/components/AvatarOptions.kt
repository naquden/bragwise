package se.atte.bragwise.ui.components

val emojiAvatars: List<String> = listOf(
    // Faces & people
    "😀", "😎", "🤩", "🥳", "😍", "🤓", "😈", "👻", "🤖", "👽",
    "😂", "😅", "😜", "🤗",
    // Animals
    "🦁", "🐯", "🐻", "🦊", "🐺", "🦅", "🦋", "🐉", "🦄", "🦈",
    "🐸", "🐧", "🦉", "🐙",
    // Objects & sports
    "🔥", "⚡", "🏆", "🎯", "🎮", "🚀", "💎", "🌟", "🍀", "🎲",
    "⚽", "🏀", "🎸", "🌈",
)

/** Returns the avatarSeed value stored in Firestore for a given flag ISO code. */
fun flagSeed(code: String): String = "flag:$code"

fun isFlagSeed(seed: String): Boolean = seed.startsWith("flag:")

fun flagCodeOf(seed: String): String = seed.removePrefix("flag:")

/** True for the old placeholder seeds a1..a12 or blank, which use the colored-initial fallback. */
fun isLegacySeed(seed: String): Boolean = seed.isBlank() || Regex("^a\\d+$").matches(seed)
