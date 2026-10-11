import { describe, expect, it } from 'vitest';
import { backupReason, fileSize, shortTime, showClock } from './format';

describe('fileSize', () => {
  it('uses readable units', () => {
    expect(fileSize(512)).toBe('512 B');
    expect(fileSize(2048)).toBe('2.0 KB');
    expect(fileSize(48 * 1024)).toBe('48 KB');
    expect(fileSize(3.5 * 1024 * 1024)).toBe('3.5 MB');
  });
});

describe('backupReason', () => {
  it('reads the reason from the file name', () => {
    expect(backupReason('lumen-20261002-193005-000-show.db')).toBe('Show started');
    expect(backupReason('lumen-20261002-193005-000-2-manual.db')).toBe('Manual');
    expect(backupReason('lumen-20261002-193005-000-other.db')).toBe('other');
  });
});

describe('shortTime', () => {
  it('shows the day only when it is not today', () => {
    const now = new Date(2026, 9, 5, 21, 0);
    expect(shortTime(new Date(2026, 9, 5, 20, 4), now)).toBe('20:04');
    expect(shortTime(new Date(2026, 9, 4, 9, 30), now)).toBe('4 Oct 09:30');
  });
});

describe('showClock', () => {
  it('shows minutes and seconds, hours from an hour on', () => {
    expect(showClock({ showTimeMs: 65_000, showTimeRunning: false }, 0)).toBe('1:05');
    expect(showClock({ showTimeMs: 3_725_400, showTimeRunning: false }, 0)).toBe('1:02:05');
  });

  it('counts on while the show runs and stands still after its end', () => {
    expect(showClock({ showTimeMs: 59_500, showTimeRunning: true }, 600)).toBe('1:00');
    expect(showClock({ showTimeMs: 59_500, showTimeRunning: false }, 600)).toBe('0:59');
  });

  it('has no time before the show started', () => {
    expect(showClock({ showTimeMs: null, showTimeRunning: false }, 1000)).toBe('–:––');
    expect(showClock(null, 0)).toBe('–:––');
  });
});
