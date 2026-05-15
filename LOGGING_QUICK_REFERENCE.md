# Quick Reference: Logging API Cheat Sheet

Keep this handy when coding!

---

## Frontend Logging

### 1. Import and Create Logger
```typescript
import { createLogger } from '../../logger/logger';

const logger = createLogger('MyComponentName');
```

### 2. Log Messages
```typescript
logger.info('User action', { userId: 123 });
logger.warn('Unusual but ok', { data: value });
logger.error('Something failed', { error: message });
logger.debug('Detailed info', { state: data });
```

### 3. Log HTTP Requests
```typescript
const start = performance.now();
logger.requestStart('GET', '/api/v1/payments');

try {
  const response = await fetch(...);
  const duration = Math.round(performance.now() - start);
  logger.requestEnd('GET', '/api/v1/payments', 200, duration);
} catch (e) {
  const duration = Math.round(performance.now() - start);
  logger.requestError('GET', '/api/v1/payments', 0, e.message);
}
```

### 4. Correlation IDs (for distributed tracing)
```typescript
import { setCorrelationId, generateCorrelationId } from '../../logger/logger';

// At session start:
setCorrelationId(generateCorrelationId());

// All logs include: "correlationId": "1715809512123-a7b3k2"
```

### 5. Parse API Response with Logging
```typescript
const response = await getJson<PagedPaymentResponse>(`/api/v1/data`);
logger.info('Data received', {
  total: response.totalElements,
  page: response.pageNumber,
  count: response.content.length,
});
return response.content;  // Extract the array
```

---

## Backend Logging

### 1. Create Logger
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyService {
    private static final Logger LOG = LoggerFactory.getLogger(MyService.class);
    
    // Use in methods...
}
```

### 2. Log Requests
```java
@GetMapping
public Response getAll(
    @RequestParam Integer page,
    @RequestParam Integer size
) {
    LOG.info("Fetching data: page={}, size={}", page, size);
    Response response = service.getAll(page, size);
    LOG.info("Data fetched: count={}, total={}", response.getCount(), response.getTotal());
    return response;
}
```

### 3. Log Errors (Info/Warn/Error)
```java
// Business rule violation (expected failure)
LOG.warn("Insufficient funds at {}: {}", uri, exception.getMessage());

// System failure (unexpected error)
LOG.error("Payment processing failed at {}: {}", uri, exception.getMessage());

// Validation error (expected rejection)
LOG.info("Validation failed at {}: {}", uri, details);
```

### 4. Log in Exception Handler
```java
@ExceptionHandler(SomeException.class)
public ErrorResponse handle(SomeException e, HttpServletRequest req) {
    LOG.warn("Error at {}: {}", req.getRequestURI(), e.getMessage());
    return new ErrorResponse("ERROR_CODE", e.getMessage(), req.getRequestURI());
}
```

---

## Log Levels

| Level | Frontend | Backend | Use Case |
|-------|----------|---------|----------|
| DEBUG | `debug()` | `LOG.debug()` | Detailed diagnostic info |
| INFO | `info()` | `LOG.info()` | Normal operation flow |
| WARN | `warn()` | `LOG.warn()` | Expected failures, business rules |
| ERROR | `error()` | `LOG.error()` | System failures, bugs |

---

## Expected Log Output

### Frontend (Browser Console)
```json
{"level":"info","timestamp":"2026-05-15T23:45:12Z","name":"HTTP","message":"GET request completed","url":"/api/v1/payments","status":200,"data":{"duration":"45ms"}}
```

### Backend (Docker logs)
```
2026-05-15 23:45:12,123 INFO  [com.example.user.api.payment.PaymentControllerAdapter] Fetching payments: page=0, size=10, pageable=true
```

---

## Troubleshooting

| Problem | Check |
|---------|-------|
| Can't see logs | Log level too high (set to INFO in config) |
| Too many logs | Log level too low (set to WARN in config) |
| No error context | Add data parameter: `logger.error('msg', { detail: val })` |
| Request not visible | Call `logger.requestStart()` before fetch |
| Type mismatch error | Check `PagedPaymentResponse` in types |
| 400 Bad Request | Check request parameters in logs |

---

## Common Patterns

### API Call with Error Handling
```typescript
async function fetchData() {
  logger.info('Fetching data');
  try {
    const { content } = await getJson<PagedPaymentResponse>('/api/v1/data');
    logger.info('Data loaded', { count: content.length });
    return content;
  } catch (error) {
    logger.error('Failed to fetch', { error: error.message });
    throw error;
  }
}
```

### Component Lifecycle
```typescript
class DataComponent extends HTMLElement {
  connectedCallback() {
    logger.info('Component mounted');
    this.loadData();
    this.render();
  }
  
  private async loadData() {
    try {
      logger.info('Loading data');
      this.data = await fetchData();
      logger.info('Data loaded', { count: this.data.length });
    } catch (error) {
      logger.error('Load failed', { error: error.message });
    }
  }
  
  disconnectedCallback() {
    logger.info('Component unmounted');
  }
}
```

### Request with Timing
```java
@PostMapping
public Response create(@RequestBody Request req) {
    long start = System.currentTimeMillis();
    LOG.info("Processing request: {}", req.getType());
    
    Response response = service.process(req);
    
    long duration = System.currentTimeMillis() - start;
    LOG.info("Request processed: duration={}ms, type={}", duration, response.getStatus());
    
    return response;
}
```

---

## Files Reference

### Frontend
- Logger: `frontend/src/logger/logger.ts`
- HTTP: `frontend/src/api/http.ts`
- API Example: `frontend/src/api/payments.ts`
- Component: `frontend/src/components/pages/payments-page.ts`

### Backend
- Controllers: `PaymentControllerAdapter.java`, `ProductControllerAdapter.java`
- Handlers: `PaymentApiExceptionHandler.java`
- Config: `application.yml` (logging section)

### Documentation
- Full Guide: `LOGGING_IMPLEMENTATION.md`
- Usage Guide: `LOGGING_USAGE_GUIDE.md`
- Troubleshooting: `PAYMENTS_ENDPOINT_TROUBLESHOOTING.md`

---

## View Logs

### Browser
```
F12 → Console tab
```

### Docker Backend
```bash
docker compose logs hexagonal-scim | grep -i "payment\|error"
```

### Follow Real-time
```bash
docker compose logs -f hexagonal-scim
```

### Find Errors
```bash
docker compose logs | grep -i "error\|exception"
```

---

## Quick Tips

✅ **DO**:
- Use `info()` for normal operations
- Use `warn()` for expected failures
- Use `error()` for actual bugs
- Include relevant data in logs
- Use correlation IDs for tracing
- Check browser console first when debugging

❌ **DON'T**:
- Log passwords or tokens
- Use `console.log()` directly
- Skip error logging
- Use string concatenation for data
- Log the same info twice
- Ignore error context

---

## Examples by Feature

### Fetch Payments
```typescript
const payments = await getJson<PagedPaymentResponse>('/api/v1/payments?page=0&size=10');
// Logs: request start → request end (with duration) → data parsed
```

### Create Payment
```typescript
await postJson('/api/v1/payments', paymentData);
// Logs: request start → validation → creation → success → request end
```

### Handle Error
```typescript
catch (error) {
  logger.error('Payment failed', { error: error.message });
  this.showError('Payment failed');
}
// Logs: error level with context message
```

### Pagination
```typescript
logger.info('Fetching page', { page, size, total });
// Logs show pagination metadata for debugging infinite scroll or pagination issues
```

---

## Status: Production Ready ✅

All logging infrastructure is in place and tested.
Start using it in your code today!

Questions? See `LOGGING_USAGE_GUIDE.md` or `LOGGING_IMPLEMENTATION.md`

