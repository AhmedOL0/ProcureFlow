import { describe, expect, it } from 'vitest';

import { formatInstant, timeAgo } from './time';

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

describe('timeAgo', () => {
  const now = Date.parse('2026-10-11T12:00:00.000Z');

  it('returns an em dash for missing or invalid input', () => {
    expect(timeAgo(null, now)).toBe('—');
    expect(timeAgo(undefined, now)).toBe('—');
    expect(timeAgo('', now)).toBe('—');
    expect(timeAgo('not-a-date', now)).toBe('—');
  });

  it('scales from just now to days, then falls back to absolute', () => {
    expect(timeAgo('2026-10-11T11:59:40.000Z', now)).toBe('just now');
    expect(timeAgo('2026-10-11T11:59:00.000Z', now)).toBe('1 min ago');
    expect(timeAgo('2026-10-11T11:30:00.000Z', now)).toBe('30 mins ago');
    expect(timeAgo('2026-10-11T11:00:00.000Z', now)).toBe('1 hr ago');
    expect(timeAgo('2026-10-11T04:00:00.000Z', now)).toBe('8 hrs ago');
    expect(timeAgo('2026-10-10T12:00:00.000Z', now)).toBe('1 day ago');
    expect(timeAgo('2026-10-08T12:00:00.000Z', now)).toBe('3 days ago');
    expect(timeAgo('2026-10-01T12:00:00.000Z', now)).toContain('2026');
  });

  it('treats future timestamps as just now', () => {
    expect(timeAgo('2026-10-11T12:05:00.000Z', now)).toBe('just now');
  });
});
