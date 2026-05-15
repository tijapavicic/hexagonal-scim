# Logging Usage Guide

Quick reference for developers on how to use structured logging in this project.

---

## Frontend Logging

### 1. Import the Logger

```typescript
import { createLogger } from '../../logger/logger';

const logger = createLogger('MyModuleName');
```

### 2. Log Different Level Messages

```typescript
// Info - normal operation
logger.info('User logged in', { userId: 123, email: 'user@example.com' });

// Debug - detailed diagnostic info (set LEVEL=DEBUG to see)
logger.debug('Generated token', { expiresIn: 3600 });

// Warn - unexpected but recoverable condition
logger.warn('Network timeout, retrying', { attempt: 1, maxAttempts: 3 });

// Error - operation failed
logger.error('Payment processing failed', { 
  error: 'Insufficient funds',
  amount: 99.99,
  available: 50.00
});
```

### 3. Log HTTP Requests

```typescript
// For complete visibility into HTTP calls:
const startTime = performance.now();
logger.requestStart('GET', '/api/v1/payments');

try {
  const response = await fetch('/api/v1/payments');
  const duration = Math.round(performance.now() - startTime);
  logger.requestEnd('GET', '/api/v1/payments', response.status, duration);
} catch (error) {
  const duration = Math.round(performance.now() - startTime);
  logger.requestError('GET', '/api/v1/payments', 0, error.message);
}
```

### 4. Track Related Operations with Correlation IDs

```typescript
import { setCorrelationId, generateCorrelationId } from '../logger/logger';

// At start of user session or request handling:
const correlationId = generateCorrelationId();
setCorrelationId(correlationId);

// All subsequent logs include this ID automatically
logger.info('User action', { action: 'payment_created' });
// Output includes: "correlationId": "1715809512123-a7b3k2"
```

### 5. Log Component Lifecycle

```typescript
class MyComponentElement extends HTMLElement {
  connectedCallback(): void {
    logger.info('Component mounted', { componentName: 'PaymentsPage' });
    this.loadData();
  }

  async loadData(): Promise<void> {
    logger.info('Loading data', { source: '/api/v1/payments' });
    try {
      const data = await fetchData();
      logger.info('Data loaded', { itemCount: data.length });
    } catch (error) {
      logger.error('Failed to load data', { 
        error: error instanceof Error ? error.message : String(error)
      });
    }
  }

  disconnectedCallback(): void {
    logger.info('Component unmounted');
  }
}
```

### 6. Log API Call Results

```typescript
export async function fetchPayments(page: number, size: number) {
  logger.info('Fetching payments', { page, size });
  
  const response = await getJson<PagedPaymentResponse>(
    `/api/v1/payments?page=${page}&size=${size}`
  );
  
  logger.info('Payments fetched', {
    count: response.content.length,
    total: response.totalElements,
    pages: response.totalPages,
    hasNext: response.hasNext,
  });
  
  return response.content;
}
```

---

## Backend Logging

### 1. Create a Logger in Java

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentService {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);
    
    // Use it in methods...
}
```

### 2. Log Request Parameters

```java
@GetMapping
public PagedPaymentResponse getAll(
    @RequestParam(name = "page", required = false) Integer page,
    @RequestParam(name = "size", required = false) Integer size
) {
    int effectivePage = page != null ? page : 0;
    int effectiveSize = size != null ? size : 10;
    
    LOG.info("Fetching payments: page={}, size={}", 
            effectivePage, effectiveSize);
    
    // ... business logic ...
    
    return response;
}
```

### 3. Log Response Metadata

```java
PagedPayments result = getAllPaymentsPort.getAll(page, size, pageable);

LOG.info("Payments fetched: totalElements={}, totalPages={}, currentPage={}, itemCount={}",
    result.totalElements(), 
    result.totalPages(), 
    result.pageNumber(), 
    result.content().size()
);

return new PagedPaymentResponse(...);
```

### 4. Log Errors in Exception Handlers

```java
@ExceptionHandler(IllegalArgumentException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ErrorResponse handleValidationError(
    IllegalArgumentException ex, 
    HttpServletRequest req
) {
    LOG.warn("Invalid request at {}: {}", 
        req.getRequestURI(), 
        ex.getMessage()
    );
    
    return new ErrorResponse("VALIDATION_ERROR", ex.getMessage(), req.getRequestURI());
}
```

### 5. Log Business Logic Warnings

```java
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public ErrorResponse handleInsufficientFunds(
    InsufficientFundsException ex, 
    HttpServletRequest req
) {
    // Use WARN for business rule violations (expected failures)
    LOG.warn("Insufficient funds at {}: {}", 
        req.getRequestURI(), 
        ex.getMessage()
    );
    
    return new ErrorResponse("INSUFFICIENT_FUNDS", ex.getMessage(), req.getRequestURI());
}
```

### 6. Log Processing Errors

```java
@ExceptionHandler(PaymentProcessingException.class)
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public ErrorResponse handleProcessing(
    PaymentProcessingException ex, 
    HttpServletRequest req
) {
    // Use ERROR for actual system failures (unexpected errors)
    LOG.error("Payment processing failed at {}: {}", 
        req.getRequestURI(), 
        ex.getMessage()
    );
    
    return new ErrorResponse("PAYMENT_PROCESSING_FAILED", ex.getMessage(), req.getRequestURI());
}
```

---

## Log Levels Guide

### Backend (SLF4J/Logback)

| Level | Use Case | Example | Output |
|-------|----------|---------|--------|
| `DEBUG` | Detailed diagnostic info | Token claims, computation steps | Usually disabled in prod |
| `INFO` | Normal operation | Request start/end, business actions | Always visible |
| `WARN` | Expected failures | Validation errors, business rules | Always visible, highlighted |
| `ERROR` | System failures | Exceptions, processing errors | Always visible, urgent |

### Frontend (Custom Logger)

| Level | Use Case | Example | Output |
|-------|----------|---------|--------|
| `debug()` | Detailed state/values | Component state, computed values | JSON on console |
| `info()` | Normal operation | Page load, API call, user action | JSON on console |
| `warn()` | Unusual condition | Retries, degraded mode | JSON on console, yellow |
| `error()` | Operation failure | fetch failed, validation failed | JSON on console, red |

---

## Configuration

### Backend (application.yml)

```yaml
logging:
  level:
    root: WARN                      # Suppress noisy frameworks
    com.example.user: INFO          # Application logic
    com.example.user.api: DEBUG     # API layer detailed logging
    org.springframework.security: INFO
  pattern:
    console: "%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

**Change log levels without restarting** (if using Actuator):
```bash
curl -X POST http://localhost:8080/actuator/loggers/com.example.user.api \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"TRACE"}'
```

### Frontend

Logging is always enabled. View in browser DevTools → Console.

**Filter by module**:
```javascript
// Show only HTTP logs
console.log = (() => {
  const orig = console.log;
  return (...args) => {
    if (args[0]?.includes('HTTP')) orig(...args);
  };
})();
```

---

## Examples

### Full Request Flow with Logging

**User navigates to Payments page**:

1. **Component mounts** (Frontend)
   ```
   [INFO] PaymentsPage - Component mounted
   ```

2. **Request starts** (Frontend HTTP)
   ```
   [INFO] HTTP - GET request started: /api/v1/payments?page=0&size=10
   ```

3. **Backend receives** (Backend Controller)
   ```
   [INFO] PaymentControllerAdapter - Fetching payments: page=0, size=10, pageable=true
   ```

4. **Response built** (Backend)
   ```
   [INFO] PaymentControllerAdapter - Payments fetched: totalElements=42, totalPages=5, currentPage=0, itemCount=10
   ```

5. **Response received** (Frontend HTTP)
   ```
   [INFO] HTTP - GET request completed: /api/v1/payments?page=0&size=10 - status: 200 - 45ms
   ```

6. **Data processed** (Frontend API)
   ```
   [INFO] PaymentsAPI - Payments fetched successfully
   {
     "count": 10,
     "totalElements": 42,
     "pageNumber": 0,
     "totalPages": 5
   }
   ```

7. **Component renders** (Frontend)
   ```
   [INFO] PaymentsPage - Payments loaded successfully
   {
     "count": 10,
     "page": 0,
     "hasMore": true
   }
   ```

---

## Observability Integration

### Prometheus Metrics (Already configured)

Backend logs are integrated with Prometheus metrics:
```bash
# View metrics
curl http://localhost:8080/actuator/prometheus | grep http_server_requests
```

### Grep Logs

```bash
# Filter for errors
docker compose logs hexagonal-scim | grep ERROR

# Filter by module
docker compose logs hexagonal-scim | grep PaymentControllerAdapter

# Follow in real-time
docker compose logs -f hexagonal-scim | grep "Fetching\|ERROR"
```

### Cloud Logging Integration

**Frontend logs** are structured JSON compatible with:
- ELK Stack (Elasticsearch, Logstash, Kibana)
- Splunk (already configured in docker-compose.yml)
- CloudWatch, Stackdriver, etc.

**Example Kibana filter**:
```
name: "PaymentsAPI" AND level: "error" AND status: 400
```

---

## Best Practices

✅ **DO**:
- Include request IDs/correlation IDs for tracing
- Log request parameters to understand what failed
- Use structured data (objects), not string concatenation
- Use appropriate log levels (INFO for normal, WARN for expected failures, ERROR for bugs)
- Log before operations that might fail

❌ **DON'T**:
- Log passwords, tokens, or sensitive data
- Log the same information twice
- Use string concatenation for structured data
- Catch exceptions without logging them
- Leave `console.log()` in production code (use logger instead)

---

## Debugging with Logs

### Find a Payment Processing Error

```bash
# Backend
docker compose logs hexagonal-scim | grep -i "insufficient\|failed\|error"

# Output shows:
# WARN at /api/v1/payments: Insufficient funds for payment

# See what parameters caused it
docker compose logs hexagonal-scim | grep -B5 "Insufficient funds"
```

### Trace a User Request

```bash
# Set correlation ID (in frontend code)
# All logs from that user will include that ID

# View all logs for that user
docker compose logs hexagonal-scim | grep "abcd1234"
```

### Find Slow Requests

```javascript
// In browser console
// Logs include "duration" for HTTP requests
// Filter for duration > 1000ms
```

---

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Can't see any logs | Check log level in application.yml (should be INFO or DEBUG) |
| Too many logs | Increase root log level to WARN in application.yml |
| Missing error details | Ensure exception handler logs at WARN/ERROR level |
| Correlation ID not appearing | Call `setCorrelationId()` before operations |
| JSON not parsing | Logs are valid JSON - test in `tool jq` if needed |
| Request not logged | Ensure you call `logger.requestStart()` and `logger.requestEnd()` |


