import { Role } from '../../../enums/role.enum';
import { credentialsCsv, parseUsersCsv } from './user-import';

const teams = [{ id: 't1', name: 'Red Fox' }, { id: 't2', name: 'Night Owls' }];
const generate = () => 'tidal-Ember-4821-quartz';

describe('parseUsersCsv', () => {
  it('reads the columns by name, in any order and case, with defaults', () => {
    const csv = '﻿Team,Username,ROLE,Password\nred fox,jmorales,creator,\n,priya.n,,Long-enough-pass1\n\n';
    const { rows, error } = parseUsersCsv(csv, teams, [], generate);
    expect(error).toBeNull();
    expect(rows.length).toBe(2);
    expect(rows[0]).toEqual(jasmine.objectContaining({
      line: 2, username: 'jmorales', role: Role.CREATOR, team: teams[0], password: 'tidal-Ember-4821-quartz', generatedPassword: true, problems: [],
    }));
    expect(rows[1]).toEqual(jasmine.objectContaining({
      line: 3, username: 'priya.n', role: Role.MEMBER, team: null, password: 'Long-enough-pass1', generatedPassword: false, problems: [],
    }));
  });

  it('checks usernames, roles, teams, passwords and duplicates per row', () => {
    const csv = [
      'username,role,team,password',
      'ok_user,member,,',
      'a b,member,,',
      'sbell,member,,',
      'OK_USER,member,,',
      'role_user,owner,,',
      'team_user,member,Blue Ridge,',
      'pass_user,member,,short',
      ',member,,',
    ].join('\n');
    const { rows } = parseUsersCsv(csv, teams, ['SBell'], generate);
    expect(rows.map(row => row.problems)).toEqual([
      [],
      ['Username must be 3 to 32 letters, numbers, dots, dashes or underscores'],
      ['An account with this name already exists'],
      ['Also on line 2'],
      ['Role owner is not Member, Facilitator, Creator or Admin'],
      ['No team named Blue Ridge'],
      ['Password must be 12 to 72 characters'],
      ['No username'],
    ]);
  });

  it('refuses files without a username column, without rows, or with too many rows', () => {
    expect(parseUsersCsv('name,team\nx,y', teams, []).error).toContain('username column');
    expect(parseUsersCsv('username,team\n', teams, []).error).toContain('no accounts');
    const big = 'username\n' + Array.from({ length: 1001 }, (_, i) => `user${i}`).join('\n');
    expect(parseUsersCsv(big, teams, []).error).toContain('at most 1000');
  });

  it('keeps quoted commas inside a field', () => {
    const { rows } = parseUsersCsv('username,team\n"lchen","Red Fox"\n', teams, []);
    expect(rows[0].team).toEqual(teams[0]);
  });

  it('writes the new accounts and passwords back out as CSV', () => {
    const csv = credentialsCsv([{ username: 'jmorales', password: 'a,b', role: 'Member', team: 'Red Fox' }]);
    expect(csv).toBe('username,password,role,team\r\njmorales,"a,b",Member,Red Fox');
  });
});
