export class FlagAnswer {
    flag?: string;
    username?: string;
    questionId?: string;

    constructor(flag: string, username: string, questionId: string) {
        this.flag = flag;
        this.username = username;
        this.questionId = questionId;
    }
}