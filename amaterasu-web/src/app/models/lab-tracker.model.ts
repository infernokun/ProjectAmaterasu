import { LabStatus } from "../enums/lab-status.enum";
import { DockerServiceInfo } from "./docker-service-info.model";
import { Lab } from "./lab.model";
import { ProxmoxVM } from "./proxmox-vm.model";
import { RemoteServer } from "./remote-server.model";
import { StoredObject } from "./stored-object.model";
import { Team } from "./team.model";

export class LabTracker extends StoredObject {
  labStarted?: Lab;
  labStatus?: LabStatus;
  labOwner?: Team;
  services?: DockerServiceInfo[];
  vms?: ProxmoxVM[];
  remoteServer?: RemoteServer;

  constructor(serverResult?: any) {
    if (serverResult) {
      super(serverResult);

      this.labStarted = serverResult.labStarted;
      this.labStatus = serverResult.labStatus;
      this.labOwner = serverResult.labOwner;
      this.services = serverResult.services ?? [];
      this.vms = serverResult.vms ?? [];
      this.remoteServer = serverResult.remoteServer;
    }
  }
}
