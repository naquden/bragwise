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
      if (result.kind !== 'GUESS') continue;
      const actualValue = result.guessValue;
      const diffs: Array<{ uid: string; diff: number }> = [];
      for (const p of players) {
        const pred = p.predictions[bet.id];
        if (!pred || pred.kind !== 'GUESS') continue;
        const diff = Math.abs(pred.guessValue - actualValue);
        diffs.push({ uid: p.uid, diff });
      }

      if (bet.placement) {
        // Placement mode: dense-rank players by ascending distance, award N..1 pts.
        // N = number of players who submitted a valid GUESS prediction for this bet.
        // Tied distances share a rank; next rank is NOT skipped (dense ranking).
        // Players without a prediction get 0 and are excluded from N.
        const n = diffs.length;
        if (n === 0) continue;
        // Sort ascending by diff to assign dense ranks.
        const sorted = [...diffs].sort((a, b) => a.diff - b.diff);
        // Compute dense rank for each sorted entry, keeping original diffs intact.
        const ranks: number[] = new Array(sorted.length);
        let rank = 1;
        for (let i = 0; i < sorted.length; i++) {
          if (i > 0 && sorted[i].diff !== sorted[i - 1].diff) rank++;
          ranks[i] = rank;
        }
        for (let i = 0; i < sorted.length; i++) {
          const pts = n - (ranks[i] - 1);
          board[sorted[i].uid] = (board[sorted[i].uid] ?? 0) + pts;
        }
      } else {
        // Classic closest-wins: award 1 point to every player tied at best distance.
        let bestDiff = Infinity;
        for (const { diff } of diffs) {
          if (diff < bestDiff) bestDiff = diff;
        }
        for (const { uid, diff } of diffs) {
          if (diff === bestDiff) board[uid] = (board[uid] ?? 0) + 1;
        }
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
