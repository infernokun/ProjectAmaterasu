import { CTFEntityHintUsage } from "../ctf/ctf-entity-hint-usage.model";
import { Hint } from "../ctf/hint.model";
import { JoinRoomResponse } from "./join-room-response.model";

export class CTFEntityHintResponse {
    joinRoomResponse?: JoinRoomResponse;
    ctfEntiyId?: string;
    requestedHint?: Hint;
    hintsUsed?: CTFEntityHintUsage[];
  
    constructor(serverResult?: any) {
      if (serverResult) {
        this.joinRoomResponse = serverResult.joinRoomResponse;
        this.ctfEntiyId = serverResult.ctfEntiyId;
        this.requestedHint = serverResult.requestedHint ? new Hint(serverResult.requestedHint) : undefined;
        this.hintsUsed = serverResult.hintsUsed ? serverResult.hintsUsed : [];
      }
    }
  }
  