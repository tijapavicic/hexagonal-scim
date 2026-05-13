# Changelog

All notable changes to this project are documented in this file.

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

