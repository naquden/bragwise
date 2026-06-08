import { HttpsError } from 'firebase-functions/v2/https';
import { Bet, PredictionPayload } from './scoring';

function invalid(code: string): HttpsError {
  return new HttpsError('invalid-argument', code);
}

export function validatePayloadAgainstBet(bet: Bet, payload: PredictionPayload): void {
  if (payload.kind !== bet.kind) throw invalid('payload-kind-mismatch');
  switch (bet.kind) {
    case 'SINGLE_PICK': {
      const p = payload as { kind: 'SINGLE_PICK'; optionId: string };
      const ids = new Set(bet.options.map((o) => o.id));
      if (!ids.has(p.optionId)) throw invalid('option-not-in-bet');
      break;
    }
    case 'BOOLEAN_PROP':
      break;
    case 'RANKING': {
      const p = payload as { kind: 'RANKING'; orderedOptionIds: string[] };
      const ordered = p.orderedOptionIds;
      if (ordered.length !== bet.topN) throw invalid('ranking-wrong-length');
      const ids = new Set(bet.options.map((o) => o.id));
      const seen = new Set<string>();
      for (const id of ordered) {
        if (!ids.has(id)) throw invalid('ranking-unknown-option');
        if (seen.has(id)) throw invalid('ranking-duplicate-option');
        seen.add(id);
      }
      break;
    }
  }
}

export function validatePredictionMap(
  bets: Bet[],
  predMap: Record<string, PredictionPayload>,
): void {
  const betById = new Map(bets.map((b) => [b.id, b]));
  for (const [betId, payload] of Object.entries(predMap)) {
    const bet = betById.get(betId);
    if (!bet) throw invalid('unknown-bet-id');
    validatePayloadAgainstBet(bet, payload);
  }
  if (Object.keys(predMap).length !== bets.length) {
    throw invalid('incomplete-predictions');
  }
}

export function validateResults(bets: Bet[], results: Record<string, PredictionPayload>): void {
  const betById = new Map(bets.map((b) => [b.id, b]));
  for (const [betId, payload] of Object.entries(results)) {
    const bet = betById.get(betId);
    if (!bet) throw invalid('result-unknown-bet-id');
    validatePayloadAgainstBet(bet, payload);
  }
  for (const bet of bets) {
    if (!(bet.id in results)) throw invalid('result-missing-bet');
  }
}
