import { describe, expect, it } from 'vitest';
import {
  cellCovering, cellLabel, emptyCell, faderBackground, faderPoint, groupNames, isOn, matrixRows, maxSpan, pointAt,
  resized, textColorFor, toggledSeparator, withCell, withoutCell, withSlide,
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
  it('lays the fields out row by row', () => {
    const rows = matrixRows(matrix);
    expect(rows).toHaveLength(2);
    expect(rows[0][0].cell?.compositionId).toBe(7);
    expect(rows[0][1]).toEqual({ column: 1, span: 1, cell: null });
    expect(rows[1][2].cell?.label).toBe('Strobe');
  });

  it('lets a field cover several columns', () => {
    const wide: PartyMatrix = { ...matrix, cells: [{ ...emptyCell(0, 0), span: 2, fader: { kind: 'dimmer', fixtureIds: [1] } }] };
    expect(matrixRows(wide)[0].map((p) => [p.column, p.span])).toEqual([[0, 2], [2, 1]]);
    expect(cellCovering(wide, 0, 1)?.column).toBe(0);
    expect(cellCovering(wide, 0, 2)).toBeNull();
    expect(maxSpan(matrix, 1, 0)).toBe(2);
    expect(maxSpan(matrix, 0, 1)).toBe(2);

    const widened = withCell(matrix, { ...emptyCell(1, 0), span: 3, label: 'Wide' });
    expect(widened.cells.filter((c) => c.row === 1).map((c) => c.label)).toEqual(['Wide']);
    expect(resized(widened, 2, 2).cells.find((c) => c.row === 1)?.span).toBe(2);
  });

  it('follows gradients', () => {
    const status = { on: [], points: [] };
    const touched = withSlide(status, 1, 0, 0.3);
    expect(isOn(touched, 1, 0)).toBe(true);
    expect(faderPoint(touched, 1, 0)).toBe(0.3);
    expect(faderPoint(withSlide(touched, 1, 0, 0.8), 1, 0)).toBe(0.8);
    expect(faderPoint(status, 1, 0)).toBeNull();
    expect(pointAt(150, 100, 200)).toBe(0.25);
    expect(pointAt(50, 100, 200)).toBe(0);
    expect(pointAt(400, 100, 200)).toBe(1);
    expect(faderBackground('color')).toContain('hsl(300 100% 50%) 100%');
    expect(cellLabel({ ...emptyCell(0, 0), fader: { kind: 'color', fixtureIds: [1] } }, new Map(), new Map()))
      .toBe('Color');
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
    expect(isOn({ on: [{ row: 1, column: 2 }], points: [] }, 1, 2)).toBe(true);
    expect(isOn(null, 1, 2)).toBe(false);
    expect(groupNames(matrix)).toEqual(['Colors', 'FX']);
  });

  it('picks readable text on the fill', () => {
    expect(textColorFor('#ffff00')).toBe('#0b0d12');
    expect(textColorFor('#1c2230')).toBe('#e8ecf4');
  });
});
