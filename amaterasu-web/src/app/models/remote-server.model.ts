import { StoredObject } from './stored-object.model';
import { RemoteServerStats } from './remote-server-stats.model';
import { CheckboxQuestion, PasswordQuestion, SimpleFormData, TextQuestion } from './simple-form-data.model';
import { ServerType } from '../enums/server-type.enum';

export class RemoteServer extends StoredObject {
  name?: string;
  ipAddress?: string;
  serverType?: ServerType;
  remoteServerStats?: RemoteServerStats;

  constructor(serverResult?: any) {
    super(serverResult);
    if (serverResult) {
      this.name = serverResult.name;
      this.ipAddress = serverResult.ipAddress;
      this.serverType = serverResult.serverType;
      this.remoteServerStats = serverResult.remoteServerStats ? new RemoteServerStats(serverResult.remoteServerStats) : undefined;
    }
  }
}

export class RemoteServerFormData extends SimpleFormData {
  constructor(
    updateResultsCB: Function = (k: any, v: any) => { }
  ) {
    super('remoteServer');

    this.preFilledData = new Map<string, string>([
    ]);

    this.questions.push(
      new CheckboxQuestion({
        label: 'Server Type',
        key: 'serverType',
        options: Object.values(ServerType).map(value => ({ key: value, value }))
      }),
      new TextQuestion({
        label: 'Name',
        key: 'name',
      }),
      new TextQuestion({
        label: 'IPAddress',
        key: 'ipAddress',
      }),
      new TextQuestion({
        label: "Username",
        key: "username",
        neededEnum: { key: "serverType", value: ServerType.DOCKER_HOST },
      }),
      new PasswordQuestion({
        label: "Password",
        key: "password",
        neededEnum: { key: "serverType", value: ServerType.DOCKER_HOST },
      }),
      new TextQuestion({
        label: "API Token",
        key: "apiToken",
        neededEnum: { key: "serverType", value: ServerType.PROXMOX },
      }, true)
    );
    this.questions.forEach((e) => (e.cb = updateResultsCB));
  }
}
