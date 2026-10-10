import { describe, expect, it } from 'vitest';
import { centeredScrollTop } from './scroll';

describe('centeredScrollTop', () => {
  it('puts the element in the middle of the container', () => {
    // container 400 high at 100, element 40 high at 700 (already scrolled by 50)
    expect(centeredScrollTop(50, { top: 100, height: 400 }, { top: 700, height: 40 })).toBe(470);
  });

  it('never scrolls above the top', () => {
    expect(centeredScrollTop(0, { top: 100, height: 400 }, { top: 120, height: 40 })).toBe(0);
  });
});
