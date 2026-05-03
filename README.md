# hexagonal-scim

Production-grade multi-module Spring Boot service using hexagonal architecture.

## Modules

- `hex-core`: domain, input/output ports, and use cases.
- `hex-inbound-adapter-web`: REST API adapter (`api`) that depends on `hex-core`.
- `hex-outbound-adapter-db`: JPA persistence adapter that depends on `hex-core`.
- `hex-application`: runnable Spring Boot app and wiring (`config`) that depends on all modules.

## Database migrations

- Schema is migration-driven with Flyway.
- Initial migration is at `hex-application/src/main/resources/db/migration/V1__create_users_table.sql`.
- Hibernate is configured with `ddl-auto: validate` so startup fails if schema and mappings diverge.

## Production release checklist

- Flyway baseline strategy:
  - For existing databases without Flyway history, run a one-time baseline before the first migration deployment.
  - Keep the baseline version documented and aligned across environments.
- Rollback expectations:
  - Treat migrations as forward-only in production.
  - Roll back with a new corrective migration, or restore from backup for critical recovery.
- Post-deploy smoke checks:
  - Confirm app startup logs show Flyway migration success.
  - Verify expected versions in `flyway_schema_history`.
  - Run a minimal read/write API smoke test for changed tables.

## API versioning

- Current endpoint version is `/api/v1/users`.
- Legacy `/api/users` remains temporarily available for backward compatibility.

## Postman

- Collection: `postman/hexagonal-scim.postman_collection.json`
- Environment: `postman/local.postman_environment.json`
- Create requests generate `uniqueEmail` automatically when it is empty.

## Build

```bash
mvn -B clean verify
```

## Run

```bash
mvn -pl hex-application spring-boot:run
```

## Run With Docker Compose

```bash
docker compose up --build
```

## Quick test

```bash
curl -i -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","displayName":"Alice"}'

curl -i http://localhost:8080/api/v1/users/1
```
