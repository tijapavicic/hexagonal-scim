# Bearer Token Implementation Checklist

> Step-by-step recipe for wiring `Authorization: Bearer <token>` from a
> Keycloak-authenticated React/TypeScript frontend to a Spring Boot hexagonal
> backend, including distributed tracing headers and PATCH support.

---

## 1. Verify the Keycloak singleton is accessible

**File:** `frontend/src/auth/keycloak.ts`

- [ ] Confirm `getKeycloak()` returns the singleton `Keycloak` instance.
- [ ] Confirm `keycloak.token` is populated after a successful login.
- [ ] Confirm `keycloak.updateToken(minValidity)` is callable (built-in Keycloak JS SDK method).

```typescript
// keycloak.ts — what you need exported
export function getKeycloak(): Keycloak { ... }
export async function login(): Promise<void> { ... }
```

---

## 2. Implement `authHeader()` — token refresh + Bearer header

**File:** `frontend/src/api/http.ts`

```typescript
async function authHeader(): Promise<Record<string, string>> {
  const keycloak = getKeycloak();

  try {
    // Proactively refresh if token expires within 30 seconds
    await keycloak.updateToken(30);
  } catch (error) {
    logger.error('Token refresh failed', { error: ... });
    await login();                       // redirect to Keycloak login
    throw new ApiHttpError(401, 'Session expired. Redirecting to login.');
  }

  if (!keycloak.token) {
    await login();
    throw new ApiHttpError(401, 'Missing access token. Redirecting to login.');
  }

  return { Authorization: `Bearer ${keycloak.token}` };
}
```

**Checklist:**
- [ ] Call `keycloak.updateToken(30)` before every request (handles expiry silently).
- [ ] On refresh failure → call `login()` to redirect user to Keycloak.
- [ ] Guard `!keycloak.token` after refresh (defensive, should not happen but be explicit).
- [ ] Return `{ Authorization: 'Bearer <token>' }` as a plain object (easy to spread).

---

## 3. Implement `buildBaseHeaders()` — all headers in one place

**Why:** Single Responsibility — every outbound call gets the same headers without copy-paste.

```typescript
async function buildBaseHeaders(): Promise<Record<string, string>> {
  const auth   = await authHeader();          // Authorization: Bearer …
  const corrId = getCorrelationId();          // frontend session trace ID (nullable)
  return {
    Accept:            'application/json',
    ...auth,
    ...(corrId ? { 'X-Correlation-ID': corrId } : {}),
    'X-Request-ID':    crypto.randomUUID(),   // unique per request
  };
}
```

**Checklist:**
- [ ] Import `getCorrelationId` from your logger module.
- [ ] Spread auth on top of `Accept` so it can never be overwritten.
- [ ] Include `X-Correlation-ID` **only when set** — conditional spread `(corrId ? {...} : {})`.
- [ ] Generate `X-Request-ID` with browser-native `crypto.randomUUID()` (no dependency needed).
- [ ] Keep this function **private** (not exported) — callers use `getJson`/`postJson`/etc.

---

## 4. Wire `buildBaseHeaders()` into `getJson`

```typescript
export async function getJson<T>(path: string): Promise<T> {
  const headers = await buildBaseHeaders();   // ← replaces inline authHeader() call

  const response = await fetch(path, {
    method: 'GET',
    headers,                                  // all 4 headers automatically included
  });
  // ... error handling unchanged
}
```

**Checklist:**
- [ ] Remove any direct calls to `authHeader()` inside `getJson`.
- [ ] Remove any inline `Accept: application/json` — it's now in `buildBaseHeaders()`.
- [ ] Handle `401`/`403` responses: call `login()` and throw `ApiHttpError`.

---

## 5. Wire `buildBaseHeaders()` into `mutate` (POST / PUT / PATCH / DELETE)

```typescript
async function mutate<T>(method: string, path: string, body?: unknown): Promise<T | void> {
  const baseHeaders = await buildBaseHeaders();
  const isJson      = body !== undefined;

  const response = await fetch(path, {
    method,
    headers: {
      ...(isJson ? { 'Content-Type': 'application/json' } : {}),
      ...baseHeaders,                         // Authorization + tracing headers last
    },
    body: isJson ? JSON.stringify(body) : undefined,
  });
  // ... error handling unchanged
}
```

**Checklist:**
- [ ] Spread `baseHeaders` **after** `Content-Type` so auth headers are never shadowed.
- [ ] Skip `Content-Type` for body-less methods (DELETE).
- [ ] Handle `204 No Content` — check `response.status === 204` and return `void`.
- [ ] Surface server validation messages: parse `err.message` from JSON error body.

---

## 6. Add `patchJson<T>` export

**Why:** Backend has `PATCH /api/v1/users/{id}` for partial user updates, but the frontend
had no typed function to call it with authentication.

```typescript
/**
 * Sends an authenticated PATCH request.
 * Use for partial updates — e.g. PATCH /api/v1/users/{id}.
 */
export function patchJson<T>(path: string, body: unknown): Promise<T> {
  return mutate<T>('PATCH', path, body) as Promise<T>;
}
```

**Checklist:**
- [ ] Add alongside `postJson` and `putJson` — same pattern.
- [ ] Cast return to `Promise<T>` (safe: body is always present for PATCH).
- [ ] Use in `users.ts` / any other API module that needs partial updates.

---

## 7. Export inventory — final public surface of `http.ts`

| Export | Method | Purpose |
|--------|--------|---------|
| `ApiHttpError` | class | Typed error with `.status` field |
| `getJson<T>` | GET | Authenticated read |
| `postJson<T>` | POST | Authenticated create |
| `putJson<T>` | PUT | Authenticated full update |
| `patchJson<T>` | PATCH | Authenticated partial update ← **new** |
| `deleteVoid` | DELETE | Authenticated delete (no body) |

**Checklist:**
- [ ] All five HTTP verb helpers are exported.
- [ ] `buildBaseHeaders` and `authHeader` are **not** exported (implementation detail).
- [ ] `ApiHttpError` is exported so callers can `instanceof` check it.

---

## 8. Backend — verify Spring Security accepts Bearer tokens

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/config/SecurityConfig.java`

```java
http.oauth2ResourceServer(oauth2 ->
    oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
);
```

**Checklist:**
- [ ] `spring.security.oauth2.resourceserver.jwt.issuer-uri` configured in `application.yml` (or docker env).
- [ ] `SecurityEnabledCondition` resolves to `true` when the JWT issuer URI is set.
- [ ] `JwtAuthenticationConverter` extracts roles from `realm_access.roles` claim.
- [ ] `AuthorizationInterceptor` maps `ROLE_USER` (reads) / `ROLE_ADMIN` (writes) correctly.
- [ ] `NoSecurityConfig` (permit-all) is only active for local H2/dev profile — never production.

---

## 9. Backend — propagate `X-Correlation-ID` into MDC

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/filter/MdcTracingFilter.java`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        // Echo back the frontend's correlation ID if provided
        String corrId = request.getHeader("X-Correlation-ID");
        if (corrId != null && !corrId.isBlank()) {
            MDC.put("correlationId", corrId);
        }
        // Unique per-request ID from frontend (or generate one if missing)
        String reqId = request.getHeader("X-Request-ID");
        MDC.put("requestId", reqId != null ? reqId : UUID.randomUUID().toString());

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("requestId");
        }
    }
}
```

**Checklist:**
- [ ] Filter runs at `HIGHEST_PRECEDENCE` so MDC is populated before any logging.
- [ ] `X-Correlation-ID` is optional — only set in MDC when present.
- [ ] `X-Request-ID` falls back to a server-generated UUID when missing.
- [ ] MDC is always cleared in `finally` block (no thread-local leaks in thread pools).
- [ ] Logback pattern includes `%X{correlationId}` and `%X{requestId}`.

---

## 10. Consume `patchJson` in `users.ts`

**File:** `frontend/src/api/users.ts`

```typescript
import { patchJson } from './http';

/** Partially update a user — only fields included in `updates` are changed. */
export function patchUser(id: string, updates: Partial<UserDto>): Promise<UserDto> {
  return patchJson<UserDto>(`/api/v1/users/${id}`, updates);
}
```

**Checklist:**
- [ ] Import `patchJson` from `./http`.
- [ ] Pass only the changed fields as `updates` (partial update semantics).
- [ ] Use `Partial<UserDto>` as the request body type.

---

## 11. Fix broken test mocks

### `payments.test.ts`

**Problem:** Mock returns `PaymentDto[]` directly, but `listPayments()` returns
`PagedPaymentResponse` with a `.content` array.

```typescript
// ❌ Wrong
vi.mocked(listPayments).mockResolvedValue([mockPayment]);

// ✅ Correct
vi.mocked(listPayments).mockResolvedValue({
  content:          [mockPayment],
  totalElements:    1,
  totalPages:       1,
  size:             10,
  number:           0,
});
```

**Checklist:**
- [ ] Update all `listPayments` mocks to return `PagedPaymentResponse`.
- [ ] Add `content`, `totalElements`, `totalPages`, `size`, `number` fields.

---

## 12. Write `http.test.ts`

**Checklist — minimum coverage:**
- [ ] `getJson` sends `Authorization: Bearer <token>` header.
- [ ] `getJson` sends `X-Request-ID` header (UUID format).
- [ ] `getJson` redirects to login on 401.
- [ ] `getJson` redirects to login on 403.
- [ ] `getJson` throws `ApiHttpError` with correct status on 4xx/5xx.
- [ ] `postJson` / `putJson` / `patchJson` send `Content-Type: application/json`.
- [ ] `deleteVoid` does NOT send `Content-Type`.
- [ ] `buildBaseHeaders` includes `X-Correlation-ID` when `getCorrelationId()` is non-null.
- [ ] `buildBaseHeaders` omits `X-Correlation-ID` when `getCorrelationId()` returns null.
- [ ] Token refresh failure → `login()` called → `ApiHttpError(401)` thrown.

---

## Summary — files changed

| File | Change |
|------|--------|
| `frontend/src/api/http.ts` | Added `buildBaseHeaders()`, `patchJson<T>`, wired all verbs to new helper |
| `frontend/src/api/users.ts` | Add `patchUser(id, updates)` using `patchJson` |
| `frontend/src/api/payments.test.ts` | Fix mock return type → `PagedPaymentResponse` |
| `frontend/src/api/http.test.ts` | **New** — full auth header unit tests |
| `hex-inbound-adapter-web/.../MdcTracingFilter.java` | Read `X-Correlation-ID` / `X-Request-ID` into MDC |

---

## Quick verification commands

```bash
# Frontend unit tests
cd frontend && npm test -- --run

# TypeScript type-check only
cd frontend && npx tsc --noEmit

# Full backend build + tests
mvn -B clean verify

# If dependencies changed
mvn -B org.owasp:dependency-check-maven:check
```

---

## Architecture decision record

| Decision | Reason |
|----------|--------|
| `buildBaseHeaders()` is private | Single place to add/remove headers; callers don't need to know implementation |
| `updateToken(30)` before every request | Proactive refresh prevents mid-flight 401s when token is about to expire |
| `X-Correlation-ID` conditional spread | Not always set (only after `setCorrelationId()` is called in logger); conditional prevents sending `undefined` as header value |
| `crypto.randomUUID()` for `X-Request-ID` | Zero dependencies; browser-native since Chrome 92 / Node 15 |
| `patchJson` casts to `Promise<T>` | `mutate` returns `Promise<T \| void>` but PATCH always has a response body |
| MDC cleared in `finally` | Thread pools reuse threads; stale MDC values would pollute unrelated requests |

