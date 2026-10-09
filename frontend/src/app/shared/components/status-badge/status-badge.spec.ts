import { describe, expect, it } from 'vitest';

import { badgeLabelFor, badgeToneFor } from './status-badge';

describe('status-badge', () => {
  it('tones backend states per the design system', () => {
    expect(badgeToneFor('APPROVED')).toBe('success');
    expect(badgeToneFor('ACTIVE')).toBe('success');
    expect(badgeToneFor('SUBMITTED')).toBe('info');
    expect(badgeToneFor('SUSPENDED')).toBe('warning');
    expect(badgeToneFor('REJECTED')).toBe('danger');
    expect(badgeToneFor('DRAFT')).toBe('neutral');
  });

  it('falls back to neutral and humanizes labels', () => {
    expect(badgeToneFor('SOMETHING_NEW')).toBe('neutral');
    expect(badgeToneFor(null)).toBe('neutral');
    expect(badgeLabelFor('PARTIALLY_RECEIVED')).toBe('Partially Received');
    expect(badgeLabelFor(null)).toBe('Unknown');
  });
});
