export class StoredObject {
    id?: string;
    createdBy?: string;
    createdDate?: Date;
    lastModifiedDate?: Date;

    constructor(serverResult?: any) {
        if (serverResult) {
            this.id = serverResult.id;
            this.createdBy = serverResult.createdBy;
            this.createdDate = new Date(serverResult.createdDate);
            this.lastModifiedDate = new Date(serverResult.lastModifiedDate);
        }
    }
}