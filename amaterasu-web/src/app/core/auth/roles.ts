import { Role } from '../../enums/role.enum';

/** Who can use each area of the app. Routes and navigation both read these. */
export const AREA_ROLES = {
  run: [Role.FACILITATOR, Role.ADMIN],
  build: [Role.CREATOR, Role.ADMIN],
  admin: [Role.ADMIN],
} as const satisfies Record<string, readonly Role[]>;

export const ROLE_LABELS: Record<Role, string> = {
  [Role.ADMIN]: 'Admin',
  [Role.CREATOR]: 'Creator',
  [Role.FACILITATOR]: 'Facilitator',
  [Role.DEVELOPER]: 'Developer',
  [Role.TEAM_ADMIN]: 'Team admin',
  [Role.MEMBER]: 'Member',
};

/** Roles from the access token's space separated `roles` claim. */
export function rolesFromToken(claims: { roles?: unknown } | null | undefined): Role[] {
  const raw = typeof claims?.roles === 'string' ? claims.roles : '';
  const known = new Set<string>(Object.values(Role));
  return raw.split(' ').filter((role): role is Role => known.has(role));
}
