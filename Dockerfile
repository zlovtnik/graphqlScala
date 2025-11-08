# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew \
    && ./gradlew clean bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/ssf-0.0.1-SNAPSHOT.jar app.jar

# Create non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup -h /app -s /sbin/nologin

# Change ownership of application files
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

ENV JAVA_OPTS=""
EXPOSE 8443

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
