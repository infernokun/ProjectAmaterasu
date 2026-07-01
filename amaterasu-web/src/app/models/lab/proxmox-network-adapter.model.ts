export class ProxmoxNetworkAdapter {
  iface?: string;
  cidr?: string;
  gateway?: string;
  availableIpCount?: number;

  constructor(result?: any) {
    if (result) {
      this.iface = result.iface;
      this.cidr = result.cidr;
      this.gateway = result.gateway;
      this.availableIpCount = result.availableIpCount;
    }
  }
}
