import { fromLocalInput, relativeTime, toDate, toLocalInput } from './time';

describe('time helpers', () => {
  it('reads ISO strings, server dates in UTC and epoch seconds', () => {
    expect(toDate('2026-11-04T09:00:00Z')?.toISOString()).toBe('2026-11-04T09:00:00.000Z');
    expect(toDate('2026-11-04 09:00:00')?.toISOString()).toBe('2026-11-04T09:00:00.000Z');
    expect(toDate(1793782800)?.toISOString()).toBe('2026-11-04T09:00:00.000Z');
    expect(toDate('')).toBeNull();
    expect(toDate('not a date')).toBeNull();
  });

  it('describes how long ago something happened', () => {
    const now = new Date('2026-11-04T12:00:00Z');
    expect(relativeTime('2026-11-04T11:59:30Z', now)).toBe('Now');
    expect(relativeTime('2026-11-04T11:48:00Z', now)).toBe('12 min ago');
    expect(relativeTime('2026-11-04T09:00:00Z', now)).toBe('3 h ago');
    expect(relativeTime('2026-11-03T09:00:00Z', now)).toBe('Yesterday');
    expect(relativeTime('2026-11-01T09:00:00Z', now)).toBe('3 days ago');
    expect(relativeTime(null, now)).toBe('Never');
  });

  it('round trips datetime-local values through the API format', () => {
    const local = '2026-11-04T14:00';
    const api = fromLocalInput(local)!;
    expect(api).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
    expect(toLocalInput(api)).toBe(local);
    expect(fromLocalInput('')).toBeNull();
  });
});
