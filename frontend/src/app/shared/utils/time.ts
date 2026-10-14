/** Human timestamps for metadata lines. Pure — covered by Vitest. */
export function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return 'N/A';
  }
  const time = Date.parse(value);
  if (Number.isNaN(time)) {
    return 'N/A';
  }
  return new Date(time).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * SLA bucket for a due timestamp: overdue past the deadline, soon inside
 * 24 hours, calm beyond that, none when missing or unreadable. Pure —
 * covered by Vitest.
 */
export function slaStatus(
  dueAt: string | null | undefined,
  now: number = Date.now(),
): 'overdue' | 'soon' | 'calm' | 'none' {
  if (!dueAt) {
    return 'none';
  }
  const due = Date.parse(dueAt);
  if (Number.isNaN(due)) {
    return 'none';
  }
  const remaining = due - now;
  if (remaining <= 0) {
    return 'overdue';
  }
  if (remaining <= 24 * 3600_000) {
    return 'soon';
  }
  return 'calm';
}

/**
 * Relative age ("2 hrs ago") for list metadata. Pure — covered by Vitest.
 * Beyond a week it falls back to the absolute instant: relative labels
 * stop meaning much past that, and the exact date stays one glance away.
 */
export function timeAgo(value: string | null | undefined, now: number = Date.now()): string {
  if (!value) {
    return 'N/A';
  }
  const time = Date.parse(value);
  if (Number.isNaN(time)) {
    return 'N/A';
  }
  const seconds = Math.max(0, Math.floor((now - time) / 1000));
  if (seconds < 60) {
    return 'just now';
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return minutes === 1 ? '1 min ago' : `${minutes} mins ago`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return hours === 1 ? '1 hr ago' : `${hours} hrs ago`;
  }
  const days = Math.floor(hours / 24);
  if (days < 7) {
    return days === 1 ? '1 day ago' : `${days} days ago`;
  }
  return formatInstant(value);
}
