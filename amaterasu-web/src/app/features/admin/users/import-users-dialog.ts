import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ROLE_LABELS } from '../../../core/auth/roles';
import { errorMessage } from '../../../core/http-error';
import { IconComponent } from '../../../shared/ui/icon.component';
import { ImportResult, TeamRef } from './admin-users.service';
import { ImportRow, TEMPLATE_CSV, credentialsCsv, parseUsersCsv } from './user-import';

export interface ImportUsersData {
  teams: TeamRef[];
  existing: string[];
  run: (rows: ImportRow[]) => Promise<ImportResult>;
}

const MAX_FILE_BYTES = 2 * 1024 * 1024;

/** Import CSV: pick a file, check every row, import the good ones and report what was skipped. */
@Component({
  selector: 'app-import-users-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="iu-h">
      <div class="row between">
        <h2 class="h2" id="iu-h">Import users from CSV</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close(!!result())"><app-icon name="close" /></button>
      </div>

      @if (result(); as done) {
        <div class="banner" [class.ok]="done.created.length" [class.warn]="!done.created.length" role="status">
          <app-icon [name]="done.created.length ? 'check' : 'alert'" />
          <div><strong>{{ done.created.length }} created, {{ skippedCount() }} skipped.</strong>
            @if (done.created.length) { Download the passwords now; they are not shown again. }</div>
        </div>
        @if (skippedRows().length) {
          <div class="table-wrap preview">
            <table class="t">
              <thead><tr><th class="r">Line</th><th>Username</th><th>Why it was skipped</th></tr></thead>
              <tbody>
                @for (row of skippedRows(); track row.line) {
                  <tr><td class="r num dim">{{ row.line }}</td><td class="mono">{{ row.username || 'none' }}</td><td class="small">{{ row.reason }}</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
        <div class="row end">
          @if (done.created.length) {
            <button type="button" class="btn" (click)="downloadPasswords()"><app-icon name="download" size="sm" />Download passwords</button>
          }
          <button type="button" class="btn primary" (click)="ref.close(true)">Done</button>
        </div>
      } @else {
        @if (!rows().length) {
          <p class="dim intro">The first row names the columns. <span class="mono">username</span> is required;
            <span class="mono">role</span>, <span class="mono">team</span> and <span class="mono">password</span> are optional.
            Role defaults to Member, team must match an existing team, and rows without a password get a generated one.</p>
          <div class="row">
            <label class="btn primary"><app-icon name="upload" size="sm" />Choose CSV file
              <input type="file" accept=".csv,text/csv" class="visually-hidden" (change)="pick($event)">
            </label>
            <button type="button" class="btn ghost" (click)="downloadTemplate()"><app-icon name="download" size="sm" />Download a template</button>
          </div>
        } @else {
          <div class="row between">
            <div class="row">
              <span class="small dim mono">{{ fileName() }}</span>
              <span class="chip s-active"><span class="dot"></span>{{ ready().length }} ready</span>
              @if (problemCount()) { <span class="chip s-bad"><span class="dot"></span>{{ problemCount() }} with problems</span> }
            </div>
            <label class="btn sm">Choose another file
              <input type="file" accept=".csv,text/csv" class="visually-hidden" (change)="pick($event)">
            </label>
          </div>
          <div class="table-wrap preview">
            <table class="t">
              <thead><tr><th class="r">Line</th><th>Username</th><th>Role</th><th>Team</th><th>Password</th><th>Check</th></tr></thead>
              <tbody>
                @for (row of rows(); track row.line) {
                  <tr [class.dimrow]="row.problems.length">
                    <td class="r num dim">{{ row.line }}</td>
                    <td class="mono">{{ row.username || 'none' }}</td>
                    <td>{{ labels[row.role] }}</td>
                    <td>{{ row.team?.name ?? 'No team' }}</td>
                    <td class="small dim">{{ row.generatedPassword ? 'Generated' : 'From file' }}</td>
                    <td>
                      @if (row.problems.length) {
                        <span class="small bad">{{ row.problems.join('. ') }}</span>
                      } @else {
                        <span class="chip s-active"><span class="dot"></span>Ready</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (problemCount()) {
            <div class="banner warn"><app-icon name="alert" />
              <div>Rows with problems are skipped. Fix them in the file and choose it again, or import the {{ ready().length }} ready {{ ready().length === 1 ? 'row' : 'rows' }} now.</div></div>
          }
        }
        @if (error()) {
          <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() }}</div></div>
        }
        <div class="row end">
          <button type="button" class="btn ghost" (click)="ref.close(false)">Cancel</button>
          <button type="button" class="btn primary" [disabled]="!ready().length || importing()" (click)="run()">
            @if (importing()) { <app-icon name="spinner" size="sm" [spin]="true" /> }
            Import {{ ready().length }} {{ ready().length === 1 ? 'account' : 'accounts' }}
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .wide { max-width: none; }
    .intro { margin: 0; line-height: 1.55; }
    .preview { max-height: 360px; overflow: auto; border: 1px solid var(--border); border-radius: var(--radius-md); }
    .bad { color: var(--bad); }
    label.btn { cursor: pointer; }
  `,
})
export class ImportUsersDialogComponent {
  protected readonly data = inject<ImportUsersData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ImportUsersDialogComponent>);
  protected readonly labels = ROLE_LABELS;

  protected readonly fileName = signal('');
  protected readonly rows = signal<ImportRow[]>([]);
  protected readonly error = signal('');
  protected readonly importing = signal(false);
  protected readonly result = signal<ImportResult | null>(null);

  protected readonly ready = computed(() => this.rows().filter(row => !row.problems.length));
  protected readonly problemCount = computed(() => this.rows().length - this.ready().length);

  /** Rows the browser held back plus rows the API refused. */
  protected readonly skippedRows = computed(() => {
    const done = this.result();
    if (!done) {
      return [];
    }
    const local = this.rows().filter(row => row.problems.length)
      .map(row => ({ line: row.line, username: row.username, reason: row.problems.join('. ') }));
    const remote = done.skipped.map(row => ({ line: row.row, username: row.username, reason: row.reason }));
    return [...local, ...remote].sort((a, b) => a.line - b.line);
  });
  protected readonly skippedCount = computed(() => this.skippedRows().length);

  protected async pick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.error.set('');
    if (file.size > MAX_FILE_BYTES) {
      this.rows.set([]);
      this.error.set('That file is over 2 MB. Split it into smaller files.');
      return;
    }
    const parsed = parseUsersCsv(await file.text(), this.data.teams, this.data.existing);
    this.fileName.set(file.name);
    this.rows.set(parsed.rows);
    if (parsed.error) {
      this.error.set(parsed.error);
    }
  }

  protected async run(): Promise<void> {
    if (!this.ready().length || this.importing()) {
      return;
    }
    this.importing.set(true);
    this.error.set('');
    try {
      this.result.set(await this.data.run(this.ready()));
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The accounts could not be imported.');
    } finally {
      this.importing.set(false);
    }
  }

  protected downloadPasswords(): void {
    const done = this.result();
    if (!done) {
      return;
    }
    const passwords = new Map(this.rows().map(row => [row.line, row.password]));
    save('imported-accounts.csv', credentialsCsv(done.created.map(created => ({
      username: created.username,
      password: passwords.get(created.row) ?? '',
      role: ROLE_LABELS[created.role] ?? created.role,
      team: created.team,
    }))));
  }

  protected downloadTemplate(): void {
    save('users-template.csv', TEMPLATE_CSV);
  }
}

function save(name: string, text: string): void {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
