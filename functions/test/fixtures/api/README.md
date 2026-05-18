# Callable Zod-schema fixtures

Per plan §5 "Validation contract (TS ↔ Kotlin parity)": each callable's
request shape lives as JSON fixtures and is parsed against both the TS Zod
schema (`functions/src/schemas.ts`) and the Kotlin client serializer
(`shared/commonMain/.../data/...`). Divergence fails the local test run.

Layout: `functions/test/fixtures/api/{callable}/{valid,invalid}/*.json`.

Required negative fixtures per callable:
- `*-server-derived-field-injected.json` — payload includes a server-stamped
  field (createdBy, status, score, etc.). MUST produce `invalid-argument`.
- `*-promoted-flag-injected.json` — for `createChallenge` / `updateDraft`,
  payload includes `promoted: true` or `visibility: "PROMOTED"`. MUST
  produce `invalid-argument`.

CI wiring is deferred — see plan §5 "CI / GitHub Actions" and decision.md.
