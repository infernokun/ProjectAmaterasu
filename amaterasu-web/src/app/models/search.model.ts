export type SearchHitType = 'TEAM_LAB' | 'LAB' | 'ROOM' | 'CHALLENGE' | 'USER' | 'TEAM' | 'SERVER';

/** One match from GET /api/search. */
export interface SearchHit {
  type: SearchHitType;
  id: string;
  title: string;
  subtitle?: string | null;
  /** Set for challenges. */
  roomId?: string | null;
  /** Lab status for team labs, server health for servers, role for users, HIDDEN or UNRELEASED for challenges. */
  status?: string | null;
}

export interface SearchResults {
  query: string;
  hits: SearchHit[];
}
