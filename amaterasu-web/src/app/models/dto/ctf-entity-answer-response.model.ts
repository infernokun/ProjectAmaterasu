import { CTFEntityHintUsage } from "../ctf/ctf-entity-hint-usage.model";
import { FlagAnswer } from "../ctf/flag-answer.model";
import { StoredObject } from "../stored-object.model";
import { JoinRoomResponse } from "./join-room-response.model";

export class CTFEntityAnswerResponse extends StoredObject {
    correct?: boolean;
    attempts?: number;
    answers?: FlagAnswer[];
    attemptTimes?: Date[];
    solvedAt?: Date;
    lastAttemptAt?: Date;
    score?: number;
    hintsUsed?: CTFEntityHintUsage[];
    solveTimeSeconds?: number;
    joinRoomResponse?: JoinRoomResponse;
  
    constructor(serverResult?: any) {
        if (serverResult) {
            super(serverResult);

            this.correct = serverResult.correct;
            this.attempts = serverResult.attempts;
            this.answers = serverResult.answers;
            this.attemptTimes = serverResult.attemptTimes?.map((time: string) => new Date(time));
            this.solvedAt = serverResult.solvedAt ? new Date(serverResult.solvedAt) : undefined;
            this.lastAttemptAt = serverResult.lastAttemptAt ? new Date(serverResult.lastAttemptAt) : undefined;
            this.score = serverResult.score;
            this.hintsUsed = serverResult.hintsUsed ? serverResult.hintsUsed.map((hint: any) => new CTFEntityHintUsage(hint)) : [];
            this.solveTimeSeconds = serverResult.solveTimeSeconds;
            this.joinRoomResponse = serverResult.joinRoomResponse;
        }
    }
}