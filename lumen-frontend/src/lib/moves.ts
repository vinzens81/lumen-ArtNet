import type {
  AttributeValues, Figure, FigurePoint, Fixture, Mode, Move, MoveFixture, MoveRequest, PositionLimits,
} from './types';

export type { PositionLimits };

/**
 * Geometry of figures and moves, in DMX values (x = pan, y = tilt, 0–255). The same rules as the server
 * (MovePattern, FigurePath), so the preview in the browser shows what the heads do.
 */

export const DMX_MAX = 255;

/** The way through the points of a figure: straight lines, a closed figure back to its first point. */
export interface FigurePath {
  points: FigurePoint[];
  closed: boolean;
  /** distance from the start to each corner (a closed figure: one more, back at the first point) */
  distances: number[];
  length: number;
  /** middle of the bounding box: the figure is scaled, rotated and mirrored around it */
  center: FigurePoint;
}

export function pathOf(points: FigurePoint[], closed: boolean): FigurePath {
  const isClosed = closed && points.length > 1;
  const corners = isClosed ? [...points, points[0]] : points;
  const distances = [0];
  for (let i = 1; i < corners.length; i++) {
    distances.push(distances[i - 1] + Math.hypot(corners[i].x - corners[i - 1].x, corners[i].y - corners[i - 1].y));
  }
  return { points, closed: isClosed, distances, length: distances[distances.length - 1] ?? 0, center: centerOf(points) };
}

export function centerOf(points: FigurePoint[]): FigurePoint {
  if (points.length === 0) return { x: DMX_MAX / 2, y: DMX_MAX / 2 };
  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  return { x: (Math.min(...xs) + Math.max(...xs)) / 2, y: (Math.min(...ys) + Math.max(...ys)) / 2 };
}

/** Where point `index` lies on the way, as a share of the whole length (0 ≤ result < 1). */
export function shareAt(path: FigurePath, index: number): number {
  if (path.length <= 0 || path.points.length === 0) return 0;
  const n = path.points.length;
  return path.distances[((index % n) + n) % n] / path.length;
}

/** The position at a share of the way: a closed figure wraps around, an open one stays at its ends. */
export function pointAt(path: FigurePath, share: number): FigurePoint {
  if (path.points.length === 0) return { x: DMX_MAX / 2, y: DMX_MAX / 2 };
  if (path.length <= 0) return path.points[0];
  const s = path.closed ? share - Math.floor(share) : Math.min(1, Math.max(0, share));
  const target = s * path.length;
  const corners = path.closed ? [...path.points, path.points[0]] : path.points;
  for (let i = 1; i < corners.length; i++) {
    if (target <= path.distances[i] || i === corners.length - 1) {
      const segment = path.distances[i] - path.distances[i - 1];
      const t = segment <= 0 ? 1 : Math.min(1, Math.max(0, (target - path.distances[i - 1]) / segment));
      return { x: corners[i - 1].x + (corners[i].x - corners[i - 1].x) * t,
        y: corners[i - 1].y + (corners[i].y - corners[i - 1].y) * t };
    }
  }
  return corners[corners.length - 1];
}

type MoveSettings = Pick<Move, 'sizeX' | 'sizeY' | 'shiftX' | 'shiftY' | 'rotation' | 'durationMs' | 'repeat'
  | 'stepOffset' | 'onceStart'>;

/** Where the fixture at `index` of the move points, `seconds` after the move started. */
export function movePosition(move: MoveSettings & { fixtures: MoveFixture[] }, path: FigurePath, seconds: number,
  index: number): FigurePoint {
  const fixture = move.fixtures[index] ?? noCorrection(0);
  let share = progress(move, path, Math.max(0, seconds) / (move.durationMs / 1000), index * move.stepOffset);
  if (fixture.reverse) share = 1 - share;
  return place(move, path, pointAt(path, share), fixture);
}

function progress(move: MoveSettings, path: FigurePath, rounds: number, startStep: number): number {
  const startShare = shareAt(path, startStep);
  switch (move.repeat) {
    case 'loop':
      return cycle(rounds + startShare);
    case 'ping_pong': {
      const value = rounds + startShare;
      const round = Math.floor(value);
      return round % 2 === 0 ? value - round : 1 - (value - round);
    }
    case 'once':
      if (move.onceStart === 'sequential') return Math.min(1, Math.max(0, rounds - delayRounds(path, startStep)));
      return path.closed ? startShare + Math.min(1, rounds) : Math.min(1, startShare + rounds);
  }
}

function delayRounds(path: FigurePath, startStep: number): number {
  const n = Math.max(1, path.points.length);
  return Math.floor(startStep / n) + shareAt(path, startStep);
}

/** Mirror, size, rotation and shift (of the move and the fixture), limited to the DMX range. */
export function place(move: Pick<Move, 'sizeX' | 'sizeY' | 'shiftX' | 'shiftY' | 'rotation'>, path: FigurePath,
  point: FigurePoint, fixture: Pick<MoveFixture, 'shiftX' | 'shiftY' | 'mirrorX' | 'mirrorY'>): FigurePoint {
  const c = path.center;
  const x = fixture.mirrorX ? 2 * c.x - point.x : point.x;
  const y = fixture.mirrorY ? 2 * c.y - point.y : point.y;
  const dx = (x - c.x) * (move.sizeX / 100);
  const dy = (y - c.y) * (move.sizeY / 100);
  const angle = (move.rotation * Math.PI) / 180;
  const rx = dx * Math.cos(angle) - dy * Math.sin(angle);
  const ry = dx * Math.sin(angle) + dy * Math.cos(angle);
  return { x: clampDmx(c.x + move.shiftX + fixture.shiftX + rx), y: clampDmx(c.y + move.shiftY + fixture.shiftY + ry) };
}

/** The figure as the move lays it out (size, shift, rotation), without the corrections of a fixture. */
export function placedFigure(move: Pick<Move, 'sizeX' | 'sizeY' | 'shiftX' | 'shiftY' | 'rotation'>,
  path: FigurePath): FigurePoint[] {
  return path.points.map((p) => place(move, path, p, noCorrection(0)));
}

/** The point (1-based) a fixture starts at: with an offset each further fixture starts that many points later. */
export function startPoint(stepOffset: number, pointCount: number, index: number): number {
  if (pointCount <= 0) return 1;
  return ((index * stepOffset) % pointCount) + 1;
}

/** Time until the last fixture of a move that runs once has reached its end, in seconds; null for loops. */
export function onceSeconds(move: MoveSettings & { fixtures: MoveFixture[] }, path: FigurePath): number | null {
  if (move.repeat !== 'once') return null;
  const rounds = move.onceStart === 'together' ? 1
    : delayRounds(path, Math.max(0, move.fixtures.length - 1) * move.stepOffset) + 1;
  return (rounds * move.durationMs) / 1000;
}

export function clampDmx(value: number): number {
  return Math.min(DMX_MAX, Math.max(0, value));
}

function cycle(value: number): number {
  return value - Math.floor(value);
}

export function noCorrection(fixtureId: number): MoveFixture {
  return { fixtureId, shiftX: 0, shiftY: 0, mirrorX: false, mirrorY: false, reverse: false };
}

/** Fixtures that can follow a move: with pan and tilt. */
export function canMove(mode: Mode | undefined): boolean {
  const functions = new Set(mode?.channels.map((c) => c.function) ?? []);
  return functions.has('pan') && functions.has('tilt');
}

/** Fixtures of the list with pan and tilt in the programmer: what "Record position" takes. */
export function positionedFixtures(programmer: Record<number, AttributeValues>, fixtureIds: number[]): number[] {
  return fixtureIds.filter((id) => programmer[id]?.pan !== undefined && programmer[id]?.tilt !== undefined);
}

/** Settings of a new move (the server uses the same defaults). */
export function newMove(name: string, figureId: number, fixtureIds: number[]): MoveRequest {
  return { name, figureId, fixtures: fixtureIds.map(noCorrection), sizeX: 100, sizeY: 100, shiftX: 0, shiftY: 0,
    rotation: 0, durationMs: 8000, repeat: 'loop', stepOffset: 0, onceStart: 'sequential' };
}

/** Whether a figure is a fixed position (a single point): tempo, repeat and offset do not matter then. */
export function isFixedPosition(figure: Pick<Figure, 'points'> | undefined): boolean {
  return (figure?.points.length ?? 0) <= 1;
}

// --- figure editor ------------------------------------------------------------------------------------------

export type Template = 'circle' | 'eight' | 'line_h' | 'line_v' | 'triangle' | 'rectangle' | 'random';

export const TEMPLATES: { kind: Template; label: string; closed: boolean }[] = [
  { kind: 'circle', label: 'Circle', closed: true },
  { kind: 'eight', label: 'Eight', closed: true },
  { kind: 'line_h', label: 'Line horizontal', closed: false },
  { kind: 'line_v', label: 'Line vertical', closed: false },
  { kind: 'triangle', label: 'Triangle', closed: true },
  { kind: 'rectangle', label: 'Rectangle', closed: true },
  { kind: 'random', label: 'Random', closed: true },
];

/**
 * Points of a template around the middle of the grid, radius 64 (a quarter of the range).
 *
 * @param random 0 ≤ result < 1, for the random template
 */
export function templatePoints(kind: Template, count: number, random: () => number = Math.random): FigurePoint[] {
  const n = Math.max(1, Math.round(count));
  const c = DMX_MAX / 2;
  const r = 64;
  const at = (x: number, y: number): FigurePoint => ({ x: round(clampDmx(c + x)), y: round(clampDmx(c + y)) });
  switch (kind) {
    case 'circle':
      return range(n).map((i) => {
        const a = (2 * Math.PI * i) / n;
        return at(r * Math.cos(a), r * Math.sin(a));
      });
    case 'eight':
      // a lemniscate: x = sin(a), y = sin(2a) / 2
      return range(n).map((i) => {
        const a = (2 * Math.PI * i) / n;
        return at(r * Math.sin(a), (r / 2) * Math.sin(2 * a));
      });
    case 'line_h':
      return range(n).map((i) => at(n === 1 ? 0 : -r + (2 * r * i) / (n - 1), 0));
    case 'line_v':
      return range(n).map((i) => at(0, n === 1 ? 0 : -r + (2 * r * i) / (n - 1)));
    case 'triangle':
      return alongPolygon([at(0, r), at(-r, -r / 2), at(r, -r / 2)], n);
    case 'rectangle':
      return alongPolygon([at(-r, r / 2), at(r, r / 2), at(r, -r / 2), at(-r, -r / 2)], n);
    case 'random':
      return range(n).map(() => at((random() * 2 - 1) * r, (random() * 2 - 1) * r));
  }
}

/** `n` points evenly along a closed polygon, starting at its first corner (at least the corners). */
function alongPolygon(corners: FigurePoint[], n: number): FigurePoint[] {
  if (n <= corners.length) return corners.slice(0, Math.max(1, n));
  const path = pathOf(corners, true);
  return range(n).map((i) => {
    const p = pointAt(path, i / n);
    return { x: round(p.x), y: round(p.y) };
  });
}

/** Snaps a value to whole DMX values, or with `coarse` to the 16 grid. */
export function snap(value: number, coarse: boolean): number {
  const step = coarse ? 16 : 1;
  return clampDmx(Math.round(value / step) * step);
}

/** Inserts a point after `index` in the middle of the line to the next point. */
export function insertAfter(points: FigurePoint[], index: number, point: FigurePoint): FigurePoint[] {
  return [...points.slice(0, index + 1), point, ...points.slice(index + 1)];
}

/** Moves a point one place up (−1) or down (+1) in the order. */
export function reorder(points: FigurePoint[], index: number, direction: -1 | 1): FigurePoint[] {
  const target = index + direction;
  if (target < 0 || target >= points.length) return points;
  const result = [...points];
  [result[index], result[target]] = [result[target], result[index]];
  return result;
}

/** The line (index of its first point) closest to `point`, if it is within `tolerance`. */
export function lineNear(points: FigurePoint[], closed: boolean, point: FigurePoint, tolerance: number): number | null {
  const lines = closed && points.length > 2 ? points.length : points.length - 1;
  let best: { index: number; distance: number } | null = null;
  for (let i = 0; i < lines; i++) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    const distance = distanceToSegment(point, a, b);
    if (distance <= tolerance && (!best || distance < best.distance)) best = { index: i, distance };
  }
  return best?.index ?? null;
}

function distanceToSegment(p: FigurePoint, a: FigurePoint, b: FigurePoint): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const lengthSquared = dx * dx + dy * dy;
  const t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared));
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
}

/** Whether a point lies outside the allowed range of a fixture (inclusive bounds in DMX values). */
export function outside(point: FigurePoint, limits: PositionLimits | null): boolean {
  if (!limits) return false;
  return point.x < limits.panMin || point.x > limits.panMax || point.y < limits.tiltMin || point.y > limits.tiltMax;
}

/** The limits of the fixtures that have them, by fixture id. */
export function limitsOf(fixtures: Fixture[]): Map<number, PositionLimits> {
  return new Map(fixtures.flatMap((f) => (f.limits ? [[f.id, f.limits] as const] : [])));
}

function range(n: number): number[] {
  return Array.from({ length: n }, (_, i) => i);
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}
