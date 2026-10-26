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
export function centerIn(container: HTMLElement | undefined, selector: string) {
  const element = container?.querySelector<HTMLElement>(selector);
  if (!container || !element) return;
  const top = centeredScrollTop(container.scrollTop, container.getBoundingClientRect(), element.getBoundingClientRect());
  container.scrollTo({ top, behavior: 'smooth' });
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
export function topIn(container: HTMLElement | undefined, selector: string, margin = 8) {
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
  container.scrollTo({ top, behavior: document.hidden ? 'auto' : 'smooth' });
}
