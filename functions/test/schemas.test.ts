import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import {
  PredictionPayloadSchema,
  SubmitPredictionsSchema,
  PostResultsSchema,
  MigrateGuestDataSchema,
  CreateChallengeSchema,
  SetNotificationPrefSchema,
} from '../src/schemas';

function readFixtures(...segments: string[]): { name: string; data: unknown }[] {
  const dir = join(__dirname, 'fixtures', 'api', ...segments);
  return readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .map((f) => ({ name: f, data: JSON.parse(readFileSync(join(dir, f), 'utf8')) }));
}

// Recursively asserts that no object in the parsed tree has an explicit-null-valued key.
// Mobile clients send null for every unset field on a flat DTO; the server must strip those
// so the parsed payload matches what `=== undefined` / `!== undefined` checks downstream
// already expect (scoring.ts, predictionValidation.ts, index.ts's setNotificationPref).
function assertNoNullValuedKeys(value: unknown, path = '$'): void {
  if (Array.isArray(value)) {
    value.forEach((v, i) => assertNoNullValuedKeys(v, `${path}[${i}]`));
    return;
  }
  if (value !== null && typeof value === 'object') {
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      expect(v, `${path}.${k} should not be present as null`).not.toBeNull();
      assertNoNullValuedKeys(v, `${path}.${k}`);
    }
  }
}

describe('SubmitPredictionsSchema / PostResultsSchema / MigrateGuestDataSchema — flat-DTO fixtures', () => {
  it('accepts every submitPredictions fixture and strips null keys', () => {
    for (const { name, data } of readFixtures('submitPredictions', 'valid')) {
      const result = SubmitPredictionsSchema.safeParse(data);
      expect(result.success, `${name}: ${!result.success && JSON.stringify(result.error.issues)}`).toBe(true);
      if (result.success) assertNoNullValuedKeys(result.data);
    }
  });

  it('accepts every postResults fixture and strips null keys', () => {
    for (const { name, data } of readFixtures('postResults', 'valid')) {
      const result = PostResultsSchema.safeParse(data);
      expect(result.success, `${name}: ${!result.success && JSON.stringify(result.error.issues)}`).toBe(true);
      if (result.success) assertNoNullValuedKeys(result.data);
    }
  });

  it('accepts every migrateGuestData fixture and strips null keys', () => {
    for (const { name, data } of readFixtures('migrateGuestData', 'valid')) {
      const result = MigrateGuestDataSchema.safeParse(data);
      expect(result.success, `${name}: ${!result.success && JSON.stringify(result.error.issues)}`).toBe(true);
      if (result.success) assertNoNullValuedKeys(result.data);
    }
  });
});

describe('CreateChallengeSchema — flat BetDto fixtures', () => {
  it('accepts the flat BetDto shape for all six bet kinds', () => {
    for (const { name, data } of readFixtures('createChallenge', 'valid')) {
      const result = CreateChallengeSchema.safeParse(data);
      expect(result.success, `${name}: ${!result.success && JSON.stringify(result.error.issues)}`).toBe(true);
    }
  });
});

describe('SetNotificationPrefSchema — mobile null-carrying fixtures', () => {
  it('accepts every mobile fixture and strips null keys', () => {
    for (const { name, data } of readFixtures('setNotificationPref', 'valid')) {
      const result = SetNotificationPrefSchema.safeParse(data);
      expect(result.success, `${name}: ${!result.success && JSON.stringify(result.error.issues)}`).toBe(true);
      if (result.success) assertNoNullValuedKeys(result.data);
    }
  });

  it('still rejects all-null', () => {
    const result = SetNotificationPrefSchema.safeParse({ enabled: null, categories: null });
    expect(result.success).toBe(false);
  });

  it('still rejects an empty object', () => {
    const result = SetNotificationPrefSchema.safeParse({});
    expect(result.success).toBe(false);
  });

  it('still rejects an unknown category key', () => {
    const result = SetNotificationPrefSchema.safeParse({ categories: { bogus: true } });
    expect(result.success).toBe(false);
  });

  it('still accepts unmodified web-client shapes', () => {
    expect(SetNotificationPrefSchema.safeParse({ enabled: true }).success).toBe(true);
    expect(SetNotificationPrefSchema.safeParse({ categories: { social: true, results: false } }).success).toBe(true);
  });
});

describe('PredictionPayloadSchema — invalid payloads still rejected with the same path', () => {
  it('rejects an unrecognised kind', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'NOPE' });
    expect(result.success).toBe(false);
  });

  it('rejects a missing required field on a declared branch', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'SINGLE_PICK' });
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues[0]?.path.join('.')).toBe('optionId');
  });

  it('rejects an empty optionId', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'SINGLE_PICK', optionId: '' });
    expect(result.success).toBe(false);
  });

  it('still strips (rather than admits) an injected server-derived field', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'GUESS', guessValue: 1, createdBy: 'evil' });
    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toEqual({ kind: 'GUESS', guessValue: 1 });
  });
});

describe('PredictionPayloadSchema — degradation-trap lock (list fields must never be dropped)', () => {
  it('accepts MULTI_SELECT with an empty selection (a legitimate "none of these")', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'MULTI_SELECT', selectedOptionIds: [] });
    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toEqual({ kind: 'MULTI_SELECT', selectedOptionIds: [] });
  });

  it('accepts RANKING with a populated list', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'RANKING', orderedOptionIds: ['a', 'b'] });
    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toEqual({ kind: 'RANKING', orderedOptionIds: ['a', 'b'] });
  });

  it('rejects MULTI_SELECT with the key missing entirely (not the same as an empty list)', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'MULTI_SELECT' });
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues[0]?.path.join('.')).toBe('selectedOptionIds');
  });

  it('rejects RANKING with the key missing entirely', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'RANKING' });
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues[0]?.path.join('.')).toBe('orderedOptionIds');
  });
});

describe('PredictionPayloadSchema — falsy-but-meaningful values round-trip', () => {
  it('BOOLEAN_PROP false', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'BOOLEAN_PROP', value: false, optionId: null });
    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toEqual({ kind: 'BOOLEAN_PROP', value: false });
  });

  it('GUESS 0', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'GUESS', guessValue: 0 });
    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toEqual({ kind: 'GUESS', guessValue: 0 });
  });

  it('OVER_UNDER over=false', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'OVER_UNDER', over: false, actualValue: null });
    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toEqual({ kind: 'OVER_UNDER', over: false });
  });

  it('OVER_UNDER actualValue=0', () => {
    const result = PredictionPayloadSchema.safeParse({ kind: 'OVER_UNDER', actualValue: 0, over: null });
    expect(result.success).toBe(true);
    if (result.success) expect(result.data).toEqual({ kind: 'OVER_UNDER', actualValue: 0 });
  });
});
