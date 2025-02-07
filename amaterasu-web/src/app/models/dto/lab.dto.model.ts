import { LabType } from "../../enums/lab-type.enum";

export class LabDTO {
  name?: string;
  description?: string;
  version?: string;
  capacity?: number;
  labType?: LabType
  dockerFile?: any;
  createdBy?: string;

  constructor(serverResult?: any) {
    if (serverResult) {

      this.name = serverResult.name;
      this.description = serverResult.description;
      this.version = serverResult.version;
      this.capacity = serverResult.capacity;
      this.labType = serverResult.labType;
      this.dockerFile = serverResult.dockerFile.content;
      this.createdBy = serverResult.createdBy;
    }
  }
}
