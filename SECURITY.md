# Security notes

ProjectAmaterasu is still a proof of concept. Review the items below before exposing it to an untrusted network.

## How auth works

- Login returns a 15 minute RS256 access token and an opaque refresh token. Refresh tokens are stored as SHA-256 hashes, rotated on every use, and expire after 7 days idle or 30 days in total. Presenting a rotated token again revokes every session for that user.
- Every request checks that the token's user still exists, is not disabled, and has not had their sessions reset since the token was issued. Changing or resetting a password resets sessions.
- Routes are restricted by role in `SecurityConfig`. Lab, tracker, room and hint actions use the authenticated user, not ids from the request, and lab actions check that the tracker belongs to the caller's team.
- Failed logins are throttled per username and per client address. Registration and token refresh are throttled per address. The client address comes from `X-Forwarded-For` only when the request arrives from a trusted proxy.
- Self registration always creates a `MEMBER`. The admin password comes from `ADMIN_PASSWORD` or is generated and logged once.
- Raw challenge records, which include flags, are only readable by authors. Actuator exposes only `health` and `info`.
- Unexpected errors return a generic message with a reference id; the details are only in the server log.

## Known gaps

1. **Tokens are kept in `localStorage`.** An XSS bug in the web app would expose them. The nginx config sends `nosniff` and frame headers but no Content Security Policy yet.
2. **No email.** There is no email confirmation and no self-service password reset; an admin resets passwords.
3. **No SSO or MFA.**
4. **Throttling is in memory.** Counters reset on restart and are not shared if the API is scaled out.
5. **The websocket handshake is public.** It only carries heartbeats today; anything sensitive sent over it would need auth first.
6. **`ALLOWED_ORIGINS` defaults to `*`.** The web app is same-origin through nginx, so set it to your own origins if nothing else calls the API from a browser.
7. **`ENCRYPTION_KEY` is only checked at startup** and a weak value logs a warning rather than refusing to start.

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
