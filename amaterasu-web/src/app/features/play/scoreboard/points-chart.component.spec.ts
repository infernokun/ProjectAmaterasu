import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PointsChartComponent } from './points-chart.component';
import { topSeries, trend } from './scoreboard-view';

describe('PointsChartComponent', () => {
  let fixture: ComponentFixture<PointsChartComponent>;
  const t0 = Date.parse('2026-11-04T09:00:00Z');

  beforeEach(() => {
    fixture = TestBed.createComponent(PointsChartComponent);
    fixture.componentRef.setInput('series', [
      { name: 'jmorales', tone: 'you', points: [{ at: t0, total: 0 }, { at: t0 + 3600_000, total: 300 }] },
      { name: 'priya.n', tone: 'o1', points: [{ at: t0, total: 0 }, { at: t0 + 1800_000, total: 450 }] },
    ]);
    fixture.componentRef.setInput('label', 'Points over time');
    fixture.detectChanges();
  });

  it('draws a step line and an end label per series', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('polyline').length).toBe(2);
    expect(Array.from(el.querySelectorAll('text.endl')).map(text => text.textContent)).toEqual(['jmorales', 'priya.n']);
    expect(el.querySelector('svg')?.getAttribute('aria-label')).toBe('Points over time');
  });

  it('marks your points', () => {
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('circle.mk').length).toBe(2);
  });
});

describe('scoreboard helpers', () => {
  it('charts the top three and you', () => {
    const rows = ['a', 'b', 'c', 'd', 'e'].map(id => ({ id, name: id, history: [] }));
    expect(topSeries(rows, 'e').map(series => [series.name, series.tone])).toEqual([['a', 'o1'], ['b', 'o2'], ['c', 'o3'], ['e', 'you']]);
    expect(topSeries(rows, 'b').map(series => series.tone)).toEqual(['o1', 'you', 'o2']);
  });

  it('describes the last hour', () => {
    expect(trend(0).label).toBe('No change');
    expect(trend(250)).toEqual({ label: '+250 pts', tone: 's-active' });
  });
});
