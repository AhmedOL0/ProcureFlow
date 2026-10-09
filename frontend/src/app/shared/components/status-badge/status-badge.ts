// Maps backend status strings to the design-system pill tones.
// Unknown states fall back to neutral — never blank, never invented.
export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const TONES: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  APPROVED: 'success',
  CLOSED: 'success',
  PAID: 'success',
  RECEIVED: 'success',
  DRAFT: 'neutral',
  INACTIVE: 'neutral',
  UNPAID: 'neutral',
  ORDERED: 'info',
  SENT: 'info',
  SUBMITTED: 'info',
  PARTIALLY_RECEIVED: 'info',
  PARTIAL: 'info',
  SUSPENDED: 'warning',
  CANCELLED: 'warning',
  REJECTED: 'danger',
};

export function badgeToneFor(status: string | null | undefined): BadgeTone {
  if (!status) {
    return 'neutral';
  }
  return TONES[status.toUpperCase()] ?? 'neutral';
}

export function badgeLabelFor(status: string | null | undefined): string {
  if (!status) {
    return 'Unknown';
  }
  return status
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}
