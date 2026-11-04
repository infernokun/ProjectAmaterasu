import { StandingPoint } from '../data/play.models';
import { ChartSeries, SeriesTone } from './points-chart.component';

export interface Ranked {
  id: string;
  name: string;
  history: StandingPoint[];
}

/** Top three plus the viewer, as chart lines. */
export function topSeries(rows: Ranked[], youId: string | undefined): ChartSeries[] {
  const picked = rows.slice(0, 3);
  const you = rows.find(row => row.id === youId);
  if (you && !picked.includes(you)) picked.push(you);
  const others: SeriesTone[] = ['o1', 'o2', 'o3'];
  let next = 0;
  return picked.map(row => ({
    name: row.name,
    tone: row.id === youId ? 'you' : others[next++ % others.length],
    points: row.history.map(point => ({ at: new Date(point.at).getTime(), total: point.total })).filter(point => !Number.isNaN(point.at)),
  }));
}

export function trend(change: number): { label: string; tone: string } {
  return change === 0 ? { label: 'No change', tone: 'muted' } : { label: `${change > 0 ? '+' : ''}${change.toLocaleString('en-US')} pts`, tone: change > 0 ? 's-active' : 's-bad' };
}
