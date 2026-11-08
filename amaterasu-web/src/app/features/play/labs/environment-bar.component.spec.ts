import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LabStatus } from '../../../enums/lab-status.enum';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { PlayApiService } from '../data/play-api.service';
import { EnvironmentView } from '../data/play.models';
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
  const view = (patch: Partial<EnvironmentView> = {}): EnvironmentView => ({
    labId: 'def1', labName: 'Hunt', labType: 'VIRTUAL_MACHINE', mode: 'SHARED', status: 'NOT_RUNNING', trackerId: null,
    tracker: null, canControl: true, note: null, copiesRunning: 0, ...patch,
  });

  function render(environment: EnvironmentView, joined = true, canRun = true) {
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
    fixture.componentRef.setInput('joined', joined);
    fixture.componentRef.setInput('canRun', canRun);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const labels = () => [...el().querySelectorAll('.actions .btn')].map(b => b.textContent!.trim());

  it('offers a facilitator Start, and shows Starting while it runs', async () => {
    render(view());
    expect(el().textContent).toContain('Not running');
    expect(labels()).toEqual(['Start']);
    api.environmentAction.and.returnValue(of({ successful: true, message: 'Environment started',
      environment: view({ trackerId: 't1', tracker: running, status: 'RUNNING' }) }));
    const emitted: EnvironmentView[] = [];
    fixture.componentInstance.changed.subscribe(env => emitted.push(env));
    [...el().querySelectorAll<HTMLButtonElement>('.actions .btn')][0].click();
    await fixture.whenStable();
    expect(api.environmentAction).toHaveBeenCalledWith('lab1', 'start');
    expect(emitted[0].status).toBe('RUNNING');
  });

  it('gives a running copy Open console, Stop and the login line', () => {
    render(view({ trackerId: 't1', tracker: running, status: 'RUNNING' }));
    expect(el().textContent).toContain('Running');
    expect(labels()).toEqual(['Open console', 'Stop', 'Details']);
    expect(el().textContent).toContain('ssh player@10.0.0.77');
    expect(el().querySelector('[data-testid="password"]')!.textContent).toContain('••••');
  });

  it('asks for the name before stopping and can pause instead', async () => {
    render(view({ trackerId: 't1', tracker: running, status: 'RUNNING' }));
    confirm.open.and.returnValue(of('alternative'));
    api.environmentAction.and.returnValue(of({ successful: true, message: 'Environment paused', environment: null }));
    [...el().querySelectorAll<HTMLButtonElement>('.actions .btn')].find(b => b.textContent!.includes('Stop'))!.click();
    await fixture.whenStable();
    expect(confirm.open).toHaveBeenCalledWith(jasmine.objectContaining({ typeToConfirm: 'Hunt' }));
    expect(api.environmentAction).toHaveBeenCalledWith('lab1', 'pause');
  });

  it('gives a player no controls on a shared copy, and keeps the login for those who joined', () => {
    render(view({ trackerId: 't1', tracker: running, status: 'RUNNING', canControl: false,
      note: 'A facilitator starts and stops this environment for everyone.' }), false, false);
    expect(labels()).toEqual([]);
    expect(el().textContent).toContain('A facilitator starts and stops');
    expect(el().textContent).toContain('Join the lab to see how to get in.');
  });
});
