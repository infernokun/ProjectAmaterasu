import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LabType } from '../../../enums/lab-type.enum';
import { LabsStore } from '../../../core/stores/labs.store';
import { relativeTime } from '../../../core/time';
import { Lab } from '../../../models/lab/lab.model';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { LabBuilderService } from './lab-builder.service';

@Component({
  selector: 'app-lab-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent],
  template: `
    <div class="page-head">
      <div>
        <h1 class="h1">Labs</h1>
        <p class="sub">Docker Compose and Proxmox VM labs. Teams only see the published ones.</p>
      </div>
      <a class="btn primary" routerLink="new"><app-icon name="plus" />New lab</a>
    </div>

    @if (labs.error()) {
      <div class="banner bad" role="alert"><app-icon name="alert" /><div class="grow">{{ labs.error() }}</div>
        <button type="button" class="btn sm" (click)="labs.load(true)">Try again</button></div>
    }

    <section class="card flush" aria-label="Labs">
      @if (labs.status() === 'idle' || (labs.loading() && !labs.labs().length)) {
        <app-skeleton variant="rows" [count]="4" label="Loading labs" />
      } @else if (!labs.labs().length) {
        <app-empty-state icon="layers" title="No labs yet"
                         message="Build one from a docker-compose.yml or from Proxmox VM templates. Teams start their own copy.">
          <a class="btn primary" routerLink="new"><app-icon name="plus" />New lab</a>
        </app-empty-state>
      } @else {
        <div class="table-wrap">
          <table class="t">
            <thead><tr><th>Lab</th><th>Type</th><th>Version</th><th class="r">Team copies</th><th>Status</th><th>Updated</th><th class="r">Actions</th></tr></thead>
            <tbody>
              @for (lab of labs.labs(); track lab.id) {
                <tr>
                  <td>
                    <a class="name" [routerLink]="lab.id">{{ lab.name }}</a>
                    @if (lab.description) { <div class="small muted clamp">{{ lab.description }}</div> }
                  </td>
                  <td><span class="tag">{{ typeLabel(lab) }}</span></td>
                  <td class="mono small dim">{{ lab.version || 'none' }}</td>
                  <td class="r num">{{ lab.capacity ?? 'Any' }}</td>
                  <td>
                    <div class="row nowrap">
                      @if (lab.published) {
                        <app-status-badge tone="active" label="Published" />
                      } @else {
                        <app-status-badge tone="off" label="Draft" />
                      }
                      @if (!lab.ready) {
                        <span class="small muted nobreak" title="A Docker host has not accepted its compose file yet">Not checked</span>
                      }
                    </div>
                  </td>
                  <td class="dim small">{{ updated(lab) }}</td>
                  <td class="r">
                    <div class="row end nowrap">
                      <a class="btn sm" [routerLink]="lab.id"><app-icon name="edit" size="sm" />Edit</a>
                      <button type="button" class="btn sm danger" (click)="remove(lab)"><app-icon name="trash" size="sm" />Delete</button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
    <p class="small muted">New labs are drafts until you publish them. Compose labs can be published once the file passes docker compose config on a Docker host; the check runs every minute.</p>
  `,
  styles: `
    .name { font-weight: 500; color: var(--text); text-decoration: none; }
    .name:hover { text-decoration: underline; }
    .clamp { max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .nowrap { flex-wrap: nowrap; }
    .nobreak { white-space: nowrap; }
    p { margin: 0; }
  `,
  host: { class: 'content' },
})
export class LabListPage {
  protected readonly labs = inject(LabsStore);
  private readonly api = inject(LabBuilderService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  constructor() {
    this.labs.load(true);
  }

  protected typeLabel(lab: Lab): string {
    return lab.labType === LabType.VIRTUAL_MACHINE ? 'Proxmox VM' : 'Docker Compose';
  }

  protected updated(lab: Lab): string {
    return relativeTime(lab.updatedAt);
  }

  protected async remove(lab: Lab): Promise<void> {
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${lab.name}?`,
      message: 'It leaves the catalog and teams can no longer start it. Teams that stopped it lose it from their list. '
        + 'While any team still has it deployed, it cannot be deleted.',
      confirmLabel: 'Delete lab',
      typeToConfirm: lab.name,
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await firstValueFrom(this.api.delete(lab.id!));
      this.labs.remove(lab.id!);
      this.toast.success(`${lab.name} deleted`);
    } catch (error) {
      this.toast.error(`${lab.name} could not be deleted.`, error);
    }
  }
}
