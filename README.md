# hexagonal-scim

Production-grade multi-module Spring Boot service using hexagonal architecture.

## Project Structure

```
hexagonal-scim/
├── Dockerfile
├── docker-compose.yml
├── pom.xml                                          # Root aggregator POM
│
├── documenttaion/diagrams/
│   ├── diag-component.puml                          # Component diagram
│   └── diag-sequence.puml                           # Sequence diagram
│
├── hex-core/                                        # Domain + ports (no dependencies on other modules)
│   └── src/main/java/com/example/user/
│       ├── model/
│       │   └── User.java
│       ├── core/
│       │   ├── UserService.java
│       │   ├── DuplicateUserException.java
│       │   └── UserNotFoundException.java
│       └── port/
│           ├── in/
│           │   ├── CreateUserPort.java
│           │   └── GetUserPort.java
│           └── out/
│               └── UserRepositoryPort.java
│
├── hex-inbound-adapter-web/                         # REST adapter → depends on hex-core
│   └── src/main/java/com/example/user/
│       ├── api/
│       │   ├── UserControllerAdapter.java
│       │   ├── ApiExceptionHandlerAdapter.java
│       │   ├── config/
│       │   │   ├── LegacyApiDeprecationProperties.java
│       │   │   ├── ApiPaginationProperties.java
│       │   │   └── OpenApiConfig.java
│       │   └── dto/
│       │       ├── CreateUserRequest.java
│       │       ├── UserResponse.java
│       │       ├── PagedUserResponse.java
│       │       └── ErrorResponse.java
│
├── hex-outbound-adapter-db/                         # JPA adapter → depends on hex-core
│   └── src/main/java/com/example/user/adapter/db/
│       ├── UserRepositoryAdapter.java
│       ├── UserJpaRepository.java
│       └── UserEntity.java
│
├── hex-application/                                 # Boot entry + wiring → depends on all modules
│   └── src/main/
│       ├── java/com/example/user/
│       │   ├── HexagonalScimApplication.java
│       │   └── config/
│       │       └── UserConfig.java
│       └── resources/
│           ├── application.yml
│           ├── application-docker.yml
│           └── db/migration/
│               ├── V1__create_users_table.sql
│               └── V2__next_change_template.sql
│
└── postman/
    ├── hexagonal-scim.postman_collection.json
    └── local.postman_environment.json
```

## Architecture

```
                        ┌─────────────────────────────┐
                        │   hex-inbound-adapter-web   │
                        │  ┌───────────────────────┐  │
          HTTP REST ───►│  │ UserControllerAdapter │  │
          (v1 + legacy) │  │ ApiExceptionHandler   │  │
                        │  │ LegacyDeprecation      │  │
                        │  │       Props            │  │
                        └──┼───────────────────────┼──┘
                           │      port.in           │
              ┌────────────▼────────────────────────▼─────────────┐
              │                  hex-core                          │
              │   ╔══════════════════════════════════╗             │
              │   ║   CreateUserPort  GetUserPort    ║  port.in   │
              │   ╠══════════════════════════════════╣             │
              │   ║          UserService             ║  domain    │
              │   ║    User  ·  DuplicateUserEx      ║             │
              │   ║         UserNotFoundException    ║             │
              │   ╠══════════════════════════════════╣             │
              │   ║       UserRepositoryPort         ║  port.out  │
              │   ╚══════════════════════════════════╝             │
              └────────────┬────────────────────────┬─────────────┘
                           │      port.out           │
                        ┌──┼───────────────────────┼──┐
                        │  │ UserRepositoryAdapter │  │
          PostgreSQL ◄──│  │ UserJpaRepository     │  │
          H2 (tests) ◄──│  │ UserEntity            │  │
                        │  └───────────────────────┘  │
                        │   hex-outbound-adapter-db   │
                        └─────────────────────────────┘

     ┌──────────────────────────────────────────────────────────┐
     │                    hex-application                        │
     │   HexagonalScimApplication  ·  UserConfig (bean wiring)  │
     │   application.yml  ·  application-docker.yml             │
     │   Flyway: V1__create_users_table  ·  V2__template        │
     └──────────────────────────────────────────────────────────┘

  Module dependency rules:
  hex-inbound-adapter-web ──► hex-core
  hex-outbound-adapter-db ──► hex-core
  hex-application         ──► all modules
  hex-core                ──► (none)
```

## Architecture (Interactive)

```mermaid
flowchart LR
    subgraph EXT_LEFT["🌐 External (Inbound)"]
        direction TB
        Client(["👤 API Client\nHTTP REST"])
        Postman(["📬 Postman\nCollection"])
    end

    subgraph WEB["📦 hex-inbound-adapter-web"]
        direction TB
        Controller["UserControllerAdapter\n/api/v1/users\n/api/users (legacy⚠️)"]
        ExHandler["ApiExceptionHandlerAdapter"]
        LegacyProps["LegacyApiDeprecationProperties\nDeprecation · Sunset · Link"]
    end

    subgraph CORE["⬡ hex-core  (no module dependencies)"]
        direction TB
        subgraph INPORTS["Inbound Ports (port.in)"]
            CreatePort(["CreateUserPort"])
            GetPort(["GetUserPort"])
        end
        subgraph DOMAIN["Domain"]
            UserService["UserService"]
            UserModel["User"]
            DupEx["DuplicateUserException"]
            NotFoundEx["UserNotFoundException"]
        end
        subgraph OUTPORTS["Outbound Ports (port.out)"]
            RepoPort(["UserRepositoryPort"])
        end
    end

    subgraph DB["📦 hex-outbound-adapter-db"]
        direction TB
        RepoAdapter["UserRepositoryAdapter"]
        JpaRepo["UserJpaRepository\n(Spring Data)"]
        Entity["UserEntity"]
    end

    subgraph APP["📦 hex-application"]
        direction TB
        Boot["HexagonalScimApplication"]
        Config["UserConfig\n(bean wiring)"]
        Flyway["Flyway Migrations\nV1 · V2"]
    end

    subgraph EXT_RIGHT["🗄️ External (Outbound)"]
        direction TB
        Pg[("PostgreSQL\ndocker-compose")]
        H2[("H2\ntests / local")]
    end

    Client -->|POST · GET| Controller
    Postman -->|API tests| Controller
    Controller --> CreatePort
    Controller --> GetPort
    LegacyProps -.->|header values| Controller
    ExHandler -.->|maps| DupEx
    ExHandler -.->|maps| NotFoundEx

    CreatePort & GetPort --> UserService
    UserService --> RepoPort
    UserService --> UserModel

    RepoPort -.->|implemented by| RepoAdapter
    RepoAdapter --> JpaRepo
    RepoAdapter <-.->|maps| Entity

    JpaRepo --> Pg
    JpaRepo --> H2

    Config -.->|wires| CreatePort & GetPort & RepoPort
    Config -.->|creates| RepoAdapter
    Boot --> Config
    Flyway -->|schema| Pg

    style CORE fill:#172554,stroke:#818CF8,color:#E6EDF3
    style WEB fill:#1E293B,stroke:#38BDF8,color:#E6EDF3
    style DB fill:#1F2937,stroke:#34D399,color:#E6EDF3
    style APP fill:#111827,stroke:#F59E0B,color:#E6EDF3
    style INPORTS fill:#1e1b4b,stroke:#818CF8,color:#E6EDF3
    style OUTPORTS fill:#2e1065,stroke:#A78BFA,color:#E6EDF3
    style DOMAIN fill:#1e3a5f,stroke:#C4B5FD,color:#E6EDF3
    style EXT_LEFT fill:#0f172a,stroke:#93C5FD,color:#E6EDF3
    style EXT_RIGHT fill:#0f172a,stroke:#FCA5A5,color:#E6EDF3
```

> **Dependency rule**: arrows between modules only flow inward toward `hex-core`.  
> `hex-application` is the only module that depends on all others and owns all bean wiring.

## Modules

- `hex-core`: domain, input/output ports, and use cases.
- `hex-inbound-adapter-web`: REST API adapter (`api`) that depends on `hex-core`.
- `hex-outbound-adapter-db`: JPA persistence adapter that depends on `hex-core`.
- `hex-application`: runnable Spring Boot app and wiring (`config`) that depends on all modules.

## Database migrations

- Schema is migration-driven with Flyway.
- Migrations live in `hex-application/src/main/resources/db/migration/`:
  - `V1__create_users_table.sql` — Initial schema with users table.
  - `V2__next_change_template.sql` — Placeholder for future changes.
  - `V3__add_sample_users.sql` — Sample data (25 test users) for local development and testing.
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
- All endpoints support pagination with `?page=0&size=20` query parameters (optional, defaults shown).

## Endpoints

- `POST   /api/v1/users`        — Create a new user
- `GET    /api/v1/users`        — List all users (paginated), e.g., `GET /api/v1/users?page=0&size=10`
- `GET    /api/v1/users/{id}`   — Get a user by ID
- `PUT    /api/v1/users/{id}`   — Full replacement — both `email` and `displayName` required
- `PATCH  /api/v1/users/{id}`   — Partial update — at least one of `email` or `displayName`
- `DELETE /api/v1/users/{id}`   — Delete a user (returns `204 No Content`)
- `/api/users`                  — Legacy paths (deprecated, returns `Deprecation`, `Sunset`, `Link` headers)

## API Documentation (Swagger UI)

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON spec**: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

Paths are configurable via `springdoc.swagger-ui.path` and `springdoc.api-docs.path` in `application.yml`.



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

## Run with PostgreSQL Profile

Switch to PostgreSQL by activating the `postgresql` profile (instead of default H2 in-memory):

```bash
mvn -pl hex-application spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=postgresql"
```

Requires a PostgreSQL server on `localhost:5432` with:
- Database: `hexdb`
- User: `hexuser`
- Password: `hexpassword`

Alternatively, update `hex-application/src/main/resources/application-postgresql.yml` for different credentials/host.

## Run With Docker Compose

```bash
docker compose up --build
```

## Quick test

```bash
# Create a user
curl -i -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","displayName":"Alice"}'

# Get all users (paginated, default page=0, size=20)
curl -i http://localhost:8080/api/v1/users

# Get page 2 with 5 items per page
curl -i 'http://localhost:8080/api/v1/users?page=1&size=5'

# Get a user by ID
curl -i http://localhost:8080/api/v1/users/1

# Full replacement (PUT)
curl -i -X PUT http://localhost:8080/api/v1/users/1 \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice2@example.com","displayName":"Alice Renamed"}'

# Partial update (PATCH) — only displayName changes, email is preserved
curl -i -X PATCH http://localhost:8080/api/v1/users/1 \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"Alice Renamed"}'

# Delete a user
curl -i -X DELETE http://localhost:8080/api/v1/users/1

# Legacy endpoint (returns Deprecation headers)
curl -i http://localhost:8080/api/users/1
```
