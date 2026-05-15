# Frontend SPA Integration Plan

> **Goal**: After successful Keycloak login, land on a homepage and deliver a full Single-Page Application
> that integrates with all backend endpoints and provides complete CRUD functionality.

---

## Backend API Surface (source of truth)

```
Users Module  – hex-inbound-adapter-web
  GET    /api/v1/users?page=0&size=10   → List<User>          ROLE_USER | ROLE_ADMIN
  GET    /api/v1/users/{id}             → User                ROLE_USER | ROLE_ADMIN
  POST   /api/v1/users                  → User (201)          ROLE_ADMIN
  PUT    /api/v1/users/{id}             → User                ROLE_ADMIN
  DELETE /api/v1/users/{id}             → 204                 ROLE_ADMIN

Payments Module  – hex-inbound-adapter-payment-web
  GET    /api/v1/payments?page=0&size=10 → List<Payment>       authenticated
  GET    /api/v1/payments/{id}           → Payment             authenticated
  POST   /api/v1/payments                → Payment (201)       authenticated
```

### User model

| Field      | Type    | Validation           |
|------------|---------|----------------------|
| id         | Long    | (server-assigned)    |
| username   | String  | `@NotBlank`          |
| email      | String  | `@NotBlank` `@Email` |
| firstName  | String  | `@NotBlank`          |
| lastName   | String  | `@NotBlank`          |
| active     | boolean | –                    |

### Payment model

| Field     | Type          | Validation          |
|-----------|---------------|---------------------|
| id        | Long          | (server-assigned)   |
| amount    | BigDecimal    | `@NotNull` `@Positive` |
| currency  | String        | `@NotBlank`         |
| status    | String        | `@NotBlank`         |
| userId    | Long          | –                   |
| createdAt | LocalDateTime | (server-assigned)   |

---

## Architecture: Client-Side Routing with Web Components

No router library needed. Use a minimal hash-based router (`#/home`, `#/users`, `#/payments`)
implemented entirely in `scim-app.ts` to keep zero dependencies.

```
src/
  main.tsx                              ← entry point (unchanged)
  auth/
    keycloak.ts                         ← existing (unchanged)
    keycloak.test.ts
  api/
    http.ts                             ← existing authFetch (unchanged)
    users.ts                            ← full CRUD (new)
    payments.ts                         ← create + list (new)
  types/
    user.dto.ts                         ← UserDto, CreateUserDto, UpdateUserDto (new)
    payment.dto.ts                      ← PaymentDto, CreatePaymentDto (new)
    user-list.dto.ts                    ← existing (keep or merge into user.dto.ts)
  components/
    scim-app.ts                         ← router shell (refactor)
    pages/
      home-page.ts                      ← dashboard with summary cards (new)
      users-page.ts                     ← list + create + edit + delete (new)
      payments-page.ts                  ← list + create + view detail (new)
    shared/
      modal-dialog.ts                   ← reusable confirm / form modal (new)
      pagination-bar.ts                 ← prev/next with page info (new)
      notification-bar.ts               ← transient success/error toast (new)
```

---

## Phase 6 – Homepage + Client-Side Router

**Milestone**: After login, land on `#/home`. Topbar shows active route links.

### 6.1 – Hash router in `scim-app.ts` ✅

- [x] Replace single-page content render with a `routeTo(hash)` dispatcher.
- [x] Listen to `window.hashchange` + handle initial load.
- [x] Routes: `#/home`, `#/users`, `#/payments`; default redirect unknown hashes to `#/home`.
- [x] Topbar nav links highlight active route.
- [x] Keep auth bootstrap before any route render (login-required guard).

### 6.2 – `home-page.ts` – Dashboard

- [ ] 3× summary cards: total users loaded, total payments loaded, logged-in username.
- [ ] Each card links to its full page (`#/users`, `#/payments`).
- [ ] Fetch `GET /api/v1/users?page=0&size=10` and `GET /api/v1/payments?page=0&size=10` for counts
  *(note: backend returns plain arrays; show count of current page + "View all" link)*.
- [ ] Loading skeleton while fetching; error card if either call fails.

**Files changed**: `scim-app.ts` (refactor), `pages/home-page.ts` (new)  
**Tests**: unit – router dispatch; component – home-page card render.

---

## Phase 7 – Users Page (Full CRUD)

**Milestone**: `#/users` renders paginated user list with create, edit, delete actions gated by role.

### 7.1 – API client `src/api/users.ts`

- [ ] `listUsers(page, size)` → `UserDto[]`
- [ ] `getUserById(id)` → `UserDto`
- [ ] `createUser(body: CreateUserDto)` → `UserDto`
- [ ] `updateUser(id, body: UpdateUserDto)` → `UserDto`
- [ ] `deleteUser(id)` → `void`
- [ ] Throw typed `ApiHttpError` with status on non-2xx.

### 7.2 – DTO types `src/types/user.dto.ts`

```typescript
export interface UserDto {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  active: boolean;
}

export type CreateUserDto = Omit<UserDto, 'id'>;
export type UpdateUserDto = Omit<UserDto, 'id'>;
```

### 7.3 – `pages/users-page.ts` component

- [ ] Paginated table: ID, Username, Email, Full Name, Status badge, Actions.
- [ ] Pagination bar: current page, prev/next buttons, page size selector (10 / 25 / 50).
- [ ] **Create button** (ADMIN only – hidden for ROLE_USER):
  - Opens modal with fields: username, email, firstName, lastName, active (checkbox).
  - Client-side validation: all fields required, email format check.
  - `POST /api/v1/users` → on success: reload list, show success toast.
  - On 409 / 400: show field-level error in the form.
- [ ] **Edit button** per row (ADMIN only):
  - Populates modal with existing values.
  - `PUT /api/v1/users/{id}` → on success: patch row in-place, show toast.
- [ ] **Delete button** per row (ADMIN only):
  - Confirm modal: "Delete user alice?".
  - `DELETE /api/v1/users/{id}` → on success: remove row, show toast.
- [ ] **View / detail mode** (both roles):
  - Click username → expand detail row.
  - `GET /api/v1/users/{id}` → render all fields.
- [ ] Empty state for zero results; error state for API failure.
- [ ] Role guard: derive `isAdmin` from `getUserProfile().roles.includes('ADMIN')`.

### 7.4 – Shared `modal-dialog.ts`

- [ ] Web Component `<modal-dialog>` with `open`, `title` attributes and a `confirm` / `cancel` event.
- [ ] Traps focus while open; ESC key closes it.
- [ ] Used for both form modals and delete confirm dialogs.

### 7.5 – Shared `notification-bar.ts`

- [ ] Web Component `<notification-bar>` with `show(message, type: 'success' | 'error')` method.
- [ ] Auto-dismiss after 4 s; fixed top-right position.

### 7.6 – Shared `pagination-bar.ts`

- [ ] Web Component `<pagination-bar>` emitting `page-change` and `size-change` events.
- [ ] Renders: "Page N" label, Prev / Next buttons, page size `<select>`.

**Files created**: `api/users.ts`, `types/user.dto.ts`, `pages/users-page.ts`,
`shared/modal-dialog.ts`, `shared/notification-bar.ts`, `shared/pagination-bar.ts`  
**Tests**: unit – each API function; component – table render, form validation, role-gated buttons.

---

## Phase 8 – Payments Page

**Milestone**: `#/payments` renders paginated payments list with create form.

### 8.1 – API client `src/api/payments.ts`

- [ ] `listPayments(page, size)` → `PaymentDto[]`
- [ ] `getPaymentById(id)` → `PaymentDto`
- [ ] `createPayment(body: CreatePaymentDto)` → `PaymentDto`
- [ ] Throw typed `ApiHttpError` on non-2xx.

### 8.2 – DTO types `src/types/payment.dto.ts`

```typescript
export interface PaymentDto {
  id: number;
  amount: string;       // BigDecimal serialised as string to avoid precision loss
  currency: string;
  status: string;
  userId: number | null;
  createdAt: string;    // ISO-8601
}

export interface CreatePaymentDto {
  amount: string;
  currency: string;
  status: string;
  userId?: number;
}
```

### 8.3 – `pages/payments-page.ts` component

- [ ] Paginated table: ID, Amount, Currency, Status, User ID, Created At, Actions.
- [ ] Shared `<pagination-bar>` component.
- [ ] **Create Payment button** (all authenticated users):
  - Form fields: amount (number > 0), currency (select: EUR / USD / GBP), status (`PENDING` default), userId (optional).
  - Client-side: amount must be > 0; currency must not be empty.
  - `POST /api/v1/payments` → on success: prepend to list, show toast.
- [ ] **View Detail** on row click:
  - `GET /api/v1/payments/{id}` → expand inline detail row with all fields.
- [ ] No delete / edit (not exposed by backend; UI makes this explicit).
- [ ] Empty state + error state.

**Files created**: `api/payments.ts`, `types/payment.dto.ts`, `pages/payments-page.ts`  
**Tests**: unit – API client functions; component – table render, create form validation.

---

## Phase 9 – Role-Aware Navigation and UX Polish

**Milestone**: UI adapts to logged-in role; consistent, accessible design.

- [ ] Topbar nav links: Home · Users · Payments (all authenticated users see them).
- [ ] "Create User" / "Edit" / "Delete" elements hidden (not just disabled) for ROLE_USER.
- [ ] Token expiry countdown in profile pill refreshed every minute.
- [ ] "Page not found" fallback for unknown hashes.
- [ ] Consistent CSS loading spinner across all pages (no library).
- [ ] Keyboard nav: tab order correct, modals trap focus, ESC closes modal.
- [ ] WCAG AA colour-contrast pass on badges and action buttons.

**Files changed**: `scim-app.ts`, all page components  
**Tests**: component – role-gate visibility for USER and ADMIN profiles.

---

## Phase 10 – End-to-End Test Expansion

**Milestone**: Playwright E2E coverage for all critical user flows.

- [ ] **Users CRUD flow** (`adminuser` / ADMIN role):
  ```
  login → #/users → create user → row appears → edit user → verify change → delete → row gone
  ```
- [ ] **Read-only Users flow** (`testuser` / USER role):
  ```
  login → #/users → create button absent → click username → detail row visible
  ```
- [ ] **Payments flow** (any authenticated user):
  ```
  login → #/payments → create payment → row appears → click row → detail visible
  ```
- [ ] **Auth expiry flow** (Vitest fake-timer unit test):
  ```
  fire onTokenExpired event → fatal-error screen shown
  ```

**Files**: `e2e/users-crud.spec.ts`, `e2e/payments.spec.ts`

---

## Phase 11 – Backend Error Contract Mapping

**Milestone**: Frontend handles all backend error shapes consistently; no stack traces in UI.

### Error response matrix

| HTTP status | Scenario          | Frontend action                           |
|-------------|-------------------|-------------------------------------------|
| 400         | Validation failure | Show field-level error inside form        |
| 401         | Unauthenticated   | Redirect to Keycloak login                |
| 403         | Forbidden         | Show "Insufficient permissions" toast     |
| 404         | Not found         | Show "User / Payment not found" message   |
| 409         | Conflict          | Show "Already exists" inline in form      |
| 5xx         | Server error      | Generic error card; `console.error` only  |

- [ ] Extend `authFetch` (`api/http.ts`) to attempt parsing `{ message }` from error response body.
- [ ] Expose parsed message to components via a typed `ApiHttpError.serverMessage` property.
- [ ] Components render `serverMessage` where available instead of generic copy.
- [ ] Never expose stack traces or internal details in rendered UI.

**Files changed**: `api/http.ts`, all page components  
**Tests**: unit – error body parsing; component – each error state renders correctly.

---

## Dependency additions

**None.** Everything built with:

- Native Web Components API (customElements, Shadow DOM)
- TypeScript
- Existing `keycloak-js` (auth)
- Existing `vitest` + `@playwright/test` (testing)
- Native `fetch` (HTTP)

> **Rationale**: zero new dependencies preserves the "backend-engineer-friendly" principle and eliminates
> framework churn risk.

---

## File-by-file delivery checklist

| File | Phase | Status |
|------|-------|--------|
| `src/components/scim-app.ts` | 6 | ✅ done |
| `src/components/pages/home-page.ts` | 6 | ✅ done |
| `src/api/users.ts` | 7 | ⬜ todo |
| `src/types/user.dto.ts` | 7 | ⬜ todo |
| `src/components/pages/users-page.ts` | 7 | ⬜ todo |
| `src/components/shared/modal-dialog.ts` | 7 | ⬜ todo |
| `src/components/shared/notification-bar.ts` | 7 | ⬜ todo |
| `src/components/shared/pagination-bar.ts` | 7 | ⬜ todo |
| `src/api/payments.ts` | 8 | ⬜ todo |
| `src/types/payment.dto.ts` | 8 | ⬜ todo |
| `src/components/pages/payments-page.ts` | 8 | ⬜ todo |
| `e2e/users-crud.spec.ts` | 10 | ⬜ todo |
| `e2e/payments.spec.ts` | 10 | ⬜ todo |

---

## Risks and assumptions

| Risk | Mitigation |
|------|-----------|
| Backend returns plain `List<T>` (no pagination envelope) | Client-side pagination state; open issue to add Spring `Page<T>` response later |
| `amount` precision loss in JSON (BigDecimal → float) | Serialize as string on frontend; backend should add `@JsonSerialize(using = ToStringSerializer.class)` |
| Payment endpoint has no explicit role restriction in `SecurityConfig` | Falls through to `anyRequest().authenticated()` — verify at runtime; add explicit rule if needed |
| No DELETE / PUT on payments | By design; UI makes read/create-only explicit |
| Token expiry mid-session UX | Handled by existing `onTokenExpired` lifecycle event; fake-timer Vitest path covers it |

---

## Verification commands

```zsh
# Run from frontend/
npm run typecheck          # TypeScript strict check
npm run lint               # ESLint
npm test                   # Vitest unit + component
npm run build              # Production build

# E2E (requires: docker compose up)
npm run test:e2e

# Full quality gate (run from repo root)
mvn -B clean verify
```

---

## Completed phases (reference)

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Auth stabilization – lifecycle events, error states, profile panel | ✅ done |
| 2 | Backend API integration – first vertical slice (users list) | ✅ done |
| 3 | Contract and security hygiene – Keycloak config, backend auth enforcement | ✅ done |
| 4 | Test coverage – Vitest unit + component, Playwright E2E smoke | ✅ done |
| 5 | Maintainability and CI – Web Components-only, quality scripts, CI gate | ✅ done |

