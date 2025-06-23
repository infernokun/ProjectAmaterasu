import { Component, OnDestroy, OnInit, signal, WritableSignal } from '@angular/core';
import { RoomService } from '../../../services/ctf/room.service';
import { Subject, of, Observable } from 'rxjs';
import { ApiResponse } from '../../../models/api-response.model';
import { RoomUserResponse } from '../../../models/ctf/room-user-response.model';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ChartConfiguration, ChartData, ChartOptions, ChartType } from 'chart.js';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-player-scoreboard',
  standalone: false,
  templateUrl: './player-scoreboard.component.html',
  styleUrl: './player-scoreboard.component.scss'
})
export class PlayerScoreboardComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  loading: WritableSignal<boolean> = signal(false);

  scoreboardUser$: Observable<RoomUserResponse | undefined> = of(undefined);

  public lineChartType: 'line' = 'line';
  public lineChartData: ChartData<'line'> = { 
    labels: [],
    datasets: [] 
  };

  public lineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Score Progress Over Time',
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

  public pieChartOptions: ChartConfiguration<'pie'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Key Percentages',
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

  public pieChartOptions2: ChartConfiguration<'pie'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      title: {
        display: true,
        text: 'Category Breakdown',
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

  pieChartData: ChartData<'pie'> = {
    labels: [],
    datasets: []
  }

  pieChartData2: ChartData<'pie'> = {
    labels: [],
    datasets: []
  }

  public pieChartType: 'pie' = 'pie';
  
  constructor(
    private roomService: RoomService, 
    private snackBar: MatSnackBar, 
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    const roomId = this.route.snapshot.params['room'];
    if (roomId) {
      this.loadScoreboard(roomId);
    }
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadScoreboard(roomId: string): void {
    this.loading.set(true);
    
    this.roomService.getRoomUserForScoreboard(roomId).subscribe({
      next: (response: ApiResponse<RoomUserResponse>) => {
        const roomUser: RoomUserResponse = new RoomUserResponse(response.data);
        
        console.log('Scoreboard data loaded:', roomUser);
        
        // Generate chart data immediately
        const chartData = this.generateChartData(roomUser);
        this.lineChartData = { ...chartData };
        
        this.pieChartData = {
          datasets: [
            {
              data: [roomUser.fails, roomUser.correct],
              backgroundColor: ['#26547c', '#ff6b6b', '#ffd166'],
            },
          ],
        };
        
        //const stuff = Array.from(roomUser.correctByCategory?.values()!)
        
        console.log(roomUser.correctByCategory);

        const datas: number[] = [];

        for (const key in roomUser.correctByCategory) {
          console.log(`Key: ${key}, Value: ${roomUser.correctByCategory[key as keyof typeof roomUser.correctByCategory]}`);
          datas.push(roomUser.correctByCategory[key as keyof typeof roomUser.correctByCategory] as number);
      }
        this.pieChartData = {
          datasets: [
            {
              data: datas,
              backgroundColor: ['#26547c', '#ff6b6b', '#ffd166'],
            },
          ],
        };
        
        console.log('Chart data generated:', this.lineChartData);
        
        this.loading.set(false);
      },
      error: (error) => {
        console.error('Error loading scoreboard:', error);
        this.loading.set(false);
        this.handleError('Failed to load scoreboard', error);
      }
    });
  }

  private generateChartData(user: RoomUserResponse): ChartData<'line'> {
    console.log('Generating chart data for user:', user);
    
    if (!user) {
      console.log('No user data');
      return { labels: [], datasets: [] };
    }
    
    if (!user.pointsHistory || user.pointsHistory.length === 0) {
      console.log('No points history');
      return { labels: [], datasets: [] };
    }
    
    console.log('Raw points history:', user.pointsHistory);
    
    // Sort points history by timestamp
    const sortedHistory = user.pointsHistory
      .filter(entry => {
        const isValid = entry.timestamp && !isNaN(new Date(entry.timestamp).getTime());
        if (!isValid) {
          console.log('Invalid timestamp entry:', entry);
        }
        return isValid;
      })
      .sort((a, b) => new Date(a.timestamp!).getTime() - new Date(b.timestamp!).getTime());
    
    console.log('Sorted history:', sortedHistory);
    
    if (sortedHistory.length === 0) {
      console.log('No valid history entries');
      return { labels: [], datasets: [] };
    }
    
    // Create time labels with simpler format
    const timeLabels = sortedHistory.map((entry, index) => {
      const date = new Date(entry.timestamp!);
      // Use a simpler format or just use index for testing
      return date.toLocaleDateString('en-US', { 
        month: 'short', 
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit'
      });
    });
    
    // Create points data
    const pointsData = sortedHistory.map(entry => entry.totalPoints || 0);
    
    console.log('Final time labels:', timeLabels);
    console.log('Final points data:', pointsData);
    
    const dataset = {
      label: `${user.username || 'Player'} Score`,
      data: pointsData,
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
    
    const result = {
      labels: timeLabels,
      datasets: [dataset]
    };
    
    console.log('Generated chart data:', result);
    return result;
  }

  private handleError(message: string, error: any): void {
    console.error(message, error);
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }
}