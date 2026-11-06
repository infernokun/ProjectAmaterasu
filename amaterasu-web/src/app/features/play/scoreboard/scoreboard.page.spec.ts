import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { RoomService } from '../../../services/ctf/room.service';
import { WebsocketService } from '../../../services/websocket.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { PlayApiService } from '../data/play-api.service';
import { RoomStandings } from '../data/play.models';
import { ScoreboardPage } from './scoreboard.page';

describe('ScoreboardPage team points', () => {
  let fixture: ComponentFixture<ScoreboardPage>;
  const opened = new Date();
  opened.setHours(9, 0, 0, 0);
  const at = (minutes: number) => new Date(opened.getTime() + minutes * 60_000).toISOString();
  const standings: RoomStandings = {
    roomId: 'r1', roomName: 'Winter Range 2026', openedAt: opened.toISOString(),
    players: [
      { rank: 1, userId: 'u2', username: 'priya.n', teamId: 'red', teamName: 'Red Fox', points: 300, solves: 2, hintsUsed: 0, wrongAttempts: 0,
        lastSolveAt: at(50), lastHourChange: 300, solvesByCategory: {}, history: [{ at: at(5), total: 0 }, { at: at(50), total: 300 }] },
      { rank: 2, userId: 'u3', username: 'ghost_rk', teamId: 'owls', teamName: 'Night Owls', points: 250, solves: 1, hintsUsed: 1, wrongAttempts: 0,
        lastSolveAt: at(40), lastHourChange: 0, solvesByCategory: {}, history: [{ at: at(5), total: 0 }, { at: at(40), total: 250 }] },
      { rank: 3, userId: 'u1', username: 'jmorales', teamId: 'red', teamName: 'Red Fox', points: 150, solves: 1, hintsUsed: 1, wrongAttempts: 2,
        lastSolveAt: at(30), lastHourChange: 150, solvesByCategory: {}, history: [{ at: at(5), total: 0 }, { at: at(30), total: 150 }] },
    ],
    teams: [
      { rank: 1, teamId: 'red', name: 'Red Fox', members: 2, teamSize: 4, points: 450, solves: 3, hintsUsed: 1, lastSolveAt: at(50),
        lastHourChange: 450, history: [{ at: at(5), total: 0 }, { at: at(30), total: 150 }, { at: at(50), total: 450 }] },
      { rank: 2, teamId: 'owls', name: 'Night Owls', members: 1, teamSize: 3, points: 250, solves: 1, hintsUsed: 1, lastSolveAt: at(40),
        lastHourChange: 0, history: [{ at: at(5), total: 0 }, { at: at(40), total: 250 }] },
    ],
  };

  beforeEach(() => {
    const auth = new FakeAuthService();
    auth.signIn({ id: 'u1', username: 'jmorales', team: { id: 'red', name: 'Red Fox' } }, 'MEMBER');
    TestBed.configureTestingModule({
      imports: [ScoreboardPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: PlayApiService, useValue: { standings: () => of(standings) } },
        { provide: RoomService, useValue: { getAllRooms: () => of({ data: [] }), checkJoinable: () => of({ data: {} }) } },
        { provide: WebsocketService, useValue: { getMessage: () => EMPTY } },
      ],
    });
    fixture = TestBed.createComponent(ScoreboardPage);
    fixture.componentRef.setInput('roomId', 'r1');
    fixture.detectChanges();
  });

  async function teamsView(): Promise<HTMLElement> {
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    Array.from(el.querySelectorAll<HTMLButtonElement>('.seg button')).find(b => b.textContent?.trim() === 'Teams')!.click();
    fixture.detectChanges();
    return el;
  }

  it('ranks teams by the sum of their members points, with your team marked', async () => {
    const el = await teamsView();
    const rows = Array.from(el.querySelectorAll('tbody tr'));
    expect(rows.map(row => row.querySelector('.name')?.textContent?.trim())).toEqual(['Red Fox', 'Night Owls']);
    expect(rows[0].querySelector('.tag.accent')?.textContent).toBe('You');
    expect(rows[0].classList).toContain('hl');
    expect(rows[0].querySelectorAll('td')[2].textContent?.trim()).toBe('4 members');
    expect(rows[0].querySelectorAll('td')[3].textContent?.trim()).toBe('450');
    expect(el.querySelector('#tbl-h')?.textContent).toBe('All teams');
  });

  it('shows how far your team leads and charts team points since the room opened', async () => {
    const el = await teamsView();
    const you = el.querySelectorAll('.kpis .card')[2];
    expect(you.querySelector('.kpi-label')?.textContent?.trim()).toBe('Red Fox');
    expect(you.querySelector('.kpi-val')?.textContent?.trim()).toBe('1st');
    expect(you.querySelector('.kpi-note')?.textContent?.trim()).toBe('200 pts ahead of Night Owls');
    expect(el.querySelector('#chart-h')?.textContent).toBe('Team points over time');
    expect(el.textContent).toContain('Top 3 and your team since the room opened at 09:00');
  });
});
