import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { errorMessage } from '../../../core/http-error';
import { isDeployed } from '../../../core/lab-deployment';
import { IconComponent } from '../../../shared/ui/icon.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { address, serverHealth } from './server-health';
import { Deployment, Server } from './servers.service';

export interface MoveLabData {
  deployment: Deployment;
  from: Server;
  /** Servers of the kind this lab runs on, other than the one it is on. */
  targets: Server[];
  move: (serverId: string, startFresh: boolean) => Promise<unknown>;
}

/** Move a team's lab to another server; a deployed one is destroyed and started fresh there. */
@Component({
  selector: 'app-move-lab-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, StatusBadgeComponent],
  template: `
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="mv-h">
      <div class="row between">
        <h2 class="h2" id="mv-h">Move {{ data.deployment.teamName }}'s lab</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      <div class="row small">
        <strong>{{ data.deployment.labName }}</strong>
        <app-status-badge [state]="data.deployment.status" />
        <span class="muted">on <span class="mono">{{ data.from.name }}</span></span>
      </div>

      @if (!data.targets.length) {
        <div class="banner"><app-icon name="server" /><div>There is no other {{ kind() }} to move it to. Add one under Servers first.</div></div>
      } @else {
        <div class="field">
          <span class="label">Move to</span>
          <div class="list targets" role="radiogroup" aria-label="Server to move to">
            @for (server of data.targets; track server.id) {
              <label class="list-item target" [class.on]="target() === server.id">
                <input type="radio" name="target" [value]="server.id" [checked]="target() === server.id" (change)="pick(server.id)">
                <span class="grow"><span class="mono name">{{ server.name }}</span>
                  <span class="small muted"> · {{ addressOf(server) }}</span></span>
                @if (server.noNewLabs) { <span class="tag">No new labs</span> }
                <app-status-badge [tone]="health(server).tone" [label]="health(server).label" />
              </label>
            }
          </div>
        </div>
        @if (target(); as id) {
          @if (deployed) {
            <div class="banner warn" role="note"><app-icon name="alert" />
              <div><strong>Its current copy is destroyed.</strong> The {{ units() }} on {{ data.from.name }} are removed, with anything
                {{ data.deployment.teamName }} changed or saved in them. A fresh copy is then started on {{ nameOf(id) }}.</div>
            </div>
            @if (takesNoNewLabs(id)) {
              <div class="banner bad"><app-icon name="alert" /><div>{{ nameOf(id) }} takes no new labs. Pick another server, or allow new labs on it first.</div></div>
            } @else if (!online(id)) {
              <div class="banner bad"><app-icon name="alert" /><div>{{ nameOf(id) }} is not online, so the fresh copy cannot start there. Pick an online server.</div></div>
            }
          } @else if (takesNoNewLabs(id)) {
            <div class="banner bad"><app-icon name="alert" /><div>{{ nameOf(id) }} takes no new labs. Pick another server, or allow new labs on it first.</div></div>
          } @else {
            <div class="banner"><app-icon name="check" />
              <div>It is not deployed, so nothing is destroyed. The next time {{ data.deployment.teamName }} starts it, it starts on {{ nameOf(id) }}.</div>
            </div>
          }
        }
      }
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() }}</div></div>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        @if (deployed) {
          <button type="button" class="btn danger solid" [disabled]="!canMove()" (click)="move()">
            @if (moving()) { <app-icon name="spinner" size="sm" [spin]="true" /> }
            Destroy and start fresh
          </button>
        } @else {
          <button type="button" class="btn primary" [disabled]="!canMove()" (click)="move()">
            @if (moving()) { <app-icon name="spinner" size="sm" [spin]="true" /> }
            Move lab
          </button>
        }
      </div>
    </div>
  `,
  styles: `
    .targets { border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; }
    .target { cursor: pointer; padding: 10px 14px; }
    .target:last-child { border-bottom: 0; }
    .target.on { background: var(--accent-soft); }
    .target input { accent-color: var(--accent); width: 16px; height: 16px; }
    .name { font-weight: 600; }
  `,
})
export class MoveLabDialogComponent {
  protected readonly data = inject<MoveLabData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<MoveLabDialogComponent>>(MatDialogRef);
  protected readonly deployed = isDeployed(this.data.deployment.status);
  protected readonly health = serverHealth;
  protected readonly addressOf = address;

  protected readonly target = signal<string | null>(
    this.data.targets.find(server => serverHealth(server).online && !server.noNewLabs)?.id ?? this.data.targets[0]?.id ?? null);
  protected readonly moving = signal(false);
  protected readonly error = signal('');

  protected readonly kind = computed(() => (this.data.from.serverType === 'PROXMOX' ? 'Proxmox node' : 'Docker host'));
  protected readonly units = computed(() => (this.data.from.serverType === 'PROXMOX' ? 'cloned VMs and their bridge' : 'containers and volumes'));
  protected readonly canMove = computed(() => {
    const id = this.target();
    return !!id && !this.moving() && !this.takesNoNewLabs(id) && (!this.deployed || this.online(id));
  });

  protected nameOf(serverId: string): string {
    return this.data.targets.find(server => server.id === serverId)?.name ?? 'the new server';
  }

  protected online(serverId: string): boolean {
    const server = this.data.targets.find(candidate => candidate.id === serverId);
    return !!server && serverHealth(server).online;
  }

  protected takesNoNewLabs(serverId: string): boolean {
    return !!this.data.targets.find(candidate => candidate.id === serverId)?.noNewLabs;
  }

  protected pick(serverId: string): void {
    this.target.set(serverId);
    this.error.set('');
  }

  protected async move(): Promise<void> {
    const id = this.target();
    if (!id || !this.canMove()) {
      return;
    }
    this.moving.set(true);
    this.error.set('');
    try {
      await this.data.move(id, this.deployed);
      this.ref.close(true);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The lab could not be moved.');
    } finally {
      this.moving.set(false);
    }
  }
}
