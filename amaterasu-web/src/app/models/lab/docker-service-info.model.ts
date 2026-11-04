export class DockerServiceInfo {
  name: string = 'unknown';
  state: string = 'unknown';
  /** Compose service name; logs and restarts take it. */
  service?: string;
  image?: string;
  ports: string[] = [];
  volumes: Map<string, string>[] = [];
  ipAddresses: string[] = [];
  networks: string[] = [];

  constructor(serverResult?: any) {
    if (serverResult) {
      this.name = serverResult.name;
      this.state = serverResult.state;
      this.service = serverResult.service ?? undefined;
      this.image = serverResult.image ?? undefined;
      this.ports = serverResult.ports;
      this.volumes = serverResult.volumes;
      this.ipAddresses = serverResult.ipAddresses;
      this.networks = serverResult.networks;
    }
  }
}
