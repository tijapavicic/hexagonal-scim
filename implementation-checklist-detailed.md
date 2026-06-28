# New Spring Boot Service — Developer Playbook

> **Principal Engineer Review: 5.5/10 — Competent skeleton, dangerously incomplete for production.**  
> Multiple `[x]` items below were stub implementations or did not exist when reviewed on 2026-06-29.  
> Items marked ⚠️ were found to be **incorrectly checked** — treat them as `[ ]` until re-verified.  
> See gap analysis notes inline for each critical finding.

> **"Build it right from day one. Retrofitting security is 10x more expensive than starting secure."**  
> Follow this playbook top-to-bottom for every new service. No skipping steps.

---

## How to Use This Guide

```
┌─────────────────────────────────────────────────────────┐
│  PHASE 1: Bootstrap     ── Scaffold + structure         │
│  PHASE 2: Core API      ── DTOs, controllers, services  │
│  PHASE 3: Security      ── Auth, validation, encoding   │
│  PHASE 4: Data          ── DB, migrations, encryption   │
│  PHASE 5: Observability ── Logging, metrics, tracing    │
│  PHASE 6: Testing       ── Unit, integration, security  │
│  PHASE 7: CI/CD         ── Pipeline, scanning, SBOM     │
│  PHASE 8: Go-Live       ── Final checks before deploy   │
└─────────────────────────────────────────────────────────┘
```

**Legend:**
- ✅ Must-do — blocks deployment if missing
- ⚡ Should-do — strongly recommended
- 📖 Reference — links to deeper guides

**Reference documents:**
- [`securing-spring-boot-api.md`](./securing-spring-boot-api.md) — full security implementation guide
- [`TESTING_PRESENTATION.md`](./TESTING_PRESENTATION.md) — testing strategy, SAST, SCA, EPSS, DAST

---

## Phase 1: Bootstrap — Scaffold & Structure

### Step 1.1 — Generate Project

```bash
# Option A: Spring Initializr CLI
curl -s "https://start.spring.io/starter.tgz" \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.3.5 \
  -d baseDir=my-service \
  -d groupId=com.example.user \
  -d artifactId=my-service \
  -d name=my-service \
  -d javaVersion=21 \
  -d dependencies=web,validation,security,data-jpa,flyway,actuator,oauth2-resource-server \
  | tar -xzvf -

# Option B: IDEA / Spring Initializr web → https://start.spring.io
```

**Required dependencies at project creation:**

| Dependency | Starter | Why |
|-----------|---------|-----|
| Spring Web | `spring-boot-starter-web` | REST API |
| Validation | `spring-boot-starter-validation` | Input validation (`@Valid`) |
| Spring Security | `spring-boot-starter-security` | Auth + security filters |
| OAuth2 Resource Server | `spring-boot-starter-oauth2-resource-server` | JWT validation |
| Spring Data JPA | `spring-boot-starter-data-jpa` | Repository layer |
| Flyway | `flyway-core` | DB migrations |
| Actuator | `spring-boot-starter-actuator` | Health/readiness probes |
| Lombok | `lombok` | Reduce boilerplate |
| PostgreSQL Driver | `postgresql` | Production DB driver |
| Testcontainers | `spring-boot-testcontainers` | Integration tests |

### Step 1.2 — Apply Hexagonal Module Structure

```
my-service/
├── src/main/java/com/example/user/
│   ├── api/                          # Inbound adapter — REST controllers
│   │   ├── dto/                      # Request/Response DTOs (NEVER domain models)
│   │   └── MyController.java
│   ├── config/                       # Spring configuration
│   │   ├── SecurityConfig.java
│   │   ├── JacksonConfig.java
│   │   └── security/                 # Security components
│   │       ├── InputSanitizer.java
│   │       ├── SecurityHeadersFilter.java
│   │       ├── RateLimitFilter.java
│   │       └── GlobalExceptionHandler.java
│   ├── core/                         # Business logic (hexagonal core)
│   │   ├── model/                    # Domain models — NO Spring/JPA annotations
│   │   ├── port/
│   │   │   ├── in/                   # Input ports (use-case interfaces)
│   │   │   └── out/                  # Output ports (repository interfaces)
│   │   └── service/                  # Use-case implementations
│   └── adapter/
│       ├── db/                       # Outbound adapter — JPA entities + repositories
│       │   ├── entity/
│       │   └── repository/
│       └── client/                   # Outbound adapter — external HTTP clients
│
├── src/main/resources/
│   ├── application.yml               # Config (NO secrets — use env vars)
│   ├── application-local.yml         # Local dev overrides (gitignored)
│   └── db/migration/                 # Flyway migration scripts
│       └── V1__initial_schema.sql
│
├── src/test/java/com/example/user/
│   ├── api/                          # Controller tests (@WebMvcTest)
│   ├── core/                         # Service unit tests
│   └── integration/                  # Integration tests (Testcontainers)
│
├── scripts/
│   └── epss-check.sh                 # EPSS vulnerability enrichment script
│
├── .github/workflows/
│   ├── ci.yml                        # Main CI pipeline
│   ├── sast.yml                      # Semgrep SAST
│   ├── sca.yml                       # Snyk SCA
│   └── sbom.yml                      # SBOM generation
│
└── pom.xml
```

### Step 1.3 — Bootstrap pom.xml

```xml
<!-- pom.xml — copy this base, add service-specific dependencies -->
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
    </parent>

    <properties>
        <java.version>21</java.version>
        <owasp.dependency-check.version>9.2.0</owasp.dependency-check.version>
        <cyclonedx.version>2.7.11</cyclonedx.version>
        <spotbugs.version>4.8.3.1</spotbugs.version>
    </properties>

    <dependencies>
        <!-- Core -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Security: input sanitization -->
        <dependency>
            <groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
            <artifactId>owasp-java-html-sanitizer</artifactId>
            <version>20220608.1</version>
        </dependency>
        <dependency>
            <groupId>org.owasp.encoder</groupId>
            <artifactId>encoder</artifactId>
            <version>1.3.1</version>
        </dependency>

        <!-- Rate limiting -->
        <dependency>
            <groupId>com.github.bucket4j</groupId>
            <artifactId>bucket4j-core</artifactId>
            <version>8.10.1</version>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>7.4</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- SBOM generation -->
            <plugin>
                <groupId>org.cyclonedx</groupId>
                <artifactId>cyclonedx-maven-plugin</artifactId>
                <version>${cyclonedx.version}</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>makeAggregateBom</goal></goals>
                    </execution>
                </executions>
            </plugin>

            <!-- CVE scanning -->
            <plugin>
                <groupId>org.owasp</groupId>
                <artifactId>dependency-check-maven</artifactId>
                <version>${owasp.dependency-check.version}</version>
                <configuration>
                    <failBuildOnCVSS>7</failBuildOnCVSS>
                </configuration>
            </plugin>

            <!-- Bug detection -->
            <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <version>${spotbugs.version}</version>
                <configuration>
                    <effort>Max</effort>
                    <threshold>Low</threshold>
                    <failOnError>true</failOnError>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### ✅ Phase 1 Checklist

- [ ] Spring Initializr project generated with Java 21
- [ ] Hexagonal directory structure created
- [ ] All required dependencies added to `pom.xml`
- [ ] Security plugins (Dependency-Check, SpotBugs, CycloneDX) configured
- [ ] `mvn clean compile` passes with zero errors

---

## Phase 2: Core API — Controllers, DTOs, Services

### Step 2.1 — Define Request/Response DTOs First

> 📖 See `securing-spring-boot-api.md` → **Request Validation** section

**Template: Request DTO**

```java
package com.example.user.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/**
 * Request DTO — represents what the client sends.
 *
 * Rules:
 * 1. ALL fields must have explicit constraints — no implicit trust
 * 2. Use @Size to prevent buffer overflow and DB truncation
 * 3. Use @Pattern for format enforcement (block injection chars)
 * 4. Use records for immutability — no setters
 * 5. NEVER use domain model as request DTO
 */
public record CreateXxxRequest(

    @NotBlank(message = "name is required")
    @Size(min = 1, max = 100, message = "name must be 1–100 characters")
    @Pattern(regexp = "^[\\p{L}\\d\\s'\\-\\.]+$", message = "name contains invalid characters")
    String name,

    @NotBlank(message = "description is required")
    @Size(max = 1000, message = "description must not exceed 1000 characters")
    String description,

    @NotNull(message = "status is required")
    Status status

) {
    public enum Status { ACTIVE, INACTIVE }
}
```

**Template: Response DTO**

```java
package com.example.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO — represents what we send back to clients.
 *
 * Rules:
 * 1. Use PUBLIC UUID — NEVER expose internal DB Long IDs
 * 2. Explicitly choose every field — no @JsonIgnoreProperties
 * 3. No passwords, secrets, internal-only fields
 * 4. Include static factory fromDomain() — keeps domain clean
 */
public record XxxResponse(
    UUID id,                // Public UUID — not DB Long
    String name,
    String description,
    String status,
    Instant createdAt
) {
    public static XxxResponse fromDomain(Xxx domain) {
        return new XxxResponse(
            domain.publicId(),
            domain.name(),
            domain.description(),
            domain.status().name(),
            domain.createdAt()
        );
    }
}
```

### Step 2.2 — Define Input Ports (Use-Case Interfaces)

```java
package com.example.user.core.port.in;

// ✅ ISP: one interface per use-case (not a fat interface)
public interface CreateXxxPort {
    XxxResponse execute(CreateXxxCommand command);
}

public interface GetXxxPort {
    Optional<XxxResponse> getById(UUID id);
    Page<XxxResponse> search(XxxSearchCriteria criteria, Pageable pageable);
}

public interface UpdateXxxPort {
    XxxResponse execute(UpdateXxxCommand command);
}
```

### Step 2.3 — Write Controller (Thin — no business logic)

```java
package com.example.user.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST Controller — thin adapter. Handles HTTP only.
 *
 * Controller responsibilities:
 * ✅ HTTP method/path routing
 * ✅ @Valid on request bodies
 * ✅ Map port commands/responses to/from HTTP
 * ✅ Structured logging (3-stage: RECEIVED, COMPLETED, PREPARED)
 * ✅ Authorization annotations
 *
 * Controller NOT responsible for:
 * ❌ Business logic
 * ❌ Database calls
 * ❌ Exception handling (use GlobalExceptionHandler)
 * ❌ Input sanitization (use InputSanitizer / filter)
 */
@RestController
@RequestMapping("/api/v1/xxxs")
public class XxxController {

    private static final Logger log = LoggerFactory.getLogger(XxxController.class);

    private final CreateXxxPort createPort;
    private final GetXxxPort getPort;

    public XxxController(CreateXxxPort createPort, GetXxxPort getPort) {
        this.createPort = createPort;
        this.getPort = getPort;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<XxxResponse> create(
        @Valid @RequestBody CreateXxxRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        // Stage 1: REQUEST_RECEIVED
        log.info("flow_stage=REQUEST_RECEIVED operation=xxx.create userId={} status=INITIATED", userId);

        XxxResponse response = createPort.execute(
            new CreateXxxCommand(userId, request.name(), request.description(), request.status())
        );

        // Stage 2: DOMAIN_OPERATION_COMPLETED
        log.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=xxx.create id={} userId={} status=SUCCESS",
            response.id(), userId);

        // Stage 3: RESPONSE_PREPARED
        log.info("flow_stage=RESPONSE_PREPARED operation=xxx.create id={} status=COMPLETED", response.id());

        return ResponseEntity
            .created(URI.create("/api/v1/xxxs/" + response.id()))
            .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<XxxResponse> getById(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("flow_stage=REQUEST_RECEIVED operation=xxx.get id={} userId={} status=INITIATED",
            id, jwt.getSubject());

        return getPort.getById(id)
            .map(resp -> {
                log.info("flow_stage=RESPONSE_PREPARED operation=xxx.get id={} status=COMPLETED", id);
                return ResponseEntity.ok(resp);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
```

### ✅ Phase 2 Checklist

- [ ] All request DTOs use Java `record` (immutable)
- [ ] All request DTO fields have `@NotNull`/`@NotBlank`/`@Size`/`@Pattern`
- [ ] All response DTOs expose `UUID` not internal `Long` IDs
- [ ] Response DTOs have `static fromDomain()` factory — no domain model leakage
- [ ] Input ports are narrow (one interface per use-case — ISP)
- [ ] Controller has zero business logic — only HTTP mapping
- [ ] 3-stage structured logging in every controller method
- [ ] `@PreAuthorize` on every endpoint
- [ ] `@Valid` on every `@RequestBody`

---

## Phase 3: Security — Auth, Validation, Headers, Rate Limiting

> 📖 Full implementation: [`securing-spring-boot-api.md`](./securing-spring-boot-api.md)

### Step 3.1 — Security Configuration

```java
package com.example.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // ── Public ───────────────────────────────────────────
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // ── Add your public routes here ──────────────────────
                // .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()

                // ── Authenticated ────────────────────────────────────
                .requestMatchers("/api/v1/**").authenticated()

                // ── DEFAULT DENY ALL ─────────────────────────────────
                .anyRequest().denyAll()          // ← Non-negotiable for any service
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
            )
            .headers(headers -> headers
                .frameOptions(f -> f.deny())
                .contentTypeOptions(c -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
            )
            .build();
    }

    // jwtConverter() — copy from securing-spring-boot-api.md
}
```

### Step 3.2 — Copy Security Components

Copy the following classes from `securing-spring-boot-api.md` into `config/security/`:

| Class | File | What It Does |
|-------|------|--------------|
| `InputSanitizer` | `InputSanitizer.java` | Cleans user input (XSS, path traversal, log injection) |
| `RequestSanitizationFilter` | `RequestSanitizationFilter.java` | Applies sanitization to all query params |
| `SecurityHeadersFilter` | `SecurityHeadersFilter.java` | Adds CSP, X-Frame-Options, HSTS, etc. |
| `RateLimitFilter` | `RateLimitFilter.java` | Token bucket rate limiting per IP |
| `GlobalExceptionHandler` | `GlobalExceptionHandler.java` | Safe error responses (no stack traces) |
| `OutputEncoder` | `OutputEncoder.java` | Context-specific output encoding |

```bash
# Verify all security filters are registered
mvn spring-boot:run &
curl -i http://localhost:8080/api/v1/xxxs 2>/dev/null | grep -E "X-Frame|X-Content|Content-Security"
# Expected output:
# X-Frame-Options: DENY
# X-Content-Type-Options: nosniff
# Content-Security-Policy: default-src 'self'; ...
```

### Step 3.3 — application.yml Security Baseline

```yaml
# application.yml — MANDATORY security settings for every new service

spring:
  application:
    name: my-service

  # Jackson — safe JSON serialization
  jackson:
    deserialization:
      fail-on-unknown-properties: true     # Reject unexpected fields
    serialization:
      write-dates-as-timestamps: false     # ISO-8601 dates

  # Security — OAuth2 JWT
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}   # From env var — NEVER hardcode

  # Datasource — from env vars ONLY
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000

  # Flyway — auto-migrate on startup
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
    out-of-order: false

# Server
server:
  port: 8080
  # NEVER expose stack traces
  error:
    include-stacktrace: never
    include-message: never
    include-binding-errors: never
    include-exception: false

# Actuator — minimal exposure
management:
  endpoints:
    web:
      exposure:
        include: health,info          # ONLY these two
  endpoint:
    health:
      show-details: never
  info:
    env:
      enabled: false

# Request size limits — DoS protection
spring.servlet.multipart.max-file-size: 10MB
spring.servlet.multipart.max-request-size: 10MB
server.tomcat.max-http-form-post-size: 2MB
```

### Step 3.4 — Add Custom Validators for Domain-Specific Fields

```java
// Add to src/main/java/com/example/user/config/security/validation/

// Example: Validate currency codes
@ValidCurrencyCode               // From securing-spring-boot-api.md

// Example: Validate monetary amounts (prevents negative-price attacks)
@ValidMonetaryAmount             // From securing-spring-boot-api.md

// Create new validators for YOUR domain:
// - @ValidOrderStatus
// - @ValidProductCategory
// - @ValidSellerTier
```

### ✅ Phase 3 Checklist

- [ ] `SecurityConfig` has `denyAll()` as default rule
- [ ] `SessionCreationPolicy.STATELESS` set
- [ ] JWT resource server configured (`issuer-uri` from env var)
- [ ] `@EnableMethodSecurity` enabled
- [ ] `InputSanitizer` component registered
- [ ] `RequestSanitizationFilter` in filter chain (Order 1)
- [ ] `SecurityHeadersFilter` in filter chain (Order 2)
- [ ] `RateLimitFilter` in filter chain
- [ ] `GlobalExceptionHandler` — returns safe errors, never stack traces
- [ ] `server.error.include-stacktrace=never` in `application.yml`
- [ ] Actuator exposed ONLY `health` and `info`
- [ ] All secrets in env vars — zero hardcoded values in any file
- [ ] Request size limits configured (prevents large payload DoS)
- [ ] Verify security headers present: `curl -i http://localhost:8080/actuator/health`

---

## Phase 4: Data Layer — Migrations, Entities, Repositories

### Step 4.1 — First Flyway Migration

```sql
-- src/main/resources/db/migration/V1__initial_schema.sql

-- Convention: always use UUID as public ID, Long as internal PK
CREATE TABLE xxxs (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255) NOT NULL,           -- userId from JWT
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0        -- Optimistic locking
);

-- Index on public_id (used by API lookups)
CREATE INDEX idx_xxxs_public_id ON xxxs(public_id);
CREATE INDEX idx_xxxs_created_by ON xxxs(created_by);
CREATE INDEX idx_xxxs_status ON xxxs(status);

-- Prevent UPDATE to created_at
CREATE OR REPLACE FUNCTION prevent_created_at_update()
RETURNS TRIGGER AS $$
BEGIN
    NEW.created_at = OLD.created_at;  -- Immutable
    NEW.updated_at = NOW();           -- Auto-update
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER xxxs_immutable_created_at
    BEFORE UPDATE ON xxxs
    FOR EACH ROW EXECUTE FUNCTION prevent_created_at_update();
```

### Step 4.2 — JPA Entity

```java
package com.example.user.adapter.db.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity — infrastructure concern only.
 *
 * Rules:
 * ✅ Has @Version for optimistic locking (prevents lost updates)
 * ✅ Exposes publicId (UUID) — NOT internal id (Long) to outside world
 * ✅ Tracks created_by (user ID from JWT)
 * ✅ Immutable created_at, auto-updated updated_at
 * ❌ Domain logic does NOT belong here
 */
@Entity
@Table(name = "xxxs")
public class XxxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;  // Optimistic locking — REQUIRED for financial/concurrent data
}
```

### Step 4.3 — Repository with Security Filters

```java
package com.example.user.adapter.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository — data access only.
 *
 * Security patterns:
 * ✅ Filter by owner at DB level (not application level)
 * ✅ Use named parameters (never string concatenation)
 * ✅ findByPublicId not findById (don't expose Long)
 */
public interface XxxJpaRepository extends JpaRepository<XxxEntity, Long> {

    // Use public UUID for all API-facing lookups
    Optional<XxxEntity> findByPublicId(UUID publicId);

    // Owner-scoped query — enforce at DB level
    @Query("SELECT x FROM XxxEntity x WHERE x.publicId = :publicId AND x.createdBy = :userId")
    Optional<XxxEntity> findByPublicIdAndOwner(@Param("publicId") UUID publicId,
                                                @Param("userId") String userId);

    // NEVER return all records — always filter/page
    @Query("SELECT x FROM XxxEntity x WHERE x.createdBy = :userId ORDER BY x.createdAt DESC")
    org.springframework.data.domain.Page<XxxEntity> findByOwner(
        @Param("userId") String userId,
        org.springframework.data.domain.Pageable pageable
    );
}
```

### ✅ Phase 4 Checklist

- [ ] Flyway migration `V1__initial_schema.sql` created
- [ ] Schema uses `BIGSERIAL` internal PK + `UUID` public ID
- [ ] `version` column added for optimistic locking
- [ ] `created_by` column tracks JWT subject
- [ ] `updated_at` auto-updates via trigger
- [ ] JPA entity has `@Version` annotation
- [ ] Repository always filters by owner at DB level
- [ ] No raw string concatenation in any `@Query`
- [ ] `mvn flyway:info` shows migration applied cleanly
- [ ] `mvn test` passes with Testcontainers integration test for migration

---

## Phase 5: Observability — Logging, Metrics, Tracing

### Step 5.1 — Structured Logging (Splunk/ELK Ready)

```xml
<!-- src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- JSON structured output for production -->
    <springProfile name="prod,staging">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>requestId</includeMdcKeyName>
            </encoder>
        </appender>

        <!-- Separate security audit log — never mix with app logs -->
        <appender name="SECURITY_AUDIT" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>/var/log/app/security-audit.log</file>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>/var/log/app/security-audit.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
                <maxHistory>365</maxHistory>       <!-- 12 months — regulatory requirement -->
            </rollingPolicy>
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>

        <logger name="SECURITY_AUDIT" level="INFO" additivity="false">
            <appender-ref ref="SECURITY_AUDIT"/>
        </logger>

        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>

    <!-- Human-readable for local dev -->
    <springProfile name="local,default">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

### Step 5.2 — MDC Request Tracing Filter

```java
package com.example.user.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds traceId and requestId to MDC for every request.
 * All log entries for a request share the same traceId — enables Splunk correlation.
 */
@Component
@Order(0)    // Must be first — before all other filters
public class MdcTracingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest req = (HttpServletRequest) request;

            // Use incoming trace header (from API gateway) or generate new one
            String traceId = req.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }

            MDC.put("traceId", traceId);
            MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
            MDC.put("method", req.getMethod());
            MDC.put("path", req.getRequestURI());

            // Set response header so clients can correlate
            ((jakarta.servlet.http.HttpServletResponse) response)
                .setHeader("X-Trace-Id", traceId);

            chain.doFilter(request, response);
        } finally {
            MDC.clear();  // CRITICAL: always clear MDC to prevent thread-local leaks
        }
    }
}
```

### Step 5.3 — Log Every Request (Access Log)

```yaml
# application.yml — enable access logging
server:
  tomcat:
    accesslog:
      enabled: true
      pattern: '%{X-Trace-Id}i %h %l %u %t "%r" %s %b %D'  # Include traceId
      directory: /var/log/app
      prefix: access
      suffix: .log
      rotate: true
      rename-on-rotate: true
      max-days: 90
```

### Step 5.4 — Prometheus Metrics (Grafana)

```yaml
# application.yml — metrics
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus    # Add prometheus for Grafana
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
```

### ✅ Phase 5 Checklist

- [ ] `logback-spring.xml` configured with JSON output for prod
- [ ] `SECURITY_AUDIT` logger writes to separate file with 365-day retention
- [ ] `MdcTracingFilter` adds `traceId` to all log entries
- [ ] Every controller method logs 3 stages: `REQUEST_RECEIVED`, `DOMAIN_OPERATION_COMPLETED`, `RESPONSE_PREPARED`
- [ ] Sensitive data (passwords, PANs, tokens) never logged
- [ ] Prometheus metrics endpoint enabled
- [ ] Access log enabled in Tomcat
- [ ] Log contains: traceId, userId, operation, status — Splunk searchable

---

## Phase 6: Testing — Unit, Integration, Security

> 📖 Full testing guide: [`TESTING_PRESENTATION.md`](./TESTING_PRESENTATION.md)

### Step 6.1 — Unit Test Every Service

```java
package com.example.user.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XxxServiceTest {

    @Mock XxxRepository repository;
    @InjectMocks XxxService service;

    // ── Happy path ───────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should create Xxx successfully")
    void shouldCreateXxx() {
        // Given
        var command = new CreateXxxCommand("userId-123", "Test Name", "Description", Status.ACTIVE);
        var savedEntity = buildEntity(command);
        when(repository.save(any())).thenReturn(savedEntity);

        // When
        var result = service.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Test Name");
        verify(repository, times(1)).save(any());
    }

    // ── Edge cases ───────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should throw exception when not found")
    void shouldThrowWhenNotFound() {
        when(repository.findByPublicIdAndOwner(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(UUID.randomUUID(), "userId-123"))
            .isInstanceOf(XxxNotFoundException.class);
    }

    // ── Security edge cases ──────────────────────────────────────────────────
    @Test
    @DisplayName("Should not return Xxx belonging to different user")
    void shouldNotReturnOtherUsersXxx() {
        UUID id = UUID.randomUUID();
        // Repository returns empty when userId doesn't match owner
        when(repository.findByPublicIdAndOwner(id, "attacker-456")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id, "attacker-456"))
            .isInstanceOf(XxxNotFoundException.class);   // 404 not 403 — don't reveal existence
    }
}
```

### Step 6.2 — Controller Security Tests

```java
package com.example.user.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(XxxController.class)
class XxxControllerSecurityTest {

    @Autowired MockMvc mockMvc;

    // ── Authentication ───────────────────────────────────────────────────────
    @Test
    @DisplayName("Unauthenticated request → 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/xxxs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated request → 200")
    void authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/xxxs").with(jwt()))
            .andExpect(status().isOk());
    }

    // ── Validation ───────────────────────────────────────────────────────────
    @Test
    @DisplayName("Empty name → 400 with field error")
    void emptyName() throws Exception {
        mockMvc.perform(post("/api/v1/xxxs").with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name": "", "description": "test", "status": "ACTIVE"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    @DisplayName("XSS in name → 400 or sanitized")
    void xssInName() throws Exception {
        mockMvc.perform(post("/api/v1/xxxs").with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name": "<script>alert(1)</script>", "description": "test", "status": "ACTIVE"}"""))
            .andExpect(status().isBadRequest());  // Pattern rejects HTML chars
    }

    // ── Security Headers ─────────────────────────────────────────────────────
    @Test
    @DisplayName("Security headers present on all responses")
    void securityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().exists("Content-Security-Policy"));
    }

    // ── Error Response ───────────────────────────────────────────────────────
    @Test
    @DisplayName("Error response never contains stack trace")
    void errorResponseNoStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/xxxs/nonexistent-uuid").with(jwt()))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist())
            .andExpect(jsonPath("$.code").exists());
    }
}
```

### Step 6.3 — Integration Test with Testcontainers

```java
package com.example.user.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class XxxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate restTemplate;

    @Test
    @DisplayName("Full create-read lifecycle with real DB")
    void createAndRead() {
        // Test with real PostgreSQL — Flyway migration runs automatically
        // ...
    }
}
```

### Step 6.4 — Sanitization Tests

```java
package com.example.user.config.security;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class InputSanitizerTest {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test void stripsScriptTags() {
        assertThat(sanitizer.sanitizePlainText("<script>alert(1)</script>Hello"))
            .isEqualTo("Hello");
    }

    @Test void preventsPathTraversal() {
        assertThat(sanitizer.sanitizeFileName("../../etc/passwd"))
            .doesNotContain("..")
            .doesNotContain("/");
    }

    @Test void preventsLogInjection() {
        assertThat(sanitizer.sanitizeForLogging("user\r\n[FAKE ENTRY] admin"))
            .doesNotContain("\r").doesNotContain("\n");
    }

    @Test void handlesNullInput() {
        assertThat(sanitizer.sanitizePlainText(null)).isNull();
        assertThat(sanitizer.sanitizeForLogging(null)).isEqualTo("null");
    }
}
```

### Step 6.5 — Test Coverage Gate

```xml
<!-- pom.xml — enforce 80% coverage minimum -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>default-prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### ✅ Phase 6 Checklist

- [ ] Unit tests for every service method (happy + failure paths)
- [ ] Security tests: 401 unauthenticated, owner isolation, XSS input
- [ ] Integration test with Testcontainers (real PostgreSQL)
- [ ] Sanitization tests: XSS, path traversal, log injection, null handling
- [ ] JaCoCo coverage gate ≥ 80% — fails build if below
- [ ] `mvn clean verify` passes all tests green
- [ ] Security header test asserts CSP, X-Frame-Options present
- [ ] Error response test: no stack trace, no exception class, has `code` field

---

## Phase 7: CI/CD — Pipeline & Security Scanning

### Step 7.1 — Main CI Workflow

```yaml
# .github/workflows/ci.yml
name: CI Pipeline
on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Maven
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}

      # ── Quality Gates ─────────────────────────────────────────────────
      - name: Build, test, coverage
        run: mvn -B clean verify

      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          files: target/site/jacoco/jacoco.xml

      # ── Security Gates ────────────────────────────────────────────────
      - name: Secret scan
        uses: trufflesecurity/trufflehog@main
        with:
          path: ./
          base: ${{ github.event.repository.default_branch }}
          head: HEAD
          extra_args: --only-verified

      - name: SAST — Semgrep
        uses: returntocorp/semgrep-action@v1
        with:
          config: >-
            p/owasp-top-ten
            p/spring-boot
            p/java

      - name: SCA — Dependency Check
        run: mvn -B dependency-check:check

      - name: EPSS enrichment
        run: bash scripts/epss-check.sh
        env:
          EPSS_THRESHOLD: '0.30'

      # ── SBOM ─────────────────────────────────────────────────────────
      - name: Generate SBOM
        run: mvn -B cyclonedx:makeAggregateBom

      - name: Upload SBOM
        uses: actions/upload-artifact@v4
        with:
          name: sbom-${{ github.sha }}
          path: target/bom.json

  # Container scan (only on main branch)
  container-scan:
    runs-on: ubuntu-latest
    needs: build-and-test
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Build image
        run: docker build -t my-service:${{ github.sha }} .
      - name: Scan with Trivy
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: my-service:${{ github.sha }}
          exit-code: '1'
          severity: 'CRITICAL,HIGH'
```

### Step 7.2 — Dockerfile (Hardened)

```dockerfile
# Dockerfile — production hardened
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# Stage 2: Runtime (minimal image)
FROM eclipse-temurin:21-jre-alpine

# ── Security hardening ────────────────────────────────────────────────────
# Non-root user — PCI-DSS container security requirement
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# No package manager in runtime image
RUN rm -rf /var/cache/apk/*

WORKDIR /app

# Copy JAR only — no source code
COPY --from=builder /build/target/*.jar app.jar
COPY --from=builder /build/target/bom.json bom.json    # SBOM alongside image

# Own the files as non-root
RUN chown -R appuser:appgroup /app

USER appuser

# Health check — required for K8s liveness/readiness
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
    "-jar", "app.jar"]
```

### ✅ Phase 7 Checklist

- [ ] GitHub Actions CI workflow created (`.github/workflows/ci.yml`)
- [ ] CI runs: `mvn clean verify` (tests + coverage)
- [ ] CI runs: TruffleHog secret scan
- [ ] CI runs: Semgrep SAST
- [ ] CI runs: OWASP Dependency-Check SCA
- [ ] CI runs: EPSS enrichment script
- [ ] CI generates SBOM and uploads as artifact
- [ ] Dockerfile uses non-root user (`appuser`)
- [ ] Dockerfile uses multi-stage build (no dev tools in runtime)
- [ ] Container scanned with Trivy (fail on CRITICAL/HIGH)
- [ ] Dependabot configured (`.github/dependabot.yml`)
- [ ] All CI gates must pass before merge to main

---

## Phase 8: Go-Live Checklist

> Run through this checklist before every deployment to staging and production.

### 🔴 Security — BLOCKS DEPLOYMENT

- [ ] **No hardcoded secrets** — `grep -r "password\|secret\|apikey" src/ --include="*.java" --include="*.yml"`
- [ ] **No stack traces in responses** — `server.error.include-stacktrace=never` confirmed
- [ ] **Default DENY** — `SecurityFilterChain` ends with `.anyRequest().denyAll()`
- [ ] **JWT validation configured** — `issuer-uri` set, not `jwk-set-uri` with skip-validation
- [ ] **MFA** — sensitive operations require step-up auth (if applicable)
- [ ] **Rate limiting** — `RateLimitFilter` active on all endpoints
- [ ] **Security headers** — CSP, X-Frame-Options, X-Content-Type-Options present
- [ ] **Input sanitized** — `InputSanitizer` used for all user-provided strings
- [ ] **Output encoded** — `OutputEncoder.forHtml()` on any user data rendered
- [ ] **Dependency-Check passes** — CVSS < 7 (or suppressions documented)
- [ ] **EPSS check passes** — no CVE > 30% exploitation probability
- [ ] **Secret scan clean** — TruffleHog found no verified secrets
- [ ] **SAST clean** — Semgrep found no HIGH/CRITICAL issues
- [ ] **SBOM generated** — `target/bom.json` exists and attached to release

### 🟡 Quality — Strongly Recommended

- [ ] **Test coverage ≥ 80%** — JaCoCo report checked
- [ ] **All tests green** — `mvn clean verify` passes
- [ ] **No SpotBugs issues** — `mvn spotbugs:check` clean
- [ ] **Integration test passing** — Testcontainers test passes against real DB
- [ ] **Container scan clean** — Trivy no CRITICAL/HIGH in image
- [ ] **Actuator limited** — only `/actuator/health` and `/actuator/info` exposed
- [ ] **Request size limits** — `max-file-size`, `max-request-size` configured
- [ ] **Flyway migration tested** — migration runs against clean DB without errors
- [ ] **Optimistic locking** — `@Version` on all concurrently-modified entities

### 🟢 Operations

- [ ] **Health endpoint working** — `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- [ ] **Structured logs** — JSON output in prod profile, includes `traceId`
- [ ] **Prometheus metrics** — `curl http://localhost:8080/actuator/prometheus` returns metrics
- [ ] **Dockerfile HEALTHCHECK** — container reports healthy
- [ ] **Non-root user** — `docker inspect` confirms `User: appuser`
- [ ] **Environment variables documented** — README lists all required env vars
- [ ] **`application-local.yml` in `.gitignore`** — no local overrides committed

### Final Command — Run Before Every Deploy

```bash
#!/usr/bin/env bash
# scripts/pre-deploy-check.sh — run this before EVERY deployment

set -euo pipefail
echo "═══ Pre-Deploy Security Check ═══"

echo "→ Running tests + coverage..."
mvn -B clean verify

echo "→ Running SCA (CVE scan)..."
mvn -B dependency-check:check

echo "→ Generating SBOM..."
mvn -B cyclonedx:makeAggregateBom

echo "→ EPSS exploitation check..."
bash scripts/epss-check.sh

echo "→ Secret scan..."
trufflehog git file://. --only-verified --fail

echo "→ SAST scan..."
semgrep scan --config=p/owasp-top-ten --config=p/spring-boot --error

echo "→ Checking for hardcoded secrets..."
if grep -rn "password.*=.*['\"][^$]" src/ --include="*.yml" --include="*.properties"; then
    echo "❌ FAIL: Potential hardcoded password found"
    exit 1
fi

echo "→ Building container..."
docker build -t "${SERVICE_NAME}:${VERSION}" .

echo "→ Scanning container..."
trivy image --exit-code 1 --severity CRITICAL,HIGH "${SERVICE_NAME}:${VERSION}"

echo ""
echo "✅ All checks passed. Safe to deploy."
```

---

## Quick Reference — Day-to-Day Commands

```bash
# Development
mvn spring-boot:run -Dspring-boot.run.profiles=local    # Run locally
mvn clean test                                           # Run tests
mvn test -Dtest=XxxServiceTest                          # Run specific test
mvn clean verify jacoco:report                           # Tests + coverage report
open target/site/jacoco/index.html                       # View coverage

# Security
mvn spotbugs:check                                       # Bug scan
mvn dependency-check:check                               # CVE scan
mvn cyclonedx:makeAggregateBom                           # Generate SBOM
trufflehog git file://. --only-verified                  # Secret scan
semgrep scan --config=p/owasp-top-ten                    # SAST
bash scripts/epss-check.sh                               # EPSS check
bash scripts/pre-deploy-check.sh                         # Full pre-deploy gate

# Database
mvn flyway:info                                          # Migration status
mvn flyway:validate                                      # Validate migrations
mvn flyway:repair                                        # Repair checksums

# Container
docker build -t my-service:dev .                         # Build
docker run --user 1000:1000 -p 8080:8080 my-service:dev # Run as non-root
trivy image my-service:dev                               # Scan
docker inspect my-service:dev | jq '.[0].Config.User'   # Verify non-root

# Verify security headers
curl -si http://localhost:8080/actuator/health | grep -E "X-Frame|X-Content|CSP|Strict"
```

---

## Document Map

| Topic | Document |
|-------|----------|
| Full security implementation | [`securing-spring-boot-api.md`](./securing-spring-boot-api.md) |
| Testing strategy, SAST, SCA, EPSS | [`TESTING_PRESENTATION.md`](./TESTING_PRESENTATION.md) |
| Logging patterns (Splunk) | [`LOGGING_IMPLEMENTATION.md`](./LOGGING_IMPLEMENTATION.md) |
| Admin/role patterns | [`ADMIN_USERS_FEATURE.md`](./ADMIN_USERS_FEATURE.md) |
| Docker hardening | `.github/skills/docker-hardening/SKILL.md` |
| Architecture conventions | `.github/copilot-instructions.md` |

---

## 🔴 Principal Engineer Review — Gap Analysis (2026-06-29)

> **Rating: 5.5/10 — Competent skeleton, dangerously incomplete for production.**  
> Items marked ⚠️ were found incorrectly checked during review. Items below are **required additions**.

### CRITICAL GAPS — Production Blocking

#### C-1: `@Transactional` missing on all write operations — DATA CORRUPTION RISK

Write methods in service classes must be transactional. A failure mid-save leaves orphaned data.

```java
// ✅ Required pattern for ALL services
@Service
@Transactional(readOnly = true)           // default for all methods
public class XxxService implements CreateXxxPort, ... {

    @Override
    @Transactional                         // override on every write
    public Xxx create(CreateXxxCommand cmd) { ... }

    @Override
    @Transactional
    public void delete(UUID id) { ... }
}
```

- [ ] All write service methods annotated `@Transactional`
- [ ] Service class default is `@Transactional(readOnly = true)`
- [ ] `@Transactional` rollback-on-failure tested in integration test

---

#### C-2: No `@Transactional` boundary test — wiring never verified in real DB

- [ ] Integration test verifies: exception mid-write rolls back entire transaction
- [ ] Integration test verifies: concurrent duplicate creates result in exactly one record (not two)

---

#### C-3: JWT secret must have NO fallback default — fail fast on missing config

```yaml
# ❌ Insecure — anyone reading the repo can forge tokens
spring.security.oauth2.resourceserver.jwt.issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8080/realms/demo}

# ✅ Required — startup FAILS without the secret
spring.security.oauth2.resourceserver.jwt.issuer-uri: ${KEYCLOAK_ISSUER_URI}
```

```java
// ✅ Add startup-fail test
@Test
void context_failsToStart_whenJwtIssuerUriMissing() {
    assertThatThrownBy(() ->
        new SpringApplicationBuilder(Application.class)
            .properties("spring.security.oauth2.resourceserver.jwt.issuer-uri=")
            .run()
    ).isInstanceOf(Exception.class);
}
```

- [ ] All required env vars have NO fallback default — application fails fast if missing
- [ ] Startup validation test for each required secret

---

#### C-4: ArchUnit tests — hexagonal boundaries NOT enforced without them

```java
// ✅ Required: HexagonalArchitectureTest.java
@AnalyzeClasses(packages = "com.example.user")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_has_no_spring_or_jpa =
        noClasses().that().resideInAPackage("..model..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
        noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule controllers_only_use_ports =
        classes().that().resideInAPackage("..api..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..api..", "..port.in..", "..model..",
                "org.springframework..", "jakarta..", "java..", "lombok.."
            );
}
```

- [ ] `HexagonalArchitectureTest.java` created with at least 3 boundary rules
- [ ] Domain has zero `org.springframework.*` or `jakarta.persistence.*` imports — ArchUnit verified
- [ ] Controllers never import service implementation classes — ArchUnit verified
- [ ] ArchUnit tests run in CI on every push

---

#### C-5: Concurrent duplicate creates — check-then-act race condition

```java
// ❌ Race condition: two threads can both pass existsBy check before either saves
if (repository.existsByKey(key)) throw new ConflictException(...);
return repository.save(entity);

// ✅ Remove the pre-check — use DB unique constraint + catch
try {
    return repository.save(entity);
} catch (DataIntegrityViolationException e) {
    throw new XxxAlreadyExistsException("Resource already exists: " + key);
}
```

- [ ] All "uniqueness" checks replaced with DB constraint + `DataIntegrityViolationException` catch
- [ ] Concurrent creation tested: two threads create same key → exactly one succeeds, one gets 409

---

#### C-6: Mapper round-trip — all fields must be verified bidirectionally

A mapper that silently drops fields corrupts data without failing tests.

```java
// ✅ Required mapper test template
@Test
void mapper_roundTrip_preservesAllFields() {
    Xxx domain = buildFullDomainObject();  // every field populated
    XxxEntity entity = mapper.toEntity(domain);
    Xxx restored = mapper.toDomain(entity);

    // Assert EVERY field, not just id/name
    assertThat(restored).usingRecursiveComparison().isEqualTo(domain);
}
```

- [ ] Mapper test uses `usingRecursiveComparison()` — no field silently dropped
- [ ] Test fails if a new field is added to the domain without updating the mapper

---

### HIGH GAPS — Fix Before First Production Deploy

#### H-1: No graceful shutdown configuration

```yaml
# ✅ Required in application.yml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

- [ ] Graceful shutdown configured
- [ ] `./mvnw spring-boot:run` verified: in-flight requests complete before shutdown

---

#### H-2: N+1 query on any `@OneToMany(fetch = LAZY)` in list endpoints

```java
// ❌ Triggers N+1 — 100 entities = 101 queries
List<XxxEntity> entities = repository.findAll(pageable).getContent();
entities.stream().map(mapper::toDomain).toList();  // each toDomain accesses lazy collection

// ✅ Use JOIN FETCH or @EntityGraph
@Query("SELECT x FROM XxxEntity x LEFT JOIN FETCH x.relatedItems")
Page<XxxEntity> findAllWithRelated(Pageable pageable);
```

- [ ] Hibernate `generate_statistics=true` in test profile
- [ ] Query count asserted in integration test for list endpoints
- [ ] No `@OneToMany(fetch = LAZY)` collection accessed outside `@Transactional` context

---

#### H-3: No Flyway migration rollback strategy

- [ ] Rollback SQL documented for every migration in `db/rollback/V{n}__rollback.sql`
- [ ] ADR documents: blue/green deployment window for zero-downtime rollback
- [ ] Staging pipeline validates rollback script before production deploy

---

#### H-4: README and RUNBOOK must exist before first deploy

- [ ] `README.md` — prerequisites, setup steps, env vars table, test/run commands
- [ ] `RUNBOOK.md` — DB connection failure, missing secret, migration failure, common 500s
- [ ] `.env.example` — all required env var keys, no values, committed to git

---

#### H-5: CORS must be explicitly configured

```java
// ✅ Explicit policy in SecurityConfig — never rely on defaults
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(System.getenv("ALLOWED_ORIGINS").split(",")));
    config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

- [ ] CORS `CorsConfigurationSource` bean explicitly configured
- [ ] Allowed origins from env var — never `*` in production

---

#### H-6: Actuator prometheus endpoint secured

```yaml
# ✅ Lock down all actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus  # remove info — can leak versions
```

```java
// In SecurityConfig
.requestMatchers("/actuator/prometheus").hasRole("METRICS_SCRAPER")
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

- [ ] `/actuator/prometheus` requires `ROLE_METRICS_SCRAPER`
- [ ] `/actuator/info` removed from exposure
- [ ] Actuator security integration test added

---

### MEDIUM GAPS — Fix Within First Sprint

#### M-1: Pre-commit hooks not set up

```yaml
# .pre-commit-config.yaml
repos:
  - repo: local
    hooks:
      - id: unit-tests
        name: Unit tests must pass
        entry: ./mvnw test -q
        language: system
        pass_filenames: false
      - id: no-secrets
        name: No hardcoded secrets
        entry: grep -rn "password.*=.*['\"][^$]" src/ --include="*.yml"
        language: system
        pass_filenames: false
```

- [ ] `.pre-commit-config.yaml` committed to repo
- [ ] `pre-commit install` documented in README as mandatory step for contributors

---

#### M-2: Max page size not enforced

```java
// ✅ Prevent ?size=100000 from reading entire table
@GetMapping
public Page<XxxResponse> list(
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
    @AuthenticationPrincipal Jwt jwt
) { ... }
```

- [ ] Max page size enforced: `@Max(200)` on all pageable endpoints
- [ ] Test: request with `size=999` returns 400

---

#### M-3: Mutation testing not configured

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.16.1</version>
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.1</version>
        </dependency>
    </dependencies>
    <configuration>
        <targetClasses>
            <param>com.example.user.core.model.*</param>
            <param>com.example.user.core.service.*</param>
        </targetClasses>
        <mutationThreshold>75</mutationThreshold>
    </configuration>
</plugin>
```

- [ ] PIT mutation testing configured for domain + service layers
- [ ] Mutation score ≥ 75% enforced in CI

---

### LOW GAPS — Polish

- [ ] `@Version` (optimistic locking) on all concurrently-modified entities verified with concurrent update test
- [ ] `BigDecimal` comparisons in tests use `isEqualByComparingTo()` not `isEqualTo()` (scale difference)
- [ ] All domain factory methods enforce invariants via `Objects.requireNonNull()` + `IllegalArgumentException` (not relying on DB constraint for domain validation)
- [ ] `application-local.yml` verified in `.gitignore` before first commit
- [ ] `./mvnw test-compile` added as first step in CI to catch compile errors before test run

---

*Last Updated: June 29, 2026*

