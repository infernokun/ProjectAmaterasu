import { Component, OnInit, OnDestroy, ViewEncapsulation } from '@angular/core';
import { Subject, takeUntil, switchMap, filter, tap, catchError, of, EMPTY, Observable } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ApiResponse } from '../../../../models/api-response.model';
import { CTFEntity } from '../../../../models/ctf/ctf-entity.model';
import { AuthService } from '../../../../services/auth.service';
import { CTFService } from '../../../../services/ctf/ctf.service';
import { WebsocketService } from '../../../../services/websocket.service';
import { EditDialogService } from '../../../../services/edit-dialog.service';

interface CategoryGroup {
  category: string;
  challenges: CTFEntity[];
}

@Component({
  selector: 'amaterasu-ctf-card',
  templateUrl: './ctf-card.component.html',
  styleUrl: './ctf-card.component.scss',
  encapsulation: ViewEncapsulation.None,
  standalone: false
})
export class CTFCardComponent implements OnInit, OnDestroy {
  public challenges: CTFEntity[] = [];
  public categorizedChallenges: CategoryGroup[] = [];
  public busy = false;
  public error: string | null = null;

  // Use service loading state directly
  loading$: Observable<Boolean> | undefined;

  // Subject for handling component destruction
  private destroy$ = new Subject<void>();

  private difficultyOrder = { 'easy': 1, 'medium': 2, 'hard': 3 };

  constructor(
    private ctfService: CTFService,
    private webSocketService: WebsocketService,
    private authService: AuthService,
    private route: ActivatedRoute,
    private dialogService: EditDialogService
  ) {
    this.loading$ = this.ctfService.loading$;
  }

  ngOnInit(): void {
    this.initializeComponent();
    //this.subscribeToWebSocketMessages();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeComponent(): void {
    this.busy = true;
    this.error = null;

    this.authService.loading$.subscribe(loading => {
      console.log('Auth loading state:', loading);
    });

    this.authService.isAuthenticated()
      .pipe(
        filter(isAuth => isAuth === true),
        switchMap(() => this.route.params),
        filter(params => !!params['room']),
        switchMap(params => this.loadChallenges(params['room'])),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (challenges) => {
          this.challenges = challenges;
          this.categorizedChallenges = this.categorizeAndSortChallenges(challenges);
          this.busy = false;
        },
        error: (error) => {
          console.error('Error loading challenges:', error);
          this.error = 'Failed to load challenges. Please try again.';
          this.busy = false;
        }
      });
  }

  private loadChallenges(roomId: string) {
    return this.ctfService.getChallengesByRoom(roomId)
      .pipe(
        tap(() => this.ctfService.loadingSubject.next(true)),
        catchError(error => {
          this.ctfService.loadingSubject.next(false);
          throw error;
        }),
        filter((response: ApiResponse<CTFEntity[]>) => !!response?.data),
        tap(() => this.ctfService.loadingSubject.next(false))
      )
      .pipe(
        // Extract data from response
        switchMap((response: ApiResponse<CTFEntity[]>) =>
          response.data ? of(response.data) : EMPTY
        )
      );
  }
  private categorizeAndSortChallenges(challenges: CTFEntity[]): CategoryGroup[] {
    // Group challenges by category
    const grouped = challenges.reduce((acc, challenge) => {
      const category = challenge.category || 'Uncategorized';
      if (!acc[category]) {
        acc[category] = [];
      }
      acc[category].push(challenge);
      return acc;
    }, {} as { [key: string]: CTFEntity[] });

    // Convert to array and sort
    return Object.keys(grouped)
      .sort() // Sort categories alphabetically
      .map(category => ({
        category,
        challenges: grouped[category].sort((a, b) => {
          // Sort by difficulty first (easy -> medium -> hard)
          const diffA = this.difficultyOrder[a.difficultyLevel?.toLowerCase() as keyof typeof this.difficultyOrder] || 999;
          const diffB = this.difficultyOrder[b.difficultyLevel?.toLowerCase() as keyof typeof this.difficultyOrder] || 999;

          if (diffA !== diffB) {
            return diffA - diffB;
          }

          // Then sort by points (ascending)
          return (a.points || 0) - (b.points || 0);
        })
      }));
  }

  public getTotalChallenges(): number {
    return this.categorizedChallenges.reduce((total, group) => total + group.challenges.length, 0);
  }

  private subscribeToWebSocketMessages(): void {
    this.webSocketService.getMessage()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (message) => {
          console.log('WebSocket message received:', message);
          // Handle websocket message logic here
          this.handleWebSocketMessage(message);
        },
        error: (error) => {
          console.error('WebSocket error:', error);
        }
      });
  }

  private handleWebSocketMessage(message: any): void {
    // Add your websocket message handling logic here
    // For example, refresh challenges if needed
    // if (message.type === 'CHALLENGE_UPDATED') {
    //   this.refreshChallenges();
    // }
  }

  public openViewDialog(challenge: CTFEntity): void {
    if (!challenge) {
      console.warn('No challenge provided for view dialog');
      return;
    }

    this.dialogService.openViewDialog(challenge)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          console.log('View dialog result:', result);
          // Handle dialog result if needed
        },
        error: (error) => {
          console.error('Error opening view dialog:', error);
        }
      });
  }

  public openEditDialog(challenge: CTFEntity): void {
    if (!challenge) {
      console.warn('No challenge provided for edit dialog');
      return;
    }

    /*this.dialogService.openEditDialog(challenge)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          console.log('Edit dialog result:', result);
          // Handle dialog result - maybe refresh the challenge list
          if (result && result.updated) {
            this.refreshChallenges();
          }
        },
        error: (error) => {
          console.error('Error opening edit dialog:', error);
        }
      });*/
  }

  public refreshChallenges(): void {
    const currentRoom = this.route.snapshot.params['room'];
    if (currentRoom) {
      this.busy = true;
      this.loadChallenges(currentRoom)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (challenges) => {
            this.challenges = challenges;
            this.categorizedChallenges = this.categorizeAndSortChallenges(challenges);
            this.busy = false;
          },
          error: (error) => {
            console.error('Error refreshing challenges:', error);
            this.error = 'Failed to refresh challenges.';
            this.busy = false;
          }
        });
    }
  }

  public retry(): void {
    this.initializeComponent();
  }
}
