import { LabStatus } from "../enums/lab-status.enum";
import { LabType } from "../enums/lab-type.enum";
import { RadioQuestion, NumberQuestion, SimpleFormData, TextQuestion, UploadBoxQuestion, CheckBoxQuestion, DropDownQuestion, ObservableMap } from "./simple-form-data.model";
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

export class LabDTO {
  name?: string;
  description?: string;
  version?: string;
  capacity?: number;
  labType?: LabType
  dockerFile?: any;
  createdBy?: string;
  vms?: string[];
  remoteServer?: string;

  constructor(serverResult?: any) {
    if (serverResult) {

      this.name = serverResult.name;
      this.description = serverResult.description;
      this.version = serverResult.version;
      this.capacity = serverResult.capacity;
      this.labType = serverResult.labType;
      this.dockerFile = serverResult.dockerFile ? serverResult.dockerFile.content : undefined;
      this.createdBy = serverResult.createdBy;
      this.vms = serverResult.vms ? serverResult.vms : undefined;
      this.remoteServer = serverResult.remoteServer ? serverResult.remoteServer : undefined;
    }
  }
}


export class LabFormData extends SimpleFormData {
  constructor(
    updateResultsCB: Function = (k: any, v: any) => { },
    observables?: ObservableMap
  ) {
    super('lab');

    this.preFilledData = new Map<string, string>([
    ]);

    this.questions.push(
      new DropDownQuestion({
        label: "Remote Server",
        key: "remoteServer",
        options: [],
        asyncData: observables && observables['remoteServer'] ? observables['remoteServer'] : undefined
      }),
      new RadioQuestion({
        label: 'Lab Type',
        key: 'labType',
        options: Object.values(LabType).map(value => ({ key: value, value }))
      }),
      new TextQuestion({
        label: 'Name',
        key: 'name',
      }),
      new TextQuestion({
        label: 'Description',
        key: 'description',
      }),
      new TextQuestion({
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
        neededEnum: { key: 'labType', value: LabType.DOCKER_COMPOSE },
      }, true),
      new CheckBoxQuestion({
        label: "VMs",
        key: "vms",
        options2: [],
        neededEnum: { key: 'labType', value: LabType.VIRTUAL_MACHINE },
        asyncData: observables && observables['vms'] ? observables['vms'] : undefined
      }, true)
    );
    this.questions.forEach((e) => (e.cb = updateResultsCB));
  }
}
