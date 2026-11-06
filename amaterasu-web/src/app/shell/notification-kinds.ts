import { StatusTone } from '../shared/lab-status';
import { IconName } from '../shared/ui/icons';

/** How each notification type looks in the bell. */
export function notificationLook(type: string): { icon: IconName; tone: StatusTone | null } {
  switch (type) {
    case 'LAB_FAILED': return { icon: 'alert', tone: 'bad' };
    case 'LAB_DEGRADED': return { icon: 'alert', tone: 'warn' };
    case 'LAB_ATTENTION': return { icon: 'activity', tone: 'warn' };
    case 'POINTS_AWARDED': return { icon: 'award', tone: 'accent' };
    case 'CHALLENGE_RELEASED': return { icon: 'flag', tone: 'work' };
    case 'SESSION_SIGNED_OUT': return { icon: 'signOut', tone: null };
    case 'PASSWORD_CHANGED': return { icon: 'key', tone: 'warn' };
    case 'ROLE_CHANGED': return { icon: 'shield', tone: 'work' };
    case 'TEAM_CHANGED': return { icon: 'users', tone: 'work' };
    case 'SERVER_OFFLINE': return { icon: 'server', tone: 'bad' };
    case 'SERVER_ONLINE': return { icon: 'server', tone: 'active' };
    default: return { icon: 'bell', tone: null };
  }
}
