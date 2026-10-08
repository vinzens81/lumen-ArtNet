import { describe, expect, it } from 'vitest';
import changelog from '../../../CHANGELOG.md?raw';
import { codeSpans, parseChangelog } from './changelog';

describe('parseChangelog', () => {
  it('reads releases, sections and items with continued lines', () => {
    const releases = parseChangelog([
      '# Changelog', '', 'Intro text.', '',
      '## 1.0.81 – 1.0.83 · 2026-10-08', '', '### Added', '- One thing', '  that goes on.', '- Two', '',
      '### Fixed', '- Three', '',
      '## 1.0.1 – 1.0.43 · 2026-10-02', '### Added', '- Start',
    ].join('\n'));

    expect(releases).toEqual([
      { versions: '1.0.81 – 1.0.83', date: '2026-10-08', sections: [
        { heading: 'Added', items: ['One thing that goes on.', 'Two'] },
        { heading: 'Fixed', items: ['Three'] },
      ] },
      { versions: '1.0.1 – 1.0.43', date: '2026-10-02', sections: [{ heading: 'Added', items: ['Start'] }] },
    ]);
  });

  it('reads the changelog of the project', () => {
    const releases = parseChangelog(changelog);
    expect(releases.length).toBeGreaterThan(0);
    expect(releases.every((r) => r.date && r.sections.every((s) => s.items.length > 0))).toBe(true);
  });

  it('splits code spans', () => {
    expect(codeSpans('A line `//## Name ##` here')).toEqual([
      { text: 'A line ', code: false }, { text: '//## Name ##', code: true }, { text: ' here', code: false },
    ]);
  });
});
