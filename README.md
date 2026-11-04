# ProjectAmaterasu

[![CI](https://github.com/infernokun/ProjectAmaterasu/actions/workflows/ci.yml/badge.svg)](https://github.com/infernokun/ProjectAmaterasu/actions/workflows/ci.yml)

ProjectAmaterasu is a virtual lab orchestration application. It deploys Docker Compose and Proxmox VM labs to remote servers and runs CTF style rooms with scoring.

## Components

| Directory            | Description                                                                 |
| -------------------- | --------------------------------------------------------------------------- |
| `amaterasu-rest`     | Spring Boot 3.5 REST API (Java 21). Postgres for storage, Redis for caching. |
| `amaterasu-web`      | Angular 22 frontend served by nginx, which also proxies `/api` to the API.  |
| `amaterasu-proxmox`  | Python helper CLI for Proxmox and a mock Proxmox API under `mock/`.         |
| `amaterasu-populate` | Scripts that seed sample rooms and challenges through the API.              |
| `amaterasu-dev`      | Compose files for local Postgres, pgAdmin, Redis and a Docker-in-Docker host. |
| `amaterasu-sample`   | Example lab compose file.                                                    |

## Running with Docker Compose

```bash
wget https://raw.githubusercontent.com/infernokun/ProjectAmaterasu/refs/heads/main/docker-compose.yml
```

```bash
docker compose up -d
```

This starts:

- `amaterasu-web` on port `8764`
- `amaterasu-rest` on port `8765` (context path `/amaterasu-rest`)
- `amaterasu-db` (Postgres 17)
- `amaterasu-redis` (Redis 8)

The API generates an RSA key pair for signing JWTs on first start and stores it in the `amaterasu-rest` volume. To use your own keys, mount them and set `RSA_PRIVATE_KEY_PATH` and `RSA_PUBLIC_KEY_PATH`:

```bash
openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:2048
```

```bash
openssl rsa -pubout -in private.pem -out public.pem
```

### Environment variables

`amaterasu-rest`:

| Variable               | Default                          | Description                          |
| ---------------------- | -------------------------------- | ------------------------------------ |
| `DB_IP`                | `amaterasu-db`                   | Postgres host                        |
| `DB_PORT`              | `5432`                           | Postgres port                        |
| `DB_NAME`              | `amaterasu`                      | Database name                        |
| `DB_USER` / `DB_PASS`  | `amaterasu` / `amaterasu`        | Database credentials                 |
| `REDIS_HOST`           | `amaterasu-redis`                | Redis host                           |
| `REDIS_PASSWORD`       | `amaterasu`                      | Redis password                       |
| `DOCKER_COMPOSE_PATH`  | `/var/tmp/amaterasu`             | Working directory for lab files      |
| `ENCRYPTION_KEY`       | `secret_key`                     | Key used to encrypt stored secrets. Change this. |
| `RSA_PRIVATE_KEY_PATH` | `/var/tmp/amaterasu/private.pem` | JWT signing key                      |
| `RSA_PUBLIC_KEY_PATH`  | `/var/tmp/amaterasu/public.pem`  | JWT verification key                 |

`amaterasu-web`:

| Variable   | Description                         |
| ---------- | ----------------------------------- |
| `BASE_URL` | Public URL of the web app           |
| `REST_URL` | API base URL, `/api` behind nginx   |

On first start the API creates an admin account from `amaterasu.defaultAdminUsername` and `amaterasu.defaultAdminPassword` in `application-local.yml`. Change the password after logging in.

## Development

API (needs Postgres and Redis, see `amaterasu-dev`):

```bash
cd amaterasu-rest && ./gradlew bootRun
```

Web (served on port 4300):

```bash
cd amaterasu-web && pnpm install && pnpm start
```

### Tests

```bash
cd amaterasu-rest && ./gradlew test
```

```bash
cd amaterasu-web && pnpm exec ng test --watch=false --browsers=ChromeHeadlessCI
```

```bash
ruff check .
```

CI runs all of the above plus Docker image builds on every pull request.

## Troubleshooting

```bash
docker compose logs amaterasu-rest
```

The API health endpoint is `http://localhost:8765/amaterasu-rest/health`.
