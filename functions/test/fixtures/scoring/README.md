# Scoring fixtures

Fixture set shared between Kotlin `ScoringEngine` (`shared/commonTest/.../scoring/`)
and TS port (`functions/src/scoring.ts`). Divergence breaks the parity test
on either side. See plan §5 "Scoring Engine".

Fixture files (TODO):
- `single_pick_correct.json`
- `single_pick_wrong.json`
- `boolean_correct.json`
- `boolean_wrong.json`
- `ranking_all_correct.json`
- `ranking_partial.json`
- `ranking_all_wrong.json`

Negative cases (must produce defined behaviour):
- empty option list
- duplicate option IDs in a Ranking payload
- optionId not present in bet.options
