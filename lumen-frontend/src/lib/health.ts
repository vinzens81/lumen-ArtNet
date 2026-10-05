import type { NodeHealth, OutputHealth } from './types';

export type Tone = 'ok' | 'warning' | 'error' | 'off';

export interface Indicator {
  tone: Tone;
  label: string;
  /** tooltip with all details */
  details: string;
}

/** Status pill for the light output in the top bar. */
export function outputIndicator(health: OutputHealth | null, connected: boolean): Indicator {
  if (!connected || health === null) {
    return { tone: 'off', label: 'Output ?', details: 'No connection to the server – the light keeps running' };
  }
  const rates = `Render loop ${health.renderRateHz.toFixed(0)}/${health.targetRateHz} fps`;
  const packets = health.artNetActive ? `Art-Net ${formatRate(health.packetsPerSecond)} packets/s` : 'Art-Net output off';
  const details = [...health.problems, rates, packets].join('\n');
  if (health.level === 'error') return { tone: 'error', label: 'Output error', details };
  if (!health.artNetActive) return { tone: health.level === 'ok' ? 'off' : 'warning', label: 'Art-Net off', details };
  const label = `Art-Net ${formatRate(health.packetsPerSecond)}/s`;
  return { tone: health.level === 'warning' ? 'warning' : 'ok', label, details };
}

function formatRate(value: number): string {
  return value >= 10 ? value.toFixed(0) : value.toFixed(1).replace(/\.0$/, '');
}

/** e.g. "ESP dimmer (192.168.0.150)" */
export function nodeName(node: NodeHealth): string {
  return node.name ? `${node.name} (${node.ipAddress})` : node.ipAddress;
}

/**
 * Status pill for the Art-Net nodes the project expects, e.g. "Nodes 2/3"; null while Art-Net is off or the project
 * expects no node. Manual addresses that never answered cannot be checked and do not count.
 */
export function nodesIndicator(health: OutputHealth | null, connected: boolean): Indicator | null {
  if (!connected || health === null || !health.artNetActive || health.nodes.length === 0) return null;
  const checked = health.nodes.filter((n) => n.state !== 'unchecked');
  const online = checked.filter((n) => n.state === 'online');
  const missing = checked.filter((n) => n.state === 'offline');
  const details = health.nodes.map((n) => `${STATE_TEXT[n.state]} ${nodeName(n)}`).join('\n');
  if (checked.length === 0) return { tone: 'off', label: 'Nodes ?', details };
  return {
    tone: missing.length > 0 ? 'error' : 'ok',
    label: `Nodes ${online.length}/${checked.length}`,
    details,
  };
}

const STATE_TEXT: Record<NodeHealth['state'], string> = {
  online: '●',
  offline: '✕ missing:',
  unchecked: '? not checkable:',
};

/** Nodes that answered in the previous status and are missing now – for a warning during the show. */
export function newlyMissing(previous: OutputHealth | null, current: OutputHealth | null): NodeHealth[] {
  if (previous === null || current === null) return [];
  const wasOnline = new Set(previous.nodes.filter((n) => n.state === 'online').map((n) => n.ipAddress));
  return current.nodes.filter((n) => n.state === 'offline' && wasOnline.has(n.ipAddress));
}
