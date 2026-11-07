import { describe, expect, it } from 'vitest';
import { centeredScrollTop, glideOffset, leadInTop, topScrollTop } from './scroll';

describe('centeredScrollTop', () => {
  it('puts the element in the middle of the container', () => {
    // container 400 high at 100, element 40 high at 700 (already scrolled by 50)
    expect(centeredScrollTop(50, { top: 100, height: 400 }, { top: 700, height: 40 })).toBe(470);
  });

  it('never scrolls above the top', () => {
    expect(centeredScrollTop(0, { top: 100, height: 400 }, { top: 120, height: 40 })).toBe(0);
  });
});

describe('topScrollTop', () => {
  it('puts the element just below the top of the container', () => {
    expect(topScrollTop(50, { top: 100, height: 400 }, { top: 700, height: 40 }, 8)).toBe(642);
  });

  it('never scrolls above the top', () => {
    expect(topScrollTop(0, { top: 100, height: 400 }, { top: 104, height: 40 }, 8)).toBe(0);
  });
});

describe('leadInTop', () => {
  const speech = { top: 500, height: 80 };

  it('takes the stage directions and the marker before the speech along', () => {
    expect(leadInTop(speech, [{ top: 460, height: 30 }, { top: 420, height: 30 }], 400, 200)).toBe(420);
  });

  it('stops where the speech would no longer fit below them', () => {
    expect(leadInTop(speech, [{ top: 300, height: 150 }, { top: 100, height: 150 }], 300, 200)).toBe(300);
  });

  it('is the speech itself without anything before it', () => {
    expect(leadInTop(speech, [], 400, 200)).toBe(500);
  });
});

describe('glideOffset', () => {
  it('moves the text from below the cut to its new place', () => {
    // the read-ahead text stood 300 px below the top of the box, after GO it stands 8 px below it
    expect(glideOffset(300, 8, 600)).toBe(292);
  });

  it('starts a text from far below at the edge of the box', () => {
    expect(glideOffset(2000, 8, 600)).toBe(592);
  });

  it('starts a text from far above at the top edge', () => {
    expect(glideOffset(-2000, 8, 600)).toBe(-608);
  });

  it('has nothing to glide when the text stays where it is', () => {
    expect(glideOffset(8.4, 8, 600)).toBeNull();
  });
});
