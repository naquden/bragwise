/**
 * TS port of `shared/commonMain/.../ScoringEngine.kt`. Phase 1 scoring is
 * uniform: 1 point per correct. Fixture-driven parity with the Kotlin
 * engine is the gate that lets it stay simple as Phase 2 adds per-type
 * rules — see plan §5 "Scoring Engine".
 */

export type Bet =
  | { kind: 'SINGLE_PICK'; id: string; title: string; options: { id: string; label: string }[] }
  | { kind: 'RANKING'; id: string; title: string; topN: number; options: { id: string; label: string }[] }
  | { kind: 'BOOLEAN_PROP'; id: string; title: string };

export type PredictionPayload =
  | { kind: 'SINGLE_PICK'; optionId: string }
  | { kind: 'RANKING'; orderedOptionIds: string[] }
  | { kind: 'BOOLEAN_PROP'; value: boolean };

export function score(bet: Bet, prediction: PredictionPayload, result: PredictionPayload): number {
  switch (bet.kind) {
    case 'SINGLE_PICK': {
      if (prediction.kind !== 'SINGLE_PICK' || result.kind !== 'SINGLE_PICK') {
        throw new Error(`payload kind mismatch for SINGLE_PICK bet ${bet.id}`);
      }
      return prediction.optionId === result.optionId ? 1 : 0;
    }
    case 'BOOLEAN_PROP': {
      if (prediction.kind !== 'BOOLEAN_PROP' || result.kind !== 'BOOLEAN_PROP') {
        throw new Error(`payload kind mismatch for BOOLEAN_PROP bet ${bet.id}`);
      }
      return prediction.value === result.value ? 1 : 0;
    }
    case 'RANKING': {
      if (prediction.kind !== 'RANKING' || result.kind !== 'RANKING') {
        throw new Error(`payload kind mismatch for RANKING bet ${bet.id}`);
      }
      const p = prediction.orderedOptionIds;
      const r = result.orderedOptionIds;
      const len = Math.min(p.length, r.length);
      let matches = 0;
      for (let i = 0; i < len; i++) {
        if (p[i] === r[i]) matches++;
      }
      return matches;
    }
  }
}
