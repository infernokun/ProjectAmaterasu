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
import { AddServerData, AddServerDialogComponent } from './add-server-dialog';
import { address, serverHealth, serverTypeLabel, uptime } from './server-health';
import { ServerCard, ServersStore } from './servers.store';

const REFRESH_MS = 60_000;

@Component({
  selector: 'app-admin-servers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent],
  providers: [ServersStore],
  templateUrl: './admin-servers.page.html',
  styles: `
    .srv { display: flex; flex-direction: column; gap: 12px; text-align: left; font: inherit; color: inherit; cursor: pointer; width: 100%; }
    .srv:hover { border-color: var(--border-2); }
    .srv .meter-row, .srv .full { width: 100%; }
    .detail-banner { padding: 16px 18px 0; }
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
    return count ? `Stop its ${count} team ${count === 1 ? 'lab' : 'labs'} first` : `Delete ${card.server.name}`;
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
