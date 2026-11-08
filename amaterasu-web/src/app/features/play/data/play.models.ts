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

export interface AttachmentView {
  name: string;
  fileId: string | null;
  size: number | null;
}

/** A file players download from a lab. */
export interface LabFileView {
  id: string;
  name: string;
  contentType: string | null;
  size: number;
  sha256: string | null;
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
  /** Markdown. */
  description: string | null;
  /** Files the question names; fileId is set when the lab has the file to download. */
  attachments: AttachmentView[];
  hints: HintView[];
  attempts: number;
  solved: boolean;
  solvedAt: string | null;
  /** Teammates who solved it; with SHARED_SOLVES that solves it for the viewer too. */
  solvedByTeammates: string[];
}

export type EnvironmentMode = 'NONE' | 'SHARED' | 'PER_TEAM';
/** SEE_TEAMMATES keeps answers per person; SHARED_SOLVES counts one member's solve for the team. */
export type TeamCredit = 'SEE_TEAMMATES' | 'SHARED_SOLVES';

/** The one status every view shows for a lab's environment. */
export type EnvironmentStatus = 'NOT_RUNNING' | 'STARTING' | 'RUNNING' | 'PAUSED' | 'FAILED';

/** One copy of a lab's environment, for the people who run it: the shared one, a team's, or the Solo copy. */
export interface EnvironmentCopy {
  /** 'shared', 'solo' or 'team:<teamId>'; what an action sends as its target. */
  target: string;
  label: string;
  teamId: string | null;
  solo: boolean;
  members: number;
  status: EnvironmentStatus;
  trackerId: string | null;
  tracker: unknown | null;
}

/** A lab's environment as the viewer sees it: the shared copy, their team's, or the Solo copy. */
export interface EnvironmentView {
  labId: string;
  labName: string;
  labType: string | null;
  mode: EnvironmentMode;
  status: EnvironmentStatus;
  trackerId: string | null;
  /** The copy, as a raw lab tracker; null until one exists. */
  tracker: unknown | null;
  /** Which copy the viewer works in, such as "Solo copy". */
  copyLabel?: string | null;
  /** The viewer runs this lab: an admin, or one of its facilitators. */
  canControl: boolean;
  /** A calm line for players when there is nothing to use yet. */
  note: string | null;
  copiesRunning: number;
  /** Every copy, for those who run the lab; empty for players and in the Labs list. */
  copies?: EnvironmentCopy[];
}

/** One lab in the Labs list, from GET /api/room/cards. */
export interface LabCard {
  id: string;
  name: string;
  summary: string;
  environmentMode: EnvironmentMode;
  environment: EnvironmentView | null;
  questionCount: number;
  playerCount: number;
  points: number;
  solved: number;
  /** The viewer facilitates this lab, or is an admin. */
  canRun: boolean;
  /** The viewer's site-wide team, if any. */
  teamId: string | null;
  teamName: string | null;
}

/** The reply to starting, pausing or stopping a lab's environment. */
export interface EnvironmentActionResult {
  successful: boolean;
  message: string;
  environment: EnvironmentView | null;
}

export interface RoomOverview {
  room: {
    id: string; name: string; surroundTag: string | null; creator: string | null; facilitators: string[];
    scenario: string | null; environmentMode: EnvironmentMode; canRun: boolean; teamCredit: TeamCredit;
  };
  playerCount: number;
  teamCount: number;
  me: { points: number; rank: number; solved: number; teamId: string | null; teamName: string | null };
  challenges: ChallengeView[];
  environment: EnvironmentView | null;
  files: LabFileView[];
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
  /** Members who played this lab. */
  members: number;
  /** Everyone on the team. */
  teamSize?: number;
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
  /** When the room was created (ISO); the points chart starts there. */
  openedAt?: string | null;
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

/** A challenge that uses a lab, from GET /api/ctf-entity/by-lab/{labId}. Never carries flags. */
export interface LabChallenge {
  id: string;
  question?: string | null;
  title?: string | null;
  roomId: string;
  roomName?: string | null;
  category?: string | null;
  points?: number | null;
  /** Whether the caller solved it. */
  solved?: boolean | null;
}

/** How to log in to a lab copy's VMs. Only the owning team and admins can read it. */
export interface LabAccess {
  username: string | null;
  password: string | null;
  /** The bastion's LAN address, null until its guest agent reports it. */
  address: string | null;
  labSubnet: string | null;
  bastion: string | null;
}
