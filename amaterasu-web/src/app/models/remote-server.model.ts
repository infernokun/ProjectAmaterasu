import { StoredObject } from './stored-object.model';
import { RemoteServerStats } from './remote-server-stats.model';
import { PasswordQuestion, SimpleFormData, TextQuestion } from './simple-form-data.model';

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

export class RemoteServerFormData extends SimpleFormData {
  constructor(
    updateResultsCB: Function = (k: any, v: any) => { }
  ) {
    super('lab');

    this.preFilledData = new Map<string, string>([
    ]);

    this.questions.push(
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
        key: "username"
      }),
      new PasswordQuestion({
        label: "Password",
        key: "password"
      })
    );
    this.questions.forEach((e) => (e.cb = updateResultsCB));
  }
}
