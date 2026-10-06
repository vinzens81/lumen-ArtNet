import { attributesOf } from './attributes';
import type { Fixture, FixtureType, Mode } from './types';

/** A patched fixture with what the programmer needs to control it. */
export interface ProgrammerEntry {
  fixture: Fixture;
  type: FixtureType;
  mode: Mode;
  attributes: string[];
}

/** Fixtures whose type and mode are known, in universe and address order. */
export function programmerEntries(
  fixtures: Fixture[],
  fixtureType: (id: string) => FixtureType | undefined,
): ProgrammerEntry[] {
  const result: ProgrammerEntry[] = [];
  for (const fixture of fixtures) {
    const type = fixtureType(fixture.fixtureTypeId);
    const mode = type?.modes.find((m) => m.name === fixture.mode);
    if (type && mode) result.push({ fixture, type, mode, attributes: attributesOf(mode) });
  }
  return result.sort((a, b) => a.fixture.universeId - b.fixture.universeId || a.fixture.address - b.fixture.address);
}
