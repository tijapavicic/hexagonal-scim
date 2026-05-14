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

## Required Keycloak settings

- Client ID: `hexagonal-scim-public` (or match `VITE_KEYCLOAK_CLIENT_ID`)
- Client type: `public`
- Valid redirect URIs: `http://localhost:3000/*` and `http://localhost:5173/*`
- Web origins: `http://localhost:3000` and `http://localhost:5173`

## Next step

Use the acquired access token for authenticated calls to backend `/api/*` endpoints.

