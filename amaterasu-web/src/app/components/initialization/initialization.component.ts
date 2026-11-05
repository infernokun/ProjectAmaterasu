import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { errorMessage } from '../../core/http-error';
import { ApplicationInfo } from '../../models/application-info.model';
import { AppInitService } from '../../services/app-init.service';
import { IconComponent } from '../../shared/ui/icon.component';

/** First run: names this installation. */
@Component({
  selector: 'amaterasu-initialization',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './initialization.component.html',
  styleUrl: './initialization.component.scss',
  imports: [ReactiveFormsModule, IconComponent],
})
export class InitializationComponent {
  private readonly appInitService = inject(AppInitService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    description: ['', [Validators.maxLength(200)]],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  private readonly initialized = toSignal(this.appInitService.isInitialized(), { initialValue: false });

  constructor() {
    effect(() => {
      if (this.initialized()) {
        this.router.navigate(['/home']);
      }
    });
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) {
      return;
    }
    const { name, description } = this.form.getRawValue();
    const info = new ApplicationInfo({ name: name.trim(), description: description.trim(), settings: '{}' });
    this.busy.set(true);
    this.error.set('');
    this.appInitService.createApplicationInfo(info)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: response => {
          this.appInitService.applicationInfo$.next(new ApplicationInfo(response.data));
          this.appInitService.setInitialized(true);
        },
        error: error => this.error.set(errorMessage(error) ?? 'Setup could not be saved. Try again.'),
      });
  }

  protected nameMissing(): boolean {
    const control = this.form.controls.name;
    return control.invalid && control.touched;
  }
}
