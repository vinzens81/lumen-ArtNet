import { describe, expect, it } from 'vitest';
import { speakerHue } from './script';
import { highlightScript } from './scriptHighlight';

const text = [
  '**Regie Anweisung**',
  'Maike Schmitz: Hallo Rolf! **lacht** Komm rein.',
  'He said: no speaker.',
  '//## Licht Marker 1 (infotext)##',
  'ROLF: Gut.',
  '//|| Pause **lang** ||',
].join('\n');

describe('highlightScript', () => {
  const segments = highlightScript(text);

  it('keeps every character in place', () => {
    expect(segments.map((s) => s.text).join('')).toBe(text);
  });

  it('colors speakers, stage directions, light markers and pauses', () => {
    const of = (kind: string) => segments.filter((s) => s.kind === kind).map((s) => s.text);
    expect(of('speaker')).toEqual(['Maike Schmitz', 'ROLF']);
    expect(of('direction')).toEqual(['Regie Anweisung', 'lacht']);
    expect(of('marker')).toEqual(['//## Licht Marker 1 (infotext)##']);
    expect(of('pause')).toEqual(['//|| Pause **lang** ||']);
    expect(of('syntax')).toEqual(['**', '**', ':', '**', '**', ':']);
    expect(segments.find((s) => s.text === 'ROLF')?.hue).toBe(speakerHue('ROLF'));
  });

  it('has nothing to color in an empty text', () => {
    expect(highlightScript('')).toEqual([]);
  });
});
