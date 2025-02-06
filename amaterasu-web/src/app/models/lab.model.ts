import { LabStatus } from "../enums/lab-status.enum";
import { LabType } from "../enums/lab-type.enum";
import { Button, CheckboxQuestion, DropDownQuestion, NumberQuestion, SimpleFormData, TextAreaQuestion, TextQuestion, UploadBoxQuestion } from "./simple-form-data.model";
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

export class LabFormData extends SimpleFormData {
  constructor(
    updateResultsCB: Function = (k: any, v: any) => { }
  ) {
    super('lab');

    this.preFilledData = new Map<string, string>([
    ]);

    this.questions.push(
      new CheckboxQuestion({
        label: 'Lab Type',
        key: 'labType',
        options: Object.values(LabType).map(value => ({ key: value, value }))
      }),
      new TextQuestion({
        label: 'Name',
        key: 'Name',
      }),
      new TextQuestion({
        label: 'Description',
        key: 'description',
      }),
      new NumberQuestion({
        label: "Version",
        key: "version"
      }),
      new NumberQuestion({
        label: "Capacity",
        key: "capacity"
      }),
      new UploadBoxQuestion({
        label: "DockerCompose",
        key: "dockerFile",
      })
    );
    this.questions.forEach((e) => (e.cb = updateResultsCB));
  }
}
