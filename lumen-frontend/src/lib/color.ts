import type { Mode } from './types';

export type Rgb = [number, number, number];

/** h, s, v in 0–1 → r, g, b in 0–1 */
export function hsvToRgb(h: number, s: number, v: number): Rgb {
  const i = Math.floor(h * 6) % 6;
  const f = h * 6 - Math.floor(h * 6);
  const p = v * (1 - s);
  const q = v * (1 - f * s);
  const t = v * (1 - (1 - f) * s);
  return ([[v, t, p], [q, v, p], [p, v, t], [p, q, v], [t, p, v], [v, p, q]] as Rgb[])[i];
}

/** r, g, b in 0–1 → h, s, v in 0–1 */
export function rgbToHsv([r, g, b]: Rgb): [number, number, number] {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  let h = 0;
  if (d > 0) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h /= 6;
    if (h < 0) h += 1;
  }
  return [h, max === 0 ? 0 : d / max, max];
}

export function hexToRgb(hex: string): Rgb {
  const n = parseInt(hex.replace('#', ''), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

export function rgbToCss([r, g, b]: Rgb): string {
  return `rgb(${Math.round(r * 255)} ${Math.round(g * 255)} ${Math.round(b * 255)})`;
}

/** How additional emitters tint the light, for the on-screen preview only. */
const EMITTERS: Record<string, Rgb> = {
  red: [1, 0, 0],
  green: [0, 1, 0],
  blue: [0, 0, 1],
  white: [1, 1, 1],
  'cold-white': [0.85, 0.92, 1],
  'warm-white': [1, 0.8, 0.55],
  amber: [1, 0.6, 0],
  lime: [0.7, 1, 0],
  uv: [0.45, 0.1, 1],
};

const WARM_WHITE: Rgb = [1, 0.85, 0.65];

export interface Preview {
  color: Rgb;
  intensity: number;
}

/**
 * Approximates the color and intensity a fixture emits, for glowing cards in the UI.
 * Fixtures without color channels glow warm white.
 */
export function previewOf(mode: Mode, values: Record<string, number>, master = 1): Preview {
  const intensity = (values.dimmer ?? 0) * master;
  const functions = new Set(mode.channels.map((c) => c.function));

  const mixed: Rgb = [0, 0, 0];
  let hasEmitters = false;
  for (const [fn, tint] of Object.entries(EMITTERS)) {
    if (!functions.has(fn)) continue;
    hasEmitters = true;
    const level = values[fn] ?? 0;
    for (let i = 0; i < 3; i++) mixed[i] += tint[i] * level;
  }
  if (hasEmitters) {
    const max = Math.max(...mixed);
    if (max === 0) return { color: [0, 0, 0], intensity: 0 };
    return { color: mixed.map((c) => c / max) as Rgb, intensity: intensity * Math.min(1, max) };
  }

  const wheel = mode.channels.find((c) => c.function === 'color-wheel');
  if (wheel) {
    const dmx = Math.round((values['color-wheel'] ?? wheel.defaultValue / 255) * 255);
    const slot = wheel.ranges.find((r) => dmx >= r.from && dmx <= r.to && r.color);
    if (slot?.color) return { color: hexToRgb(slot.color), intensity };
  }
  return { color: WARM_WHITE, intensity };
}
