/** Human timestamps for metadata lines. Pure — covered by Vitest. */
export function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const time = Date.parse(value);
  if (Number.isNaN(time)) {
    return '—';
  }
  return new Date(time).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
