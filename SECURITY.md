# Security notes

ProjectAmaterasu is still a proof of concept. The items below are known gaps that should be closed before the app is exposed to an untrusted network.

## Open

1. **API is public.** `SecurityConfig` starts with `requestMatchers("/**").permitAll()`, so JWTs are issued but never required. Only `/api/auth/**`, `/api/application-info` and the socket handshake should be public.
2. **No role checks.** There is no `@EnableMethodSecurity` or `@PreAuthorize`, and `UserController.createUser` binds `role` from the request body.
3. **Role claim casing.** `Role` mixes casing (`Creator`, `Facilitator`) and the converter prefixes `ROLE_`, so `hasRole('ADMIN')` style checks will not match until the names are normalized. Fix this before item 2.
4. **Default admin password.** `application-local.yml` ships `amaterasu_admin` / `password`. Require it from an environment variable instead.
5. **Refresh tokens.** 90 day sliding expiry, rotation only every 30 days, no reuse detection, and the client IP is read from `X-Forwarded-For`.
6. **Room endpoints trust path ids.** `add-points/{roomId}/{userId}`, `joinRoom`, `leaveRoom` and `useHint` take the user id from the path instead of the authenticated principal.
7. **CORS allows every origin** in `WebConfig` and the actuator config.
8. **Encryption key is not validated.** `ENCRYPTION_KEY` defaults to `secret_key` in compose and has no length check.
9. **Error bodies include exception messages.** `GlobalExceptionHandler` returns `ex.getMessage()` to the client.

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

The open items above are already known and do not need a report.
