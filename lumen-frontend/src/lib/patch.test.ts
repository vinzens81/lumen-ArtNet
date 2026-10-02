import { describe, expect, it } from 'vitest';
import { locateValues, nextFreeAddress, numberedNames, occupancy } from './patch';
import type { Fixture, Mode } from './types';

function fixture(id: number, address: number, channelCount: number | null): Fixture {
  return {
    id,
    name: `F${id}`,
    shortName: null,
    fixtureTypeId: 'par',
    mode: '4ch',
    universeId: 1,
    address,
    channelCount,
    lastAddress: channelCount === null ? null : address + channelCount - 1,
    position: null,
    sortOrder: 0,
  };
}

describe('occupancy', () => {
  it('marks every channel of a fixture and overlaps', () => {
    const cells = occupancy([fixture(1, 1, 4), fixture(2, 4, 2)]);

    expect(cells[0]).toEqual([1]);
    expect(cells[3]).toEqual([1, 2]);
    expect(cells[5]).toEqual([]);
  });

  it('clips fixtures at channel 512 and counts unknown types as one channel', () => {
    const cells = occupancy([fixture(1, 510, 8), fixture(2, 100, null)]);

    expect(cells[511]).toEqual([1]);
    expect(cells[99]).toEqual([2]);
    expect(cells[100]).toEqual([]);
  });
});

describe('nextFreeAddress', () => {
  it('finds the first gap that is large enough', () => {
    const fixtures = [fixture(1, 1, 8), fixture(2, 12, 4)];

    expect(nextFreeAddress(fixtures, 3)).toBe(9);
    expect(nextFreeAddress(fixtures, 4)).toBe(16);
  });

  it('can ignore the fixture being edited', () => {
    expect(nextFreeAddress([fixture(1, 1, 8)], 8, 1, 1)).toBe(1);
  });

  it('returns null when the universe is full', () => {
    expect(nextFreeAddress([fixture(1, 1, 510)], 4)).toBeNull();
  });
});

describe('numberedNames', () => {
  it('numbers names only for several fixtures', () => {
    expect(numberedNames('PAR', 1)).toEqual(['PAR']);
    expect(numberedNames('PAR', 3)).toEqual(['PAR 1', 'PAR 2', 'PAR 3']);
  });
});

describe('locateValues', () => {
  it('turns on dimmer and additive colors present in the mode', () => {
    const mode: Mode = {
      name: '4ch',
      dmxChannelCount: 4,
      channels: ['red', 'green', 'blue', 'strobe'].map((fn, i) => ({
        offset: i + 1,
        function: fn,
        name: fn,
        resolution: 8,
        dmxChannelCount: 1,
        defaultValue: 0,
        invert: false,
        fade: 'smooth',
        ranges: [],
      })),
    };

    expect(locateValues(mode)).toEqual({ dimmer: 1, red: 1, green: 1, blue: 1 });
  });
});
