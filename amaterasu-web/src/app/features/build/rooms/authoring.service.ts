import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { ApiResponse } from '../../../models/api-response.model';
import { EnvironmentService } from '../../../services/environment.service';

export interface AuthorRoom {
  id: string;
  name: string;
  surroundTag: string;
  updatedAt: string | null;
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

export interface AuthorChallenge {
  id?: string;
  roomId: string | null;
  question: string;
  category: string;
  difficultyLevel: DifficultyLevel;
  points: number;
  maxAttempts: number;
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
}

/** Authoring endpoints for rooms and their challenges. Raw challenges include flags, so creators only. */
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

  createRoom(name: string, surroundTag: string): Observable<AuthorRoom> {
    return this.http.post<ApiResponse<any>>(this.url('/room'), { name, surroundTag }).pipe(map(r => toRoom(r.data)));
  }

  updateRoom(roomId: string, name: string, surroundTag: string): Observable<AuthorRoom> {
    return this.http.put<ApiResponse<any>>(this.url(`/room/${roomId}`), { name, surroundTag }).pipe(map(r => toRoom(r.data)));
  }

  deleteRoom(roomId: string): Observable<unknown> {
    return this.http.delete(this.url(`/room/${roomId}`));
  }

  /** How many players joined the room. */
  players(roomId: string): Observable<number> {
    return this.http.get<ApiResponse<unknown[]>>(this.url(`/room/scoreboard/${roomId}/users`)).pipe(map(r => (r.data ?? []).length));
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
    flags: challenge.flags,
    hints: challenge.hints,
  };
}

export function toRoom(raw: any): AuthorRoom {
  return { id: raw.id, name: raw.name ?? 'Untitled room', surroundTag: raw.surroundTag ?? 'CTF{x}', updatedAt: raw.updatedAt ?? null };
}

export function toChallenge(raw: any): AuthorChallenge {
  return {
    id: raw.id,
    roomId: raw.room?.id ?? null,
    question: raw.question ?? '',
    category: raw.category ?? '',
    difficultyLevel: raw.difficultyLevel ?? DifficultyLevel.EASY,
    points: raw.points ?? 0,
    maxAttempts: raw.maxAttempts ?? 10,
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
  };
}
