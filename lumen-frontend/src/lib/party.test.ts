import { describe, expect, it } from 'vitest';
import {
  cellLabel, emptyCell, groupNames, isOn, matrixGrid, resized, textColorFor, toggledSeparator, withCell, withoutCell,
} from './party';
import type { Composition, PartyMatrix, Scene } from './types';

const matrix: PartyMatrix = {
  rows: 2,
  columns: 3,
  separators: [1],
  cells: [
    { ...emptyCell(0, 0), compositionId: 7, group: 'Colors' },
    { ...emptyCell(1, 2), label: 'Strobe', compositionId: 8, mode: 'flash', group: 'FX' },
  ],
};

describe('party matrix', () => {
  it('lays the fields out as a grid', () => {
    const grid = matrixGrid(matrix);
    expect(grid).toHaveLength(2);
    expect(grid[0][0]?.compositionId).toBe(7);
    expect(grid[0][1]).toBeNull();
    expect(grid[1][2]?.label).toBe('Strobe');
  });

  it('puts, removes and keeps fields inside when resizing', () => {
    const added = withCell(matrix, { ...emptyCell(0, 1), label: 'Text' });
    expect(added.cells.map((c) => [c.row, c.column])).toEqual([[0, 0], [0, 1], [1, 2]]);
    const replaced = withCell(added, { ...emptyCell(0, 1), label: 'Other' });
    expect(replaced.cells.filter((c) => c.row === 0 && c.column === 1).map((c) => c.label)).toEqual(['Other']);
    expect(withoutCell(matrix, 0, 0).cells).toHaveLength(1);

    const smaller = resized(matrix, 1, 2);
    expect(smaller.cells).toHaveLength(1);
    expect(smaller.separators).toEqual([]);
    expect(resized(matrix, 99, 0)).toMatchObject({ rows: 12, columns: 1 });
  });

  it('switches lines between rows', () => {
    expect(toggledSeparator(matrix, 1).separators).toEqual([]);
    expect(toggledSeparator({ ...matrix, rows: 4 }, 3).separators).toEqual([1, 3]);
  });

  it('labels a field by its text or what it plays', () => {
    const scenes = new Map([[3, { id: 3, number: '2', name: 'Fair' } as Scene]]);
    const compositions = new Map([[7, { id: 7, name: 'Red' } as Composition]]);
    expect(cellLabel(matrix.cells[0], scenes, compositions)).toBe('Red');
    expect(cellLabel(matrix.cells[1], scenes, compositions)).toBe('Strobe');
    expect(cellLabel({ ...emptyCell(0, 0), sceneId: 3 }, scenes, compositions)).toBe('2 Fair');
    expect(cellLabel({ ...emptyCell(0, 0), sceneId: 9 }, scenes, compositions)).toBe('(missing scene)');
  });

  it('knows the fields that are on and the groups', () => {
    expect(isOn({ on: [{ row: 1, column: 2 }] }, 1, 2)).toBe(true);
    expect(isOn(null, 1, 2)).toBe(false);
    expect(groupNames(matrix)).toEqual(['Colors', 'FX']);
  });

  it('picks readable text on the fill', () => {
    expect(textColorFor('#ffff00')).toBe('#0b0d12');
    expect(textColorFor('#1c2230')).toBe('#e8ecf4');
  });
});
