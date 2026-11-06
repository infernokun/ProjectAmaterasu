import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PlayApiService } from '../data/play-api.service';

interface LogLine {
  source: string;
  text: string;
  level: 'e' | 'w' | '';
}

const FOLLOW_MS = 5000;

/** Compose logs for one service or all of them, with a filter, follow and download. */
@Component({
  selector: 'app-lab-logs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="card flush">
      <div class="card-head">
        <div class="row" role="group" aria-label="Service">
          @for (name of choices(); track name) {
            <button type="button" class="pick" [class.on]="service() === name" (click)="service.set(name)">{{ name === 'all' ? 'All' : name }}</button>
          }
        </div>
        <div class="row">
          <label class="search filter"><app-icon name="search" size="sm" />
            <input type="search" placeholder="Filter lines" aria-label="Filter log lines" [value]="filter()" (input)="filter.set($any($event.target).value)"></label>
          <button type="button" class="toggle" [class.on]="follow()" (click)="follow.set(!follow())" aria-label="Follow new lines" [attr.aria-pressed]="follow()"><span></span></button>
          <span class="small dim">Follow</span>
          <button type="button" class="btn sm" (click)="download()" [disabled]="!raw()"><app-icon name="download" size="sm" />Download</button>
        </div>
      </div>
      <div class="body">
        @if (error()) {
          <div class="banner bad" role="alert"><app-icon name="alert" /><div class="grow row between"><span>{{ error() }}</span><button type="button" class="btn sm" (click)="load()">Try again</button></div></div>
        } @else {
          <pre class="code logs" aria-live="off">@for (line of shown(); track $index) {@if (line.source) {<span class="c">{{ line.source }} </span>}<span [class]="line.level">{{ line.text }}</span>
}</pre>
          <div class="small muted note">{{ note() }}</div>
        }
      </div>
    </div>
  `,
  styles: `
    .filter { margin: 0; flex: 0 1 180px; height: 34px; }
    .body { padding: 14px; }
    .logs { min-height: 320px; max-height: 560px; }
    .note { margin-top: 8px; }
  `,
})
export class LabLogsComponent {
  private readonly api = inject(PlayApiService);

  readonly tracker = input.required<LabTracker>();
  readonly services = input<string[]>([]);
  readonly service = model('all');

  protected readonly raw = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly filter = signal('');
  protected readonly follow = signal(false);

  protected readonly choices = computed(() => ['all', ...new Set(this.services())]);

  private readonly lines = computed<LogLine[]>(() => this.raw().split('\n').filter(line => line.length).map(line => {
    const split = line.indexOf(' | ');
    const source = split > 0 && split < 60 ? line.slice(0, split).trim() : '';
    const text = source ? line.slice(split + 3) : line;
    const level = /\b(error|fatal|exception|panic)\b/i.test(text) ? 'e' : /\bwarn(ing)?\b/i.test(text) ? 'w' : '';
    return { source, text, level };
  }));

  protected readonly shown = computed(() => {
    const filter = this.filter().trim().toLowerCase();
    return filter ? this.lines().filter(line => (line.source + ' ' + line.text).toLowerCase().includes(filter)) : this.lines();
  });

  protected readonly note = computed(() => {
    if (this.loading() && !this.raw()) return 'Loading logs';
    const total = this.lines().length;
    if (!total) return 'No log lines yet.';
    const shown = this.shown().length;
    return `${shown === total ? total : `${shown} of ${total}`} lines, the last 1,000 per service${this.follow() ? ' · following' : ''}`;
  });

  constructor() {
    effect(() => {
      this.service();
      this.tracker().id;
      untracked(() => {
        this.raw.set('');
        this.load();
      });
    });
    const timer = setInterval(() => {
      if (this.follow() && !this.loading()) this.load();
    }, FOLLOW_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  async load(): Promise<void> {
    const tracker = this.tracker();
    if (!tracker.id || !tracker.remoteServer?.id) {
      this.error.set('This lab is not on a server, so it has no logs.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    try {
      this.raw.set(await firstValueFrom(this.api.logs(tracker.id, tracker.remoteServer.id, this.service())) ?? '');
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Logs could not be loaded.');
    } finally {
      this.loading.set(false);
    }
  }

  protected download(): void {
    const name = `${this.tracker().labStarted?.name ?? 'lab'}-${this.service()}.log`.replace(/[^a-zA-Z0-9._-]+/g, '-');
    const url = URL.createObjectURL(new Blob([this.raw()], { type: 'text/plain' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  }
}
