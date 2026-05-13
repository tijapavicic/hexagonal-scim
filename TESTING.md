# Testing Guide (v2.0.0)

This project now supports payment flow and a single sellable product (`BaseCatHouse`).

## 1) What this covers

- Health check
- Product read flow (including `BaseCatHouse` ID capture)
- Product catalog read-only policy
- Payment creation and retrieval
- Currency behavior (`EUR` default, `USD` supported, unsupported currency rejected)

## 2) Postman assets

- Collection: `postman/hexagonal-scim-v2.postman_collection.json`
- Existing environments (optional):
  - `postman/local.postman_environment.json`
  - `postman/local-docker.postman_environment.json`

## 3) Start the app

### Local (no Keycloak required)

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
mvn -B -pl hex-application -am spring-boot:run
```

### Docker mode (Keycloak + auth)

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
docker compose up --build
```

In Docker mode, run token request first in Postman folder `0. Auth (docker mode)`.

## 4) Run in Postman

1. Import collection `postman/hexagonal-scim-v2.postman_collection.json`.
2. Set `baseUrl` if needed (default `http://localhost:8080`).
3. If using Docker mode with auth enabled, run `Get Token - testuser` first.
4. Run collection in order:
   - `1. Health`
   - `2. Products (BaseCatHouse only)`
   - `3. Payments`

### Expected outcomes

- `Get all products`: captures `baseCatHouseId` automatically.
- `Create product blocked`: returns `405` and code `PRODUCT_CATALOG_READ_ONLY`.
- `Create payment EUR default (PAYPAL)`: returns `201`, `currency=EUR`, status `COMPLETED`.
- `Create payment USD (IDEAL)`: returns `201`, `currency=USD`.
- `Create payment with unsupported currency`: returns `400` and code `INVALID_REQUEST`.

## 5) Optional CLI run with Newman

```bash
newman run postman/hexagonal-scim-v2.postman_collection.json
```

If you need environment variables in Newman, add `-e` with one of the environment JSON files.

