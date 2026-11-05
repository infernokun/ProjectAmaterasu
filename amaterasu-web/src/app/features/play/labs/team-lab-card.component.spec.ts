import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
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
});
