import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Role } from '../../../enums/role.enum';
import { errorMessage } from '../../../core/http-error';
import { checkPassword, generatePassword, MIN_PASSWORD } from '../../../core/password';
import { ROLE_LABELS } from '../../../core/auth/roles';
import { IconComponent } from '../../../shared/ui/icon.component';
import { NewUser, TeamRef } from './admin-users.service';

/** Roles an admin hands out, lowest first. */
export const ASSIGNABLE_ROLES: Role[] = [Role.MEMBER, Role.FACILITATOR, Role.CREATOR, Role.ADMIN];

export interface AddUserData {
  teams: TeamRef[];
  save: (user: NewUser) => Promise<unknown>;
}

@Component({
  selector: 'app-add-user-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" role="dialog" aria-modal="true" aria-labelledby="au-h" (submit)="$event.preventDefault(); submit()">
      <div class="row between">
        <h2 class="h2" id="au-h">Add user</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="close" /><div>{{ error() }}</div></div>
      }
      <label class="field">
        <span class="label">Username</span>
        <input class="input mono" autocomplete="off" spellcheck="false" [value]="username()" (input)="username.set(value($event))" required>
        <span class="help">3 to 32 letters, numbers, dots, dashes or underscores. Usernames cannot be changed.</span>
      </label>
      <div class="field">
        <span class="label">Temporary password</span>
        <div class="row nowrap">
          <input class="input mono" autocomplete="new-password" [value]="password()" (input)="password.set(value($event))" aria-label="Temporary password">
          <button type="button" class="btn" (click)="password.set(generate())"><app-icon name="refresh" size="sm" />Generate</button>
        </div>
        <span class="help">At least {{ min }} characters with a number or symbol. Share it with them directly; they can change it under Account.</span>
      </div>
      <div class="form-grid">
        <label class="field">
          <span class="label">Role</span>
          <select class="input" [value]="role()" (change)="role.set($any(value($event)))">
            @for (option of roles; track option) {
              <option [value]="option">{{ labels[option] }}</option>
            }
          </select>
        </label>
        <label class="field">
          <span class="label">Team</span>
          <select class="input" [value]="teamId()" (change)="teamId.set(value($event))">
            <option value="">No team</option>
            @for (team of data.teams; track team.id) {
              <option [value]="team.id">{{ team.name }}</option>
            }
          </select>
        </label>
      </div>
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="submit" class="btn primary" [disabled]="!valid() || saving()">
          @if (saving()) { <app-icon name="spinner" size="sm" [spin]="true" /> }
          Add user
        </button>
      </div>
    </form>
  `,
  styles: `.nowrap { flex-wrap: nowrap; }`,
})
export class AddUserDialogComponent {
  protected readonly data = inject<AddUserData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<AddUserDialogComponent>);
  protected readonly roles = ASSIGNABLE_ROLES;
  protected readonly labels = ROLE_LABELS;
  protected readonly min = MIN_PASSWORD;
  protected readonly generate = generatePassword;

  protected readonly username = signal('');
  protected readonly password = signal(generatePassword());
  protected readonly role = signal<Role>(Role.MEMBER);
  protected readonly teamId = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  protected readonly valid = computed(() =>
    /^[A-Za-z0-9_.-]{3,32}$/.test(this.username().trim()) && checkPassword(this.password()).ok);

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected async submit(): Promise<void> {
    if (!this.valid() || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      await this.data.save({
        username: this.username().trim(), password: this.password(), role: this.role(), teamId: this.teamId() || null,
      });
      this.ref.close(true);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The user could not be added.');
    } finally {
      this.saving.set(false);
    }
  }
}

export interface ResetPasswordData {
  username: string;
  save: (password: string) => Promise<unknown>;
}

@Component({
  selector: 'app-reset-password-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" role="dialog" aria-modal="true" aria-labelledby="rp-h" (submit)="$event.preventDefault(); submit()">
      <div class="row between">
        <h2 class="h2" id="rp-h">Reset password for {{ data.username }}</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      <div class="seg" role="group" aria-label="Method">
        <button type="button" [class.on]="generated()" (click)="generated.set(true)">Generate one</button>
        <button type="button" [class.on]="!generated()" (click)="generated.set(false)">Type one</button>
      </div>
      @if (generated()) {
        <div class="field">
          <span class="label">Temporary password</span>
          <div class="row nowrap">
            <input class="input mono" [value]="temporary" readonly aria-label="Temporary password">
            <button type="button" class="btn" aria-label="Copy password" (click)="copy()">
              <app-icon [name]="copied() ? 'check' : 'copy'" size="sm" />{{ copied() ? 'Copied' : 'Copy' }}
            </button>
          </div>
          <span class="help">Shown once. Share it with {{ data.username }} directly.</span>
        </div>
      } @else {
        <label class="field">
          <span class="label">New password</span>
          <input class="input" type="password" autocomplete="new-password" placeholder="At least 12 characters"
                 [value]="typed()" (input)="typed.set($any($event.target).value)">
          @if (typed() && !typedCheck().ok) {
            <span class="help">Use at least 12 characters and mix letters with numbers or symbols.</span>
          }
        </label>
      }
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="close" /><div>{{ error() }}</div></div>
      }
      <div class="banner warn"><app-icon name="alert" /><div>{{ data.username }} is signed out on every device as soon as you save.</div></div>
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="submit" class="btn primary" [disabled]="!ready() || saving()">Reset password</button>
      </div>
    </form>
  `,
  styles: `.nowrap { flex-wrap: nowrap; } .seg { align-self: flex-start; }`,
})
export class ResetPasswordDialogComponent {
  protected readonly data = inject<ResetPasswordData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ResetPasswordDialogComponent>);

  protected readonly temporary = generatePassword();
  protected readonly generated = signal(true);
  protected readonly typed = signal('');
  protected readonly copied = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly typedCheck = computed(() => checkPassword(this.typed()));
  protected readonly ready = computed(() => this.generated() || this.typedCheck().ok);

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.temporary);
      this.copied.set(true);
    } catch {
      this.error.set('Copying is blocked here. Select the password and copy it yourself.');
    }
  }

  protected async submit(): Promise<void> {
    if (!this.ready() || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      await this.data.save(this.generated() ? this.temporary : this.typed());
      this.ref.close(true);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The password could not be reset.');
    } finally {
      this.saving.set(false);
    }
  }
}
