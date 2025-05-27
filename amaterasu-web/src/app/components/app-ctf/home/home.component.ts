import { Component, OnInit, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, combineLatest, debounceTime, distinctUntilChanged, finalize, map, startWith, takeUntil } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormControl } from '@angular/forms';
import { ApiResponse } from '../../../models/api-response.model';
import { Room, RoomFormData } from '../../../models/ctf/room.model';
import { RoomService } from '../../../services/ctf/room.service';
import { EditDialogService } from '../../../services/edit-dialog.service';

@Component({
  selector: 'amaterasu-ctf-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
  standalone: false
})
export class CTFHomeComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly loadingSubject = new BehaviorSubject<boolean>(false);

  // Observables
  rooms$: Observable<Room[] | undefined>;
  filteredRooms$: Observable<Room[]> | undefined = undefined;
  isLoading$ = this.loadingSubject.asObservable();

  // Form controls
  searchControl = new FormControl('');

  // Component state
  readonly trackByRoomId = (index: number, room: Room): string => room.id || index.toString();

  constructor(
    private roomService: RoomService,
    private editDialogService: EditDialogService,
    private snackBar: MatSnackBar
  ) {
    this.rooms$ = this.roomService.rooms$;
    this.setupFilteredRooms();
  }

  ngOnInit(): void {
    this.loadRooms();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.loadingSubject.complete();
  }

  /**
   * Setup filtered rooms observable with search functionality
   */
  private setupFilteredRooms(): void {
    const search$ = this.searchControl.valueChanges.pipe(
      startWith(''),
      debounceTime(300),
      distinctUntilChanged(),
      map(term => (term || '').toLowerCase().trim())
    );

    this.filteredRooms$ = combineLatest([
      this.rooms$,
      search$
    ]).pipe(
      map(([rooms, searchTerm]) => this.filterRooms(rooms || [], searchTerm)),
      takeUntil(this.destroy$)
    );
  }

  /**
   * Filter rooms based on search term
   */
  private filterRooms(rooms: Room[], searchTerm: string): Room[] {
    if (!searchTerm) {
      return rooms;
    }

    return rooms.filter(room =>
      room.name?.toLowerCase().includes(searchTerm) ||
      room.creator?.toLowerCase().includes(searchTerm) ||
      room.facilitators?.some(facilitator =>
        facilitator.toLowerCase().includes(searchTerm)
      ) ||
      room.surroundTag?.toLowerCase().includes(searchTerm)
    );
  }

  /**
   * Load all rooms from the server
   */
  loadRooms(): void {
    this.setLoading(true);

    this.roomService.getAllRooms()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.setLoading(false))
      )
      .subscribe({
        next: (response) => this.handleRoomsLoaded(response),
        error: (error) => this.handleLoadRoomsError(error)
      });
  }

  /**
   * Handle successful rooms loading
   */
  private handleRoomsLoaded(response: ApiResponse<Room[]>): void {
    if (!response?.data) {
      console.warn('No room data received from server', response);
      this.roomService.addRooms([]);
      return;
    }

    try {
      // Convert server objects to Room instances if needed
      const rooms = Array.isArray(response.data)
        ? response.data.map(roomData => new Room(roomData))
        : [];

      this.roomService.addRooms(rooms);
      console.log(`Successfully loaded ${rooms.length} rooms`);

      if (rooms.length === 0) {
        this.showInfo('No rooms found. Create your first room to get started!');
      }
    } catch (error) {
      console.error('Error processing room data:', error);
      this.showError('Error processing room data. Please refresh the page.');
    }
  }

  /**
   * Handle rooms loading error
   */
  private handleLoadRoomsError(error: any): void {
    console.error('Failed to load rooms:', error);

    const errorMessage = this.getErrorMessage(error);
    this.showError(`Failed to load rooms: ${errorMessage}`);

    // Set empty array on error to prevent template issues
    this.roomService.addRooms([]);
  }

  /**
   * Open room creation dialog
   */
  createRoom(): void {
    if (this.loadingSubject.value) {
      this.showWarning('Please wait for the current operation to complete.');
      return;
    }

    try {
      const roomFormData = new RoomFormData();

      this.editDialogService
        .openDialog<Room>(roomFormData, this.handleRoomCreation.bind(this))
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          error: (error) => {
            console.error('Error with room creation dialog:', error);
            this.showError('Could not process room creation. Please try again.');
          }
        });
    } catch (error) {
      console.error('Error opening room creation dialog:', error);
      this.showError('Could not open room creation dialog. Please try again.');
    }
  }

  /**
   * Handle room creation from dialog
   */
  private handleRoomCreation(room: Room): void {
    if (!room) {
      console.warn('No room data provided for creation');
      return;
    }

    // Validate room data
    if (!room.name?.trim()) {
      this.showError('Room name is required.');
      return;
    }

    this.setLoading(true);

    this.roomService.createRoom(room)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.setLoading(false))
      )
      .subscribe({
        next: (response) => this.handleRoomCreated(response),
        error: (error) => this.handleCreateRoomError(error)
      });
  }

  /**
   * Handle successful room creation
   */
  private handleRoomCreated(response: ApiResponse<Room>): void {
    if (!response?.data) {
      this.showError('Room creation failed: No data returned from server.');
      return;
    }

    try {
      const newRoom = new Room(response.data);
      this.roomService.addNewRoom(newRoom);
      this.showSuccess(`Room "${newRoom.name}" successfully created!`);

      // Clear search to show the new room
      this.searchControl.setValue('');

      console.log('Room successfully created:', newRoom);
    } catch (error) {
      console.error('Error processing created room:', error);
      this.showError('Room was created but there was an error displaying it. Please refresh the page.');
    }
  }

  /**
   * Handle room creation error
   */
  private handleCreateRoomError(error: any): void {
    console.error('Failed to create room:', error);

    const errorMessage = this.getErrorMessage(error);
    this.showError(`Failed to create room: ${errorMessage}`);
  }

  /**
   * Refresh rooms data
   */
  refreshRooms(): void {
    this.loadRooms();
  }

  /**
   * Clear search filter
   */
  clearSearch(): void {
    this.searchControl.setValue('');
  }

  /**
   * Get user-friendly error message
   */
  private getErrorMessage(error: any): string {
    if (error?.error?.message) {
      return error.error.message;
    }
    if (error?.message) {
      return error.message;
    }
    if (error?.status === 0) {
      return 'Network connection error. Please check your internet connection.';
    }
    if (error?.status >= 500) {
      return 'Server error. Please try again later.';
    }
    if (error?.status === 404) {
      return 'Service not found. Please contact support.';
    }
    if (error?.status === 403) {
      return 'Access denied. Please check your permissions.';
    }
    return 'An unexpected error occurred. Please try again.';
  }

  /**
   * Set loading state
   */
  private setLoading(loading: boolean): void {
    this.loadingSubject.next(loading);
  }

  /**
   * Show success message
   */
  private showSuccess(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 4000,
      panelClass: ['success-snackbar'],
      horizontalPosition: 'end',
      verticalPosition: 'top'
    });
  }

  /**
   * Show error message
   */
  private showError(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 6000,
      panelClass: ['error-snackbar'],
      horizontalPosition: 'end',
      verticalPosition: 'top'
    });
  }

  /**
   * Show warning message
   */
  private showWarning(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 4000,
      panelClass: ['warning-snackbar'],
      horizontalPosition: 'end',
      verticalPosition: 'top'
    });
  }

  /**
   * Show info message
   */
  private showInfo(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 3000,
      panelClass: ['info-snackbar'],
      horizontalPosition: 'end',
      verticalPosition: 'top'
    });
  }
}
