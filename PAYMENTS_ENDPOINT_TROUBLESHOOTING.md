# Payments Endpoint 400 Error - Troubleshooting Guide

**Quick Fix**: See the complete logging implementation details in `LOGGING_IMPLEMENTATION.md` and testing steps in `LOGGING_DELIVERY_SUMMARY.md`.

---

## Symptoms

- ❌ https://localhost:3000/#/payments shows error
- ❌ HTTP status: 400 Bad Request
- ❌ Payments table doesn't load
- ❌ No visible error message in browser

---

## Diagnosis Flowchart

### Step 1: Open Browser DevTools

```
F12 or Cmd+Option+I → Console tab
```

**Look for error logs**:
```json
{"level":"error","name":"HTTP","message":"GET request failed","status":400,"error":"..."}
```

### Step 2: Check Backend Logs

```bash
docker compose logs hexagonal-scim | grep -i "error\|warn"
```

**Look for**:
```
WARN  [PaymentControllerAdapter] Validation error at /api/v1/payments: ...
ERROR [PaymentApiExceptionHandler] ...
```

### Step 3: Verify Docker is Running

```bash
docker compose ps
```

**Should show** (all running):
```
hexagonal-scim         Up
hexagonal-scim-db      Up
hexagonal-scim-keycloak Up
```

---

## Common Causes & Solutions

### 1. Type Mismatch (Most Common)

**Symptom**: Frontend expects `PaymentDto[]`, backend returns `PagedPaymentResponse`

**Check**:
```typescript
// frontend/src/types/payment.dto.ts - should have:
export interface PagedPaymentResponse {
  content: PaymentDto[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

// frontend/src/api/payments.ts - should have:
export async function listPayments(page = 0, size = 10): Promise<PaymentDto[]> {
  const response = await getJson<PagedPaymentResponse>(`...`);
  return response.content;  // ← Extract content
}
```

**Fix**: If missing, see `LOGGING_IMPLEMENTATION.md` → Frontend Implementation → Type Definition Fix

### 2. Keycloak Token Expired

**Symptom**: 401 Unauthorized in logs

**Check**:
```bash
# Verify token in browser
localStorage.getItem('kc_token')  # Should not be empty
```

**Fix**:
```bash
# Clear cache and re-login
docker compose down -v
docker compose up --build
# Login fresh
```

### 3. Backend Not Responding

**Symptom**: Connection timeout or refused

**Check**:
```bash
curl -H "Authorization: Bearer <TOKEN>" https://localhost:8080/api/v1/payments
# Should return 200 with payments JSON
```

**Fix**:
```bash
# Rebuild backend
docker compose up --build hexagonal-scim

# Check it's running
docker compose logs hexagonal-scim | grep -i "started\|error"
```

### 4. Database Connection Failed

**Symptom**: Backend logs show SQL errors

**Check**:
```bash
docker compose logs hexagonal-scim-db | grep error
```

**Fix**:
```bash
# Reset database
docker compose down -v
docker compose up --build
```

### 5. Wrong Request Format

**Symptom**: Validation error in logs

**Check**:
```typescript
// These should be numbers, not strings
page: number,  // NOT string
size: number,  // NOT string
```

**Fix**: Ensure query parameters are numbers:
```typescript
const page = parseInt(pageStr, 10);  // Convert string to number
const size = parseInt(sizeStr, 10);
const response = await listPayments(page, size);
```

---

## Detailed Debugging Steps

### Enable Full Logging

**Backend** - Set DEBUG level in `application.yml`:
```yaml
logging:
  level:
    com.example.user.api: DEBUG  # Was INFO
```

**Restart and check logs**:
```bash
docker compose restart hexagonal-scim
docker compose logs -f hexagonal-scim
```

**You should see**:
```
DEBUG [PaymentControllerAdapter] ... detailed info ...
```

### View Complete Error

**Browser Console**:
```javascript
// Copy the entire log entry
// Paste into text editor to see full JSON
```

**Backend**:
```bash
docker compose logs hexagonal-scim | grep "400\|Bad\|invalid"
```

### Trace Request Flow

**Browser** → **Backend** → **Database**:

1. **Frontend sends**:
   ```javascript
   logger.info('Fetching payments', { page: 0, size: 10 });
   ```

2. **Backend receives**:
   ```
   INFO [PaymentControllerAdapter] Fetching payments: page=0, size=10, pageable=true
   ```

3. **Database query**:
   ```
   DEBUG [PaymentRepository] SELECT * FROM payments WHERE page=0 LIMIT 10
   ```

4. **Response back**:
   ```
   INFO [PaymentControllerAdapter] Payments fetched: totalElements=42, totalPages=5
   ```

5. **Frontend gets**:
   ```javascript
   logger.info('Payments fetched successfully', { count: 10 })
   ```

**If any step is missing**: Check logs at that stage

---

## Log Analysis Examples

### 400 Error - Type Mismatch

**Frontend Log**:
```json
{
  "level": "error",
  "name": "HTTP",
  "message": "GET request failed",
  "status": 400,
  "error": "{\"timestamp\":\"2026-05-15T23:45:12.123Z\",\"status\":400}"
}
```

**Solution**: Check type definitions (see Type Mismatch cause above)

### 401 Error - Token Expired

**Frontend Log**:
```json
{
  "level": "error",
  "name": "HTTP",
  "message": "GET request failed",
  "status": 401,
  "error": "Unauthorized"
}
```

**Solution**: User needs to log in again

### 500 Error - Backend Crash

**Backend Log**:
```
ERROR [PaymentControllerAdapter] Payment processing failed at /api/v1/payments: NullPointerException: ...
```

**Solution**: Check backend logs for stack trace, fix code issue

### Timeout - Database Slow

**Frontend Log** (takes >5 seconds):
```json
{
  "level": "warn",
  "data": { "duration": "5123ms" }
}
```

**Solution**: Database is slow, check `docker compose logs hexagonal-scim-db`

---

## Network Issues

### CORS Error

**Symptom**: Browser console shows CORS error

**Check**: Ensure localhost:3000 calls localhost:8080 with proper headers

**Fix**: Verify CORS config in backend Spring config

### HTTPS Certificate Error

**Symptom**: `ERR_CERT_AUTHORITY_INVALID`

**Check**:
```bash
ls docker/certs/server.crt
```

**Fix**: Regenerate certificates (see docker setup docs)

### Network Timeout

**Symptom**: Request hangs for >30s then fails

**Check**:
```bash
ping localhost:8080
curl https://localhost:8080/api/v1/health
```

**Fix**: Backend not responding - restart services:
```bash
docker compose restart hexagonal-scim
```

---

## Performance Issues

### Slow Pagination

**Symptom**: Page loads slowly, logs show duration >1000ms

**Check Server**:
```bash
docker compose logs hexagonal-scim | grep "duration\|took"
```

**Check Database**:
```bash
docker compose logs hexagonal-scim-db | grep "Query took"
```

**Fix**:
1. Add database indexes
2. Reduce page size (change size parameter)
3. Check database connection pool
4. Use simpler queries

---

## Verification Checklist

After implementing the fix, verify:

- [ ] Frontend builds without errors: `npm run build`
- [ ] Backend builds with all tests passing: `mvn verify`
- [ ] Docker services start: `docker compose up --build`
- [ ] Payments page loads: https://localhost:3000/#/payments (after login)
- [ ] HTTP status is 200 (not 400)
- [ ] Browser console shows successful logs
- [ ] Docker logs show backend processing logs
- [ ] Table displays payment data
- [ ] Pagination works (click next/prev)

---

## Getting Help

### Check Logs First

Priority order:
1. Browser DevTools → Console (Frontend logs)
2. `docker compose logs hexagonal-scim` (Backend logs)
3. `docker compose logs hexagonal-scim-db` (Database logs)
4. `docker compose logs hexagonal-scim-keycloak` (Auth logs)

### Review Documentation

1. `LOGGING_IMPLEMENTATION.md` - Architecture & implementation
2. `LOGGING_USAGE_GUIDE.md` - How to read & interpret logs
3. `LOGGING_DELIVERY_SUMMARY.md` - What was changed & why

### Try These Commands

```bash
# Full system restart
docker compose down -v && docker compose up --build

# Show all errors
docker compose logs | grep -i "error\|exception"

# Follow logs in real-time
docker compose logs -f hexagonal-scim

# Check services are running
docker compose ps

# Verify endpoints
curl -k https://localhost:8080/api/v1/health
```

---

## Quick Fixes (Copy-Paste)

### Fix 1: Rebuild Everything
```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
docker compose down -v
docker compose up --build
# Wait 30s for services to start
# Open https://localhost:3000
# Login: test@example.com / Test123!
# Click Payments
```

### Fix 2: Clear Frontend Cache
```bash
# Browser DevTools → Application → Clear storage
# Or: Cmd+Shift+Delete (Chrome)
# Then reload page
```

### Fix 3: Restart Backend Only
```bash
docker compose restart hexagonal-scim
# Wait 5s
# Try payments page again
```

### Fix 4: Check Token
```javascript
// In browser console:
console.log(localStorage.getItem('kc_token'));
// Should be non-empty JWT

// If empty - user not logged in
// Click Login button
```

---

## Success Indicators

✅ **Success**: You should see:

1. **Browser**: Payments page loads with table of payments
2. **Status**: HTTP 200 (shown in Network tab)
3. **Console**: Logs showing request started → request completed
4. **Server**: Docker logs showing "Payments fetched: totalElements=X"
5. **Table**: 10-30 payments visible with pagination

❌ **Failure**: Stop and check logs if:

1. Page shows error message
2. HTTP status is 400, 401, 500
3. Console shows red error logs
4. Table is empty (but no error)
5. Request hangs >10 seconds

---

## Support Resources

- `frontend/src/logger/logger.ts` - Logging framework
- `frontend/src/api/http.ts` - HTTP client with logging
- `dockerfile` - Application build config
- `docker-compose.yml` - Service orchestration
- `application.yml` - Backend configuration

**Need to add more logging?** See `LOGGING_USAGE_GUIDE.md` → Frontend Logging / Backend Logging sections.

