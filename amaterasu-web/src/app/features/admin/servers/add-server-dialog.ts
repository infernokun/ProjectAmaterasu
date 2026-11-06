import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ServerType } from '../../../enums/server-type.enum';
import { errorMessage } from '../../../core/http-error';
import { IconComponent } from '../../../shared/ui/icon.component';
import { NewServer } from './servers.service';

export interface AddServerData {
  validate: (server: NewServer) => Promise<boolean>;
  add: (server: NewServer) => Promise<unknown>;
}

type TestState = 'idle' | 'running' | 'ok' | 'failed';

@Component({
  selector: 'app-add-server-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" role="dialog" aria-modal="true" aria-labelledby="add-h" (submit)="$event.preventDefault(); add()">
      <div class="row between">
        <h2 class="h2" id="add-h">Add server</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      <div class="seg" role="group" aria-label="Server type">
        <button type="button" [class.on]="docker()" (click)="pick(types.DOCKER_HOST)">Docker host over SSH</button>
        <button type="button" [class.on]="!docker()" (click)="pick(types.PROXMOX)">Proxmox node</button>
      </div>
      <div class="form-grid">
        <label class="field"><span class="label">Name</span>
          <input class="input mono" [value]="name()" (input)="edit(name, $event)" [placeholder]="docker() ? 'dock-03' : 'pve-c'" required>
        </label>
        <label class="field"><span class="label">Address</span>
          <input class="input mono" [value]="address()" (input)="edit(address, $event)" placeholder="10.20.0.13" required>
        </label>
      </div>
      @if (docker()) {
        <div class="form-grid">
          <label class="field"><span class="label">SSH port</span>
            <input class="input num" type="number" min="1" max="65535" [value]="port()" (input)="edit(port, $event)">
          </label>
          <label class="field"><span class="label">Username</span>
            <input class="input mono" autocomplete="off" [value]="username()" (input)="edit(username, $event)">
          </label>
        </div>
        <label class="field"><span class="label">Password</span>
          <input class="input" type="password" autocomplete="new-password" [value]="password()" (input)="edit(password, $event)">
          <span class="help">Stored encrypted with the server's ENCRYPTION_KEY</span>
        </label>
      } @else {
        <div class="form-grid">
          <label class="field"><span class="label">API port</span>
            <input class="input num" type="number" min="1" max="65535" [value]="port()" (input)="edit(port, $event)">
          </label>
          <label class="field"><span class="label">Node name</span>
            <input class="input mono" [value]="nodeName()" (input)="edit(nodeName, $event)" placeholder="pve-c">
          </label>
        </div>
        <label class="field"><span class="label">API token</span>
          <input class="input mono" autocomplete="off" placeholder="TOKEN_ID=SECRET" [value]="apiToken()" (input)="edit(apiToken, $event)">
          <span class="help">Format TOKEN_ID=SECRET. Stored encrypted.</span>
        </label>
      }
      @switch (test()) {
        @case ('ok') {
          <div class="banner ok" role="status"><app-icon name="check" /><div>{{ docker() ? 'Connected over SSH and found Docker.' : 'Token accepted by the node.' }} You can add {{ name() }} now.</div></div>
        }
        @case ('failed') {
          <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() || 'No answer from ' + address() + '. Check the address, port and credentials.' }}</div></div>
        }
      }
      @if (addError()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ addError() }}</div></div>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="button" class="btn" [disabled]="!complete() || test() === 'running'" (click)="runTest()">
          @if (test() === 'running') { <app-icon name="spinner" size="sm" [spin]="true" /> }
          Test connection
        </button>
        <button type="submit" class="btn primary" [disabled]="test() !== 'ok' || adding()">Add server</button>
      </div>
    </form>
  `,
  styles: `.seg { align-self: flex-start; }`,
})
export class AddServerDialogComponent {
  private readonly data = inject<AddServerData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<AddServerDialogComponent>>(MatDialogRef);
  protected readonly types = ServerType;

  protected readonly type = signal<ServerType>(ServerType.DOCKER_HOST);
  protected readonly docker = computed(() => this.type() === ServerType.DOCKER_HOST);
  protected readonly name = signal('');
  protected readonly address = signal('');
  protected readonly port = signal('22');
  protected readonly username = signal('amaterasu');
  protected readonly password = signal('');
  protected readonly apiToken = signal('');
  protected readonly nodeName = signal('');
  protected readonly test = signal<TestState>('idle');
  protected readonly error = signal('');
  protected readonly addError = signal('');
  protected readonly adding = signal(false);

  protected readonly complete = computed(() => !!this.name().trim() && !!this.address().trim() && Number(this.port()) > 0
    && (this.docker() ? !!this.username().trim() && !!this.password() : /^.+=.+$/.test(this.apiToken().trim()) && !!this.nodeName().trim()));

  private request(): NewServer {
    const base = { serverType: this.type(), name: this.name().trim(), ipAddress: this.address().trim(), port: Number(this.port()) };
    return this.docker()
      ? { ...base, username: this.username().trim(), password: this.password() }
      : { ...base, apiToken: this.apiToken().trim(), nodeName: this.nodeName().trim() };
  }

  protected pick(type: ServerType): void {
    this.type.set(type);
    this.port.set(type === ServerType.PROXMOX ? '8006' : '22');
    this.test.set('idle');
  }

  /** Any change means the last test no longer applies. */
  protected edit(field: { set(value: string): void }, event: Event): void {
    field.set((event.target as HTMLInputElement).value);
    this.test.set('idle');
    this.addError.set('');
  }

  protected async runTest(): Promise<void> {
    this.test.set('running');
    this.error.set('');
    try {
      this.test.set((await this.data.validate(this.request())) ? 'ok' : 'failed');
    } catch (error) {
      this.error.set(errorMessage(error) ?? '');
      this.test.set('failed');
    }
  }

  protected async add(): Promise<void> {
    if (this.test() !== 'ok' || this.adding()) {
      return;
    }
    this.adding.set(true);
    this.addError.set('');
    try {
      await this.data.add(this.request());
      this.ref.close(true);
    } catch (error) {
      this.addError.set(errorMessage(error) ?? 'The server could not be added.');
    } finally {
      this.adding.set(false);
    }
  }
}
