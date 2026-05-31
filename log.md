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

1. **Start the application:**
```bash
cd hex-application
mvn spring-boot:run
```

2. **Test with invalid UUID (original issue):**
```bash
curl -X GET "http://localhost:8080/api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64"
```

**Expected Response:**
```json
{
  "code": "INVALID_PARAMETER_TYPE",
  "message": "Invalid value 'a59ba86d-5c3c-42f3-b15c-a54e623fbb64' for parameter 'id': expected Long",
  "path": "/api/v1/users/a59ba86d-5c3c-42f3-b15c-a54e623fbb64",
  "timestamp": "2026-05-31T11:35:00.123456Z"
}
```

3. **Test with other invalid types:**
```bash
# Non-numeric string
curl -X GET "http://localhost:8080/api/v1/users/not-a-number"

# Floating point when integer expected
curl -X GET "http://localhost:8080/api/v1/users/3.14"

# Way too large number (overflow)
curl -X GET "http://localhost:8080/api/v1/users/99999999999999999999"
```

All should return `400 BAD REQUEST` with `INVALID_PARAMETER_TYPE` error code.

4. **Valid request (for comparison):**
```bash
curl -X GET "http://localhost:8080/api/v1/users/1"
```

**Expected Result:**
- Status: `200 OK`
- User data returned

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


