# syntax=docker/dockerfile:1.4.2
# Stage 1: Compile
FROM node:lts-bullseye AS compile-stage

WORKDIR /app/

# Install pnpm globally
RUN npm install -g pnpm

COPY --link *.json /app/
COPY --link pnpm-lock.yaml /app/
COPY --link src/ /app/src

RUN --mount=type=cache,target=/root/.npm/_cacache \
    --mount=type=secret,id=npmrc,dst=/root/.npmrc \
    set -eux \
    && pnpm install --frozen-lockfile \
    && pnpm run build:app

# Stage 2: Build runtime image
FROM nginx:alpine AS runtime-stage

LABEL maintainer="infernokun@infernokun.com" \
  description="NGINX Angular Docker image" \
  source="https://github.com/infernokun/ProjectAmaterasu" \
  run="docker run -p 80:80 <docker image> -d"

VOLUME ["/data/nginx"]

# user ID 101 is nginx
COPY --link --chown=101:101 nginx/nginx.conf /etc/nginx/nginx.conf
RUN set -eux \
  && chown 101:101 -R /var/cache/nginx \
  && chown 101:101 -R /var/log/nginx \
  && chown 101:101 -R /etc/nginx \
  && chown 101:101 -R /usr/share/nginx \
  && touch /var/run/nginx.pid \
  && chown 101:101 -R /var/run/nginx.pid \
  && rm /etc/nginx/conf.d/default.conf

COPY --link --chown=101:101 --from=compile-stage /app/dist/app/browser /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --start-period=5s CMD curl --fail http://localhost/ || exit 1

# Start Nginx server
CMD ["nginx", "-g", "daemon off;"]
