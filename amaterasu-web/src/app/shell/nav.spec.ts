import { Role } from '../enums/role.enum';
import { navFor } from './nav';

describe('navFor', () => {
  const groups = (...roles: Role[]) => navFor(roles).map(group => group.label);

  it('shows members only Play', () => {
    expect(groups(Role.MEMBER)).toEqual(['Play']);
  });

  it('adds Run for facilitators and Build for creators', () => {
    expect(groups(Role.FACILITATOR)).toEqual(['Play', 'Run']);
    expect(groups(Role.CREATOR)).toEqual(['Play', 'Build']);
  });

  it('shows admins every group', () => {
    expect(groups(Role.ADMIN)).toEqual(['Play', 'Run', 'Build', 'Admin']);
  });

  it('never links the developer code page', () => {
    const links = navFor([Role.ADMIN, Role.DEVELOPER]).flatMap(group => group.items.map(item => item.link));
    expect(links.some(link => link.includes('code'))).toBeFalse();
  });
});
