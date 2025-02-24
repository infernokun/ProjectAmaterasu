import { StoredObject } from './stored-object.model';
import { RemoteServerStats } from './remote-server-stats.model';
import { RadioQuestion, PasswordQuestion, SimpleFormData, TextQuestion } from './simple-form-data.model';
import { ServerType } from '../enums/server-type.enum';

export class RemoteServer extends StoredObject {
  name?: string;
  ipAddress?: string;
  serverType?: ServerType;
  remoteServerStats?: RemoteServerStats;
  nodeName?: string;

  constructor(serverResult?: any) {
    super(serverResult);
    if (serverResult) {
      this.name = serverResult.name;
      this.ipAddress = serverResult.ipAddress;
      this.serverType = serverResult.serverType;
      this.remoteServerStats = serverResult.remoteServerStats ? new RemoteServerStats(serverResult.remoteServerStats) : undefined;
      this.nodeName = serverResult.nodeName;
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
      new RadioQuestion({
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
        neededEnum: { key: "serverType", value: ServerType.DOCKER_HOST }
      }),
      new PasswordQuestion({
        label: "Password",
        key: "password",
        neededEnum: { key: "serverType", value: ServerType.DOCKER_HOST }
      }),
      new TextQuestion({
        label: "API Token",
        key: "apiToken",
        neededEnum: { key: "serverType", value: ServerType.PROXMOX },
        hint: "TOKEN_ID=SECRET",
        size: 100
      }, true),
      new TextQuestion({
        label: "Node Name",
        key: "nodeName",
        neededEnum: { key: "serverType", value: ServerType.PROXMOX }
      }, true)
    );
    this.questions.forEach((e) => (e.cb = updateResultsCB));
  }
}
