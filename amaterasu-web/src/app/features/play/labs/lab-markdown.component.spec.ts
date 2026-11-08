import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LabStatus } from '../../../enums/lab-status.enum';
import { PlayApiService } from '../data/play-api.service';
import { EnvironmentView } from '../data/play.models';
import { LabMarkdownComponent } from './lab-markdown.component';

describe('LabMarkdownComponent', () => {
  let fixture: ComponentFixture<LabMarkdownComponent>;
  const scenario = '## Getting in\nConnect with `ssh {{username}}@{{address}}`. The password is {{password}}. Net {{subnet}}.\n\n<script>alert(1)</script>';
  const env = (labStatus: LabStatus): EnvironmentView => ({
    labId: 'def1', labName: 'Hunt', labType: 'VIRTUAL_MACHINE', mode: 'SHARED', status: 'RUNNING', trackerId: 't1',
    tracker: { id: 't1', labStatus, vms: [{ vmid: 1, status: 'running', role: 'bastion', ciuser: 'player', accessAddress: '10.0.0.77', labSubnet: '172.16.0.0/24' }] },
    canControl: false, note: null, copiesRunning: 1,
  });

  function render(environment: EnvironmentView | null, canSeeLogin: boolean) {
    TestBed.configureTestingModule({
      imports: [LabMarkdownComponent],
      providers: [{ provide: PlayApiService, useValue: jasmine.createSpyObj('PlayApiService', ['access']) }],
    });
    fixture = TestBed.createComponent(LabMarkdownComponent);
    fixture.componentRef.setInput('source', scenario);
    fixture.componentRef.setInput('environment', environment);
    fixture.componentRef.setInput('canSeeLogin', canSeeLogin);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  it('fills placeholders from the running environment and masks the password', () => {
    render(env(LabStatus.ACTIVE), true);
    expect(el().querySelector('h3')!.textContent).toBe('Getting in');
    expect(el().querySelector('code')!.textContent).toBe('ssh player@10.0.0.77');
    expect(el().textContent).toContain('172.16.0.0/24');
    expect(el().querySelector('[data-testid="password"]')!.textContent).toContain('••••');
    expect(el().querySelector('script')).toBeNull();
    expect(el().textContent).toContain('<script>alert(1)</script>');
  });

  it('says when values come while the environment is down', () => {
    render(env(LabStatus.STOPPED), true);
    expect(el().textContent).toContain('The password is available once the environment is running');
    expect(el().querySelector('code')!.textContent).toBe('ssh ‹username›@‹address›');
    expect(el().querySelector('code .ph-tag')!.getAttribute('title')).toBe('available once the environment is running');
    expect(el().querySelector('[data-testid="password"]')).toBeNull();
  });
});
