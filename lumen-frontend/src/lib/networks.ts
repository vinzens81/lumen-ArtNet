import type { ArtNetNode, LocalNetwork, OutputProtocol, Universe } from './types';

/** e.g. "en7 · 2.0.0.10/8" */
export function networkLabel(network: LocalNetwork): string {
  return `${network.interfaceName} · ${network.address}/${network.prefixLength}`;
}

/** The universe is bound to an interface this computer does not have (any more) – it is not sent at all. */
export function interfaceMissing(universe: Pick<Universe, 'networkInterface'>, networks: LocalNetwork[]): boolean {
  return universe.networkInterface !== null && !networks.some((n) => n.interfaceName === universe.networkInterface);
}

export const sendsArtNet = (protocol: OutputProtocol) => protocol !== 'sacn';
export const sendsSacn = (protocol: OutputProtocol) => protocol !== 'artnet';

/** The multicast group of an sACN universe, e.g. 239.255.1.2 for 258. */
export function sacnMulticastAddress(universe: number): string {
  return `239.255.${(universe >> 8) & 0xff}.${universe & 0xff}`;
}

/**
 * Online nodes that receive the universe by Art-Net in auto mode – only those in its network if it is bound to one.
 */
export function receiversOf(
  universe: Pick<Universe, 'mode' | 'portAddress' | 'networkInterface' | 'protocol'>,
  nodes: ArtNetNode[],
): ArtNetNode[] {
  if (universe.mode !== 'auto' || !sendsArtNet(universe.protocol)) return [];
  return nodes.filter(
    (n) =>
      n.online &&
      n.portAddresses.includes(universe.portAddress) &&
      (universe.networkInterface === null || n.networkInterface === universe.networkInterface),
  );
}
