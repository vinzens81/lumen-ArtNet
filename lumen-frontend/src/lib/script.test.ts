import { describe, expect, it } from 'vitest';
import {
  parseScript, passages, scriptBlocks, sectionOf, speakerHue, stepPassage, type ScriptBlock, type ScriptPart,
} from './script';

function lines(text: string): string[] {
  return parseScript(text).parts.flatMap((part) => (part.kind === 'line' ? [part.text] : []));
}

describe('parseScript', () => {
  it('splits sentences at . ? ! and …', () => {
    expect(lines('To be. Or not? Never! Well… Then go.')).toEqual(['To be.', 'Or not?', 'Never!', 'Well…', 'Then go.']);
    expect(lines('What?! Wait... Now.')).toEqual(['What?!', 'Wait...', 'Now.']);
  });

  it('keeps closing quotes with their sentence', () => {
    expect(lines('He said: „Komm.“ Sie ging. "Stop!" Done.')).toEqual(['He said: „Komm.“', 'Sie ging.', '"Stop!"', 'Done.']);
  });

  it('does not split at numbers', () => {
    expect(lines('Am 3. Oktober kam er. Es kostet 3.50 Euro. Ende.')).toEqual([
      'Am 3. Oktober kam er.',
      'Es kostet 3.50 Euro.',
      'Ende.',
    ]);
  });

  it('does not split at acronyms and abbreviations', () => {
    expect(lines('Er kam z.B. aus den U.S.A. Danach ging er.')).toEqual(['Er kam z.B. aus den U.S.A. Danach ging er.']);
    expect(lines('Dr. Faust trifft Hr. Wagner, d. h. seinen Diener. Gut.')).toEqual([
      'Dr. Faust trifft Hr. Wagner, d. h. seinen Diener.',
      'Gut.',
    ]);
    expect(lines('Signed J. Smith. Go.')).toEqual(['Signed J. Smith.', 'Go.']);
  });

  it('does not split when the next word starts in lower case', () => {
    expect(lines('Ich… ich weiß nicht. Nein!')).toEqual(['Ich… ich weiß nicht.', 'Nein!']);
  });

  it('ends a sentence at a line break and keeps breaks and paragraphs', () => {
    const script = parseScript('ROMEO\nBut soft\n\n\nJULIET\nAy me.');
    expect(script.parts).toEqual<ScriptPart[]>([
      { kind: 'line', text: 'ROMEO', index: 0 },
      { kind: 'break' },
      { kind: 'line', text: 'But soft', index: 1 },
      { kind: 'paragraph' },
      { kind: 'line', text: 'JULIET', index: 2 },
      { kind: 'break' },
      { kind: 'line', text: 'Ay me.', index: 3 },
    ]);
  });

  it('takes **…** as stage directions that are not counted', () => {
    const script = parseScript('Hark. **Thunder, the lights flicker** Who goes there?\n**Exit**');
    expect(script.parts).toEqual<ScriptPart[]>([
      { kind: 'line', text: 'Hark.', index: 0 },
      { kind: 'note', text: 'Thunder, the lights flicker' },
      { kind: 'line', text: 'Who goes there?', index: 1 },
      { kind: 'break' },
      { kind: 'note', text: 'Exit' },
    ]);
    expect(script.lineCount).toBe(2);
    expect(script.noteCount).toBe(2);
  });

  it('ends a sentence before a stage direction and allows directions over several lines', () => {
    expect(parseScript('Half a sentence **long\ndirection** and the rest.').parts).toEqual<ScriptPart[]>([
      { kind: 'line', text: 'Half a sentence', index: 0 },
      { kind: 'note', text: 'long\ndirection' },
      { kind: 'line', text: 'and the rest.', index: 1 },
    ]);
  });

  it('keeps an unclosed ** as text', () => {
    expect(lines('Bold ** move. Yes.')).toEqual(['Bold ** move.', 'Yes.']);
  });

  it('has no parts for an empty text', () => {
    expect(parseScript('  \n\n ')).toEqual({ parts: [], lineCount: 0, noteCount: 0, speakers: [], markers: [] });
  });
});

describe('speakers', () => {
  it('takes a name and a colon at the start of a line as the speaker', () => {
    const script = parseScript('Maike Schmitz: Hallo Rolf! Wie geht es?\nROLF: Gut.');
    expect(script.parts).toEqual<ScriptPart[]>([
      { kind: 'speaker', name: 'Maike Schmitz' },
      { kind: 'line', text: 'Hallo Rolf!', index: 0 },
      { kind: 'line', text: 'Wie geht es?', index: 1 },
      { kind: 'break' },
      { kind: 'speaker', name: 'ROLF' },
      { kind: 'line', text: 'Gut.', index: 2 },
    ]);
    expect(script.speakers).toEqual(['Maike Schmitz', 'ROLF']);
  });

  it('needs capitalized words at the start of a line', () => {
    expect(parseScript('He said: Go.').speakers).toEqual([]);
    expect(parseScript('Go now. Rolf: no.').speakers).toEqual([]);
    expect(parseScript('Der Wirt:\nNoch ein Bier?').speakers).toEqual(['Der Wirt']);
    expect(parseScript('Uhrzeit:12 Uhr').speakers).toEqual([]);
  });

  it('indents speeches under their speaker and puts stage directions on lines of their own', () => {
    const blocks = scriptBlocks(parseScript(
      'Prolog.\nMaike: Hallo Rolf! **lacht** Wie geht es?\nweiter.\nRolf: Gut.\n\nErzähler: Später.'));
    expect(blocks.map(summary)).toEqual([
      'text: Prolog.',
      'speech Maike: Hallo Rolf!',
      'note: lacht',
      'speech Maike (continued): Wie geht es? / weiter.',
      'speech Rolf: Gut.',
      'paragraph',
      'speech Erzähler: Später.',
    ]);
  });

  it('gives a speaker always the same color', () => {
    expect(speakerHue('Maike')).toBe(speakerHue('Maike'));
    expect(speakerHue('Maike')).not.toBe(speakerHue('Rolf'));
  });
});

function summary(block: ScriptBlock): string {
  const text = (parts: ScriptPart[]) => parts.map((p) => (p.kind === 'line' ? p.text : p.kind === 'break' ? '/' : ''))
    .join(' ');
  switch (block.kind) {
    case 'speech': return `speech ${block.speaker}${block.continued ? ' (continued)' : ''}: ${text(block.parts)}`;
    case 'text': return `text: ${text(block.parts)}`;
    case 'note': return `note: ${block.text}`;
    case 'marker': return `marker ${block.section}: ${block.name} (${block.info})`;
    case 'paragraph': return 'paragraph';
  }
}

describe('light markers', () => {
  const script = parseScript([
    '**Regie Anweisung**',
    'Michael: Lorem ipsum. Dolor sit.',
    'Doro: Sadipscing elitr.',
    '',
    '//## Licht Marker 1 (infotext 1)##',
    '**Regie Anweisung 2**',
    'Michael: Invidunt ut labore.',
    '//## Licht Marker 2 ##',
    'Michael: Diam voluptua.',
  ].join('\n'));

  it('finds the markers with their info and the part every sentence is in', () => {
    expect(script.markers).toEqual([{ name: 'Licht Marker 1', info: 'infotext 1' }, { name: 'Licht Marker 2', info: '' }]);
    expect(script.lineCount).toBe(5);
    expect([0, 1, 2, 3, 4].map((line) => sectionOf(script, line))).toEqual([0, 0, 0, 1, 2]);
  });

  it('shows a marker on a line of its own between the speeches', () => {
    expect(scriptBlocks(script).map(summary)).toEqual([
      'note: Regie Anweisung',
      'speech Michael: Lorem ipsum. Dolor sit.',
      'speech Doro: Sadipscing elitr.',
      'marker 1: Licht Marker 1 (infotext 1)',
      'note: Regie Anweisung 2',
      'speech Michael: Invidunt ut labore.',
      'marker 2: Licht Marker 2 ()',
      'speech Michael: Diam voluptua.',
    ]);
  });
});

describe('stepping through the script', () => {
  it('steps over the whole speech of a speaker, stage directions included', () => {
    const script = parseScript('Prolog. Second line.\nMaike: Hallo! **lacht** Wie gehts?\nRolf: Gut.\n\nEnde.');

    expect(passages(script)).toEqual([{ first: 0, last: 1 }, { first: 2, last: 3 }, { first: 4, last: 4 },
      { first: 5, last: 5 }]);
    expect(stepPassage(script, 0, 1)).toBe(2);
    expect(stepPassage(script, 3, 1), 'from inside a speech').toBe(4);
    expect(stepPassage(script, 4, -1)).toBe(2);
    expect(stepPassage(script, 5, 1), 'stays within the scene').toBe(5);
    expect(stepPassage(script, 0, -1)).toBe(0);
  });

  it('starts a new passage at a light marker, even in a speech', () => {
    const script = parseScript('Maike: One.\n//## Light ##\nTwo.');
    expect(passages(script)).toEqual([{ first: 0, last: 0 }, { first: 1, last: 1 }]);
  });

  it('is null without sentences', () => {
    expect(stepPassage(parseScript('**Silence**'), 0, 1)).toBeNull();
  });
});
