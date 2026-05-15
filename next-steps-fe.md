# Next Steps - Frontend

This plan starts from the current milestone: Web Components bootstrap + redirect-based Keycloak login.

## Goal

Progress from login-only UI to a production-ready frontend that can securely call backend APIs and remain easy for backend engineers to maintain.

## Assumptions

- Frontend is served locally with Vite on `http://localhost:3000` (or `http://localhost:5173` in some setups).
- Keycloak is reachable at `https://localhost:8443` with realm `hexagonal-scim`.
- Public client `hexagonal-scim-public` is configured for browser login with PKCE.
- Backend API routes are exposed under `/api/*`.

## Phase 1 - Auth Stabilization (Now)

- [x] Add token lifecycle events (`onAuthSuccess`, `onTokenExpired`, `onAuthLogout`, `onAuthError`) in `frontend/src/auth/keycloak.ts`.
- [x] Improve user-facing error states in `frontend/src/components/scim-app.ts` for:
  - [x] Keycloak unreachable
  - [x] Invalid redirect/client config
  - [x] Session timeout
- [x] Add a small profile panel showing `preferred_username`, realm roles, and token expiry timestamp.
- [ ] Keep logout redirect deterministic (`window.location.origin`) and verify behavior in both Vite dev and Docker Nginx runtime.
  - Vite dev runtime check passed (`http://127.0.0.1:3000` returned `HTTP/1.1 200 OK`).
  - Docker runtime verification is pending because Docker daemon was not available locally during this check.

## Phase 2 - Backend API Integration

- [x] Add a minimal API module (e.g., `frontend/src/api/http.ts`) that:
  - [x] Injects `Authorization: Bearer <token>`
  - [x] Calls `updateToken(30)` before request when needed
  - [x] Handles `401/403` with re-login strategy
- [x] Build one thin vertical slice endpoint call (recommended: list users) and render response in a basic table/list component.
- [x] Add explicit loading/empty/error states for the first API page.
- [x] Keep request/response models in dedicated DTO files (`frontend/src/types/*`) to avoid leaking transport shape into rendering logic.

## Phase 3 - Contract and Security Hygiene

- [x] Confirm Keycloak client settings:
  - [x] Redirect URIs include dev and docker origins
  - [x] Web Origins are explicit (avoid permissive `*`)
  - [x] Standard Flow enabled, Direct Access Grants disabled unless intentionally needed
    - `hexagonal-scim-public` keeps Direct Access Grants enabled intentionally for local Postman/password-grant workflows.
- [x] Verify backend rejects unauthenticated requests and enforces role-based access where expected.
  - Verified by `ApiSecurityContractIntegrationTest` in `hex-application`:
    - unauthenticated `GET /api/v1/users` -> `401`
    - authenticated `ROLE_USER` `POST /api/v1/users` -> `403`
- [x] Ensure frontend does not log tokens or sensitive payloads.
- [x] Add a concise troubleshooting section for common local issues (invalid redirect URI, mixed http/https, expired realm/client config).

## Phase 4 - Test Coverage

- [x] Unit tests (Vitest/Jest) for auth utility behavior (init once, refresh path, logout path).
- [x] Component tests for `scim-app` rendering states (loading, authenticated, error).
- [x] E2E smoke test (Playwright):
  - [x] Open app
  - [x] Redirect to Keycloak
  - [x] Login
  - [x] Return to app
  - [x] See authenticated indicator
- [x] Add one API E2E path after login (e.g., users list succeeds with bearer token).
  - Verified by `frontend/e2e/auth-smoke.spec.ts`:
    - waits for `GET /api/v1/users` and asserts `200` after login
  - Verification commands executed:
    - `npm test` -> 2 files, 6 tests passed
    - `npm run test:e2e` -> 1 Playwright smoke test passed

## Phase 5 - Maintainability and Delivery

- [x] Decide on one frontend style path and remove unused React scaffolding if no longer needed.
  - Chosen path: Web Components + TypeScript only.
  - Removed unused React-era files under `frontend/src` (`App.tsx`, `pages/*`, `context/*`, legacy `components/*`, legacy API helpers).
- [x] Add `frontend/README.md` section with architecture diagram (small) and file ownership map.
- [x] Add npm scripts for quality checks (`typecheck`, `lint`, `test`).
- [x] Integrate frontend checks into CI before merge.
  - Added `frontend-quality` job in `.github/workflows/ci.yml` running `typecheck`, `lint`, `test`, and `build`.

## Definition of Done for Frontend Step 2

- [ ] User can log in via Keycloak and stay authenticated during normal usage.
- [ ] Frontend successfully calls at least one protected `/api/*` endpoint with bearer token.
- [ ] Failure states are explicit and actionable.
- [ ] Tests cover auth bootstrap and one protected API flow.
- [ ] Documentation is updated and runnable by a backend-focused engineer in under 10 minutes.

## Suggested Execution Order

1. Auth stabilization
2. One protected API vertical slice
3. Tests for auth + one E2E smoke flow
4. Documentation and cleanup

## Risks and Trade-offs

- Keeping both React-era code and Web Components can confuse contributors; cleanup should happen soon after API slice lands.
- Browser-only token handling is simpler for this phase but requires strict logging discipline and explicit security headers.
- Local HTTPS/HTTP origin mismatches are the most likely source of auth friction; treat environment parity as a first-class concern.