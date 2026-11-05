import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { SessionStore } from '../../../core/stores/session.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { Lab } from '../../../models/lab/lab.model';
import { RemoteServer } from '../../../models/lab/remote-server.model';
import { IconComponent } from '../../../shared/ui/icon.component';
import { LabServersStore } from '../data/lab-servers.store';
import { LabActionsService } from './lab-actions.service';
import { labTypeLabel } from './lab-view';

/** A lab the team can start, on the server with the most headroom unless they pick another. */
@Component({
  selector: 'app-lab-catalog-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, MatMenu, MatMenuItem, MatMenuTrigger],
  template: `
    <article class="card stack" [attr.aria-label]="lab().name">
      <div class="row between"><span class="tag">{{ typeLabel() }}</span>
        @if (lab().version) {
          <span class="small muted">Version <span class="mono">{{ lab().version }}</span></span>
        }
      </div>
      <h3 class="h3 name">{{ lab().name }}</h3>
      @if (lab().description) {
        <p class="small dim desc">{{ lab().description }}</p>
      }
      @if (lab().capacity) {
        <div class="small muted">Up to {{ lab().capacity }} team copies at once</div>
      }
      <div class="grow"></div>
      @if (!lab().ready) {
        <button type="button" class="btn sm block" disabled><app-icon name="clock" size="sm" />Validating, not ready yet</button>
      } @else if (!session.teamId()) {
        <button type="button" class="btn sm block" disabled>Join a team to start labs</button>
      } @else if (!server()) {
        <button type="button" class="btn sm block" disabled>{{ servers.loaded() ? 'No server is online for this lab' : 'Checking servers' }}</button>
      } @else {
        <div class="row start-row">
          <button type="button" class="btn sm primary grow" [disabled]="starting()" (click)="start()">
            @if (starting()) {
              <app-icon name="spinner" size="sm" [spin]="true" />Starting on {{ server()!.name }}
            } @else {
              <app-icon name="play" size="sm" />Start on {{ server()!.name }}
            }
          </button>
          @if (choices().length > 1) {
            <button type="button" class="btn sm" [matMenuTriggerFor]="menu" [disabled]="starting()" aria-label="Choose another server">
              <app-icon name="server" size="sm" />
            </button>
            <mat-menu #menu="matMenu">
              @for (choice of choices(); track choice.id) {
                <button mat-menu-item type="button" [disabled]="!online(choice)" (click)="picked.set(choice)">
                  {{ choice.name }}{{ online(choice) ? '' : ' (offline)' }}
                </button>
              }
            </mat-menu>
          }
        </div>
      }
    </article>
  `,
  styles: `
    :host { display: block; }
    .card { height: 100%; }
    .name { font-size: 15px; }
    .desc { margin: 0; }
    .start-row { flex-wrap: nowrap; }
  `,
})
export class LabCatalogCardComponent {
  private readonly trackers = inject(LabTrackersStore);
  private readonly actions = inject(LabActionsService);
  protected readonly session = inject(SessionStore);
  protected readonly servers = inject(LabServersStore);

  readonly lab = input.required<Lab>();

  protected readonly picked = signal<RemoteServer | undefined>(undefined);
  protected readonly typeLabel = computed(() => labTypeLabel(this.lab()));
  protected readonly choices = computed(() => this.servers.serversFor(this.lab()));
  protected readonly server = computed(() => this.picked() ?? this.servers.defaultFor(this.lab()));
  protected readonly starting = computed(() => !!this.trackers.starting()[this.lab().id!]);

  protected online(server: RemoteServer): boolean {
    return server.remoteServerStats?.status === LabStatus.ACTIVE;
  }

  protected start(): void {
    const server = this.server();
    if (server?.id) {
      this.actions.start(this.lab(), server.id);
    }
  }
}
