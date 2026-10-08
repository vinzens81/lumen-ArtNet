/** A release of the changelog: a version range and date, with what was added, changed, fixed or removed. */
export interface Release {
  versions: string;
  date: string;
  sections: { heading: string; items: string[] }[];
}

/**
 * Reads CHANGELOG.md: `## <versions> · <date>` starts a release, `### <heading>` a section, `- ` an item (indented
 * lines continue it). Everything else is left out.
 */
export function parseChangelog(markdown: string): Release[] {
  const releases: Release[] = [];
  for (const line of markdown.split('\n')) {
    const release = releases[releases.length - 1];
    const section = release?.sections[release.sections.length - 1];
    if (line.startsWith('## ')) {
      const [versions, date = ''] = line.slice(3).split(' · ');
      releases.push({ versions: versions.trim(), date: date.trim(), sections: [] });
    } else if (line.startsWith('### ') && release) {
      release.sections.push({ heading: line.slice(4).trim(), items: [] });
    } else if (line.startsWith('- ') && section) {
      section.items.push(line.slice(2).trim());
    } else if (/^\s+\S/.test(line) && section && section.items.length > 0) {
      section.items[section.items.length - 1] += ` ${line.trim()}`;
    }
  }
  return releases;
}

/** Text with `code` spans, for rendering. */
export function codeSpans(text: string): { text: string; code: boolean }[] {
  return text.split('`').map((part, i) => ({ text: part, code: i % 2 === 1 })).filter((part) => part.text !== '');
}
