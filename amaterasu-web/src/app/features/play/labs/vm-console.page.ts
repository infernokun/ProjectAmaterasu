import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CrumbsService } from '../../../core/crumbs.service';
import { errorMessage } from '../../../core/http-error';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { StatusTone } from '../../../shared/lab-status';
import { IconComponent } from '../../../shared/ui/icon.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { PlayApiService } from '../data/play-api.service';
import { ConsoleService } from './console.service';
import { ConsoleState, disconnectReason, unavailableReason } from './console-state';
import { LabAccessComponent } from './lab-access.component';
import { isVm, labTypeLabel } from './lab-view';
import { RFB_LOADER, RfbClient } from './novnc';

const STATE_LOOK: Record<ConsoleState, { tone: StatusTone; label: string }> = {
  connecting: { tone: 'work', label: 'Connecting' },
  connected: { tone: 'active', label: 'Connected' },
  disconnected: { tone: 'off', label: 'Disconnected' },
  unavailable: { tone: 'bad', label: 'Not available' },
};

/** A browser console on one VM of a team's Proxmox lab, relayed through the API. */
@Component({
  selector: 'app-vm-console-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, StatusBadgeComponent, LabAccessComponent],
  templateUrl: './vm-console.page.html',
  styleUrl: './vm-console.page.scss',
  host: { class: 'content' },
})
export class VmConsolePage {
  private readonly trackers = inject(LabTrackersStore);
  private readonly api = inject(PlayApiService);
  private readonly consoles = inject(ConsoleService);
  private readonly loadRfb = inject(RFB_LOADER);
  private readonly crumbs = inject(CrumbsService);

  readonly trackerId = input.required<string>();
  readonly vmid = input.required<string>();

  private readonly fetched = signal<LabTracker | null>(null);
  protected readonly loadError = signal('');
  protected readonly tracker = computed(() => this.trackers.byId().get(this.trackerId()) ?? this.fetched());
  protected readonly vms = computed(() => this.tracker()?.vms ?? []);
  protected readonly vm = computed(() => this.vms().find(machine => String(machine.vmid) === String(this.vmid())));
  protected readonly vmName = computed(() => this.vm()?.name ?? `VM ${this.vmid()}`);
  protected readonly typeLabel = computed(() => (this.tracker() ? labTypeLabel(this.tracker()!.labStarted) : 'Proxmox VM'));
  protected readonly server = computed(() => this.tracker()?.remoteServer?.name ?? 'the node');

  protected readonly state = signal<ConsoleState>('connecting');
  protected readonly reason = signal('');
  protected readonly desktopName = signal('');
  protected readonly look = computed(() => STATE_LOOK[this.state()]);

  private readonly screen = viewChild<ElementRef<HTMLDivElement>>('screen');
  private readonly frame = viewChild<ElementRef<HTMLDivElement>>('frame');
  private rfb: RfbClient | null = null;
  private attempt = 0;

  constructor() {
    effect(() => {
      const trackerId = this.trackerId();
      const vmid = this.vmid();
      untracked(() => this.connect(trackerId, vmid));
    });
    effect(() => {
      const tracker = this.tracker();
      const name = this.vmName();
      const lab = tracker?.labStarted?.name;
      if (tracker && lab) {
        untracked(() => this.crumbs.set([
          { label: 'Labs', link: '/labs' },
          { label: lab, link: ['/labs', tracker.id!] },
          { label: `${name} console` },
        ]));
      }
    });
    inject(DestroyRef).onDestroy(() => {
      this.attempt++;
      this.close();
    });
  }

  protected reconnect(): void {
    this.connect(this.trackerId(), this.vmid());
  }

  protected disconnect(): void {
    this.attempt++;
    this.close();
    this.state.set('disconnected');
    this.reason.set('You closed the console.');
  }

  protected ctrlAltDel(): void {
    this.rfb?.sendCtrlAltDel();
    this.rfb?.focus({ preventScroll: true });
  }

  protected fullScreen(): void {
    this.frame()?.nativeElement.requestFullscreen?.().catch(() => undefined);
    this.rfb?.focus({ preventScroll: true });
  }

  private async connect(trackerId: string, vmid: string): Promise<void> {
    const attempt = ++this.attempt;
    const current = () => attempt === this.attempt;
    this.close();
    this.state.set('connecting');
    this.reason.set('');
    this.desktopName.set('');

    const tracker = await this.ensureTracker(trackerId);
    if (!current()) return;
    if (!tracker) {
      return this.unavailable(this.loadError() || 'This lab could not be loaded.');
    }
    if (!isVm(tracker.labStarted)) {
      return this.unavailable('Consoles are only for Proxmox VM labs. This lab runs containers.');
    }
    if (!this.vm()) {
      return this.unavailable(`VM ${vmid} is not part of this lab.`);
    }
    if (this.vm()!.status && this.vm()!.status !== 'running') {
      return this.unavailable(`${this.vmName()} is ${this.vm()!.status}. Start or resume the lab first.`);
    }

    let Rfb;
    try {
      Rfb = await this.loadRfb();
    } catch {
      return current() ? this.unavailable('The console viewer could not be loaded. Reload the page and try again.') : undefined;
    }
    if (!current()) return;

    let session;
    try {
      session = await firstValueFrom(this.consoles.open(trackerId, vmid));
    } catch (error) {
      return current() ? this.unavailable(unavailableReason(error)) : undefined;
    }
    const target = this.screen()?.nativeElement;
    if (!current() || !target) return;

    let closed: { code: number; reason: string } | null = null;
    let security: string | null = null;
    const socket = this.consoles.connect(session.path);
    socket.addEventListener('close', event => (closed = { code: event.code, reason: event.reason }));

    const rfb = new Rfb(target, socket, { credentials: { password: session.password }, wsProtocols: ['binary'] });
    rfb.scaleViewport = true;
    rfb.resizeSession = false;
    rfb.focusOnClick = true;
    rfb.background = 'transparent';
    rfb.addEventListener('connect', () => {
      if (!current()) return;
      this.state.set('connected');
      rfb.focus({ preventScroll: true });
    });
    rfb.addEventListener('desktopname', event => this.desktopName.set((event as CustomEvent<{ name: string }>).detail?.name ?? ''));
    rfb.addEventListener('securityfailure', event => {
      const detail = (event as CustomEvent<{ status: number; reason?: string }>).detail;
      security = detail?.reason || 'authentication failed';
    });
    rfb.addEventListener('disconnect', event => {
      if (this.rfb === rfb) this.rfb = null;
      if (!current()) return;
      const clean = !!(event as CustomEvent<{ clean: boolean }>).detail?.clean;
      // The socket's close event can land just after noVNC's
      setTimeout(() => {
        if (!current()) return;
        this.state.set('disconnected');
        this.reason.set(disconnectReason(closed, clean, security));
      });
    });
    this.rfb = rfb;
  }

  private unavailable(reason: string): void {
    this.state.set('unavailable');
    this.reason.set(reason);
  }

  private close(): void {
    const rfb = this.rfb;
    this.rfb = null;
    try {
      rfb?.disconnect();
    } catch {
      // already closed
    }
  }

  private async ensureTracker(id: string): Promise<LabTracker | null> {
    const known = this.trackers.byId().get(id) ?? (this.fetched()?.id === id ? this.fetched() : null);
    if (known) return known;
    try {
      const tracker = await firstValueFrom(this.api.tracker(id));
      this.fetched.set(tracker);
      return tracker;
    } catch (error) {
      this.loadError.set(errorMessage(error) ?? 'This lab could not be loaded.');
      return null;
    }
  }
}
