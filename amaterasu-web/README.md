# amaterasu-web

Angular 22 frontend for ProjectAmaterasu.

## Development server

```bash
pnpm install
```

```bash
pnpm start
```

The app is served on `http://localhost:4300/`. Runtime settings such as the API URL come from `src/assets/environment/app.config.json`.

## Structure

| Folder | What lives there |
| ------ | ---------------- |
| `src/app/shell` | Sidebar, top bar and the signed-out landing page |
| `src/app/features/play` | Home, the Labs list and lab page, environment consoles, scoreboard (everyone) |
| `src/app/features/run` | Live event (facilitators and admins) |
| `src/app/features/build` | Labs with their scenario, questions and files, and the environment builder (creators and admins) |
| `src/app/features/admin` | Users and teams, servers, audit log (admins) |
| `src/app/features/account` | Profile, password, sessions, appearance |
| `src/app/core` | Signal stores shared across features, role definitions, theme, HTTP helpers |
| `src/app/shared/ui` | Shared components: page header, status badge, confirm dialog, empty state, icons |
| `src/styles` | Design tokens for light and dark, the Material theme and component styles |

Components are standalone and `OnPush`, keep their state in signals, and use the built-in control flow. Shared and feature state lives in `@ngrx/signals` stores. The app runs without zone.js, so anything that changes the view has to go through a signal. Routes are declared with `page()` from `core/page.ts`, and each area is guarded by role with `roleGuard`.

## Build

```bash
pnpm run build:prod
```

Output goes to `dist/app/browser`.

## Unit tests

```bash
pnpm exec ng test --watch=false --browsers=ChromeHeadlessCI
```

## Docker

The `Dockerfile` builds the app and serves it with nginx. At container start `scripts/docker-entrypoint.sh` fills `app.config.json` from the `BASE_URL` and `REST_URL` environment variables.
