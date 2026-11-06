import { Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';

export interface Crumb {
  label: string;
  link?: string | unknown[];
}

/**
 * Pages whose breadcrumb depends on data (a room or lab name) set it here; otherwise the top bar
 * uses the route's section and title. Cleared on every navigation.
 */
@Injectable({ providedIn: 'root' })
export class CrumbsService {
  private readonly override = signal<Crumb[] | null>(null);
  readonly custom = this.override.asReadonly();

  private path = '';

  constructor() {
    inject(Router).events
      .pipe(filter(event => event instanceof NavigationStart), takeUntilDestroyed())
      .subscribe(event => {
        // A change of query params alone (a selected tab or challenge) keeps the page's breadcrumb
        const path = event.url.split(/[?#]/)[0];
        if (path !== this.path) {
          this.path = path;
          this.override.set(null);
        }
      });
  }

  set(crumbs: Crumb[]): void {
    this.override.set(crumbs);
  }
}
