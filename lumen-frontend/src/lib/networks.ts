import type { ArtNetNode, LocalNetwork, Universe } from './types';

/** e.g. "en7 · 2.0.0.10/8" */
export function networkLabel(network: LocalNetwork): string {
  return `${network.interfaceName} · ${network.address}/${network.prefixLength}`;
}

/** The universe is bound to an interface this computer does not have (any more) – it is not sent at all. */
export function interfaceMissing(universe: Pick<Universe, 'networkInterface'>, networks: LocalNetwork[]): boolean {
  return universe.networkInterface !== null && !networks.some((n) => n.interfaceName === universe.networkInterface);
}

/** Online nodes that receive the universe in auto mode – only those in its network if it is bound to one. */
export function receiversOf(
  universe: Pick<Universe, 'mode' | 'portAddress' | 'networkInterface'>,
  nodes: ArtNetNode[],
): ArtNetNode[] {
  if (universe.mode !== 'auto') return [];
  return nodes.filter(
    (n) =>
      n.online &&
      n.portAddresses.includes(universe.portAddress) &&
      (universe.networkInterface === null || n.networkInterface === universe.networkInterface),
  );
}
