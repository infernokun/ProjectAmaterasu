import { HttpErrorResponse } from '@angular/common/http';
import { CLOSE_NOT_ALLOWED, CLOSE_UPSTREAM, disconnectReason, unavailableReason } from './console-state';

describe('console reasons', () => {
  const http = (status: number, message?: string) =>
    new HttpErrorResponse({ status, error: message ? { message } : null });

  it('explains why a console cannot open', () => {
    expect(unavailableReason(http(403, 'Access denied'))).toBe('Only the team that owns this lab can open its consoles.');
    expect(unavailableReason(http(409, 'dc01 is stopped. Start or resume the lab first.')))
      .toBe('dc01 is stopped. Start or resume the lab first.');
    expect(unavailableReason(http(502, 'pve-a refused the console: VM 101 not running'))).toContain('VM 101 not running');
    expect(unavailableReason(http(0))).toBe('The API did not answer. Check your connection and try again.');
    expect(unavailableReason(new Error('boom'))).toBe('The console could not be opened.');
  });

  it('explains why an open console closed', () => {
    expect(disconnectReason({ code: CLOSE_UPSTREAM, reason: 'pve-a did not accept the console connection.' }, false))
      .toBe('pve-a did not accept the console connection.');
    expect(disconnectReason({ code: CLOSE_NOT_ALLOWED, reason: '' }, false)).toBe('This console link expired. Reconnect to get a new one.');
    expect(disconnectReason({ code: 1000, reason: 'The VM closed the console.' }, true)).toBe('The VM closed the console.');
    expect(disconnectReason({ code: 1006, reason: '' }, false)).toBe('The connection to the API dropped.');
    expect(disconnectReason(null, false, 'Authentication failure')).toBe('The VM refused the console: Authentication failure');
    expect(disconnectReason(null, true)).toBe('The console was closed.');
  });
});
