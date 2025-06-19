import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, BehaviorSubject, Subject, combineLatest, of } from 'rxjs';
import { takeUntil, startWith, debounceTime, distinctUntilChanged, map, tap } from 'rxjs/operators';
import { ChartConfiguration, ChartData, ChartType } from 'chart.js';
import { RoomService } from '../../../services/ctf/room.service';
import { ApiResponse } from '../../../models/api-response.model';
import { RoomUserResponse, PointsHistoryEntry } from '../../../models/ctf/room-user-response.model';

interface ScoreboardUser {
  username: string;
  points: number;
  rank: number;
  pointsHistory: PointsHistoryEntry[];
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
  scoreboardUsers$: Observable<ScoreboardUser[]> = of([]);
  filteredUsers$: Observable<ScoreboardUser[]> = of([]);
  isLoading$ = this.loadingSubject.asObservable();
  
  // Form controls
  searchControl = new FormControl('');
  
  // Component state
  readonly trackByUsername = (index: number, user: ScoreboardUser): string => user.username || index.toString();
  currentRoomId = signal<string | null>(null);
  
  // Chart configuration
  public lineChartType: ChartType = 'bar';
  public lineChartData: ChartData<'bar'> = { 
    labels: [],
    datasets: [] 
  };

  public lineChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Player Points Ranking',
        color: '#ffffff',
        font: {
          size: 16,
          weight: 'bold'
        }
      },
      legend: {
        display: false
      }
    },
    scales: {
      x: {
        title: {
          display: true,
          text: 'Players',
          color: '#ffffff'
        },
        ticks: {
          maxRotation: 45,
          minRotation: 0,
          color: '#ffffff'
        },
        grid: {
          color: '#475569'
        }
      },
      y: {
        title: {
          display: true,
          text: 'Points',
          color: '#ffffff'
        },
        beginAtZero: true,
        ticks: {
          color: '#ffffff'
        },
        grid: {
          color: '#475569'
        }
      }
    },
    interaction: {
      intersect: false,
      mode: 'index'
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
    
    // Setup chart data - subscribe to update the chart
    this.scoreboardUsers$.pipe(
      map(users => this.generateChartData(users)),
      tap(chartData => {
        this.lineChartData = chartData;
        console.log('Chart data updated:', chartData);
      }),
      takeUntil(this.destroy$)
    ).subscribe();
  }
  
  private loadScoreboard(roomId: string): void {
    if (!roomId) return;
    
    this.loadingSubject.next(true);
    
    this.roomService.getRoomUsersForScoreboard(roomId).subscribe({
      next: (response: ApiResponse<RoomUserResponse[]>) => {
        console.log('Scoreboard data loaded:', response);
        this.loadingSubject.next(false);
        
        // Transform and set the data
        const transformedUsers = this.transformToScoreboardUsers(response.data || []);
        console.log('Transformed users:', transformedUsers);
        
        // Update the observable with new data
        this.scoreboardUsers$ = of(transformedUsers);
        
        // Re-setup observables with new data
        this.setupObservables();
      },
      error: (error) => {
        this.loadingSubject.next(false);
        this.handleError('Failed to load scoreboard', error);
      }
    });
  }
  
  private transformToScoreboardUsers(roomUsers: RoomUserResponse[]): ScoreboardUser[] {
    // Sort by points descending

    roomUsers.push(new RoomUserResponse({ username: 'Sasuke', points: 123, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Naruto', points: 156, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Sakura', points: 98, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Kakashi', points: 189, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Itachi', points: 201, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Gaara', points: 134, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Shikamaru', points: 87, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Neji', points: 145, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Rock Lee', points: 112, pointsHistory: [] }));
    roomUsers.push(new RoomUserResponse({ username: 'Hinata', points: 76, pointsHistory: [] }));
    const sorted = [...roomUsers]//.sort((a, b) => (b.points || 0) - (a.points || 0));


    
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
        pointsHistory: user.pointsHistory ? user.pointsHistory : []
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
  
  private generateChartData(users: ScoreboardUser[]): ChartData<'bar'> {
    if (!users || users.length === 0) {
      return { 
        labels: [],
        datasets: [] 
      };
    }
    
    // Show top 10 users
    const usersToShow = users.slice(0, 10);
    
    if (usersToShow.length === 0) {
      return { 
        labels: [],
        datasets: [] 
      };
    }
    
    // Generate colors based on ranking
    const backgroundColors = usersToShow.map((user) => {
      if (user.rank === 1) return '#ffd700'; // Gold for 1st place
      if (user.rank === 2) return '#c0c0c0'; // Silver for 2nd place
      if (user.rank === 3) return '#cd7f32'; // Bronze for 3rd place
      return '#dc2626'; // Primary red for others
    });
    
    const borderColors = backgroundColors.map(color => 
      color === '#dc2626' ? '#991b1b' : color
    );
    
    return {
      labels: usersToShow.map(user => user.username),
      datasets: [{
        label: 'Points',
        data: usersToShow.map(user => user.points),
        backgroundColor: backgroundColors,
        borderColor: borderColors,
        borderWidth: 2,
        borderRadius: 4,
        borderSkipped: false,
      }]
    };
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
    return user.pointsHistory[user.pointsHistory.length - 1]?.pointsChange || 0;
  }
  
  getLastActivity(user: ScoreboardUser): Date | null {
    if (!user.pointsHistory || user.pointsHistory.length === 0) return null;
    return user.pointsHistory[user.pointsHistory.length - 1]?.timestamp || null;
  }
  
  private handleError(message: string, error: any): void {
    console.error(message, error);
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }
}