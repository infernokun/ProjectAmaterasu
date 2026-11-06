import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ServerType } from '../../../enums/server-type.enum';
import { errorMessage } from '../../../core/http-error';
import { IconComponent } from '../../../shared/ui/icon.component';
import { ServerEdit, ServerSettings } from './servers.service';

export interface EditServerData {
  settings: ServerSettings;
  /** Names of the other servers, which this one may not take. */
  otherNames: string[];
  validate: (edit: ServerEdit) => Promise<boolean>;
  save: (edit: ServerEdit) => Promise<unknown>;
}

type TestState = 'idle' | 'running' | 'ok' | 'failed';

const ADDRESS = /^([A-Za-z0-9.-]+|\[[0-9A-Fa-f:.]+\])$/;

/** What is wrong with an edit, field by field; empty when it can be saved. */
export function editProblems(edit: ServerEdit, type: ServerType, otherNames: string[]): Partial<Record<keyof ServerEdit, string>> {
  const problems: Partial<Record<keyof ServerEdit, string>> = {};
  const name = edit.name.trim();
  if (!name) {
    problems.name = 'Give the server a name';
  } else if (otherNames.some(other => other.toLowerCase() === name.toLowerCase())) {
    problems.name = `Another server is called ${name}`;
  }
  if (!ADDRESS.test(edit.ipAddress.trim())) {
    problems.ipAddress = 'A host name or IP address, without http:// or a path';
  }
  if (!Number.isInteger(edit.port) || edit.port < 1 || edit.port > 65535) {
    problems.port = 'A port from 1 to 65535';
  }
  if (type === ServerType.DOCKER_HOST && !edit.username?.trim()) {
    problems.username = 'The SSH user Amaterasu signs in as';
  }
  if (type === ServerType.PROXMOX && !edit.nodeName?.trim()) {
    problems.nodeName = 'The node name as Proxmox shows it';
  }
  if (type === ServerType.PROXMOX && edit.apiToken && !/^.+=.+$/.test(edit.apiToken.trim())) {
    problems.apiToken = 'Format TOKEN_ID=SECRET';
  }
  return problems;
}

/** Edit a server: name, address, port and user, with the password or API token entered again only to change it. */
@Component({
  selector: 'app-edit-server-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" role="dialog" aria-modal="true" aria-labelledby="edit-h" (submit)="$event.preventDefault(); save()">
      <div class="row between">
        <h2 class="h2" id="edit-h">Edit {{ data.settings.name }}</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      <div class="small muted">{{ docker ? 'Docker host over SSH' : 'Proxmox node' }}. The type cannot change; add a new server for that.</div>
      <div class="form-grid">
        <label class="field"><span class="label">Name</span>
          <input class="input mono" [class.err]="shown('name')" [value]="name()" (input)="edit(name, $event)" (blur)="touch('name')">
          @if (shown('name')) { <span class="help bad">{{ problems().name }}</span> }
        </label>
        <label class="field"><span class="label">Address</span>
          <input class="input mono" [class.err]="shown('ipAddress')" [value]="address()" (input)="edit(address, $event)" (blur)="touch('ipAddress')">
          @if (shown('ipAddress')) { <span class="help bad">{{ problems().ipAddress }}</span> }
        </label>
      </div>
      <div class="form-grid">
        <label class="field"><span class="label">{{ docker ? 'SSH port' : 'API port' }}</span>
          <input class="input num" type="number" min="1" max="65535" [class.err]="shown('port')" [value]="port()"
                 (input)="edit(port, $event)" (blur)="touch('port')">
          @if (shown('port')) { <span class="help bad">{{ problems().port }}</span> }
        </label>
        @if (docker) {
          <label class="field"><span class="label">Username</span>
            <input class="input mono" autocomplete="off" [class.err]="shown('username')" [value]="username()"
                   (input)="edit(username, $event)" (blur)="touch('username')">
            @if (shown('username')) { <span class="help bad">{{ problems().username }}</span> }
          </label>
        } @else {
          <label class="field"><span class="label">Node name</span>
            <input class="input mono" [class.err]="shown('nodeName')" [value]="nodeName()" (input)="edit(nodeName, $event)" (blur)="touch('nodeName')">
            @if (shown('nodeName')) { <span class="help bad">{{ problems().nodeName }}</span> }
          </label>
        }
      </div>
      @if (docker) {
        <label class="field"><span class="label">Password</span>
          <input class="input" type="password" autocomplete="new-password" [value]="secret()" (input)="edit(secret, $event)"
                 [placeholder]="data.settings.hasPassword ? 'Leave empty to keep the stored password' : 'No password stored'">
          <span class="help">Enter it again only to change it. Stored encrypted with the server's ENCRYPTION_KEY.</span>
        </label>
      } @else {
        <label class="field"><span class="label">API token</span>
          <input class="input mono" autocomplete="off" [class.err]="shown('apiToken')" [value]="secret()" (input)="edit(secret, $event)"
                 (blur)="touch('apiToken')" [placeholder]="data.settings.hasApiToken ? 'Leave empty to keep the stored token' : 'TOKEN_ID=SECRET'">
          <span class="help" [class.bad]="shown('apiToken')">{{ shown('apiToken') ? problems().apiToken : 'Enter it again only to change it. Format TOKEN_ID=SECRET, stored encrypted.' }}</span>
        </label>
      }
      @switch (test()) {
        @case ('ok') {
          <div class="banner ok" role="status"><app-icon name="check" /><div>{{ docker ? 'Connected over SSH with these settings.' : 'The node accepted these settings.' }}</div></div>
        }
        @case ('failed') {
          <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ testError() || 'No answer from ' + address().trim() + ':' + port() + '. Check the address, port and credentials.' }}</div></div>
        }
        @default {
          @if (connectionChanged()) {
            <div class="banner"><app-icon name="refresh" /><div>The connection settings changed. Test them before saving.</div></div>
          }
        }
      }
      @if (saveError()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ saveError() }}</div></div>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="button" class="btn" [disabled]="!valid() || test() === 'running'" (click)="runTest()">
          @if (test() === 'running') { <app-icon name="spinner" size="sm" [spin]="true" /> }
          Test connection
        </button>
        <button type="submit" class="btn primary" [disabled]="!canSave()" [title]="saveHint()">
          @if (saving()) { <app-icon name="spinner" size="sm" [spin]="true" /> }
          Save changes
        </button>
      </div>
    </form>
  `,
  styles: `.bad { color: var(--bad); }`,
})
export class EditServerDialogComponent {
  protected readonly data = inject<EditServerData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<EditServerDialogComponent>>(MatDialogRef);
  protected readonly docker = this.data.settings.serverType === ServerType.DOCKER_HOST;

  private readonly original = this.data.settings;
  protected readonly name = signal(this.original.name);
  protected readonly address = signal(this.original.ipAddress);
  protected readonly port = signal(String(this.original.port ?? (this.docker ? 22 : 8006)));
  protected readonly username = signal(this.original.username ?? '');
  protected readonly nodeName = signal(this.original.nodeName ?? '');
  protected readonly secret = signal('');
  protected readonly test = signal<TestState>('idle');
  protected readonly testError = signal('');
  protected readonly saveError = signal('');
  protected readonly saving = signal(false);
  private readonly touched = signal<Set<string>>(new Set());

  protected readonly request = computed<ServerEdit>(() => ({
    name: this.name().trim(),
    ipAddress: this.address().trim(),
    port: Number(this.port()),
    ...(this.docker
      ? { username: this.username().trim(), password: this.secret() || undefined }
      : { nodeName: this.nodeName().trim(), apiToken: this.secret().trim() || undefined }),
  }));
  protected readonly problems = computed(() => editProblems(this.request(), this.original.serverType, this.data.otherNames));
  protected readonly valid = computed(() => Object.keys(this.problems()).length === 0);

  /** Anything that changes how Amaterasu reaches the server, which a test has to confirm. */
  protected readonly connectionChanged = computed(() => {
    const edit = this.request();
    return edit.ipAddress !== this.original.ipAddress || edit.port !== this.original.port || !!this.secret()
      || (this.docker ? edit.username !== (this.original.username ?? '') : edit.nodeName !== (this.original.nodeName ?? ''));
  });
  protected readonly changed = computed(() => this.connectionChanged() || this.request().name !== this.original.name);
  protected readonly canSave = computed(() => this.valid() && this.changed() && !this.saving()
    && (!this.connectionChanged() || this.test() === 'ok'));
  protected readonly saveHint = computed(() => {
    if (!this.changed()) return 'Nothing changed yet';
    if (!this.valid()) return 'Fix the marked fields first';
    if (this.connectionChanged() && this.test() !== 'ok') return 'Test the connection first';
    return '';
  });

  protected shown(field: keyof ServerEdit): boolean {
    return !!this.problems()[field] && this.touched().has(field);
  }

  protected touch(field: keyof ServerEdit): void {
    this.touched.update(set => new Set([...set, field]));
  }

  /** Any change means the last test no longer applies. */
  protected edit(field: { set(value: string): void }, event: Event): void {
    field.set((event.target as HTMLInputElement).value);
    this.test.set('idle');
    this.saveError.set('');
  }

  protected async runTest(): Promise<void> {
    this.test.set('running');
    this.testError.set('');
    try {
      this.test.set((await this.data.validate(this.request())) ? 'ok' : 'failed');
    } catch (error) {
      this.testError.set(errorMessage(error) ?? '');
      this.test.set('failed');
    }
  }

  protected async save(): Promise<void> {
    this.touched.set(new Set(['name', 'ipAddress', 'port', 'username', 'nodeName', 'apiToken']));
    if (!this.canSave()) {
      return;
    }
    this.saving.set(true);
    this.saveError.set('');
    try {
      await this.data.save(this.request());
      this.ref.close(true);
    } catch (error) {
      this.saveError.set(errorMessage(error) ?? 'The server could not be saved.');
    } finally {
      this.saving.set(false);
    }
  }
}
