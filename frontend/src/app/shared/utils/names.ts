import { getDefaultLanguage } from '../../core/i18n/language.service';

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
export function humanizeRole(
  role: string | null | undefined,
  lang: 'en' | 'fr' = getDefaultLanguage(),
): string {
  if (!role) {
    return lang === 'fr' ? 'Aucun rôle' : 'No role';
  }
  if (lang === 'fr' && ROLE_FR[role]) {
    return ROLE_FR[role];
  }
  const words = role.toLowerCase().split('_').filter(Boolean);
  if (words.length === 0) {
    return lang === 'fr' ? 'Aucun rôle' : 'No role';
  }
  const [first, ...rest] = words as [string, ...string[]];
  return [first.charAt(0).toUpperCase() + first.slice(1), ...rest].join(' ');
}

const ROLE_FR: Record<string, string> = {
  TENANT_ADMIN: 'Administrateur',
  OFFICER: 'Acheteur',
  MEMBER: 'Membre',
  APPROVER: 'Approbateur',
  FINANCE: 'Finance',
  AUDITOR: 'Auditeur',
};

/**
 * Human label for UPPER_SNAKE machine strings (notification types, audit
 * actions), e.g. "REQUEST_DECIDED" -> "Request decided". Pure formatting —
 * the value itself still comes from the server.
 */
export function humanizeLabel(value: string | null | undefined): string {
  const words = (value ?? '').toLowerCase().split('_').filter(Boolean);
  if (words.length === 0) {
    return 'N/A';
  }
  const [first, ...rest] = words as [string, ...string[]];
  return [first.charAt(0).toUpperCase() + first.slice(1), ...rest].join(' ');
}
