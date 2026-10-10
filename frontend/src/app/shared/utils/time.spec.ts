import { describe, expect, it } from 'vitest';

import { formatInstant } from './time';

describe('formatInstant', () => {
  it('returns an em dash for missing or invalid input', () => {
    expect(formatInstant(null)).toBe('—');
    expect(formatInstant(undefined)).toBe('—');
    expect(formatInstant('')).toBe('—');
    expect(formatInstant('not-a-date')).toBe('—');
  });

  it('renders real timestamps readably', () => {
    const rendered = formatInstant('2026-10-10T01:22:39.678Z');
    expect(rendered).not.toBe('—');
    expect(rendered).toContain('2026');
  });
});
