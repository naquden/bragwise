import { describe, it, expect } from 'vitest';
import { computeLeaderboard } from '../src/leaderboard';
import { Bet, PredictionPayload } from '../src/scoring';

describe('computeLeaderboard — GUESS closest-wins', () => {
  const guessBet: Bet = {
    kind: 'GUESS',
    id: 'b1',
    title: 'Arrival time',
    granularity: 'TIME',
    closest: true,
  };

  it('single winner gets the point', () => {
    const players = [
      { uid: 'alice', predictions: { b1: { kind: 'GUESS', guessValue: 870 } as PredictionPayload } },
      { uid: 'bob',   predictions: { b1: { kind: 'GUESS', guessValue: 900 } as PredictionPayload } },
      { uid: 'carol', predictions: { b1: { kind: 'GUESS', guessValue: 960 } as PredictionPayload } },
    ];
    const results: Record<string, PredictionPayload> = { b1: { kind: 'GUESS', guessValue: 875 } };
    const board = computeLeaderboard([guessBet], players, results);
    expect(board['alice']).toBe(1); // closest (diff=5)
    expect(board['bob']).toBe(0);   // diff=25
    expect(board['carol']).toBe(0); // diff=85
  });

  it('tie → all tied players score', () => {
    const players = [
      { uid: 'alice', predictions: { b1: { kind: 'GUESS', guessValue: 850 } as PredictionPayload } },
      { uid: 'bob',   predictions: { b1: { kind: 'GUESS', guessValue: 900 } as PredictionPayload } },
      { uid: 'carol', predictions: { b1: { kind: 'GUESS', guessValue: 850 } as PredictionPayload } },
    ];
    const results: Record<string, PredictionPayload> = { b1: { kind: 'GUESS', guessValue: 875 } };
    const board = computeLeaderboard([guessBet], players, results);
    expect(board['alice']).toBe(1); // diff=25
    expect(board['bob']).toBe(1);   // diff=25 — same as alice and carol
    expect(board['carol']).toBe(1); // diff=25
  });

  it('player without prediction does not score', () => {
    const players = [
      { uid: 'alice', predictions: { b1: { kind: 'GUESS', guessValue: 870 } as PredictionPayload } },
      { uid: 'bob',   predictions: {} },
    ];
    const results: Record<string, PredictionPayload> = { b1: { kind: 'GUESS', guessValue: 875 } };
    const board = computeLeaderboard([guessBet], players, results);
    expect(board['alice']).toBe(1);
    expect(board['bob']).toBe(0);
  });

  it('recompute after player deletion can flip winner', () => {
    // alice was closest before; remove her → bob becomes closest
    const players = [
      { uid: 'bob',   predictions: { b1: { kind: 'GUESS', guessValue: 900 } as PredictionPayload } },
      { uid: 'carol', predictions: { b1: { kind: 'GUESS', guessValue: 960 } as PredictionPayload } },
    ];
    const results: Record<string, PredictionPayload> = { b1: { kind: 'GUESS', guessValue: 875 } };
    const board = computeLeaderboard([guessBet], players, results);
    expect(board['bob']).toBe(1);   // now closest (diff=25)
    expect(board['carol']).toBe(0);
  });
});

describe('computeLeaderboard — GUESS exact (closest=false)', () => {
  const exactBet: Bet = {
    kind: 'GUESS',
    id: 'b2',
    title: 'Birth day',
    granularity: 'DAY',
    closest: false,
  };

  it('exact match scores 1', () => {
    const players = [
      { uid: 'alice', predictions: { b2: { kind: 'GUESS', guessValue: 19900 } as PredictionPayload } },
      { uid: 'bob',   predictions: { b2: { kind: 'GUESS', guessValue: 19901 } as PredictionPayload } },
    ];
    const results: Record<string, PredictionPayload> = { b2: { kind: 'GUESS', guessValue: 19900 } };
    const board = computeLeaderboard([exactBet], players, results);
    expect(board['alice']).toBe(1);
    expect(board['bob']).toBe(0);
  });
});

describe('computeLeaderboard — GUESS placement mode', () => {
  const placementBet: Bet = {
    kind: 'GUESS',
    id: 'p1',
    title: 'Final score',
    granularity: 'NUMBER',
    closest: true,
    placement: true,
  };

  it('descending N..1 points with no ties', () => {
    // 3 players, distances 1, 5, 10 → ranks 1,2,3 → points 3,2,1
    const players = [
      { uid: 'alice', predictions: { p1: { kind: 'GUESS', guessValue: 101 } as PredictionPayload } }, // diff=1
      { uid: 'bob',   predictions: { p1: { kind: 'GUESS', guessValue: 105 } as PredictionPayload } }, // diff=5
      { uid: 'carol', predictions: { p1: { kind: 'GUESS', guessValue: 110 } as PredictionPayload } }, // diff=10
    ];
    const results: Record<string, PredictionPayload> = { p1: { kind: 'GUESS', guessValue: 100 } };
    const board = computeLeaderboard([placementBet], players, results);
    expect(board['alice']).toBe(3); // rank 1 → 3 pts
    expect(board['bob']).toBe(2);   // rank 2 → 2 pts
    expect(board['carol']).toBe(1); // rank 3 → 1 pt
  });

  it('dense ties: distances [2,2,5,9,20] → points [5,5,4,3,2]', () => {
    // N=5; diffs: alice=2, bob=2, carol=5, dave=9, eve=20
    // dense ranks: 1,1,2,3,4 → points: 5,5,4,3,2
    const players = [
      { uid: 'alice', predictions: { p1: { kind: 'GUESS', guessValue: 102 } as PredictionPayload } }, // diff=2
      { uid: 'bob',   predictions: { p1: { kind: 'GUESS', guessValue: 98  } as PredictionPayload } }, // diff=2
      { uid: 'carol', predictions: { p1: { kind: 'GUESS', guessValue: 105 } as PredictionPayload } }, // diff=5
      { uid: 'dave',  predictions: { p1: { kind: 'GUESS', guessValue: 109 } as PredictionPayload } }, // diff=9
      { uid: 'eve',   predictions: { p1: { kind: 'GUESS', guessValue: 120 } as PredictionPayload } }, // diff=20
    ];
    const results: Record<string, PredictionPayload> = { p1: { kind: 'GUESS', guessValue: 100 } };
    const board = computeLeaderboard([placementBet], players, results);
    expect(board['alice']).toBe(5); // tied rank 1 → 5 pts
    expect(board['bob']).toBe(5);   // tied rank 1 → 5 pts
    expect(board['carol']).toBe(4); // dense rank 2 → 4 pts (NOT skipped)
    expect(board['dave']).toBe(3);  // dense rank 3 → 3 pts
    expect(board['eve']).toBe(2);   // dense rank 4 → 2 pts
  });

  it('player without prediction gets 0 and is excluded from N', () => {
    // N=2 (only alice and carol predicted); bob did not.
    // diffs: alice=1, carol=5 → ranks 1,2 → points 2,1
    const players = [
      { uid: 'alice', predictions: { p1: { kind: 'GUESS', guessValue: 101 } as PredictionPayload } }, // diff=1
      { uid: 'bob',   predictions: {} },                                                               // no prediction
      { uid: 'carol', predictions: { p1: { kind: 'GUESS', guessValue: 105 } as PredictionPayload } }, // diff=5
    ];
    const results: Record<string, PredictionPayload> = { p1: { kind: 'GUESS', guessValue: 100 } };
    const board = computeLeaderboard([placementBet], players, results);
    expect(board['alice']).toBe(2); // rank 1, N=2 → 2 pts
    expect(board['bob']).toBe(0);   // no prediction → 0
    expect(board['carol']).toBe(1); // rank 2, N=2 → 1 pt
  });

  it('N=1 single predictor gets 1 point', () => {
    const players = [
      { uid: 'alice', predictions: { p1: { kind: 'GUESS', guessValue: 99 } as PredictionPayload } },
    ];
    const results: Record<string, PredictionPayload> = { p1: { kind: 'GUESS', guessValue: 100 } };
    const board = computeLeaderboard([placementBet], players, results);
    expect(board['alice']).toBe(1); // N=1, rank 1 → 1 pt
  });

  it('regression: placement=false still gives old 1-pt-to-closest behavior', () => {
    const classicBet: Bet = {
      kind: 'GUESS',
      id: 'p1',
      title: 'Final score',
      granularity: 'NUMBER',
      closest: true,
      placement: false,
    };
    const players = [
      { uid: 'alice', predictions: { p1: { kind: 'GUESS', guessValue: 101 } as PredictionPayload } }, // diff=1
      { uid: 'bob',   predictions: { p1: { kind: 'GUESS', guessValue: 105 } as PredictionPayload } }, // diff=5
      { uid: 'carol', predictions: { p1: { kind: 'GUESS', guessValue: 110 } as PredictionPayload } }, // diff=10
    ];
    const results: Record<string, PredictionPayload> = { p1: { kind: 'GUESS', guessValue: 100 } };
    const board = computeLeaderboard([classicBet], players, results);
    expect(board['alice']).toBe(1); // closest → 1 pt
    expect(board['bob']).toBe(0);
    expect(board['carol']).toBe(0);
  });
});
