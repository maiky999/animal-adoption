# Docker image of the Łapka application (used by docker-compose.prod.yml).
# Two stages: the first one builds the jar with Maven, the second one only runs it.
# Thanks to that the final image contains just the JRE and the jar - no Maven, no source code.

# ---------- Stage 1: build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY translations translations
COPY domain domain
COPY core core
COPY web web

# Tests are skipped here - they are run on the developer's machine (./mvnw test) before deploying.
# The cache mount keeps downloaded dependencies between builds, so a rebuild after "git pull" is much faster.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package \
    && cp web/target/animaladoption-web-*.jar /build/app.jar

# ---------- Stage 2: run ----------
FROM eclipse-temurin:21-jre

# The application does not need root rights - running as a regular user limits the damage
# if someone ever found a vulnerability in it
RUN groupadd --system app && useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build /build/app.jar app.jar

# Uploaded photos are saved to /app/uploads (app.upload.dir = "uploads", relative to the working directory).
# docker-compose.prod.yml mounts a volume here, so the photos survive rebuilding the container.
RUN mkdir uploads && chown app:app uploads

USER app
EXPOSE 8080

# MaxRAMPercentage - the heap takes at most 75% of the memory limit given to the container (mem_limit)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
