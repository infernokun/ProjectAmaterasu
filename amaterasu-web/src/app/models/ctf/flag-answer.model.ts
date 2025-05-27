export class FlagAnswer {
    flag?: string;
    userId?: string;
    questionId?: string;

    constructor(flag: string, userId: string, questionId: string) {
        this.flag = flag;
        this.userId = userId;
        this.questionId = questionId;
    }
}