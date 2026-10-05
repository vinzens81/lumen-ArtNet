import { describe, expect, it } from 'vitest';
import { beatAt, buttonKey, commonIntensity, commonPalette, selectedFixtures } from './party';
import type { Group, PartyStatus } from './types';

const groups: Group[] = [
  { id: 1, name: 'Front', color: null, sortOrder: 0, fixtureIds: [10, 11] },
  { id: 2, name: 'Back', color: null, sortOrder: 1, fixtureIds: [11, 12] },
];

function status(fixtures: PartyStatus['fixtures']): PartyStatus {
  return { bpm: 120, beatMs: 500, msSinceBeat: 0, fixtures, buttons: [], palettes: [] };
}

describe('selectedFixtures', () => {
  it('joins the groups without duplicates', () => {
    expect(selectedFixtures(groups, new Set([1, 2]), false, [])).toEqual([10, 11, 12]);
    expect(selectedFixtures(groups, new Set(), false, [])).toEqual([]);
    expect(selectedFixtures(groups, new Set([1]), true, [10, 11, 12, 13])).toEqual([10, 11, 12, 13]);
  });
});

describe('beatAt', () => {
  it('counts beats in a bar of four from the status', () => {
    expect(beatAt(0, 500, 0)).toEqual({ beat: 0, phase: 0 });
    expect(beatAt(250, 500, 500)).toEqual({ beat: 1, phase: 0.5 });
    expect(beatAt(0, 500, 2000).beat).toBe(0);
  });
});

describe('commonIntensity / commonPalette', () => {
  it('is only set when all fixtures agree', () => {
    const s = status({ 10: { palette: 'red', intensity: 1 }, 11: { palette: 'red', intensity: 0.5 } });
    expect(commonIntensity([10], s)).toBe(1);
    expect(commonIntensity([10, 11], s)).toBeNull();
    expect(commonIntensity([12], s)).toBe(0);
    expect(commonPalette([10, 11], s)).toBe('red');
    expect(commonPalette([10, 12], s)).toBeNull();
  });
});

describe('buttonKey', () => {
  it('numbers the first nine buttons', () => {
    expect(buttonKey(0)).toBe('1');
    expect(buttonKey(8)).toBe('9');
    expect(buttonKey(9)).toBeNull();
  });
});
