# Security & Correctness Findings — ProjectAmaterasu

This document records the results of a security/correctness audit of the
`amaterasu-rest` backend. Items already **fixed** on the
`claude/lab-creator-improvements` branch are marked ✅. Items that are
**behavior-changing** (and could break the current proof-of-concept until the
frontend is verified) are marked ⚠️ and left for a deliberate decision.

> Context: this is a proof-of-concept. Several "open by default" choices look
> intentional for local development. The recommendations below are what you'd
> want before exposing the app to an untrusted network.

---

## Authentication & Authorization (highest priority)

### 1. ⚠️ The entire API is effectively public
`config/SecurityConfig.java` — the first matcher is `requestMatchers("/**").permitAll()`,
which matches every request, so `anyRequest().authenticated()` and the JWT
resource-server config never gate anything.

**Impact:** every endpoint (user CRUD, team CRUD, lab deploy, proxmox control)
is reachable with no token.

**Fix:** remove the `/**` permitAll and enumerate only the genuinely public
routes, e.g.:
```java
auth.requestMatchers("/api/auth/**", "/api/application-info").permitAll();
auth.requestMatchers("/socket/**").permitAll(); // if the socket handshake is unauthenticated
auth.anyRequest().authenticated();
```
Order matchers most-specific-first.

### 2. ⚠️ No role-based authorization; `role` is bound from the client
There is no `@EnableMethodSecurity` and no `@PreAuthorize` anywhere.
`UserController.createUser` accepts a full `User` body including `role`, so any
caller can create an `ADMIN`.

**Fix:** add `@EnableMethodSecurity`, annotate admin-only operations with
`@PreAuthorize("hasRole('ADMIN')")`, and accept a DTO that does **not** carry
`role` on self-service creation (assign `MEMBER` server-side).

### 3. ⚠️ Default admin with a hardcoded weak password
`application-local.yml` ships `defaultAdminUsername: amaterasu_admin` /
`defaultAdminPassword: password`, and `ApplicationInitializer` creates that
account on every boot.

**Fix:** require the admin password via a mandatory secret env var with no
default; fail startup if unset; force a password change on first login.

### 4. ⚠️ JWT role claim naming is inconsistent
`RefreshTokenService` writes the raw enum value into the `roles` claim and the
converter prepends `ROLE_`. The `Role` enum mixes cases (e.g. `Creator`,
`Facilitator`), so `hasRole('ADMIN')` checks would silently never match.

**Fix:** normalize role names (uppercase, no spaces) and make the emitted claim
match the authority format the converter expects. Needed before #2 is effective.

### 5. ⚠️ Refresh tokens: long-lived, not rotated, weak binding
`RefreshTokenService` — 90-day expiry with sliding renewal, rotation only every
30 days, no reuse detection, and device/IP binding only logs (never blocks). IP
is taken from attacker-controllable `X-Forwarded-For`/`X-Real-IP`.

**Fix:** rotate on every refresh, shorten TTL, add reuse detection (revoke the
chain on reuse), and only trust forwarded headers from known proxies.

### 6. ⚠️ Unauthenticated "add points" endpoint
`RoomController` `add-points/{roomId}/{userId}` grants +100 points to an
arbitrary user with no auth/role gate (a dev button). `joinRoom`, `leaveRoom`,
and `useHint` also take `userId` from the path instead of the authenticated
principal.

**Fix:** derive `userId` from `SecurityContextHolder`; gate `add-points` behind
an admin role or remove it.

### 7. ⚠️ CORS allows all origins
`config/WebConfig.java` uses `allowedOriginPatterns("*")` +
`allowedMethods("*")` + `allowedHeaders("*")`; actuator CORS in
`application-local.yml` is also `*`. Combined with CSRF disabled, any site can
script state-changing calls on a victim's behalf.

**Fix:** restrict `allowedOrigins` to the known frontend origin(s); restrict
methods/headers to what's used.

---

## Cryptography

### 8. ✅ Stopped logging AES key material
`utils/AESUtil.java` logged the derived AES key (and salt/IV/ciphertext) at
DEBUG. Removed. (The AES-GCM + PBKDF2 scheme itself is sound.)

### 9. ⚠️ Encryption key is unvalidated
`ENCRYPTION_KEY` has no minimum-length/entropy check; if unset the passphrase
binds empty. Consider validating presence/length at startup (fail fast) and
AES-256.

---

## Error handling / information disclosure

### 10. ✅ Catch-all 500s now logged
`GlobalExceptionHandler` — the `RuntimeException`/`Exception` handlers logged
nothing (500s were invisible). Now logged at ERROR with stack; added a 409
handler for `OptimisticLockingFailureException`.

### 11. ⚠️ Error responses leak internal messages
The same handlers put `ex.getMessage()` directly in the response body. Consider
a generic client message + a correlation id, with details server-side only.
`ChallengeNotAnsweredException` also returns HTTP 200 for a failure.

---

## Data model / persistence

### 12. ✅ Redis config namespace corrected
`spring.redis.*` → `spring.data.redis.*` (Spring Boot 3). The old keys were
ignored, so caching fell back to `localhost:6379` with no password.

### 13. ⚠️ `RemoteServer.serverType` persisted as ordinal
`models/entities/lab/RemoteServer.java` lacks `@Enumerated(EnumType.STRING)`
(every other enum uses STRING). Reordering `ServerType` would silently remap
existing rows, and three crons branch on it. **Requires a data migration** for
existing ordinal rows, so left for a deliberate change.

### 14. ⚠️ `Team.teamActiveLabs` / `teamDeletedLabs` lack `@ElementCollection`
A `List<String>` without `@ElementCollection` (or a converter) isn't cleanly
persisted by JPA. Verify how these round-trip; annotate appropriately.

---

## CTF scoring (fixed)

### 15. ✅ Infinite-points exploit fixed
`FlagService` overwrote `correct` on every submission, so re-solving after a
wrong answer re-awarded points. Solved state is no longer downgraded.

### 16. ✅ Flag matching hardened
`validateFlag` now trims input and honors each flag's `caseSensitive` flag
(previously always case-insensitive). **Note:** flags default to
`caseSensitive = true`, so matching is now case-sensitive by default — set
`caseSensitive = false` on flags that should accept any case.

### 17. ✅ Scoreboard room scoping + fail-count underflow fixed
`RoomController` scoreboard now looks up the RoomUser by `(user, room)` and
clamps fail counts at 0.

### 18. ✅ `RoomUserRepository` id type corrected (`Long` → `String`).

### 19. ⚠️ Remaining CTF hardening (not yet done)
- Enforce `CTFEntity.maxAttempts` (currently unenforced — unlimited brute force).
- Add a lock/`@Version` around the get-or-create + points award to prevent
  concurrent double-scoring.
- Add `@NotBlank` to `CTFEntityAnswerRequest` fields; authorize room membership
  before validating the flag (currently a non-member can probe flag correctness).
- `JOIN FETCH` on scoreboard/status queries to remove N+1s.
- `CTFEntityService.updateEntityFields` only updates flags, silently dropping
  question/points/hints on `PUT` — a broken update path.

---

## Other correctness (already fixed on this branch)

- Docker Compose deploy: shell/YAML injection, broken teardown, stderr-poisoned
  JSON, and several NPEs (see the lab-deploy commit).
- Proxmox stats cron guard (`&&` → `||`) and null-data filtering.
- Frontend: server-selection sync, RxJS leaks, deploy error handling.
- `amaterasu-proxmox/main.py`: gated the destructive default `quick_delete`.
