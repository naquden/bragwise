---
name: translate-strings
description: Add or update string translations in Bragwise. Use when adding new strings, translating missing strings, or fixing translation issues across any supported locale.
---

# String Translations — Bragwise

## String system

Bragwise uses **Compose Multiplatform string resources** (not Android `res/values/`).

| Locale | File path |
|--------|-----------|
| English (default) | `shared/src/commonMain/composeResources/values/strings.xml` |

Locale variants (Finnish, Swedish, etc.) land in Phase 5. When adding a new locale, create:
`shared/src/commonMain/composeResources/values-<locale>/strings.xml`

## String format

Standard Android string format — `%1$s`, `%2$s` for positional substitutions:

```xml
<string name="share_challenge_title">%1$s</string>
<string name="share_challenge_subject">%1$s on Bragwise</string>
```

**Never rename** the positional parameter. Placement in the sentence may change per language, but the `%1$s` token must be preserved exactly.

## Escape characters

Apostrophes must be escaped:
```xml
<string name="example">It\'s your turn</string>
```

HTML entities for special chars:
```xml
<string name="example">A &amp; B</string>
```

## Non-translatable strings

Brand names use `translatable="false"`:
```xml
<string name="app_name" translatable="false">Bragwise</string>
```

## Translation quality rules

1. **Preserve `%1$s` syntax exactly** — wrong syntax silently breaks runtime substitution
2. **No periods** at end of UI strings — project convention
3. **No `\n` in string resources** — formatting belongs in the layout, not the string
4. **Technical terms**: keep domain-specific terms (Challenge, Leaderboard, Prediction) — don't invent local equivalents unless they're established
5. **Professional but friendly tone** — users are competing with friends
6. **Concise** — UI strings need to fit in buttons and labels

## Workflow: adding new strings

1. Add the English string to `shared/src/commonMain/composeResources/values/strings.xml`
2. If locale files exist, add translations to each
3. Reference in Compose via `stringResource(Res.string.key_name)`

## Workflow: finding missing translations

```bash
# List string names in English but missing in a locale (example: Finnish)
comm -23 \
  <(grep -o 'name="[^"]*"' shared/src/commonMain/composeResources/values/strings.xml | sort) \
  <(grep -o 'name="[^"]*"' shared/src/commonMain/composeResources/values-fi/strings.xml | sort)
```

Run for each locale to find gaps.

## Domain terminology reference

| Term | Meaning |
|------|---------|
| Challenge | A prediction event (e.g. World Cup 2026) |
| Bet | A single question within a challenge |
| Prediction | A user's answer to a bet |
| Leaderboard | Ranked list of participants by score |
| Rank | User's position in a challenge leaderboard |
| Promoted | A featured/official challenge |
| Trusted | Verified challenge organiser |
| Locked | Challenge that no longer accepts new predictions |
| Results | Posted outcome of a bet used for scoring |
| Single Pick | Bet type: choose one option from a list |
| Ranking | Bet type: order items by predicted finish |
| Boolean | Bet type: Yes/No question |
