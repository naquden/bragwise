/**
 * TS port of `shared/commonMain/.../ScoringEngine.kt`. Phase 1 scoring is
 * uniform: 1 point per correct. Fixture-driven parity with the Kotlin
 * engine is the gate that lets it stay simple as Phase 2 adds per-type
 * rules — see plan §5 "Scoring Engine".
 */

export type Bet =
  | { kind: 'SINGLE_PICK'; id: string; title: string; options: { id: string; label: string }[] }
  | { kind: 'RANKING'; id: string; title: string; topN: number; options: { id: string; label: string }[] }
  | { kind: 'BOOLEAN_PROP'; id: string; title: string }
  | { kind: 'GUESS'; id: string; title: string; granularity: 'TIME' | 'DAY' | 'NUMBER'; closest: boolean; placement?: boolean }
  | { kind: 'MULTI_SELECT'; id: string; title: string; options: { id: string; label: string }[] }
  | { kind: 'OVER_UNDER'; id: string; title: string; line: number };

export type PredictionPayload =
  | { kind: 'SINGLE_PICK'; optionId: string }
  | { kind: 'RANKING'; orderedOptionIds: string[] }
  | { kind: 'BOOLEAN_PROP'; value: boolean }
  | { kind: 'GUESS'; guessValue: number }
  | { kind: 'MULTI_SELECT'; selectedOptionIds: string[] }
  | { kind: 'OVER_UNDER'; over?: boolean; actualValue?: number };

/**
 * Pairwise score function.
 * NOTE: GUESS bets with closest=true are scored cross-player by computeLeaderboard,
 * not here. This function returns 0 for those bets so the aggregator can add
 * points without double-counting.
 */
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
    case 'GUESS': {
      if (prediction.kind !== 'GUESS' || result.kind !== 'GUESS') {
        throw new Error(`payload kind mismatch for GUESS bet ${bet.id}`);
      }
      if (bet.closest) {
        // Closest-wins (and placement) is cross-player; scored by computeLeaderboard, not here.
        return 0;
      }
      return prediction.guessValue === result.guessValue ? 1 : 0;
    }
    case 'MULTI_SELECT': {
      if (prediction.kind !== 'MULTI_SELECT' || result.kind !== 'MULTI_SELECT') {
        throw new Error(`payload kind mismatch for MULTI_SELECT bet ${bet.id}`);
      }
      const predictedSet = new Set(prediction.selectedOptionIds);
      const resultSet = new Set(result.selectedOptionIds);
      let correct = 0;
      let wrong = 0;
      for (const id of predictedSet) {
        if (resultSet.has(id)) correct++; else wrong++;
      }
      return correct - wrong;
    }
    case 'OVER_UNDER': {
      if (prediction.kind !== 'OVER_UNDER' || result.kind !== 'OVER_UNDER') {
        throw new Error(`payload kind mismatch for OVER_UNDER bet ${bet.id}`);
      }
      const actualValue = result.actualValue;
      const predictedOver = prediction.over;
      if (actualValue === undefined || predictedOver === undefined) return 0;
      if (actualValue === bet.line) return 0;  // push
      if (predictedOver && actualValue > bet.line) return 1;
      if (!predictedOver && actualValue < bet.line) return 1;
      return 0;
    }
  }
}
