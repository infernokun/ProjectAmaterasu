import { StoredObject } from "./stored-object.model";

export class Team extends StoredObject {
  name?: string;
  description?: string;
  teamActiveLabs?: string[];
  teamDeletedLabs?: string[];

  constructor(serverResult?: any) {
    if (serverResult) {
      super(serverResult);

      this.name = serverResult.name;
      this.description = serverResult.description;
      this.teamActiveLabs = serverResult.teamActiveLabs;
      this.teamDeletedLabs = serverResult.teamDeletedLabs;
    }
  }
}
