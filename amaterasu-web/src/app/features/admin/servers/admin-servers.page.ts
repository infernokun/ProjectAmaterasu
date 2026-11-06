import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ServerType } from '../../../enums/server-type.enum';
import { relativeTime, formatDateTime } from '../../../core/time';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { DIALOG_DEFAULTS } from '../../../shared/ui/dialog';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { RouterLink } from '@angular/router';
import { isDeployed } from '../../../core/lab-deployment';
import { AddServerData, AddServerDialogComponent } from './add-server-dialog';
import { EditServerData, EditServerDialogComponent } from './edit-server-dialog';
import { MoveLabData, MoveLabDialogComponent } from './move-lab-dialog';
import { Deployment, ServerSettings } from './servers.service';
import { address, serverHealth, serverTypeLabel, uptime } from './server-health';
import { ServerCard, ServersStore } from './servers.store';

const REFRESH_MS = 60_000;

@Component({
  selector: 'app-admin-servers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent],
  providers: [ServersStore],
  templateUrl: './admin-servers.page.html',
  styles: `
    .srv { display: flex; flex-direction: column; gap: 12px; text-align: left; font: inherit; color: inherit; cursor: pointer; width: 100%; }
    .srv:hover { border-color: var(--border-2); }
    .srv .meter-row, .srv .full { width: 100%; }
    .detail-banner { padding: 16px 18px 0; }
    .nowrap { flex-wrap: nowrap; }
  `,
  host: { class: 'content' },
})
export class AdminServersPage {
  protected readonly store = inject(ServersStore);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  protected readonly typeLabel = serverTypeLabel;
  protected readonly address = address;
  protected readonly uptime = uptime;
  protected readonly since = relativeTime;
  protected readonly at = formatDateTime;

  /** Ticks every few seconds so "last update" stays current. */
  private readonly now = signal(Date.now());

  protected readonly summary = computed(() => {
    const servers = this.store.servers();
    const docker = servers.filter(s => s.serverType === ServerType.DOCKER_HOST).length;
    const proxmox = servers.filter(s => s.serverType === ServerType.PROXMOX).length;
    return `${docker} Docker ${docker === 1 ? 'host' : 'hosts'} and ${proxmox} Proxmox ${proxmox === 1 ? 'node' : 'nodes'} · stats refresh every minute`;
  });

  protected readonly lastUpdate = computed(() => {
    const at = this.store.statsUpdatedAt();
    if (!at) {
      return 'no stats yet';
    }
    const seconds = Math.max(0, Math.round((this.now() - at) / 1000));
    return seconds < 60 ? `last update ${seconds}s ago` : `last update ${relativeTime(at, new Date(this.now())).toLowerCase()}`;
  });

  /** ?server= from the top bar search. */
  readonly serverParam = input<string>(undefined, { alias: 'server' });

  constructor() {
    this.store.load();
    effect(() => {
      const serverId = this.serverParam();
      if (serverId) untracked(() => this.store.select(serverId));
    });
    const refresh = setInterval(() => this.store.load(), REFRESH_MS);
    const tick = setInterval(() => this.now.set(Date.now()), 5_000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(refresh);
      clearInterval(tick);
    });
  }

  protected unitColumn(card: ServerCard): string {
    return card.server.serverType === ServerType.PROXMOX ? 'VMs and bridge' : 'Containers';
  }

  protected deleteNote(card: ServerCard): string {
    const count = card.deployments.length;
    return count ? `Move or stop its ${count} team ${count === 1 ? 'lab' : 'labs'} first` : `Delete ${card.server.name}`;
  }

  protected osLine(card: ServerCard): string {
    const stats = card.server.stats;
    const os = [stats?.osName, stats?.osVersion].filter(Boolean).join(' ');
    const health = serverHealth(card.server);
    if (!health.online) {
      return os || serverTypeLabel(card.server.serverType);
    }
    return `${os || serverTypeLabel(card.server.serverType)} · up ${uptime(stats?.uptime ?? 0)}`;
  }

  protected addServer(): void {
    const data: AddServerData = {
      validate: server => this.store.validate(server),
      add: async server => {
        const added = await this.store.add(server);
        this.toast.success(`${added.name} added. Its stats arrive within a minute.`);
      },
    };
    this.dialog.open(AddServerDialogComponent, { ...DIALOG_DEFAULTS, width: '520px', data });
  }

  protected deployed(deployment: Deployment): boolean {
    return isDeployed(deployment.status);
  }

  protected async edit(card: ServerCard): Promise<void> {
    let settings: ServerSettings;
    try {
      settings = await this.store.settings(card.server.id);
    } catch (error) {
      this.toast.error(`${card.server.name} could not be opened for editing.`, error);
      return;
    }
    const data: EditServerData = {
      settings,
      otherNames: this.store.servers().filter(server => server.id !== card.server.id).map(server => server.name),
      validate: edit => this.store.validateEdit(card.server.id, edit),
      save: async edit => {
        const saved = await this.store.update(card.server.id, edit);
        this.toast.success(`${saved.name} saved`);
      },
    };
    this.dialog.open(EditServerDialogComponent, { ...DIALOG_DEFAULTS, width: '520px', data });
  }

  protected move(card: ServerCard, deployment: Deployment): void {
    const data: MoveLabData = {
      deployment,
      from: card.server,
      targets: this.store.servers()
        .filter(server => server.id !== card.server.id && server.serverType === card.server.serverType)
        .sort((a, b) => a.name.localeCompare(b.name)),
      move: async (serverId, startFresh) => {
        await this.store.moveLab(deployment.id, serverId, startFresh);
        const target = this.store.servers().find(server => server.id === serverId)?.name ?? 'the new server';
        this.toast.success(startFresh
          ? `${deployment.teamName}'s ${deployment.labName} started fresh on ${target}`
          : `${deployment.teamName}'s ${deployment.labName} will start on ${target}`);
      },
    };
    this.dialog.open(MoveLabDialogComponent, { ...DIALOG_DEFAULTS, width: '520px', data });
  }

  protected readonly togglingNewLabs = signal(false);

  /** Stop new labs here, or allow them again. Labs already on the server are not touched. */
  protected async toggleNewLabs(card: ServerCard): Promise<void> {
    const allowed = card.server.noNewLabs;
    this.togglingNewLabs.set(true);
    try {
      const saved = await this.store.setNewLabs(card.server.id, allowed);
      this.toast.success(allowed ? `New labs can start on ${saved.name} again` : `No new labs start on ${saved.name}. Labs already there keep running.`);
    } catch (error) {
      this.toast.error(`${card.server.name} could not be changed.`, error);
    } finally {
      this.togglingNewLabs.set(false);
    }
  }

  protected async check(card: ServerCard): Promise<void> {
    try {
      const checked = await this.store.check(card.server.id);
      const health = serverHealth(checked);
      if (health.online) {
        this.toast.success(`${checked.name} answered`);
      } else {
        this.toast.show(`${checked.name} did not answer`, 'bad');
      }
    } catch (error) {
      this.toast.error(`${card.server.name} could not be checked.`, error);
    }
  }

  protected async remove(card: ServerCard): Promise<void> {
    const { server } = card;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${server.name}?`,
      message: 'Teams can no longer start labs here. Stopped labs that last ran on it start fresh on another server. '
        + 'The stored credentials are deleted.',
      confirmLabel: 'Delete server',
      typeToConfirm: server.name,
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await this.store.remove(server.id);
      this.toast.success(`${server.name} deleted`);
    } catch (error) {
      this.toast.error(`${server.name} could not be deleted.`, error);
      this.store.load();
    }
  }
}
