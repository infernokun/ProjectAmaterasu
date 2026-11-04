# Stage 1: Compile
FROM gradle:8.14.3-jdk21 AS compile-stage

WORKDIR /app/
COPY --link gradlew /app/gradlew
COPY --link gradle /app/gradle
COPY --link build.gradle settings.gradle package.json /app/
COPY --link src /app/src
RUN chmod +x gradlew

RUN gradle bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime-stage
LABEL maintainer="infernokun@infernokun.com" \
      description="ProjectAmaterasu REST API" \
      org.opencontainers.image.source="https://github.com/infernokun/ProjectAmaterasu"

ARG PROJECT=amaterasu-rest
ENV PROJECT=${PROJECT}

# apk upgrade picks up OS fixes the base tag has not been rebuilt with yet
RUN apk upgrade --no-cache && apk add --no-cache tini curl bash

# /var/tmp/amaterasu is a volume (JWT keys, lab files); it must exist and be owned by the app
# user here so a fresh named volume is seeded writable
RUN set -eux; \
    addgroup -S -g 1001 java; \
    adduser -S -D -u 1001 -G java java; \
    mkdir -p /app /var/tmp/amaterasu; \
    chown java:java -R /app /var/tmp/amaterasu

COPY --link --chown=1001:1001 scripts/docker-entrypoint.sh /
COPY --link --chown=1001:1001 --from=compile-stage /app/build/libs/${PROJECT}.jar /app/${PROJECT}.jar

RUN chmod +x /docker-entrypoint.sh

USER java
WORKDIR /app/

VOLUME ["/var/tmp/amaterasu"]
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://localhost:8080/amaterasu-rest/health || exit 1

# Last, so a new commit only rebuilds this layer
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision=$GIT_SHA

ENTRYPOINT ["/sbin/tini", "-g", "--", "/docker-entrypoint.sh"]
CMD ["/app/${PROJECT}.jar"]
