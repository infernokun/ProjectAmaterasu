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
FROM eclipse-temurin:21-jre-noble AS runtime-stage
LABEL maintainer="infernokun@infernokun.com" \
      description="Java service Docker image" \
      source="https://github.com/infernokun/ProjectAmaterasu" \
      run="docker run -p 8080:8080 <docker image> -d"

ARG PROJECT=amaterasu-rest
ENV PROJECT=${PROJECT}

RUN apt-get update && apt-get install -y --no-install-recommends \
    tini \
    curl \
 && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    groupadd -g 1001 java; \
    useradd -m -r -u 1001 -g java java; \
    mkdir -p /app /var/tmp/amaterasu; \
    chown java:java -R /app /home/java /var/tmp/amaterasu

COPY --link --chown=1001:1001 scripts/docker-entrypoint.sh /
COPY --link --chown=1001:1001 --from=compile-stage /app/build/libs/${PROJECT}.jar /app/${PROJECT}.jar

RUN chmod +x /docker-entrypoint.sh

USER java
WORKDIR /app/

VOLUME ["/var/tmp/amaterasu"]
EXPOSE 8080

HEALTHCHECK --start-period=60s CMD curl -fs http://localhost:8080/amaterasu-rest/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "-g", "--", "/docker-entrypoint.sh"]
CMD ["/app/${PROJECT}.jar"]
