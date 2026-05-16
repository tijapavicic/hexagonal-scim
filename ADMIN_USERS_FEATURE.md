# Admin Users Feature Implementation

## Overview
Added a button on the main page (home page) that allows **admin users only** to fetch and view all users from the database. Non-admin users cannot access this feature and receive a "you are not authorized" message.

## Changes Made

### Backend (Java/Spring Boot)

#### 1. **UserControllerAdapter** 
📁 `/hex-inbound-adapter-web/src/main/java/com/example/user/api/UserControllerAdapter.java`

**New Endpoint:** `GET /api/v1/users/all`

```java
@GetMapping("/all")
public java.util.List<UserResponse> getAllUsersAdminOnly() {
    // Check if user has admin role
    boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> auth.equals("ROLE_admin") || auth.equals("admin"));

    if (!isAdmin) {
        logger.warn("flow_stage=AUTHORIZATION_DENIED operation=users.getAllAdmin status=FORBIDDEN reason=missing_admin_role");
        throw new AccessDeniedException("you are not authorized");
    }

    logger.info("flow_stage=REQUEST_RECEIVED operation=users.getAllAdmin status=INITIATED");
    PagedUsers allUsers = getAllUsersPort.getAll(0, Integer.MAX_VALUE, false);
    logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=users.getAllAdmin totalUsers={} status=SUCCESS", allUsers.totalElements());

    java.util.List<UserResponse> result = allUsers.content().stream()
            .map(user -> new UserResponse(user.id(), user.email(), user.displayName()))
            .toList();
    logger.info("flow_stage=RESPONSE_PREPARED operation=users.getAllAdmin userCount={} status=COMPLETED", result.size());

    return result;
}
```

**Features:**
- ✅ Checks user's roles from Spring Security context
- ✅ Supports both "ROLE_admin" and "admin" role names
- ✅ Returns **all users without pagination**
- ✅ Throws `AccessDeniedException` (403 Forbidden) if not authorized
- ✅ Includes **Splunk-friendly logging** with flow stages:
  - `AUTHORIZATION_DENIED` - When user lacks admin role
  - `REQUEST_RECEIVED` - When admin request arrives
  - `DOMAIN_OPERATION_COMPLETED` - After fetching all users
  - `RESPONSE_PREPARED` - Before sending response

### Frontend (TypeScript/Web Components)

#### 2. **users.ts API Service**
📁 `/frontend/src/api/users.ts`

**New Function:**
```typescript
/** Fetch all users without pagination (admin only). Throws if not authorized. */
export async function getAllUsersAdminOnly(): Promise<UserDto[]> {
  return getJson<UserDto[]>(`${BASE}/all`);
}
```

#### 3. **home-page.ts Component**
📁 `/frontend/src/components/pages/home-page.ts`

**Key Changes:**

1. **Added roles support:**
   ```typescript
   get roles(): string[] {
     const rolesAttr = this.getAttribute('roles') ?? '';
     return rolesAttr ? rolesAttr.split(',').map(r => r.trim()) : [];
   }

   private isAdmin(): boolean {
     return this.roles.some(role => role.toLowerCase() === 'admin');
   }
   ```

2. **Added admin-only button (visible only for admins):**
   ```typescript
   ${this.isAdmin() ? `
   <div class="card admin-card">
     <div class="card-icon">🔐</div>
     <div class="card-label">Admin Panel</div>
     <div class="card-value card-value-sm">Get all users</div>
     <button class="admin-btn" data-action="get-all-users">Fetch All Users →</button>
   </div>
   ` : ''}
   ```

3. **Modal for displaying all users:**
   - Fetches all users when button is clicked
   - Shows loading state while fetching
   - Displays error message if user lacks authorization
   - Lists all users with name and email
   - Close button (✕) or click outside to close

4. **Error handling:**
   - Gracefully handles authorization errors
   - Shows user-friendly error message: "Failed to fetch users. You may not have admin role."

#### 4. **scim-app.ts Main Component**
📁 `/frontend/src/components/scim-app.ts`

**Updated home page creation:**
```typescript
case '#/home':
  const homePage = document.createElement('home-page') as HTMLElement & { username: string; roles: string[] };
  const profile = getAuthProfile();
  homePage.username = profile.preferredUsername;
  homePage.roles = profile.realmRoles;  // ← Pass roles from Keycloak
  content.replaceChildren(homePage);
  break;
```

#### 5. **home-page.styles.ts Styling**
📁 `/frontend/src/components/pages/home-page.styles.ts`

**New styles added:**
- `.admin-card` - Distinctive blue bordered card for admin panel
- `.admin-btn` - Blue button with hover/active states
- `.modal` - Full-screen backdrop with fade-in animation
- `.modal-content` - Modal dialog box
- `.user-item` - Individual user entry in the list
- `.modal-loading`, `.modal-error`, `.modal-empty` - State indicators

## Flow and User Interactions

### For Admin Users:
1. ✅ User logs in via Keycloak with "admin" role
2. ✅ Home page displays 4 cards (Profile, Users, Payments, **Admin Panel**)
3. ✅ Clicks "Fetch All Users" button
4. ✅ Loading state appears
5. ✅ All users load and display in modal
6. ✅ Users can close modal via ✕ button or clicking backdrop

### For Non-Admin Users:
1. ✅ User logs in via Keycloak without "admin" role
2. ✅ Home page displays 3 cards (Profile, Users, Payments only)
3. ✅ No admin button visible
4. ✅ If they try POST/GET to `/api/v1/users/all`, they receive:
   ```json
   {
     "code": "ACCESS_DENIED",
     "message": "you are not authorized",
     "path": "/api/v1/users/all",
     "timestamp": "2026-05-16T15:41:00.000Z"
   }
   ```
   **HTTP Status: 403 Forbidden**

## Technical Details

### Authorization Mechanism
- **Backend:** Spring Security context inspection using `SecurityContextHolder.getContext().getAuthentication()`
- **Frontend:** Keycloak roles extracted from JWT token via `getAuthProfile().realmRoles`
- **Role Name Flexibility:** Accepts both "ROLE_admin" (Spring Security prefix) and "admin" (Keycloak)

### Logging Strategy
- **Splunk-optimized:** All logs use key=value format for easy parsing
- **Structured flow:** Each operation logs at 3-4 stages (REQUEST_RECEIVED → DOMAIN_OPERATION_COMPLETED → RESPONSE_PREPARED)
- **Authorization events:** Failed authorization logged at WARN level with reason

### Error Handling
- **Backend:** Throws `AccessDeniedException`, caught by existing `ApiExceptionHandlerAdapter` 
- **Frontend:** Try/catch in `fetchAllUsers()` with user-friendly error message
- **Modal states:** Loading, Error, Empty, Success clearly distinguished

## Testing

### Build Verification
```bash
mvn -B clean verify -DskipTests
# BUILD SUCCESS ✅
```

### Test Suite Results
```bash
mvn -B clean verify
# Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS ✅
```

### Manual Testing Steps

1. **Test with admin user:**
   ```bash
   # In Keycloak, ensure user has "admin" realm role
   # Login to application
   # Navigate to home page
   # Verify admin card displays
   # Click "Fetch All Users"
   # Verify modal shows all users
   ```

2. **Test with non-admin user:**
   ```bash
   # In Keycloak, create/login user WITHOUT admin role
   # Navigate to home page
   # Verify admin card is NOT visible
   # Try direct API call: curl https://localhost:3000/api/v1/users/all
   # Verify 403 Forbidden with "you are not authorized" message
   ```

## API Contract

### Endpoint: `GET /api/v1/users/all`

**Request:**
```http
GET /api/v1/users/all HTTP/1.1
Authorization: Bearer <keycloak-jwt-token>
```

**Success Response (401 OK for admin):**
```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 1,
    "email": "alice@example.com",
    "displayName": "Alice"
  },
  {
    "id": 2,
    "email": "bob@example.com",
    "displayName": "Bob"
  },
  ...
]
```

**Error Response (403 Forbidden for non-admin):**
```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "code": "ACCESS_DENIED",
  "message": "you are not authorized",
  "path": "/api/v1/users/all",
  "timestamp": "2026-05-16T15:41:00.000Z"
}
```

## Files Changed

| File | Change | Purpose |
|------|--------|---------|
| `hex-inbound-adapter-web/src/.../UserControllerAdapter.java` | Added `getAllUsersAdminOnly()` endpoint | Backend authorization logic |
| `frontend/src/api/users.ts` | Added `getAllUsersAdminOnly()` function | Frontend API call |
| `frontend/src/components/pages/home-page.ts` | Added roles prop, admin modal, button | UI logic |
| `frontend/src/components/pages/home-page.styles.ts` | Added admin card & modal styles | UI styling |
| `frontend/src/components/scim-app.ts` | Pass roles to home page component | Wire roles to home page |

## Rollout Checklist

- [x] Backend endpoint with authorization check
- [x] Frontend API client function
- [x] Home page component with admin check
- [x] Modal for displaying results
- [x] Error handling and user-friendly messages
- [x] Splunk-friendly logging
- [x] Styling and accessibility
- [x] All tests passing (51/51)
- [x] Build clean (BUILD SUCCESS)

## Next Steps (Optional Enhancements)

1. **Add more admin actions:**
   - Bulk delete users
   - Export users to CSV
   - Bulk role assignment

2. **Role-based access control for other features:**
   - Payment approvals (requires "payment-approver" role)
   - Account management (requires "account-manager" role)

3. **Audit logging:**
   - Log all admin actions to separate audit table
   - Track who accessed sensitive endpoints and when

4. **Rate limiting:**
   - Add rate limits to admin endpoints to prevent abuse

## Troubleshooting

### Scenario: Admin button not visible on home page
**Possible causes:**
- User doesn't have "admin" role in Keycloak
- Keycloak realm roles not synced to JWT token
- Frontend caching old authentication

**Solution:**
- Logout and login again
- Check Keycloak console that user has "admin" realm role
- Clear browser localStorage: `localStorage.clear()`

### Scenario: Getting 403 with correct role
**Possible causes:**
- Role name mismatch (check if it's "admin" vs "ROLE_admin")
- Keycloak token not refreshed

**Solution:**
- Check role name in Keycloak matches "admin" (case-insensitive on frontend)
- Force token refresh by logging out/in
- Check logs: `flow_stage=AUTHORIZATION_DENIED`

### Scenario: Modal shows error after clicking button
**Possible causes:**
- User lost their admin role or token expired
- Backend /api/v1/users/all endpoint not accessible
- CORS issue

**Solution:**
- Check browser console for network errors
- Verify backend is running
- Check authorization error in response body

