# Authorization Interceptor — Developer & Engineering Guide

> **Audience:** Backend engineers, security reviewers, team leads
> **Module:** `hex-inbound-adapter-web`
> **Last updated:** May 2026

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Why We Built This](#2-why-we-built-this)
3. [Architecture Overview](#3-architecture-overview)
4. [How the Request Pipeline Works](#4-how-the-request-pipeline-works)
5. [Changed Files — Full Reference](#5-changed-files--full-reference)
6. [Deep Dive: AuthorizationInterceptor](#6-deep-dive-authorizationinterceptor)
7. [Deep Dive: SecurityConfig](#7-deep-dive-securityconfig)
8. [Deep Dive: WebMvcConfig](#8-deep-dive-webmvcconfig)
9. [Deep Dive: ApiExceptionHandlerAdapter](#9-deep-dive-apiexceptionhandleradapter)
10. [Deep Dive: ErrorResponse DTO](#10-deep-dive-errorresponse-dto)
11. [Deep Dive: SecurityControllerTest](#11-deep-dive-securitycontrollertest)
12. [Role-Based Access Control Matrix](#12-role-based-access-control-matrix)
13. [Error Response Reference](#13-error-response-reference)
14. [End-to-End Flow Diagrams](#14-end-to-end-flow-diagrams)
15. [Testing Guide](#15-testing-guide)
16. [How to Extend This](#16-how-to-extend-this)
17. [FAQ](#17-faq)
18. [Glossary](#18-glossary)

---

## 1. Executive Summary

Before this change, the application relied entirely on Spring Security's filter chain
to enforce authentication. Any authenticated user — regardless of role — could call any
endpoint, including destructive operations such as `DELETE`.

**What changed:**

| Concern | Before | After |
|---|---|---|
| Authentication enforcement | ✅ Spring Security filter chain | ✅ Unchanged |
| Role-based access (read vs. write) | ❌ Not enforced | ✅ `AuthorizationInterceptor` |
| 401/403 response format | ❌ HTML / plain text | ✅ Uniform JSON envelope |
| Security test coverage | ❌ Disabled (`addFilters = false`) | ✅ Full JWT-based test suite |

---

## 2. Why We Built This

### The Problem

A Spring Security filter chain configured with `.anyRequest().authenticated()` only checks:

> *"Does this request carry a valid JWT?"*

It does **not** check:

> *"Is the user allowed to call THIS endpoint in THIS way?"*

Without role checks, a regular `read-only` user could:
- `POST /api/v1/users` → create accounts
- `DELETE /api/v1/users/{id}` → delete any user
- `PUT /api/v1/users/{id}` → overwrite any user's data

This violates the **Principle of Least Privilege** — a fundamental security requirement.

### The Solution

We introduced a two-layer defense:

```
Layer 1 — Spring Security Filter Chain:  Is the token valid?        → 401 if not
Layer 2 — AuthorizationInterceptor:      Does the role allow this?  → 403 if not
```

Both layers produce **consistent JSON error responses** using the same `ErrorResponse` record.

### Why HandlerInterceptor and Not @PreAuthorize?

| Approach | Pros | Cons |
|---|---|---|
| `@PreAuthorize` on each method | Fine-grained per-method control | Scattered across controller methods; easy to forget on new endpoints |
| `AuthorizationInterceptor` | Centralized, path + method pattern logic | Cannot access method-level context |
| Spring Security `hasRole()` in `HttpSecurity` | Framework-native | Requires two separate `requestMatchers` chains, harder to read |

**We chose `HandlerInterceptor`** because:
- All `/api/**` endpoints must follow the same read/write split — no exceptions
- The logic is centralized in one class and is immediately visible to any new developer
- It runs inside `DispatcherServlet`, after token validation, so `SecurityContextHolder` is populated

---

## 3. Architecture Overview

```
hex-inbound-adapter-web/
└── src/main/java/com/example/user/api/
    ├── config/
    │   ├── SecurityConfig.java              ← JWT filter chain + Keycloak role mapping
    │   ├── AuthorizationInterceptor.java    ← NEW: Role-based access guard (RBAC)
    │   ├── WebMvcConfig.java                ← NEW: Registers interceptor on /api/**
    │   ├── NoSecurityConfig.java            ← Permit-all fallback (local/H2 dev)
    │   ├── ApiPaginationProperties.java
    │   ├── LegacyApiDeprecationProperties.java
    │   └── OpenApiConfig.java
    ├── dto/
    │   └── ErrorResponse.java               ← CHANGED: Added path + timestamp fields
    ├── ApiExceptionHandlerAdapter.java       ← CHANGED: Added 401/403/405/404/500 handlers
    └── UserControllerAdapter.java           ← Unchanged

hex-inbound-adapter-web/
└── src/test/java/com/example/user/api/
    ├── SecurityControllerTest.java          ← NEW: Full security test suite (12 scenarios)
    └── UserControllerAdapterTest.java       ← Unchanged (addFilters=false, business logic only)
```

---

## 4. How the Request Pipeline Works

Every HTTP request passes through the following stages in order:

```
  HTTP Request (e.g. POST /api/v1/users)
        │
        ▼
┌─────────────────────────────────────┐
│  Spring Security Filter Chain        │  ← SecurityConfig.java
│                                     │
│  • Reads Authorization: Bearer ...  │
│  • Validates JWT signature via JWKS  │
│  • Maps realm_access.roles →        │
│    ROLE_USER, ROLE_ADMIN            │
│  • Populates SecurityContextHolder   │
│                                     │
│  ✅ Valid token → continue           │
│  ❌ No/invalid token → 401 JSON     │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  DispatcherServlet                   │
│                                     │
│  Matches URL to HandlerInterceptors  │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  AuthorizationInterceptor.preHandle  │  ← AuthorizationInterceptor.java
│                                     │
│  • Reads roles from SecurityContext  │
│  • Anonymous? → pass through (401   │
│    already handled above)            │
│  • Has ROLE_USER or ROLE_ADMIN?     │
│    No  → throw AccessDeniedException │ → 403 JSON
│  • Is write method (POST/PUT/...)?  │
│    Requires ROLE_ADMIN              │
│    No  → throw AccessDeniedException │ → 403 JSON
│  ✅ All checks pass → continue      │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Controller Method Executes          │  ← UserControllerAdapter.java
│  Business Logic                      │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  ApiExceptionHandlerAdapter          │  ← Catches any exception
│  (@RestControllerAdvice)             │
│                                     │
│  AccessDeniedException  → 403 JSON  │
│  AuthenticationException → 401 JSON │
│  DuplicateUserException → 409 JSON  │
│  UserNotFoundException  → 404 JSON  │
│  MethodArgNotValid...   → 400 JSON  │
│  Exception              → 500 JSON  │
└──────────────┬──────────────────────┘
               │
               ▼
         HTTP Response
```

---

## 5. Changed Files — Full Reference

| File | Status | What Changed |
|---|---|---|
| `api/config/AuthorizationInterceptor.java` | 🆕 **New** | RBAC guard implementing `HandlerInterceptor` |
| `api/config/WebMvcConfig.java` | 🆕 **New** | Registers interceptor on `/api/**` |
| `api/config/SecurityConfig.java` | ✏️ **Modified** | No logic changes; already correct — documented here for context |
| `api/ApiExceptionHandlerAdapter.java` | ✏️ **Modified** | Added 6 new exception handlers |
| `api/dto/ErrorResponse.java` | ✏️ **Modified** | Added `path` and `timestamp` fields |
| `test/.../SecurityControllerTest.java` | 🆕 **New** | 12 security test scenarios |

---

## 6. Deep Dive: AuthorizationInterceptor

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/config/AuthorizationInterceptor.java`

### What It Does

Implements `HandlerInterceptor.preHandle()` — a Spring MVC hook that runs before the
target controller method. It reads the authenticated principal from `SecurityContextHolder`
(already populated by the JWT filter) and enforces two rules:

1. **Any request to `/api/**`** requires `ROLE_USER` or `ROLE_ADMIN`
2. **Write requests** (`POST`, `PUT`, `PATCH`, `DELETE`) additionally require `ROLE_ADMIN`

### Key Design Decisions

#### ✅ Why Not Throw 401 for Anonymous Users?

```java
if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
    return true;  // Pass through — Spring Security already handles 401
}
```

Spring Security's `ExceptionTranslationFilter` handles missing/invalid tokens before
`DispatcherServlet` is reached. By the time the interceptor runs, an unauthenticated request
has already received a `401` response. Throwing again here would be a double-response bug.

#### ✅ Why Throw `AccessDeniedException`?

```java
throw new AccessDeniedException("Access denied: ROLE_ADMIN required for ...");
```

`AccessDeniedException` is part of `spring-security-core`. Throwing it from inside
`DispatcherServlet` (not a security filter) means it is caught by Spring MVC's
`HandlerExceptionResolver`, which routes it to `@RestControllerAdvice`. This gives us
**JSON error responses** instead of the default Spring Security HTML 403 page.

#### ✅ Write Method Set

```java
private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
```

`Set.of()` (Java 9+) produces an **immutable, highly optimized** set with O(1) lookup —
perfect for a high-frequency guard that runs on every request.

### Full Source Annotation

```java
@Component  // Spring-managed bean; injected into WebMvcConfig
public class AuthorizationInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // ① Not authenticated at all — Spring Security filter handles 401, pass through
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

        // ② Check minimum role: must be ROLE_USER or ROLE_ADMIN
        boolean hasRequiredRole = authorities.stream()
                .anyMatch(a -> "ROLE_USER".equals(a.getAuthority())
                            || "ROLE_ADMIN".equals(a.getAuthority()));

        if (!hasRequiredRole) {
            throw new AccessDeniedException("Access denied: ROLE_USER or ROLE_ADMIN required");
        }

        // ③ For write operations, additionally require ROLE_ADMIN
        if (WRITE_METHODS.contains(request.getMethod())) {
            boolean hasAdminRole = authorities.stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdminRole) {
                throw new AccessDeniedException(
                        "Access denied: ROLE_ADMIN required for " + request.getMethod()); // → 403
            }
        }

        return true; // ④ All checks passed — proceed to controller
    }
}
```

---

## 7. Deep Dive: SecurityConfig

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/config/SecurityConfig.java`

### Conditional Activation

```java
@ConditionalOnExpression(
    "!'${spring.security.oauth2.resourceserver.jwt.issuer-uri:}'.isEmpty()" +
    " || !'${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}'.isEmpty()"
)
```

This bean only activates when at least one JWT property is configured. Without either
property (e.g. plain `mvn spring-boot:run` with H2), `NoSecurityConfig` takes over and
permits all requests — enabling frictionless local development without Keycloak.

### JWT Filter Chain

```
.anyRequest().authenticated()
```

Every request not explicitly permitted returns `401` unless a valid JWT is present.
The `AuthorizationInterceptor` then provides the second layer (role check).

### Keycloak Role Mapping

```
JWT claim: "realm_access": { "roles": ["user", "admin", "offline_access"] }
                                              ↓
Spring authority:  ROLE_USER, ROLE_ADMIN, ROLE_OFFLINE_ACCESS
```

This mapping is performed by `keycloakJwtConverter()`. Note that Spring Security's
`hasRole('user')` automatically prefixes `ROLE_`, so `ROLE_USER` is matched by `hasRole('user')`.

### Why `jwk-set-uri` Instead of `issuer-uri`?

In Docker Compose, the browser obtains a token from `https://localhost:8443` (external).
The JWT's `iss` claim is therefore `https://localhost:8443/realms/hexagonal-scim`.

However, the Spring Boot app communicates with Keycloak via `http://keycloak:8180` (internal
Docker network). If `issuer-uri` is used, Spring fetches the OIDC discovery document and
compares the `iss` claim to the configured URI — they would never match across two different
hostnames.

**Solution:** Use `jwk-set-uri` only. This fetches the public keys for signature validation
but does **not** compare the `iss` claim. Completely valid for a local development environment.

---

## 8. Deep Dive: WebMvcConfig

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/config/WebMvcConfig.java`

### What It Does

`WebMvcConfig` wires the `AuthorizationInterceptor` into the MVC interceptor chain,
scoped to API paths only.

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/**")          // ← Guard all API endpoints
                .excludePathPatterns(
                        "/actuator/**",              // ← Health/metrics (own security rules)
                        "/swagger-ui/**",            // ← Developer documentation
                        "/api-docs/**"               // ← OpenAPI spec
                );
    }
}
```

### Why Separate from SecurityConfig?

Spring Security filters and Spring MVC interceptors are two different extension points:

| Extension Point | Phase | What it sees |
|---|---|---|
| Security Filter (`SecurityConfig`) | Before `DispatcherServlet` | Raw `HttpServletRequest` |
| MVC Interceptor (`WebMvcConfig`) | Inside `DispatcherServlet` | Resolved handler + Spring context |

Keeping them separate follows the **Single Responsibility Principle** and makes each class
easier to test and reason about in isolation.

---

## 9. Deep Dive: ApiExceptionHandlerAdapter

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/ApiExceptionHandlerAdapter.java`

### Before/After

**Before:** Only three handlers existed — domain exceptions and validation.
```
DuplicateUserException → 409
UserNotFoundException  → 404
MethodArgumentNotValidException → 400
```

**After:** Six additional handlers were added, providing full coverage:

```
AccessDeniedException              → 403 FORBIDDEN
AuthenticationException            → 401 UNAUTHORIZED (belt-and-suspenders)
HttpRequestMethodNotSupportedException → 405 METHOD_NOT_ALLOWED
NoResourceFoundException           → 404 NOT_FOUND (missing routes)
Exception (catch-all)              → 500 INTERNAL_ERROR (logs full trace)
```

### Why AccessDeniedException Is Caught Here

When `AuthorizationInterceptor` (running inside `DispatcherServlet`) throws
`AccessDeniedException`, Spring MVC's `HandlerExceptionResolver` routes it to
`@RestControllerAdvice` **before** Spring Security's own `ExceptionTranslationFilter`
gets a chance to catch it.

This is the key architectural point that gives us JSON error responses:

```
AccessDeniedException thrown in interceptor
      │
      ▼
  HandlerExceptionResolver (Spring MVC internal)
      │
      ▼
  @RestControllerAdvice.handleAccessDenied()
      │
      ▼
  JSON: { "code": "ACCESS_DENIED", "message": "...", "path": "...", "timestamp": "..." }
```

If the exception were thrown from a security *filter* (outside `DispatcherServlet`),
Spring Security's filter-level handler would catch it first, and we would get HTML.

### Catch-All Handler

```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public ErrorResponse handleGeneric(Exception ex, HttpServletRequest req) {
    log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
    return error("INTERNAL_ERROR", "An unexpected error occurred", req);
}
```

This ensures that:
1. No stack trace is leaked to the client
2. The full exception is logged for engineers (with method and path context)
3. The response format is always the same JSON envelope

---

## 10. Deep Dive: ErrorResponse DTO

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/dto/ErrorResponse.java`

### Before/After

**Before:** Two fields — `code` and `message`.

**After:** Four fields — `code`, `message`, `path`, `timestamp`.

```java
public record ErrorResponse(
    String code,       // Machine-readable: "ACCESS_DENIED", "USER_NOT_FOUND", etc.
    String message,    // Human-readable: "ROLE_ADMIN required for POST operations"
    String path,       // Request path: "/api/v1/users"
    String timestamp   // ISO-8601 UTC: "2026-05-11T10:15:30.123Z"
)
```

### Why a Java Record?

`record` (Java 16+) provides:
- Immutable fields by default — no accidental mutation
- Auto-generated constructor, getters, `equals`, `hashCode`, `toString`
- Compact, readable syntax
- Jackson serializes records natively without extra configuration

### Sample JSON Output

```json
{
  "code": "ACCESS_DENIED",
  "message": "Access denied: ROLE_ADMIN required for POST operations",
  "path": "/api/v1/users",
  "timestamp": "2026-05-11T10:15:30.123Z"
}
```

Every error the API ever returns will have exactly these four fields — no surprises for
frontend developers or API consumers.

---

## 11. Deep Dive: SecurityControllerTest

**File:** `hex-inbound-adapter-web/src/test/java/com/example/user/api/SecurityControllerTest.java`

### Philosophy

`UserControllerAdapterTest` uses `@AutoConfigureMockMvc(addFilters = false)` — security
filters are disabled. This makes business-logic tests fast and focused, but means
**security is never tested there**.

`SecurityControllerTest` is the counterpart: **security filters are fully enabled**.
It tests that authorization rules actually work in production conditions.

### Test Setup

```java
@WebMvcTest(controllers = UserControllerAdapter.class)
@Import({
        SecurityConfig.class,              // ← Real security config (not mocked)
        ApiExceptionHandlerAdapter.class,  // ← Real exception handler
        WebMvcConfig.class,                // ← Real interceptor registration
        AuthorizationInterceptor.class,    // ← Real RBAC guard
        ...
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/test"
})
```

A **mock `JwtDecoder`** (`@MockBean`) is registered to prevent Spring Security from
performing a live OIDC discovery call to a Keycloak that doesn't exist during tests.
The `jwt()` request post-processor from `spring-security-test` builds synthetic JWT
tokens with whichever authorities the test specifies.

### Scenarios Covered (12 tests)

| Test | Token | Method | Expected |
|---|---|---|---|
| `returns401WhenNoTokenProvided` | None | `GET /api/v1/users` | **401** |
| `returns401WhenNoTokenForWriteOperation` | None | `POST /api/v1/users` | **401** |
| `returns403WhenUserRoleTriesPost` | `ROLE_USER` | `POST` | **403** `ACCESS_DENIED` |
| `returns403WhenUserRoleTriesPut` | `ROLE_USER` | `PUT /1` | **403** |
| `returns403WhenUserRoleTriesPatch` | `ROLE_USER` | `PATCH /1` | **403** |
| `returns403WhenUserRoleTriesDelete` | `ROLE_USER` | `DELETE /1` | **403** |
| `returns403WhenTokenHasNoRecognisedRole` | `ROLE_UNKNOWN` | `GET` | **403** |
| `returns200WhenUserRoleReadsUsers` | `ROLE_USER` | `GET` | **200** |
| `returns200WhenUserRoleGetById` | `ROLE_USER` | `GET /1` | **200** |
| `returns201WhenAdminCreatesUser` | `ROLE_ADMIN` | `POST` | **201** |
| `returns204WhenAdminDeletesUser` | `ROLE_ADMIN` | `DELETE /1` | **204** |
| `returns200WhenAdminReadsUsers` | `ROLE_ADMIN` | `GET` | **200** |

### How Synthetic JWT Tokens Work

```java
mockMvc.perform(
    post(USERS_PATH)
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
        ...
)
```

`jwt()` from `spring-security-test` creates a synthetic `JwtAuthenticationToken` and
injects it directly into `SecurityContextHolder` — bypassing the actual JWT parsing step.
This means:
- No real token signing needed
- No Keycloak dependency in tests
- 100% deterministic — roles are exactly what the test specifies

---

## 12. Role-Based Access Control Matrix

| HTTP Method | No Token | `ROLE_UNKNOWN` | `ROLE_USER` | `ROLE_ADMIN` |
|---|---|---|---|---|
| `GET /api/v1/users` | **401** | **403** | ✅ 200 | ✅ 200 |
| `GET /api/v1/users/{id}` | **401** | **403** | ✅ 200 | ✅ 200 |
| `POST /api/v1/users` | **401** | **403** | **403** | ✅ 201 |
| `PUT /api/v1/users/{id}` | **401** | **403** | **403** | ✅ 200 |
| `PATCH /api/v1/users/{id}` | **401** | **403** | **403** | ✅ 200 |
| `DELETE /api/v1/users/{id}` | **401** | **403** | **403** | ✅ 204 |
| `GET /actuator/health` | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 200 |
| `GET /swagger-ui/**` | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 200 |

---

## 13. Error Response Reference

All error responses follow the same JSON shape:

```json
{
  "code":      "<MACHINE_READABLE_CODE>",
  "message":   "<HUMAN_READABLE_DETAIL>",
  "path":      "<REQUEST_URI>",
  "timestamp": "<ISO-8601_UTC>"
}
```

### Error Code Catalog

| HTTP Status | `code` | Trigger |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Bean Validation failure on request body |
| `401` | `UNAUTHORIZED` | Missing or invalid JWT token |
| `403` | `ACCESS_DENIED` | Valid token but insufficient role |
| `404` | `USER_NOT_FOUND` | `UserNotFoundException` from use case |
| `404` | `NOT_FOUND` | No route matched the request URL |
| `405` | `METHOD_NOT_ALLOWED` | HTTP method not supported on this path |
| `409` | `USER_ALREADY_EXISTS` | `DuplicateUserException` from use case |
| `500` | `INTERNAL_ERROR` | Unhandled exception (stack trace logged, not exposed) |

---

## 14. End-to-End Flow Diagrams

### Scenario A — No Token (401)

```
Browser                   Spring Security Filter          Spring MVC
   │                              │                            │
   │──── GET /api/v1/users ──────►│                            │
   │                              │ No Authorization header     │
   │                              │ BearerTokenAuthenticationFilter
   │                              │ throws AuthenticationException
   │                              │                            │
   │◄─── 401 Unauthorized ────────│                            │
   │     { "code": "UNAUTHORIZED" }
```

### Scenario B — Wrong Role (403)

```
Browser         Spring Security Filter     AuthorizationInterceptor    @ControllerAdvice
   │                    │                          │                         │
   │── POST /api (ROLE_USER token) ──────────────►│                         │
   │                    │ JWT valid ✅             │                         │
   │                    │ SecurityContext set       │                         │
   │                    │──────────────────────────►│                        │
   │                    │                          │ ROLE_USER found ✅      │
   │                    │                          │ POST requires ROLE_ADMIN │
   │                    │                          │ throw AccessDeniedException
   │                    │                          │──────────────────────────►│
   │                    │                          │                          │ catches it
   │◄───── 403 Forbidden ─────────────────────────────────────────────────────│
   │       { "code": "ACCESS_DENIED", "message": "ROLE_ADMIN required..." }
```

### Scenario C — Admin (201 Created)

```
Browser         Spring Security Filter     AuthorizationInterceptor    Controller
   │                    │                          │                        │
   │── POST /api (ROLE_ADMIN token) ─────────────►│                        │
   │                    │ JWT valid ✅             │                        │
   │                    │──────────────────────────►│                       │
   │                    │                          │ ROLE_ADMIN ✅          │
   │                    │                          │ POST requires ROLE_ADMIN ✅
   │                    │                          │────────────────────────►│
   │                    │                          │                        │ executes use case
   │◄─── 201 Created ───────────────────────────────────────────────────────│
   │     { "id": 1, "email": "...", "displayName": "..." }
```

---

## 15. Testing Guide

### Running All Security Tests

```bash
# Run only security tests
mvn -pl hex-inbound-adapter-web test -Dtest="SecurityControllerTest"

# Run entire test suite (all modules)
mvn -B clean verify
```

### Expected Test Output

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.user.api.SecurityControllerTest
...
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
```

### Adding a New Security Test

If you add a new endpoint and need to verify its access control:

```java
@Test
void returns403WhenUserRoleTriesNewEndpoint() throws Exception {
    mockMvc.perform(
            post("/api/v1/your-new-path")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{...}")
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
}
```

---

## 16. How to Extend This

### Add a New Role (e.g. `ROLE_AUDITOR`)

1. **Create the role in Keycloak** (Realm → Roles → Add Role: `auditor`)
2. **Assign the role to a user** in Keycloak
3. **Update `AuthorizationInterceptor`** — add `ROLE_AUDITOR` to the required-role check:

   ```java
   boolean hasRequiredRole = authorities.stream()
       .anyMatch(a -> "ROLE_USER".equals(a.getAuthority())
                   || "ROLE_ADMIN".equals(a.getAuthority())
                   || "ROLE_AUDITOR".equals(a.getAuthority()));  // ← add here
   ```

4. **Add a test** in `SecurityControllerTest` for the new role's allowed and denied operations

### Add Rate Limiting

The `AuthorizationInterceptor.preHandle()` already has access to each request's
`HttpServletRequest`. A rate-limiter (e.g. Bucket4j) can be integrated here to track
per-principal request counts.

### Add Request Logging

Add an `afterCompletion` method to `AuthorizationInterceptor`:

```java
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                            Object handler, Exception ex) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String principal = (auth != null) ? auth.getName() : "anonymous";
    log.info("{} {} {} → {}",
             request.getMethod(), request.getRequestURI(),
             principal, response.getStatus());
}
```

---

## 17. FAQ

**Q: Why do 401 responses not go through `ApiExceptionHandlerAdapter`?**

Spring Security's `BearerTokenAuthenticationFilter` runs **before** `DispatcherServlet`.
When a token is missing or invalid, the filter directly writes the response and the request
never reaches Spring MVC — so `@RestControllerAdvice` is never invoked. The JSON 401 body
is written by Spring Security's `BearerTokenAuthenticationEntryPoint`.

---

**Q: What happens if someone passes a `ROLE_ADMIN` Keycloak role but with lowercase?**

The Keycloak JWT converter uppercases all roles:
```java
.map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
```
So a Keycloak role named `admin` becomes `ROLE_ADMIN` in Spring — correctly matched.

---

**Q: Can I test this with curl?**

Yes. Obtain a token from Keycloak, then:
```bash
# 401 — no token
curl -s http://localhost:8080/api/v1/users | jq .

# 403 — wrong role (ROLE_USER trying to POST)
TOKEN=$(curl -s -X POST https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token \
  -d "grant_type=password&client_id=hexagonal-scim-app&username=testuser&password=password" \
  | jq -r '.access_token')

curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"new@test.com","displayName":"New"}' | jq .

# 201 — admin token
ADMIN_TOKEN=$(curl -s -X POST https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token \
  -d "grant_type=password&client_id=hexagonal-scim-app&username=adminuser&password=password" \
  | jq -r '.access_token')

curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"new@test.com","displayName":"New"}' | jq .
```

---

**Q: Does this work in local development without Keycloak?**

Yes. When `issuer-uri` and `jwk-set-uri` are both absent (plain `mvn spring-boot:run`),
`SecurityConfig` does **not** activate. `NoSecurityConfig` activates instead and permits
all requests. The interceptor is still registered but has nothing to enforce (all requests
pass as anonymous). Perfect for local H2 development.

---

**Q: What if both `ROLE_USER` and `ROLE_ADMIN` are assigned to the same user in Keycloak?**

The interceptor allows it — `ROLE_ADMIN` is a superset of `ROLE_USER`. Such a user passes
all checks for both read and write operations.

---

## 18. Glossary

| Term | Definition |
|---|---|
| **JWT** | JSON Web Token — a signed, self-contained token carrying user identity and claims |
| **JWKS** | JSON Web Key Set — Keycloak's public key endpoint used to verify JWT signatures |
| **RBAC** | Role-Based Access Control — authorization model mapping roles to permissions |
| **HandlerInterceptor** | Spring MVC hook that runs before/after a controller method handles a request |
| **@RestControllerAdvice** | Spring annotation marking a class as a global exception handler for REST controllers |
| **SecurityContextHolder** | Spring Security's thread-local store for the current authentication |
| **GrantedAuthority** | Spring Security interface representing a permission/role (e.g. `ROLE_ADMIN`) |
| **BearerTokenAuthenticationFilter** | Spring Security filter that extracts and validates JWT from `Authorization: Bearer` header |
| **ExceptionTranslationFilter** | Spring Security filter that converts `AccessDeniedException` into HTTP 403 responses — bypassed when the exception is thrown inside `DispatcherServlet` |
| **realm_access.roles** | Keycloak JWT claim structure containing the user's assigned realm roles |
| **PKCE** | Proof Key for Code Exchange — OAuth2 flow used by the React frontend |
| **Principle of Least Privilege** | Security principle: grant only the minimum permissions required to perform a task |

---

*Document maintained by the engineering team. Update this file whenever a new role is added,
interceptor logic changes, or new security test scenarios are introduced.*

