import type { Channel, Mode } from './types';

/** Attribute id of a channel: the function, or `generic:<name>` for generic channels. */
export function attributeOf(channel: Channel): string {
  return channel.function === 'generic' ? `generic:${channel.name}` : channel.function;
}

/** All attributes of a mode; fixtures without a dimmer channel get a virtual dimmer. */
export function attributesOf(mode: Mode): string[] {
  const attributes = [...new Set(mode.channels.map(attributeOf))];
  return attributes.includes('dimmer') ? attributes : ['dimmer', ...attributes];
}

/** Default value of an attribute (0–1) as defined in the fixture profile. */
export function defaultValue(mode: Mode, attribute: string): number {
  if (attribute === 'dimmer' && !mode.channels.some((c) => c.function === 'dimmer')) {
    return 0;
  }
  const channel = mode.channels.find((c) => attributeOf(c) === attribute);
  if (!channel) return 0;
  return channel.defaultValue / (channel.resolution === 16 ? 65535 : 255);
}

/** Only the values a fixture actually has. */
export function supportedValues(mode: Mode, values: Record<string, number>): Record<string, number> {
  const attributes = new Set(attributesOf(mode));
  return Object.fromEntries(Object.entries(values).filter(([attribute]) => attributes.has(attribute)));
}
