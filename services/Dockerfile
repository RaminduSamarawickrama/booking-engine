# syntax=docker/dockerfile:1.7
# One Dockerfile for every Spring Boot service in this Gradle multi-project build.
#   docker build --build-arg SERVICE=booking -t booking-service ./services
# Each service sets `tasks.bootJar { archiveFileName.set("app.jar") }` (see build conventions).

FROM eclipse-temurin:21-jdk-alpine AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    test -n "$SERVICE" && ./gradlew --no-daemon -q ":${SERVICE}:bootJar" -x test
RUN java -Djarmode=tools -jar "${SERVICE}/build/libs/app.jar" extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
# Layers ordered from least to most frequently changed, so rebuilds reuse the cache.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
