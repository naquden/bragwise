import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { AvatarSeedSchema } from '../src/schemas';

interface AvatarFixture {
  emoji: string[];
  flagCodes: string[];
  legacyRegex: string;
}

const fixturePath = join(__dirname, 'fixtures', 'avatar', 'seeds.json');
const fixture: AvatarFixture = JSON.parse(readFileSync(fixturePath, 'utf8'));

describe('AvatarSeedSchema', () => {
  it('fixture has entries', () => {
    expect(fixture.emoji.length).toBeGreaterThan(0);
    expect(fixture.flagCodes.length).toBeGreaterThan(0);
  });

  it('accepts every emoji seed', () => {
    for (const seed of fixture.emoji) {
      expect(AvatarSeedSchema.safeParse(seed).success, `emoji: ${seed}`).toBe(true);
    }
  });

  it('accepts every flag seed', () => {
    for (const code of fixture.flagCodes) {
      const seed = `flag:${code}`;
      expect(AvatarSeedSchema.safeParse(seed).success, `flag seed: ${seed}`).toBe(true);
    }
  });

  it('accepts legacy seeds a1..a12', () => {
    for (let i = 1; i <= 12; i++) {
      expect(AvatarSeedSchema.safeParse(`a${i}`).success).toBe(true);
    }
  });

  it('accepts blank (legacy default)', () => {
    expect(AvatarSeedSchema.safeParse('').success).toBe(true);
  });

  it('rejects arbitrary free text', () => {
    expect(AvatarSeedSchema.safeParse('hello').success).toBe(false);
  });

  it('rejects script injection', () => {
    expect(AvatarSeedSchema.safeParse('<script>alert(1)</script>').success).toBe(false);
  });

  it('rejects overlong string', () => {
    expect(AvatarSeedSchema.safeParse('a'.repeat(33)).success).toBe(false);
  });

  it('rejects invalid flag code', () => {
    expect(AvatarSeedSchema.safeParse('flag:ZZ').success).toBe(false);
  });
});
