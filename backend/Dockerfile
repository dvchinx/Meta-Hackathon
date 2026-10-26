FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI (they need Docker for Testcontainers), not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package && cp target/backend-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S civic && adduser -S civic -G civic && mkdir -p /data/media && chown civic:civic /data/media
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER civic
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
