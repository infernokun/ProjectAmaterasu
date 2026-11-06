export class ProxmoxVM {
  name?: string;
  vmid?: number;
  status?: string;
  uptime?: number;
  mem?: number;
  maxmem?: number;
  cpu?: number;
  cpus?: number;
  template?: boolean;
  /** 'bastion' for the lab's way in from the LAN. */
  role?: string;
  /** Cloud-init user that logs in with the copy's password. */
  ciuser?: string;
  /** The template enables the guest agent, so `ready` is tracked. */
  guestAgent?: boolean;
  /** Whether the guest agent answers; unset when it is not tracked. */
  ready?: boolean | null;
  /** A bastion's LAN address. */
  accessAddress?: string | null;
  uplinkBridge?: string | null;
  /** The lab network behind a bastion, e.g. 172.16.0.0/24. */
  labSubnet?: string | null;

  constructor(serverResult?: any) {
    if (serverResult) {
      this.name = serverResult.name;
      this.vmid = serverResult.vmid;
      this.status = serverResult.status;
      this.uptime = serverResult.uptime;
      this.mem = serverResult.mem;
      this.maxmem = serverResult.maxmem;
      this.cpu = serverResult.cpu;
      this.cpus = serverResult.cpus;
      this.template = serverResult.template;
      this.role = serverResult.role;
      this.ciuser = serverResult.ciuser;
      this.guestAgent = serverResult.guestAgent;
      this.ready = serverResult.ready;
      this.accessAddress = serverResult.accessAddress;
      this.uplinkBridge = serverResult.uplinkBridge;
      this.labSubnet = serverResult.labSubnet;
    }
  }
}
