import { User } from "../user.model";

// The refresh token is an HttpOnly cookie and never reaches the page
export class LoginResponse {
  accessToken?: string;
  user?: User;

  constructor(serverResult?: any) {
    if (serverResult) {
      this.accessToken = serverResult.accessToken;
      this.user = serverResult.user ? new User(serverResult.user) : undefined;
    }
  }
}
