import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { score, Bet, PredictionPayload } from '../src/scoring';

interface Case {
  name: string;
  bet: Bet;
  prediction: PredictionPayload;
  result: PredictionPayload;
  expected: number;
}

interface Fixture {
  cases: Case[];
}

const fixturePath = join(__dirname, 'fixtures', 'scoring', 'cases.json');
const fixture: Fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));

describe('ScoringEngine TS parity', () => {
  if (fixture.cases.length === 0) {
    throw new Error('fixture file has zero cases');
  }

  for (const c of fixture.cases) {
    it(c.name, () => {
      expect(score(c.bet, c.prediction, c.result)).toBe(c.expected);
    });
  }
});

describe('score — GUESS placement=true returns 0', () => {
  it('placement=true is cross-player; score() must return 0 to avoid double-counting', () => {
    const bet: Bet = {
      kind: 'GUESS',
      id: 'bp1',
      title: 'Final score',
      granularity: 'NUMBER',
      closest: true,
      placement: true,
    };
    const prediction: PredictionPayload = { kind: 'GUESS', guessValue: 42 };
    const result: PredictionPayload = { kind: 'GUESS', guessValue: 42 };
    // Even with a perfect guess, score() returns 0 — points are added by computeLeaderboard.
    expect(score(bet, prediction, result)).toBe(0);
  });
});
