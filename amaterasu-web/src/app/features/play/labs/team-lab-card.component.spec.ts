import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { PlayApiService } from '../data/play-api.service';
import { LabActionsService } from './lab-actions.service';
import { TeamLabCardComponent } from './team-lab-card.component';

describe('TeamLabCardComponent', () => {
  let fixture: ComponentFixture<TeamLabCardComponent>;
  let actions: jasmine.SpyObj<LabActionsService>;
  let state: string;
  const tracker = new LabTracker({
    id: 'lt1', labStatus: LabStatus.ACTIVE, labStarted: { name: 'Web Exploitation 101', labType: LabType.DOCKER_COMPOSE },
    remoteServer: { name: 'dock-01' }, services: [{ name: 'lt1-web-1', state: 'running' }],
  });

  beforeEach(() => {
    state = LabStatus.ACTIVE;
    actions = jasmine.createSpyObj<LabActionsService>('LabActionsService', ['pause', 'stop', 'resume', 'remove', 'restart']);
    TestBed.configureTestingModule({
      imports: [TeamLabCardComponent],
      providers: [
        provideRouter([]),
        { provide: LabActionsService, useValue: actions },
        { provide: LabTrackersStore, useValue: { displayState: () => () => state } },
        { provide: PlayApiService, useValue: { access: () => of({}) } },
      ],
    });
    fixture = TestBed.createComponent(TeamLabCardComponent);
    fixture.componentRef.setInput('tracker', tracker);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) => Array.from(el().querySelectorAll('button')).find(b => b.textContent?.trim() === label);

  it('asks before stopping and offers Pause instead', () => {
    button('Stop')!.click();
    fixture.detectChanges();
    expect(el().textContent).toContain('Containers, volumes and uploaded files are removed.');
    button('Pause instead')!.click();
    expect(actions.pause).toHaveBeenCalledWith(tracker);
    expect(actions.stop).not.toHaveBeenCalled();
  });

  it('shows a request in flight on the card', () => {
    state = 'PAUSING';
    fixture.componentRef.setInput('tracker', new LabTracker({ ...tracker }));
    fixture.detectChanges();
    expect(el().textContent).toContain('Pausing on dock-01');
    expect(button('Pause')).toBeUndefined();
  });

  describe('with a bastion', () => {
    const vmLab = new LabTracker({
      id: 'lt2', labStatus: LabStatus.ACTIVE, labStarted: { name: 'Pivot', labType: LabType.VIRTUAL_MACHINE },
      remoteServer: { name: 'pve-a' }, bridges: ['abr0'],
      vms: [
        { vmid: 105, name: 'bastion', status: 'running', role: 'bastion', ciuser: 'player', guestAgent: true, ready: true,
          accessAddress: '10.0.0.42' },
        { vmid: 106, name: 'target', status: 'running', guestAgent: true, ready: false },
      ],
    });

    it('shows SSH access on the card while the lab runs', () => {
      fixture.componentRef.setInput('tracker', vmLab);
      fixture.detectChanges();
      expect(el().textContent).toContain('SSH access');
      expect(el().textContent).toContain('ssh player@10.0.0.42');
      expect(el().textContent).toContain('2 of 2 VMs running, 1 starting up. SSH in through the bastion');
      expect(el().textContent).not.toContain('ssh -J');
    });

    it('drops it while paused', () => {
      state = LabStatus.PAUSED;
      fixture.componentRef.setInput('tracker', new LabTracker({ ...vmLab, labStatus: LabStatus.PAUSED }));
      fixture.detectChanges();
      expect(el().textContent).not.toContain('SSH access');
    });
  });
});
