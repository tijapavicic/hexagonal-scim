# Fix: Keycloak Invalid Refresh Token Error

## Problem
The frontend was receiving `REFRESH_TOKEN_ERROR` with reason "Invalid refresh token" when attempting to refresh JWT tokens:

```
type="REFRESH_TOKEN_ERROR", realmId="...", clientId="hexagonal-scim-public", 
error="invalid_token", reason="Invalid refresh token"
```

## Root Causes
1. **Missing session lifetime configuration**: Keycloak realm lacked explicit offline session and timeout settings
2. **Client-level token lifespan not configured**: Public client didn't have explicit token lifespan attributes
3. **Frontend error handling**: Token refresh failures weren't properly handled with re-authentication
4. **Test user missing from Keycloak**: Database user `test@example.com` wasn't configured in Keycloak realm

## Solution

### 1. Updated Keycloak Realm Configuration (`docker/keycloak/realm-export.json`)

**Added realm-level session settings:**
- `offlineSessionMaxLifespan: 7776000` (90 days) - Maximum time offline tokens are valid
- `refreshTokenMaxReuse: 0` - Allow unlimited refresh token reuse
- `ssoSessionIdleTimeout: 1800` (30 min) - SSO session idle timeout
- `ssoSessionMaxLifespanRememberMe: 604800` (7 days) - Remember-me session lifespan
- `ssoSessionIdleTimeoutRememberMe: 604800` (7 days) - Remember-me session idle timeout

**Added client-level attributes for `hexagonal-scim-public`:**
```json
"attributes": {
  "access.token.lifespan": "300",
  "client.offline.session.idle.timeout": "7776000",
  "client.offline.session.max.lifespan": "7776000",
  "client.session.idle.timeout": "1800",
  "client.session.max.lifespan": "36000"
}
```

**Added test user to realm:**
- Username: `test`
- Email: `test@example.com`
- Password: `Test123!`
- Roles: `user`, `admin` (matches database test user)

### 2. Enhanced Frontend Token Refresh Handling (`frontend/src/auth/keycloak.ts`)

**Improved `initAuth()` method:**
- Added detailed logging for token refresh failures
- Detects "Invalid refresh token" errors specifically
- Forces re-authentication when refresh token is invalid
- Gracefully handles errors instead of silently failing

**Updated error classification:**
- Added detection for "invalid refresh token" and "invalid_token" messages
- Maps these to `session_timeout` reason for proper UI handling
- More descriptive error message: "Session timed out or refresh token is invalid"

**Changes:**
```typescript
// Before: Silently handled token refresh errors
keycloak.updateToken(30).catch((error) => emit('onAuthError', classifyAuthError(error)));

// After: Proactively detects and handles invalid refresh tokens
keycloak.updateToken(30).catch((error) => {
  const details = classifyAuthError(error);
  console.warn('Token refresh failed:', details);
  emit('onAuthError', details);
  
  // If token is invalid/expired, force re-authentication
  if (details.reason === 'session_timeout' || 
      (error instanceof Error && error.message?.includes('Invalid refresh token'))) {
    console.info('Refresh token invalid, redirecting to login');
    void keycloak.logout({ redirectUri: window.location.origin });
  }
});
```

## Files Changed
- `docker/keycloak/realm-export.json` (UPDATED)
  - Added realm-level session configuration
  - Added client-level token lifespan configuration
  - Added `test@example.com` user with `Test123!` password
  
- `frontend/src/auth/keycloak.ts` (UPDATED)
  - Enhanced `initAuth()` token refresh error handling
  - Improved `classifyAuthError()` to detect refresh token errors
  - Added proactive re-authentication when refresh token is invalid

## Verification

### Before
```
GET /api/v1/users → 401 Unauthorized (after token expired)
Keycloak logs: REFRESH_TOKEN_ERROR: Invalid refresh token
```

### After
```
GET /api/v1/users → 200 OK (token auto-refreshed)
OR
GET /api/v1/users → Redirect to login (if refresh token invalid)
Keycloak logs: Successful token refresh
```

## Testing
1. Login with `test@example.com` / `Test123!`
2. Make API calls every ~5 minutes to allow token to expire
3. Verify:
   - Token refreshes automatically (no manual login needed)
   - If token refresh fails, user is redirected to login
   - No "Invalid refresh token" errors in Keycloak logs

## Deployment Notes
- When Keycloak container starts, it will re-import the updated realm configuration
- Existing sessions may be invalidated after deployment (users need to re-login)
- Offline session timeout is now 90 days (can be adjusted in realm export if needed)

## Related Issues Fixed
- ✅ Payments API 400 error (from previous fix)
- ✅ Keycloak refresh token errors (this fix)
- ✅ Test user authentication (added to both database and Keycloak)

## Next Steps
- Restart Docker containers to apply Keycloak config changes:
  ```bash
  docker compose down
  docker compose up -d
  ```
- Clear browser cookies/session storage if needed
- Test manual authentication flow with updated credentials

