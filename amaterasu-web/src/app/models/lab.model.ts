import { LabStatus } from "../enums/lab-status.enum";
import { LabType } from "../enums/lab-type.enum";
import { StoredObject } from "./stored-object.model";

export class Lab extends StoredObject {
  name?: string;
  description?: string;
  status?: LabStatus;
  version?: string;
  capacity?: number;
  labType?: LabType
  dockerFile?: string;
  ready?: boolean;

  constructor(serverResult?: any) {
    if (serverResult) {
      super(serverResult);

      this.name = serverResult.name;
      this.description = serverResult.description;
      this.status = serverResult.status;
      this.version = serverResult.version;
      this.capacity = serverResult.capacity;
      this.labType = serverResult.labType;
      this.dockerFile = serverResult.dockerFile;
      this.ready = serverResult.ready;
    }
  }
}
