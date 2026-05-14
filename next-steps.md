# Next Steps

This document tracks the follow-up actions after delivering the Accounts feature.

## 1) Immediate (today)

- [ ] Open PR with title: `feat(accounts): add user-owned accounts with hexagonal ports/adapters and V7 migration`
- [ ] Paste PR notes for scope, migration, and test coverage.
- [ ] Ensure branch is up to date and CI passes.
- [ ] Verify `CHANGELOG.md` and `README.md` match final merged behavior.

### Quick verify commands

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
mvn -B clean verify
```

## 2) Short Term (this sprint)

- [x] Add request validation annotations to account DTOs (for example, non-blank `name`, max length).
- [x] Add controller tests for validation error responses (400) and payload edge cases.
- [x] Add integration test for duplicate account name on same user -> 409.
- [x] Add integration test for same account name across different users -> allowed.
- [x] Add integration test for delete-account flow and list consistency.
- [x] Add API examples for accounts in `README.md` (create/get/list/delete).

## 3) Observability and Operations

- [x] Add structured logs for account create/get/list/delete with `userId` and `accountId`.
- [x] Add metrics counters and error-rate tracking for account endpoints.
- [x] Confirm dashboards/alerts include account endpoint latency and 4xx/5xx trends.

## 4) Security and Governance

- [x] Confirm account endpoints are covered by the intended auth policy.
- [x] Verify no permissive CORS changes were introduced.
- [x] Keep actuator exposure minimal and explicit.
- [x] Run dependency scan if dependencies change in follow-up work.

### Optional security gate command (only if dependencies change)

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
mvn -B org.owasp:dependency-check-maven:check
```

## 5) Account Balance & Payment Deduction

> **Goal:** Users have accounts with a balance. When a payment is initiated, the total amount is
> deducted from the specified account. If the account has insufficient funds, the payment is rejected
> before it ever reaches the payment gateway.

### How it fits together (architecture)

```
POST /api/v1/payments
  { userId, accountId, productId, quantity, paymentMethod, currency }
        │
        ▼
  PaymentControllerAdapter
        │
        ▼
  InitiatePaymentPort.initiate(userId, accountId, productId, quantity, ...)
        │
        ▼
  PaymentService
    1. Load product → check stock
    2. Calculate totalAmount
    3. Load account via GetAccountPort → check balance >= totalAmount  ← NEW
    4. Save Payment(PENDING)
    5. gateway.process(payment)
    6. On COMPLETED → deduct balance via DebitAccountPort              ← NEW
                    → decrement stock
    7. On FAILED    → mark Payment(FAILED), balance unchanged
        │
        ▼
  DebitAccountPort (outbound) → AccountRepositoryAdapter → DB
```

### New / changed domain exception

| Exception | HTTP | When |
|-----------|------|------|
| `InsufficientFundsException` | 422 | Account balance < payment total |

### Step-by-step implementation plan

#### Step A — `hex-core`: add balance to Account + new ports + new exception
- [x] Add `balance: BigDecimal` field to `Account` record (compact constructor: must be ≥ 0)
- [x] Add `InsufficientFundsException` domain exception
- [x] Add outbound port `DebitAccountPort` — `void debit(Long accountId, BigDecimal amount)`
- [x] Add outbound port `CreditAccountPort` — `void credit(Long accountId, BigDecimal amount)` (for top-up)

#### Step B — `hex-outbound-adapter-db`: balance column + debit/credit implementation
- [x] Flyway `V8__add_balance_to_accounts.sql` — `ALTER TABLE accounts ADD COLUMN balance NUMERIC(19,4) NOT NULL DEFAULT 0`
- [x] Update `AccountEntity` to include `balance`
- [x] Implement `DebitAccountPort` in `AccountRepositoryAdapter` (atomic UPDATE with optimistic lock / check)
- [x] Implement `CreditAccountPort` in `AccountRepositoryAdapter`

#### Step C — `hex-outbound-adapter-payment-db`: track userId + accountId on payment
- [x] Flyway `V9__add_user_account_to_payments.sql` — add `user_id BIGINT` and `account_id BIGINT` columns to `payments`
- [x] Update `PaymentEntity` with new fields
- [x] Update `PaymentRepositoryAdapter` mapping

#### Step D — `hex-payment-core`: wire balance check + deduction into PaymentService
- [x] Add `userId` and `accountId` to `Payment` record
- [x] Update `InitiatePaymentPort.initiate()` signature to accept `userId` and `accountId`
- [x] Inject `DebitAccountPort` + `GetAccountPort` into `PaymentService`
- [x] Before saving PENDING: load account, check `balance >= totalAmount`, throw `InsufficientFundsException` if not
- [x] On COMPLETED: call `debitAccountPort.debit(accountId, totalAmount)`
- [x] Update `PaymentServiceTest` for new cases: insufficient funds, successful deduction, failed payment leaves balance unchanged

#### Step E — `hex-inbound-adapter-payment-web`: update request/response DTOs
- [x] Add `userId` + `accountId` to `CreatePaymentRequest` (with `@NotNull` validation)
- [x] Add `userId` + `accountId` to `PaymentResponse`
- [x] Update `PaymentControllerAdapter` to pass new fields
- [x] Add `InsufficientFundsException` → 422 handler to `PaymentApiExceptionHandler`

#### Step F — `hex-inbound-adapter-web`: add top-up endpoint
- [x] `POST /api/v1/users/{userId}/accounts/{accountId}/topup` with body `{ "amount": 100.00 }`
- [x] `TopUpAccountRequest` DTO with `@DecimalMin("0.01")` validation
- [x] New inbound port `TopUpAccountPort` in `hex-core`
- [x] Implement in `AccountService` / core — delegates to `CreditAccountPort`
- [x] Return updated `AccountResponse` (include `balance` field)

#### Step G — tests
- [ ] Unit: `PaymentService` — insufficient funds → 422, exact deduction amount, failed payment leaves balance intact
- [ ] Unit: `AccountService` — top-up happy path, top-up negative amount rejected
- [ ] Integration: full flow — top up account → initiate payment → verify balance reduced
- [ ] Integration: insufficient funds → 422, balance unchanged

#### Step H — docs & verification
- [ ] Update `README.md` with top-up and payment-with-account curl examples
- [ ] Update `security-governance.md` — top-up endpoint requires ROLE_ADMIN
- [ ] Run `mvn -B clean verify` — all tests green
- [ ] Run `docker compose up --build` — smoke test full payment flow end to end

### Quick verify command
```bash
cd /path/to/hexagonal-scim
mvn -B clean verify
```

---

## 6) Release Readiness (original §5)

- [ ] Run smoke checks against a fresh database to validate Flyway `V7` ordering.
- [ ] Run API regression set (health/products/payments + new account scenarios).
- [ ] Add release note line for account lifecycle semantics and HTTP error mappings.
- [ ] Tag and release only after CI, integration checks, and migration verification are green.

## 7) Medium Term — Module Split Criteria (original §6)

Keep Accounts in the same user module path unless one or more split triggers occur:

- [ ] Separate team ownership for accounts.
- [ ] Independent release cadence required.
- [ ] Independent scaling/storage patterns needed.
- [ ] Account workflows become mostly independent of synchronous user invariants.
- [ ] Clear bounded-context language diverges from user management.

