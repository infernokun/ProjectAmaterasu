import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LabStatus } from '../../../enums/lab-status.enum';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { PlayApiService } from '../data/play-api.service';
import { EnvironmentCopy, EnvironmentView } from '../data/play.models';
import { EnvironmentBarComponent } from './environment-bar.component';

describe('EnvironmentBarComponent', () => {
  let fixture: ComponentFixture<EnvironmentBarComponent>;
  let api: jasmine.SpyObj<PlayApiService>;
  let confirm: jasmine.SpyObj<ConfirmService>;

  const running = {
    id: 't1', labStatus: LabStatus.ACTIVE, roomId: 'lab1', labStarted: { id: 'def1', name: 'Hunt', labType: 'VIRTUAL_MACHINE' },
    vms: [{ vmid: 150, name: 'bastion', status: 'running', role: 'bastion', ciuser: 'player', guestAgent: true, ready: true,
      accessAddress: '10.0.0.77', labSubnet: '172.16.0.0/24' }],
  };
  const copy = (patch: Partial<EnvironmentCopy> = {}): EnvironmentCopy => ({
    target: 'shared', label: 'Shared copy', teamId: null, solo: false, members: 0, status: 'NOT_RUNNING', trackerId: null,
    tracker: null, ...patch,
  });
  const view = (patch: Partial<EnvironmentView> = {}): EnvironmentView => ({
    labId: 'def1', labName: 'Hunt', labType: 'VIRTUAL_MACHINE', mode: 'SHARED', status: 'NOT_RUNNING', trackerId: null,
    tracker: null, copyLabel: 'Shared copy', canControl: true, note: null, copiesRunning: 0, copies: [copy()], ...patch,
  });

  function render(environment: EnvironmentView) {
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['environmentAction', 'access']);
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['open']);
    TestBed.configureTestingModule({
      imports: [EnvironmentBarComponent],
      providers: [
        provideRouter([]),
        { provide: PlayApiService, useValue: api },
        { provide: ConfirmService, useValue: confirm },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'show', 'error']) },
      ],
    });
    fixture = TestBed.createComponent(EnvironmentBarComponent);
    fixture.componentRef.setInput('labId', 'lab1');
    fixture.componentRef.setInput('environment', environment);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const labels = () => [...el().querySelectorAll('.actions .btn')].map(b => b.textContent!.trim());
  const copyButtons = (target: string) =>
    [...el().querySelectorAll<HTMLElement>(`.copy[data-target="${target}"] .btn`)];
  const copyLabels = (target: string) => copyButtons(target).map(b => b.textContent!.trim());

  it('offers an admin Start on the shared copy, and shows Starting while it runs', async () => {
    render(view());
    expect(el().textContent).toContain('Not running');
    expect(copyLabels('shared')).toEqual(['Start']);
    api.environmentAction.and.returnValue(of({ successful: true, message: 'Environment started',
      environment: view({ trackerId: 't1', tracker: running, status: 'RUNNING',
        copies: [copy({ trackerId: 't1', tracker: running, status: 'RUNNING' })] }) }));
    const emitted: EnvironmentView[] = [];
    fixture.componentInstance.changed.subscribe(env => emitted.push(env));
    copyButtons('shared')[0].click();
    await fixture.whenStable();
    expect(api.environmentAction).toHaveBeenCalledWith('lab1', 'start', 'shared');
    expect(emitted[0].status).toBe('RUNNING');
  });

  it('gives a running copy Open console, the login line and Pause and Stop for the admin', () => {
    render(view({ trackerId: 't1', tracker: running, status: 'RUNNING',
      copies: [copy({ trackerId: 't1', tracker: running, status: 'RUNNING' })] }));
    expect(el().textContent).toContain('Running');
    expect(labels()).toEqual(['Open console', 'Details']);
    expect(copyLabels('shared')).toEqual(['Pause', 'Stop', 'Details']);
    expect(el().textContent).toContain('ssh player@10.0.0.77');
    expect(el().querySelector('[data-testid="password"]')!.textContent).toContain('••••');
  });

  it('asks for the name before stopping and can pause instead', async () => {
    render(view({ trackerId: 't1', tracker: running, status: 'RUNNING',
      copies: [copy({ trackerId: 't1', tracker: running, status: 'RUNNING' })] }));
    confirm.open.and.returnValue(of('alternative'));
    api.environmentAction.and.returnValue(of({ successful: true, message: 'Environment paused', environment: null }));
    copyButtons('shared').find(b => b.textContent!.includes('Stop'))!.click();
    await fixture.whenStable();
    expect(confirm.open).toHaveBeenCalledWith(jasmine.objectContaining({ typeToConfirm: 'Hunt' }));
    expect(api.environmentAction).toHaveBeenCalledWith('lab1', 'pause', 'shared');
  });

  it('gives a player no buttons and a calm note while their copy is not running', () => {
    render(view({ mode: 'PER_TEAM', copyLabel: 'Solo copy', canControl: false, copies: [],
      note: 'Not running yet: an admin starts this environment.' }));
    expect(el().querySelectorAll('button').length).toBe(0);
    expect(labels()).toEqual([]);
    expect(el().textContent).toContain('Not running yet: an admin starts this environment.');
    expect(el().textContent).toContain('Solo copy');
  });

  it("shows a player their running copy's access, still without lifecycle buttons", () => {
    render(view({ trackerId: 't1', tracker: running, status: 'RUNNING', canControl: false, copies: [] }));
    expect(labels()).toEqual(['Open console', 'Details']);
    expect(el().textContent).toContain('ssh player@10.0.0.77');
    expect([...el().querySelectorAll('button')].map(b => b.textContent!.trim())).not.toContain('Stop');
  });

  it('lists every copy of a per-team lab with its own actions, and starts every team in turn', async () => {
    const red = { ...running, id: 't2', roomId: null, labOwner: { id: 'red' } };
    render(view({ mode: 'PER_TEAM', copyLabel: 'Solo copy', copies: [
      copy({ target: 'team:blue', label: 'Blue', teamId: 'blue', members: 2 }),
      copy({ target: 'team:red', label: 'Red', teamId: 'red', members: 3, trackerId: 't2', tracker: red, status: 'RUNNING' }),
      copy({ target: 'team:green', label: 'Green', teamId: 'green', members: 1 }),
      copy({ target: 'solo', label: 'Solo copy', solo: true, members: 1 }),
    ] }));
    expect(copyLabels('team:blue')).toEqual(['Start']);
    expect(copyLabels('team:red')).toEqual(['Pause', 'Stop', 'Details']);
    expect(copyLabels('solo')).toEqual(['Start']);
    expect(el().textContent).toContain('3 members');
    api.environmentAction.and.returnValue(of({ successful: true, message: 'Environment started', environment: null }));
    [...el().querySelectorAll<HTMLButtonElement>('.copies-head .btn')].find(b => b.textContent!.includes('Start for every team'))!.click();
    await fixture.whenStable();
    expect(api.environmentAction.calls.allArgs()).toEqual([['lab1', 'start', 'team:blue'], ['lab1', 'start', 'team:green']]);
  });
});
