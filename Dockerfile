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
# eclipse-temurin:17-jre-alpine is ~100 MB smaller than the full JDK image.
FROM eclipse-temurin:17-jre-alpine AS runtime

# Run as a non-root user — principle of least privilege.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
COPY --from=build /workspace/hex-application/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
