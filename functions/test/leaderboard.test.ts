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
