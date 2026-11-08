import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { PlayApiService } from '../data/play-api.service';
import { LabSeat, TeamMode } from '../data/play.models';
import { TeamPickerComponent } from './team-picker.component';

describe('TeamPickerComponent', () => {
  let fixture: ComponentFixture<TeamPickerComponent>;
  let api: jasmine.SpyObj<PlayApiService>;
  const seat: LabSeat = { roomId: 'lab1', userId: 'u1', status: 'JOINED', points: 0, teamId: 't1', teamName: 'Owls' };

  async function render(mode: TeamMode, joined = false, currentTeamId: string | null = null) {
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['labTeams', 'joinLab', 'changeTeam']);
    api.labTeams.and.returnValue(of([
      { id: 't1', name: 'Owls', members: ['ana', 'ben'], size: 2, full: false },
      { id: 't2', name: 'Foxes', members: ['cy', 'dee'], size: 2, full: true },
    ]));
    api.joinLab.and.returnValue(of(seat));
    api.changeTeam.and.returnValue(of(seat));
    TestBed.configureTestingModule({ imports: [TeamPickerComponent], providers: [{ provide: PlayApiService, useValue: api }] });
    fixture = TestBed.createComponent(TeamPickerComponent);
    fixture.componentRef.setInput('labId', 'lab1');
    fixture.componentRef.setInput('teamMode', mode);
    fixture.componentRef.setInput('maxTeamSize', 2);
    fixture.componentRef.setInput('joined', joined);
    fixture.componentRef.setInput('currentTeamId', currentTeamId);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) => [...el().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent!.trim() === label);

  it('offers solo, the teams with room left, and a new team', async () => {
    await render('TEAMS_OPTIONAL');
    expect(el().textContent).toContain('ana, ben · 2 of 2');
    expect(button('Full')!.disabled).toBeTrue();
    const done: LabSeat[] = [];
    fixture.componentInstance.done.subscribe(s => done.push(s));
    button('Join team')!.click();
    await fixture.whenStable();
    expect(api.joinLab).toHaveBeenCalledWith('lab1', { teamId: 't1' });
    expect(done[0].teamName).toBe('Owls');
  });

  it('creates a team, and has no solo choice when teams are required', async () => {
    await render('TEAMS_REQUIRED');
    expect(button('Play solo')).toBeUndefined();
    const input = el().querySelector<HTMLInputElement>('form input')!;
    input.value = ' Night Owls ';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    button('Create and join')!.click();
    await fixture.whenStable();
    expect(api.joinLab).toHaveBeenCalledWith('lab1', { newTeamName: 'Night Owls' });
  });

  it('changes the team of a player who joined, and shows why it failed', async () => {
    await render('TEAMS_OPTIONAL', true, 't1');
    expect(el().textContent).toContain('Your team');
    api.changeTeam.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Owls is full (2 players)' } })));
    button('Play solo')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.changeTeam).toHaveBeenCalledWith('lab1', {});
    expect(el().textContent).toContain('Owls is full (2 players)');
  });
});
