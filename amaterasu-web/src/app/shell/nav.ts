import { Role } from '../enums/role.enum';
import { AREA_ROLES } from '../core/auth/roles';
import { IconName } from '../shared/ui/icons';

export type NavCountKey = 'labs';

export interface NavItem {
  label: string;
  icon: IconName;
  link: string;
  /** A live count shown at the right of the item. */
  count?: NavCountKey;
}

export interface NavGroup {
  label: 'Play' | 'Run' | 'Build' | 'Admin';
  /** Left out for groups everyone sees. */
  roles?: readonly Role[];
  items: NavItem[];
}

/** The sidebar, grouped by job. A group shows only for the roles that can use it. */
export const NAV: readonly NavGroup[] = [
  {
    label: 'Play',
    items: [
      { label: 'Home', icon: 'home', link: '/home' },
      { label: 'Labs', icon: 'flask', link: '/labs', count: 'labs' },
      { label: 'Scoreboard', icon: 'trophy', link: '/scoreboard' },
    ],
  },
  {
    label: 'Run',
    roles: AREA_ROLES.run,
    items: [{ label: 'Live event', icon: 'activity', link: '/run/live' }],
  },
  {
    label: 'Build',
    roles: AREA_ROLES.build,
    items: [
      { label: 'Labs', icon: 'edit', link: '/build/labs' },
      { label: 'Environments', icon: 'layers', link: '/build/environments' },
    ],
  },
  {
    label: 'Admin',
    roles: AREA_ROLES.admin,
    items: [
      { label: 'Users and teams', icon: 'users', link: '/admin/users' },
      { label: 'Servers', icon: 'server', link: '/admin/servers' },
      { label: 'Audit log', icon: 'file', link: '/admin/audit' },
    ],
  },
];

export function navFor(roles: readonly Role[]): NavGroup[] {
  return NAV.filter(group => !group.roles || group.roles.some(role => roles.includes(role)));
}
