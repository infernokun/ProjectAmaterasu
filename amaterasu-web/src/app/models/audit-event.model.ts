/** One recorded action, from GET /api/audit. */
export interface AuditEvent {
  id: string;
  /** ISO timestamp. */
  at: string;
  actorId?: string | null;
  actorUsername: string;
  /** e.g. LAB_STARTED, USER_DISABLED, SIGN_IN. */
  action: string;
  /** LAB_TRACKER, LAB, USER, TEAM, ROOM, CHALLENGE, SERVER or SESSION. */
  targetType?: string | null;
  targetId?: string | null;
  targetLabel?: string | null;
  teamId?: string | null;
  detail?: string | null;
}

/** A page of results in Spring's shape. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  /** Zero based. */
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
