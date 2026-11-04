# Stage 1: Compile
FROM node:24-bookworm-slim AS compile-stage

WORKDIR /app/

# Install pnpm globally
RUN npm install -g pnpm@10

COPY --link *.json pnpm-lock.yaml pnpm-workspace.yaml /app/
COPY --link src/ /app/src/

RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
  --mount=type=secret,id=npmrc,dst=/root/.npmrc \
  set -eux \
  && pnpm install --frozen-lockfile \
  && pnpm run build:app

# Stage 2: Build runtime image
FROM nginx:alpine AS runtime-stage

LABEL maintainer="infernokun@infernokun.com" \
  description="ProjectAmaterasu web frontend" \
  org.opencontainers.image.source="https://github.com/infernokun/ProjectAmaterasu"

COPY --link --chown=101:101 nginx/nginx.conf nginx/security-headers.conf /etc/nginx/
RUN set -eux \
  && apk upgrade --no-cache \
  && chown 101:101 -R /var/cache/nginx \
  && chown 101:101 -R /var/log/nginx \
  && chown 101:101 -R /etc/nginx \
  && chown 101:101 -R /usr/share/nginx \
  && touch /var/run/nginx.pid \
  && chown 101:101 /var/run/nginx.pid \
  && rm -f /etc/nginx/conf.d/default.conf

COPY --link --chown=101:101 --from=compile-stage /app/dist/app/browser /usr/share/nginx/html/

COPY --chmod=755 ./scripts/docker-entrypoint.sh /docker-entrypoint.sh

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s CMD wget -q --spider http://localhost/health || exit 1

USER nginx

ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision=$GIT_SHA

ENTRYPOINT ["/docker-entrypoint.sh"]
