import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LabStatus } from '../../enums/lab-status.enum';
import { LAB_STATE_META } from '../lab-status';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent', () => {
  let fixture: ComponentFixture<StatusBadgeComponent>;
  const chip = () => (fixture.nativeElement as HTMLElement).querySelector('.chip')!;

  beforeEach(() => {
    fixture = TestBed.createComponent(StatusBadgeComponent);
  });

  it('labels every LabStatus', () => {
    for (const status of Object.values(LabStatus)) {
      expect(LAB_STATE_META[status]?.label).withContext(status).toBeTruthy();
    }
  });

  it('shows a lab state with its color and a dot', () => {
    fixture.componentRef.setInput('state', LabStatus.PAUSED);
    fixture.detectChanges();
    expect(chip().textContent?.trim()).toBe('Paused');
    expect(chip().classList).toContain('s-paused');
    expect(chip().querySelector('.dot')).toBeTruthy();
  });

  it('shows a spinner for a request in flight', () => {
    fixture.componentRef.setInput('state', 'STOPPING');
    fixture.detectChanges();
    expect(chip().textContent?.trim()).toBe('Stopping');
    expect(chip().classList).toContain('s-work');
    expect(chip().querySelector('.dot')).toBeNull();
  });

  it('takes a custom tone and label', () => {
    fixture.componentRef.setInput('tone', 'bad');
    fixture.componentRef.setInput('label', '1 down');
    fixture.detectChanges();
    expect(chip().textContent?.trim()).toBe('1 down');
    expect(chip().classList).toContain('s-bad');
  });
});
