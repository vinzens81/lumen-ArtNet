import { describe, expect, it } from 'vitest';
import { groupByValues } from './liveCommands';
import type { AttributeValues } from './types';

describe('groupByValues', () => {
  it('bundles fixtures with the same values into one command', () => {
    const commands = groupByValues(
      new Map([
        [1, { dimmer: 0.5, red: 1 }],
        [2, { red: 1, dimmer: 0.5 }],
      ]),
    );

    expect(commands).toEqual([{ fixtureIds: [1, 2], values: { dimmer: 0.5, red: 1 } }]);
  });

  it('keeps different values of the same attributes apart', () => {
    const commands = groupByValues(
      new Map([
        [1, { dimmer: 1, red: 1 }],
        [2, { dimmer: 1, red: 0 }],
      ]),
    );

    expect(commands).toEqual([
      { fixtureIds: [1], values: { dimmer: 1, red: 1 } },
      { fixtureIds: [2], values: { dimmer: 1, red: 0 } },
    ]);
  });

  it('keeps fixtures with different attributes apart', () => {
    const commands = groupByValues(
      new Map<number, AttributeValues>([
        [1, { red: 1 }],
        [2, { red: 1, white: 1 }],
      ]),
    );

    expect(commands).toHaveLength(2);
  });
});
