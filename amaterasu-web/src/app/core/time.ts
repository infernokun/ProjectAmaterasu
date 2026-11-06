/**
 * A date from the API. Accepts ISO strings, "2026-11-04 09:00:00" (server local time, which the
 * containers run in UTC), epoch seconds and Date objects.
 */
export function toDate(value: string | number | Date | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }
  if (typeof value === 'number') {
    return new Date(value < 1e12 ? value * 1000 : value);
  }
  const plain = /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$/.test(value);
  const date = new Date(plain ? value.replace(' ', 'T') + 'Z' : value);
  return isNaN(date.getTime()) ? null : date;
}

/** "Now", "4 min ago", "2 h ago", "Yesterday", "3 days ago", or a date for anything older. */
export function relativeTime(value: string | number | Date | null | undefined, now = new Date()): string {
  const date = toDate(value);
  if (!date) {
    return 'Never';
  }
  const seconds = Math.round((now.getTime() - date.getTime()) / 1000);
  if (seconds < 60) {
    return 'Now';
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min ago`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} h ago`;
  }
  const days = Math.floor(hours / 24);
  if (days === 1) {
    return 'Yesterday';
  }
  if (days < 7) {
    return `${days} days ago`;
  }
  return formatDate(date);
}

/** "21 Oct 2026". */
export function formatDate(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  return date ? date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
}

/** "21 Oct 2026, 14:05". */
export function formatDateTime(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  return date
    ? date.toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    : '';
}

/** The value for an `<input type="datetime-local">`, in the browser's time zone. */
export function toLocalInput(value: string | number | Date | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return '';
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** A datetime-local value as the API's "yyyy-MM-dd HH:mm:ss" in UTC, or null when empty. */
export function fromLocalInput(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (isNaN(date.getTime())) {
    return null;
  }
  return date.toISOString().slice(0, 19).replace('T', ' ');
}
