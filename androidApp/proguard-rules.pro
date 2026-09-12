# ── Bragwise R8 keep rules (release only) ────────────────────────────────────
# Keep this file tiny. Each rule pins names R8 would otherwise rename, which is
# exactly what Play Console measures as "Obfuscation". Add nothing without a
# verified reason.

# Readable crash reports: keep line numbers, collapse all source file names to the
# literal "SourceFile" (no paths leak, no naming cost).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manifest entry points. With isShrinkResources = true, AGP no longer feeds
# AAPT2-generated keep rules to R8 (ProguardConfigurableTask.kt:420-423), and the
# manifest still names these classes literally. Strict full mode does not keep
# <init>() implicitly (BooleanOption.kt:151-156), so it is spelled out.
-keep class se.atte.bragwise.BragwiseApplication { <init>(); }
-keep class se.atte.bragwise.MainActivity { <init>(); }
-keep class se.atte.bragwise.push.BragwiseFirebaseMessagingService { <init>(); }

# Enum constant names are wire format AND persisted format:
#   Visibility, ScoringMode -> createChallenge payload via .name
#     (ChallengeRemoteDataSource.kt:222,226), zod-validated server side
#     (functions/src/schemas.ts:112,205) -> a renamed constant would be a hard 400
#   OptionType, GuessGranularity -> bet DTOs (ChallengeRemoteDataSource.kt:340-357)
#   ChallengeStatus -> Firestore status strings (FirestoreMappers.kt:130-175)
#   all of the above -> local SQLite drafts (SqlDelightLocalDraftStore.kt:104-171)
#   ThemeMode -> SharedPreferences (AndroidThemePrefs.kt:18 write, :24 read)
#
# No keep rule needed, and this was measured, not assumed. R8 renames the static
# constant *fields* (mapping.txt: STANDARD -> a), but the name string itself is an
# ldc literal passed to Enum.<init>(String,int) in <clinit>, and R8 leaves it
# alone: with no keep rule, "PLACEMENT" and "INVITE_ONLY" are each still present
# once in build/intermediates/dex/release/minifyReleaseWithR8/classes.dex, and
# neither appears as a string literal anywhere in Kotlin source. valueOf() looks
# up via values()+name() (kept by AGP's default file), never via field names.
# Re-run that check if R8 ever changes: build, then
#   grep -ao 'INVITE_ONLY|PLACEMENT' <that classes.dex>   (expect both)
#
# Re-verified on the shipped 0.7.9 release dex (dexdump -d): every enum <clinit>
# still passes the unrenamed name literal to Enum.<init>, and $VALUES holds every
# constant. usage.txt does report one removal — "Visibility PROMOTED" — but that is
# only the redundant *static field* (nothing reads it; the sole read,
# ChallengeCard.kt:180, compiles to an ordinal switch). The instance is still
# constructed and stored in $VALUES, so values()/valueOf("PROMOTED") and .name are
# unaffected.

# ── -dontwarn: intentionally empty ───────────────────────────────────────────
# Do not guess. android.r8.failOnMissingClasses is Enforced since AGP 8.0
# (BooleanOption.kt:688), so missing classes fail the build loudly and R8 writes
# ready-to-paste rules into build/outputs/mapping/release/missing_rules.txt.
# Copy only those lines, each with a comment naming the dependency.

# ── Verified NOT needed — do not add ─────────────────────────────────────────
#  * @Serializable DTOs / Companion / serializer(): kotlinx-serialization-core
#    ships its own consumer rules (kotlinx-serialization-common.pro,
#    kotlinx-serialization-r8.pro).
#  * The 17 @Serializable nav routes (ui/nav/AppNav.kt:130-146): serialName is a
#    compile-time string literal in the generated $serializer, not a runtime class
#    name — stable across the unminified -> minified app update.
#  * -adaptclassstrings: NEVER. It would rewrite those serialName literals.
#  * GitLive Firebase, native Firebase, Koin, Compose, navigation-*, SQLDelight:
#    no name-based reflection in our usage, or the library ships its own rules.
