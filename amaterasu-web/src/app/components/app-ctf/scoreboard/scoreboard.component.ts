import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, BehaviorSubject, Subject, combineLatest, of } from 'rxjs';
import { takeUntil, startWith, debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';
import { RoomService } from '../../../services/ctf/room.service';
import { ApiResponse } from '../../../models/api-response.model';
import { RoomUserResponse, PointsHistoryEntry } from '../../../models/ctf/room-user-response.model';

interface ScoreboardUser {
  username: string;
  points: number;
  rank: number;
  pointsHistory: PointsHistoryEntry[];
}

interface ChartDataPoint {
  timestamp: Date;
  points: number;
  username: string;
}

@Component({
  selector: 'app-scoreboard',
  standalone: false,
  templateUrl: './scoreboard.component.html',
  styleUrl: './scoreboard.component.scss'
})
export class ScoreboardComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly loadingSubject = new BehaviorSubject<boolean>(false);
  
  // Observables
  roomUsers$: Observable<RoomUserResponse[]> = of([]);
  scoreboardUsers$: Observable<ScoreboardUser[]> = of([]);
  filteredUsers$: Observable<ScoreboardUser[]> = of([]);
  chartData$: Observable<ChartDataPoint[]> = of([]);
  isLoading$ = this.loadingSubject.asObservable();
  
  // Form controls
  searchControl = new FormControl('');
  timeRangeControl = new FormControl('7d'); // 1d, 7d, 30d, all
  
  // Component state
  readonly trackByUsername = (index: number, user: ScoreboardUser): string => user.username || index.toString();
  currentRoomId = signal<string | null>(null);
  showChart = signal<boolean>(false);
  selectedUser = signal<string | null>(null);
  
  // Chart options
  chartOptions = {
    responsive: true,
    plugins: {
      title: {
        display: true,
        text: 'Points Over Time'
      },
      legend: {
        display: true,
        position: 'top' as const
      }
    },
    scales: {
      x: {
        type: 'time' as const,
        time: {
          unit: 'day' as const
        },
        title: {
          display: true,
          text: 'Date'
        }
      },
      y: {
        title: {
          display: true,
          text: 'Points'
        },
        beginAtZero: true
      }
    },
    interaction: {
      intersect: false,
      mode: 'index' as const
    }
  };
  
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackBar: MatSnackBar,
    private roomService: RoomService
  ) {
    this.setupObservables();
  }
  
  ngOnInit(): void {
    const roomId = this.route.snapshot.params['room'];
    this.currentRoomId.set(roomId);
    this.loadScoreboard(roomId);
  }
  
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.loadingSubject.complete();
  }
  
  private setupObservables(): void {
    // Setup room users observable
    this.roomUsers$ = this.currentRoomId() ? of([]) : 
      this.roomService.getRoomUsersForScoreboard(this.currentRoomId()!).pipe(
        map((response: ApiResponse<RoomUserResponse[]>) => response.data || []),
        tap(users => console.log('Room users loaded:', users)),
        takeUntil(this.destroy$)
      );
    
    // Transform room users to scoreboard users with ranking
    this.scoreboardUsers$ = this.roomUsers$.pipe(
      map(users => this.transformToScoreboardUsers(users)),
      takeUntil(this.destroy$)
    );
    
    // Setup filtered users with search
    const search$ = this.searchControl.valueChanges.pipe(
      startWith(''),
      debounceTime(300),
      distinctUntilChanged(),
      map(term => (term || '').toLowerCase().trim())
    );
    
    this.filteredUsers$ = combineLatest([
      this.scoreboardUsers$,
      search$
    ]).pipe(
      map(([users, searchTerm]) => this.filterUsers(users, searchTerm)),
      takeUntil(this.destroy$)
    );
    
    // Setup chart data
    this.chartData$ = combineLatest([
      this.scoreboardUsers$,
      this.timeRangeControl.valueChanges.pipe(startWith('7d')),
      this.selectedUser()! ? of(null) : of(this.selectedUser())
    ]).pipe(
      map(([users, timeRange, selectedUser]) => 
        this.generateChartData(users, timeRange!, selectedUser)
      ),
      takeUntil(this.destroy$)
    );
  }
  
  private loadScoreboard(roomId: string): void {
    if (!roomId) return;
    
    this.loadingSubject.next(true);
    
    this.roomService.getRoomUsersForScoreboard(roomId).subscribe({
      next: (response: ApiResponse<RoomUserResponse[]>) => {
        console.log('Scoreboard data loaded:', response);
        this.loadingSubject.next(false);
        
        // Update the observable by re-setting up with new data
        this.roomUsers$ = of(response.data || []);
        this.scoreboardUsers$ = this.roomUsers$.pipe(
          map(users => this.transformToScoreboardUsers(users))
        );
        this.setupFilteredObservables();
      },
      error: (error) => {
        this.loadingSubject.next(false);
        this.handleError('Failed to load scoreboard', error);
      }
    });
  }
  
  private setupFilteredObservables(): void {
    const search$ = this.searchControl.valueChanges.pipe(
      startWith(''),
      debounceTime(300),
      distinctUntilChanged(),
      map(term => (term || '').toLowerCase().trim())
    );
    
    this.filteredUsers$ = combineLatest([
      this.scoreboardUsers$,
      search$
    ]).pipe(
      map(([users, searchTerm]) => this.filterUsers(users, searchTerm)),
      takeUntil(this.destroy$)
    );
  }
  
  private transformToScoreboardUsers(roomUsers: RoomUserResponse[]): ScoreboardUser[] {
    // Sort by points descending
    const sorted = [...roomUsers].sort((a, b) => (b.points || 0) - (a.points || 0));
    
    // Add ranking with tie handling
    return sorted.map((user, index) => {
      let rank = index + 1;
      
      // Handle tied scores
      if (index > 0 && sorted[index - 1].points === user.points) {
        // Find the rank of the first user with this score
        for (let i = 0; i < index; i++) {
          if (sorted[i].points === user.points) {
            rank = i + 1;
            break;
          }
        }
      }
      
      return {
        username: user.username || 'Unknown',
        points: user.points || 0,
        rank: rank,
        pointsHistory: user.pointsHistory ? [user.pointsHistory] : []
      };
    });
  }
  
  private filterUsers(users: ScoreboardUser[], searchTerm: string): ScoreboardUser[] {
    if (!searchTerm) return users;
    
    return users.filter(user =>
      user.username.toLowerCase().includes(searchTerm) ||
      user.points.toString().includes(searchTerm)
    );
  }
  
  private generateChartData(users: ScoreboardUser[], timeRange: string, selectedUser: string | null): ChartDataPoint[] {
    const now = new Date();
    let startDate: Date;
    
    // Calculate start date based on time range
    switch (timeRange) {
      case '1d':
        startDate = new Date(now.getTime() - 24 * 60 * 60 * 1000);
        break;
      case '7d':
        startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        break;
      case '30d':
        startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        break;
      default:
        startDate = new Date(0); // All time
    }
    
    const chartData: ChartDataPoint[] = [];
    
    // Filter users if specific user selected
    const usersToShow = selectedUser ? 
      users.filter(u => u.username === selectedUser) : 
      users.slice(0, 5); // Show top 5 users
    
    usersToShow.forEach(user => {
      user.pointsHistory.forEach(history => {
        if (history.timestamp && history.timestamp >= startDate) {
          chartData.push({
            timestamp: history.timestamp,
            points: history.totalPoints || 0,
            username: user.username
          });
        }
      });
    });
    
    return chartData.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
  }
  
  // UI Methods
  toggleChart(): void {
    this.showChart.set(!this.showChart());
  }
  
  selectUserForChart(username: string): void {
    const current = this.selectedUser();
    this.selectedUser.set(current === username ? null : username);
  }
  
  goBackToRoom(): void {
    const roomId = this.currentRoomId();
    if (roomId) {
      this.router.navigate(['/room', roomId]);
    }
  }
  
  refreshScoreboard(): void {
    const roomId = this.currentRoomId();
    if (roomId) {
      this.loadScoreboard(roomId);
    }
  }
  
  // Utility methods
  getRankClass(rank: number): string {
    switch (rank) {
      case 1: return 'first-place';
      case 2: return 'second-place';
      case 3: return 'third-place';
      default: return '';
    }
  }
  
  getPointsChange(user: ScoreboardUser): number {
    if (!user.pointsHistory || user.pointsHistory.length === 0) return 0;
    return user.pointsHistory[user.pointsHistory.length - 1].pointsChange || 0;
  }
  
  getLastActivity(user: ScoreboardUser): Date | null {
    if (!user.pointsHistory || user.pointsHistory.length === 0) return null;
    return user.pointsHistory[user.pointsHistory.length - 1].timestamp || null;
  }
  
  private handleError(message: string, error: any): void {
    console.error(message, error);
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }
}