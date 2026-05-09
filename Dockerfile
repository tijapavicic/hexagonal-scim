# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy only POM files first so the dependency resolution layer is cached
# independently from source changes. The layer is invalidated only when a
# POM changes — not on every source edit.
COPY pom.xml                              ./
COPY hex-core/pom.xml                     hex-core/
COPY hex-inbound-adapter-web/pom.xml      hex-inbound-adapter-web/
COPY hex-outbound-adapter-db/pom.xml      hex-outbound-adapter-db/
COPY hex-application/pom.xml              hex-application/
RUN mvn -B --no-transfer-progress dependency:go-offline -q

# Copy source and build — only reaches here when source or a POM changed
COPY . .
RUN mvn -B --no-transfer-progress clean package -DskipTests

# ── Runtime stage ─────────────────────────────────────────────────────────────
# eclipse-temurin:17-jre-jammy (Ubuntu 22.04 LTS) ships a native arm64 manifest
# and an amd64 manifest — works on Apple Silicon and x86-64 CI runners without
# emulation.  The Alpine variant (17-jre-alpine) is amd64-only and fails on arm64.
FROM eclipse-temurin:17-jre-jammy AS runtime

# Run as a non-root user — principle of least privilege.
# Ubuntu syntax: addgroup/adduser are Debian-style (no -S flag).
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app
COPY --from=build /workspace/hex-application/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
