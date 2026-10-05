import type { AttributeValues } from './types';

/** One `programmer.set` command: the same values for all its fixtures. */
export type ProgrammerCommand = { fixtureIds: number[]; values: AttributeValues };

/**
 * Bundles pending programmer values into as few commands as possible: fixtures with exactly the same attributes
 * and values share one command, fixtures with different values (e.g. a chase step) each keep their own.
 */
export function groupByValues(perFixture: Map<number, AttributeValues>): ProgrammerCommand[] {
  const commands = new Map<string, ProgrammerCommand>();
  for (const [fixtureId, values] of perFixture) {
    const signature = JSON.stringify(Object.entries(values).sort(([a], [b]) => a.localeCompare(b)));
    const command = commands.get(signature);
    if (command) command.fixtureIds.push(fixtureId);
    else commands.set(signature, { fixtureIds: [fixtureId], values: { ...values } });
  }
  return [...commands.values()];
}
