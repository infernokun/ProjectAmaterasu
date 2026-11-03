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
