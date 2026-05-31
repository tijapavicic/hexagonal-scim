# Frontend Keycloak Integration Guide

## Overview

The frontend has been updated to support Keycloak UUID-based user lookups, matching the backend implementation.

---

## What Changed

### 1. User DTOs Updated

**File:** `src/types/user.dto.ts`

```typescript
export interface UserDto {
  id: number;
  email: string;
  displayName: string;
  keycloakId?: string;  // Optional: populated after OAuth2 login
}
```

**Changes:**
- Removed: `username`, `firstName`, `lastName`, `active`
- Added: `keycloakId` (optional, matches backend)
- Simplified to match backend `UserResponse` structure

---

### 2. New API Method

**File:** `src/api/users.ts`

```typescript
/**
 * Fetch a single user by Keycloak UUID (external OAuth2 identity).
 */
export async function getUserByKeycloakId(keycloakId: string): Promise<UserDto> {
  return getJson<UserDto>(`/api/v1/users/by-keycloak-id/${keycloakId}`);
}
```

**Usage:**
```typescript
import { getUserByKeycloakId } from '@/api/users';

const user = await getUserByKeycloakId('a59ba86d-5c3c-42f3-b15c-a54e623fbb64');
console.log('User:', user.email, user.displayName);
```

---

### 3. Enhanced Keycloak Auth

**File:** `src/auth/keycloak.ts`

**New fields in AuthProfile:**
```typescript
export interface AuthProfile {
  keycloakId: string | null;  // Keycloak user UUID from 'sub' claim
  preferredUsername: string;
  email: string | null;  // User email from JWT
  realmRoles: string[];
  tokenExpiresAt: string | null;
}
```

**New helper function:**
```typescript
/**
 * Get the Keycloak user UUID (sub claim) from the current JWT token.
 */
export function getKeycloakUserId(): string | null {
  const parsed = keycloak.tokenParsed as { sub?: string } | undefined;
  return parsed?.sub ?? null;
}
```

**Usage:**
```typescript
import { getKeycloakUserId, getAuthProfile } from '@/auth/keycloak';

const keycloakId = getKeycloakUserId();
console.log('Keycloak user ID:', keycloakId);

const profile = getAuthProfile();
console.log('Full profile:', profile);
// {
//   keycloakId: "a59ba86d-5c3c-42f3-b15c-a54e623fbb64",
//   preferredUsername: "john@example.com",
//   email: "john@example.com",
//   realmRoles: ["user", "seller"],
//   tokenExpiresAt: "2026-05-31T14:30:00.000Z"
// }
```

---

### 4. Current User Helper

**File:** `src/api/current-user.ts` (NEW)

Convenience functions for working with the authenticated user:

```typescript
import { getCurrentUser, currentUserExists } from '@/api/current-user';

// Get current user data from backend
try {
  const user = await getCurrentUser();
  console.log('Logged in as:', user.email);
} catch (error) {
  console.error('User not found or not authenticated');
}

// Check if user exists in backend
const exists = await currentUserExists();
if (!exists) {
  console.log('User needs to be created in backend');
}
```

---

## Usage Patterns

### Pattern 1: Get Current User on Component Mount

```typescript
import { useEffect, useState } from 'react';
import { getCurrentUser } from '@/api/current-user';
import type { UserDto } from '@/types/user.dto';

export function UserProfile() {
  const [user, setUser] = useState<UserDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCurrentUser()
      .then(setUser)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!user) return <div>Not authenticated</div>;

  return (
    <div>
      <h1>{user.displayName}</h1>
      <p>{user.email}</p>
      <small>ID: {user.id}</small>
    </div>
  );
}
```

---

### Pattern 2: Create User on First Login

```typescript
import { getCurrentUser, currentUserExists } from '@/api/current-user';
import { createUser } from '@/api/users';
import { getAuthProfile } from '@/auth/keycloak';

async function ensureUserExists() {
  const exists = await currentUserExists();
  
  if (!exists) {
    const profile = getAuthProfile();
    
    // Create user in backend with Keycloak data
    const newUser = await createUser({
      email: profile.email ?? profile.preferredUsername,
      displayName: profile.preferredUsername,
    });
    
    console.log('Created new user:', newUser);
    return newUser;
  }
  
  return await getCurrentUser();
}

// Use in app initialization
ensureUserExists()
  .then(user => console.log('User ready:', user))
  .catch(err => console.error('Failed to ensure user:', err));
```

---

### Pattern 3: Conditional Rendering Based on User Existence

```typescript
import { useEffect, useState } from 'react';
import { currentUserExists } from '@/api/current-user';

export function ConditionalFeature() {
  const [userExists, setUserExists] = useState<boolean | null>(null);

  useEffect(() => {
    currentUserExists().then(setUserExists);
  }, []);

  if (userExists === null) {
    return <div>Checking user status...</div>;
  }

  if (!userExists) {
    return (
      <div>
        <h2>Welcome! Complete your profile</h2>
        <p>Your account needs to be set up...</p>
      </div>
    );
  }

  return (
    <div>
      <h2>Welcome back!</h2>
      {/* Normal app features */}
    </div>
  );
}
```

---

### Pattern 4: Display User Info from Keycloak Token

```typescript
import { getAuthProfile } from '@/auth/keycloak';

export function UserBadge() {
  const profile = getAuthProfile();

  return (
    <div className="user-badge">
      <span>{profile.preferredUsername}</span>
      <span>{profile.email}</span>
      {profile.realmRoles.includes('seller') && (
        <span className="badge">Seller</span>
      )}
      {profile.realmRoles.includes('admin') && (
        <span className="badge">Admin</span>
      )}
      <small>Token expires: {profile.tokenExpiresAt}</small>
    </div>
  );
}
```

---

## API Endpoints

### Option 1: Lookup by Numeric ID
```typescript
import { getUserById } from '@/api/users';

const user = await getUserById(1);
```

### Option 2: Lookup by Keycloak UUID
```typescript
import { getUserByKeycloakId } from '@/api/users';

const user = await getUserByKeycloakId('a59ba86d-5c3c-42f3-b15c-a54e623fbb64');
```

### Option 3: Get Current User (Recommended)
```typescript
import { getCurrentUser } from '@/api/current-user';

const user = await getCurrentUser();  // Uses Keycloak ID from JWT
```

---

## Error Handling

### User Not Found (404)
```typescript
try {
  const user = await getCurrentUser();
} catch (error) {
  if (error instanceof Error && error.message.includes('not found')) {
    // User needs to be created in backend
    console.log('First time login - create user');
  }
}
```

### Not Authenticated
```typescript
import { getKeycloakUserId } from '@/auth/keycloak';

const keycloakId = getKeycloakUserId();
if (!keycloakId) {
  console.error('User not authenticated');
  // Redirect to login
}
```

### Network/API Errors
```typescript
try {
  const user = await getCurrentUser();
} catch (error) {
  console.error('API error:', error);
  // Show error message to user
}
```

---

## Testing

### Mock Keycloak in Tests

```typescript
import { vi } from 'vitest';
import * as keycloak from '@/auth/keycloak';

vi.spyOn(keycloak, 'getKeycloakUserId').mockReturnValue('test-uuid-123');
vi.spyOn(keycloak, 'getAuthProfile').mockReturnValue({
  keycloakId: 'test-uuid-123',
  preferredUsername: 'testuser',
  email: 'test@example.com',
  realmRoles: ['user'],
  tokenExpiresAt: null,
});
```

### Mock API Calls

```typescript
import { vi } from 'vitest';
import * as usersApi from '@/api/users';

vi.spyOn(usersApi, 'getUserByKeycloakId').mockResolvedValue({
  id: 1,
  email: 'test@example.com',
  displayName: 'Test User',
  keycloakId: 'test-uuid-123',
});
```

---

## Migration Guide

### If you were using old UserDto fields:

**Before:**
```typescript
const username = user.username;
const fullName = `${user.firstName} ${user.lastName}`;
const isActive = user.active;
```

**After:**
```typescript
const username = user.email;  // Or get from Keycloak profile
const fullName = user.displayName;
// 'active' field no longer exists; use backend status if needed
```

---

## Next Steps

1. ✅ Update components using old UserDto structure
2. ✅ Use `getCurrentUser()` for fetching authenticated user
3. ✅ Implement "create user on first login" flow
4. ✅ Test with real Keycloak instance
5. ✅ Add error boundaries for API failures

---

## Backend Integration

The frontend changes align with the backend implementation documented in:
- `KEYCLOAK_UUID_SUPPORT_COMPLETE.md`
- `OAUTH2_KEYCLOAK_INTEGRATION.md`

**Backend Endpoints:**
- `GET /api/v1/users/{id}` - Lookup by numeric ID
- `GET /api/v1/users/by-keycloak-id/{keycloakId}` - Lookup by Keycloak UUID
- `GET /api/v1/users/all` - List all users (admin only)

---

## Files Changed

| File | Change |
|------|--------|
| `src/types/user.dto.ts` | Updated to match backend structure |
| `src/api/users.ts` | Added `getUserByKeycloakId()` |
| `src/auth/keycloak.ts` | Added `keycloakId` and `email` to profile |
| `src/api/current-user.ts` | NEW: Helper for current user operations |

---

## Status

✅ **Frontend aligned with backend Keycloak UUID support**

All code is ready for OAuth2-authenticated user lookups!

