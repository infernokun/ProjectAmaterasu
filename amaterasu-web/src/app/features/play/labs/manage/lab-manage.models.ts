/** Shapes from /api/room/{labId}/teams and /api/room/{labId}/manage. Dates are ISO strings. */
import { EnvironmentCopy, EnvironmentMode, EnvironmentStatus, TeamCredit } from '../../data/play.models';

/** Whose teams count in a lab: the site teams, none (everyone solo) or the lab's own. */
export type TeamMode = 'SITE' | 'NONE' | 'LAB';

export interface LabTeamMember {
  userId: string;
  username: string;
  displayName: string | null;
}

export interface LabTeamView {
  id: string;
  name: string;
  description: string | null;
  members: LabTeamMember[];
  /** The team's copy of a per-team environment; null when the lab has none. */
  copyStatus: EnvironmentStatus | null;
}

export interface LabTeamsView {
  roomId: string;
  roomName: string;
  teamMode: TeamMode;
  teamCredit: TeamCredit;
  environmentMode: EnvironmentMode;
  /** The lab's own teams, kept while another mode is picked. */
  labTeams: LabTeamView[];
  siteTeams: LabTeamView[];
}

/** Someone who can be put on a lab team. */
export interface TeamCandidate {
  userId: string;
  username: string;
  displayName: string | null;
  role: string | null;
  siteTeamName: string | null;
  labTeamId: string | null;
  labTeamName: string | null;
  /** Has answered or unlocked a hint in this lab. */
  played: boolean;
}

export interface TimeBucket {
  at: string;
  solves: number;
  attempts: number;
}

export interface QuestionRate {
  id: string;
  title: string;
  points: number;
  solves: number;
  attemptedBy: number;
  attempts: number;
  /** Percent of the lab's players who solved it. */
  solveRate: number;
  hintsUsed: number;
}

export interface ManageOverview {
  roomId: string;
  roomName: string;
  teamMode: TeamMode;
  teamCredit: TeamCredit;
  environmentMode: EnvironmentMode;
  players: number;
  /** Did something in the last 15 minutes. */
  activePlayers: number;
  teams: number;
  questions: number;
  solves: number;
  attempts: number;
  wrongAttempts: number;
  nearMisses: number;
  hintsUsed: number;
  hintPoints: number;
  firstActivityAt: string | null;
  bucketMinutes: number;
  solvesOverTime: TimeBucket[];
  questionRates: QuestionRate[];
  copies: EnvironmentCopy[];
}

export interface NearMissView {
  flagId: string | null;
  flag: string;
  reasons: string[];
}

export interface Submission {
  /** "answerId:index"; what credit and accept take. */
  id: string;
  answerId: string;
  index: number;
  userId: string;
  username: string;
  teamId: string | null;
  teamName: string | null;
  questionId: string;
  questionTitle: string;
  questionPoints: number;
  flag: string | null;
  at: string | null;
  correct: boolean;
  /** Who gave credit for this attempt. */
  creditedBy: string | null;
  /** The player has the question now, however they got it. */
  questionSolved: boolean;
  nearMiss: NearMissView | null;
}

export interface SubmissionPage {
  items: Submission[];
  page: number;
  size: number;
  total: number;
  nearMisses: number;
}

export type SubmissionResult = 'all' | 'correct' | 'wrong' | 'near';

export interface SubmissionQuery {
  userId?: string | null;
  teamId?: string | null;
  questionId?: string | null;
  result?: SubmissionResult;
  page?: number;
  size?: number;
}

export interface PlayerRow {
  userId: string;
  username: string;
  displayName: string | null;
  teamId: string | null;
  teamName: string | null;
  points: number;
  solves: number;
  attempts: number;
  wrongAttempts: number;
  hintsUsed: number;
  hintPoints: number;
  firstActivityAt: string | null;
  lastActivityAt: string | null;
}

export interface TeamRow {
  teamId: string;
  name: string;
  members: string[];
  /** Members' points plus awards to the team as a whole. */
  points: number;
  awardPoints: number;
  solves: number;
  attempts: number;
  hintsUsed: number;
}

export interface ManagePlayers {
  players: PlayerRow[];
  teams: TeamRow[];
}

export interface QuestionProgress {
  questionId: string;
  title: string;
  points: number;
  solved: boolean;
  solvedAt: string | null;
  score: number;
  attempts: number;
  hintsUsed: number;
  hintPoints: number;
  creditedBy: string | null;
  /** Who solved it for a shared-solves team. */
  solvedByTeammate: string | null;
}

export interface PointsHistoryEntry {
  timestamp: string;
  totalPoints: number;
  pointsChange: number;
  reason: string;
  awardedBy: string | null;
  kind: 'AWARD' | 'CREDIT' | 'RESET' | null;
}

export interface PlayerDetail {
  player: PlayerRow;
  questions: QuestionProgress[];
  submissions: Submission[];
  history: PointsHistoryEntry[];
}

export interface WrongAnswer {
  answer: string;
  count: number;
  players: number;
  nearMiss: NearMissView | null;
}

export interface QuestionStats {
  id: string;
  title: string;
  category: string | null;
  points: number;
  attempts: number;
  attemptedBy: number;
  solves: number;
  solveRate: number;
  hintsUsed: number;
  nearMisses: number;
  wrongAnswers: WrongAnswer[];
}

export type ActivityKind = 'FIRST_ACTIVITY' | 'HINT' | 'SOLVE' | 'CREDIT' | 'AWARD' | 'TEAM_AWARD' | 'RESET' | 'ACCEPTED_ANSWER'
  | 'TEAMS' | 'ENVIRONMENT';

export interface ActivityItem {
  kind: ActivityKind;
  at: string | null;
  who: string | null;
  team: string | null;
  text: string;
  detail: string | null;
  points: number | null;
}

export interface Credited {
  userId: string;
  username: string;
  questionId: string;
  points: number;
  /** Set when the solve also counts for the player's team. */
  team: string | null;
}

export interface Accepted {
  flagId: string;
  flag: string;
  credited: Credited[];
  skipped: string[];
}

export interface ResetResult {
  userId: string;
  username: string;
  questionId: string | null;
  questionsReset: number;
  pointsChange: number;
}
