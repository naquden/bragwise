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
    let total = 0;
    for (const bet of bets) {
      const pred = p.predictions[bet.id];
      const result = results[bet.id];
      if (pred && result) total += score(bet, pred, result);
    }
    board[p.uid] = total;
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
