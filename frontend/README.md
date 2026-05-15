# Frontend (Web Components + Keycloak)

This first frontend milestone is intentionally small: the app bootstraps with a custom element and uses redirect-based Keycloak login.

## What this step does

- Uses plain Web Components (`<scim-app>`) for maintainability.
- Initializes `keycloak-js` with `onLoad: 'login-required'` and PKCE (`S256`).
- Redirects to local Keycloak when there is no session.
- Renders authenticated state and supports logout.

## Local setup

1. Copy `.env.example` to `.env` if you need custom values.
2. Install dependencies.
3. Start the Vite dev server.

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim/frontend
npm install
npm run dev
```

## Run frontend with Docker Compose

From repository root, start the stack:

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
docker compose up --build
```

Then open `https://localhost:3000`.

- `testuser` / `password`
- `adminuser` / `password`

> The frontend container serves static assets via Nginx and proxies `/api/*` to the backend container.

## Test coverage (Phase 4)

Run unit + component tests (Vitest):

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim/frontend
npm test
```

Run E2E smoke tests (Playwright):

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim/frontend
npm run test:e2e
```

Optional E2E overrides:

- `E2E_BASE_URL` (default: `https://localhost:3000`)
- `E2E_USERNAME` (default: `testuser`)
- `E2E_PASSWORD` (default: `password`)

Install Playwright browser binaries (one-time if needed):

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim/frontend
npx playwright install chromium
```

## Required Keycloak settings

- Client ID: `hexagonal-scim-public` (or match `VITE_KEYCLOAK_CLIENT_ID`)
- Client type: `public`
- Standard Flow: `Enabled`
- Direct Access Grants: `Enabled` (intentional for local Postman/password-grant workflows)
- Valid redirect URIs: `http://localhost:3000/*`, `https://localhost:3000/*`, `http://localhost:5173/*`, `https://localhost:5173/*`
- Web origins: `http://localhost:3000`, `https://localhost:3000`, `http://localhost:5173`, `https://localhost:5173`

## Troubleshooting

- **Invalid redirect URI**
  - Cause: frontend origin missing in Keycloak client config.
  - Fix: add exact origin/redirect values from this README to `hexagonal-scim-public`.
- **Mixed HTTP/HTTPS in browser**
  - Cause: app opened over `http://` while Keycloak redirect expects `https://` (or the opposite).
  - Fix: when using Docker, always open `https://localhost:3000`.
- **Realm/client config changes not visible**
  - Cause: Keycloak imports `docker/keycloak/realm-export.json` only on first init of a fresh DB volume.
  - Fix: restart with fresh volumes for local reset:

```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
docker compose down -v
docker compose up --build
```
- **Session expired / login loop**
  - Cause: token expired or browser holds stale auth cookies.
  - Fix: use "Sign in again" in UI or clear site data for `localhost` and retry.

## Next step

Use the acquired access token for authenticated calls to backend `/api/*` endpoints.

