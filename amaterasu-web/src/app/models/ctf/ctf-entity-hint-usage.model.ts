import { StoredObject } from "../stored-object.model";
import { Hint } from "./hint.model";

export class CTFEntityHintUsage extends StoredObject {
    ctfEntityAnswerId?: string;
    hint?: Hint;
    usedAt?: Date;
    pointsDeducted?: number;
    usageOrder?: number;
  
    constructor(serverResult?: any) {
      super(serverResult);
      if (serverResult) {
        this.ctfEntityAnswerId = serverResult.ctfEntityAnswerId;
        this.hint = serverResult.hint ? new Hint(serverResult.hint) : undefined;
        this.usedAt = serverResult.usedAt ? new Date(serverResult.usedAt) : undefined;
        this.pointsDeducted = serverResult.pointsDeducted;
        this.usageOrder = serverResult.usageOrder;
      }
    }
  }
  