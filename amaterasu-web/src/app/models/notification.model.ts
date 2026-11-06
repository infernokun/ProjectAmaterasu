/** One notification for the signed-in user, from /api/notifications or the socket. */
export interface AppNotification {
  id: string;
  /** e.g. LAB_FAILED, POINTS_AWARDED, CHALLENGE_RELEASED, SERVER_OFFLINE. */
  type: string;
  title: string;
  body?: string | null;
  /** A path in this app, possibly with a query string. */
  link?: string | null;
  /** ISO timestamps. */
  createdAt: string;
  readAt?: string | null;
}
