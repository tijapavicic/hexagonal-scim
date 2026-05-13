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
├── hex-payment-core/                                # Product/Payment domain + ports (no dependencies on other modules)
│   └── src/main/java/com/example/user/
│       ├── model/
│       │   ├── Product.java
│       │   ├── Payment.java
│       │   ├── PaymentMethod.java
│       │   └── PaymentStatus.java
│       ├── core/
│       │   ├── ProductService.java
│       │   └── PaymentService.java
│       └── port/
│           ├── in/
│           │   ├── CreateProductPort.java
│           │   └── InitiatePaymentPort.java
│           └── out/
│               ├── ProductRepositoryPort.java
│               ├── PaymentRepositoryPort.java
│               ├── PaymentGatewayPort.java
│               └── PaymentStrategyPort.java
│
├── hex-inbound-adapter-payment-web/                 # Payment/Product REST adapter → depends on hex-payment-core
│   └── src/main/java/com/example/user/api/payment/
│       ├── ProductControllerAdapter.java
│       ├── PaymentControllerAdapter.java
│       ├── PaymentApiExceptionHandler.java
│       └── dto/
│
├── hex-outbound-adapter-payment-db/                 # Payment/Product JPA + strategy adapters → depends on hex-payment-core
│   └── src/main/java/com/example/user/
│       ├── adapter/payment/db/
│       ├── adapter/payment/
│       └── config/
│
├── hex-application/                                 # Boot entry + wiring → depends on all modules
│   └── src/main/
│       ├── java/com/example/user/
│       │   ├── HexagonalScimApplication.java
│       │   └── config/
│       │       ├── UserConfig.java
│       │       ├── ProductConfig.java
│       │       └── PaymentConfig.java
│       └── resources/
│           ├── application.yml
│           ├── application-docker.yml
│           └── db/migration/
│               ├── V1__create_users_table.sql
│               ├── V2__next_change_template.sql
│               ├── V3__add_sample_users.sql
│               ├── V4__create_products_table.sql
│               ├── V5__create_payments_table.sql
│               └── V6__seed_base_cat_house.sql
│
└── postman/
    ├── hexagonal-scim.postman_collection.json
    └── local.postman_environment.json
```

## Architecture

```
   USER FLOW
                        ┌─────────────────────────────┐
                        │   hex-inbound-adapter-web   │
                        │  ┌───────────────────────┐  │
          HTTP REST ───►│  │ UserControllerAdapter │  │
                        │  │ ApiExceptionHandler   │  │
                        └──┼───────────────────────┼──┘
                           │      port.in           │
              ┌────────────▼────────────────────────▼─────────────┐
              │                  hex-core                          │
              │   ╔══════════════════════════════════╗             │
              │   ║   CreateUserPort  GetUserPort    ║  port.in   │
              │   ╠══════════════════════════════════╣             │
              │   ║          UserService             ║  domain     │
              │   ║    User · Duplicate/UserNotFound ║             │
              │   ╠══════════════════════════════════╣             │
              │   ║       UserRepositoryPort         ║  port.out   │
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


   PRODUCT/PAYMENT FLOW
                   ┌───────────────────────────────────────┐
                   │  hex-inbound-adapter-payment-web      │
                   │  ┌─────────────────────────────────┐  │
    HTTP REST ────►│  │ ProductControllerAdapter       │  │
                   │  │ PaymentControllerAdapter       │  │
                   │  │ PaymentApiExceptionHandler     │  │
                   │  └─────────────────────────────────┘  │
                   └───────────────┬───────────────────────┘
                                   │ port.in
              ┌────────────────────▼────────────────────────────┐
              │               hex-payment-core                   │
              │ ╔══════════════════════════════════════════════╗ │
              │ ║ Create/Get/Update/DeleteProductPort          ║ │
              │ ║ Initiate/Get/GetAllPaymentPort               ║ │
              │ ╠══════════════════════════════════════════════╣ │
              │ ║ ProductService · PaymentService              ║ │
              │ ║ Product · Payment · PaymentMethod/Status     ║ │
              │ ╠══════════════════════════════════════════════╣ │
              │ ║ ProductRepositoryPort · PaymentRepositoryPort║ │
              │ ║ PaymentStrategyPort · PaymentGatewayPort     ║ │
              │ ╚══════════════════════════════════════════════╝ │
              └───────────────┬───────────────────────┬─────────┘
                              │ port.out              │ strategy
               ┌──────────────▼──────────────┐    ┌──▼────────────────────────┐
               │ hex-outbound-adapter-       │    │ hex-outbound-adapter-      │
               │ payment-db (JPA)            │    │ payment-db (Gateway impls) │
               │ Product/Payment RepoAdapter │    │ BankAccount/PayPal/IDEAL   │
               │ ProductEntity/PaymentEntity │    │ PaymentStrategyRegistry     │
               └──────────────┬──────────────┘    └────────────────────────────┘
                              │
                      PostgreSQL / H2


     ┌────────────────────────────────────────────────────────────┐
     │                      hex-application                       │
     │  HexagonalScimApplication                                 │
     │  UserConfig · ProductConfig · PaymentConfig (bean wiring)│
     │  Flyway: V1 users · V4 products · V5 payments            │
     └────────────────────────────────────────────────────────────┘

  Module dependency rules:
  hex-inbound-adapter-web ──► hex-core
  hex-outbound-adapter-db ──► hex-core
  hex-inbound-adapter-payment-web ──► hex-payment-core
  hex-outbound-adapter-payment-db ──► hex-payment-core
  hex-application         ──► all modules
  hex-core                ──► (none)
  hex-payment-core        ──► (none)
```

## Architecture (Interactive)

```mermaid
flowchart LR
    subgraph EXT_LEFT["🌐 External (Inbound)"]
        direction TB
        Client(["👤 API Client\nHTTP REST"])
        Postman(["📬 Postman\nCollection"])
    end

    subgraph WEB_USER["📦 hex-inbound-adapter-web"]
        direction TB
        UserController["UserControllerAdapter\n/api/v1/users\n/api/users (legacy⚠️)"]
        UserExHandler["ApiExceptionHandlerAdapter"]
        LegacyProps["LegacyApiDeprecationProperties\nDeprecation · Sunset · Link"]
    end

    subgraph WEB_PAY["📦 hex-inbound-adapter-payment-web"]
        direction TB
        ProductController["ProductControllerAdapter\n/api/v1/products"]
        PaymentController["PaymentControllerAdapter\n/api/v1/payments"]
        PaymentExHandler["PaymentApiExceptionHandler"]
    end

    subgraph CORE_USER["⬡ hex-core  (no module dependencies)"]
        direction TB
        subgraph USER_IN["Inbound Ports (port.in)"]
            CreateUser(["CreateUserPort"])
            GetUser(["GetUserPort"])
        end
        subgraph USER_DOMAIN["Domain"]
            UserService["UserService"]
            UserModel["User"]
            DupEx["DuplicateUserException"]
            NotFoundEx["UserNotFoundException"]
        end
        subgraph USER_OUT["Outbound Ports (port.out)"]
            UserRepoPort(["UserRepositoryPort"])
        end
    end

    subgraph CORE_PAY["⬡ hex-payment-core  (no module dependencies)"]
        direction TB
        subgraph PAY_IN["Inbound Ports (port.in)"]
            ProductPorts(["Create/Get/Update/DeleteProductPort"])
            PaymentPorts(["Initiate/Get/GetAllPaymentPort"])
        end
        subgraph PAY_DOMAIN["Domain"]
            ProductService["ProductService"]
            PaymentService["PaymentService"]
            ProductModel["Product"]
            PaymentModel["Payment"]
            PaymentEnums["PaymentMethod · PaymentStatus"]
        end
        subgraph PAY_OUT["Outbound Ports (port.out)"]
            ProductRepoPort(["ProductRepositoryPort"])
            PaymentRepoPort(["PaymentRepositoryPort"])
            StrategyPort(["PaymentStrategyPort"])
            GatewayPort(["PaymentGatewayPort"])
        end
    end

    subgraph DB_USER["📦 hex-outbound-adapter-db"]
        direction TB
        UserRepoAdapter["UserRepositoryAdapter"]
        UserJpaRepo["UserJpaRepository\n(Spring Data)"]
        UserEntity["UserEntity"]
    end

    subgraph DB_PAY["📦 hex-outbound-adapter-payment-db"]
        direction TB
        ProductRepoAdapter["ProductRepositoryAdapter"]
        PaymentRepoAdapter["PaymentRepositoryAdapter"]
        ProductJpaRepo["ProductJpaRepository"]
        PaymentJpaRepo["PaymentJpaRepository"]
        ProductEntity["ProductEntity"]
        PaymentEntity["PaymentEntity"]
        StrategyRegistry["PaymentStrategyRegistry"]
        BankGateway["BankTransferGatewayAdapter\n(BANK_ACCOUNT)"]
        PayPalGateway["PayPalGatewayAdapter"]
        IdealGateway["IdealGatewayAdapter"]
    end

    subgraph APP["📦 hex-application"]
        direction TB
        Boot["HexagonalScimApplication"]
        UserConfig["UserConfig\n(bean wiring)"]
        ProductConfig["ProductConfig"]
        PaymentConfig["PaymentConfig"]
        Flyway["Flyway Migrations\nV1..V5"]
    end

    subgraph EXT_RIGHT["🗄️ External (Outbound)"]
        direction TB
        Pg[("PostgreSQL\ndocker-compose")]
        H2[("H2\ntests / local")]
    end

    Client -->|POST · GET users| UserController
    Client -->|CRUD products / payments| ProductController
    Client -->|Pay by BANK_ACCOUNT / PAYPAL / IDEAL| PaymentController
    Postman -->|API tests| UserController
    Postman -->|API tests| ProductController
    Postman -->|API tests| PaymentController

    UserController --> CreateUser
    UserController --> GetUser
    LegacyProps -.->|header values| UserController
    UserExHandler -.->|maps| DupEx
    UserExHandler -.->|maps| NotFoundEx

    ProductController --> ProductPorts
    PaymentController --> PaymentPorts
    PaymentExHandler -.->|maps product/payment errors| PaymentService

    CreateUser & GetUser --> UserService
    UserService --> UserRepoPort
    UserService --> UserModel

    ProductPorts --> ProductService
    PaymentPorts --> PaymentService
    ProductService --> ProductModel
    PaymentService --> PaymentModel
    PaymentService --> PaymentEnums
    ProductService --> ProductRepoPort
    PaymentService --> PaymentRepoPort
    PaymentService --> StrategyPort
    StrategyPort --> GatewayPort

    UserRepoPort -.->|implemented by| UserRepoAdapter
    UserRepoAdapter --> UserJpaRepo
    UserRepoAdapter <-.->|maps| UserEntity

    ProductRepoPort -.->|implemented by| ProductRepoAdapter
    PaymentRepoPort -.->|implemented by| PaymentRepoAdapter
    ProductRepoAdapter --> ProductJpaRepo
    PaymentRepoAdapter --> PaymentJpaRepo
    ProductRepoAdapter <-.->|maps| ProductEntity
    PaymentRepoAdapter <-.->|maps| PaymentEntity

    StrategyPort -.->|implemented by| StrategyRegistry
    StrategyRegistry --> BankGateway
    StrategyRegistry --> PayPalGateway
    StrategyRegistry --> IdealGateway
    BankGateway -.->|returns PENDING| PaymentService
    PayPalGateway -.->|returns COMPLETED| PaymentService
    IdealGateway -.->|returns COMPLETED| PaymentService

    UserJpaRepo --> Pg
    UserJpaRepo --> H2
    ProductJpaRepo --> Pg
    PaymentJpaRepo --> Pg
    ProductJpaRepo --> H2
    PaymentJpaRepo --> H2

    UserConfig -.->|wires| CreateUser & GetUser & UserRepoPort
    ProductConfig -.->|wires| ProductPorts & ProductRepoPort
    PaymentConfig -.->|wires| PaymentPorts & PaymentRepoPort & StrategyPort
    Boot --> UserConfig
    Boot --> ProductConfig
    Boot --> PaymentConfig
    Flyway -->|schema| Pg

    style CORE_USER fill:#172554,stroke:#818CF8,color:#E6EDF3
    style CORE_PAY fill:#1f3a8a,stroke:#60A5FA,color:#E6EDF3
    style WEB_USER fill:#1E293B,stroke:#38BDF8,color:#E6EDF3
    style WEB_PAY fill:#0f766e,stroke:#2DD4BF,color:#E6EDF3
    style DB_USER fill:#1F2937,stroke:#34D399,color:#E6EDF3
    style DB_PAY fill:#334155,stroke:#22D3EE,color:#E6EDF3
    style APP fill:#111827,stroke:#F59E0B,color:#E6EDF3
    style USER_IN fill:#1e1b4b,stroke:#818CF8,color:#E6EDF3
    style USER_OUT fill:#2e1065,stroke:#A78BFA,color:#E6EDF3
    style USER_DOMAIN fill:#1e3a5f,stroke:#C4B5FD,color:#E6EDF3
    style PAY_IN fill:#0c4a6e,stroke:#7DD3FC,color:#E6EDF3
    style PAY_OUT fill:#164e63,stroke:#67E8F9,color:#E6EDF3
    style PAY_DOMAIN fill:#1e40af,stroke:#93C5FD,color:#E6EDF3
    style EXT_LEFT fill:#0f172a,stroke:#93C5FD,color:#E6EDF3
    style EXT_RIGHT fill:#0f172a,stroke:#FCA5A5,color:#E6EDF3
```

> **Dependency rule**: arrows between modules only flow inward toward `hex-core` or `hex-payment-core`.  
> `hex-application` is the only module that depends on all others and owns all bean wiring.

## Modules

- `hex-core`: domain, input/output ports, and use cases.
- `hex-inbound-adapter-web`: REST API adapter (`api`) that depends on `hex-core`.
- `hex-outbound-adapter-db`: JPA persistence adapter that depends on `hex-core`.
- `hex-payment-core`: product/payment domain, input/output ports, and use cases.
- `hex-inbound-adapter-payment-web`: product/payment REST API adapter that depends on `hex-payment-core`.
- `hex-outbound-adapter-payment-db`: product/payment JPA + payment strategy adapter that depends on `hex-payment-core`.
- `hex-application`: runnable Spring Boot app and wiring (`config`) that depends on all modules.

## Database migrations

- Schema is migration-driven with Flyway.
- Migrations live in `hex-application/src/main/resources/db/migration/`:
  - `V1__create_users_table.sql` — Initial schema with users table.
  - `V2__next_change_template.sql` — Placeholder for future changes.
  - `V3__add_sample_users.sql` — Sample data (25 test users) for local development and testing.
  - `V4__create_products_table.sql` — Product catalog schema.
  - `V5__create_payments_table.sql` — Payment transaction schema.
  - `V6__seed_base_cat_house.sql` — Seeds the single sellable product `BaseCatHouse`.
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
- `POST   /api/v1/products`     — Create product
- `GET    /api/v1/products`     — List products
- `GET    /api/v1/products/{id}`— Get product by ID
- `PUT    /api/v1/products/{id}`— Update product
- `DELETE /api/v1/products/{id}`— Delete product
- `POST   /api/v1/payments`     — Initiate payment (`BANK_ACCOUNT`, `PAYPAL`, `IDEAL`), optional `currency` (`EUR` default, `USD` supported)
- `GET    /api/v1/payments`     — List payments
- `GET    /api/v1/payments/{id}`— Get payment by ID

> **Current production phase**: product catalog is read-only and only `BaseCatHouse` is sellable.
> Payment attempts for other product types are rejected.

## API Documentation (Swagger UI)

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON spec**: `http://localhost:8080/api-docs`

Paths are configurable via `springdoc.swagger-ui.path` and `springdoc.api-docs.path` in `application.yml`.

## Postman

- Collection: `postman/hexagonal-scim.postman_collection.json`
- Environments: `postman/local.postman_environment.json` (no auth), `postman/local-docker.postman_environment.json` (with Keycloak)
- Run **Keycloak → Get Token — testuser** first; the JWT is stored automatically and used by all API requests.

## Frontend

A React + TypeScript single-page application is included in the `frontend/` directory.

**Technology stack**: React 18 · Vite · TypeScript · Tailwind CSS · keycloak-js · TanStack Query · React Router

**Features**: Keycloak SSO login (PKCE), paginated user table, create / edit / delete modals, role badges in navbar, automatic token refresh.

### Run frontend in development mode

```bash
# Terminal 1 — backend (H2, no auth)
mvn -pl hex-application spring-boot:run

# Terminal 2 — frontend dev server with hot-reload (port 3000)
cd frontend && npm install && npm run dev
```

> In dev mode the Vite proxy forwards `/api/*` to `http://localhost:8080`, so there is no CORS issue.  
> Auth is disabled on the backend when started without Keycloak, so no login is required.

### Run everything with Docker Compose (with auth)

```bash
docker compose down -v && docker compose up --build
```

Docker Compose automatically merges `docker-compose.override.yml` — this exposes port 8080 (backend) and 5432 (PostgreSQL) to the host for local development.

| Service | URL |
|---------|-----|
| Frontend (React) | https://localhost:3000 |
| Backend (Spring Boot) | http://localhost:8080 (dev only — via override) |
| Keycloak admin | https://localhost:8443 |
| Prometheus | http://localhost:9090 (dev only — via override) |
| Grafana | http://localhost:3001  admin / admin (dev only — via override) |
| Splunk Web UI | http://localhost:8000  admin / Admin1234! (dev only — via override) |

**Production / CI — skip the dev override:**

```bash
docker compose -f docker-compose.yml up --build
```

Port 8080 is NOT exposed on the host. All external API traffic flows through Nginx on port 3000.

Login with `testuser / password` or `adminuser / password` — Keycloak redirects back automatically.

> **Self-signed certificate**: the browser will show a security warning on first visit.  
> Click **Advanced → Proceed** (Chrome) or **Accept the Risk** (Firefox).  
> The cert lives in `docker/certs/` and is valid for 10 years.

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

Starts three services: **PostgreSQL** (5432), **Keycloak** (8180), and the **Spring Boot app** (8080).  
The `hexagonal-scim` realm, two clients, two test users, and two roles are imported automatically.

---

## Authentication (Keycloak)

> Full guide: `documenttaion/KEYCLOAK_LOCAL_DEVELOPMENT.md`

### Keycloak admin console

| URL | Username | Password |
|-----|----------|----------|
| https://localhost:8443 | `admin` | `admin` |

### Test users (realm: `hexagonal-scim`)

| Username | Password | Roles |
|----------|----------|-------|
| `testuser` | `password` | `user` |
| `adminuser` | `password` | `user`, `admin` |

### Get an access token

```bash
# Password grant — testuser
curl -sk -X POST \
  https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token \
  -d "grant_type=password&client_id=hexagonal-scim-public&username=testuser&password=password" \
  | jq -r .access_token

# Client credentials — M2M
curl -sk -X POST \
  https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token \
  -d "grant_type=client_credentials&client_id=hexagonal-scim-app&client_secret=hexagonal-scim-secret" \
  | jq -r .access_token
```

### Store token and call the API

```bash
TOKEN=$(curl -sk -X POST \
  https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token \
  -d "grant_type=password&client_id=hexagonal-scim-public&username=testuser&password=password" \
  | jq -r .access_token)

curl -i http://localhost:8080/api/v1/users -H "Authorization: Bearer $TOKEN"
```

### Security behaviour

| Startup mode | JWT enforced? |
|---|---|
| `docker compose up` (Keycloak running) | ✅ Yes — `401` without valid token |
| `mvn spring-boot:run` (H2, no Keycloak) | ❌ No — open, dev convenience |

> **Self-signed cert**: use `curl -sk` (skip TLS verification) or trust the cert in your OS keychain. See `docker/certs/` for the cert file.

> **Postman**: import `postman/hexagonal-scim.postman_collection.json` and `postman/local-docker.postman_environment.json`.  
> Run **"Get Token — testuser"** first — it stores the JWT automatically and all subsequent API requests use it.

---

## TODO

### 🔴 Critical — Before Production

#### 🔴 CRITICAL 1 — Close port 8080 to the host

`app` in `docker-compose.yml` has `ports: "8080:8080"` which binds the backend HTTP port to `0.0.0.0` on the host. Any process on the machine (or the LAN if the firewall allows) can call `http://localhost:8080` directly, bypassing Nginx and sending unencrypted traffic containing JWT bearer tokens.

**Fix — remove the `ports` mapping from the `app` service:**

```yaml
# docker-compose.yml — BEFORE (dev)
app:
  ports:
    - "8080:8080"   # ← remove this in production

# docker-compose.yml — AFTER (production)
app:
  # No ports: — backend only reachable through Nginx on hexagonal-net
  expose:
    - "8080"        # internal Docker network only; tells Nginx which port to proxy
```

Nginx already proxies `/api/*` to `http://app:8080` on the internal `hexagonal-net` bridge — removing the host port binding closes the direct path without breaking anything.

---

#### 🔴 CRITICAL 2 — Replace self-signed TLS certificate

`docker/certs/server.crt` is a self-signed dev certificate. Browsers show a security warning, HSTS is not effective, and the cert cannot be pinned or validated by external services. **Do not use in staging or production.**

**Fix options (choose one based on environment):**

**Option A — Let's Encrypt (public server, automated renewal):**
```bash
# Install Certbot
brew install certbot   # macOS
# or: apt install certbot

# Obtain cert (requires port 80 open and a real domain)
certbot certonly --standalone -d yourdomain.com

# Certs land at:
#   /etc/letsencrypt/live/yourdomain.com/fullchain.pem  → server.crt
#   /etc/letsencrypt/live/yourdomain.com/privkey.pem    → server.key

# Mount into docker-compose.yml:
volumes:
  - /etc/letsencrypt/live/yourdomain.com/fullchain.pem:/etc/nginx/certs/server.crt:ro
  - /etc/letsencrypt/live/yourdomain.com/privkey.pem:/etc/nginx/certs/server.key:ro
```

**Option B — Corporate / internal CA (air-gapped / private network):**
```bash
# Generate CSR
openssl req -new -newkey rsa:2048 -nodes \
  -keyout server.key \
  -out server.csr \
  -subj "/CN=yourdomain.internal/O=YourOrg"

# Submit server.csr to your CA → receive server.crt (+ chain)
# Replace docker/certs/server.crt and server.key
# Distribute the CA root cert to browsers / OS trust stores
```

**Option C — AWS / GCP / Azure (cloud-managed TLS):**
- Terminate TLS at the load balancer (ACM, Cloud Armor, Azure Front Door)
- Remove TLS from Nginx entirely — the LB handles it
- Backend stays HTTP-only on the internal VPC network (same pattern as now, but properly isolated)

**Also update `KC_HTTPS_CERTIFICATE_FILE` in `docker-compose.yml`** to point to the new cert path for Keycloak.

---

| Priority | Item | Detail |
|----------|------|--------|
| 🟠 HIGH | **Rotate all default secrets** | `KEYCLOAK_ADMIN_PASSWORD: admin`, `hexagonal-scim-secret` client secret, DB passwords (`scim/scim`) are hardcoded defaults. Move to environment-specific secrets management (Vault, AWS Secrets Manager, Kubernetes Secrets). |
| 🟠 HIGH | **Restrict actuator exposure** | `/actuator/health` and `/actuator/info` are public. Audit before adding more endpoints. In production, bind actuator to a separate management port not exposed externally. |
| 🟡 MEDIUM | **Enable Keycloak production mode** | Keycloak runs with `start-dev` which disables caches, uses in-memory sessions, and is not hardened. Switch to `start` (production mode) with proper hostname configuration. |
| 🟡 MEDIUM | **Separate Keycloak database** | Keycloak and the app share the same PostgreSQL instance via `init-keycloak.sql`. Use separate database instances in production for failure isolation. |

---

## Versions

### v0.0.4 (2026-05-11)

**Observability — Prometheus + Grafana (metrics) + Splunk + Fluent Bit (logs)**

| Area | What was added |
|------|----------------|
| **Micrometer / Prometheus** | `micrometer-registry-prometheus` added. `/actuator/prometheus` exposed. Histogram buckets enabled for P50/P95/P99 latency. Metrics tagged with `application=hexagonal-scim`. |
| **Structured logging** | `logstash-logback-encoder 8.0` added. `logback-spring.xml` profile-aware: plain text locally, JSON to stdout + rolling file (`/app/logs/app.log`) in docker profile. |
| **Prometheus** | `docker/prometheus/prometheus.yml` — scrapes `app:8080/actuator/prometheus` every 10s. `prom/prometheus:v2.54.1`. |
| **Grafana** | `docker/grafana/` — auto-provisioned Prometheus datasource + custom Spring Boot 3 / JVM dashboard (11 panels: request rate, error rate, p50/p95/p99 latency, heap, CPU, threads, GC, per-endpoint breakdown). `grafana/grafana:11.3.2`. |
| **Fluent Bit** | `docker/fluent-bit/` — tails `/app/logs/app.log` from shared Docker volume, parses JSON, ships to Splunk HEC with retry. `fluent/fluent-bit:3.2`. |
| **Splunk** | `splunk/splunk:9.3` with HEC auto-configured via `SPLUNK_HEC_TOKEN`. Token: `11111111-1111-1111-1111-111111111111`. |
| **Docker Compose** | `app_logs` named volume added to base file (shared between app and Fluent Bit). Override adds all 4 observability services for local dev. New `docker-compose.observability.yml` for staging (no host port bindings, credentials from env vars). |
| **Documentation** | `documenttaion/OBSERVABILITY.md` — full guide: pipeline diagrams, search queries, PromQL reference, production checklist, troubleshooting. |



### v0.0.3 (2026-05-10)

**CI/CD pipeline — GitHub Actions, OWASP gate, Docker push, auto-tagging**

| Area | What was added |
|------|----------------|
| **CI workflow** | `.github/workflows/ci.yml` — triggers on every PR and push to `main`. Jobs: (1) `mvn -B clean verify`, (2) OWASP Dependency Check (CVSS ≥ 7 = fail), (3) multi-arch Docker build verification (no push). Test reports and OWASP HTML report uploaded as artifacts. |
| **Release workflow** | `.github/workflows/release.yml` — triggers on merge to `main`. Auto-increments patch version from latest `vX.Y.Z` git tag, pushes multi-arch images (`linux/amd64`, `linux/arm64`) to `ghcr.io` tagged `vX.Y.Z` + `latest`, creates annotated Git tag and GitHub Release with auto-generated PR-based notes. |
| **OWASP suppression** | `.github/owasp-suppressions.xml` — empty baseline; add entries when a CVE is false-positive or formally accepted/mitigated. |
| **Concurrency** | CI cancels in-flight runs for the same branch on rapid push. Release never cancels mid-run. |
| **Optional secret** | `NVD_API_KEY` — set in repo Settings → Secrets to raise NVD API rate limit and avoid scan throttling. |

---

### v0.0.2 (2026-05-10)

**Profile-based port exposure — port 8080 closed in production, open in local dev**

| Area | What changed |
|------|--------------|
| **`docker-compose.yml`** | Removed `ports: "8080:8080"` from `app` service. Uses `expose: "8080"` — backend is internal-only on `hexagonal-net`. |
| **`docker-compose.override.yml`** (NEW) | Auto-loaded by Docker Compose when running locally (`docker compose up`). Adds `ports: "8080:8080"` for backend Postman/curl access and `ports: "5432:5432"` for DB tools. |
| **CI / production** | Run `docker compose -f docker-compose.yml up` — override is skipped, port 8080 stays closed, all external traffic via Nginx HTTPS. |
| **Security posture** | JWT bearer tokens can no longer be intercepted via unencrypted `http://localhost:8080` in non-dev environments. |

---

### v0.0.2 (2026-05-10)

**HTTPS everywhere + Keycloak healthcheck fix**

| Area | What changed |
|------|--------------|
| **TLS** | Self-signed dev certificate generated (`docker/certs/server.crt` + `server.key`, RSA 2048, 10 yr, SAN: `localhost`, `keycloak`, `127.0.0.1`) |
| **Keycloak** | HTTPS on port **8443** (browser-facing). HTTP stays on 8180 internal-only for JWK-set fetch and healthcheck. `KC_HTTPS_CERTIFICATE_FILE/KEY_FILE` env vars configured |
| **Frontend Nginx** | HTTPS on port **443** (mapped to host 3000). HTTP 80 → 301 redirect. HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy headers added |
| **Keycloak healthcheck** | Fixed: KC 25 UBI image has no `curl`/`wget`. Replaced with `bash /dev/tcp` probe against the OIDC discovery endpoint on internal HTTP port 8180 |
| **Spring Boot** | Unchanged — fetches JWK via `http://keycloak:8180` internally (avoids self-signed cert trust issue) |
| **`docker-compose.yml`** | `VITE_KEYCLOAK_URL` → `https://localhost:8443`, cert volume mounted to KC and Nginx, port mapping `3000:443` |

---

### v0.0.1 (2026-05-10)

**Initial release — hexagonal architecture + Keycloak SSO + React frontend**

| Area | What was added |
|------|----------------|
| **Architecture** | Multi-module hexagonal layout: `hex-core`, `hex-payment-core`, `hex-inbound-adapter-web`, `hex-inbound-adapter-payment-web`, `hex-outbound-adapter-db`, `hex-outbound-adapter-payment-db`, `hex-application` |
| **API** | Full CRUD REST API (`POST`, `GET`, `PUT`, `PATCH`, `DELETE`) on `/api/v1/users` |
| **Legacy API** | Backward-compatible `/api/users` with `Deprecation`, `Sunset`, `Link` headers |
| **Pagination** | `GET /api/v1/users?page=0&size=10` with `PagedUserResponse` |
| **Persistence** | JPA + Flyway migrations (V1–V3). PostgreSQL in production, H2 in tests |
| **Security** | Spring Security OAuth2 Resource Server with Keycloak JWT validation. Conditional on `jwk-set-uri` / `issuer-uri` — disabled automatically for local H2 dev |
| **Keycloak** | Realm `hexagonal-scim`, two clients (`hexagonal-scim-public` PKCE, `hexagonal-scim-app` M2M), two users (`testuser`, `adminuser`), two roles (`user`, `admin`) |
| **Frontend** | React 18 + Vite + TypeScript + Tailwind CSS SPA at `http://localhost:3000`. PKCE login flow, paginated user table, create / edit / delete modals, role badges, automatic token refresh |
| **Docker** | Multi-stage `Dockerfile` (Maven build → `eclipse-temurin:17-jre-jammy`), `docker-compose.yml` with PostgreSQL + Keycloak + Spring Boot + React/Nginx |
| **Docs** | OpenAPI / Swagger UI, Postman collection with Keycloak auth, `KEYCLOAK_LOCAL_DEVELOPMENT.md` |

---

## Quick test

```bash
# Get a token first (when running via Docker Compose)
TOKEN=$(curl -sk -X POST \
  https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token \
  -d "grant_type=password&client_id=hexagonal-scim-public&username=testuser&password=password" \
  | jq -r .access_token)

# Create a user
curl -i -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"email":"alice@example.com","displayName":"Alice"}'

# Get all users (paginated, default page=0, size=10)
curl -i http://localhost:8080/api/v1/users -H "Authorization: Bearer $TOKEN"

# Get page 2 with 5 items per page
curl -i "http://localhost:8080/api/v1/users?page=1&size=5" -H "Authorization: Bearer $TOKEN"

# Get a user by ID
curl -i http://localhost:8080/api/v1/users/1 -H "Authorization: Bearer $TOKEN"

# Full replacement (PUT)
curl -i -X PUT http://localhost:8080/api/v1/users/1 \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"email":"alice2@example.com","displayName":"Alice Renamed"}'

# Partial update (PATCH) — only displayName changes, email is preserved
curl -i -X PATCH http://localhost:8080/api/v1/users/1 \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"displayName":"Alice Renamed"}'

# Delete a user
curl -i -X DELETE http://localhost:8080/api/v1/users/1 -H "Authorization: Bearer $TOKEN"

# Legacy endpoint (returns Deprecation headers)
curl -i http://localhost:8080/api/users/1 -H "Authorization: Bearer $TOKEN"
```


