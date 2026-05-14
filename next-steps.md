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

- [ ] Add structured logs for account create/get/list/delete with `userId` and `accountId`.
- [ ] Add metrics counters and error-rate tracking for account endpoints.
- [ ] Confirm dashboards/alerts include account endpoint latency and 4xx/5xx trends.

## 4) Security and Governance

- [ ] Confirm account endpoints are covered by the intended auth policy.
- [ ] Verify no permissive CORS changes were introduced.
- [ ] Keep actuator exposure minimal and explicit.
- [ ] Run dependency scan if dependencies change in follow-up work.

### Optional security gate command (only if dependencies change)

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
mvn -B org.owasp:dependency-check-maven:check
```

## 5) Release Readiness

- [ ] Run smoke checks against a fresh database to validate Flyway `V7` ordering.
- [ ] Run API regression set (health/products/payments + new account scenarios).
- [ ] Add release note line for account lifecycle semantics and HTTP error mappings.
- [ ] Tag and release only after CI, integration checks, and migration verification are green.

## 6) Medium Term (future split criteria)

Keep Accounts in the same user module path unless one or more split triggers occur:

- [ ] Separate team ownership for accounts.
- [ ] Independent release cadence required.
- [ ] Independent scaling/storage patterns needed.
- [ ] Account workflows become mostly independent of synchronous user invariants.
- [ ] Clear bounded-context language diverges from user management.

