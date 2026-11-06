# ProjectAmaterasu

[![CI](https://github.com/infernokun/ProjectAmaterasu/actions/workflows/ci.yml/badge.svg)](https://github.com/infernokun/ProjectAmaterasu/actions/workflows/ci.yml)

ProjectAmaterasu is a virtual lab orchestration application. It deploys Docker Compose and Proxmox VM labs to remote servers, gives every team its own isolated copy, and runs CTF style rooms with scoring.

## Components

| Directory | What it is |
| --------- | ---------- |
| [`amaterasu-web`](amaterasu-web/README.md) | Angular 22 frontend served by nginx, which also proxies `/api` to the API |
| [`amaterasu-rest`](amaterasu-rest/README.md) | Spring Boot 3.5 REST API (Java 21). Postgres for storage, Redis for caching |
| [`amaterasu-proxmox`](amaterasu-proxmox/README.md) | Python helper CLI for Proxmox, and a Proxmox API mock under `mock/` |
| [`amaterasu-populate`](amaterasu-populate/README.md) | Scripts that seed sample rooms and challenges through the API |
| `amaterasu-dev` | Compose files for local Postgres, pgAdmin, Redis and a Docker-in-Docker SSH host |
| `amaterasu-sample` | Example lab compose file |

## Architecture

```
Browser ──▶ amaterasu-web (nginx, :8764)
                │  /api
                ▼
            amaterasu-rest (:8765, loopback) ──▶ Postgres   users, teams, labs, trackers, CTF data
                │                             └─▶ Redis      cache
                ├── SSH ──────▶ Docker hosts   one Compose project per lab deployment
                └── HTTPS ────▶ Proxmox nodes  template clones on per-lab bridges
```

## Quick start

```bash
wget https://raw.githubusercontent.com/infernokun/ProjectAmaterasu/refs/heads/main/docker-compose.yml https://raw.githubusercontent.com/infernokun/ProjectAmaterasu/refs/heads/main/.env.example
```

```bash
cp .env.example .env
```

Set the passwords and `ENCRYPTION_KEY` in `.env`, then:

```bash
docker compose --env-file .env up -d
```

The web app is on port `8764`. The API listens on `127.0.0.1:8765` (context path `/amaterasu-rest`) and is reached through nginx at `/api`. Serve it over HTTPS outside a lab network; if TLS ends at a proxy in front of the web container, have that proxy send `X-Forwarded-Proto`.

Upgrading from a version that defaulted `ENCRYPTION_KEY` to `secret_key`: the API now refuses that key. Set a new one, then re-enter the password or API token of each remote server, since they were encrypted with the old key.

On first start the API creates the `amaterasu_admin` account with the password from `ADMIN_PASSWORD`. If that is blank, a random password is generated and printed once in the API log:

```bash
docker compose logs amaterasu-rest | grep "generated password"
```

It also generates the RSA key pair it signs JWTs with into the `amaterasu-rest` volume.

## Accounts and access

Every API route except login, registration, token refresh and the health check needs a bearer token, and so does the live update socket. Access tokens last 15 minutes and the web app keeps them in memory only. The refresh token is an HttpOnly cookie scoped to `/api/auth`, so page scripts can't read it. It is rotated on every use, expires after 7 days idle or 30 days in total, and reusing an old one signs that user out everywhere.

Failed logins and the registration and refresh rate limits are counted in Redis, so they hold across restarts and API instances. If Redis is unreachable the API counts in memory until it is back.

| Role | Can |
| ---- | --- |
| `MEMBER` | Join rooms, submit flags, run their own team's labs |
| `FACILITATOR` | Member access, plus awarding points |
| `CREATOR` | Member access, plus authoring labs, rooms and challenges |
| `ADMIN` | Everything, including users, teams and remote servers |

Self registration always creates a `MEMBER`. Repeated failed logins are throttled per username and per client address.

| Endpoint | Who | Does |
| -------- | --- | ---- |
| `POST /api/auth/password` | Any user | Changes their own password and signs out their other sessions |
| `PUT /api/user/{id}/password` | Admin | Sets a user's password and signs them out |
| `PUT /api/user/{id}/disabled` | Admin | Disables or re-enables an account; a disabled user is rejected on the next request |

## Lab lifecycle

| Action | Docker Compose lab | Proxmox VM lab |
| ------ | ------------------ | -------------- |
| Start  | Copies the lab's compose file for this deployment and runs `up -d` | Clones every template, puts the clones on private bridges for this lab and starts them |
| Pause  | `stop`: containers are kept, so progress survives | Graceful shutdown: VMs and disks are kept |
| Resume | `up -d` on the existing containers | Starts the existing VMs |
| Stop   | `down -v`: removes containers, volumes and uploaded files | Destroys the clones and removes the lab's bridges |
| Delete | Stop, then removes the lab from the team | Stop, then removes the lab from the team |

Each deployment is its own Compose project, so published ports and fixed container, volume and network names are dropped to let several teams run the same lab on one host. Each VM lab gets its own `abrN` bridges with no host address, so labs cannot reach each other or the Proxmox host's networks. Students reach VMs through the Proxmox console.

A template tagged `bastion` in Proxmox is a lab's way in over SSH: its `net0` stays on the bridge the template has (the uplink, e.g. `vmbr0`) and its other NICs join the lab bridge. Every clone NIC gets a fresh MAC. Each copy gets its own password for the templates' cloud-init user (`ciuser`), set before the first boot and shown only to the owning team and admins. The bastion's LAN address is read from its guest agent and shown with the password on the lab card and page.

## Build and development

The root `package.json` wraps the common commands:

| Command | What it does |
| ------- | ------------ |
| `npm run start:rest` | API on `http://localhost:8080` (needs Postgres and Redis, see `amaterasu-dev`) |
| `npm run start:web` | Web dev server on `http://localhost:4300` |
| `npm run start:mock` | Proxmox API mock on `https://127.0.0.1:8006` |
| `npm run test:rest` | API tests |
| `npm run test:web` | Web unit tests in headless Chrome |
| `npm run lint:python` | Ruff over the Python code |
| `npm run deps:lock` | Recompile the hash-locked Python requirements |

### Python dependencies

Python requirements are managed with [uv](https://docs.astral.sh/uv/). Each `requirements.in` lists the direct dependencies, and `requirements.txt` is the hash-locked file compiled from it. Edit the `.in` file, then run `npm run deps:lock` from the repo root and commit both. `npm run deps:upgrade` moves everything to the latest versions the `.in` pins allow. CI fails if a `requirements.txt` is out of date with its `.in`.

```bash
uv venv && uv pip install --require-hashes -r requirements-dev.txt -r amaterasu-proxmox/requirements.txt
```

## Configuration

Every compose setting has a default; see [`.env.example`](.env.example) for the full list.

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `REGISTRY` / `NAMESPACE` / `TAG` | `docker.io` / `infernokun` / `latest` | Where the web and API images are pulled from |
| `WEB_PORT` | `8764` | Published web port |
| `REST_BIND` / `REST_PORT` | `127.0.0.1` / `8765` | Published API address and port |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `amaterasu` | Database credentials |
| `REDIS_PASSWORD` | `amaterasu` | Redis password |
| `ENCRYPTION_KEY` | required | Encrypts stored server passwords and API tokens. At least 32 characters; the API will not start with a short or default key |
| `ADMIN_PASSWORD` | generated | Password for `amaterasu_admin`, used only when that account is first created |
| `ALLOWED_ORIGINS` | same origin only | Other origins allowed to call the API from a browser, comma-separated |
| `DOCKER_COMPOSE_PATH` | `/var/tmp/amaterasu` | Working directory for lab files on each Docker host |
| `RSA_PRIVATE_KEY_PATH` / `RSA_PUBLIC_KEY_PATH` | `/var/tmp/amaterasu/*.pem` | JWT keys, generated on first start when missing |

## CI

`.github/workflows/ci.yml` runs on every pull request and push to `main`:

- `amaterasu-rest`: `./gradlew build`, which compiles and runs the tests
- `amaterasu-web`: install, production build and unit tests
- `python`: lockfile check, Ruff, and a smoke run of the Proxmox helper
- Docker builds of the rest, web and proxmox images, labelled with the commit

## Troubleshooting

```bash
docker compose logs amaterasu-rest
```

The API health endpoint is `http://localhost:8765/amaterasu-rest/health`, and the web container answers `/health`.
