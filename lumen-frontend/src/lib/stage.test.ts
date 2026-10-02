import { describe, expect, it } from 'vitest';
import { beamOffset, freeSpots, toStage } from './stage';

describe('toStage', () => {
  it('maps pointer coordinates into the stage area and clamps them', () => {
    const rect = { left: 100, top: 50, width: 400, height: 200 };
    expect(toStage(300, 150, rect)).toEqual({ x: 0.5, y: 0.5 });
    expect(toStage(0, 400, rect)).toEqual({ x: 0, y: 1 });
  });
});

describe('freeSpots', () => {
  it('fills rows from the top and skips taken spots', () => {
    const spots = freeSpots(3, [{ x: 0.375, y: 0.12 }], 4);
    expect(spots).toEqual([
      { x: 0.125, y: 0.12 },
      { x: 0.625, y: 0.12 },
      { x: 0.875, y: 0.12 },
    ]);
    expect(freeSpots(5, [], 4)[4]).toEqual({ x: 0.125, y: 0.26 });
  });
});

describe('beamOffset', () => {
  it('keeps fixed lights in place', () => {
    expect(beamOffset({ pan: 1, tilt: 1 }, false)).toEqual({ x: 0, y: 0 });
  });

  it('turns the beam with pan and moves it out with tilt', () => {
    expect(beamOffset({ pan: 0.5, tilt: 0.5 }, true)).toEqual({ x: 0, y: 0 });
    const forward = beamOffset({ pan: 0.5, tilt: 1 }, true, 0.2);
    expect(forward.x).toBeCloseTo(0);
    expect(forward.y).toBeCloseTo(0.2);
    const quarter = beamOffset({ pan: 0.5 + 1 / 6, tilt: 1 }, true, 0.2); // +90°
    expect(quarter.x).toBeCloseTo(0.2);
    expect(quarter.y).toBeCloseTo(0);
  });
});
