import { HttpErrorResponse } from '@angular/common/http';

/** The human readable message from an API error response, if it has one. */
export function errorMessage(error: unknown): string | undefined {
  if (error instanceof HttpErrorResponse) {
    const body = error.error;
    if (body && typeof body === 'object' && typeof body.message === 'string' && body.message) {
      return body.message;
    }
    if (error.status === 0) {
      return 'The server could not be reached.';
    }
  }
  return undefined;
}
