/** Shapes returned by the room, scoreboard and live event endpoints. Dates are ISO strings. */

export type ChallengeState = 'OPEN' | 'LOCKED' | 'EXPIRED';

export interface HintView {
  id: string;
  number: number;
  cost: number;
  unlockAfterAttempts: number;
  unlocked: boolean;
  text: string | null;
  usedAt: string | null;
}

export interface ChallengeView {
  id: string;
  title: string;
  category: string;
  difficulty: string | null;
  points: number;
  author: string | null;
  maxAttempts: number | null;
  solves: number;
  state: ChallengeState;
  releaseDate: string | null;
  expirationDate: string | null;
  description: string | null;
  attachments: string[];
  hints: HintView[];
  attempts: number;
  solved: boolean;
  solvedAt: string | null;
}

export interface RoomOverview {
  room: { id: string; name: string; surroundTag: string | null; creator: string | null; facilitators: string[] };
  playerCount: number;
  teamCount: number;
  me: { joined: boolean; points: number; rank: number; solved: number };
  challenges: ChallengeView[];
}

export interface StandingPoint {
  at: string;
  total: number;
}

export interface PlayerStanding {
  rank: number;
  userId: string;
  username: string;
  teamId: string | null;
  teamName: string | null;
  points: number;
  solves: number;
  hintsUsed: number;
  wrongAttempts: number;
  lastSolveAt: string | null;
  lastHourChange: number;
  solvesByCategory: Record<string, number>;
  history: StandingPoint[];
}

export interface TeamStanding {
  rank: number;
  teamId: string;
  name: string;
  members: number;
  points: number;
  solves: number;
  hintsUsed: number;
  lastSolveAt: string | null;
  lastHourChange: number;
  history: StandingPoint[];
}

export interface RoomStandings {
  roomId: string;
  roomName: string;
  players: PlayerStanding[];
  teams: TeamStanding[];
}

export type FeedKind = 'SOLVE' | 'HINT' | 'AWARD';

export interface FeedItem {
  kind: FeedKind;
  at: string | null;
  who: string;
  team: string | null;
  text: string;
  detail: string | null;
  points: number;
}

export interface ChallengeHealth {
  id: string;
  title: string;
  solves: number;
  wrongAttempts: number;
  attemptedBy: number;
  hintsUsed: number;
}

export interface PlayerRef {
  userId: string;
  username: string;
  teamName: string | null;
  points: number;
}

export interface RoomLive {
  roomId: string;
  roomName: string;
  playerCount: number;
  teamCount: number;
  solvesRecent: number;
  solvesTotal: number;
  wrongRecent: number;
  topWrong: { title: string; count: number } | null;
  hintsUnlocked: number;
  hintPoints: number;
  feed: FeedItem[];
  challenges: ChallengeHealth[];
  players: PlayerRef[];
}

/** The answer endpoint's reply, reduced to what the room view uses. */
export interface AnswerResult {
  correct: boolean;
  attempts: number;
  joinRoomResponse?: { points?: number };
}

/** A team copy's compose file, as GET /lab-tracker/settings returns it. */
export interface LabFiles {
  yml: string;
  json: { services?: Record<string, { image?: string; volumes?: (string | { source?: string; target?: string })[] }> } | null;
}
