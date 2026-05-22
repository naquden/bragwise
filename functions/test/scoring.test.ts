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
