import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { RoomService } from '../../../services/ctf/room.service';
import { Subject, of, Observable } from 'rxjs';
import { ApiResponse } from '../../../models/api-response.model';
import { RoomUserResponse } from '../../../models/ctf/room-user-response.model';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ChartConfiguration, ChartData } from 'chart.js';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-player-scoreboard',
  standalone: false,
  templateUrl: './player-scoreboard.component.html',
  styleUrl: './player-scoreboard.component.scss'
})
export class PlayerScoreboardComponent implements OnInit, OnDestroy {
  private readonly componentDestroyed$ = new Subject<void>();
  
  // Loading state
  isLoadingScoreboardData: WritableSignal<boolean> = signal(false);
  
  // User data
  currentUserScoreboard$: Observable<RoomUserResponse | undefined> = of(undefined);
  currentUserScoreboard: WritableSignal<RoomUserResponse | undefined> = signal(undefined);

  // Chart types
  readonly LINE_CHART_TYPE: 'line' = 'line';
  readonly PIE_CHART_TYPE: 'pie' = 'pie';

  // Chart data
  scoreProgressChartData: ChartData<'line'> = { 
    labels: [],
    datasets: [] 
  };

  performanceOverviewChartData: ChartData<'pie'> = {
    labels: [],
    datasets: []
  };

  categoryBreakdownChartData: ChartData<'pie'> = {
    labels: [],
    datasets: []
  };

  // Chart configurations
  scoreProgressChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Score Progress Over Time',
        color: '#ffffff',
        font: {
          size: 18,
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
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#dc2626',
        borderWidth: 1,
        callbacks: {
          title: function(context) {
            return context[0].label || '';
          },
          label: function(context) {
            return `${context.dataset.label}: ${context.parsed.y} points`;
          }
        }
      }
    },
    scales: {
      x: {
        type: 'category',
        title: {
          display: true,
          text: 'Time',
          color: '#ffffff'
        },
        ticks: {
          color: '#ffffff',
          maxRotation: 45,
          minRotation: 0,
          maxTicksLimit: 10
        },
        grid: {
          color: 'rgba(220, 38, 127, 0.2)'
        }
      },
      y: {
        type: 'linear',
        title: {
          display: true,
          text: 'Points',
          color: '#ffffff'
        },
        beginAtZero: true,
        ticks: {
          color: '#ffffff',
          stepSize: 1
        },
        grid: {
          color: 'rgba(220, 38, 127, 0.2)'
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

  performanceOverviewChartOptions: ChartConfiguration<'pie'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Performance Overview',
        color: '#ffffff',
        font: {
          size: 18,
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
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#475569',
        borderWidth: 1,
        callbacks: {
          label: function(context) {
            const label = context.label || '';
            const value = context.parsed;
            const total = context.dataset.data.reduce((sum: number, val: any) => sum + val, 0);
            const percentage = ((value / total) * 100).toFixed(1);
            return `${label}: ${value} (${percentage}%)`;
          }
        }
      }
    }
  };

  categoryBreakdownChartOptions: ChartConfiguration<'pie'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Success by Category',
        color: '#ffffff',
        font: {
          size: 18,
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
        backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: '#475569',
        borderWidth: 1,
        callbacks: {
          label: function(context) {
            const label = context.label || '';
            const value = context.parsed;
            const total = context.dataset.data.reduce((sum: number, val: any) => sum + val, 0);
            const percentage = ((value / total) * 100).toFixed(1);
            return `${label}: ${value} solved (${percentage}%)`;
          }
        }
      }
    }
  };
  
  constructor(
    private roomService: RoomService, 
    private snackBar: MatSnackBar, 
    private activatedRoute: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const roomId = this.activatedRoute.snapshot.params['room'];
    if (roomId) {
      this.loadUserScoreboardData(roomId);
    }
  }

  ngOnDestroy(): void {
    this.componentDestroyed$.next();
    this.componentDestroyed$.complete();
  }

  private loadUserScoreboardData(roomId: string): void {
    this.isLoadingScoreboardData.set(true);
    
    this.roomService.getRoomUserForScoreboard(roomId).subscribe({
      next: (apiResponse: ApiResponse<RoomUserResponse>) => {
        const userScoreboardData: RoomUserResponse = apiResponse.data;
        this.currentUserScoreboard.set(apiResponse.data);
        console.log('User scoreboard data loaded:', userScoreboardData);
        
        this.generateAllChartData(userScoreboardData);
        this.isLoadingScoreboardData.set(false);
      },
      error: (error) => {
        console.error('Error loading user scoreboard data:', error);
        this.isLoadingScoreboardData.set(false);
        this.displayErrorMessage('Failed to load scoreboard data', error);
      }
    });
  }

  private generateAllChartData(userScoreboardData: RoomUserResponse): void {
    // Generate line chart for score progress
    this.scoreProgressChartData = this.generateScoreProgressChartData(userScoreboardData);
    
    // Generate pie chart for performance overview (correct vs fails)
    this.performanceOverviewChartData = this.generatePerformanceOverviewChartData(userScoreboardData);
    
    // Generate pie chart for category breakdown
    this.categoryBreakdownChartData = this.generateCategoryBreakdownChartData(userScoreboardData);
    
    console.log('Score progress chart data:', this.scoreProgressChartData);
    console.log('Performance overview chart data:', this.performanceOverviewChartData);
    console.log('Category breakdown chart data:', this.categoryBreakdownChartData);
  }

  private generateScoreProgressChartData(userScoreboardData: RoomUserResponse): ChartData<'line'> {
    console.log('Generating score progress chart data for user:', userScoreboardData);
    
    if (!userScoreboardData?.pointsHistory || userScoreboardData.pointsHistory.length === 0) {
      console.log('No points history available');
      return { labels: [], datasets: [] };
    }
    
    console.log('Raw points history:', userScoreboardData.pointsHistory);
    
    // Filter and sort points history by timestamp
    const validPointsHistory = userScoreboardData.pointsHistory
      .filter(historyEntry => {
        const hasValidTimestamp = historyEntry.timestamp && !isNaN(new Date(historyEntry.timestamp).getTime());
        if (!hasValidTimestamp) {
          console.log('Invalid timestamp entry:', historyEntry);
        }
        return hasValidTimestamp;
      })
      .sort((entryA, entryB) => new Date(entryA.timestamp!).getTime() - new Date(entryB.timestamp!).getTime());
    
    console.log('Valid sorted history:', validPointsHistory);
    
    if (validPointsHistory.length === 0) {
      console.log('No valid history entries found');
      return { labels: [], datasets: [] };
    }
    
    // Create formatted time labels
    const formattedTimeLabels = validPointsHistory.map((historyEntry) => {
      const entryDate = new Date(historyEntry.timestamp!);
      return entryDate.toLocaleDateString('en-US', { 
        month: 'short', 
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit'
      });
    });
    
    // Extract points data
    const pointsProgressData = validPointsHistory.map(historyEntry => historyEntry.totalPoints || 0);
    
    console.log('Formatted time labels:', formattedTimeLabels);
    console.log('Points progress data:', pointsProgressData);
    
    const scoreProgressDataset = {
      label: `${userScoreboardData.username || 'Player'} Score Progress`,
      data: pointsProgressData,
      borderColor: '#dc2626',
      backgroundColor: 'rgba(220, 38, 127, 0.1)',
      fill: true,
      tension: 0.3,
      pointBackgroundColor: '#dc2626',
      pointBorderColor: '#ffffff',
      pointBorderWidth: 2,
      pointRadius: 6,
      pointHoverRadius: 8,
      borderWidth: 3
    };
    
    const chartDataResult = {
      labels: formattedTimeLabels,
      datasets: [scoreProgressDataset]
    };
    
    console.log('Generated score progress chart data:', chartDataResult);
    return chartDataResult;
  }

  private generatePerformanceOverviewChartData(userScoreboardData: RoomUserResponse): ChartData<'pie'> {
    const correctAnswersCount = userScoreboardData.correct || 0;
    const failedAttemptsCount = userScoreboardData.fails || 0;
    
    if (correctAnswersCount === 0 && failedAttemptsCount === 0) {
      return { labels: [], datasets: [] };
    }

    return {
      labels: ['Correct Answers', 'Failed Attempts'],
      datasets: [
        {
          data: [correctAnswersCount, failedAttemptsCount],
          backgroundColor: ['#10b981', '#ef4444'],
          borderColor: ['#059669', '#dc2626'],
          borderWidth: 2,
          hoverBackgroundColor: ['#34d399', '#f87171'],
          hoverBorderColor: ['#047857', '#b91c1c'],
          hoverBorderWidth: 3
        },
      ],
    };
  }

  private generateCategoryBreakdownChartData(userScoreboardData: RoomUserResponse): ChartData<'pie'> {
    if (!userScoreboardData.correctByCategory || Object.keys(userScoreboardData.correctByCategory).length === 0) {
      return { labels: [], datasets: [] };
    }

    const categoryLabels: string[] = [];
    const categoryCounts: number[] = [];
    const categoryColors = [
      '#3b82f6', '#8b5cf6', '#f59e0b', '#ef4444', 
      '#10b981', '#f97316', '#06b6d4', '#ec4899',
      '#84cc16', '#6366f1', '#f43f5e', '#14b8a6'
    ];
    
    Object.entries(userScoreboardData.correctByCategory).forEach(([categoryName, categoryCount]) => {
      if (categoryCount && categoryCount > 0) {
        categoryLabels.push(categoryName);
        categoryCounts.push(categoryCount);
      }
    });
    
    if (categoryCounts.length === 0) {
      return { labels: [], datasets: [] };
    }

    return {
      labels: categoryLabels,
      datasets: [
        {
          data: categoryCounts,
          backgroundColor: categoryColors.slice(0, categoryCounts.length),
          borderColor: categoryColors.slice(0, categoryCounts.length),
          borderWidth: 2,
          hoverBackgroundColor: categoryColors.slice(0, categoryCounts.length).map(color => color + 'CC'),
          hoverBorderWidth: 3
        },
      ],
    };
  }

  hasScoreProgressData(): boolean {
    return this.scoreProgressChartData.datasets.length > 0;
  }

  hasPerformanceOverviewData(): boolean {
    return this.performanceOverviewChartData.datasets.length > 0 && 
           this.performanceOverviewChartData.datasets[0]?.data?.some(dataPoint => dataPoint > 0);
  }

  hasCategoryBreakdownData(): boolean {
    return this.categoryBreakdownChartData.datasets.length > 0 && 
           this.categoryBreakdownChartData.datasets[0]?.data?.some(dataPoint => dataPoint > 0);
  }

  private displayErrorMessage(message: string, error: any): void {
    console.error(message, error);
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }
}