# Fix: Keycloak "already_logged_in" Authentication Error

## Problem

```
type="RESTART_AUTHENTICATION_ERROR", 
error="already_logged_in", 
redirect_uri="https://localhost:3000/#/payments"
```

### Root Cause

The frontend Keycloak initialization used `onLoad: 'login-required'`, which **forces** a login flow every time the app initializes. When a user:

1. Has an active authenticated session in Keycloak
2. Navigates to a route like `#/payments`
3. The app calls `keycloak.init({ onLoad: 'login-required' })`

Keycloak detects an existing session and the attempt to "restart authentication" conflicts with the existing session, triggering the `RESTART_AUTHENTICATION_ERROR` with `already_logged_in` status.

### Why This Matters

- **Poor UX**: Users get logged out redirects when they simply navigate routes
- **Session conflicts**: Keycloak rejects authentication attempts when a valid session already exists
- **Increased latency**: Unnecessary redirect round-trips on every app initialization

## Solution

Changed the initialization strategy from **`onLoad: 'login-required'`** to **`onLoad: 'check-sso'`**:

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| `login-required` | Forces login prompt every time | ❌ Breaks existing sessions |
| `check-sso` | Checks for existing session; redirects to login only if none exists | ✅ Respects active sessions |

### How It Works Now

1. **First page load** (no session):
   - `keycloak.init({ onLoad: 'check-sso' })` → no session found
   - App calls `login()` → redirects to Keycloak
   - User enters credentials, Keycloak redirects back with auth code
   - Browser reloads, app reinitializes
   - `check-sso` now finds the fresh session

2. **Route navigation** (user already logged in):
   - `keycloak.init({ onLoad: 'check-sso' })` → existing session found
   - App renders normally; no redirect loop

3. **Manual refresh** (user already logged in):
   - `check-sso` validates the existing session instantly
   - No "already_logged_in" error; user stays logged in

4. **Expired token**:
   - Token refresh interval (60s) catches expiry
   - If refresh fails with `session_timeout`, app calls `logout()` → redirects to login

## Files Changed

### `frontend/src/auth/keycloak.ts`
- **Line 146**: Changed `onLoad: 'login-required'` → `onLoad: 'check-sso'`
- **Lines 150–186**: Refactored to use async/await and properly await `login()` call
  - If no session found, awaits `login()` which redirects the browser
  - After user returns from Keycloak, `check-sso` validates the session
  - Token refresh interval still runs; invalid tokens trigger `logout()`

### `frontend/src/components/scim-app.ts`
- **Lines 118–148**: Simplified `bootstrap()` logic
  - Removed unnecessary `!authenticated` check (initAuth always returns true on resolve)
  - Relies on error handlers and `onAuthError` event for failures
  - Comments clarify the redirect flow

## Testing

### Before (reproduces the error)

```bash
# In Docker Compose environment
docker compose up --build

# 1. Open frontend: https://localhost:3000
# 2. Login as testuser / password
# 3. Click "Payments" link (#/payments)
# 4. Refresh the page (Cmd+R or F5)
# 5. Observe: RESTART_AUTHENTICATION_ERROR in Keycloak logs
```

### After (error should be gone)

```bash
docker compose up --build

# Same steps 1–4
# 5. Refresh the page → no error in logs; user stays logged in
```

### Additional Tests

- [ ] Manual login flow works (no session → Keycloak redirect → back to home)
- [ ] Token refresh works (interval keeps token fresh; check Keycloak logs for valid refresh calls)
- [ ] Logout works (button clears session and shows login form)
- [ ] Browser close triggers logout (beforeunload event fires)
- [ ] Route navigation works without redirect loops (click Home, Users, Payments)
- [ ] Expired session is caught and redirects to login (wait for token to expire; refresh → logout)

## Security & Compliance

✅ **No regression**: PKCE flow remains enabled (`pkceMethod: 'S256'`)
✅ **Standard pattern**: `check-sso` is the recommended approach for single-page apps per Keycloak docs
✅ **Token lifecycle**: Refresh interval (60s) + expiry handling unchanged
✅ **No hardcoded timeout**: Uses server-side session configuration (`ssoSessionMaxLifespan`, etc.)

## Rollback

If issues arise, revert both files:
```bash
git checkout frontend/src/auth/keycloak.ts frontend/src/components/scim-app.ts
```

## Related Documentation

- [Keycloak.js Guide](https://www.keycloak.org/docs/latest/securing_apps/index.html#_javascript_adapter)
- `frontend/.env.example` — Keycloak environment variables
- `docker/keycloak/realm-export.json` — Session lifetime config (`ssoSessionMaxLifespan: 36000`, `ssoSessionIdleTimeout: 1800`)

## Questions?

This fix prevents Keycloak from seeing the app's auth check as a "restart" by respecting existing sessions. For deeper diving:
- Keycloak logs: `docker compose logs hexagonal-scim-keycloak | grep "ERROR\|WARN"`
- App logs: `docker compose logs hexagonal-scim | grep "auth\|Auth"`

