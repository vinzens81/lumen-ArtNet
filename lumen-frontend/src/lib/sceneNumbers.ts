/** Scene numbers have at most 4 digits before and 3 after the decimal point (see SceneNumber on the server). */
const MAX_DECIMALS = 3;
const MAX_NUMBER = 9999;

/**
 * A free scene number strictly between two numbers, with as few decimals as possible: the next whole number if
 * there is room (2 and 5 → 3), otherwise the middle (2 and 3 → 2.5, 2.5 and 3 → 2.8).
 *
 * @param before number of the scene before, null at the start of the list
 * @param after  number of the scene after, null at the end of the list
 * @returns null if no number fits (e.g. between 2.001 and 2.002)
 */
export function numberBetween(before: string | null, after: string | null): string | null {
  const low = before === null ? 0 : Number(before);
  if (after === null) {
    const next = Math.floor(low) + 1;
    return next <= MAX_NUMBER ? String(next) : null;
  }
  const high = Number(after);
  const whole = Math.floor(low) + 1;
  if (whole < high) return String(whole);
  for (let decimals = 1; decimals <= MAX_DECIMALS; decimals++) {
    const scale = 10 ** decimals;
    const l = Math.round(low * scale);
    const h = Math.round(high * scale);
    if (h - l < 2) continue;
    const n = Math.round((l + h) / 2);
    if (n > 0) return format(n / scale, decimals);
  }
  return null;
}

/**
 * New number for a scene dragged to position {@code index} of the other scenes (ordered by number, without the
 * dragged scene); null if no number fits there.
 */
export function numberAt(others: string[], index: number): string | null {
  const before = index > 0 ? others[index - 1] : null;
  const after = index < others.length ? others[index] : null;
  return numberBetween(before, after);
}

function format(value: number, decimals: number): string {
  return String(Number(value.toFixed(decimals)));
}
