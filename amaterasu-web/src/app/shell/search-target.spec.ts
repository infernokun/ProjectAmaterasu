import { highlight, searchTarget } from './search-target';

describe('searchTarget', () => {
  it('opens team copies on their environment page and labs in Play', () => {
    expect(searchTarget({ type: 'TEAM_LAB', id: 't1', title: 'Web' }, false)).toEqual({ commands: ['/environments', 't1'] });
    expect(searchTarget({ type: 'ROOM', id: 'r1', title: 'Range' }, true)).toEqual({ commands: ['/labs', 'r1'] });
  });

  it('sends members to the labs and authors to the environment builder', () => {
    expect(searchTarget({ type: 'LAB', id: 'l1', title: 'Web' }, false)).toEqual({ commands: ['/labs'] });
    expect(searchTarget({ type: 'LAB', id: 'l1', title: 'Web' }, true)).toEqual({ commands: ['/build/environments', 'l1'] });
  });

  it('opens a question in its lab, or in the editor while it is hidden or not released', () => {
    const open = { type: 'CHALLENGE' as const, id: 'c1', title: 'Kerberoast', roomId: 'r1' };
    expect(searchTarget(open, false)).toEqual({ commands: ['/labs', 'r1'], queryParams: { challenge: 'c1' } });
    expect(searchTarget({ ...open, status: 'UNRELEASED' }, true)).toEqual({ commands: ['/build/labs', 'r1'] });
  });

  it('opens users, teams and servers in Admin', () => {
    expect(searchTarget({ type: 'USER', id: 'u1', title: 'jmorales' }, true).queryParams).toEqual({ q: 'jmorales' });
    expect(searchTarget({ type: 'TEAM', id: 't1', title: 'Red Fox' }, true).queryParams).toEqual({ tab: 'teams' });
    expect(searchTarget({ type: 'SERVER', id: 's1', title: 'pve-a' }, true))
      .toEqual({ commands: ['/admin/servers'], queryParams: { server: 's1' } });
  });
});

describe('highlight', () => {
  it('splits the title around the first match, ignoring case', () => {
    expect(highlight('AD Forest Breach', 'forest')).toEqual({ before: 'AD ', match: 'Forest', after: ' Breach' });
    expect(highlight('Kerberoast', 'web')).toEqual({ before: 'Kerberoast', match: '', after: '' });
  });
});
