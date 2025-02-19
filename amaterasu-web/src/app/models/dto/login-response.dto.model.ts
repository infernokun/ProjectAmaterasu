import { User } from "../user.model";

export class LoginResponseDTO {
  jwt?: string;
  user?: User;

  constructor(serverResult?: any) {
    if (serverResult) {
      this.jwt = serverResult.jwt;
      this.user = serverResult.user ? new User(serverResult.user) : undefined;
    }
  }
}
