import { describe, expect, it } from 'vitest';
import {
  cellCovering, cellLabel, emptyCell, faderBackground, faderLabel, faderPoint, gridRow, groupNames, isOn, maxRowSpan,
  maxSpan, movedPage, newPageName, padPointAt, pagePlaces, pointAt, resized, rowTracks, textColorFor,
  toggledSeparator, withCell, withoutCell, withPage, withSlide,
} from './party';
import type { Composition, PartyPage, Scene } from './types';

const page: PartyPage = {
  name: 'Party',
  rows: 2,
  columns: 3,
  separators: [1],
  cells: [
    { ...emptyCell(0, 0), compositionId: 7, group: 'Colors' },
    { ...emptyCell(1, 2), label: 'Strobe', compositionId: 8, mode: 'flash', group: 'FX' },
  ],
};

const at = (row: number, column: number, pageIndex = 0) => ({ page: pageIndex, row, column });

describe('party pages', () => {
  it('lays the fields and the free slots out row by row', () => {
    const places = pagePlaces(page);
    expect(places).toHaveLength(6);
    expect(places[0].cell?.compositionId).toBe(7);
    expect(places[1]).toEqual({ row: 0, column: 1, span: 1, rowSpan: 1, cell: null });
    expect(places[5].cell?.label).toBe('Strobe');
  });

  it('lets a field cover several columns and rows', () => {
    const pad = { ...emptyCell(0, 1), span: 2, rowSpan: 2, fader: { kind: 'pan-tilt' as const, fixtureIds: [1] } };
    const tall: PartyPage = { ...page, cells: [page.cells[0], pad] };
    expect(pagePlaces(tall).map((p) => [p.row, p.column, p.span, p.rowSpan])).toEqual([[0, 0, 1, 1], [0, 1, 2, 2],
      [1, 0, 1, 1]]);
    expect(cellCovering(tall, 1, 2)?.column).toBe(1);
    expect(cellCovering(tall, 1, 0)).toBeNull();
    expect(maxSpan(page, 1, 0)).toBe(2);
    expect(maxSpan(page, 0, 1, 2)).toBe(1);
    expect(maxRowSpan(page, 0, 1)).toBe(2);
    expect(maxRowSpan(page, 0, 2)).toBe(1);

    const widened = withCell(page, { ...emptyCell(0, 1), span: 2, rowSpan: 2, label: 'Big' });
    expect(widened.cells.map((c) => c.label ?? c.compositionId)).toEqual([7, 'Big']);
    expect(resized(widened, 1, 2).cells.find((c) => c.label === 'Big')).toMatchObject({ span: 1, rowSpan: 1 });
  });

  it('puts rows and the tracks between them on the grid', () => {
    expect(rowTracks(page, false, true)).toBe('minmax(0, 1fr) 22px minmax(0, 1fr)');
    expect(rowTracks({ ...page, separators: [] }, false, false)).toBe('auto 10px auto');
    expect(rowTracks({ ...page, separators: [] }, true, false)).toBe('auto 22px auto');
    expect(gridRow(0)).toBe('1 / span 1');
    expect(gridRow(1, 2)).toBe('3 / span 3');
  });

  it('follows gradients and pads', () => {
    const status = { on: [], points: [] };
    const touched = withSlide(status, at(1, 0), 0.3, 0.5);
    expect(isOn(touched, at(1, 0))).toBe(true);
    expect(isOn(touched, at(1, 0, 1))).toBe(false);
    expect(faderPoint(touched, at(1, 0))).toEqual({ x: 0.3, y: 0.5 });
    expect(faderPoint(withSlide(touched, at(1, 0), 0.8, 0.2), at(1, 0))).toEqual({ x: 0.8, y: 0.2 });
    expect(faderPoint(status, at(1, 0))).toBeNull();
    expect(pointAt(150, 100, 200)).toBe(0.25);
    expect(pointAt(50, 100, 200)).toBe(0);
    expect(pointAt(400, 100, 200)).toBe(1);
    expect(padPointAt(150, 125, { left: 100, top: 100, width: 200, height: 100 })).toEqual({ x: 0.25, y: 0.75 });
    expect(faderBackground('color')).toContain('hsl(300 100% 50%) 100%');
    expect(cellLabel({ ...emptyCell(0, 0), fader: { kind: 'color', fixtureIds: [1] } }, new Map(), new Map()))
      .toBe('Color');
    expect(faderLabel({ kind: 'pan-tilt', fixtureIds: [1] })).toBe('Pan / tilt');
  });

  it('puts, removes and keeps fields inside when resizing', () => {
    const added = withCell(page, { ...emptyCell(0, 1), label: 'Text' });
    expect(added.cells.map((c) => [c.row, c.column])).toEqual([[0, 0], [0, 1], [1, 2]]);
    const replaced = withCell(added, { ...emptyCell(0, 1), label: 'Other' });
    expect(replaced.cells.filter((c) => c.row === 0 && c.column === 1).map((c) => c.label)).toEqual(['Other']);
    expect(withoutCell(page, 0, 0).cells).toHaveLength(1);

    const smaller = resized(page, 1, 2);
    expect(smaller.cells).toHaveLength(1);
    expect(smaller.separators).toEqual([]);
    expect(resized(page, 99, 0)).toMatchObject({ rows: 12, columns: 1, name: 'Party' });
  });

  it('switches lines between rows', () => {
    expect(toggledSeparator(page, 1).separators).toEqual([]);
    expect(toggledSeparator({ ...page, rows: 4 }, 3).separators).toEqual([1, 3]);
  });

  it('adds, replaces and moves pages', () => {
    const matrix = { pages: [page, { ...page, name: 'Page 2', cells: [] }] };
    expect(newPageName(matrix)).toBe('Page 3');
    expect(newPageName({ pages: [page] })).toBe('Page 2');
    expect(withPage(matrix, 1, { ...page, name: 'Movers' }).pages.map((p) => p.name)).toEqual(['Party', 'Movers']);
    expect(movedPage(matrix, 1, -1).pages.map((p) => p.name)).toEqual(['Page 2', 'Party']);
    expect(movedPage(matrix, 1, 1)).toBe(matrix);
  });

  it('labels a field by its text or what it plays', () => {
    const scenes = new Map([[3, { id: 3, number: '2', name: 'Fair' } as Scene]]);
    const compositions = new Map([[7, { id: 7, name: 'Red' } as Composition]]);
    expect(cellLabel(page.cells[0], scenes, compositions)).toBe('Red');
    expect(cellLabel(page.cells[1], scenes, compositions)).toBe('Strobe');
    expect(cellLabel({ ...emptyCell(0, 0), sceneId: 3 }, scenes, compositions)).toBe('2 Fair');
    expect(cellLabel({ ...emptyCell(0, 0), sceneId: 9 }, scenes, compositions)).toBe('(missing scene)');
  });

  it('knows the fields that are on and the groups of all pages', () => {
    expect(isOn({ on: [at(1, 2)], points: [] }, at(1, 2))).toBe(true);
    expect(isOn(null, at(1, 2))).toBe(false);
    const other = { ...page, cells: [{ ...emptyCell(0, 0), compositionId: 9, group: 'Beams' }] };
    expect(groupNames({ pages: [page, other] })).toEqual(['Beams', 'Colors', 'FX']);
  });

  it('picks readable text on the fill', () => {
    expect(textColorFor('#ffff00')).toBe('#0b0d12');
    expect(textColorFor('#1c2230')).toBe('#e8ecf4');
  });
});
