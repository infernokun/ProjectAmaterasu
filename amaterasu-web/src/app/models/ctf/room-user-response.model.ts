export interface PointsHistoryEntry {
    timestamp: Date;
    totalPoints: number;
    pointsChange: number;
    reason?: string;
  }
  
  export interface RoomUserServerResponse {
    username?: string;
    points?: number;
    pointsHistory?: Array<{
      timestamp: string | Date;
      totalPoints?: number;
      pointsChange?: number;
      reason?: string;
    }>;
    fails?: number;
    correct?: number;
    correctByCategory?: {[key: string]: number};
    place?: string;
  }
  
  export class RoomUserResponse {
    username: string;
    points: number;
    pointsHistory: PointsHistoryEntry[];
    fails: number;
    correct: number;
    correctByCategory?: {[key: string]: number};
    place?: string;
  
    constructor(serverResult?: RoomUserServerResponse) {
      this.username = serverResult?.username || 'Unknown User';
      this.points = serverResult?.points || 0;
      this.pointsHistory = this.parsePointsHistory(serverResult?.pointsHistory || []);
      this.fails = serverResult?.fails || 0;
      this.correct = serverResult?.correct || 0;
      this.correctByCategory = serverResult?.correctByCategory;
      this.place = serverResult?.place || 'N/A';
    }
  
    private parsePointsHistory(serverHistory: Array<{
      timestamp: string | Date;
      totalPoints?: number;
      pointsChange?: number;
      reason?: string;
    }>): PointsHistoryEntry[] {
      return serverHistory
        .map(entry => this.parseHistoryEntry(entry))
        .filter((entry): entry is PointsHistoryEntry => entry !== null)
        .sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
    }
  
    private parseHistoryEntry(entry: {
      timestamp: string | Date;
      totalPoints?: number;
      pointsChange?: number;
      reason?: string;
    }): PointsHistoryEntry | null {
      try {
        const timestamp = new Date(entry.timestamp);
        
        // Validate timestamp
        if (isNaN(timestamp.getTime())) {
          console.warn('Invalid timestamp in points history:', entry.timestamp);
          return null;
        }
  
        // Validate required numeric fields
        if (typeof entry.totalPoints !== 'number' || typeof entry.pointsChange !== 'number') {
          console.warn('Invalid points data in history entry:', entry);
          return null;
        }
  
        return {
          timestamp,
          totalPoints: entry.totalPoints,
          pointsChange: entry.pointsChange,
          reason: entry.reason
        };
      } catch (error) {
        console.error('Error parsing points history entry:', error, entry);
        return null;
      }
    }
  
    // Utility methods
    getCurrentPoints(): number {
      if (this.pointsHistory.length === 0) {
        return this.points;
      }
      
      const latestEntry = this.pointsHistory[this.pointsHistory.length - 1];
      return latestEntry.totalPoints;
    }
  
    getLastActivity(): Date | null {
      if (this.pointsHistory.length === 0) {
        return null;
      }
      
      return this.pointsHistory[this.pointsHistory.length - 1].timestamp;
    }
  
    getLastPointsChange(): number {
      if (this.pointsHistory.length === 0) {
        return 0;
      }
      
      return this.pointsHistory[this.pointsHistory.length - 1].pointsChange;
    }
  
    getPointsAt(targetDate: Date): number {
      const entriesBeforeDate = this.pointsHistory.filter(
        entry => entry.timestamp.getTime() <= targetDate.getTime()
      );
  
      if (entriesBeforeDate.length === 0) {
        return 0;
      }
  
      return entriesBeforeDate[entriesBeforeDate.length - 1].totalPoints;
    }
  
    hasActivity(): boolean {
      return this.pointsHistory.length > 0;
    }
  
    // Static factory method for creating sample users
    static createSample(username: string, points: number, historyEntries?: Partial<PointsHistoryEntry>[]): RoomUserResponse {
      const now = new Date();
      const defaultHistory = historyEntries || [
        {
          timestamp: new Date(now.getTime() - 24 * 60 * 60 * 1000), // 24 hours ago
          totalPoints: Math.floor(points * 0.3),
          pointsChange: Math.floor(points * 0.3),
          reason: 'Initial progress'
        },
        {
          timestamp: new Date(now.getTime() - 12 * 60 * 60 * 1000), // 12 hours ago
          totalPoints: Math.floor(points * 0.7),
          pointsChange: Math.floor(points * 0.4),
          reason: 'Continued progress'
        },
        {
          timestamp: new Date(now.getTime() - 2 * 60 * 60 * 1000), // 2 hours ago
          totalPoints: points,
          pointsChange: Math.floor(points * 0.3),
          reason: 'Latest achievement'
        }
      ];
  
      return new RoomUserResponse({
        username,
        points,
        pointsHistory: defaultHistory.map(entry => ({
          timestamp: entry.timestamp || new Date(),
          totalPoints: entry.totalPoints || 0,
          pointsChange: entry.pointsChange || 0,
          reason: entry.reason
        }))
      });
    }
  }