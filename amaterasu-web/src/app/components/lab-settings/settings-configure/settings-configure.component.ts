import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { Subject } from 'rxjs';
import { ServerType } from '../../../enums/server-type.enum';
import { LabTracker } from '../../../models/lab-tracker.model';
import { ComposeFile, Volume } from '../lab-settings.component';

export interface VolumeChange {
  serviceName: string;
  index: number;
  targetPath: string;
  newFile: File | null;
  isDirectory: boolean;
}

@Component({
  selector: 'app-settings-configure',
  standalone: false,
  templateUrl: './settings-configure.component.html',
  styleUrl: './settings-configure.component.scss',
})
export class SettingsConfigureComponent {
  // Existing properties
  private readonly destroy$ = new Subject<void>();
  labId: string = '';
  labName: string = '';
  admin = false;
  @Input() ymlData?: ComposeFile;
  @Input() labTrackerId?: string;
  ServerType = ServerType;

  // New properties for volume editing
  isEditMode = false;
  volumeChanges = new Map<string, VolumeChange>(); // Key is "serviceName-index"
  currentEditingService?: string;
  currentEditingIndex?: number;
  currentUploadIsDirectory = false;

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  ngOnInit() {}

  getServiceEntries(): Array<{
    name: string;
    image?: string;
    volumes?: string[];
  }> {
    if (!this.ymlData?.services) return [];

    return Object.entries(this.ymlData.services).map(([name, service]) => ({
      name,
      image: service.image,
      volumes: service.volumes as string[],
    }));
  }

  // New methods for volume editing functionality

  browseForVolume(
    serviceName: string,
    index: number,
    isDirectory = true
  ): void {
    this.currentEditingService = serviceName;
    this.currentEditingIndex = index;
    this.currentUploadIsDirectory = isDirectory;

    // Check if this is likely a file or directory based on the path
    const serviceEntry = this.getServiceEntries().find(
      (entry) => entry.name === serviceName
    );
    if (serviceEntry?.volumes && serviceEntry.volumes[index]) {
      const volumePath = serviceEntry.volumes[index];
      // If the path doesn't end with a trailing slash, it might be a file
      this.currentUploadIsDirectory = volumePath.endsWith('/');
    }

    // Trigger file input click
    setTimeout(() => {
      this.fileInput.nativeElement.click();
    });
  }

  handleFileSelection(event: Event): void {
    const input = event.target as HTMLInputElement;

    if (
      !this.currentEditingService ||
      this.currentEditingIndex === undefined ||
      !input.files ||
      input.files.length === 0
    ) {
      return;
    }

    const selectedFile = input.files[0];
    const serviceEntry = this.getServiceEntries().find(
      (entry) => entry.name === this.currentEditingService
    );

    if (
      serviceEntry?.volumes &&
      serviceEntry.volumes[this.currentEditingIndex]
    ) {
      const volumeMapping = serviceEntry.volumes[this.currentEditingIndex];
      const targetPath = this.extractTargetPath(volumeMapping);
      const isDirectory = this.isTargetPathDirectory(targetPath);
      const changeKey = `${this.currentEditingService}-${this.currentEditingIndex}`;

      this.volumeChanges.set(changeKey, {
        serviceName: this.currentEditingService,
        index: this.currentEditingIndex,
        targetPath,
        newFile: selectedFile,
        isDirectory,
      });
    }

    // Reset input so the same file can be selected again
    input.value = '';
  }

  private isTargetPathDirectory(volume: string): boolean {
    const parts = volume.split(':');
    const target = parts.length > 1 ? parts[1] : '';

    // Heuristic: if target ends with slash or doesn't contain a file extension
    return !target || !target.split('/').pop()?.includes('.');
  }

  private extractTargetPath(volume: string): string {
    const parts = volume.split(':');
    return parts.length >= 2 ? parts[1] : ''; // source:target[:ro]
  }

  isVolumeModified(serviceName: string, index: number): boolean {
    return this.volumeChanges.has(`${serviceName}-${index}`);
  }

  removeVolumeChange(serviceName: string, index: number): void {
    this.volumeChanges.delete(`${serviceName}-${index}`);
  }

  getVolumeChanges(): VolumeChange[] {
    return Array.from(this.volumeChanges.values());
  }

  get hasChanges(): boolean {
    return this.volumeChanges.size > 0;
  }

  saveVolumeChanges(): void {
    // Prepare data for submission
    const changes: VolumeChange[] = Array.from(this.volumeChanges.values());
    const formData = new FormData();

    formData.append('labTrackerId', this.labTrackerId ?? '');

    // Add volume change metadata as a JSON blob
    const metadata = changes.map((change) => ({
      serviceName: change.serviceName,
      index: change.index,
      targetPath: change.targetPath,
      isDirectory: change.isDirectory,
      fileName: change.newFile?.name
    }));

    formData.append(
      'volumeChanges',
      new Blob([JSON.stringify(metadata)], { type: 'application/json' })
    );

    changes.forEach((change, idx) => {
      if (change.newFile) {
        // Ensure the file field is predictable and matches backend expectations
        formData.append(`file-${idx}`, change.newFile);
      }
    });

    console.log(formData);

    // Call your backend service
    /*this.labTrackerService.uploadVolumeFiles(formData)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          console.log('Files uploaded successfully', response);
          // Clear the changes after successful upload
          this.volumeChanges.clear();
          this.isEditMode = false;
          
          // Optionally reload the lab tracker to reflect changes
          this.loadLabTracker();
        },
        error: (error) => {
          console.error('Error uploading files', error);
          // Handle error (show message, etc.)
        }
      });*/
  }
}
