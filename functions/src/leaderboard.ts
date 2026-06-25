import { Bet, PredictionPayload, score } from './scoring';

export interface RankedEntry {
  uid: string;
  points: number;
  rank: number;
}

export function computeLeaderboard(
  bets: Bet[],
  players: Array<{ uid: string; predictions: Record<string, PredictionPayload> }>,
  results: Record<string, PredictionPayload>,
): Record<string, number> {
  const board: Record<string, number> = {};
  for (const p of players) {
    board[p.uid] = 0;
  }

  for (const bet of bets) {
    const result = results[bet.id];
    if (!result) continue;

    if (bet.kind === 'GUESS' && bet.closest) {
      // Cross-player closest-wins pass: award 1 point to every player whose
      // guess is closest to the actual result (ties → all tied players score).
      if (result.kind !== 'GUESS') continue;
      const actualValue = result.guessValue;
      let bestDiff = Infinity;
      const diffs: Array<{ uid: string; diff: number }> = [];
      for (const p of players) {
        const pred = p.predictions[bet.id];
        if (!pred || pred.kind !== 'GUESS') continue;
        const diff = Math.abs(pred.guessValue - actualValue);
        diffs.push({ uid: p.uid, diff });
        if (diff < bestDiff) bestDiff = diff;
      }
      for (const { uid, diff } of diffs) {
        if (diff === bestDiff) board[uid] = (board[uid] ?? 0) + 1;
      }
    } else {
      // Pairwise path for all other bets (including GUESS with closest=false).
      for (const p of players) {
        const pred = p.predictions[bet.id];
        if (pred && result) board[p.uid] = (board[p.uid] ?? 0) + score(bet, pred, result);
      }
    }
  }

  return board;
}

export function competitionRanks(leaderboard: Record<string, number>): RankedEntry[] {
  const sorted = Object.entries(leaderboard).sort(([, a], [, b]) => b - a);
  const result: RankedEntry[] = [];
  let i = 0;
  while (i < sorted.length) {
    const points = sorted[i][1];
    let j = i;
    while (j < sorted.length && sorted[j][1] === points) j++;
    for (let k = i; k < j; k++) {
      result.push({ uid: sorted[k][0], points, rank: i + 1 });
    }
    i = j;
  }
  return result;
}
