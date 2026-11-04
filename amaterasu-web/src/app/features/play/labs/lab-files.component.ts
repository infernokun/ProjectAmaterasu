import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { PlayApiService } from '../data/play-api.service';
import { LabFiles } from '../data/play.models';

interface Mount {
  key: string;
  service: string;
  index: number;
  source: string;
  target: string;
  readOnly: boolean;
  directory: boolean;
}

/** The team copy's compose file, and its bind mounts, where files can be replaced. */
@Component({
  selector: 'app-lab-files',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    @if (unavailable()) {
      <div class="card"><app-empty-state icon="file" title="No files right now" [message]="unavailable()" /></div>
    } @else if (error()) {
      <div class="banner bad" role="alert"><app-icon name="alert" /><div class="grow row between"><span>{{ error() }}</span><button type="button" class="btn sm" (click)="load()">Try again</button></div></div>
    } @else if (!files()) {
      <div class="card"><app-skeleton [count]="6" label="Loading lab files" /></div>
    } @else {
      <div class="cols files">
        <div class="card flush mounts">
          <div class="card-head"><h3 class="h3">Mounted files</h3></div>
          <div class="list">
            @for (mount of mounts(); track mount.key) {
              <div class="list-item mount">
                <app-icon name="file" size="sm" />
                <div class="grow">
                  <div class="mono small">{{ mount.target }}</div>
                  <div class="small muted">{{ mount.service }}{{ mount.readOnly ? ' · read only in the container' : '' }}</div>
                </div>
                <label class="btn sm" [class.disabled]="busy() === mount.key">
                  @if (busy() === mount.key) {
                    <app-icon name="spinner" size="sm" [spin]="true" />Saving
                  } @else {
                    <app-icon name="upload" size="sm" />Replace
                  }
                  <input type="file" class="visually-hidden" [disabled]="!!busy()" (change)="upload(mount, $event)"
                    [attr.aria-label]="'Replace ' + mount.target + ' for ' + mount.service">
                </label>
              </div>
            } @empty {
              <app-empty-state title="No bind mounts" message="This lab's services do not mount files from the host." />
            }
          </div>
        </div>
        <div class="card flush compose">
          <div class="card-head"><div><h3 class="h3 mono">docker-compose.yml</h3>
            <div class="small muted">Saved to this team's copy. Other teams are not affected.</div></div></div>
          <div class="body"><pre class="code">@for (line of lines(); track $index) {<span class="ln">{{ $index + 1 }}</span>{{ line }}
}</pre></div>
        </div>
      </div>
    }
  `,
  styles: `
    .files { gap: 16px; }
    .mounts { flex: 1 1 260px; }
    .compose { flex: 999 1 420px; min-width: 0; }
    .mount { gap: 10px; }
    .body { padding: 14px; }
    label.btn { position: relative; }
    label.btn.disabled { opacity: .6; pointer-events: none; }
    label.btn:focus-within { outline: 2px solid var(--focus); outline-offset: 2px; }
  `,
})
export class LabFilesComponent {
  private readonly api = inject(PlayApiService);
  private readonly toast = inject(ToastService);

  readonly tracker = input.required<LabTracker>();

  protected readonly files = signal<LabFiles | null>(null);
  protected readonly error = signal('');
  protected readonly busy = signal('');

  protected readonly unavailable = computed(() => {
    const tracker = this.tracker();
    if (!tracker.remoteServer?.id) return 'This lab has not been deployed to a server yet.';
    if (tracker.labStatus === LabStatus.STOPPED || tracker.labStatus === LabStatus.NONE) {
      return 'This copy was destroyed. Start a fresh copy to get its files back.';
    }
    return '';
  });

  protected readonly lines = computed(() => (this.files()?.yml ?? '').replace(/\n$/, '').split('\n'));

  protected readonly mounts = computed<Mount[]>(() => {
    const services = this.files()?.json?.services ?? {};
    const mounts: Mount[] = [];
    for (const [service, definition] of Object.entries(services)) {
      (definition?.volumes ?? []).forEach((volume, index) => {
        const parts = typeof volume === 'string' ? volume.split(':') : [volume.source ?? '', volume.target ?? ''];
        const [source, target, mode] = parts;
        if (!source || !target || !(source.startsWith('/') || source.startsWith('.'))) return;
        const last = source.split('/').pop() ?? '';
        mounts.push({ key: `${service}-${index}`, service, index, source, target, readOnly: mode === 'ro', directory: !last.includes('.') });
      });
    }
    return mounts;
  });

  constructor() {
    effect(() => {
      this.tracker().id;
      const blocked = this.unavailable();
      untracked(() => {
        if (!blocked && !this.files()) this.load();
      });
    });
  }

  async load(): Promise<void> {
    const tracker = this.tracker();
    this.error.set('');
    try {
      this.files.set(await firstValueFrom(this.api.labFiles(tracker.id!, tracker.remoteServer!.id!)));
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Lab files could not be loaded.');
    }
  }

  protected async upload(mount: Mount, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const tracker = this.tracker();
    this.busy.set(mount.key);
    try {
      await firstValueFrom(this.api.uploadLabFile(tracker.id!, tracker.remoteServer!.id!,
        { serviceName: mount.service, index: mount.index, targetPath: mount.source, directory: mount.directory }, file));
      this.toast.success(`Saved ${file.name} and restarted ${mount.service}`);
    } catch (error) {
      this.toast.error(`${file.name} could not be saved`, error);
    } finally {
      this.busy.set('');
    }
  }
}
