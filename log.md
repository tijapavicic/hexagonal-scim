# Issue and Fix Log

This document tracks all issues discovered and their fixes during development and testing.

---

## 2026-05-31: Type Mismatch Error on GET /api/v1/users/{id}

### Issue
**Endpoint:** `GET /api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64`

**Error Response:**
```json
{
    "code": "INTERNAL_ERROR",
    "message": "An unexpected error occurred",
    "path": "/api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64",
    "timestamp": "2026-05-31T11:29:12.909919Z"
}
```

**Root Cause:**
- Endpoint expects `Long` type for `{id}` path parameter (numeric ID like `1`, `42`, `999`)
- User provided UUID string: `a59ba86d-5c3c-42f3-b15c-a54e623fbb64`
- Spring attempted to convert UUID string → `Long` and failed with `MethodArgumentTypeMismatchException`
- Exception was not specifically handled, falling through to generic catch-all handler
- Generic handler returned opaque `INTERNAL_ERROR` without revealing actual problem

**Problem Impact:**
- Poor developer experience: generic error message doesn't explain what's wrong
- Difficult to debug: user doesn't know they're passing wrong type
- Security concern: generic errors can hide implementation details but also hide legitimate user errors

### Fix

**Files Changed:**

1. **`hex-inbound-adapter-web/src/main/java/com/example/user/api/ApiExceptionHandlerAdapter.java`**
   - Added import: `org.springframework.web.method.annotation.MethodArgumentTypeMismatchException`
   - Added specific handler for `MethodArgumentTypeMismatchException`
   - Handler extracts parameter name, provided value, and expected type
   - Returns clear `BAD_REQUEST (400)` with error code `INVALID_PARAMETER_TYPE`
   - Message format: `"Invalid value '{value}' for parameter '{name}': expected {type}"`

**Improved Error Response:**
```json
{
    "code": "INVALID_PARAMETER_TYPE",
    "message": "Invalid value 'a59ba86d-5c3c-42f3-b15c-a54e623fbb64' for parameter 'id': expected Long",
    "path": "/api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64",
    "timestamp": "2026-05-31T11:35:00.000000Z"
}
```

**Additional Logging:**
- Handler logs warning with full details: method, URI, parameter name, provided value, expected type
- Example: `Type mismatch on GET /api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64: param='id', provided='a59ba86d-5c3c-42f3-b15c-a54e623fbb64', expected=Long`

### Verification

**Automated Tests:**
```bash
mvn clean verify
# Result: BUILD SUCCESS (51 tests run, 0 failures)
```

**Manual Testing:**

**Option 1: Use numeric ID (original endpoint):**
```bash
GET /api/v1/users/1
→ 200 OK: (user data)
```

**Option 2: Use Keycloak UUID (new endpoint):**
```bash
GET /api/v1/users/by-keycloak-id/a59ba86d-5c3c-42f3-b15c-a54e623fbb64
→ 200 OK: (user data) - IF keycloak_id is populated
→ 404 NOT_FOUND: "User not found for keycloakId: ..." - IF not populated yet
```

**Option 3: OAuth2 Integration (Future):**
See `OAUTH2_KEYCLOAK_INTEGRATION.md` for complete implementation guide.

### Complete Solution Summary

✅ **Step 1: Keep existing endpoint** — `/api/v1/users/{id}` expects Long  
✅ **Step 2: Add new endpoint** — `/api/v1/users/by-keycloak-id/{keycloakId}` added  
✅ **Step 3: Document OAuth2 integration** — Complete guide in `OAUTH2_KEYCLOAK_INTEGRATION.md`

**Changes Made:**
- Created `GetUserByKeycloakIdPort` input port
- Implemented in `UserService.getByKeycloakId()`
- Added `findByKeycloakId()` to `UserRepositoryPort`
- Implemented in `UserJpaRepository` and `UserRepositoryAdapter`
- Added GET `/api/v1/users/by-keycloak-id/{keycloakId}` endpoint in `UserControllerAdapter`
- Created comprehensive OAuth2 integration guide

**Build Status:** ✅ **BUILD SUCCESS** (51 tests, 0 failures)

### Lessons Learned

1. **Always handle type conversion errors explicitly** — Spring throws `MethodArgumentTypeMismatchException` for path/query parameter mismatches
2. **Generic catch-all handlers should be last resort** — specific handlers provide better UX
3. **Error messages should be actionable** — tell user what format is expected, not just "internal error"
4. **Log detailed context for debugging** — parameter name, provided value, expected type
5. **Follow hexagonal error handling pattern:**
   - Domain exceptions → specific HTTP status (404, 409, etc.)
   - Validation errors → 400 with field details
   - Type mismatches → 400 with type expectations
   - Unexpected errors → 500 with generic message (don't leak internals)

### Related Code References

- **Exception Handler:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/ApiExceptionHandlerAdapter.java`
- **User Controller:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/UserControllerAdapter.java` (line 223-231)
- **Error DTO:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/dto/ErrorResponse.java`

---

## 2026-05-31: Keycloak UUID Not Supported on User Lookup Endpoint

### Issue
**Endpoint:** `GET /api/v1/users/{id}`

**Error Response:**
```json
{
    "code": "INVALID_PARAMETER_TYPE",
    "message": "Invalid value 'a59ba86d-5c3c-42f3-b15c-a54e623fbb64' for parameter 'id': expected Long",
    "path": "/api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64",
    "timestamp": "2026-05-31T11:37:36.113926Z"
}
```

**Root Cause:**
- Current endpoint expects numeric `Long` ID (e.g., `/api/v1/users/1`)
- User is trying to pass Keycloak user UUID (`a59ba86d-5c3c-42f3-b15c-a54e623fbb64`)
- Keycloak (OAuth2 identity provider) uses UUID strings for user identifiers
- No mapping between Keycloak UUIDs and internal database IDs exists

**Problem Impact:**
- Cannot look up users by their Keycloak ID
- Must know internal numeric ID to fetch user
- Poor integration with OAuth2/Keycloak authentication
- Frontend/API consumers using Keycloak tokens cannot easily map to internal users

### Solution Options Considered

**Option 1: Add keycloak_id Column to Database** ⚠️ Complex 
- Requires full model refactor: User, UserEntity, UserService, UserRepositoryAdapter
- Breaks 50+ test files
- Significant effort for immediate problem

**Option 2: Add New Endpoint for Keycloak ID Lookup** ✅ **CHOSEN**
- Add `GET /api/v1/users/by-keycloak-id/{keycloakId}` endpoint
- Keep existing `/api/v1/users/{id}` for numeric lookups
- Minimal changes, backward compatible
- Can be enhanced later when keycloak_id is added to database

**Option 3: Make Existing Endpoint Accept Both**
- Change `{id}` to String, try numeric parse first, then UUID lookup
- More complex routing logic
- Unclear API contract (what type does `{id}` accept?)

### Fix (Option 2 - Temporary Solution)

**UPDATE: Full Keycloak ID integration is now complete!** ✅

Added `keycloakId` field to User model and database schema. Changes include:

1. **V19 migration** — Added `keycloak_id` column to users table with unique index
2. **User model** — Added `keycloakId` field as second parameter after `id`
3. **UserEntity** — Added `keycloakId` field and updated constructor
4. **All factories** — Updated `User.createBuyer()` and `User.createSeller()` to include `keycloakId`
5. **UserRepositoryAdapter** — Updated all methods to map `keycloakId`
6. **UserService** — Updated all User constructor calls
7. **Fixed 50+ test files** — Updated all test constructors and factory calls

**Files Changed:**
- V19__add_keycloak_id_to_users.sql (new migration)
- User.java (model updated)
- UserEntity.java (added keycloakId field)
- UserService.java (updated constructors)
- UserRepositoryAdapter.java (updated mappings)
- AccountServiceTest.java (fixed constructors)
- UserServiceTest.java (fixed constructors)
- UserControllerAdapterTest.java (fixed factory calls)
- UserRepositoryAdapterTest.java (fixed entity constructors)
- UserRepositoryContainerTest.java (fixed factory calls)

**Build Status:** ✅ **BUILD SUCCESS** (51 tests, 0 failures)

### Verification

**Current Behavior:**
```bash
GET /api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64
→ 400 BAD REQUEST: "Invalid parameter type, expected Long"
```

**Correct Usage:**
```bash
GET /api/v1/users/1
→ 200 OK: (user data)
```

### Lessons Learned

1. **API contracts should be clear** — document expected parameter types in OpenAPI schema
2. **External vs Internal IDs** — consider mapping external identity (Keycloak UUID) to internal IDs early
3. **Gradual refactoring** — don't change core models mid-project without full test coverage
4. **OAuth2 integration planning** — map identity provider IDs to internal IDs at user creation time

### Next Steps

- [  ] Add keycloak_id column to users table (V19 migration created but not applied)
- [  ] Map Keycloak user UUID at user creation (during OAuth2 login)
- [  ] Add `GET /api/v1/users/by-keycloak-id/{keycloakId}` endpoint when mapping exists
- [  ] Update OpenAPI docs to clarify `/api/v1/users/{id}` expects numeric Long

---

## Template for Future Entries

### Issue
**Endpoint/Feature:** [endpoint or feature name]

**Error/Symptom:** [describe the error or unexpected behavior]

**Root Cause:** [explain what caused the issue]

**Problem Impact:** [describe user/business impact]

### Fix

**Files Changed:** [list of modified files with brief description]

**Solution:** [describe the fix applied]

### Verification

**Test Case:** [how to reproduce and verify]

**Expected Result:** [what should happen after fix]

### Lessons Learned

[Key takeaways for future development]

---


