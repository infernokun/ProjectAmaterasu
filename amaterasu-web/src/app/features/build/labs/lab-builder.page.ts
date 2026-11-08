import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LabType } from '../../../enums/lab-type.enum';
import { CrumbsService } from '../../../core/crumbs.service';
import { relativeTime } from '../../../core/time';
import { ThemeStore } from '../../../core/theme/theme.store';
import { CodeModel, MonacoEditorComponent } from '../../../components/common/monaco-editor/monaco-editor.component';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { address, serverHealth } from '../../admin/servers/server-health';
import { Server } from '../../admin/servers/servers.service';
import { BuilderType, LabBuilderStore } from './lab-builder.store';

interface Step {
  label: string;
  state: 'done' | 'now' | '';
}

@Component({
  selector: 'app-lab-builder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, SkeletonComponent, MonacoEditorComponent],
  providers: [LabBuilderStore],
  templateUrl: './lab-builder.page.html',
  styleUrl: './lab-builder.page.scss',
  host: { class: 'content' },
})
export class LabBuilderPage {
  protected readonly store = inject(LabBuilderStore);
  private readonly theme = inject(ThemeStore);
  private readonly router = inject(Router);
  private readonly crumbs = inject(CrumbsService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  /** From the route; absent for a new lab. */
  readonly labId = input<string>();

  protected readonly types = LabType;
  protected readonly address = address;
  protected readonly editorTheme = computed(() => (this.theme.theme() === 'light' ? 'vs' : 'vs-dark'));
  protected readonly codeModel = computed<CodeModel>(() => ({ language: 'yaml', uri: 'docker-compose.yml', value: this.store.compose() }));

  protected readonly title = computed(() => this.store.name().trim() || (this.store.labId() ? 'Untitled environment' : 'New environment'));
  protected readonly serverName = computed(() => this.store.server()?.name ?? 'a server');

  protected readonly statusTag = computed(() => {
    const original = this.store.original();
    if (!original) {
      return 'New';
    }
    return original.published ? 'Published' : 'Draft';
  });

  protected readonly saveHint = computed(() => {
    if (!this.store.canSave()) {
      return `Fill in the basics and the ${this.store.isCompose() ? 'compose file' : 'templates'} first`;
    }
    return this.store.dirty() || !this.store.labId() ? '' : 'Nothing changed since the last save';
  });

  protected readonly savedNote = computed(() => {
    if (this.store.saving()) {
      return 'Saving';
    }
    if (this.store.dirty()) {
      return 'Unsaved changes';
    }
    const savedAt = this.store.savedAt();
    if (savedAt) {
      return `Saved ${relativeTime(savedAt).toLowerCase()}`;
    }
    return this.store.original() ? `Last changed ${relativeTime(this.store.original()!.updatedAt).toLowerCase()}` : 'Not saved yet';
  });

  protected readonly steps = computed<Step[]>(() => {
    const done = [
      !!this.store.name().trim() && this.store.capacity() >= 1,
      this.store.sourceReady(),
      this.store.validated(),
      this.store.published() && !this.store.dirty(),
    ];
    const labels = ['Basics', this.store.isCompose() ? 'Compose file' : 'Pick templates', 'Validate', 'Publish'];
    const now = done.findIndex(d => !d);
    return labels.map((label, i) => ({ label, state: done[i] ? 'done' : i === now ? 'now' : '' }));
  });

  protected readonly validationChip = computed(() => {
    switch (this.store.validation()) {
      case 'running':
        return { tone: 's-work', label: 'Running' };
      case 'done': {
        const problems = this.store.problems();
        const warnings = this.store.warnings();
        if (problems) {
          return { tone: 's-bad', label: `${problems} ${problems === 1 ? 'problem' : 'problems'}` };
        }
        if (warnings) {
          return { tone: 's-warn', label: `${warnings} ${warnings === 1 ? 'warning' : 'warnings'}` };
        }
        return { tone: 's-active', label: 'Passed' };
      }
      default:
        return { tone: 's-off', label: 'Not run' };
    }
  });

  protected readonly unitsLabel = computed(() => {
    if (this.store.isCompose()) {
      const count = this.store.analysis().services.length;
      return `${count} ${count === 1 ? 'service' : 'services'}`;
    }
    const count = this.store.picked().length;
    return `${count} ${count === 1 ? 'VM' : 'VMs'}`;
  });

  protected readonly perCopyText = computed(() => {
    const { cpus, memoryGb } = this.store.perCopy();
    if (this.store.isCompose()) {
      const unlimited = this.store.analysis().unlimited.length;
      const declared = `${cpus || 'no'} vCPU and ${memoryGb} GB RAM declared per team copy`;
      return unlimited ? `${declared}, not counting ${unlimited} ${unlimited === 1 ? 'service' : 'services'} without limits` : declared;
    }
    return `${cpus} vCPU and ${memoryGb} GB RAM per team copy`;
  });

  protected readonly bridgeNote = computed(() => {
    const networks = this.store.networks().filter(n => n.iface.startsWith('vmbr'));
    return networks.map(n => `${n.iface}${n.cidr ? ' ' + n.cidr : n.address ? ' ' + n.address : ''}`).join(', ');
  });

  constructor() {
    effect(() => {
      const id = this.labId();
      untracked(() => this.store.init(id && id !== 'new' ? id : null));
    });
    effect(() => {
      const name = this.store.name().trim();
      const editing = !!this.store.original();
      untracked(() => this.crumbs.set([
        { label: 'Build' },
        { label: 'Environments', link: '/build/environments' },
        { label: editing ? name || 'Untitled environment' : 'New environment' },
      ]));
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected gb(bytes: number): number {
    return Math.round((bytes / 1024 ** 3) * 10) / 10;
  }

  protected serverOption(server: Server): string {
    const health = serverHealth(server);
    const kind = server.serverType === 'PROXMOX' ? 'Proxmox node' : 'Docker host';
    return `${server.name} (${health.online ? kind : health.label.toLowerCase()})`;
  }

  protected pickType(type: BuilderType): void {
    this.store.setType(type);
  }

  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > 512 * 1024) {
      this.toast.show('That file is over 512 KB. Compose files are usually a few KB.', 'bad');
      return;
    }
    this.store.setCompose(await file.text());
    this.toast.info(`Loaded ${file.name}. Validate it, then save.`);
  }

  protected async save(): Promise<void> {
    const creating = !this.store.labId();
    try {
      const lab = await this.store.save();
      this.toast.success(creating ? `${lab.name} created` : `${lab.name} saved`);
      if (creating) {
        this.router.navigate(['/build/environments', lab.id], { replaceUrl: true });
      }
    } catch (error) {
      this.toast.error('The lab could not be saved.', error);
    }
  }

  protected async publish(): Promise<void> {
    const creating = !this.store.labId();
    const answer = await firstValueFrom(this.confirm.open({
      title: `Publish ${this.store.name().trim()}?`,
      message: 'Teams see it in the lab catalog and can start their own copy right away.'
        + (this.store.dirty() || creating ? ' Your changes are saved first.' : ''),
      confirmLabel: 'Publish lab',
      tone: 'primary',
      icon: 'layers',
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      const lab = await this.store.publish();
      this.toast.success(`${lab.name} is published. Teams can start it now.`);
      if (creating) {
        this.router.navigate(['/build/environments', lab.id], { replaceUrl: true });
      }
    } catch (error) {
      this.toast.error('The lab could not be published.', error);
    }
  }

  protected async unpublish(): Promise<void> {
    const name = this.store.name().trim();
    const answer = await firstValueFrom(this.confirm.open({
      title: `Unpublish ${name}?`,
      message: 'It goes back to a draft: teams no longer see it in the catalog and cannot start it. '
        + 'Teams that stopped their copy keep it in their list until you publish again. '
        + 'While any team has it running or paused it stays published; they need to stop it first.',
      confirmLabel: 'Unpublish',
      tone: 'primary',
      icon: 'layers',
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await this.store.unpublish();
      this.toast.success(`${name} is a draft again`);
    } catch (error) {
      this.toast.error(`${name} could not go back to draft.`, error);
    }
  }

  protected async discard(): Promise<void> {
    if (this.store.dirty()) {
      const answer = await firstValueFrom(this.confirm.open({
        title: 'Discard your changes?',
        message: this.store.labId() ? 'The lab goes back to how it was last saved.' : 'Nothing about this lab has been saved yet.',
        confirmLabel: 'Discard changes',
        cancelLabel: 'Keep editing',
      }));
      if (answer !== 'confirm') {
        return;
      }
    }
    this.router.navigate(['/build/environments']);
  }
}
