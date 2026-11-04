/** One entry in the audit log (GET /api/audit). */
export interface AuditEvent {
  id: string;
  at: string;
  actorId: string | null;
  actorUsername: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  targetLabel: string | null;
  teamId: string | null;
  detail: string | null;
}

/** Spring's page shape. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
