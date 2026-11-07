/** The part of a box that matters for scrolling. */
export interface Span {
  top: number;
  height: number;
}

/** Scroll position of a container that puts the element in its middle (never above the top). */
export function centeredScrollTop(scrollTop: number, container: Span, element: Span): number {
  return Math.max(0, scrollTop + element.top - container.top - (container.height - element.height) / 2);
}

/**
 * Scrolls the container so its element matching the selector is in the middle. Unlike scrollIntoView this moves only
 * the container, never the page or the headings above it.
 */
export function centerIn(container: HTMLElement | undefined, selector: string, behavior: ScrollBehavior = 'smooth') {
  const element = container?.querySelector<HTMLElement>(selector);
  if (!container || !element) return;
  const top = centeredScrollTop(container.scrollTop, container.getBoundingClientRect(), element.getBoundingClientRect());
  container.scrollTo({ top, behavior });
}

/** Scroll position of a container that puts the element `margin` below its top edge (never above the top). */
export function topScrollTop(scrollTop: number, container: Span, element: Span, margin: number): number {
  return Math.max(0, scrollTop + element.top - container.top - margin);
}

/**
 * Where the top of what is scrolled to starts: the element, or – as far as they fit into `height` with at least
 * `keep` pixels of the element below them – the blocks right before it (nearest first), e.g. the stage directions and
 * the light marker that lead into a speech.
 */
export function leadInTop(element: Span, before: Span[], height: number, keep: number): number {
  const bottom = element.top + Math.min(element.height, keep);
  let top = element.top;
  for (const block of before) {
    if (bottom - block.top > height) break;
    top = block.top;
  }
  return top;
}

/** Blocks of the script shown before a speech that belong to it: stage directions, light markers, empty lines. */
const LEAD_IN = ['direction', 'marker', 'paragraph'];

/**
 * Scrolls the container so the element matching the selector stands at its top, with what follows below it. A
 * sentence that starts a speech brings its speaker along, and the stage directions and light markers right before it
 * as far as they fit.
 */
export function topIn(container: HTMLElement | undefined, selector: string, margin = 8,
  behavior: ScrollBehavior = 'smooth') {
  const line = container?.querySelector<HTMLElement>(selector);
  if (!container || !line) return;
  const speech = line.closest<HTMLElement>('.speech');
  const element = speech?.querySelector('[data-line]') === line ? speech : line;
  const before: Span[] = [];
  // a light marker the operator stopped at starts its part: what stands before it belongs to the part before
  const first = element.classList.contains('marker') ? null : element.previousElementSibling;
  for (let block = first; block; block = block.previousElementSibling) {
    if (!LEAD_IN.some((kind) => block!.classList.contains(kind))) break;
    if (!block.classList.contains('paragraph')) before.push(block.getBoundingClientRect());
  }
  const box = container.getBoundingClientRect();
  const span = element.getBoundingClientRect();
  // the speech keeps at least half of the box
  const leadTop = leadInTop(span, before, box.height - 2 * margin, box.height / 2);
  const top = topScrollTop(container.scrollTop, box, { top: leadTop, height: span.height }, margin);
  // a hidden page (e.g. the tablet's screen is off) does not animate: jump, so the speech is in place when it shows
  container.scrollTo({ top, behavior: document.hidden ? 'auto' : behavior });
}

/** How long the text of a new scene glides into its place. */
const GLIDE_MS = 450;
/** How long the text of a new scene fades in when it was not read ahead. */
const FADE_MS = 180;

/**
 * How far the text of the new scene has to glide after GO: from where it stood below the cut (relative to the top of
 * the scrolling box) to where it stands now. A text that stood far outside the box starts at its edge, so it comes in
 * from there instead of rushing through. `null` when there is nothing to glide.
 */
export function glideOffset(from: number, to: number, height: number): number | null {
  const start = Math.min(height, Math.max(-height, from));
  const offset = start - to;
  return Math.abs(offset) < 1 ? null : offset;
}

/**
 * A scene change in a script with read-ahead: before GO the text of the coming scene stands below the cut, after GO
 * at the top. Scrolling there smoothly runs through the whole text in between, which is confusing during the show.
 * Instead the box jumps to the new place at once and the text glides from where it stood before, so the eye stays on
 * it; a scene that was not read ahead (Back, a scene chosen by hand) fades in.
 *
 * {@link capture} runs before the page shows the new scene (`$effect.pre`), {@link scroll} afterwards.
 */
export class SceneGlide {
  /** `undefined`: no scene change to land; `null`: fade in; a number: where the read-ahead text stood */
  #from: number | null | undefined = undefined;

  /** @param readAhead the new scene is the one whose text stands below the cut */
  capture(container: HTMLElement | undefined, readAhead: boolean) {
    const preview = readAhead ? container?.querySelector('.next-scene .preview, .next-scene .empty') : null;
    this.#from = container && preview
      ? preview.getBoundingClientRect().top - container.getBoundingClientRect().top
      : null;
  }

  /** Forgets a captured scene change, e.g. when the script is not shown. */
  reset() {
    this.#from = undefined;
  }

  /** Scrolls with `scrollTo`: smoothly as usual, at once after a scene change – then the text glides or fades in. */
  scroll(container: HTMLElement | undefined, scrollTo: (behavior: ScrollBehavior) => void) {
    const from = this.#from;
    this.#from = undefined;
    if (from === undefined || !container) {
      scrollTo('smooth');
      return;
    }
    scrollTo('auto');
    const scene = container.querySelector<HTMLElement>('.current-scene');
    const text = scene?.parentElement;
    if (!scene || !text || typeof text.animate !== 'function' || document.hidden
      || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    const box = container.getBoundingClientRect();
    const offset = from === null ? null : glideOffset(from, scene.getBoundingClientRect().top - box.top, box.height);
    if (offset !== null) {
      text.animate([{ transform: `translateY(${offset}px)` }, { transform: 'translateY(0)' }],
        { duration: GLIDE_MS, easing: 'cubic-bezier(0.2, 0, 0, 1)' });
    } else if (from === null) {
      text.animate([{ opacity: 0 }, { opacity: 1 }], { duration: FADE_MS, easing: 'ease-out' });
    }
  }
}
