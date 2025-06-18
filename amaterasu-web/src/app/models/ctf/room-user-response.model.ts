export interface PointsHistoryEntry {
    timestamp?: Date;
    totalPoints?: number;
    pointsChange?: number;
    reason: string;
}

export class RoomUserResponse {
    username?: string;
    points?: number;
    pointsHistory?: PointsHistoryEntry[];

    constructor(serverResult?: any) {
        if (serverResult) {
            this.username = serverResult.username;
            this.points = serverResult.points;
            this.pointsHistory = serverResult.pointsHistory ?? [];
        }
    }
}