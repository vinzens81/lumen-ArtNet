import { describe, expect, it } from 'vitest';
import { newlyMissing, nodesIndicator, outputIndicator } from './health';
import type { NodeHealth, OutputHealth } from './types';

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
  nodes: [],
};

describe('outputIndicator', () => {
  it('shows the packet rate while everything works', () => {
    const indicator = outputIndicator(healthy, true);
    expect(indicator).toMatchObject({ tone: 'ok', label: 'DMX 40/s' });
    expect(indicator.details).toContain('Render loop 40/40 fps');
  });

  it('puts problems first in the details', () => {
    const indicator = outputIndicator(
      { ...healthy, level: 'warning', packetsPerSecond: 0, problems: ['No DMX packets are sent'] },
      true,
    );
    expect(indicator.tone).toBe('warning');
    expect(indicator.label).toBe('DMX 0/s');
    expect(indicator.details.split('\n')[0]).toBe('No DMX packets are sent');
  });

  it('reports errors and a disabled or unknown output', () => {
    expect(outputIndicator({ ...healthy, level: 'error' }, true).label).toBe('Output error');
    expect(outputIndicator({ ...healthy, artNetActive: false }, true)).toMatchObject({ tone: 'off', label: 'Output off' });
    expect(outputIndicator(healthy, false).tone).toBe('off');
    expect(outputIndicator(null, true).tone).toBe('off');
  });
});

function node(ipAddress: string, state: NodeHealth['state'], name = ''): NodeHealth {
  return { ipAddress, name, state, universes: [1], lastSeen: null };
}

describe('nodesIndicator', () => {
  it('counts the checkable nodes that answer', () => {
    const indicator = nodesIndicator(
      { ...healthy, nodes: [node('192.168.0.150', 'online', 'ESP'), node('10.0.0.5', 'unchecked')] },
      true,
    );
    expect(indicator).toMatchObject({ tone: 'ok', label: 'Nodes 1/1' });
    expect(indicator?.details).toBe('● ESP (192.168.0.150)\n? not checkable: 10.0.0.5');
  });

  it('turns red when a node is missing', () => {
    const indicator = nodesIndicator(
      { ...healthy, nodes: [node('192.168.0.150', 'offline', 'ESP'), node('2.0.0.21', 'online')] },
      true,
    );
    expect(indicator).toMatchObject({ tone: 'error', label: 'Nodes 1/2' });
    expect(indicator?.details).toContain('✕ missing: ESP (192.168.0.150)');
  });

  it('is hidden without Art-Net, without nodes or without connection', () => {
    const nodes = [node('192.168.0.150', 'online')];
    expect(nodesIndicator({ ...healthy, artNetActive: false, nodes }, true)).toBeNull();
    expect(nodesIndicator(healthy, true)).toBeNull();
    expect(nodesIndicator({ ...healthy, nodes }, false)).toBeNull();
  });

  it('cannot judge only manual addresses', () => {
    expect(nodesIndicator({ ...healthy, nodes: [node('10.0.0.5', 'unchecked')] }, true)).toMatchObject({
      tone: 'off',
      label: 'Nodes ?',
    });
  });
});

describe('newlyMissing', () => {
  it('reports nodes that just stopped answering, once', () => {
    const before = { ...healthy, nodes: [node('192.168.0.150', 'online'), node('10.0.0.6', 'offline')] };
    const after = { ...healthy, nodes: [node('192.168.0.150', 'offline'), node('10.0.0.6', 'offline')] };

    expect(newlyMissing(before, after).map((n) => n.ipAddress)).toEqual(['192.168.0.150']);
    expect(newlyMissing(after, after)).toEqual([]);
    expect(newlyMissing(null, after)).toEqual([]);
  });
});
