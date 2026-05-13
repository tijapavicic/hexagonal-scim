# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Added
- Postman v2 automated test collection for health, BaseCatHouse product policy, and payment flows:
  - `postman/hexagonal-scim-v2.postman_collection.json`
- Runnable test instructions:
  - `TESTING.md`
- CI/CD Newman gates with JUnit XML export and artifacts:
  - `.github/workflows/ci.yml`
  - `.github/workflows/release.yml`
- User accounts feature in hexagonal style (one user -> zero..many accounts, one account -> one user):
  - `Account` domain model + ports + `AccountService` in `hex-core`
  - `AccountControllerAdapter` and account DTOs in `hex-inbound-adapter-web`
  - account persistence adapter/JPA entity/repository in `hex-outbound-adapter-db`
  - Flyway migration `V7__create_accounts_table.sql`
  - tests: `AccountServiceTest`, `AccountControllerAdapterTest`, and application integration coverage

### Changed
- README updated with Newman CLI execution examples and links to the v2 collection and testing guide.
- Release workflow quality gate now validates API behavior before image push/tag creation.
- README endpoint/migration sections updated for accounts API and `V7` migration.

### Reproduce (commands used by this commit)

```bash
# 1) Build and test locally
cd /Users/copor/IdeaProjects/hexagonal-scim
mvn -B clean verify

# 2) Run app locally (no Keycloak required)
mvn -B -pl hex-application -am spring-boot:run

# 3) Run Postman tests via Newman (v2 collection)
newman run postman/hexagonal-scim-v2.postman_collection.json

# 4) Optional: run only core smoke folders (same as CI/release workflows)
newman run postman/hexagonal-scim-v2.postman_collection.json \
  --folder "1. Health" \
  --folder "2. Products (BaseCatHouse only)" \
  --folder "3. Payments"

# 5) Accounts API quick check
curl -i -X POST http://localhost:8080/api/v1/users/1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"name":"Primary"}'

curl -i http://localhost:8080/api/v1/users/1/accounts
```

## [2.0.0] - 2026-05-14

### Added
- Payment domain and application flow across dedicated modules:
  - `hex-payment-core`
  - `hex-inbound-adapter-payment-web`
  - `hex-outbound-adapter-payment-db`
- Multiple payment methods via strategy pattern: `BANK_ACCOUNT`, `PAYPAL`, `IDEAL`.
- Payment API endpoints for create/list/get.
- Product API support for the payment domain.
- Single production product type `BaseCatHouse` (vanilla product) with seed migration.
- Optional payment currency request support (`EUR` default, `USD` supported).
- Integration-test location enforcement script: `scripts/enforce-integration-tests-location.sh`.
- Pull request template with hexagonal architecture checklist.

### Changed
- Project version upgraded to `2.0.0` in all Maven modules.
- Architecture split into separate user core and payment core paths with explicit composition-root wiring in `hex-application`.
- Product phase policy updated to sell one product type only (`BaseCatHouse`).
- Test strategy refined:
  - integration tests only in `hex-application`
  - adapter/core tests converted to unit-focused tests where appropriate.
- Runtime dependency ownership tightened:
  - DB runtime drivers owned by `hex-application`
  - adapter module dependencies trimmed.

### Fixed
- Payment amount/currency handling for supported currencies.
- Compilation and wiring issues introduced during modularization and product-policy rollout.

### Security
- Validation and boundary checks improved through stricter module/test separation.
- Dependency boundaries documented and enforced for hexagonal architecture constraints.

