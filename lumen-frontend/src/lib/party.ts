import type { Composition, Move, PartyCell, PartyFader, PartyMatrix, PartyStatus, Scene } from './types';

export const MAX_MATRIX_SIZE = 12;
export const DEFAULT_COLOR = '#1c2230';
export const DEFAULT_BORDER = '#3cf0ff';

/** A place in a row of the matrix: a field over `span` columns, or an empty place (one column). */
export interface MatrixPlace {
  column: number;
  span: number;
  cell: PartyCell | null;
}

/** The fields row by row; a field covering several columns is one place, the columns it covers are left out. */
export function matrixRows(matrix: PartyMatrix): MatrixPlace[][] {
  return Array.from({ length: matrix.rows }, (_, row) => {
    const places: MatrixPlace[] = [];
    for (let column = 0; column < matrix.columns;) {
      const cell = cellAt(matrix, row, column);
      const span = cell ? Math.max(1, Math.min(cell.span, matrix.columns - column)) : 1;
      places.push({ column, span, cell });
      column += span;
    }
    return places;
  });
}

/** The field starting at the place. */
export function cellAt(matrix: PartyMatrix, row: number, column: number): PartyCell | null {
  return matrix.cells.find((c) => c.row === row && c.column === column) ?? null;
}

/** The field covering the place, also when it starts further left. */
export function cellCovering(matrix: PartyMatrix, row: number, column: number): PartyCell | null {
  return matrix.cells.find((c) => c.row === row && column >= c.column && column < c.column + c.span) ?? null;
}

/** How many columns the field at a place can cover: up to the next field or the end of the row. */
export function maxSpan(matrix: PartyMatrix, row: number, column: number): number {
  const next = matrix.cells
    .filter((c) => c.row === row && c.column > column)
    .reduce((nearest, c) => Math.min(nearest, c.column), matrix.columns);
  return Math.max(1, next - column);
}

/** A new field at a place: a text until it gets something to play. */
export function emptyCell(row: number, column: number): PartyCell {
  return { row, column, label: null, sceneId: null, compositionId: null, moveId: null, color: DEFAULT_COLOR,
    borderColor: DEFAULT_BORDER, mode: 'toggle', group: null, span: 1, fader: null };
}

/** The matrix with the field put at its place, replacing every field it covers. */
export function withCell(matrix: PartyMatrix, cell: PartyCell): PartyMatrix {
  const end = cell.column + cell.span;
  const cells = matrix.cells.filter((c) => c.row !== cell.row || c.column + c.span <= cell.column
    || c.column >= end);
  return { ...matrix, cells: [...cells, cell].sort((a, b) => a.row - b.row || a.column - b.column) };
}

export function withoutCell(matrix: PartyMatrix, row: number, column: number): PartyMatrix {
  return { ...matrix, cells: matrix.cells.filter((c) => c.row !== row || c.column !== column) };
}

/** A new size; fields and lines outside it are dropped. */
export function resized(matrix: PartyMatrix, rows: number, columns: number): PartyMatrix {
  const clamp = (n: number) => Math.max(1, Math.min(MAX_MATRIX_SIZE, Math.round(n) || 1));
  const r = clamp(rows);
  const c = clamp(columns);
  return {
    rows: r,
    columns: c,
    separators: matrix.separators.filter((s) => s < r),
    cells: matrix.cells.filter((cell) => cell.row < r && cell.column < c)
      .map((cell) => (cell.column + cell.span > c ? { ...cell, span: c - cell.column } : cell)),
  };
}

/** Switches the line above the row on or off. */
export function toggledSeparator(matrix: PartyMatrix, row: number): PartyMatrix {
  const separators = matrix.separators.includes(row)
    ? matrix.separators.filter((s) => s !== row)
    : [...matrix.separators, row].sort((a, b) => a - b);
  return { ...matrix, separators };
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

export function isOn(status: PartyStatus | null, row: number, column: number): boolean {
  return status?.on.some((p) => p.row === row && p.column === column) ?? false;
}

/** The point last touched on a gradient field (0–1), null if it has not been touched yet. */
export function faderPoint(status: PartyStatus | null, row: number, column: number): number | null {
  return status?.points.find((p) => p.row === row && p.column === column)?.point ?? null;
}

/** The status right after touching a gradient: on, at the point (the server pushes the rest of its group). */
export function withSlide(status: PartyStatus, row: number, column: number, point: number): PartyStatus {
  const same = (p: { row: number; column: number }) => p.row === row && p.column === column;
  return {
    on: status.on.some(same) ? status.on : [...status.on, { row, column }],
    points: [...status.points.filter((p) => !same(p)), { row, column, point }],
  };
}

/** Where on a gradient a pointer is: 0 at its left edge, 1 at its right edge. */
export function pointAt(clientX: number, left: number, width: number): number {
  if (width <= 0) return 0;
  return Math.min(1, Math.max(0, (clientX - left) / width));
}

/**
 * Colors run from red (hue 0°) to violet (300°), as on the server; intensity from dark to full. A CSS gradient for
 * the field.
 */
export function faderBackground(kind: PartyFader['kind']): string {
  if (kind === 'dimmer') return 'linear-gradient(to right, #000000, #fff4e0)';
  const stops = [0, 60, 120, 180, 240, 300].map((hue, i) => `hsl(${hue} 100% 50%) ${i * 20}%`);
  return `linear-gradient(to right, ${stops.join(', ')})`;
}

/** What a gradient field is called when it has no label. */
export function faderLabel(fader: PartyFader): string {
  return fader.kind === 'dimmer' ? 'Intensity' : 'Color';
}

/** Group names used in the matrix, sorted – offered when editing a field. */
export function groupNames(matrix: PartyMatrix): string[] {
  return [...new Set(matrix.cells.flatMap((c) => (c.group ? [c.group] : [])))].sort((a, b) => a.localeCompare(b));
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
