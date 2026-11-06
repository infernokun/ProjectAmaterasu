import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from '../../../core/http-error';

export type ConsoleState = 'connecting' | 'connected' | 'disconnected' | 'unavailable';

/** Close codes the API's console relay uses. */
export const CLOSE_NOT_ALLOWED = 4401;
export const CLOSE_NO_SERVER = 4404;
export const CLOSE_UPSTREAM = 4502;

/** Why the console could not open, from the API's answer. */
export function unavailableReason(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const message = errorMessage(error);
    switch (error.status) {
      case 0: return 'The API did not answer. Check your connection and try again.';
      case 403: return 'Only the team that owns this lab can open its consoles.';
      case 404: return message ?? 'This VM is no longer part of the lab.';
      case 409: case 502: return message ?? 'The node could not open a console for this VM.';
    }
    return message ?? 'The console could not be opened.';
  }
  return errorMessage(error) ?? 'The console could not be opened.';
}

/** Why an open console closed, from the websocket close and noVNC's own report. */
export function disconnectReason(close: { code: number; reason: string } | null, clean: boolean, security?: string | null): string {
  if (security) {
    return `The VM refused the console: ${security}`;
  }
  if (close) {
    if (close.code === CLOSE_NOT_ALLOWED) {
      return close.reason || 'This console link expired. Reconnect to get a new one.';
    }
    if ((close.code === CLOSE_NO_SERVER || close.code === CLOSE_UPSTREAM) && close.reason) {
      return close.reason;
    }
    if (close.code === 1000 && close.reason) {
      return close.reason;
    }
    if (close.code === 1006) {
      return 'The connection to the API dropped.';
    }
  }
  return clean ? 'The console was closed.' : 'The console connection dropped.';
}
