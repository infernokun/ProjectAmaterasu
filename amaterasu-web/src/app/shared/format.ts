import { formatDistanceToNowStrict } from 'date-fns';

/** 1st, 2nd, 3rd, 11th, 22nd. */
export function ordinal(value: number): string {
  const mod100 = value % 100;
  const suffix = mod100 >= 11 && mod100 <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' } as Record<number, string>)[value % 10] ?? 'th';
  return `${value}${suffix}`;
}

export function points(value: number | null | undefined): string {
  return (value ?? 0).toLocaleString('en-US');
}

/** "+200" or "-25". */
export function signed(value: number): string {
  return `${value > 0 ? '+' : ''}${value.toLocaleString('en-US')}`;
}

export type DateInput = string | number | Date | null | undefined;

/** A Date from an ISO string, epoch milliseconds, or a Date; null when it is missing or invalid. */
export function toDate(at: DateInput): Date | null {
  if (at === null || at === undefined || at === '') return null;
  const date = at instanceof Date ? at : new Date(at);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function ago(at: DateInput): string {
  const date = toDate(at);
  return date ? formatDistanceToNowStrict(date, { addSuffix: true }) : '';
}

export function clock(at: DateInput): string {
  const date = toDate(at);
  return date ? date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }) : '';
}

export function dateTime(at: DateInput): string {
  const date = toDate(at);
  return date ? date.toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';
}

/** Two letters for an avatar. */
export function initials(name: string | null | undefined): string {
  if (!name) return '';
  const parts = name.replace(/[^a-zA-Z0-9 ._-]/g, '').split(/[\s._-]+/).filter(Boolean);
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : name.slice(0, 2)).toUpperCase();
}
