import type { Channel, Mode } from './types';

/** Attribute id of a channel, e.g. `red`, `red@3` for head 3 or `generic:<name>`. */
export function attributeOf(channel: Channel): string {
  return channel.attribute;
}

/** Head of an attribute id: `red@3` → 3, `red` → 0 (the whole fixture). */
export function headOf(attribute: string): number {
  const match = /@(\d+)$/.exec(attribute);
  return match ? Number(match[1]) : 0;
}

/** The attribute of the whole fixture: `red@3` → `red`. */
export function baseOf(attribute: string): string {
  return attribute.replace(/@\d+$/, '');
}

/** The attribute for a head; head 0 is the whole fixture. */
export function withHead(attribute: string, head: number): string {
  return head === 0 ? baseOf(attribute) : `${baseOf(attribute)}@${head}`;
}

/**
 * All attributes of a mode (same as Mode.attributes() on the server): fixtures without a dimmer channel get a
 * virtual dimmer, heads without a dimmer channel one each, and every attribute of the heads also exists for the
 * whole fixture.
 */
export function attributesOf(mode: Mode): string[] {
  const hasDimmer = mode.channels.some((c) => c.function === 'dimmer');
  const hasHeadDimmers = mode.channels.some((c) => c.function === 'dimmer' && c.head > 0);
  const attributes = new Set<string>();
  if (!hasDimmer) attributes.add('dimmer');
  if (mode.heads > 0) mode.channels.forEach((c) => attributes.add(baseOf(attributeOf(c))));
  mode.channels.forEach((c) => attributes.add(attributeOf(c)));
  if (!hasHeadDimmers) for (let head = 1; head <= mode.heads; head++) attributes.add(`dimmer@${head}`);
  return [...attributes];
}

/** A value as the fixture shows it: the head's own value, otherwise the whole fixture's. */
export function valueFor(values: Record<string, number>, attribute: string): number | undefined {
  return values[attribute] ?? (headOf(attribute) > 0 ? values[baseOf(attribute)] : undefined);
}

/** Default value of an attribute (0–1) as defined in the fixture profile. */
export function defaultValue(mode: Mode, attribute: string): number {
  if (baseOf(attribute) === 'dimmer' && !mode.channels.some((c) => c.function === 'dimmer')) {
    return 0;
  }
  const channel = mode.channels.find((c) => attributeOf(c) === attribute)
    ?? mode.channels.find((c) => baseOf(attributeOf(c)) === baseOf(attribute));
  if (!channel) return 0;
  return channel.defaultValue / (channel.resolution === 16 ? 65535 : 255);
}

/** Only the values a fixture actually has. */
export function supportedValues(mode: Mode, values: Record<string, number>): Record<string, number> {
  const attributes = new Set(attributesOf(mode));
  return Object.fromEntries(Object.entries(values).filter(([attribute]) => attributes.has(attribute)));
}

/**
 * Only the heads have dimmers, the fixture as a whole has none – e.g. a dimmer pack with four channels (as
 * Mode.dimmersOnlyPerHead() on the server). Its channels are set directly; there is no master to set.
 */
export function dimmersOnlyPerHead(mode: Mode): boolean {
  return mode.channels.some((c) => c.function === 'dimmer' && c.head > 0)
    && !mode.channels.some((c) => c.function === 'dimmer' && c.head === 0);
}

/** Heads that have a dimmer channel, in order. */
export function headDimmers(mode: Mode): number[] {
  return [...new Set(mode.channels.filter((c) => c.function === 'dimmer' && c.head > 0).map((c) => c.head))]
    .sort((a, b) => a - b);
}

/** Whether the heads have nothing but their dimmer – then choosing a head offers nothing new. */
export function onlyHeadDimmers(mode: Mode): boolean {
  return mode.channels.every((c) => c.head === 0 || c.function === 'dimmer');
}
