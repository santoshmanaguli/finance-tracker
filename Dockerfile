# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Multi-stage build.
#   Stage 1 (build): full JDK + Maven, compiles and packages the JAR.
#   Stage 2 (run):   JRE only. No compiler, no Maven, no source code.
# The final image is smaller and has less attack surface.
# ---------------------------------------------------------------------------

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1) Copy only what dependency resolution needs. Docker caches this layer, so
#    dependency download is skipped on rebuilds unless pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# 2) Now the source. Tests run in CI (they need Docker for Testcontainers),
#    not inside the image build.
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# 3) Split the fat JAR into layers (dependencies change rarely, our code often).
RUN java -Djarmode=tools -jar target/finance-tracker-*.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run as root inside a container: a compromised app would be root in it.
RUN addgroup -S app && adduser -S app -G app
USER app

# Least-frequently-changing layers first, so a code change only re-ships the
# last (small) layer instead of ~60 MB of dependencies.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080

# Container-aware JVM: size the heap from the container's memory limit,
# not the host's RAM. Fail fast on OOM so Docker restarts a clean process.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Exec form (JSON array): java is PID 1 and receives SIGTERM directly,
# which triggers Spring's graceful shutdown.
ENTRYPOINT ["java", "-jar", "finance-tracker-0.1.0-SNAPSHOT.jar"]
