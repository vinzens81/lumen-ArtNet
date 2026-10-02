import type { OutputHealth } from './types';

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
