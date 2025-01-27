import { StoredObject } from "./stored-object.model";
import { Team } from "./team.model";

export class User extends StoredObject {
    username?: string;
    team?: Team;

    constructor(serverResult?: any) {
        if (serverResult) {
            super(serverResult);
            
            this.username = serverResult.username;
            this.team = serverResult.team;
        }
    }
}
