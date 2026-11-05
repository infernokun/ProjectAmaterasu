import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { errorMessage } from '../../core/http-error';
import { ToastComponent, ToastData } from '../ui/toast.component';

/** Short confirmations and errors in the bottom right corner. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snackBar = inject(MatSnackBar);

  show(message: string, tone: ToastData['tone'] = 'ok', duration = 3600): void {
    this.snackBar.openFromComponent(ToastComponent, {
      data: { message, tone },
      duration,
      horizontalPosition: 'end',
      verticalPosition: 'bottom',
      panelClass: ['ama-toast', tone],
      politeness: tone === 'bad' ? 'assertive' : 'polite',
    });
  }

  success(message: string): void {
    this.show(message, 'ok');
  }

  info(message: string): void {
    this.show(message, 'info');
  }

  /** Shows the API's message when the error carries one, otherwise `fallback`. */
  error(fallback: string, error?: unknown): void {
    this.show(errorMessage(error) ?? fallback, 'bad', 6000);
  }
}
