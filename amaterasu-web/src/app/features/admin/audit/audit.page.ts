import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { formatDateTime, relativeTime } from '../../../core/time';
import { AuditEvent } from '../../../models/audit-event.model';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { ACTION_GROUPS, TARGET_TYPES, actionMeta, targetTypeLabel } from './audit-actions';
import { AuditStore, TimeRange } from './audit.store';

@Component({
  selector: 'app-audit',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent],
  providers: [AuditStore],
  template: `
    <div class="page-head">
      <div>
        <h1 class="h1">Audit log</h1>
        <p class="sub">Lab, account, team, authoring and server changes, newest first</p>
      </div>
      <button type="button" class="btn" (click)="store.load()" [disabled]="store.loading()">
        <app-icon name="refresh" size="sm" [spin]="store.loading()" />Refresh
      </button>
    </div>

    <div class="stack">
      <div class="row" role="group" aria-label="Action">
        @for (group of groups; track group.key) {
          <button type="button" class="pick" [class.on]="store.group() === group.key" [attr.aria-pressed]="store.group() === group.key"
                  (click)="store.setGroup(group.key)">{{ group.label }}</button>
        }
      </div>
      <div class="row between">
        <div class="row">
          <select class="input sel" aria-label="Target" [value]="store.targetType()" (change)="store.setTargetType(value($event))">
            @for (target of targets; track target.value) {
              <option [value]="target.value">{{ target.label }}</option>
            }
          </select>
          <select class="input sel" aria-label="Team" (change)="store.setTeam(value($event))">
            <option value="" [selected]="!store.teamId()">All teams</option>
            @for (team of store.teams(); track team.id) {
              <option [value]="team.id" [selected]="store.teamId() === team.id">{{ team.name }}</option>
            }
          </select>
        </div>
        <div class="seg" role="group" aria-label="Time">
          @for (range of ranges; track range.value) {
            <button type="button" [class.on]="store.range() === range.value" (click)="store.setRange(range.value)">{{ range.label }}</button>
          }
        </div>
      </div>
    </div>

    @if (store.error()) {
      <div class="banner bad" role="alert">
        <app-icon name="alert" /><div class="grow">{{ store.error() }}</div>
        <button type="button" class="btn sm" (click)="store.load()">Try again</button>
      </div>
    }

    <section class="card flush" aria-label="Events">
      @if (store.loading() && !store.events().length) {
        <app-skeleton variant="rows" [count]="8" label="Loading events" />
      } @else if (!store.events().length) {
        <app-empty-state icon="file" title="No events match these filters"
                         message="Try a longer time range or another action. Events are recorded as people use the app." />
      } @else {
        <div class="table-wrap">
          <table class="t">
            <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Target</th><th>Team</th><th>Detail</th></tr></thead>
            <tbody>
              @for (event of store.events(); track event.id) {
                <tr>
                  <td class="dim small nowrap" [title]="exact(event.at)">{{ ago(event.at) }}</td>
                  <td class="nowrap" style="font-weight:500">{{ event.actorUsername }}</td>
                  <td><app-status-badge [tone]="meta(event).tone" [label]="meta(event).label" /></td>
                  <td>
                    @if (event.targetLabel || event.targetType) {
                      <span class="small muted">{{ typeLabel(event.targetType) }}</span>
                      <div>{{ event.targetLabel || event.targetId }}</div>
                    }
                  </td>
                  <td class="dim">{{ teamName(event) }}</td>
                  <td class="small dim detail">{{ event.detail }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="card-foot">
          <span class="small muted grow">{{ store.rangeLabel() }}</span>
          <button type="button" class="btn sm" [disabled]="store.page() === 0 || store.loading()" (click)="store.goTo(store.page() - 1)">Previous</button>
          <span class="small dim num">Page {{ store.page() + 1 }} of {{ store.pageCount() }}</span>
          <button type="button" class="btn sm" [disabled]="store.page() + 1 >= store.pageCount() || store.loading()"
                  (click)="store.goTo(store.page() + 1)">Next</button>
        </div>
      }
    </section>
  `,
  styles: `
    .sel { width: auto; min-width: 160px; }
    .nowrap { white-space: nowrap; }
    .detail { max-width: 360px; }
  `,
  host: { class: 'content' },
})
export class AuditPage {
  protected readonly store = inject(AuditStore);
  protected readonly groups = ACTION_GROUPS;
  protected readonly targets = TARGET_TYPES;
  protected readonly ranges: { value: TimeRange; label: string }[] = [
    { value: 'hour', label: 'Last hour' },
    { value: 'day', label: '24 hours' },
    { value: 'week', label: '7 days' },
    { value: 'all', label: 'All time' },
  ];
  protected readonly ago = (at: string) => relativeTime(at);
  protected readonly exact = formatDateTime;
  protected readonly typeLabel = targetTypeLabel;

  protected meta(event: AuditEvent) {
    return actionMeta(event.action);
  }

  protected teamName(event: AuditEvent): string {
    return event.teamId ? this.store.teamNames().get(event.teamId) ?? 'Deleted team' : '';
  }

  protected value(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }
}
