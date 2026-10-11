/** Human-readable file size, e.g. "48 KB". */
export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`;
}

/** What a backup file name says about why it was made, e.g. "lumen-20261002-193005-000-show.db" → "Show started". */
export function backupReason(name: string): string {
  const reason = /-([a-z][a-z0-9-]*)\.db$/.exec(name)?.[1] ?? '';
  const labels: Record<string, string> = {
    startup: 'Server started',
    show: 'Show started',
    project: 'Project switched',
    manual: 'Manual',
  };
  return labels[reason] ?? reason;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** When something happened, in local time: "20:14" today, "5 Oct 20:14" on another day. */
export function shortTime(date: Date, now: Date = new Date()): string {
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  const sameDay = date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate();
  return sameDay ? time : `${date.getDate()} ${MONTHS[date.getMonth()]} ${time}`;
}

/**
 * The show time as a clock: "12:05" below an hour, "1:02:05" from an hour on; "–:––" before the show started.
 * `sinceStatusMs` is the time since the status arrived, counted on while the show time runs.
 */
export function showClock(status: { showTimeMs: number | null; showTimeRunning: boolean } | null,
  sinceStatusMs: number): string {
  if (status?.showTimeMs == null) return '–:––';
  const ms = status.showTimeMs + (status.showTimeRunning ? Math.max(0, sinceStatusMs) : 0);
  const total = Math.floor(ms / 1000);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = String(total % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`;
}
