import type {
  Composition, Move, PartyCell, PartyFader, PartyMatrix, PartyPage, PartyPlace, PartyStatus, Scene,
} from './types';

export const MAX_MATRIX_SIZE = 12;
export const MAX_PAGES = 12;
export const MAX_PAGE_NAME = 40;
export const DEFAULT_COLOR = '#1c2230';
export const DEFAULT_BORDER = '#3cf0ff';

/** A place of the page grid: a field over `span` × `rowSpan`, or an empty slot (1 × 1). */
export interface PagePlace {
  row: number;
  column: number;
  span: number;
  rowSpan: number;
  cell: PartyCell | null;
}

/** Every field once and every slot no field covers, row by row – what the page grid shows. */
export function pagePlaces(page: PartyPage): PagePlace[] {
  const places: PagePlace[] = [];
  for (let row = 0; row < page.rows; row++) {
    for (let column = 0; column < page.columns; column++) {
      const cell = cellAt(page, row, column);
      if (cell) {
        places.push({ row, column, cell, span: Math.max(1, Math.min(cell.span, page.columns - column)),
          rowSpan: Math.max(1, Math.min(cell.rowSpan, page.rows - row)) });
      } else if (!cellCovering(page, row, column)) {
        places.push({ row, column, span: 1, rowSpan: 1, cell: null });
      }
    }
  }
  return places;
}

/**
 * The rows of the page grid as CSS tracks: a track per row and one between each two rows (a gap, a line, or – while
 * editing – the place to click for a line). Filling the room, the rows share the height; otherwise they grow with
 * their fields.
 */
export function rowTracks(page: PartyPage, editing: boolean, fill: boolean): string {
  const row = fill ? 'minmax(0, 1fr)' : 'auto';
  return Array.from({ length: page.rows }, (_, r) => {
    if (r === 0) return row;
    const between = editing || page.separators.includes(r) ? '22px' : '10px';
    return `${between} ${row}`;
  }).join(' ');
}

/** The CSS grid row of a field or the line above a row: rows and the tracks between them alternate. */
export function gridRow(row: number, rowSpan = 1): string {
  return `${2 * row + 1} / span ${2 * rowSpan - 1}`;
}

/** The field starting at the place. */
export function cellAt(page: PartyPage, row: number, column: number): PartyCell | null {
  return page.cells.find((c) => c.row === row && c.column === column) ?? null;
}

/** The field covering the place, also when it starts further left or higher up. */
export function cellCovering(page: PartyPage, row: number, column: number): PartyCell | null {
  return page.cells.find((c) => row >= c.row && row < c.row + c.rowSpan && column >= c.column
    && column < c.column + c.span) ?? null;
}

const overlaps = (c: PartyCell, row: number, column: number, span: number, rowSpan: number) =>
  c.row < row + rowSpan && row < c.row + c.rowSpan && c.column < column + span && column < c.column + c.span;

/** Whether a field of that size at the place would reach into another field or out of the page. */
function fits(page: PartyPage, row: number, column: number, span: number, rowSpan: number): boolean {
  return row + rowSpan <= page.rows && column + span <= page.columns
    && !page.cells.some((c) => !(c.row === row && c.column === column) && overlaps(c, row, column, span, rowSpan));
}

/** How many columns the field at a place can cover with its rows: up to the next field or the end of the page. */
export function maxSpan(page: PartyPage, row: number, column: number, rowSpan = 1): number {
  let span = 1;
  while (fits(page, row, column, span + 1, rowSpan)) span++;
  return span;
}

/** How many rows the field at a place can cover with its columns. */
export function maxRowSpan(page: PartyPage, row: number, column: number, span = 1): number {
  let rowSpan = 1;
  while (fits(page, row, column, span, rowSpan + 1)) rowSpan++;
  return rowSpan;
}

/** A new field at a place: a text until it gets something to play. */
export function emptyCell(row: number, column: number): PartyCell {
  return { row, column, label: null, sceneId: null, compositionId: null, moveId: null, color: DEFAULT_COLOR,
    borderColor: DEFAULT_BORDER, mode: 'toggle', group: null, span: 1, rowSpan: 1, fader: null };
}

/** The page with the field put at its place, replacing every field it covers. */
export function withCell(page: PartyPage, cell: PartyCell): PartyPage {
  const cells = page.cells.filter((c) => !overlaps(c, cell.row, cell.column, cell.span, cell.rowSpan));
  return { ...page, cells: [...cells, cell].sort((a, b) => a.row - b.row || a.column - b.column) };
}

export function withoutCell(page: PartyPage, row: number, column: number): PartyPage {
  return { ...page, cells: page.cells.filter((c) => c.row !== row || c.column !== column) };
}

/** A new size; fields and lines outside it are dropped, fields reaching out of it get smaller. */
export function resized(page: PartyPage, rows: number, columns: number): PartyPage {
  const clamp = (n: number) => Math.max(1, Math.min(MAX_MATRIX_SIZE, Math.round(n) || 1));
  const r = clamp(rows);
  const c = clamp(columns);
  return {
    ...page,
    rows: r,
    columns: c,
    separators: page.separators.filter((s) => s < r),
    cells: page.cells.filter((cell) => cell.row < r && cell.column < c)
      .map((cell) => ({ ...cell, span: Math.min(cell.span, c - cell.column),
        rowSpan: Math.min(cell.rowSpan, r - cell.row) })),
  };
}

/** Switches the line above the row on or off. */
export function toggledSeparator(page: PartyPage, row: number): PartyPage {
  const separators = page.separators.includes(row)
    ? page.separators.filter((s) => s !== row)
    : [...page.separators, row].sort((a, b) => a - b);
  return { ...page, separators };
}

/** A new empty page, four by four. */
export function emptyPage(name: string): PartyPage {
  return { name, rows: 4, columns: 4, separators: [], cells: [] };
}

/** A name for a new page that no page has yet: "Page 2", "Page 3", … */
export function newPageName(matrix: PartyMatrix): string {
  const taken = new Set(matrix.pages.map((p) => p.name));
  let n = matrix.pages.length + 1;
  while (taken.has(`Page ${n}`)) n++;
  return `Page ${n}`;
}

/** The matrix with one page replaced. */
export function withPage(matrix: PartyMatrix, index: number, page: PartyPage): PartyMatrix {
  return { pages: matrix.pages.map((p, i) => (i === index ? page : p)) };
}

/** The matrix with a page moved one place to the left (-1) or right (+1). */
export function movedPage(matrix: PartyMatrix, index: number, by: -1 | 1): PartyMatrix {
  const target = index + by;
  if (target < 0 || target >= matrix.pages.length) return matrix;
  const pages = [...matrix.pages];
  [pages[index], pages[target]] = [pages[target], pages[index]];
  return { pages };
}

/** What the field shows: its label, else the name of what it plays. */
export function cellLabel(cell: PartyCell, scenes: Map<number, Scene>, compositions: Map<number, Composition>,
  moves: Map<number, Move> = new Map()): string {
  if (cell.label) return cell.label;
  if (cell.sceneId !== null) {
    const scene = scenes.get(cell.sceneId);
    return scene ? `${scene.number} ${scene.name}` : '(missing scene)';
  }
  if (cell.compositionId !== null) return compositions.get(cell.compositionId)?.name ?? '(missing composition)';
  if (cell.moveId !== null) return moves.get(cell.moveId)?.name ?? '(missing move)';
  if (cell.fader !== null) return faderLabel(cell.fader);
  return '';
}

export function playsSomething(cell: PartyCell): boolean {
  return cell.sceneId !== null || cell.compositionId !== null || cell.moveId !== null || cell.fader !== null;
}

export const samePlace = (a: PartyPlace, b: PartyPlace) =>
  a.page === b.page && a.row === b.row && a.column === b.column;

export function isOn(status: PartyStatus | null, place: PartyPlace): boolean {
  return status?.on.some((p) => samePlace(p, place)) ?? false;
}

/** The point last touched on a gradient or pad field, null if it has not been touched yet. */
export function faderPoint(status: PartyStatus | null, place: PartyPlace): { x: number; y: number } | null {
  const point = status?.points.find((p) => samePlace(p, place));
  return point ? { x: point.x, y: point.y } : null;
}

/** The status right after touching a gradient or pad: on, at the point (the server pushes the rest of its group). */
export function withSlide(status: PartyStatus, place: PartyPlace, x: number, y: number): PartyStatus {
  const here = { page: place.page, row: place.row, column: place.column };
  return {
    on: status.on.some((p) => samePlace(p, here)) ? status.on : [...status.on, here],
    points: [...status.points.filter((p) => !samePlace(p, here)), { ...here, x, y }],
  };
}

/** Where on a gradient a pointer is: 0 at its left edge, 1 at its right edge. */
export function pointAt(clientX: number, left: number, width: number): number {
  if (width <= 0) return 0;
  return Math.min(1, Math.max(0, (clientX - left) / width));
}

/** Where on a pad a pointer is: x 0–1 from the left, y 0–1 from the bottom. */
export function padPointAt(clientX: number, clientY: number, box: { left: number; top: number; width: number;
  height: number; }): { x: number; y: number } {
  return { x: pointAt(clientX, box.left, box.width), y: 1 - pointAt(clientY, box.top, box.height) };
}

/**
 * Colors run from red (hue 0°) to violet (300°), as on the server; intensity from dark to full. A CSS gradient for
 * the field.
 */
export function faderBackground(kind: PartyFader['kind']): string {
  if (kind === 'dimmer') return 'linear-gradient(to right, #000000, #fff4e0)';
  if (kind === 'pan-tilt') {
    return 'linear-gradient(rgb(255 255 255 / 6%) 1px, transparent 1px) 0 0 / 25% 25%, '
      + 'linear-gradient(90deg, rgb(255 255 255 / 6%) 1px, transparent 1px) 0 0 / 25% 25%, #0d1118';
  }
  const stops = [0, 60, 120, 180, 240, 300].map((hue, i) => `hsl(${hue} 100% 50%) ${i * 20}%`);
  return `linear-gradient(to right, ${stops.join(', ')})`;
}

/** What a gradient or pad field is called when it has no label. */
export function faderLabel(fader: PartyFader): string {
  return fader.kind === 'dimmer' ? 'Intensity' : fader.kind === 'color' ? 'Color' : 'Pan / tilt';
}

/** Group names used on all pages, sorted – offered when editing a field. */
export function groupNames(matrix: PartyMatrix): string[] {
  return [...new Set(matrix.pages.flatMap((page) => page.cells.flatMap((c) => (c.group ? [c.group] : []))))]
    .sort((a, b) => a.localeCompare(b));
}

/** Dark or light text, whichever reads better on the fill color. */
export function textColorFor(fill: string): string {
  const n = Number.parseInt(fill.slice(1), 16);
  if (!/^#[0-9a-fA-F]{6}$/.test(fill) || Number.isNaN(n)) return '#e8ecf4';
  const [r, g, b] = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.35 ? '#0b0d12' : '#e8ecf4';
}
