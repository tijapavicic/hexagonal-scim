# Development Instructions

This document captures the exact implementation approach, step by step, for the frontend SPA work completed so far and the immediate next target (`8.1`).

## 1) Scope and Goal

- Build a maintainable Web Components frontend that integrates with backend endpoints.
- Keep auth redirect-first with Keycloak.
- Use typed API contracts (`DTO` files) and clear module boundaries.
- Deliver in phases with test/build verification after each milestone.

## 2) Current State (Completed)

Completed phases:
- `6.1` Hash router shell in `frontend/src/components/scim-app.ts`
- `6.2` Homepage dashboard in `frontend/src/components/pages/home-page.ts`
- `7.1` Users API client in `frontend/src/api/users.ts`
- `7.2` Users DTO contracts in `frontend/src/types/user.dto.ts`
- `7.3` Users CRUD page in `frontend/src/components/pages/users-page.ts`
- Shared UI components:
  - `frontend/src/components/shared/modal-dialog.ts`
  - `frontend/src/components/shared/pagination-bar.ts`
  - `frontend/src/components/shared/notification-bar.ts`
- CSS extraction refactor:
  - `frontend/src/components/pages/users-page.styles.ts`

## 3) Step-by-Step Implementation Details

### Step A — Router shell (`6.1`)

1. Keep auth bootstrap (`initAuth`) as gate before page rendering.
2. Add hash route dispatcher (`#/home`, `#/users`, `#/payments`) in `scim-app.ts`.
3. Add `window.hashchange` listener to re-render route content.
4. Redirect unknown hash to `#/home`.
5. Add topbar navigation and active-link highlighting.
6. Preserve logout and auth error rendering.

Files:
- `frontend/src/components/scim-app.ts`
- `frontend/src/components/scim-app.test.ts`

### Step B — Homepage dashboard (`6.2`)

1. Add `home-page` component with username card and quick links.
2. Fetch counts from:
   - `GET /api/v1/users?page=0&size=10`
   - `GET /api/v1/payments?page=0&size=10`
3. Display loading skeleton while requests are pending.
4. Show per-card error state if either request fails.
5. Keep links to full sections (`#/users`, `#/payments`).

Files:
- `frontend/src/components/pages/home-page.ts`
- `frontend/src/components/pages/home-page.test.ts`

### Step C — Users API client (`7.1`)

1. Extend `http.ts` with mutation helpers:
   - `postJson<T>()`
   - `putJson<T>()`
   - `deleteVoid()`
2. Implement centralized error behavior (including server JSON `message` when available).
3. Build typed users API methods:
   - `listUsers(page, size)`
   - `getUserById(id)`
   - `createUser(body)`
   - `updateUser(id, body)`
   - `deleteUser(id)`

Files:
- `frontend/src/api/http.ts`
- `frontend/src/api/users.ts`
- `frontend/src/api/users.test.ts`

### Step D — DTO contracts (`7.2`)

1. Define transport shape in `user.dto.ts`.
2. Use explicit interfaces for request payloads:
   - `CreateUserDto`
   - `UpdateUserDto`
3. Keep API and component imports typed to these DTOs.

File:
- `frontend/src/types/user.dto.ts`

### Step E — Users page full CRUD (`7.3`)

1. Render paginated user table with columns:
   - ID, Username, Email, Full Name, Status, Actions
2. Add role guard from Keycloak profile roles:
   - Hide create/edit/delete for non-admin users
   - Accept `ADMIN` and `ROLE_ADMIN`
3. Add create/edit modal flow with validation.
4. Add delete confirmation modal.
5. Add username click-to-expand detail row using `getUserById`.
6. Add list states:
   - loading
   - empty
   - error + retry
7. Add pagination component integration:
   - prev/next
   - size selector (10/25/50)
8. Add notifications on successful operations/errors.

Files:
- `frontend/src/components/pages/users-page.ts`
- `frontend/src/components/pages/users-page.test.ts`
- `frontend/src/components/shared/modal-dialog.ts`
- `frontend/src/components/shared/pagination-bar.ts`
- `frontend/src/components/shared/notification-bar.ts`

### Step F — CSS extraction refactor

1. Move long inline styles out of `users-page.ts`.
2. Create `users-page.styles.ts` exporting `USERS_PAGE_STYLES`.
3. Import styles constant into component render path.

Files:
- `frontend/src/components/pages/users-page.styles.ts`
- `frontend/src/components/pages/users-page.ts`

## 4) Verification Standard After Each Change

Run from `frontend/`:

```zsh
npm run typecheck
npm test
npm run build
```

Expected quality gate for completed phases:
- TypeScript compiles with no type errors.
- Unit/component tests pass.
- Production build succeeds.

## 5) Coding Rules Used

- Web Components + TypeScript only.
- Keep DTOs in `src/types` and API in `src/api`.
- Do not leak auth tokens in UI or logs.
- Keep styles maintainable (extract large CSS blocks when needed).
- Prefer typed errors (`ApiHttpError`) and deterministic UI states.

## 6) What We Will Do Next (`8.1`)

Target: implement payments API client.

### `8.1` implementation steps

1. Create `frontend/src/api/payments.ts`.
2. Implement methods:
   - `listPayments(page, size): Promise<PaymentDto[]>`
   - `getPaymentById(id): Promise<PaymentDto>`
   - `createPayment(body: CreatePaymentDto): Promise<PaymentDto>`
3. Reuse `getJson/postJson` from `frontend/src/api/http.ts`.
4. Add tests in `frontend/src/api/payments.test.ts` for:
   - URL/path correctness
   - payload correctness for create
   - non-2xx error propagation
5. Keep behavior aligned with users API client patterns.

## 7) Definition of Done for `8.1`

- `payments.ts` created with 3 typed methods.
- `payments.test.ts` green with coverage on success/error paths.
- `npm run typecheck`, `npm test`, `npm run build` all pass.
- `next-steps-integration.md` updated: `8.1` checked complete.

## 8) Quick Resume Checklist

When resuming work, execute in this order:

1. Read `next-steps-integration.md` sections `8.1` and `8.2`.
2. Implement `frontend/src/api/payments.ts`.
3. Add/adjust tests (`payments.test.ts`).
4. Run quality gates.
5. Update tracking docs.

