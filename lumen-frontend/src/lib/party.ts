import type { Composition, PartyCell, PartyMatrix, PartyStatus, Scene } from './types';

export const MAX_MATRIX_SIZE = 12;
export const DEFAULT_COLOR = '#1c2230';
export const DEFAULT_BORDER = '#3cf0ff';

/** The fields as a grid: rows of columns, null for an empty place. */
export function matrixGrid(matrix: PartyMatrix): (PartyCell | null)[][] {
  const grid: (PartyCell | null)[][] = Array.from({ length: matrix.rows },
    () => Array.from({ length: matrix.columns }, () => null));
  for (const cell of matrix.cells) {
    if (cell.row < matrix.rows && cell.column < matrix.columns) grid[cell.row][cell.column] = cell;
  }
  return grid;
}

export function cellAt(matrix: PartyMatrix, row: number, column: number): PartyCell | null {
  return matrix.cells.find((c) => c.row === row && c.column === column) ?? null;
}

/** A new field at a place: a text until it gets something to play. */
export function emptyCell(row: number, column: number): PartyCell {
  return { row, column, label: null, sceneId: null, compositionId: null, color: DEFAULT_COLOR,
    borderColor: DEFAULT_BORDER, mode: 'toggle', group: null };
}

/** The matrix with the field put at its place (replacing what was there). */
export function withCell(matrix: PartyMatrix, cell: PartyCell): PartyMatrix {
  const cells = matrix.cells.filter((c) => c.row !== cell.row || c.column !== cell.column);
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
    cells: matrix.cells.filter((cell) => cell.row < r && cell.column < c),
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
export function cellLabel(cell: PartyCell, scenes: Map<number, Scene>, compositions: Map<number, Composition>): string {
  if (cell.label) return cell.label;
  if (cell.sceneId !== null) {
    const scene = scenes.get(cell.sceneId);
    return scene ? `${scene.number} ${scene.name}` : '(missing scene)';
  }
  if (cell.compositionId !== null) return compositions.get(cell.compositionId)?.name ?? '(missing composition)';
  return '';
}

export function playsSomething(cell: PartyCell): boolean {
  return cell.sceneId !== null || cell.compositionId !== null;
}

export function isOn(status: PartyStatus | null, row: number, column: number): boolean {
  return status?.on.some((p) => p.row === row && p.column === column) ?? false;
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
