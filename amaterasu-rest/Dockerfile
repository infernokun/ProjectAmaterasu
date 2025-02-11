# syntax=docker/dockerfile:1.4.2
# Stage 1: Compile
FROM openjdk:21-jdk-slim AS compile-stage

RUN apt-get update && apt-get install -y coreutils gradle

WORKDIR /app/
COPY --link gradlew /app/gradlew
COPY --link gradle /app/gradle
COPY --link build.gradle /app/build.gradle
COPY --link settings.gradle /app/settings.gradle
COPY --link src /app/src
RUN chmod +x gradlew

RUN ./gradlew build -x test --no-daemon

# Stage 2: Runtime
FROM amazoncorretto:22 AS runtime-stage
LABEL maintainer="infernokun@infernokun.com" \
    description="Java service Docker image" \
    source="https://gitlab.corp.icr-team.com/Unicorn" \
    run="docker run -p 8080:8080 <docker image> -d"

VOLUME ["/data/certs"]

# Install required utilities (groupadd, useradd, etc.)
RUN yum install -y shadow-utils

#COPY --link --from=compile-stage /etc/ssl/certs /etc/ssl/certs
RUN set -eux \
    && groupadd -g 1000 java \
    && useradd -m -r -u 1000 -g java java \
    && mkdir -p /app \
    && chown java:java -R /app /home/java

ENV JAVA_TOOL_OPTIONS='-XX:-TieredCompilation'

ARG PROJECT
ENV PROJECT=${PROJECT}

# Copy the correct location for the built JAR (usually in build/libs)
COPY --link --chown=java:java scripts/docker-entrypoint.sh /
COPY --link --chown=java:java --from=compile-stage /app/build/libs/${PROJECT}*.jar /app/${PROJECT}.jar

USER java
WORKDIR /app/

EXPOSE 8080

ENTRYPOINT ["tini", "-g", "--", "/docker-entrypoint.sh"]
CMD ["/app/${PROJECT}.jar", "${JAVA_TOOL_OPTIONS}"]
