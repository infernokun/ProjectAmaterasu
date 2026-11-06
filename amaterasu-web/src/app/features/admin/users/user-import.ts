import Papa from 'papaparse';
import { Role } from '../../../enums/role.enum';
import { MAX_PASSWORD, MIN_PASSWORD, generatePassword } from '../../../core/password';
import { TeamRef } from './admin-users.service';

export const MAX_IMPORT_ROWS = 1000;
const USERNAME = /^[A-Za-z0-9_.-]{3,32}$/;
const ROLES: Record<string, Role> = {
  member: Role.MEMBER, facilitator: Role.FACILITATOR, creator: Role.CREATOR, admin: Role.ADMIN,
};
const HEADERS: Record<string, keyof RawRow> = {
  username: 'username', user: 'username', login: 'username',
  role: 'role',
  team: 'team', 'team name': 'team',
  password: 'password', 'temporary password': 'password',
};

interface RawRow {
  username: string;
  role: string;
  team: string;
  password: string;
}

/** One account from the file, checked in the browser before anything is sent. */
export interface ImportRow {
  /** Line in the file, counting the header as line 1. */
  line: number;
  username: string;
  role: Role;
  team: TeamRef | null;
  password: string;
  generatedPassword: boolean;
  problems: string[];
}

export interface ParsedImport {
  rows: ImportRow[];
  /** Why the file as a whole cannot be used, such as a missing username column. */
  error: string | null;
}

/** A file to start from, with the columns the importer reads. */
export const TEMPLATE_CSV = 'username,role,team,password\njdoe,Member,Red Fox,\nasmith,Facilitator,,\n';

/**
 * Parses a CSV of accounts. The header row names the columns: username is required; role, team and
 * password are optional. Rows without a password get a generated one.
 */
export function parseUsersCsv(text: string, teams: TeamRef[], existing: string[], generate = generatePassword): ParsedImport {
  const parsed = Papa.parse<Record<string, string>>(text.replace(/^﻿/, ''), {
    header: true,
    skipEmptyLines: 'greedy',
    transformHeader: header => header.trim().toLowerCase(),
  });
  const columns = new Set((parsed.meta.fields ?? []).map(field => HEADERS[field]).filter(Boolean));
  if (!columns.has('username')) {
    return { rows: [], error: 'The first row has to name the columns, with at least a username column.' };
  }
  if (!parsed.data.length) {
    return { rows: [], error: 'The file has no accounts under its header row.' };
  }
  if (parsed.data.length > MAX_IMPORT_ROWS) {
    return { rows: [], error: `Import at most ${MAX_IMPORT_ROWS} accounts at a time. This file has ${parsed.data.length}.` };
  }

  const byTeam = new Map(teams.map(team => [team.name.trim().toLowerCase(), team]));
  const taken = new Set(existing.map(name => name.toLowerCase()));
  const seen = new Map<string, number>();

  const rows = parsed.data.map((record, index) => {
    const raw: RawRow = { username: '', role: '', team: '', password: '' };
    for (const [field, value] of Object.entries(record)) {
      const key = HEADERS[field];
      if (key) {
        raw[key] = (value ?? '').trim();
      }
    }
    const line = index + 2;
    const problems: string[] = [];
    const lower = raw.username.toLowerCase();

    if (!raw.username) {
      problems.push('No username');
    } else if (!USERNAME.test(raw.username)) {
      problems.push('Username must be 3 to 32 letters, numbers, dots, dashes or underscores');
    } else if (taken.has(lower)) {
      problems.push('An account with this name already exists');
    } else if (seen.has(lower)) {
      problems.push(`Also on line ${seen.get(lower)}`);
    }
    if (raw.username && !seen.has(lower)) {
      seen.set(lower, line);
    }

    const role = raw.role ? ROLES[raw.role.toLowerCase()] : Role.MEMBER;
    if (!role) {
      problems.push(`Role ${raw.role} is not Member, Facilitator, Creator or Admin`);
    }

    const team = raw.team ? byTeam.get(raw.team.toLowerCase()) ?? null : null;
    if (raw.team && !team) {
      problems.push(`No team named ${raw.team}`);
    }

    const bytes = new TextEncoder().encode(raw.password).length;
    if (raw.password && (raw.password.length < MIN_PASSWORD || bytes > MAX_PASSWORD)) {
      problems.push(`Password must be ${MIN_PASSWORD} to ${MAX_PASSWORD} characters`);
    }

    return {
      line,
      username: raw.username,
      role: role ?? Role.MEMBER,
      team,
      password: raw.password || generate(),
      generatedPassword: !raw.password,
      problems,
    };
  });
  return { rows, error: null };
}

/** The new accounts and their passwords, to hand out after the import. */
export function credentialsCsv(rows: { username: string; password: string; team: string | null; role: string }[]): string {
  return Papa.unparse({
    fields: ['username', 'password', 'role', 'team'],
    data: rows.map(row => [row.username, row.password, row.role, row.team ?? '']),
  });
}
