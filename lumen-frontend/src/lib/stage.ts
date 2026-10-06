import type { AttributeValues, Mode, Position, StageShape, StageShapeKind } from './types';

export const DEFAULT_ASPECT = 16 / 9;

export function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

/** Position within the stage area (0–1) for a pointer position on the stage element. */
export function toStage(clientX: number, clientY: number, rect: { left: number; top: number; width: number; height: number }): Position {
  return {
    x: round(clamp01((clientX - rect.left) / rect.width)),
    y: round(clamp01((clientY - rect.top) / rect.height)),
  };
}

function round(value: number): number {
  return Math.round(value * 1000) / 1000;
}

/**
 * Free spots for fixtures that are not on the stage yet: rows across the upstage area (top), left to right,
 * skipping spots that are already taken.
 */
export function freeSpots(count: number, taken: Position[], columns = 8): Position[] {
  const result: Position[] = [];
  const occupied = (p: Position) =>
    [...taken, ...result].some((t) => Math.abs(t.x - p.x) < 0.04 && Math.abs(t.y - p.y) < 0.04);
  for (let row = 0; result.length < count && row < 50; row++) {
    for (let column = 0; column < columns && result.length < count; column++) {
      const spot = { x: round((column + 0.5) / columns), y: round(Math.min(0.95, 0.12 + row * 0.14)) };
      if (!occupied(spot)) result.push(spot);
    }
  }
  return result;
}

/**
 * Where the light lands relative to the fixture, as a fraction of the stage size. Only moving lights (pan/tilt)
 * move their beam: pan turns it (centre = towards the audience, ±270°), tilt sets how far it reaches
 * (centre = straight down). A rough picture, not a calibrated one.
 */
export function beamOffset(values: AttributeValues | undefined, movable: boolean, reach = 0.18): Position {
  if (!movable || !values) return { x: 0, y: 0 };
  const angle = ((values.pan ?? 0.5) - 0.5) * 1.5 * 2 * Math.PI;
  const distance = Math.abs((values.tilt ?? 0.5) - 0.5) * 2 * reach;
  return { x: Math.sin(angle) * distance, y: Math.cos(angle) * distance };
}

export type DrawnShape = Exclude<StageShapeKind, 'auto'>;

export const SHAPE_KINDS: { kind: StageShapeKind; label: string }[] = [
  { kind: 'auto', label: 'Auto' },
  { kind: 'spot', label: 'Spot' },
  { kind: 'bar', label: 'Bar' },
  { kind: 'strip', label: 'Strip' },
];

export const DEFAULT_SHAPE: StageShape = { kind: 'auto', rotation: 0, length: 0.15 };

/**
 * The form a fixture is drawn in: its own choice, or for "auto" a bar for LED bars and multi-pixel fixtures,
 * otherwise a round spot.
 */
export function drawnShape(kind: StageShapeKind, mode: Mode | undefined, category: string | undefined): DrawnShape {
  if (kind !== 'auto') return kind;
  return category === 'bar' || (mode?.heads ?? 0) > 1 ? 'bar' : 'spot';
}

/** Rotation turned by `delta` degrees, kept within 0–359. */
export function rotate(rotation: number, delta: number): number {
  return ((Math.round(rotation + delta) % 360) + 360) % 360;
}

/** Bar/strip length within the allowed range, rounded to whole percent of the stage width. */
export function clampLength(length: number): number {
  return Math.min(1, Math.max(0.02, Math.round(length * 100) / 100));
}
