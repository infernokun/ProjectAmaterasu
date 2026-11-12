import { InjectionToken, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { map } from 'rxjs';
import { Crumb } from '../../../core/crumbs.service';
import { IconName } from '../../../shared/ui/icons';
import { EnvironmentView } from '../data/play.models';
import { LabLink, consoleVm, labConsoleLink, labDetailsLink } from './lab-address';
import { trackerOf } from './environment-status';

/**
 * From this width a lab shows its sub-pages beside the questions. Under it the sidebar is still open
 * (it folds at 960px) and two panes would leave each under 450px.
 */
export const SPLIT_QUERY = '(min-width: 1280px)';

/** Whether the lab page has room for two panes. */
export const LAB_SPLIT = new InjectionToken<Signal<boolean>>('LAB_SPLIT', {
  providedIn: 'root',
  factory: () => toSignal(inject(BreakpointObserver).observe(SPLIT_QUERY).pipe(map(state => state.matches)), {
    initialValue: typeof window !== 'undefined' && !!window.matchMedia?.(SPLIT_QUERY).matches,
  }),
});

/** The lab sub-page showing, from its route: 'details', 'console', 'edit', 'teams' or 'manage'. */
export interface SubPage {
  key: string;
  title: string;
}

export interface LabView extends LabLink {
  key: string;
  label: string;
  icon: IconName;
}

/** The lab's views, as the viewer may use them. The question list is always first. */
export function labViews(
  labId: string,
  environment: EnvironmentView | null,
  can: { build: boolean; run: boolean },
  challenge?: string | null,
): LabView[] {
  const views: LabView[] = [
    { key: '', label: 'Questions', icon: 'flag', commands: ['/labs', labId], queryParams: challenge ? { challenge } : {} },
  ];
  if (environment) {
    views.push({ key: 'details', label: 'Details', icon: 'layers', ...labDetailsLink(labId) });
    const vm = consoleVm(trackerOf(environment));
    if (vm) views.push({ key: 'console', label: 'Console', icon: 'terminal', ...labConsoleLink(labId, vm.vmid) });
  }
  if (can.build) views.push({ key: 'edit', label: 'Edit', icon: 'edit', commands: ['/labs', labId, 'edit'], queryParams: {} });
  if (can.run) {
    views.push({ key: 'teams', label: 'Teams', icon: 'users', commands: ['/labs', labId, 'teams'], queryParams: {} });
    views.push({ key: 'manage', label: 'Manage', icon: 'shield', commands: ['/labs', labId, 'manage'], queryParams: {} });
  }
  return views;
}

/** Labs › On Call › Details; a console sits under the copy's Details. */
export function labCrumbs(labId: string, name: string, sub: SubPage | null, copy?: string | null): Crumb[] {
  const crumbs: Crumb[] = [{ label: 'Labs', link: '/labs' }];
  if (!sub) return [...crumbs, { label: name }];
  crumbs.push({ label: name, link: ['/labs', labId] });
  if (sub.key === 'console') {
    const details = labDetailsLink(labId, copy);
    crumbs.push({ label: 'Details', link: details.commands, queryParams: details.queryParams });
  }
  return [...crumbs, { label: sub.title }];
}
