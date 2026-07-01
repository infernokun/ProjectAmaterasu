export interface LabVmNetworkConfig {
  vmid?: number;
  bridge?: string;
  ipAddress?: string;
}

export class LabRequest {
  labId?: string;
  userId?: string;
  teamId?: string;
  labTrackerId?: string;
  remoteServerId?: string;
  networkConfig?: LabVmNetworkConfig[];

  constructor(serverResult?: any) {
    if (serverResult) {
      this.labId = serverResult.labId;
      this.userId = serverResult.userId;
      this.teamId = serverResult.teamId;
      this.labTrackerId = serverResult.labTrackerId;
      this.remoteServerId = serverResult.remoteServerId;
      this.networkConfig = serverResult.networkConfig;
    }
  }
}
