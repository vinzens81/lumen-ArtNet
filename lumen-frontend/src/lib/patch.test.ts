import { describe, expect, it } from 'vitest';
import {
  channelOwners, commonType, locateValues, patchedChannels, nextFreeAddress, numberedNames, occupancy, plannedFixtures,
  rangeToggled, toggled,
} from './patch';
import type { Channel, Fixture, Mode } from './types';

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
    stageShape: { kind: 'auto', rotation: 0, length: 0.15 },
    limits: null,
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
      heads: 0,
      channels: ['red', 'green', 'blue', 'strobe'].map((fn, i) => ({
        offset: i + 1,
        function: fn,
        attribute: fn,
        head: 0,
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

describe('channelOwners', () => {
  it('maps every DMX channel to its fixture and profile channel', () => {
    const head = { ...fixture(1, 10, 3), name: 'Spot' };
    const channel = (name: string, offset: number, dmxChannelCount: number) =>
      ({ name, offset, dmxChannelCount }) as Channel;
    const modes = new Map<number, Mode>([
      [1, { name: '3ch', dmxChannelCount: 3, heads: 0, channels: [channel('Pan', 0, 2), channel('Dimmer', 2, 1)] }],
    ]);

    const owners = channelOwners([head], modes);

    expect(owners[8]).toBeNull();
    expect(owners[9]).toEqual({ fixture: head, channel: 'Pan' });
    expect(owners[10]?.channel).toBe('Pan fine');
    expect(owners[11]?.channel).toBe('Dimmer');
    expect(owners[12]).toBeNull();
  });

  it('lists only the channels fixtures use, by fixture in address order', () => {
    const channel = (name: string, offset: number) => ({ name, offset, dmxChannelCount: 1 }) as Channel;
    const mode: Mode = { name: '2ch', dmxChannelCount: 2, heads: 0, channels: [channel('Dimmer', 0), channel('Red', 1)] };
    const modes = new Map<number, Mode>([[1, mode], [2, mode]]);

    const result = patchedChannels([fixture(1, 300, 2), fixture(2, 5, 2)], modes);

    expect(result.map((r) => r.fixture.id)).toEqual([2, 1]);
    expect(result[1].channels).toEqual([{ channel: 300, name: 'Dimmer' }, { channel: 301, name: 'Red' }]);
  });
});

describe('plannedFixtures', () => {
  it('patches several fixtures back to back with numbered names', () => {
    const base = { name: 'LED PAR', shortName: 'P', fixtureTypeId: 'par', mode: '4ch', universeId: 1, position: null, sortOrder: 0 };

    const planned = plannedFixtures(base, 10, 4, 8);

    expect(planned.map((f) => f.address)).toEqual([10, 14, 18, 22, 26, 30, 34, 38]);
    expect(planned.map((f) => f.name)).toEqual(['LED PAR 1', 'LED PAR 2', 'LED PAR 3', 'LED PAR 4', 'LED PAR 5',
      'LED PAR 6', 'LED PAR 7', 'LED PAR 8']);
    expect(planned.every((f) => f.shortName === null)).toBe(true);
    expect(plannedFixtures(base, 1, 4, 1)).toEqual([{ ...base, address: 1 }]);
  });
});

describe('selection', () => {
  it('toggles a fixture in and out', () => {
    expect(toggled([1, 2], 3)).toEqual([1, 2, 3]);
    expect(toggled([1, 2, 3], 2)).toEqual([1, 3]);
  });

  it('ticks or unticks the range from the last clicked fixture', () => {
    const order = [10, 11, 12, 13, 14];

    expect(rangeToggled([10], order, 10, 13)).toEqual([10, 11, 12, 13]);
    expect(rangeToggled([10, 11, 12, 13], order, 13, 11)).toEqual([10]);
    expect(rangeToggled([14], order, 14, 12)).toEqual([14, 12, 13]);
  });

  it('toggles only the fixture without an anchor in the list', () => {
    expect(rangeToggled([1], [10, 11], null, 11)).toEqual([1, 11]);
    expect(rangeToggled([1], [10, 11], 99, 11)).toEqual([1, 11]);
  });

  it('finds the type all fixtures share', () => {
    expect(commonType([fixture(1, 1, 4), fixture(2, 5, 4)])).toBe('par');
    expect(commonType([fixture(1, 1, 4), { ...fixture(2, 5, 4), fixtureTypeId: 'spot' }])).toBeNull();
    expect(commonType([])).toBeNull();
  });
});
