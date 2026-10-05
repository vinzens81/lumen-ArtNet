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
