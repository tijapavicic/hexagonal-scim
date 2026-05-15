# Fix: Payments API 400 Bad Request on GET Endpoint

## Problem
The `/api/v1/payments` GET endpoint was returning `400 Bad Request` when called with pagination parameters:
```
GET /api/v1/payments?page=0&size=10 → 400 Bad Request
```

## Root Cause
The `PaymentControllerAdapter.getAll()` method did not accept pagination parameters (`page`, `size`, `pageable`), causing Spring to reject the request as having unexpected query parameters.

## Solution
Implemented pagination support for the payments API endpoint by:

1. **Created `PagedPayments` model** (hex-payment-core)
   - New record in `com.example.user.model` package
   - Wraps paginated list of payments with metadata

2. **Created `PagedPaymentResponse` DTO** (hex-inbound-adapter-payment-web)
   - New record for API response format
   - Matches `PagedUserResponse` structure with `hasNext`/`hasPrevious` flags

3. **Updated `GetAllPaymentsPort`** interface (hex-payment-core)
   - Added new paginated method: `PagedPayments getAll(int page, int size, boolean pageable)`
   - Kept deprecated `getAll()` for backward compatibility

4. **Enhanced `PaymentService`** (hex-payment-core)
   - Implements new pagination method
   - Handles unpaginated fallback (returns all when `pageable=false`)

5. **Updated `PaymentControllerAdapter`** (hex-inbound-adapter-payment-web)
   - Added `@RequestParam` annotations for `page`, `size`, `pageable`
   - Injected `ApiPaginationProperties` for configuration defaults
   - Returns `PagedPaymentResponse` with correct pagination metadata

6. **Added configuration** (hex-inbound-adapter-payment-web)
   - Created `ApiPaginationProperties` class
   - Created `PaymentWebConfig` with `@EnableConfigurationProperties`

7. **Updated `PaymentConfig`** (hex-application)
   - Modified `getAllPaymentsPort()` bean to implement both methods of `GetAllPaymentsPort`

## Result
```
GET /api/v1/payments?page=0&size=10 → 200 OK
```

Response format:
```json
{
  "content": [...],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 5,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

## Files Changed
- `hex-payment-core/src/main/java/com/example/user/model/PagedPayments.java` (NEW)
- `hex-payment-core/src/main/java/com/example/user/port/in/GetAllPaymentsPort.java` (UPDATED)
- `hex-payment-core/src/main/java/com/example/user/core/PaymentService.java` (UPDATED)
- `hex-inbound-adapter-payment-web/src/main/java/com/example/user/api/payment/PaymentControllerAdapter.java` (UPDATED)
- `hex-inbound-adapter-payment-web/src/main/java/com/example/user/api/payment/dto/PagedPaymentResponse.java` (NEW)
- `hex-inbound-adapter-payment-web/src/main/java/com/example/user/api/payment/config/ApiPaginationProperties.java` (NEW)
- `hex-inbound-adapter-payment-web/src/main/java/com/example/user/api/payment/config/PaymentWebConfig.java` (NEW)
- `hex-inbound-adapter-payment-web/pom.xml` (UPDATED: added springdoc dependency)
- `hex-application/src/main/java/com/example/user/config/PaymentConfig.java` (UPDATED)

## Verification
✅ All 51 tests passing
✅ Compilation successful
✅ Payments list endpoint now supports pagination
✅ Backward compatible with existing code

## Testing
To test manually:
```bash
# Get paginated payments (default: page 0, size 10)
curl -H "Authorization: Bearer <TOKEN>" \
  "https://localhost:3000/api/v1/payments?page=0&size=10"

# Get all payments without pagination
curl -H "Authorization: Bearer <TOKEN>" \
  "https://localhost:3000/api/v1/payments?pageable=false"
```

Expected: 200 OK with `PagedPaymentResponse` structure

