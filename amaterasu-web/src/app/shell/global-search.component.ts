import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, computed, effect, inject, input, signal, untracked, viewChild, Injector } from '@angular/core';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { NgTemplateOutlet } from '@angular/common';
import { Router } from '@angular/router';
import { SearchStore } from '../core/stores/search.store';
import { SessionStore } from '../core/stores/session.store';
import { SearchHit } from '../models/search.model';
import { IconComponent } from '../shared/ui/icon.component';
import { StatusBadgeComponent } from '../shared/ui/status-badge.component';
import { LabStatus } from '../enums/lab-status.enum';
import { SEARCH_ICONS, highlight, searchTarget } from './search-target';

const LIST_ID = 'global-search-results';

/**
 * The top bar search. Press / or Ctrl+K to focus it; arrows move through the results and Enter
 * opens one. At narrow widths it is an icon that opens the same results in a full width panel.
 */
@Component({
  selector: 'app-global-search',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkConnectedOverlay, CdkOverlayOrigin, NgTemplateOutlet, IconComponent, StatusBadgeComponent],
  host: {
    '(document:keydown)': 'onDocumentKey($event)',
    '[class.narrow]': 'narrow()',
  },
  template: `
    @if (!narrow()) {
      <label class="search" cdkOverlayOrigin #origin="cdkOverlayOrigin" #anchor>
        <app-icon name="search" size="sm" />
        <input #box type="search" placeholder="Search labs, environments, questions" aria-label="Search" role="combobox"
               aria-autocomplete="list" [attr.aria-controls]="listId" [attr.aria-expanded]="open()"
               [attr.aria-activedescendant]="activeId()" autocomplete="off" spellcheck="false"
               [value]="store.query()" (input)="onInput($event)" (focus)="open.set(true)" (click)="open.set(true)"
               (keydown)="onKey($event)" />
        <span class="kbd" aria-hidden="true">/</span>
      </label>
      <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="origin" [cdkConnectedOverlayOpen]="open()"
                   [cdkConnectedOverlayPositions]="wide" [cdkConnectedOverlayMinWidth]="440"
                   [cdkConnectedOverlayWidth]="anchorWidth()" [cdkConnectedOverlayPush]="true"
                   (overlayOutsideClick)="outside($event)" (detach)="open.set(false)">
        <div class="pop">
          <ng-container [ngTemplateOutlet]="results" />
        </div>
      </ng-template>
    } @else {
      <button type="button" class="iconbtn" cdkOverlayOrigin #iconOrigin="cdkOverlayOrigin" #iconButton
              aria-label="Search" aria-haspopup="dialog" [attr.aria-expanded]="open()" (click)="openNarrow()">
        <app-icon name="search" />
      </button>
      <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="iconOrigin" [cdkConnectedOverlayOpen]="open()"
                   [cdkConnectedOverlayPositions]="sheet" cdkConnectedOverlayWidth="calc(100vw - 16px)"
                   [cdkConnectedOverlayPush]="true" [cdkConnectedOverlayViewportMargin]="8"
                   (overlayOutsideClick)="close()" (detach)="open.set(false)">
        <div class="pop sheet" role="dialog" aria-label="Search">
          <label class="search sheet-box">
            <app-icon name="search" size="sm" />
            <input #sheetBox type="search" placeholder="Search labs, environments, questions" aria-label="Search" role="combobox"
                   aria-autocomplete="list" [attr.aria-controls]="listId" aria-expanded="true"
                   [attr.aria-activedescendant]="activeId()" autocomplete="off" spellcheck="false"
                   [value]="store.query()" (input)="onInput($event)" (keydown)="onKey($event)" />
            <button type="button" class="btn sm ghost" (click)="close()">Close</button>
          </label>
          <ng-container [ngTemplateOutlet]="results" />
        </div>
      </ng-template>
    }

    <ng-template #results>
      <div class="results" role="listbox" [id]="listId" aria-label="Search results">
        @if (store.tooShort()) {
          <div class="hint small">
            <span>Search {{ store.scope() }}.</span>
            <span class="muted">Press <span class="kbd">/</span> or <span class="kbd">Ctrl K</span> from any page.</span>
          </div>
        } @else if (store.error() && !store.stale()) {
          <div class="hint small" role="status"><app-icon name="alert" size="sm" />{{ store.error() }}</div>
        } @else if (!store.flat().length) {
          @if (store.stale() || store.loading()) {
            <div class="hint small" role="status"><app-icon name="spinner" size="sm" [spin]="true" />Searching</div>
          } @else {
            <div class="hint small" role="status">No matches for "{{ store.trimmed() }}".</div>
          }
        } @else {
          @for (group of store.groups(); track group.type) {
            <div role="group" [attr.aria-labelledby]="'sg-' + group.type">
              <div class="group-label" [id]="'sg-' + group.type">{{ group.label }}</div>
              @for (item of group.items; track item.hit.type + item.hit.id) {
                <div class="option" role="option" [id]="optionId(item.index)" [class.on]="item.index === active()"
                     [attr.aria-selected]="item.index === active()" (mousedown)="$event.preventDefault()"
                     (mousemove)="active.set(item.index)" (click)="go(item.hit)">
                  <span class="feed-ico"><app-icon [name]="icons[item.hit.type]" size="sm" /></span>
                  <span class="grow text">
                    <span class="title">{{ parts(item.hit).before }}<mark>{{ parts(item.hit).match }}</mark>{{ parts(item.hit).after }}</span>
                    @if (item.hit.subtitle) {
                      <span class="small muted sub">{{ item.hit.subtitle }}</span>
                    }
                  </span>
                  @switch (item.hit.type) {
                    @case ('TEAM_LAB') {
                      <app-status-badge [state]="labState(item.hit)" />
                    }
                    @case ('SERVER') {
                      @if (item.hit.status) {
                        <app-status-badge [state]="labState(item.hit)" />
                      }
                    }
                    @case ('CHALLENGE') {
                      @if (item.hit.status === 'HIDDEN') {
                        <span class="tag">Hidden</span>
                      } @else if (item.hit.status === 'UNRELEASED') {
                        <span class="tag">Not released</span>
                      }
                    }
                  }
                </div>
              }
            </div>
          }
        }
      </div>
      <div class="foot small muted" aria-hidden="true">
        <span><span class="kbd">↑</span> <span class="kbd">↓</span> to move</span>
        <span><span class="kbd">Enter</span> to open</span>
        <span><span class="kbd">Esc</span> to close</span>
      </div>
    </ng-template>
  `,
  styleUrl: './global-search.component.scss',
})
export class GlobalSearchComponent {
  protected readonly store = inject(SearchStore);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  /** Narrow layouts show an icon instead of the box. */
  readonly narrow = input(false);

  protected readonly open = signal(false);
  protected readonly active = signal(0);
  protected readonly listId = LIST_ID;
  protected readonly icons = SEARCH_ICONS;

  private readonly box = viewChild<ElementRef<HTMLInputElement>>('box');
  private readonly sheetBox = viewChild<ElementRef<HTMLInputElement>>('sheetBox');
  private readonly anchor = viewChild<ElementRef<HTMLElement>>('anchor');
  private readonly iconButton = viewChild<ElementRef<HTMLButtonElement>>('iconButton');

  protected readonly wide: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  ];
  protected readonly sheet: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 10 },
  ];

  protected readonly anchorWidth = signal<number | string>('');
  protected readonly activeId = computed(() =>
    this.open() && this.store.flat().length ? this.optionId(Math.min(this.active(), this.store.flat().length - 1)) : null);

  constructor() {
    effect(() => {
      this.store.flat();
      untracked(() => this.active.set(0));
    });
    effect(() => {
      if (this.open() && !this.narrow()) {
        const width = this.anchor()?.nativeElement.getBoundingClientRect().width;
        untracked(() => this.anchorWidth.set(width ? Math.max(width, 440) : ''));
      }
    });
    // Leaving the narrow layout while the panel is open would strand it on a removed button
    effect(() => {
      this.narrow();
      untracked(() => this.open.set(false));
    });
  }

  protected optionId(index: number): string {
    return `${LIST_ID}-${index}`;
  }

  protected parts(hit: SearchHit) {
    return highlight(hit.title, this.store.trimmed());
  }

  protected labState(hit: SearchHit): LabStatus | null {
    return (hit.status as LabStatus) ?? null;
  }

  protected onInput(event: Event): void {
    this.store.setQuery((event.target as HTMLInputElement).value);
    this.open.set(true);
  }

  protected onKey(event: KeyboardEvent): void {
    const count = this.store.flat().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.open.set(true);
        if (count) this.active.set((this.active() + 1) % count);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (count) this.active.set((this.active() - 1 + count) % count);
        break;
      case 'Home':
        if (count && this.open()) { event.preventDefault(); this.active.set(0); }
        break;
      case 'End':
        if (count && this.open()) { event.preventDefault(); this.active.set(count - 1); }
        break;
      case 'Enter': {
        const hit = this.store.flat()[this.active()];
        if (hit) {
          event.preventDefault();
          this.go(hit);
        }
        break;
      }
      case 'Escape':
        event.preventDefault();
        if (this.open()) {
          this.close();
        } else if (this.store.query()) {
          this.store.clear();
        } else {
          (event.target as HTMLElement).blur();
        }
        break;
    }
  }

  /** / or Ctrl+K (Cmd+K on a Mac) jumps to the search from anywhere outside a text field. */
  protected onDocumentKey(event: KeyboardEvent): void {
    const shortcut = (event.key === 'k' || event.key === 'K') && (event.ctrlKey || event.metaKey);
    if (!shortcut && (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey || editing(event.target))) {
      return;
    }
    event.preventDefault();
    if (this.narrow()) {
      this.openNarrow();
    } else {
      this.box()?.nativeElement.focus();
      this.box()?.nativeElement.select();
      this.open.set(true);
    }
  }

  protected openNarrow(): void {
    this.open.set(true);
    afterNextRender(() => this.sheetBox()?.nativeElement.focus(), { injector: this.injector });
  }

  protected outside(event: MouseEvent): void {
    // Clicks in the box itself keep the results open
    if (!this.anchor()?.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  protected close(): void {
    const wasNarrow = this.narrow() && this.open();
    this.open.set(false);
    if (wasNarrow) this.iconButton()?.nativeElement.focus();
  }

  protected go(hit: SearchHit): void {
    const target = searchTarget(hit, this.session.canBuild());
    this.open.set(false);
    this.store.clear();
    this.box()?.nativeElement.blur();
    this.router.navigate(target.commands, { queryParams: target.queryParams, fragment: target.fragment });
  }
}

function editing(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
    || !!target.closest('.console-screen');
}
