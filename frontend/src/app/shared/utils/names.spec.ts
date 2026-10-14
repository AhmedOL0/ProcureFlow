import { describe, expect, it } from 'vitest';

import { humanizeLabel, humanizeRole, initialsFor } from './names';

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

describe('humanizeLabel', () => {
  it('title-cases machine strings', () => {
    expect(humanizeLabel('REQUEST_DECIDED')).toBe('Request decided');
    expect(humanizeLabel('purchase_order')).toBe('Purchase order');
  });

  it('falls back to a dash when empty', () => {
    expect(humanizeLabel(null)).toBe('N/A');
    expect(humanizeLabel('')).toBe('N/A');
  });
});
