# Security & Governance

> How the security posture of this service is enforced, how to verify it, and what to do before releasing.

---

## Table of Contents

1. [Auth Policy — Who Can Access What](#1-auth-policy--who-can-access-what)
2. [CORS Policy](#2-cors-policy)
3. [Actuator Exposure](#3-actuator-exposure)
4. [Dependency Vulnerability Scan](#4-dependency-vulnerability-scan)
5. [How to Run the Security Tests](#5-how-to-run-the-security-tests)
6. [Security Checklist Before Releasing](#6-security-checklist-before-releasing)

---

## 1. Auth Policy — Who Can Access What

### How it works

Authentication and authorisation are handled in two layers:

| Layer | Class | Responsibility |
|-------|-------|----------------|
| **Spring Security filter chain** | `SecurityConfig` | Validates JWT signature and rejects requests with no/invalid token (`401`) |
| **Authorization interceptor** | `AuthorizationInterceptor` | Enforces role-based access control on top of valid tokens (`403`) |

### Role matrix

| HTTP Method | Required Role | Applies to |
|-------------|---------------|-----------|
| `GET` | `ROLE_USER` or `ROLE_ADMIN` | All `/api/**` endpoints |
| `POST` | `ROLE_ADMIN` | All `/api/**` endpoints |
| `PUT` / `PATCH` | `ROLE_ADMIN` | All `/api/**` endpoints |
| `DELETE` | `ROLE_ADMIN` | All `/api/**` endpoints |

> There is no custom role configuration for account endpoints specifically — the same policy that protects `/api/v1/users/**` also protects `/api/v1/users/{userId}/accounts/**` because `anyRequest().authenticated()` + the interceptor applies to all `/api/**` paths.

### Public endpoints (no token required)

```
GET  /actuator/health
GET  /actuator/info
GET  /swagger-ui/**
GET  /api-docs/**
```

### Role extraction from Keycloak JWT

Roles are read from the `realm_access.roles` claim in the Keycloak JWT:

```json
{
  "realm_access": {
    "roles": ["user", "admin"]
  }
}
```

This becomes `ROLE_USER` and `ROLE_ADMIN` in Spring Security's `GrantedAuthority` collection.

### Verified by tests

| Test class | What it proves |
|------------|----------------|
| `AccountAuthorizationInterceptorTest` | Account paths (GET/POST/DELETE) follow the correct role rules |
| `SecurityControllerTest` | User paths follow the correct role rules |

---

## 2. CORS Policy

### Current decision

**CORS is explicitly disabled at the Spring Security layer.**

```java
// SecurityConfig.java
http.cors(AbstractHttpConfigurer::disable)
```

### Why

The React frontend is served by **the same Nginx instance** that reverse-proxies `/api/*` to the Spring Boot backend:

```
Browser → https://localhost:3000 (Nginx) → http://app:8080 (Spring Boot, internal)
```

Because the browser only ever talks to one origin (`https://localhost:3000`), there is no cross-origin request to the backend. Disabling CORS at the Spring layer ensures:

- No `Access-Control-Allow-Origin: *` headers can accidentally leak.
- Direct browser access to `http://localhost:8080` (bypassing Nginx) won't yield manipulable CORS headers.

### If you ever need to add CORS

Before adding any CORS configuration, check with the team. If it's genuinely needed (e.g. a separate mobile/SPA origin), add a **named, explicit allowlist** — never use `*`:

```java
// Example — only add if you have a specific, justified need:
http.cors(cors -> cors.configurationSource(request -> {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.yourcompany.com"));
    config.setAllowedMethods(List.of("GET", "POST", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    return config;
}))
```

### How to verify CORS is not permissive

```bash
# Should get a 403 or no CORS headers — NOT "Access-Control-Allow-Origin: *"
curl -v -H "Origin: https://evil.com" \
        -H "Access-Control-Request-Method: POST" \
        -X OPTIONS \
        http://localhost:8080/api/v1/users

# Check the response headers — you must NOT see:
# Access-Control-Allow-Origin: *
# Access-Control-Allow-Origin: https://evil.com
```

---

## 3. Actuator Exposure

### What is exposed

Configured in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

| Endpoint | URL | Purpose | Auth required? |
|----------|-----|---------|----------------|
| `health` | `/actuator/health` | Liveness / readiness check | No (public) |
| `info` | `/actuator/info` | App version info | No (public) |
| `prometheus` | `/actuator/prometheus` | Metrics for Prometheus scraping | No* |
| `metrics` | `/actuator/metrics` | Metrics browser | No* |

> *Prometheus and metrics are intentionally not behind auth because Prometheus scrapes without an auth token. In a real production environment, access to `/actuator/prometheus` should be restricted to the internal observability network only (not exposed to the internet).

### What is NOT exposed (and why)

| Endpoint | Risk if exposed |
|----------|----------------|
| `/actuator/env` | Leaks all environment variables, including secrets and passwords |
| `/actuator/beans` | Exposes the entire Spring bean graph — useful for attackers mapping the app |
| `/actuator/conditions` | Exposes auto-configuration decisions |
| `/actuator/configprops` | Leaks all resolved config values (same risk as `/env`) |
| `/actuator/loggers` | Allows runtime log-level changes — can be used to flood logging |
| `/actuator/threaddump` | Exposes JVM thread state |
| `/actuator/heapdump` | Downloads a full JVM heap dump — can contain passwords in memory |
| `/actuator/shutdown` | **Terminates the JVM remotely** — never expose this |

### Verified by tests

`ActuatorExposureTest` verifies:
- ✅ Approved endpoints return `200`
- ❌ Sensitive endpoints return `404` (not just `403` — they are not registered at all)
- ✅ The `GET /actuator` discovery response lists only the approved 4 endpoints

---

## 4. Dependency Vulnerability Scan

### When to run

Run the OWASP dependency check if **any dependency version changes** (pom.xml edit, version bump, new library added).

Treat **HIGH** and **CRITICAL** findings as release blockers.

### Command

```bash
cd /path/to/hexagonal-scim
mvn -B org.owasp:dependency-check-maven:check
```

### Output

The report is written to:
```
target/dependency-check-report.html
```

Open it in a browser to review findings.

### Suppressing a false positive

If a finding is a confirmed false positive, add a suppression:

```xml
<!-- dependency-check-suppressions.xml -->
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
  <suppress>
    <notes>False positive — CVE-XXXX-YYYY affects the foo:bar artifact, not this transitive dep</notes>
    <cve>CVE-XXXX-YYYY</cve>
  </suppress>
</suppressions>
```

And reference it in `pom.xml`:
```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <configuration>
    <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
  </configuration>
</plugin>
```

> Document every suppression with a reason and a tracking ticket.

---

## 5. How to Run the Security Tests

### Run just the security-related tests

```bash
# Auth policy tests — account endpoints
mvn -pl hex-inbound-adapter-web test \
    -Dtest="AccountAuthorizationInterceptorTest,SecurityControllerTest"

# Actuator exposure tests
mvn -pl hex-application test \
    -Dtest="ActuatorExposureTest"
```

### Run the full suite (required before any PR)

```bash
cd /path/to/hexagonal-scim
mvn -B clean verify
```

### What the tests prove

#### `AccountAuthorizationInterceptorTest`

```
├── Unauthenticated
│   ├── GET  /api/v1/users/42/accounts       → passes through (Spring Security → 401)
│   ├── POST /api/v1/users/42/accounts       → passes through
│   └── DELETE /api/v1/users/42/accounts/7   → passes through
├── ROLE_USER
│   ├── GET  accounts                        → ✅ allowed
│   ├── GET  account by id                   → ✅ allowed
│   ├── POST account                         → ❌ 403 (needs ROLE_ADMIN)
│   └── DELETE account                       → ❌ 403 (needs ROLE_ADMIN)
├── ROLE_ADMIN
│   ├── GET  accounts                        → ✅ allowed
│   ├── POST account                         → ✅ allowed
│   └── DELETE account                       → ✅ allowed
└── Unknown role (ROLE_VIEWER)
    ├── GET  accounts                        → ❌ 403
    └── POST account                         → ❌ 403
```

#### `ActuatorExposureTest`

```
Allowed (must return 200):
  GET /actuator/health      ✅
  GET /actuator/info        ✅
  GET /actuator/prometheus  ✅
  GET /actuator/metrics     ✅

Forbidden (must return 404 — not registered):
  GET  /actuator/env         ✅ 404
  GET  /actuator/beans       ✅ 404
  GET  /actuator/conditions  ✅ 404
  GET  /actuator/configprops ✅ 404
  GET  /actuator/loggers     ✅ 404
  GET  /actuator/threaddump  ✅ 404
  GET  /actuator/heapdump    ✅ 404
  POST /actuator/shutdown    ✅ 404

Discovery:
  GET /actuator lists exactly: health, info, prometheus, metrics  ✅
```

---

## 6. Security Checklist Before Releasing

Copy this into your PR description when making changes that touch security:

```markdown
## Security Checklist

- [ ] `mvn -B clean verify` passes (all tests green)
- [ ] `ActuatorExposureTest` passes — no sensitive endpoints exposed
- [ ] `AccountAuthorizationInterceptorTest` passes — account auth policy unchanged
- [ ] No `Access-Control-Allow-Origin: *` introduced (check with `curl -v -H "Origin: https://evil.com" ...`)
- [ ] No new `@CrossOrigin` annotations added to controllers
- [ ] If dependencies changed: `mvn -B org.owasp:dependency-check-maven:check` run and report reviewed
- [ ] No credentials, tokens, or secrets hardcoded (check with `git diff | grep -iE 'password|secret|token|key'`)
- [ ] Actuator `include` list in `application.yml` still contains only: `health,info,prometheus,metrics`
```

---

*For the full observability stack setup, see [`how-to-use-observability.md`](how-to-use-observability.md).*  
*For port management and Docker debugging, see [`ports.md`](ports.md).*

