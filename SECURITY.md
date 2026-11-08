# Security notes

ProjectAmaterasu is still a proof of concept. Review the items below before exposing it to an untrusted network.

## How auth works

- Login returns a 15 minute RS256 access token, which the web app keeps in memory only, and sets the refresh token as an HttpOnly, SameSite=Strict cookie scoped to `/api/auth`. Page scripts can read neither from storage.
- Refresh tokens are stored as SHA-256 hashes, rotated on every use, and expire after 7 days idle or 30 days in total. Presenting a rotated token again after a 30 second grace window revokes every session for that user; inside the window (two tabs refreshing at once) it only returns a new access token.
- Every request checks that the token's user still exists, is not disabled, and has not had their sessions reset since the token was issued. Changing or resetting a password resets sessions. Signing out in one tab signs out the others.
- Routes are restricted by role in `SecurityConfig`. Lab, tracker, room and hint actions use the authenticated user, not ids from the request. Only admins, and facilitators for the labs they are assigned to, start, pause, resume or stop an environment copy; players only read the copy they work in (their team's, the Solo copy without a team, or a lab's shared copy).
- The live update socket needs an access token on the handshake. Browsers can't send headers there, so it goes in the `access_token` query parameter, which only that path accepts and which is kept out of the logs.
- Failed logins are throttled per username and per client address, and registration and token refresh per address. Counters are kept in Redis, with an in-memory fallback while Redis is unreachable. The client address comes from `X-Forwarded-For` only when the request arrives from a trusted proxy.
- Cross-origin browser access is off unless `ALLOWED_ORIGINS` lists the origins.
- nginx sends a Content Security Policy that allows scripts only from the app itself.
- The API refuses to start with an `ENCRYPTION_KEY` under 32 characters or a known default.
- Self registration always creates a `MEMBER`. The admin password comes from `ADMIN_PASSWORD` or is generated and logged once.
- Raw challenge records, which include flags, are only readable by authors. Actuator exposes only `health` and `info`.
- Unexpected errors return a generic message with a reference id; the details are only in the server log.

## Known gaps

1. **No email.** There is no email confirmation and no self-service password reset; an admin resets passwords.
2. **No SSO or MFA.**
3. **Styles allow `'unsafe-inline'`** in the Content Security Policy, because Angular adds component styles as inline `<style>` tags. Scripts do not.
4. **Access tokens are not revoked on sign out.** One stays usable until it expires, at most 15 minutes, unless the account is disabled or its password changed.

## Reporting a vulnerability

Do not open a public issue. Report it privately through a GitHub security advisory on this repository, and include:

- what the issue is and what an attacker could do with it
- steps to reproduce, or a proof of concept
- any fix you have in mind

Only the latest `main` is maintained.

## Scope

- The REST API (`amaterasu-rest`), including what it runs on Docker hosts over SSH and on Proxmox nodes through the API
- The web frontend and its nginx configuration (`amaterasu-web`)
- The Docker images and `docker-compose.yml`

The known gaps above do not need a report.
