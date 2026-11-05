import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_SNACK_BAR_DATA } from '@angular/material/snack-bar';
import { IconComponent } from './icon.component';

export interface ToastData {
  message: string;
  tone: 'ok' | 'info' | 'bad';
}

@Component({
  selector: 'app-toast',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <app-icon [name]="data.tone === 'bad' ? 'alert' : data.tone === 'info' ? 'bell' : 'check'" />
    <span>{{ data.message }}</span>
  `,
  styles: `:host { display: flex; align-items: center; gap: 10px; font-weight: 500; }`,
})
export class ToastComponent {
  protected readonly data = inject<ToastData>(MAT_SNACK_BAR_DATA);
}
