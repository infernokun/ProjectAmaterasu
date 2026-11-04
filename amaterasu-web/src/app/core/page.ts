import { Route } from '@angular/router';

/** Route data every page sets; the top bar builds its breadcrumb from it. */
export interface PageData {
  /** The sidebar group, e.g. "Admin". Left out for Play pages and Account. */
  section?: 'Run' | 'Build' | 'Admin' | 'Dev';
  /** The page name in the breadcrumb and the browser tab. */
  title: string;
  /** What the page is for; placeholders show it under the title. */
  description?: string;
  /** Still rendered by a previous component that brings no page padding of its own. */
  legacy?: boolean;
}

/** A route with its title and breadcrumb data set from one object. */
export function page(route: Route & { data: PageData }): Route {
  return { ...route, title: route.title ?? route.data.title };
}
