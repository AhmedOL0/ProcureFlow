/** Initials avatar text, e.g. "Acme Parts" -> "AP". Pure — covered by Vitest. */
export function initialsFor(name: string | null | undefined): string {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  const first = words[0]?.charAt(0) ?? '';
  const last = words.length > 1 ? (words[words.length - 1]?.charAt(0) ?? '') : '';
  return (first + last).toUpperCase();
}

/** Human role label, e.g. "TENANT_ADMIN" -> "Tenant admin". Pure — covered by Vitest. */
export function humanizeRole(role: string | null | undefined): string {
  const words = (role ?? '').toLowerCase().split('_').filter(Boolean);
  if (words.length === 0) {
    return 'No role';
  }
  const [first, ...rest] = words as [string, ...string[]];
  return [first.charAt(0).toUpperCase() + first.slice(1), ...rest].join(' ');
}
