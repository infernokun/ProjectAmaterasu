import { StatusTone } from '../../../shared/lab-status';

export interface ActionGroup {
  key: string;
  label: string;
  actions: string[];
}

export const ACTION_GROUPS: ActionGroup[] = [
  { key: 'all', label: 'All', actions: [] },
  {
    key: 'labs', label: 'Environments',
    actions: ['LAB_STARTED', 'LAB_PAUSED', 'LAB_STOPPED', 'LAB_DELETED', 'LAB_ACTION_FAILED', 'LAB_CREATED', 'LAB_UPDATED', 'LAB_REMOVED'],
  },
  { key: 'signin', label: 'Sign in', actions: ['SIGN_IN', 'SIGN_IN_FAILED', 'PASSWORD_CHANGED', 'SESSION_REVOKED'] },
  {
    key: 'users', label: 'Users',
    actions: ['USER_CREATED', 'USER_ROLE_CHANGED', 'USER_DISABLED', 'USER_ENABLED', 'USER_TEAM_CHANGED', 'PASSWORD_RESET'],
  },
  { key: 'teams', label: 'Teams', actions: ['TEAM_CREATED', 'TEAM_UPDATED', 'TEAM_DELETED'] },
  {
    key: 'authoring', label: 'Labs and questions',
    actions: ['ROOM_CREATED', 'ROOM_UPDATED', 'ROOM_DELETED', 'CHALLENGE_CREATED', 'CHALLENGE_UPDATED', 'CHALLENGE_DELETED', 'CHALLENGE_IMPORTED'],
  },
  { key: 'scores', label: 'Points', actions: ['POINTS_AWARDED', 'FLAG_SOLVED'] },
  { key: 'servers', label: 'Servers', actions: ['SERVER_ADDED', 'SERVER_DELETED'] },
];

export const TARGET_TYPES: { value: string; label: string }[] = [
  { value: '', label: 'All targets' },
  { value: 'LAB_TRACKER', label: 'Team environments' },
  { value: 'LAB', label: 'Environments' },
  { value: 'USER', label: 'Users' },
  { value: 'TEAM', label: 'Teams' },
  { value: 'ROOM', label: 'Labs' },
  { value: 'CHALLENGE', label: 'Questions' },
  { value: 'SERVER', label: 'Servers' },
  { value: 'SESSION', label: 'Sessions' },
];

const LABELS: Record<string, [string, StatusTone]> = {
  LAB_STARTED: ['Started environment', 'active'],
  LAB_PAUSED: ['Paused environment', 'paused'],
  LAB_STOPPED: ['Stopped environment', 'off'],
  LAB_DELETED: ['Removed environment from team', 'off'],
  LAB_ACTION_FAILED: ['Environment action failed', 'bad'],
  LAB_CREATED: ['Created environment', 'accent'],
  LAB_UPDATED: ['Edited environment', 'work'],
  LAB_REMOVED: ['Deleted environment', 'bad'],
  SIGN_IN: ['Signed in', 'active'],
  SIGN_IN_FAILED: ['Sign in failed', 'warn'],
  PASSWORD_CHANGED: ['Changed password', 'work'],
  PASSWORD_RESET: ['Reset password', 'warn'],
  SESSION_REVOKED: ['Signed out a session', 'work'],
  USER_CREATED: ['Added user', 'accent'],
  USER_ROLE_CHANGED: ['Changed role', 'warn'],
  USER_DISABLED: ['Disabled user', 'bad'],
  USER_ENABLED: ['Enabled user', 'active'],
  USER_TEAM_CHANGED: ['Moved user', 'work'],
  TEAM_CREATED: ['Created team', 'accent'],
  TEAM_UPDATED: ['Edited team', 'work'],
  TEAM_DELETED: ['Deleted team', 'bad'],
  ROOM_CREATED: ['Created lab', 'accent'],
  ROOM_UPDATED: ['Edited lab', 'work'],
  ROOM_DELETED: ['Deleted lab', 'bad'],
  CHALLENGE_CREATED: ['Created question', 'accent'],
  CHALLENGE_UPDATED: ['Edited question', 'work'],
  CHALLENGE_DELETED: ['Deleted question', 'bad'],
  CHALLENGE_IMPORTED: ['Imported questions', 'accent'],
  POINTS_AWARDED: ['Awarded points', 'accent'],
  FLAG_SOLVED: ['Solved a question', 'active'],
  SERVER_ADDED: ['Added server', 'accent'],
  SERVER_DELETED: ['Deleted server', 'bad'],
};

/** A readable label and color for an action; unknown actions read as their words. */
export function actionMeta(action: string): { label: string; tone: StatusTone } {
  const known = LABELS[action];
  if (known) {
    return { label: known[0], tone: known[1] };
  }
  const words = action.toLowerCase().replace(/_/g, ' ');
  return { label: words.charAt(0).toUpperCase() + words.slice(1), tone: 'off' };
}

export function targetTypeLabel(type: string | null | undefined): string {
  if (!type) {
    return '';
  }
  const label = TARGET_TYPES.find(target => target.value === type)?.label ?? type;
  return label === 'Team environments' ? 'Environment' : label.replace(/s$/, '');
}
