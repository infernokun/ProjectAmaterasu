import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Role } from '../../enums/role.enum';
import { checkPassword } from '../../core/password';
import { SessionStore } from '../../core/stores/session.store';
import { formatDate, relativeTime, toDate } from '../../core/time';
import { ConfirmService } from '../../shared/services/confirm.service';
import { ToastService } from '../../shared/services/toast.service';
import { IconComponent } from '../../shared/ui/icon.component';
import { IconName } from '../../shared/ui/icons';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { ThemePickerComponent } from '../../shared/ui/theme-picker.component';
import { DensityPickerComponent } from '../../shared/ui/density-picker.component';
import { ClassificationSettingComponent } from './classification-setting.component';
import { AccountSession } from './account.service';
import { AccountStore } from './account.store';
import { deviceName } from './user-agent';

type Section = 'profile' | 'password' | 'sessions' | 'appearance';

const SECTIONS: { key: Section; label: string; icon: IconName }[] = [
  { key: 'profile', label: 'Profile', icon: 'user' },
  { key: 'password', label: 'Password', icon: 'lock' },
  { key: 'sessions', label: 'Sessions', icon: 'monitor' },
  { key: 'appearance', label: 'Appearance', icon: 'moon' },
];

interface Capability {
  can: boolean;
  text: string;
}

@Component({
  selector: 'app-account',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, SkeletonComponent, ThemePickerComponent, DensityPickerComponent, ClassificationSettingComponent],
  providers: [AccountStore],
  templateUrl: './account.page.html',
  styles: `
    .secnav { display: flex; flex-direction: column; gap: 2px; flex: 1 1 200px; max-width: 100%; }
    .secnav button { display: flex; align-items: center; gap: 10px; min-height: 40px; padding: 0 12px; border: 0; border-radius: 8px;
      background: transparent; color: var(--text-2); font: inherit; font-weight: 500; cursor: pointer; text-align: left; }
    .secnav button:hover { background: var(--surface-2); color: var(--text); }
    .secnav button.on { background: var(--accent-soft); color: var(--text); }
    .secnav button:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
    .panel { flex: 999 1 520px; min-width: 0; }
    .rule { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--text-3); }
    .rule.ok { color: var(--ok); }
    .narrow { max-width: 420px; }
    .ok-icon { color: var(--ok); }
    .mismatch { color: var(--bad); }
    p { margin: 0; }
  `,
  host: { class: 'content', style: 'max-width:1080px' },
})
export class AccountPage {
  protected readonly session = inject(SessionStore);
  protected readonly store = inject(AccountStore);
  private readonly router = inject(Router);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  /** From the ?section= query parameter. */
  readonly sectionParam = input<string>(undefined, { alias: 'section' });
  protected readonly section = linkedSignal<Section>(() =>
    SECTIONS.some(s => s.key === this.sectionParam()) ? this.sectionParam() as Section : 'profile');
  protected readonly sections = SECTIONS;

  protected readonly created = computed(() => formatDate(this.session.user()?.createdAt));
  protected readonly capabilities = computed<Capability[]>(() => capabilitiesFor(this.session.role(), this.session.team()?.name));

  // Password form
  protected readonly current = signal('');
  protected readonly next = signal('');
  protected readonly confirmNext = signal('');
  protected readonly check = computed(() => checkPassword(this.next(), this.current() || undefined));
  protected readonly mismatch = computed(() => this.confirmNext().length > 0 && this.confirmNext() !== this.next());
  protected readonly canSubmit = computed(() =>
    !!this.current() && this.check().ok && this.confirmNext() === this.next() && !this.store.changing());
  protected readonly strengthLevel = computed(() => (this.check().score >= 3 ? 'ok' : this.check().score === 2 ? 'warn' : 'bad'));
  protected readonly strengthPct = computed(() => (this.next().length ? Math.max(10, this.check().score * 25) : 0));

  constructor() {
    effect(() => {
      if (this.section() === 'sessions') {
        this.store.loadSessions();
      }
    });
  }

  protected show(section: Section): void {
    this.section.set(section);
    this.router.navigate([], { queryParams: { section: section === 'profile' ? null : section }, replaceUrl: true });
  }

  protected typed(field: { set(value: string): void }, event: Event): void {
    field.set((event.target as HTMLInputElement).value);
    this.store.clearPasswordNotice();
  }

  protected async changePassword(): Promise<void> {
    if (!this.canSubmit()) {
      return;
    }
    if (await this.store.changePassword(this.current(), this.next())) {
      this.current.set('');
      this.next.set('');
      this.confirmNext.set('');
    }
  }

  protected device(session: AccountSession): string {
    return deviceName(session.userAgent);
  }

  protected meta(session: AccountSession): string {
    const where = session.ipAddress ?? 'Unknown address';
    if (session.current) {
      return `${where} · signed in ${when(session.signedInAt)}`;
    }
    return `${where} · last active ${when(session.lastActive ?? session.signedInAt)}`;
  }

  protected async signOut(session: AccountSession): Promise<void> {
    try {
      await this.store.signOut(session.id);
      this.toast.success(`${this.device(session)} was signed out`);
    } catch (error) {
      this.toast.error('That session could not be signed out.', error);
    }
  }

  protected async signOutOthers(): Promise<void> {
    const count = this.store.others().length;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Sign out ${count === 1 ? 'the other session' : `${count} other sessions`}?`,
      message: 'Those devices need to sign in again. This browser stays signed in.',
      confirmLabel: 'Sign out everywhere else',
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await this.store.signOutOthers();
      this.toast.success('Every other session was signed out');
    } catch (error) {
      this.toast.error('The other sessions could not be signed out.', error);
    }
  }
}

/** "today 08:58", "yesterday 17:12", "3 days ago". */
function when(value: string | null): string {
  const date = toDate(value);
  if (!date) {
    return 'at an unknown time';
  }
  const time = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  const today = new Date();
  const yesterday = new Date(today.getTime() - 86_400_000);
  if (date.toDateString() === today.toDateString()) {
    return `today ${time}`;
  }
  if (date.toDateString() === yesterday.toDateString()) {
    return `yesterday ${time}`;
  }
  return relativeTime(date).toLowerCase();
}

export function capabilitiesFor(role: Role | undefined, teamName: string | undefined): Capability[] {
  const labs = teamName
    ? { can: true, text: `Start, pause, resume and stop ${teamName} labs` }
    : { can: false, text: 'Run team labs. An admin needs to put you on a team first.' };
  const base: Capability[] = [{ can: true, text: 'Join labs and answer their questions' }, labs];
  switch (role) {
    case Role.ADMIN:
      return [...base,
        { can: true, text: 'Run the live event and award points' },
        { can: true, text: 'Author labs, their questions and environments' },
        { can: true, text: 'Manage users, teams and servers' }];
    case Role.CREATOR:
      return [...base,
        { can: true, text: 'Author labs, their questions and environments' },
        { can: false, text: 'Award points or manage users' }];
    case Role.FACILITATOR:
      return [...base,
        { can: true, text: 'Run the live event and award points' },
        { can: false, text: 'Author labs or manage users' }];
    default:
      return [...base, { can: false, text: 'Award points, author labs or manage users' }];
  }
}
