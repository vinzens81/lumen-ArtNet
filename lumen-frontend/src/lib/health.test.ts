import { describe, expect, it } from 'vitest';
import { outputIndicator } from './health';
import type { OutputHealth } from './types';

const healthy: OutputHealth = {
  level: 'ok',
  problems: [],
  renderRateHz: 40,
  targetRateHz: 40,
  renderErrors: 0,
  lastRenderError: null,
  watchdogRestarts: 0,
  artNetActive: true,
  packetsPerSecond: 40,
  sendErrorsPerSecond: 0,
  universes: 1,
};

describe('outputIndicator', () => {
  it('shows the packet rate while everything works', () => {
    const indicator = outputIndicator(healthy, true);
    expect(indicator).toMatchObject({ tone: 'ok', label: 'Art-Net 40/s' });
    expect(indicator.details).toContain('Render loop 40/40 fps');
  });

  it('puts problems first in the details', () => {
    const indicator = outputIndicator(
      { ...healthy, level: 'warning', packetsPerSecond: 0, problems: ['No Art-Net packets are sent'] },
      true,
    );
    expect(indicator.tone).toBe('warning');
    expect(indicator.label).toBe('Art-Net 0/s');
    expect(indicator.details.split('\n')[0]).toBe('No Art-Net packets are sent');
  });

  it('reports errors and a disabled or unknown output', () => {
    expect(outputIndicator({ ...healthy, level: 'error' }, true).label).toBe('Output error');
    expect(outputIndicator({ ...healthy, artNetActive: false }, true)).toMatchObject({ tone: 'off', label: 'Art-Net off' });
    expect(outputIndicator(healthy, false).tone).toBe('off');
    expect(outputIndicator(null, true).tone).toBe('off');
  });
});
