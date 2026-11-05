import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ConfirmService } from '../services/confirm.service';
import { ConfirmResult } from './confirm-dialog.component';

describe('ConfirmService', () => {
  let confirm: ConfirmService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }] });
    confirm = TestBed.inject(ConfirmService);
  });

  afterEach(() => TestBed.inject(MatDialog).closeAll());

  const surface = () => document.querySelector('app-confirm-dialog') as HTMLElement;
  const button = (label: string) =>
    Array.from(surface().querySelectorAll('button')).find(b => b.textContent?.trim() === label) as HTMLButtonElement;

  function open(options: Parameters<ConfirmService['open']>[0]): Promise<ConfirmResult> {
    const result = firstValueFrom(confirm.open(options));
    TestBed.tick();
    return result;
  }

  it('names what is lost and offers the safer choice', async () => {
    const result = open({
      title: 'Stop Web Exploitation 101?',
      message: 'Containers, volumes and uploaded files are removed.',
      confirmLabel: 'Stop and destroy',
      alternativeLabel: 'Pause instead',
    });
    expect(surface().textContent).toContain('Containers, volumes and uploaded files are removed.');
    expect(button('Stop and destroy').classList).toContain('danger');
    button('Pause instead').click();
    expect(await result).toBe('alternative');
  });

  it('waits for the typed name before allowing the action', async () => {
    const result = open({ title: 'Delete Red Fox?', message: 'Gone.', confirmLabel: 'Delete team', typeToConfirm: 'Red Fox' });
    const confirmButton = button('Delete team');
    expect(confirmButton.disabled).toBeTrue();

    const input = surface().querySelector('input') as HTMLInputElement;
    input.value = 'Red Fox';
    input.dispatchEvent(new Event('input'));
    TestBed.tick();
    expect(confirmButton.disabled).toBeFalse();
    confirmButton.click();
    expect(await result).toBe('confirm');
  });

  it('treats Cancel as cancel', async () => {
    const result = open({ title: 'Disable jmorales?', message: 'Signed out.', confirmLabel: 'Disable account' });
    button('Cancel').click();
    expect(await result).toBe('cancel');
  });
});
