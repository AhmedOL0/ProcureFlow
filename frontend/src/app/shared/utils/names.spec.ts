import { describe, expect, it } from 'vitest';

import { humanizeRole, initialsFor } from './names';

describe('initialsFor', () => {
  it('takes first and last initials', () => {
    expect(initialsFor('Acme Parts')).toBe('AP');
    expect(initialsFor('  bechtle  ag ')).toBe('BA');
    expect(initialsFor('Solo')).toBe('S');
  });

  it('falls back to a placeholder', () => {
    expect(initialsFor(null)).toBe('?');
    expect(initialsFor('')).toBe('?');
    expect(initialsFor('   ')).toBe('?');
  });
});

describe('humanizeRole', () => {
  it('title-cases role codes', () => {
    expect(humanizeRole('TENANT_ADMIN')).toBe('Tenant admin');
    expect(humanizeRole('MEMBER')).toBe('Member');
  });

  it('falls back when empty', () => {
    expect(humanizeRole(null)).toBe('No role');
    expect(humanizeRole('')).toBe('No role');
  });
});
