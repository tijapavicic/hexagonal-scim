# Upgrade v3 — Self-Service Payments & Authorization Fix

**Date:** 2026-05-15  
**Scope:** Backend authorization policy · Payment API contract · Frontend payment form  
**Modules touched:** `hex-inbound-adapter-web`, `hex-inbound-adapter-payment-web`, `frontend`

---

## 🔍 What Triggered This Upgrade

The following log entries appeared during a `ROLE_USER` session after clicking "Create Payment":

```json
{"level":"WARN","message":"Access denied — write operation requires ROLE_ADMIN. principal=7e22ec2c-9c2e-41db-ab2a-5b3a3ca56e0d ip=172.18.0.7 method=POST path=/api/v1/payments"}
{"level":"WARN","message":"403 Forbidden: POST /api/v1/payments — Insufficient permissions"}
```

**What the user expected:** any authenticated user should be able to initiate a payment (purchase a product from their own account).  
**What actually happened:** the backend rejected the request and the frontend form was also sending completely wrong fields.

Two distinct bugs were found:

| # | Bug | Location |
|---|---|---|
| 1 | Blanket "all writes = ROLE_ADMIN" rule blocked `POST /api/v1/payments` | `AuthorizationInterceptor.java` |
| 2 | Frontend form sent `amount`, `status` — backend expected `productId`, `quantity`, `paymentMethod` | `payments-page.ts`, `payment.dto.ts` |

---

## 📐 Architecture Context — Where These Files Live

```
hex-inbound-adapter-web/
  └── src/main/.../api/config/
        └── AuthorizationInterceptor.java   ← intercepts ALL /api/** requests

hex-inbound-adapter-payment-web/
  └── src/main/.../api/payment/
        ├── PaymentControllerAdapter.java   ← REST controller
        └── dto/
              ├── CreatePaymentRequest.java ← inbound DTO (what client sends)
              └── PaymentResponse.java      ← outbound DTO (what server returns)

hex-payment-core/
  └── src/main/.../
        ├── model/Payment.java              ← domain model
        ├── port/in/InitiatePaymentPort.java← use-case interface
        └── core/PaymentService.java        ← business logic

frontend/src/
  ├── types/payment.dto.ts                  ← TypeScript shapes
  ├── api/payments.ts                       ← HTTP client
  └── components/pages/payments-page.ts    ← UI component
```

This project follows **Hexagonal Architecture**:
- Inbound adapters (web controllers, interceptors) call domain ports
- The domain (core) has no knowledge of HTTP or databases
- Outbound adapters implement domain ports to talk to databases / gateways

---

## 🔐 Bug 1 — Blanket Write-Protection in `AuthorizationInterceptor`

### What it was before

```java
private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

if (WRITE_METHODS.contains(request.getMethod()) && !isAdmin) {
    log.warn("Access denied — write operation requires ROLE_ADMIN ...");
    throw new AccessDeniedException("Insufficient permissions");
}
```

**Problem:** This rule said *"ANY HTTP mutation (POST/PUT/PATCH/DELETE) is forbidden for non-admins"*.  
That was correct for user management (you shouldn't let regular users create or delete other users), but wrong for payments — paying for something is a self-service action that every authenticated user should be able to do.

### What it is now

```java
/**
 * POST paths that any authenticated user (ROLE_USER or ROLE_ADMIN) may call.
 * All other write paths still require ROLE_ADMIN.
 */
private static final Set<String> USER_ALLOWED_POST_PATHS = Set.of("/api/v1/payments");

if (WRITE_METHODS.contains(request.getMethod()) && !isAdmin) {
    String path = request.getRequestURI();
    boolean isSelfServicePost = "POST".equals(request.getMethod())
            && USER_ALLOWED_POST_PATHS.contains(path);

    if (!isSelfServicePost) {
        log.warn("Access denied — write operation requires ROLE_ADMIN ...");
        throw new AccessDeniedException("Insufficient permissions");
    }
}
```

### Why this approach?

**Option A (rejected): move the check into the controller with `@PreAuthorize`**  
This would scatter security decisions across many files. You'd have to grep every controller to understand the full policy. Easy to miss.

**Option B (rejected): remove the interceptor and only use Spring Security filters**  
The interceptor provides a single, auditable place for the RBAC policy plus structured WARN logs. Removing it would lose the security audit trail.

**Option C (chosen): path-specific allowlist inside the interceptor**  
One place. One change. Easy to read: `USER_ALLOWED_POST_PATHS` documents the exceptions explicitly. Adding a new self-service endpoint in future is a one-liner.

### Decision table after the fix

| Role | GET /api/v1/users | POST /api/v1/users | GET /api/v1/payments | POST /api/v1/payments | PUT /api/v1/payments/{id} | DELETE /api/v1/payments/{id} |
|---|---|---|---|---|---|---|
| Unauthenticated | Pass-through (401 from Spring Security) | Pass-through | Pass-through | Pass-through | Pass-through | Pass-through |
| `ROLE_USER` | ✅ | ❌ 403 | ✅ | ✅ **new** | ❌ 403 | ❌ 403 |
| `ROLE_ADMIN` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Unknown role | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 |

### How the interceptor works — step by step

```
Request arrives at /api/v1/payments (POST)
  │
  ▼
HandlerInterceptor.preHandle() is called by Spring MVC before the controller
  │
  ├── Is there any Authentication in SecurityContextHolder?
  │     └── No → pass through (Spring Security returns 401 separately)
  │
  ├── Does the authenticated user have ROLE_USER or ROLE_ADMIN?
  │     └── No → throw AccessDeniedException (403)
  │
  ├── Is this a write method (POST/PUT/PATCH/DELETE)?
  │     └── Yes → Is user NOT admin?
  │               └── Yes → Is path in USER_ALLOWED_POST_PATHS?
  │                         ├── Yes (e.g. /api/v1/payments) → allow ✅
  │                         └── No  (e.g. /api/v1/users)    → deny ❌
  │
  └── return true (allow request to reach the controller)
```

### What is `HandlerInterceptor`?

Spring MVC provides a hook called `HandlerInterceptor`. It runs *after* authentication (Spring Security filters) but *before* the controller method is invoked. It's the right layer for business-level authorization rules that are too coarse for Spring Security filters but too cross-cutting for individual controllers.

```
Browser → [Spring Security Filters] → [HandlerInterceptor] → [Controller]
                 (who are you?)               (can you do this?)     (do the work)
```

---

## 📦 Bug 2 — Wrong `CreatePaymentRequest` Fields

### The domain model — what a payment really is

Before fixing the frontend, we had to understand what the backend actually does.

```java
// hex-payment-core: Payment domain model
public record Payment(
    Long id,
    Long userId,       // optional: which user is paying
    Long accountId,    // optional: which account to debit
    Long productId,    // required: what they are buying
    int quantity,      // required: how many
    BigDecimal totalAmount,    // calculated by server = price × quantity
    String currency,           // EUR or USD
    PaymentStatus status,      // PENDING → COMPLETED or FAILED
    PaymentMethod paymentMethod // BANK_ACCOUNT, PAYPAL, IDEAL
) {}
```

Key insight: **the client never sends `amount` or a final `status`**. The server calculates `totalAmount` from the product catalogue (`price × quantity`), and `status` is set by the payment gateway. The old frontend was sending:

```typescript
// ❌ OLD (wrong) — these fields don't exist in the backend
{ amount: "19.99", currency: "GBP", status: "PENDING", userId: 7 }
```

The backend was receiving this and failing with a 400 validation error even before reaching the 403 auth check.

### The payment flow — what the backend actually does

```
1. Client sends: { productId: 1, quantity: 2, paymentMethod: "PAYPAL", currency: "EUR" }
        │
        ▼
2. PaymentControllerAdapter receives the request
        │
        ▼
3. InitiatePaymentPort.initiate() is called (domain port)
        │
        ▼
4. PaymentService (domain logic):
   a. Look up product from ProductRepository → get price
   b. Check stock quantity ≥ requested quantity
   c. If userId + accountId provided: check account balance ≥ totalAmount
   d. Save payment with status=PENDING
   e. Call PaymentGatewayPort (strategy pattern) to process payment
   f. Update status to COMPLETED or FAILED
   g. If COMPLETED + accountId provided: debit account, decrement stock
        │
        ▼
5. Return PaymentResponse: { id, productId, quantity, totalAmount, currency, status, paymentMethod, ... }
```

### What we changed in `CreatePaymentRequest`

```java
// ❌ BEFORE — userId required, which forced clients to impersonate themselves
public record CreatePaymentRequest(
    @NotNull Long userId,      // forced — but clients might send wrong ID
    @NotNull Long accountId,   // forced
    @NotNull Long productId,
    @Min(1) int quantity,
    @NotNull PaymentMethod paymentMethod,
    @Size(min = 3, max = 3) String currency
) {}

// ✅ AFTER — userId and accountId are optional
public record CreatePaymentRequest(
    Long userId,               // nullable: both must be null or both non-null
    Long accountId,            // nullable: validated together by PaymentService
    @NotNull Long productId,
    @Min(1) int quantity,
    @NotNull PaymentMethod paymentMethod,
    @Size(min = 3, max = 3) String currency
) {}
```

**Why make userId optional?**

The `userId` in this system is the domain **Long** ID (e.g. `42`), not the Keycloak UUID (e.g. `7e22ec2c-...`). There is no Keycloak-to-domain-user mapping in the database — we cannot auto-resolve the logged-in user's Long ID from the JWT token.

Making `userId` optional means:
- **Without userId + accountId**: proceed with no account debit (anonymous/guest purchase)
- **With both userId + accountId**: validate balance, debit account on success

The `PaymentService` already enforced the both-or-neither rule before this change:

```java
if (userId != null || accountId != null) {
    if (userId == null || accountId == null) {
        throw new IllegalArgumentException(
            "Both userId and accountId must be provided together");
    }
    BigDecimal balance = getAccountPort.getById(userId, accountId).balance();
    if (balance.compareTo(totalAmount) < 0) {
        throw new InsufficientFundsException("Insufficient funds for account id: " + accountId);
    }
}
```

---

## 🖥️ Bug 3 — Frontend Form With Wrong Fields

### Frontend architecture recap

The payment page (`payments-page.ts`) is a **Web Component** — a native browser custom element with no framework:

```
<payments-page>         ← this.innerHTML = light DOM
  ├── <notification-bar> ← shadow DOM toast component  
  └── <div id="main">
        ├── page-header + "New Payment" button
        ├── <table> with payment rows
        ├── <pagination-bar> ← shadow DOM pagination
        └── <modal-dialog>  ← shadow DOM form dialog
```

### Old form fields (completely wrong)

```html
<!-- OLD: these fields have nothing to do with how payments work -->
<input name="amount"   placeholder="e.g. 19.99">  <!-- doesn't exist in backend -->
<select name="currency">EUR / USD / GBP</select>   <!-- GBP not supported by backend -->
<select name="status">PENDING / COMPLETED / FAILED</select>  <!-- set by server, not client -->
<input name="userId">  <!-- optional numeric ID -->
```

### New form fields (matching the real contract)

```html
<!-- NEW: these are the actual required fields -->
<input  name="productId"     type="number" required>   <!-- which product to buy -->
<input  name="quantity"      type="number" min="1">     <!-- how many (default: 1) -->
<select name="paymentMethod">BANK_ACCOUNT / PAYPAL / IDEAL</select>  <!-- matches PaymentMethod enum -->
<select name="currency">EUR / USD</select>              <!-- matches resolveChargeCurrency() -->

<!-- Optional section: fill BOTH or leave BOTH empty -->
<input  name="userId"        type="number">  <!-- your system user ID -->
<input  name="accountId"     type="number">  <!-- your account ID -->
```

### Frontend DTO — before and after

```typescript
// ❌ BEFORE
export interface CreatePaymentDto {
  amount: string;    // doesn't exist in backend
  currency: string;
  status: string;    // set by server, not client
  userId?: number;
}

// ✅ AFTER
export interface CreatePaymentDto {
  productId:     number;   // required: which product
  quantity:      number;   // required: how many
  paymentMethod: string;   // required: BANK_ACCOUNT | PAYPAL | IDEAL
  currency:      string;   // required: EUR | USD
  userId?:       number;   // optional: must pair with accountId
  accountId?:    number;   // optional: must pair with userId
}
```

### Frontend response DTO — before and after

```typescript
// ❌ BEFORE — wrong fields
export interface PaymentDto {
  id:        number;
  amount:    string;     // doesn't exist in backend response
  currency:  string;
  status:    string;
  userId:    number | null;
  createdAt: string;     // doesn't exist in backend response
}

// ✅ AFTER — matches PaymentResponse Java record exactly
export interface PaymentDto {
  id:            number;
  userId:        number | null;
  accountId:     number | null;
  productId:     number | null;
  quantity:      number;
  totalAmount:   string;       // BigDecimal as string (precision-safe)
  currency:      string;
  status:        string;
  paymentMethod: string;
}
```

### Frontend validation change

Old validation checked that `amount` was a positive decimal. New validation:

```typescript
private validateForm(values: PaymentFormValues): string | null {
  // productId: must be a positive integer
  if (!values.productId || isNaN(Number(values.productId)) || Number(values.productId) <= 0)
    return 'Product ID must be a positive number.';

  // quantity: must be whole number >= 1
  const qty = Number(values.quantity);
  if (!Number.isInteger(qty) || qty < 1)
    return 'Quantity must be a whole number ≥ 1.';

  if (!values.paymentMethod) return 'Payment method is required.';
  if (!values.currency)      return 'Currency is required.';

  // userId + accountId: must be paired (both or neither)
  const hasUserId    = values.userId    !== '';
  const hasAccountId = values.accountId !== '';
  if (hasUserId !== hasAccountId)
    return 'User ID and Account ID must both be provided together, or both left empty.';

  return null;
}
```

This mirrors the backend constraint in `PaymentService.initiate()` — failing fast on the frontend avoids a round-trip.

### Table display — before and after

```
BEFORE                          AFTER
─────────────────────────       ──────────────────────────────────
ID | Amount | Currency |        ID | Product | Qty | Total | Currency | Status | Actions
   | Status | User ID  |
   | Created At        |
```

The new columns (`Product`, `Qty`, `Total`) are what the domain model actually contains. `Created At` was removed because the backend `PaymentResponse` doesn't have that field.

### Role guard on "New Payment" button

The button is now **shown to all authenticated users**:

```typescript
// ❌ BEFORE — only admins could see it (was wrong)
private mainTemplate(): string {
  return `
    <div class="page-header">
      <h2>Payments</h2>
      ${this.isAdmin ? `<button data-action="create">+ Create Payment</button>` : ''}
    </div> ...`;
}

// ✅ AFTER — every authenticated user can initiate a payment
private mainTemplate(): string {
  return `
    <div class="page-header">
      <h2>Payments</h2>
      <button class="btn btn-primary" data-action="create">+ New Payment</button>
    </div> ...`;
}
```

The `isAdmin` field is still present in the component (retained for potential future use), but no longer gates the Create button.

---

## 🧪 Tests Changed

### `SecurityControllerTest.java` — new test added

**File:** `hex-inbound-adapter-web/src/test/java/com/example/user/api/SecurityControllerTest.java`

```java
@Test
void postPayments_roleUser_allowed() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("u", "n/a", "ROLE_USER"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");

    assertDoesNotThrow(
        () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()),
        "POST /api/v1/payments must be allowed for ROLE_USER — self-service purchase"
    );
}
```

This test directly instantiates `AuthorizationInterceptor` (no Spring context needed — fast and deterministic) and verifies the new allowlist rule works.

Existing tests that still pass:
- `writeRequestRejectsRoleUserWithoutAdmin()` — `POST /api/v1/users` still requires ROLE_ADMIN ✅
- `readRequestAllowsRoleUser()` — GET is still allowed ✅
- `requestRejectsUnknownRole()` — unknown role still blocked ✅

### `payments-page.test.ts` — complete overhaul

**File:** `frontend/src/components/pages/payments-page.test.ts`

Key changes:

**1. Test fixtures updated**

```typescript
// OLD (wrong shape)
const paymentA = { id: 1, amount: '100.00', currency: 'EUR', status: 'PENDING', userId: 42, createdAt: '...' };

// NEW (matches real PaymentResponse)
const paymentA = {
  id: 1, productId: 1, quantity: 2, totalAmount: '199.98',
  currency: 'EUR', status: 'PENDING', paymentMethod: 'BANK_ACCOUNT',
  userId: 42, accountId: 7,
};
```

**2. Create form test updated**

```typescript
// OLD — filling fields that don't exist (#p-amount, #p-status)
amountInput!.value  = '19.99';
statusSelect!.value = 'PENDING';

// NEW — filling real form fields
productIdInput!.value = '1';
quantityInput!.value  = '2';
methodSelect!.value   = 'PAYPAL';
currencySelect!.value = 'USD';
userIdInput!.value    = '42';
accountIdInput!.value = '7';

expect(createPaymentMock).toHaveBeenCalledWith({
  productId: 1, quantity: 2, paymentMethod: 'PAYPAL',
  currency: 'USD', userId: 42, accountId: 7,
});
```

**3. Role guard test flipped**

```typescript
// OLD — ROLE_USER should NOT see the button (wrong business rule)
it('hides create button for ROLE_USER', async () => {
  expect(el.querySelector('[data-action="create"]')).toBeNull();
});

// NEW — ROLE_USER SHOULD see the button (correct business rule)
it('shows create button for ROLE_USER (self-service purchase allowed)', async () => {
  expect(el.querySelector('[data-action="create"]')).not.toBeNull();
});
```

---

## 📁 Files Changed — Summary

| File | Module | Type | Change |
|---|---|---|---|
| `api/config/AuthorizationInterceptor.java` | `hex-inbound-adapter-web` | Backend | Added `USER_ALLOWED_POST_PATHS`; `POST /api/v1/payments` now allowed for `ROLE_USER` |
| `api/payment/dto/CreatePaymentRequest.java` | `hex-inbound-adapter-payment-web` | Backend | Removed `@NotNull` from `userId` and `accountId`; both are now optional |
| `api/SecurityControllerTest.java` | `hex-inbound-adapter-web` | Test | Added `postPayments_roleUser_allowed()` test |
| `types/payment.dto.ts` | `frontend` | TypeScript | Rewrote `PaymentDto` and `CreatePaymentDto` to match the real backend contract |
| `components/pages/payments-page.ts` | `frontend` | TypeScript | New form fields, new table columns, Create button shown for all users, updated validation |
| `components/pages/payments-page.test.ts` | `frontend` | Test | Updated fixtures, form field assertions, role guard assertion |

---

## ✅ Verification

```bash
# Backend — build and test affected modules
mvn -B clean verify -pl hex-inbound-adapter-web,hex-inbound-adapter-payment-web --also-make

# Frontend — type check + unit tests + build
cd frontend && npm run typecheck && npm run test && npm run build
```

**Results:**
- Backend: `BUILD SUCCESS` — all tests including new `postPayments_roleUser_allowed` pass
- Frontend: `58/58 tests passed` — no TypeScript errors

---

## ⚠️ Risks & Known Limitations

| Risk | Severity | Notes |
|---|---|---|
| JWT email → system user mapping depends on existing user email records | Medium | In secured runtime, `userId` is resolved from JWT `email` using `ResolvePayerPort`. If no matching user email exists, account-debit payments are rejected with `400` and a clear message. |
| Legacy fallback accepts `userId` only when no JWT principal is present | Medium | For local/no-security test runtime compatibility, controller falls back to request `userId` when JWT is unavailable. In secured runtime, JWT resolution takes precedence and client `userId` is ignored. |
| `POST /api/v1/payments` allowlist is path-exact, not pattern-based | Low | If sub-paths like `/api/v1/payments/initiate` are added later, they won't inherit this allowlist automatically — must be added explicitly. This is intentional (secure by default). |
| Only `BaseCatHouse` product can be purchased (backend enforces this) | Low | Frontend now loads products via `GET /api/v1/products` and shows a dropdown, but the backend still intentionally allows only `BaseCatHouse` in the current production phase. |

---

## 🔮 Suggested Next Steps

1. **Persist Keycloak subject (`sub`) on users** — map JWT subject directly to domain user records and remove the no-security `userId` fallback.

2. **Enforce account ownership in payment create flow** — when `accountId` is supplied, verify it belongs to the resolved authenticated user and return `403` for cross-user attempts.

3. **Show only caller-owned payment history** — add a scoped endpoint/filter (for example `GET /api/v1/payments/me`) so regular users do not browse global payment records.

4. **Add `PUT /api/v1/payments/{id}` self-service carve-out** (if needed) — following the same allowlist approach, add explicit path entries for cancellation/update operations.

