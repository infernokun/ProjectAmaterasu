import { StatusTone } from '../../../shared/lab-status';

export interface ActionGroup {
  key: string;
  label: string;
  actions: string[];
}

export const ACTION_GROUPS: ActionGroup[] = [
  { key: 'all', label: 'All', actions: [] },
  {
    key: 'labs', label: 'Labs',
    actions: ['LAB_STARTED', 'LAB_PAUSED', 'LAB_STOPPED', 'LAB_DELETED', 'LAB_ACTION_FAILED', 'LAB_CREATED', 'LAB_UPDATED', 'LAB_REMOVED'],
  },
  { key: 'signin', label: 'Sign in', actions: ['SIGN_IN', 'SIGN_IN_FAILED', 'PASSWORD_CHANGED', 'SESSION_REVOKED'] },
  {
    key: 'users', label: 'Users',
    actions: ['USER_CREATED', 'USER_ROLE_CHANGED', 'USER_DISABLED', 'USER_ENABLED', 'USER_TEAM_CHANGED', 'PASSWORD_RESET'],
  },
  { key: 'teams', label: 'Teams', actions: ['TEAM_CREATED', 'TEAM_UPDATED', 'TEAM_DELETED'] },
  {
    key: 'authoring', label: 'Rooms and challenges',
    actions: ['ROOM_CREATED', 'ROOM_UPDATED', 'ROOM_DELETED', 'CHALLENGE_CREATED', 'CHALLENGE_UPDATED', 'CHALLENGE_DELETED', 'CHALLENGE_IMPORTED'],
  },
  { key: 'scores', label: 'Points', actions: ['POINTS_AWARDED', 'FLAG_SOLVED'] },
  { key: 'servers', label: 'Servers', actions: ['SERVER_ADDED', 'SERVER_DELETED'] },
];

export const TARGET_TYPES: { value: string; label: string }[] = [
  { value: '', label: 'All targets' },
  { value: 'LAB_TRACKER', label: 'Team labs' },
  { value: 'LAB', label: 'Lab catalog' },
  { value: 'USER', label: 'Users' },
  { value: 'TEAM', label: 'Teams' },
  { value: 'ROOM', label: 'Rooms' },
  { value: 'CHALLENGE', label: 'Challenges' },
  { value: 'SERVER', label: 'Servers' },
  { value: 'SESSION', label: 'Sessions' },
];

const LABELS: Record<string, [string, StatusTone]> = {
  LAB_STARTED: ['Started lab', 'active'],
  LAB_PAUSED: ['Paused lab', 'paused'],
  LAB_STOPPED: ['Stopped lab', 'off'],
  LAB_DELETED: ['Removed lab from team', 'off'],
  LAB_ACTION_FAILED: ['Lab action failed', 'bad'],
  LAB_CREATED: ['Created lab', 'accent'],
  LAB_UPDATED: ['Edited lab', 'work'],
  LAB_REMOVED: ['Deleted lab', 'bad'],
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
  ROOM_CREATED: ['Created room', 'accent'],
  ROOM_UPDATED: ['Edited room', 'work'],
  ROOM_DELETED: ['Deleted room', 'bad'],
  CHALLENGE_CREATED: ['Created challenge', 'accent'],
  CHALLENGE_UPDATED: ['Edited challenge', 'work'],
  CHALLENGE_DELETED: ['Deleted challenge', 'bad'],
  CHALLENGE_IMPORTED: ['Imported challenges', 'accent'],
  POINTS_AWARDED: ['Awarded points', 'accent'],
  FLAG_SOLVED: ['Solved a challenge', 'active'],
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
  return label === 'Team labs' ? 'Lab' : label.replace(/s$/, '');
}
