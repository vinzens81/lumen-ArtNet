import { describe, expect, it } from 'vitest';
import {
  canMove, insertAfter, isFixedPosition, limitsOf, positionedFixtures, lineNear, movePosition, newMove, noCorrection, onceSeconds, outside, pathOf,
  placedFigure, pointAt, reorder, shareAt, snap, startPoint, templatePoints,
} from './moves';
import type { FigurePoint, Fixture, Mode, MoveFixture } from './types';

const SQUARE: FigurePoint[] = [{ x: 64, y: 64 }, { x: 192, y: 64 }, { x: 192, y: 192 }, { x: 64, y: 192 }];

function move(fixtures: number, extra: Partial<ReturnType<typeof newMove>> = {}) {
  return { ...newMove('Move', 1, Array.from({ length: fixtures }, (_, i) => i + 1)), ...extra };
}

function expectPoint(actual: FigurePoint, x: number, y: number) {
  expect(actual.x).toBeCloseTo(x, 6);
  expect(actual.y).toBeCloseTo(y, 6);
}

describe('moves', () => {
  it('follows a closed figure at an even speed and wraps around', () => {
    const path = pathOf(SQUARE, true);
    expect(path.length).toBe(512);
    expect(shareAt(path, 2)).toBe(0.5);
    expectPoint(pointAt(path, 0.875), 64, 128);
    expectPoint(movePosition(move(1), path, 1, 0), 128, 64); // 8 s per round
    expectPoint(movePosition(move(1), path, 9, 0), 128, 64);
  });

  it('keeps an open figure at its ends and jumps back in a loop', () => {
    const path = pathOf(SQUARE, false);
    expectPoint(pointAt(path, 2), 64, 192);
    expectPoint(movePosition(move(1), path, 8, 0), 64, 64);
  });

  it('starts each further fixture at a later point', () => {
    const path = pathOf(SQUARE, true);
    const m = move(4, { stepOffset: 1 });
    expectPoint(movePosition(m, path, 0, 3), 64, 192);
    expect(startPoint(1, 4, 3)).toBe(4);
    expect(startPoint(3, 4, 2)).toBe(3);
  });

  it('runs ping-pong back every other round', () => {
    const path = pathOf(SQUARE, false);
    const m = move(1, { repeat: 'ping_pong' });
    expectPoint(movePosition(m, path, 8, 0), 64, 192);
    expectPoint(movePosition(m, path, 12, 0), 192, 128);
  });

  it('runs once in sequence or together', () => {
    const open = pathOf(SQUARE, false);
    const snake = move(2, { repeat: 'once', stepOffset: 1 });
    expectPoint(movePosition(snake, open, 8 / 3, 1), 64, 64);
    expectPoint(movePosition(snake, open, 100, 1), 64, 192);
    expect(onceSeconds(snake, open)).toBeCloseTo(8 + 8 / 3, 6);

    const closed = pathOf(SQUARE, true);
    const together = move(2, { repeat: 'once', onceStart: 'together', stepOffset: 2 });
    expectPoint(movePosition(together, closed, 100, 1), 192, 192);
    expect(onceSeconds(together, closed)).toBe(8);
  });

  it('mirrors, reverses and shifts per fixture', () => {
    const path = pathOf(SQUARE, true);
    const fixtures: MoveFixture[] = [
      { ...noCorrection(1), mirrorX: true },
      { ...noCorrection(2), reverse: true },
      { ...noCorrection(3), shiftX: -18, shiftY: 6 },
    ];
    const m = move(3, { fixtures });
    expectPoint(movePosition(m, path, 1, 0), 128, 64);
    expectPoint(movePosition(m, path, 2, 1), 64, 192);
    expectPoint(movePosition(m, path, 0, 2), 46, 70);
  });

  it('places the figure by size, shift and rotation and limits it to the DMX range', () => {
    const path = pathOf(SQUARE, true);
    const placed = placedFigure({ sizeX: 50, sizeY: 200, shiftX: 10, shiftY: 0, rotation: 0 }, path);
    expectPoint(placed[0], 106, 0);
    const rotated = placedFigure({ sizeX: 100, sizeY: 100, shiftX: 0, shiftY: 0, rotation: 90 }, path);
    expectPoint(rotated[0], 192, 64);
  });

  it('treats a single point as a fixed position', () => {
    const path = pathOf([{ x: 128, y: 100 }], true);
    expect(path.closed).toBe(false);
    expectPoint(movePosition(move(1), path, 5, 0), 128, 100);
    expect(isFixedPosition({ points: [{ x: 1, y: 1 }] })).toBe(true);
    expect(isFixedPosition({ points: SQUARE })).toBe(false);
  });

  it('makes templates with the wanted number of points', () => {
    expect(templatePoints('circle', 8)).toHaveLength(8);
    expect(templatePoints('circle', 4)[0]).toEqual({ x: 191.5, y: 127.5 });
    expect(templatePoints('line_h', 3).map((p) => p.x)).toEqual([63.5, 127.5, 191.5]);
    expect(templatePoints('rectangle', 4)).toHaveLength(4);
    expect(templatePoints('triangle', 6)).toHaveLength(6);
    expect(templatePoints('random', 5, () => 0.5).every((p) => p.x === 127.5)).toBe(true);
    expect(templatePoints('eight', 8)[2]).toEqual({ x: 191.5, y: 127.5 });
  });

  it('edits points: snap, insert, reorder and find the line under the pointer', () => {
    expect(snap(100.4, false)).toBe(100);
    expect(snap(100, true)).toBe(96);
    expect(snap(300, false)).toBe(255);
    expect(insertAfter(SQUARE, 0, { x: 128, y: 64 })[1]).toEqual({ x: 128, y: 64 });
    expect(reorder(SQUARE, 0, 1)[0]).toEqual(SQUARE[1]);
    expect(reorder(SQUARE, 0, -1)).toBe(SQUARE);
    expect(lineNear(SQUARE, true, { x: 128, y: 66 }, 4)).toBe(0);
    expect(lineNear(SQUARE, true, { x: 64, y: 128 }, 4)).toBe(3);
    expect(lineNear(SQUARE, false, { x: 64, y: 128 }, 4)).toBeNull();
  });

  it('records the position of fixtures with pan and tilt in the programmer', () => {
    const programmer = { 1: { pan: 0.5, tilt: 0.2 }, 2: { pan: 0.5 }, 3: { red: 1 } };
    expect(positionedFixtures(programmer, [1, 2, 3, 4])).toEqual([1]);
  });

  it('knows which fixtures can move and which points are outside their range', () => {
    const mode = (...functions: string[]) =>
      ({ name: 'm', dmxChannelCount: functions.length, channels: functions.map((f) => ({ function: f })) }) as Mode;
    expect(canMove(mode('pan', 'tilt', 'dimmer'))).toBe(true);
    expect(canMove(mode('dimmer'))).toBe(false);
    const limits = { panMin: 20, panMax: 200, tiltMin: 0, tiltMax: 100 };
    expect(outside({ x: 10, y: 50 }, limits)).toBe(true);
    expect(outside({ x: 100, y: 50 }, limits)).toBe(false);
    expect(outside({ x: 300, y: 300 }, null)).toBe(false);
    const fixtures = [{ id: 1, limits }, { id: 2, limits: null }] as unknown as Fixture[];
    expect([...limitsOf(fixtures).keys()]).toEqual([1]);
  });
});
