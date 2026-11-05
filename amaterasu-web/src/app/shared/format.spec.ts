import { ago, initials, ordinal, signed, toDate } from './format';

describe('format helpers', () => {
  it('writes ordinals', () => {
    expect([1, 2, 3, 4, 11, 12, 13, 21, 22, 103].map(ordinal)).toEqual(['1st', '2nd', '3rd', '4th', '11th', '12th', '13th', '21st', '22nd', '103rd']);
  });

  it('signs point changes', () => {
    expect(signed(200)).toBe('+200');
    expect(signed(-25)).toBe('-25');
    expect(signed(1500)).toBe('+1,500');
  });

  it('reads dates from strings, epoch milliseconds and Dates', () => {
    expect(toDate('2026-11-04T10:00:00Z')?.toISOString()).toBe('2026-11-04T10:00:00.000Z');
    expect(toDate(Date.parse('2026-11-04T10:00:00Z'))?.toISOString()).toBe('2026-11-04T10:00:00.000Z');
    expect(toDate('nope')).toBeNull();
    expect(ago(null)).toBe('');
  });

  it('makes avatar initials', () => {
    expect(initials('priya.n')).toBe('PN');
    expect(initials('Red Fox')).toBe('RF');
    expect(initials('jmorales')).toBe('JM');
  });
});
