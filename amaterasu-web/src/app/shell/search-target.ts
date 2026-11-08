import { SearchHit, SearchHitType } from '../models/search.model';
import { IconName } from '../shared/ui/icons';

/** Where a search result opens. */
export interface SearchTarget {
  commands: string[];
  queryParams?: Record<string, string>;
  fragment?: string;
}

/** Authors open environments and hidden or unreleased questions in Build; everyone else in Play. */
export function searchTarget(hit: SearchHit, author: boolean): SearchTarget {
  switch (hit.type) {
    case 'TEAM_LAB':
      return { commands: ['/environments', hit.id] };
    case 'LAB':
      return author ? { commands: ['/build/environments', hit.id] } : { commands: ['/labs'] };
    case 'ROOM':
      return { commands: ['/labs', hit.id] };
    case 'CHALLENGE':
      return author && hit.status
        ? { commands: ['/build/labs', hit.roomId ?? ''] }
        : { commands: ['/labs', hit.roomId ?? ''], queryParams: { challenge: hit.id } };
    case 'USER':
      return { commands: ['/admin/users'], queryParams: { q: hit.title } };
    case 'TEAM':
      return { commands: ['/admin/users'], queryParams: { tab: 'teams' } };
    case 'SERVER':
      return { commands: ['/admin/servers'], queryParams: { server: hit.id } };
  }
}

export const SEARCH_ICONS: Record<SearchHitType, IconName> = {
  TEAM_LAB: 'flask',
  LAB: 'layers',
  ROOM: 'flag',
  CHALLENGE: 'trophy',
  USER: 'user',
  TEAM: 'users',
  SERVER: 'server',
};

/** The title split around the first match, for highlighting. */
export function highlight(title: string, query: string): { before: string; match: string; after: string } {
  const at = query ? title.toLowerCase().indexOf(query.toLowerCase()) : -1;
  return at < 0
    ? { before: title, match: '', after: '' }
    : { before: title.slice(0, at), match: title.slice(at, at + query.length), after: title.slice(at + query.length) };
}
