# amaterasu-rest

The ProjectAmaterasu API. It owns users, teams, labs, lab deployments (trackers) and CTF rooms, and it is the only service that talks to Docker hosts and Proxmox nodes.

> **Java** 21 · **Spring Boot** 3.5 · **Build** Gradle
> **Port** `8080`, context path `/amaterasu-rest`, API base `/api`
> **Published** `127.0.0.1:8765` in `docker-compose.yml`; nginx in `amaterasu-web` reaches it over the compose network

## Role in the stack

```
amaterasu-web ─▶ /api ─▶ amaterasu-rest ─▶ Postgres   entities
                                        ├─▶ Redis      cache
                                        ├─▶ SSH        Docker hosts: compose files, docker compose, stats script
                                        └─▶ HTTPS      Proxmox API: clones, tasks, bridges
```

## Layout

| Package | Contents |
| ------- | -------- |
| `controllers` | REST endpoints. Lab actions are under `/api/labs` (`start`, `pause`, `stop`, `delete`), deployments under `/api/lab-tracker` |
| `services/alt` | Integrations: `DockerService` and `ProxmoxService` run the lab lifecycle, `RemoteCommandService` runs SSH commands, `LabActionService` picks one by lab type |
| `services/entity` | Entity services, including `LabService`, which owns lab and tracker state |
| `services/cron` | Scheduled jobs: server stats, lab file readiness, Proxmox VM status |
| `components` | Startup tasks and `LabActionGuard`, which keeps two actions from running on one lab at once |
| `models`, `repositories` | JPA entities, DTOs and Spring Data repositories |

## Running locally

Start Postgres and Redis from `amaterasu-dev`, then:

```bash
./gradlew bootRun
```

The `local` profile in `src/main/resources/application-local.yml` reads its settings from environment variables with local defaults (`DB_IP`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`, `REDIS_HOST`, `REDIS_PASSWORD`, `ENCRYPTION_KEY`, `DOCKER_COMPOSE_PATH`, `RSA_PRIVATE_KEY_PATH`, `RSA_PUBLIC_KEY_PATH`). Missing RSA keys are generated on first start.

## Tests

```bash
./gradlew test
```

Tests run against H2 with the settings in `src/test/resources/application.yml`; no Postgres, Redis, Docker host or Proxmox node is needed.

## Docker image

```bash
npm run docker:build
```

Builds the jar in a Gradle stage and runs it on `eclipse-temurin:21-jre-alpine` as uid 1001 under `tini`. `/var/tmp/amaterasu` is a volume for the JWT keys. Pass `--build-arg GIT_SHA=<commit>` to label the image with its revision.
