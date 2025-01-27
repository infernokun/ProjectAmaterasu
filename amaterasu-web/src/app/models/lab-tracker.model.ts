import { LabStatus } from "../enums/lab-status.enum";
import { Lab } from "./lab.model";
import { StoredObject } from "./stored-object.model";
import { Team } from "./team.model";

export class LabTracker extends StoredObject {
    labStarted?: Lab;
    labStatus?: LabStatus;
    labOwner?: Team;

    constructor(serverResult?: any) {
        if (serverResult) {
            super(serverResult);
            
            this.labStarted = serverResult.labStarted;
            this.labStatus = serverResult.labStatus;
            this.labOwner = serverResult.labOwner;
        }
    }
}
