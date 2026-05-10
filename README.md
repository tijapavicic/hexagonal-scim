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

### v0.0.3 (2026-05-10)

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
| **Architecture** | Multi-module hexagonal layout: `hex-core`, `hex-inbound-adapter-web`, `hex-outbound-adapter-db`, `hex-application` |
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


