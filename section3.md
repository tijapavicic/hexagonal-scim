# Section 3 - Contract and Security Hygiene (Implementation Notes)

This document explains what was implemented for Phase 3, why each change was made, what was verified, and what remains open.

## Scope implemented

Phase 3 requested:

1. Confirm Keycloak client settings
   - Redirect URIs include dev and docker origins
   - Web Origins are explicit (no permissive `*`)
   - Standard Flow enabled, Direct Access Grants disabled unless intentionally needed
2. Verify backend rejects unauthenticated requests and enforces role-based access
3. Ensure frontend does not log tokens or sensitive payloads
4. Add concise troubleshooting guidance for local auth issues

---

## 1) Keycloak client settings - implemented

### What changed

File changed: `docker/keycloak/realm-export.json`

For client `hexagonal-scim-public`, explicit dev redirect/origin entries were added:

- Added redirect URIs:
  - `http://localhost:5173/*`
  - `https://localhost:5173/*`
- Added web origins:
  - `http://localhost:5173`
  - `https://localhost:5173`

### Why

- The frontend can run in both Docker (`https://localhost:3000`) and local Vite (`http://localhost:5173`).
- Missing dev redirects/origins causes login failure (`invalid_redirect_uri`) in Keycloak.
- Explicit allowlists are safer than wildcard origins.

### Standard Flow / Direct Access Grants decision

- `standardFlowEnabled` is enabled for browser OIDC code flow.
- `directAccessGrantsEnabled` remains enabled intentionally for local password-grant testing workflows (Postman/CLI token retrieval in this repository).

This matches the checklist wording: "disabled unless intentionally needed".

---

## 2) Backend auth rejection and RBAC verification - status

### What was checked

Runtime verification command used:

- Unauthenticated request to protected endpoint:
  - `GET http://localhost:8080/api/v1/users`

Observed result in local Docker runtime:

- HTTP status was `200` (expected `401/403` for secured mode)

### Why this is marked open

- The runtime result indicates auth rejection is not consistently enforced in the current local Docker run.
- Because the observed behavior does not meet the expected contract, this checklist item remains open in `next-steps-fe.md`.

### Evidence collected

- Keycloak endpoint reachable (`200` from realm discovery endpoint)
- App, Keycloak, DB containers running healthy
- Unauthenticated users endpoint returned `200` in current runtime

### Follow-up needed

A backend-focused fix is needed to guarantee secured-mode enforcement in Docker runtime. Suggested follow-up:

1. Confirm which `SecurityFilterChain` is active at runtime.
2. Add an integration test asserting `401` on `/api/v1/users` without a token when Docker/JWT properties are present.
3. Ensure role checks (`ROLE_USER`/`ROLE_ADMIN`) are exercised by runtime tests, not only unit tests.

---

## 3) Frontend sensitive logging posture - implemented

### What was checked

Frontend source inspection for token/value logging patterns was performed.

### Result

- No explicit frontend logging of bearer token values was introduced in active Web Components flow.
- Auth/API modules only use token values for Authorization header attachment and token refresh.

### Why this matters

- Avoiding token leaks in browser console is a basic OWASP-aligned control.
- Tokens should remain in memory and transport headers only.

---

## 4) Troubleshooting section - implemented

### What changed

File changed: `frontend/README.md`

Added a new `## Troubleshooting` section covering:

- Invalid redirect URI
- Mixed HTTP/HTTPS origins
- Realm/client import changes not visible (volume reset guidance)
- Session expired / login loop recovery

Also improved Keycloak settings section to explicitly list:

- Standard Flow setting
- Direct Access Grants rationale
- Complete redirect/origin values for both Docker and Vite dev origins

### Why

- Reduces local setup friction.
- Addresses the most common causes of OIDC login failures in this project.

---

## Files changed for Phase 3

- `docker/keycloak/realm-export.json`
- `frontend/README.md`
- `next-steps-fe.md`

Additionally updated during investigation:

- `hex-inbound-adapter-web/src/main/java/com/example/user/api/config/SecurityConfig.java`
  - Replaced a brittle property-check expression with property-based activation condition for JWT security config.

---

## Verification commands executed

```bash
# Keycloak realm discovery reachability
curl -k -s -o /dev/null -w "%{http_code}\n" \
  https://localhost:8443/realms/hexagonal-scim/.well-known/openid-configuration

# Runtime unauthenticated API behavior
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/users

# Backend module compile after config updates
mvn -B -pl hex-inbound-adapter-web -am -DskipTests compile
```

Observed key result:

- Keycloak discovery: `200`
- Unauthenticated users endpoint in current runtime: `200` (open issue)
- Backend compile: success

---

## Final Phase 3 status

- Keycloak client settings: **Done**
- Frontend token/sensitive logging hygiene: **Done**
- Troubleshooting docs: **Done**
- Backend unauthenticated/RBAC runtime verification: **Open (requires backend follow-up fix)**

