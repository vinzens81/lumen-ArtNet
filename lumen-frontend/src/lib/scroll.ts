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
 * Scrolls the container so the element matching the selector stands at its top, with what follows below it. A
 * sentence that starts a speech brings its speaker along.
 */
export function topIn(container: HTMLElement | undefined, selector: string, margin = 8) {
  const line = container?.querySelector<HTMLElement>(selector);
  if (!container || !line) return;
  const speech = line.closest<HTMLElement>('.speech');
  const element = speech?.querySelector('[data-line]') === line ? speech : line;
  const top = topScrollTop(container.scrollTop, container.getBoundingClientRect(), element.getBoundingClientRect(),
    margin);
  container.scrollTo({ top, behavior: 'smooth' });
}
