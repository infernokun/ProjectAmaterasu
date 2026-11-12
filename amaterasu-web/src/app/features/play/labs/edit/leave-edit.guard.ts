import { CanDeactivateFn } from '@angular/router';

/** A page holding edits that are not saved yet. */
export interface LeavesEdits {
  canLeave(): boolean | Promise<boolean>;
}

/** Asks the page before leaving it, so unsaved edits are not dropped silently. */
export const leaveEditGuard: CanDeactivateFn<LeavesEdits> = component => component?.canLeave?.() ?? true;
