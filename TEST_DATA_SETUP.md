# Test Data Setup - Quick Reference

## What Was Added

### 1. **Database Migration** (`V10__add_test_data_for_manual_testing.sql`)
   - **Location**: `hex-application/src/main/resources/db/migration/`
   - **Includes**:
     - 1 dedicated test user: `test@example.com` (password: `Test123!`)
     - 6 products (cat houses, digital art licenses, gallery pass)
     - 3 accounts linked to test user with sample balances
     - 5 payments with different statuses (PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED)

### 2. **Manual Testing Guide** (`MANUAL_TESTING.md`)
   - **Location**: Project root directory
   - **Scope**: Comprehensive step-by-step instructions for:
     - Frontend login & dashboard testing
     - User CRUD operations (list, create, update, delete)
     - Pagination and filtering
     - Error handling & validation
     - Direct API testing with curl/Postman
     - Database inspection with psql/pgAdmin
     - Performance testing
     - Complete testing checklist

### 3. **Updated Integration Tests**
   - **File**: `HexagonalScimApplicationTest.java`
   - **Changes**: Updated test assertions to expect 26 users total (25 sample + 1 test user)
   - **Status**: ✅ All tests passing

---

## Quick Start

### Run Docker Compose
```bash
cd /Users/copor/IdeaProjects/hexagonal-scim
docker compose up --build
```

### Access the Application
- **Frontend**: https://localhost:3000
- **Backend API**: http://localhost:8080 (direct from host, via LAN)
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Keycloak Admin**: https://localhost:8443 (admin / admin)
- **PostgreSQL**: localhost:5432 (scim / scim)

### Login Credentials
```
Email:    test@example.com
Password: Test123!
```

---

## Test Data at a Glance

### Test User
- **Email**: test@example.com
- **Password**: Test123!
- **Accounts**: 3 (Personal, Savings, Art Fund)
- **Balance**: €22,500 total across accounts

### Sample Products
| Product | Price | Stock |
|---------|-------|-------|
| BaseCatHouse | €79.99 | 100 |
| Premium CatHouse Deluxe | €149.99 | 50 |
| Cat Playground Structure | €199.99 | 25 |
| Digital Art License - Basic | €29.99 | 1000 |
| Digital Art License - Premium | €99.99 | 500 |
| Art Gallery Collection Pass | €249.00 | 100 |

### Payment Samples
| Product | Quantity | Status | Method |
|---------|----------|--------|--------|
| BaseCatHouse | 1 | PENDING | Credit Card |
| Premium CatHouse | 1 | COMPLETED | Credit Card |
| Digital Art Basic | 1 | FAILED | Bank Transfer |
| Cat Playground | 2 | REFUNDED | PayPal |
| Digital Art Premium | 1 | CANCELLED | Credit Card |

---

## File Summary

| File | Purpose | Status |
|------|---------|--------|
| `V10__add_test_data_for_manual_testing.sql` | Database seeding | ✅ Created |
| `MANUAL_TESTING.md` | Comprehensive testing guide | ✅ Created |
| `HexagonalScimApplicationTest.java` | Updated test assertions | ✅ Updated |

---

## Verification

### Maven Build Status
```
✅ Tests run: 51, Failures: 0, Errors: 0
✅ BUILD SUCCESS
```

### Migration Applied
```
Migrating schema "public" to version "10 - add test data for manual testing"
Successfully applied 10 migrations to schema "public", now at version v10
```

---

## Next Steps

1. **Run Docker**: `docker compose up --build`
2. **Open Manual Testing Guide**: Read `MANUAL_TESTING.md` for detailed scenarios
3. **Login**: Use `test@example.com` / `Test123!`
4. **Follow Test Scenarios**: Work through scenarios 1-8 in the guide
5. **Validate**: Verify test data is loaded in database using psql

---

## Notes

- Test data is **idempotent**: Migration uses `WHERE NOT EXISTS` clauses to avoid duplicates if re-run
- All test data is **isolated** to the V10 migration file for easy rollback/cleanup
- **No sensitive data** hardcoded (passwords managed via Keycloak)
- **Production-safe**: Test data only seeded in development environments

---

## Troubleshooting

### "Test data not appearing"
```bash
# Check migration logs
docker compose logs app | grep -i migration

# Verify in database
psql -h localhost -U scim -d hexagonal_scim
SELECT COUNT(*) FROM users;  -- Should show 26
SELECT COUNT(*) FROM payments;  -- Should show 5
```

### "401 Unauthorized" on API calls
- Fresh token needed (expires every 5 minutes)
- See "Authentication & Authorization" section in `MANUAL_TESTING.md`

### "Connection refused"
- Verify Docker services are running: `docker compose ps`
- Rebuild if needed: `docker compose down && docker compose up --build`

---

## Documentation References

- Main testing guide: `MANUAL_TESTING.md` (comprehensive, all scenarios)
- Development guide: `development-instructions.md`
- Testing guide: `TESTING.md`
- Architecture docs: `documenttaion/`


