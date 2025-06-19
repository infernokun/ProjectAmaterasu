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
  selector: 'amaterasu-scoreboard',
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
  
  // Chart configuration - Changed to line chart
  public lineChartType: ChartType = 'line';
  public lineChartData: ChartData<'line'> = { 
    labels: [],
    datasets: [] 
  };

  public lineChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Player Points Over Time',
        color: '#ffffff',
        font: {
          size: 16,
          weight: 'bold'
        }
      },
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#ffffff',
          usePointStyle: true,
          padding: 15,
          font: {
            size: 12
          }
        }
      },
      tooltip: {
        mode: 'index',
        intersect: false,
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#475569',
        borderWidth: 1
      }
    },
    scales: {
      x: {
        title: {
          display: true,
          text: 'Time Progression',
          color: '#ffffff'
        },
        ticks: {
          color: '#ffffff',
          maxRotation: 45,
          minRotation: 0,
          maxTicksLimit: 8, // Limit number of ticks to avoid crowding
          callback: function(value, index, ticks) {
            // The value is already a formatted string, just return it
            return this.getLabelForValue(new Date(value).getTime());
          }
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
    },
    elements: {
      line: {
        tension: 0.2
      },
      point: {
        radius: 4,
        hoverRadius: 6
      }
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
        const roomUsers: RoomUserResponse[] =  response.data.map(roomUser => new RoomUserResponse(roomUser));

        console.log('Scoreboard data loaded:', response);
        this.loadingSubject.next(false);
        
        // Transform and set the data
        const transformedUsers = this.transformToScoreboardUsers(roomUsers || []);
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
    const now = new Date();
    const sampleUsers = [
      RoomUserResponse.createSample('Sasuke', 623, [
        { timestamp: new Date("2025-06-17 12:31:00"), pointsChange: 50, totalPoints: 50 },
        { timestamp: new Date("2025-06-18 18:31:00"), pointsChange: 30, totalPoints: 80 },
        { timestamp: new Date("2025-06-19 00:31:00"), pointsChange: 43, totalPoints: 123 },
        { timestamp: new Date("2025-06-19 04:31:00"), pointsChange: 500, totalPoints: 623 }
      ]),
      RoomUserResponse.createSample('Naruto', 156, [
        { timestamp: new Date("2025-06-18 16:31:00"), pointsChange: 40, totalPoints: 40 },
        { timestamp: new Date("2025-06-18 20:31:00"), pointsChange: 56, totalPoints: 96 },
        { timestamp: new Date("2025-06-19 04:31:00"), pointsChange: 60, totalPoints: 156 }
      ]),
      RoomUserResponse.createSample('Sakura', 98, [
        { timestamp: new Date("2025-06-18 14:31:00"), pointsChange: 35, totalPoints: 35 },
        { timestamp: new Date("2025-06-18 22:31:00"), pointsChange: 28, totalPoints: 63 },
        { timestamp: new Date("2025-06-19 02:31:00"), pointsChange: 35, totalPoints: 98 }
      ])
     ];

    roomUsers = [...roomUsers, ...sampleUsers];

    console.log('rromUsers', roomUsers)

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
  
  private generateChartData(users: ScoreboardUser[]): ChartData<'line'> {
    console.log('Generating chart data for users:', users);
    
    if (!users || users.length === 0) {
      return { 
        labels: [],
        datasets: [] 
      };
    }
    
    // Filter users that have points history
    const usersWithHistory = users.filter(user => 
      user.pointsHistory && user.pointsHistory.length > 0
    );
    
    console.log('Users with history:', usersWithHistory);
    
    if (usersWithHistory.length === 0) {
      return { 
        labels: [],
        datasets: [] 
      };
    }
    
    // Get all unique timestamps and sort them
    const allTimestamps = new Set<number>();
    usersWithHistory.forEach(user => {
      user.pointsHistory.forEach(entry => {
        if (entry.timestamp) {
          const timestamp = new Date(entry.timestamp);
          if (!isNaN(timestamp.getTime())) {
            allTimestamps.add(timestamp.getTime());
          }
        }
      });
    });
    
    const sortedTimestamps = Array.from(allTimestamps).sort((a, b) => a - b);
    const timeLabels = sortedTimestamps.map(timestamp => {
      const date = new Date(timestamp);
      return date.toLocaleString('en-US', { 
        month: 'short', 
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
      });
    });
    
    console.log('Time labels:', timeLabels);
    
    // Generate distinct colors for each user
    const colors = [
      '#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#ffeaa7',
      '#dda0dd', '#98d8c8', '#f7dc6f', '#bb8fce', '#85c1e9',
      '#f8c471', '#82e0aa', '#f1948a', '#85d4f8', '#f9ca24'
    ];
    
    const datasets = usersWithHistory.map((user, index) => {
      const color = colors[index % colors.length];
      
      // Create data points for this user - simplified approach
      const userData: number[] = [];
      
      // For each timestamp, find or interpolate the user's points
      sortedTimestamps.forEach(timestamp => {
        // Find exact match first
        let points = null;
        const exactMatch = user.pointsHistory.find(entry => {
          const entryTime = new Date(entry.timestamp!).getTime();
          return entryTime === timestamp;
        });
        
        if (exactMatch) {
          points = exactMatch.totalPoints;
        } else {
          // Find the most recent entry before this timestamp
          const previousEntries = user.pointsHistory.filter(entry => {
            const entryTime = new Date(entry.timestamp!).getTime();
            return entryTime <= timestamp;
          }).sort((a, b) => new Date(b.timestamp!).getTime() - new Date(a.timestamp!).getTime());
          
          if (previousEntries.length > 0) {
            points = previousEntries[0].totalPoints;
          } else {
            points = 0; // Default to 0 if no previous data
          }
        }
        
        userData.push(points || 0);
      });
      
      console.log(`User ${user.username} data:`, userData);
      
      return {
        label: user.username,
        data: userData,
        borderColor: color,
        backgroundColor: color + '20', // Add transparency
        fill: false,
        tension: 0.1,
        pointBackgroundColor: color,
        pointBorderColor: '#ffffff',
        pointBorderWidth: 2,
        pointRadius: 5,
        pointHoverRadius: 8,
        borderWidth: 3
      };
    });
    
    console.log('Final datasets:', datasets);
    
    return {
      labels: timeLabels,
      datasets: datasets
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