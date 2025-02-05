import { StoredObject } from './stored-object.model';
import { RemoteServerStats } from './remote-server-stats.model';

export class RemoteServer extends StoredObject {
  name?: string;
  ipAddress?: string;
  remoteServerStats?: RemoteServerStats;

  constructor(serverResult?: any) {
    super(serverResult);
    if (serverResult) {
      this.name = serverResult.name;
      this.ipAddress = serverResult.ipAddress;
      // Instantiate remoteServerStats if available.
      this.remoteServerStats = serverResult.remoteServerStats ? new RemoteServerStats(serverResult.remoteServerStats) : undefined;
    }
  }
}
