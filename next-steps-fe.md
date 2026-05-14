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

- [ ] Add token lifecycle events (`onAuthSuccess`, `onTokenExpired`, `onAuthLogout`, `onAuthError`) in `frontend/src/auth/keycloak.ts`.
- [ ] Improve user-facing error states in `frontend/src/components/scim-app.ts` for:
  - [ ] Keycloak unreachable
  - [ ] Invalid redirect/client config
  - [ ] Session timeout
- [ ] Add a small profile panel showing `preferred_username`, realm roles, and token expiry timestamp.
- [ ] Keep logout redirect deterministic (`window.location.origin`) and verify behavior in both Vite dev and Docker Nginx runtime.

## Phase 2 - Backend API Integration

- [ ] Add a minimal API module (e.g., `frontend/src/api/http.ts`) that:
  - [ ] Injects `Authorization: Bearer <token>`
  - [ ] Calls `updateToken(30)` before request when needed
  - [ ] Handles `401/403` with re-login strategy
- [ ] Build one thin vertical slice endpoint call (recommended: list users) and render response in a basic table/list component.
- [ ] Add explicit loading/empty/error states for the first API page.
- [ ] Keep request/response models in dedicated DTO files (`frontend/src/types/*`) to avoid leaking transport shape into rendering logic.

## Phase 3 - Contract and Security Hygiene

- [ ] Confirm Keycloak client settings:
  - [ ] Redirect URIs include dev and docker origins
  - [ ] Web Origins are explicit (avoid permissive `*`)
  - [ ] Standard Flow enabled, Direct Access Grants disabled unless intentionally needed
- [ ] Verify backend rejects unauthenticated requests and enforces role-based access where expected.
- [ ] Ensure frontend does not log tokens or sensitive payloads.
- [ ] Add a concise troubleshooting section for common local issues (invalid redirect URI, mixed http/https, expired realm/client config).

## Phase 4 - Test Coverage

- [ ] Unit tests (Vitest/Jest) for auth utility behavior (init once, refresh path, logout path).
- [ ] Component tests for `scim-app` rendering states (loading, authenticated, error).
- [ ] E2E smoke test (Playwright):
  - [ ] Open app
  - [ ] Redirect to Keycloak
  - [ ] Login
  - [ ] Return to app
  - [ ] See authenticated indicator
- [ ] Add one API E2E path after login (e.g., users list succeeds with bearer token).

## Phase 5 - Maintainability and Delivery

- [ ] Decide on one frontend style path and remove unused React scaffolding if no longer needed.
- [ ] Add `frontend/README.md` section with architecture diagram (small) and file ownership map.
- [ ] Add npm scripts for quality checks (`typecheck`, `lint`, `test`).
- [ ] Integrate frontend checks into CI before merge.

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