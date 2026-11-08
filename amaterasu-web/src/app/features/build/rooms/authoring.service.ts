import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { ApiResponse } from '../../../models/api-response.model';
import { EnvironmentService } from '../../../services/environment.service';

export type EnvironmentMode = 'NONE' | 'SHARED' | 'PER_TEAM';
export type TeamCredit = 'SEE_TEAMMATES' | 'SHARED_SOLVES';

/** A lab as authors edit it. It is stored as a room; its environment is a lab definition. */
export interface AuthorRoom {
  id: string;
  name: string;
  surroundTag: string;
  updatedAt: string | null;
  scenario: string;
  environmentMode: EnvironmentMode;
  environmentId: string | null;
  /** How site-wide teams count in this lab. */
  teamCredit: TeamCredit;
  /** Drafts are only seen by authors and the lab's facilitators. */
  published: boolean;
}

/** Fields a lab edit can send; left out ones stay as they are. */
export interface RoomChanges {
  name?: string;
  surroundTag?: string;
  scenario?: string;
  environmentMode?: EnvironmentMode;
  environmentId?: string | null;
  teamCredit?: TeamCredit;
  published?: boolean;
}

export interface AuthorFlag {
  flag: string;
  caseSensitive: boolean;
  surroundWithTag: boolean;
  weight: number;
}

export interface AuthorHint {
  /** Kept so hints players already paid for keep their history. */
  id?: string;
  hint: string;
  cost: number;
  unlockAfterAttempts: number;
}

/** An account picked as a room's facilitator. */
export interface Person {
  id: string;
  username: string;
}

/** An environment (lab definition) a lab can run. */
export interface LabOption {
  id: string;
  name: string;
  published: boolean;
  labType?: string | null;
}

export interface AuthorChallenge {
  id?: string;
  roomId: string | null;
  /** The lab players use for it, or null. */
  labId: string | null;
  question: string;
  category: string;
  difficultyLevel: DifficultyLevel;
  points: number;
  /** Null for unlimited attempts. */
  maxAttempts: number | null;
  /** Markdown. */
  description: string;
  visible: boolean;
  /** "yyyy-MM-dd HH:mm:ss" in UTC, as the API stores it. */
  releaseDate: string | null;
  expirationDate: string | null;
  solutionExplanation: string;
  author: string | null;
  flags: AuthorFlag[];
  hints: AuthorHint[];
  solveCount: number;
  attemptCount: number;
  /** Names of lab files players download for it. */
  attachments: string[];
}

/** A file players download from a lab, without its bytes. */
export interface LabFileView {
  id: string;
  name: string;
  contentType: string | null;
  size: number;
  sha256: string | null;
}

/** Authoring endpoints for labs and their questions. Raw questions include flags, so creators only. */
@Injectable({ providedIn: 'root' })
export class AuthoringService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);

  private url(path: string): string {
    return `${this.env.settings?.restUrl}${path}`;
  }

  rooms(): Observable<AuthorRoom[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/room')).pipe(map(r => (r.data ?? []).map(toRoom)));
  }

  room(roomId: string): Observable<AuthorRoom> {
    return this.http.get<ApiResponse<any>>(this.url(`/room/${roomId}`)).pipe(map(r => toRoom(r.data)));
  }

  createRoom(changes: RoomChanges): Observable<AuthorRoom> {
    return this.http.post<ApiResponse<any>>(this.url('/room'), changes).pipe(map(r => toRoom(r.data)));
  }

  updateRoom(roomId: string, changes: RoomChanges): Observable<AuthorRoom> {
    return this.http.put<ApiResponse<any>>(this.url(`/room/${roomId}`), changes).pipe(map(r => toRoom(r.data)));
  }

  deleteRoom(roomId: string): Observable<unknown> {
    return this.http.delete(this.url(`/room/${roomId}`));
  }

  /** How many players joined the room. */
  players(roomId: string): Observable<number> {
    return this.http.get<ApiResponse<unknown[]>>(this.url(`/room/scoreboard/${roomId}/users`)).pipe(map(r => (r.data ?? []).length));
  }

  facilitators(roomId: string): Observable<Person[]> {
    return this.http.get<ApiResponse<Person[]>>(this.url(`/room/${roomId}/facilitators`)).pipe(map(r => r.data ?? []));
  }

  /** Accounts with the Facilitator role, who can be assigned to a room. */
  facilitatorCandidates(): Observable<Person[]> {
    return this.http.get<ApiResponse<Person[]>>(this.url('/room/facilitator-candidates')).pipe(map(r => r.data ?? []));
  }

  setFacilitators(roomId: string, userIds: string[]): Observable<Person[]> {
    return this.http.put<ApiResponse<Person[]>>(this.url(`/room/${roomId}/facilitators`), { userIds }).pipe(map(r => r.data ?? []));
  }

  /** Saves the order players see the room's challenges in; every challenge in the room, once. */
  reorder(roomId: string, challengeIds: string[]): Observable<unknown> {
    return this.http.put(this.url(`/room/${roomId}/order`), { challengeIds });
  }

  labs(): Observable<LabOption[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/labs')).pipe(map(r => (r.data ?? [])
      .map(lab => ({ id: lab.id, name: lab.name ?? 'Untitled environment', published: lab.published === true, labType: lab.labType ?? null }))
      .sort((a, b) => a.name.localeCompare(b.name))));
  }

  /** Every challenge in every room, for the counts on the room list. */
  allChallenges(): Observable<AuthorChallenge[]> {
    return this.http.get<ApiResponse<any[]>>(this.url('/ctf-entity/full')).pipe(map(r => (r.data ?? []).map(toChallenge)));
  }

  challenges(roomId: string): Observable<AuthorChallenge[]> {
    return this.http.get<ApiResponse<any[]>>(this.url(`/ctf-entity/room/${roomId}`)).pipe(map(r => (r.data ?? []).map(toChallenge)));
  }

  create(challenge: AuthorChallenge): Observable<AuthorChallenge> {
    const body = {
      ...writable(challenge),
      roomId: challenge.roomId,
      author: challenge.author,
      hints: challenge.hints.map((hint, orderIndex) => ({ hint: hint.hint, cost: hint.cost, unlockAfterAttempts: hint.unlockAfterAttempts, orderIndex })),
    };
    return this.http.post<ApiResponse<any>>(this.url('/ctf-entity'), body).pipe(map(r => toChallenge(r.data)));
  }

  update(challenge: AuthorChallenge): Observable<AuthorChallenge> {
    return this.http.put<ApiResponse<any>>(this.url(`/ctf-entity/${challenge.id}`), writable(challenge)).pipe(map(r => toChallenge(r.data)));
  }

  delete(challengeId: string): Observable<unknown> {
    return this.http.delete(this.url(`/ctf-entity/${challengeId}`));
  }

  files(roomId: string): Observable<LabFileView[]> {
    return this.http.get<ApiResponse<LabFileView[]>>(this.url(`/room/${roomId}/files`)).pipe(map(r => r.data ?? []));
  }

  /** Uploads a lab file; a name that exists is replaced. */
  uploadFile(roomId: string, file: File, name?: string): Observable<LabFileView> {
    const form = new FormData();
    form.append('file', file, file.name);
    if (name) form.append('name', name);
    return this.http.post<ApiResponse<LabFileView>>(this.url(`/room/${roomId}/files`), form).pipe(map(r => r.data));
  }

  deleteFile(roomId: string, fileId: string): Observable<unknown> {
    return this.http.delete(this.url(`/room/${roomId}/files/${fileId}`));
  }

  /** Adds challenges in one request; each one needs `room: { id }`. */
  importChallenges(challenges: object[]): Observable<number> {
    return this.http.post<ApiResponse<unknown[]>>(this.url('/ctf-entity/batch'), challenges).pipe(map(r => (r.data ?? []).length));
  }
}

function writable(challenge: AuthorChallenge) {
  return {
    question: challenge.question,
    category: challenge.category,
    difficultyLevel: challenge.difficultyLevel,
    points: challenge.points,
    maxAttempts: challenge.maxAttempts,
    description: challenge.description,
    visible: challenge.visible,
    releaseDate: challenge.releaseDate,
    expirationDate: challenge.expirationDate,
    solutionExplanation: challenge.solutionExplanation,
    labId: challenge.labId,
    attachments: challenge.attachments,
    flags: challenge.flags,
    hints: challenge.hints,
  };
}

export function toRoom(raw: any): AuthorRoom {
  return {
    id: raw.id, name: raw.name ?? 'Untitled lab', surroundTag: raw.surroundTag ?? 'CTF{x}', updatedAt: raw.updatedAt ?? null,
    scenario: raw.scenario ?? '', environmentMode: raw.environmentId ? raw.environmentMode ?? 'NONE' : 'NONE',
    environmentId: raw.environmentId ?? null,
    teamCredit: raw.teamCredit ?? 'SEE_TEAMMATES',
    published: raw.published !== false,
  };
}

export function toChallenge(raw: any): AuthorChallenge {
  return {
    id: raw.id,
    roomId: raw.room?.id ?? null,
    labId: raw.labId ?? null,
    question: raw.question ?? '',
    category: raw.category ?? '',
    difficultyLevel: raw.difficultyLevel ?? DifficultyLevel.EASY,
    points: raw.points ?? 0,
    maxAttempts: raw.maxAttempts ?? null,
    description: raw.description ?? '',
    visible: raw.visible !== false,
    releaseDate: raw.releaseDate ?? null,
    expirationDate: raw.expirationDate ?? null,
    solutionExplanation: raw.solutionExplanation ?? '',
    author: raw.author ?? null,
    flags: (raw.flags ?? []).map((flag: any) => ({
      flag: flag.flag ?? '', caseSensitive: flag.caseSensitive !== false, surroundWithTag: !!flag.surroundWithTag, weight: flag.weight ?? 1,
    })),
    hints: [...(raw.hints ?? [])]
      .sort((a: any, b: any) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))
      .map((hint: any) => ({ id: hint.id, hint: hint.hint ?? '', cost: hint.cost ?? 0, unlockAfterAttempts: hint.unlockAfterAttempts ?? 0 })),
    solveCount: raw.solveCount ?? 0,
    attemptCount: raw.attemptCount ?? 0,
    attachments: raw.attachments ?? [],
  };
}
