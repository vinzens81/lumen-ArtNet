import { describe, expect, it } from 'vitest';
import { interfaceMissing, networkLabel, receiversOf } from './networks';
import type { ArtNetNode, LocalNetwork } from './types';

const wifi: LocalNetwork = { interfaceName: 'en0', address: '192.168.0.23', prefixLength: 24, broadcastAddress: '192.168.0.255' };

function node(ipAddress: string, networkInterface: string | null, online = true): ArtNetNode {
  return {
    ipAddress, bindIndex: 1, shortName: ipAddress, longName: '', macAddress: '', portAddresses: [0], nodeReport: '',
    lastSeen: '2026-10-05T20:00:00Z', online, networkInterface,
  };
}

describe('networkLabel', () => {
  it('shows interface, address and prefix', () => {
    expect(networkLabel(wifi)).toBe('en0 · 192.168.0.23/24');
  });
});

describe('interfaceMissing', () => {
  it('is only true for a bound universe whose interface is gone', () => {
    expect(interfaceMissing({ networkInterface: null }, [])).toBe(false);
    expect(interfaceMissing({ networkInterface: 'en0' }, [wifi])).toBe(false);
    expect(interfaceMissing({ networkInterface: 'en7' }, [wifi])).toBe(true);
  });
});

describe('receiversOf', () => {
  const nodes = [node('192.168.0.150', 'en0'), node('2.0.0.21', 'en7'), node('192.168.0.151', 'en0', false)];

  it('takes online nodes of all networks for an unbound universe', () => {
    expect(receiversOf({ mode: 'auto', portAddress: 0, networkInterface: null }, nodes).map((n) => n.ipAddress))
      .toEqual(['192.168.0.150', '2.0.0.21']);
  });

  it('takes only the nodes in the network of a bound universe', () => {
    expect(receiversOf({ mode: 'auto', portAddress: 0, networkInterface: 'en7' }, nodes).map((n) => n.ipAddress))
      .toEqual(['2.0.0.21']);
  });

  it('has none outside auto mode or on another port', () => {
    expect(receiversOf({ mode: 'broadcast', portAddress: 0, networkInterface: null }, nodes)).toEqual([]);
    expect(receiversOf({ mode: 'auto', portAddress: 5, networkInterface: null }, nodes)).toEqual([]);
  });
});
