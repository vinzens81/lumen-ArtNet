import { describe, expect, it } from 'vitest';
import { backupReason, fileSize } from './format';

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
