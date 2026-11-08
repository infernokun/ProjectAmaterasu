import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ToastService } from '../../../shared/services/toast.service';
import { PlayApiService } from '../data/play-api.service';

/** Saves a lab file in the browser. */
@Injectable({ providedIn: 'root' })
export class LabFileDownload {
  private readonly api = inject(PlayApiService);
  private readonly toast = inject(ToastService);

  async save(labId: string, fileId: string, name: string): Promise<void> {
    try {
      const blob = await firstValueFrom(this.api.downloadFile(labId, fileId));
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = name.slice(name.lastIndexOf('/') + 1);
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      this.toast.error(`${name} could not be downloaded.`, error);
    }
  }
}

export function fileSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return '';
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : bytes >= 1024 ? `${Math.round(bytes / 1024)} KB` : `${bytes} B`;
}
