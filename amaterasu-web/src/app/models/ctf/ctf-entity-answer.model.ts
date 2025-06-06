import { StoredObject } from '../stored-object.model';
import { CTFEntity } from './ctf-entity.model';
import { Hint } from './hint.model';

export class CTFEntityAnswer extends StoredObject {
  roomUser?: any; // RoomUser or JoinRoomResponse
  ctfEntity?: CTFEntity;
  correct?: boolean;
  attempts?: number;
  answers?: any[]; // CTFEntityAnswerRequest[]
  attemptTimes?: Date[];
  solvedAt?: Date;
  lastAttemptAt?: Date;
  score?: number;
  hintsUsed?: Hint[];
  solveTimeSeconds?: number;

  constructor(serverResult?: any) {
    super(serverResult);

    if (serverResult) {
      this.roomUser = serverResult.roomUser;
      this.ctfEntity = serverResult.ctfEntity ? new CTFEntity(serverResult.ctfEntity) : undefined;
      this.correct = serverResult.correct;
      this.attempts = serverResult.attempts;
      this.answers = serverResult.answers ? serverResult.answers : [];
      this.attemptTimes = serverResult.attemptTimes ? serverResult.attemptTimes.map((time: string) => new Date(time)) : [];
      this.solvedAt = serverResult.solvedAt ? new Date(serverResult.solvedAt) : undefined;
      this.lastAttemptAt = serverResult.lastAttemptAt ? new Date(serverResult.lastAttemptAt)  : undefined;
      this.score = serverResult.score;
      this.hintsUsed = serverResult.hintsUsed ? serverResult.hintsUsed.map((hint: any) => new Hint(hint)) : [];
      this.solveTimeSeconds = serverResult.solveTimeSeconds;
    }
  }
}
