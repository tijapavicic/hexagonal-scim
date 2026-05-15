# Manual Testing Guide

Complete instructions for manually testing the hexagonal-scim application using the provided test data.

## Overview

This guide provides step-by-step instructions for testing key features of the hexagonal-scim application, including user management, product browsing, account management, and payment flows. All test data is seeded automatically via the database migration `V10__add_test_data_for_manual_testing.sql`.

---

## Prerequisites

### Local Environment Setup

Ensure you have the following running:

1. **Docker Compose** with all services started:
   ```bash
   cd /Users/copor/IdeaProjects/hexagonal-scim
   docker compose up --build
   ```

   This starts:
   - PostgreSQL (port 5432, local data volume `postgres_data`)
   - Keycloak (https://localhost:8443, realm: `hexagonal-scim`)
   - Spring Boot Backend (http://localhost:8080, internal only)
   - React Frontend (https://localhost:3000)
   - Nginx (port 3000, proxies /api/* to backend)

2. **Postman or curl** for API testing (optional but recommended)

3. **pgAdmin or psql** for database inspection (optional)

---

## Test User Credentials

### Primary Test User

| Field | Value |
|-------|-------|
| **Email** | `test@example.com` |
| **Password** | `Test123!` |
| **Display Name** | Test User |
| **Login Method** | Keycloak OAuth2 |

### Sample Users (Pre-seeded)

The database comes with 25 sample users for testing list/search functionality:
- `alice@example.com` (Alice Johnson)
- `bob@example.com` (Bob Smith)
- `charlie@example.com` (Charlie Brown)
- ... and 22 more (see `V3__add_sample_users.sql`)

---

## Test Data Inventory

### Users
- **Test User**: `test@example.com` (main test account)
- **Sample Users**: 25 additional users for list/pagination testing

### Products (6 total)
1. **BaseCatHouse** - €79.99 (100 in stock)
2. **Premium CatHouse Deluxe** - €149.99 (50 in stock)
3. **Cat Playground Structure** - €199.99 (25 in stock)
4. **Digital Art License - Basic** - €29.99 (1000 in stock)
5. **Digital Art License - Premium** - €99.99 (500 in stock)
6. **Art Gallery Collection Pass** - €249.00 (100 in stock)

### Accounts (Test User)
1. **Personal Checking** - Balance: €5,000.00
2. **Savings Account** - Balance: €15,000.00
3. **Art Fund** - Balance: €2,500.00

### Payments (Various statuses)
- **PENDING**: BaseCatHouse (test user)
- **COMPLETED**: Premium CatHouse Deluxe (test user)
- **FAILED**: Digital Art License - Basic (test user)
- **REFUNDED**: Cat Playground Structure x2 (test user)
- **CANCELLED**: Digital Art License - Premium (test user)

---

## Test Scenarios

### Scenario 1: Frontend Login & Dashboard

**Goals**: Verify authentication and homepage rendering.

**Steps**:

1. Navigate to **https://localhost:3000** in your browser.
2. Click any area to trigger login.
3. Verify redirect to **Keycloak login page** (https://localhost:8443).
4. Enter credentials:
   - Username: `test@example.com`
   - Password: `Test123!`
5. After successful login, verify:
   - ✅ Redirect back to https://localhost:3000
   - ✅ Dashboard displays "Welcome, Test User"
   - ✅ Quick stats cards load (Users count, Payments count)
   - ✅ Navigation pills visible (#/home, #/users, #/payments)

**Expected Results**:
- Frontend correctly integrates Keycloak authentication
- User profile loaded from JWT token
- API calls to `/api/v1/users` and `/api/v1/payments` succeed
- No CORS errors in browser console

---

### Scenario 2: Users Page - List & Pagination

**Goals**: Test user list retrieval and pagination.

**Steps**:

1. From dashboard, click **Users** pill or navigate to **https://localhost:3000#/users**.
2. Verify user table loads with:
   - ✅ Column headers: ID, Email, Display Name, Actions
   - ✅ Default page size (10 users)
   - ✅ Pagination controls visible
3. Test pagination:
   - Click **Next** button to load page 2 (users 11-20).
   - Click **Previous** to go back to page 1.
   - Change page size dropdown (10 → 25 → 50) and verify table updates.
4. Verify `test@example.com` appears in the list.

**Expected API Calls**:
```bash
# Default list (page 0, size 10)
curl -H "Authorization: Bearer <TOKEN>" \
  "https://localhost:3000/api/v1/users?page=0&size=10"

# Response: PagedUserResponse with 10 users, totalElements: 26 (25 sample + test)
```

**Expected Results**:
- Pagination works correctly
- All 26 users eventually load across pages
- Table performance remains smooth with large datasets

---

### Scenario 3: Users Page - Create User

**Goals**: Test user creation via the UI (if Create button is visible to admin roles).

**Steps**:

1. From the Users page, locate the **Create User** button (admin-only).
2. Click to open the Create User modal.
3. Fill in:
   - Email: `newuser@example.com`
   - Display Name: `New Test User`
4. Click **Create**.
5. Verify:
   - ✅ Modal closes
   - ✅ Success notification appears
   - ✅ New user appears in the table (may be on last page)

**Expected API Call**:
```bash
curl -X POST "https://localhost:3000/api/v1/users" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newuser@example.com",
    "displayName": "New Test User"
  }'

# Response: 201 Created with UserResponse
```

**Expected Results**:
- User successfully created
- Email must be unique (409 Conflict if duplicate)
- Input validation enforced (email format, required fields)

---

### Scenario 4: Users Page - Update User

**Goals**: Test full update (PUT) functionality.

**Steps**:

1. From the Users page, find `test@example.com`.
2. Click the user row or the **Edit** action icon.
3. Modal opens with current data pre-filled.
4. Change display name to `Test User - Updated`.
5. Change email to `test-updated@example.com`.
6. Click **Save**.
7. Verify:
   - ✅ Modal closes
   - ✅ Table refreshes with new data
   - ✅ Success notification

**Expected API Call**:
```bash
curl -X PUT "https://localhost:3000/api/v1/users/{userId}" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test-updated@example.com",
    "displayName": "Test User - Updated"
  }'

# Response: 200 OK with UserResponse
```

**Expected Results**:
- User fields updated successfully
- Email uniqueness constraint enforced
- Old email can no longer be used for login (until Keycloak is synced)

---

### Scenario 5: Users Page - Delete User

**Goals**: Test delete functionality.

**Steps**:

1. Create a temporary user (e.g., `tempuser@example.com`).
2. Find the user in the list.
3. Click **Delete** action icon.
4. Confirm deletion in the modal.
5. Verify:
   - ✅ Modal closes
   - ✅ User removed from table
   - ✅ Success notification

**Expected API Call**:
```bash
curl -X DELETE "https://localhost:3000/api/v1/users/{userId}" \
  -H "Authorization: Bearer <TOKEN>"

# Response: 204 No Content
```

**Expected Results**:
- User permanently deleted
- Related accounts & payments cascaded (if foreign key cascades configured)
- 404 when attempting to GET deleted user

---

### Scenario 6: Direct API Testing (curl / Postman)

**Goals**: Verify backend APIs work independently of frontend.

#### 6.1 List Users

```bash
curl -X GET "http://localhost:8080/api/v1/users?page=0&size=10" \
  -H "Authorization: Bearer <JWT_TOKEN>"

# Expected Response (200 OK):
{
  "content": [
    { "id": 1, "email": "alice@example.com", "displayName": "Alice Johnson" },
    ...
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 26,
  "totalPages": 3,
  "hasNext": true,
  "hasPrevious": false
}
```

#### 6.2 Get Single User

```bash
curl -X GET "http://localhost:8080/api/v1/users/1" \
  -H "Authorization: Bearer <JWT_TOKEN>"

# Expected Response (200 OK):
{
  "id": 1,
  "email": "alice@example.com",
  "displayName": "Alice Johnson"
}
```

#### 6.3 Create User

```bash
curl -X POST "http://localhost:8080/api/v1/users" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "createtest@example.com",
    "displayName": "Create Test"
  }'

# Expected Response (201 Created):
{
  "id": 27,
  "email": "createtest@example.com",
  "displayName": "Create Test"
}
```

#### 6.4 Update User

```bash
curl -X PUT "http://localhost:8080/api/v1/users/1" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice-updated@example.com",
    "displayName": "Alice Johnson Updated"
  }'

# Expected Response (200 OK):
{
  "id": 1,
  "email": "alice-updated@example.com",
  "displayName": "Alice Johnson Updated"
}
```

#### 6.5 Partial Update (PATCH)

```bash
curl -X PATCH "http://localhost:8080/api/v1/users/1" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Alice - Patched"
  }'

# Expected Response (200 OK):
{
  "id": 1,
  "email": "alice@example.com",        # unchanged
  "displayName": "Alice - Patched"     # updated
}
```

#### 6.6 Delete User

```bash
curl -X DELETE "http://localhost:8080/api/v1/users/27" \
  -H "Authorization: Bearer <JWT_TOKEN>"

# Expected Response (204 No Content):
# (empty body)
```

---

### Scenario 7: Error Handling & Validation

**Goals**: Verify error responses and input validation.

#### 7.1 Invalid Email Format

```bash
curl -X POST "http://localhost:8080/api/v1/users" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "not-an-email",
    "displayName": "Bad Email Test"
  }'

# Expected Response (400 Bad Request):
{
  "code": "VALIDATION_ERROR",
  "message": "Email must be a valid email address"
}
```

#### 7.2 Duplicate Email

```bash
curl -X POST "http://localhost:8080/api/v1/users" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "displayName": "Duplicate Test"
  }'

# Expected Response (409 Conflict):
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email 'alice@example.com' is already registered"
}
```

#### 7.3 Missing Required Fields

```bash
curl -X POST "http://localhost:8080/api/v1/users" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "onlyemail@example.com"
  }'

# Expected Response (400 Bad Request):
{
  "code": "VALIDATION_ERROR",
  "message": "displayName is required"
}
```

#### 7.4 User Not Found

```bash
curl -X GET "http://localhost:8080/api/v1/users/99999" \
  -H "Authorization: Bearer <JWT_TOKEN>"

# Expected Response (404 Not Found):
{
  "code": "USER_NOT_FOUND",
  "message": "User with ID 99999 not found"
}
```

---

### Scenario 8: Database Inspection

**Goals**: Verify test data is correctly seeded in the database.

#### Using psql (Command Line)

```bash
# Connect to PostgreSQL (runs in Docker)
psql -h localhost -U scim -d hexagonal_scim

# Check users table
SELECT id, email, display_name FROM users ORDER BY id LIMIT 10;

# Expected output:
#  id |        email         |    display_name
# ----+---------------------+-------------------
#   1 | alice@example.com    | Alice Johnson
#   2 | bob@example.com      | Bob Smith
#  ... 
#  26 | test@example.com     | Test User

# Check products table
SELECT id, name, price, currency, stock_quantity FROM products;

# Check accounts table
SELECT a.id, u.email, a.name, a.balance
FROM accounts a
JOIN users u ON a.user_id = u.id
WHERE LOWER(u.email) = LOWER('test@example.com');

# Expected output:
#  id | email              |         name          | balance
# ----+--------------------+-----------------------+---------
#  1  | test@example.com   | Personal Checking     | 5000.00
#  2  | test@example.com   | Savings Account       | 15000.00
#  3  | test@example.com   | Art Fund              | 2500.00

# Check payments table
SELECT p.id, pr.name, p.quantity, p.total_amount, p.status, p.payment_method
FROM payments p
JOIN products pr ON p.product_id = pr.id
ORDER BY p.id;
```

#### Using pgAdmin (Web UI)

1. Open pgAdmin (if enabled in docker-compose.override.yml).
2. Connect to PostgreSQL:
   - Host: `localhost`
   - Port: `5432`
   - Username: `scim`
   - Password: `scim`
3. Navigate to `hexagonal_scim` database.
4. Inspect tables: `users`, `products`, `accounts`, `payments`.

---

## Authentication & Authorization

### Getting a JWT Token (for API Testing)

If testing APIs directly with curl/Postman and need a JWT token:

#### 1. Keycloak Token Endpoint

```bash
curl -X POST "https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token" \
  -k \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=hexagonal-scim-public" \
  -d "grant_type=password" \
  -d "username=test@example.com" \
  -d "password=Test123!"

# Note: -k skips SSL certificate verification (development only)
# Expected Response:
{
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "token_type": "Bearer",
  ...
}
```

#### 2. Use Token in Subsequent Requests

```bash
JWT_TOKEN="eyJ0eXAiOiJKV1QiLCJhbGc..."

curl -X GET "http://localhost:8080/api/v1/users?page=0&size=10" \
  -H "Authorization: Bearer ${JWT_TOKEN}"
```

---

## Troubleshooting

### Issue: "Connection Refused" on localhost:8080 or localhost:3000

**Solution**: Ensure Docker containers are running:
```bash
docker compose ps

# Should show:
# hexagonal-scim-db       Up (healthy)
# hexagonal-scim-keycloak Up (healthy)
# hexagonal-scim-app      Up (healthy)
# hexagonal-scim-frontend Up (healthy)
```

If containers are down:
```bash
docker compose up -d
```

---

### Issue: SSL Certificate Errors in Browser

**Cause**: Self-signed certificate used for local HTTPS.

**Solution**: 
1. In Firefox: Click "Accept the Risk and Continue"
2. In Chrome: Click "Proceed to localhost (unsafe)"
3. Trust the certificate permanently (see below)

---

### Issue: "Invalid Redirect URI" Error in Keycloak

**Solution**: Check Keycloak realm configuration:
1. Access Keycloak Admin Console: https://localhost:8443 (admin / admin)
2. Realm: `hexagonal-scim`
3. Clients: `hexagonal-scim-public`
4. Valid Redirect URIs: Ensure `https://localhost:3000/*` is listed
5. Web Origins: Ensure `https://localhost:3000` is listed

---

### Issue: Test Data Not Appearing in Database

**Cause**: Migration file `V10__add_test_data_for_manual_testing.sql` failed to execute.

**Solution**:
1. Check Flyway migration status in logs:
   ```bash
   docker compose logs app | grep -i "flyway\|migration"
   ```
2. If a migration failed, manually inspect the database:
   ```bash
   psql -h localhost -U scim -d hexagonal_scim
   SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
   ```
3. If V10 is missing, the file may not have been picked up. Ensure:
   - File is in `/hex-application/src/main/resources/db/migration/`
   - File name follows naming convention: `V{number}__{description}.sql`
   - Rebuild and restart: `docker compose down && docker compose up --build`

---

### Issue: 401 Unauthorized on API Calls

**Cause**: JWT token expired or invalid.

**Solution**:
1. Get a fresh token from Keycloak (see Authentication section).
2. Ensure token is included as `Authorization: Bearer <TOKEN>` header.
3. Check token expiry (default 5 minutes):
   ```bash
   # Decode JWT (various online tools available, or use cli tool)
   # Look for "exp" field (Unix timestamp)
   ```

---

## Performance Testing

### Test High Pagination Loads

```bash
# Test with large page size
curl -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8080/api/v1/users?page=0&size=100"

# Should handle gracefully (max capped at 100 in UI, or per API rules)
```

### Test Concurrent User Fetches

```bash
# Using Apache Bench
ab -n 100 -c 10 -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8080/api/v1/users?page=0&size=10"

# Measures response time, throughput, and error rate under load
```

---

## Checklist for Complete Manual Testing

- [ ] **Authentication**: Login via Keycloak with `test@example.com`
- [ ] **Dashboard**: Homepage loads, stats cards fetch data
- [ ] **Users List**: All 26 users appear, pagination works
- [ ] **Users Create**: Can create new user (if admin role)
- [ ] **Users Update**: Can update existing user (full PUT)
- [ ] **Users Patch**: Can partially update user (PATCH)
- [ ] **Users Delete**: Can delete user (if admin role)
- [ ] **Error Handling**: Invalid email, duplicate email, missing fields all reject properly
- [ ] **API Testing**: Direct curl/Postman calls succeed with valid token
- [ ] **Database**: psql confirms test data seeded correctly
- [ ] **Permissions**: User roles enforce admin-only actions (create/edit/delete)
- [ ] **Performance**: No slowdowns with large page sizes or concurrent requests

---

## Next Steps

After completing manual testing:

1. **Document Findings**: Note any bugs, missing features, or improvements.
2. **Create Issues**: File GitHub issues for defects found.
3. **Run Automated Tests**: Execute integration tests:
   ```bash
   mvn -B clean verify
   ```
4. **Security Scan** (if required):
   ```bash
   mvn -B org.owasp:dependency-check-maven:check
   ```
5. **Code Review**: Use test results to guide code review priorities.

---

## Additional Resources

- **Swagger UI**: http://localhost:8080/swagger-ui.html (backend API docs)
- **Keycloak Admin**: https://localhost:8443 (admin / admin)
- **PostgreSQL**: localhost:5432 (scim / scim)
- **Postman Collection**: `/postman/hexagonal-scim-v2.postman_collection.json`
- **Development Guide**: `development-instructions.md`
- **Testing Guide**: `TESTING.md`

