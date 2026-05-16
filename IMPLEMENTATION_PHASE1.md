# 🏗️ Phase 1 Implementation Guide - Backend Development

**Status:** Ready to Implement  
**Target Duration:** 2 Weeks (70 hours total)  
**Database Migrations:** ✅ V11-V18 Created  
**Prerequisites:** ✅ All verified  

---

## 📋 BACKEND IMPLEMENTATION SCHEDULE

```
Week 1 (Days 1-5): 13 hours
├── Day 1-2: Database & Core Models (5h)
├── Day 2-3: Domain Models & Exceptions (3h)
├── Day 3: Input Ports (3h)
└── Day 5: Output Ports + Documentation (2h)

Week 2 (Days 1-5): 23 hours
├── Day 1: Payment Core + Services (2h)
├── Day 1-2: DB Adapters (5h)
├── Day 2-3: Use-Case Services (6h)
├── Day 4: REST Controllers (4h)
└── Day 5: Integration Tests + Docs (6h)
```

---

## 🎯 STEP-BY-STEP IMPLEMENTATION

### **WEEK 1: Domain Models & Ports**

#### **Day 1-2: Create Database & Test Migrations**

**Task 1.1:** Verify migrations execute on local database

```bash
# Start fresh local database
docker compose down db && docker compose up -d db

# Wait for PostgreSQL to be healthy
docker compose logs -f db

# Verify hexagonal_scim database exists
psql -h localhost -U scim -d hexagonal_scim -c "\dt"

# Check migration status
psql -h localhost -U scim -d hexagonal_scim -c "SELECT version, description FROM flyway_schema_history ORDER BY version;"
```

**Expected Output:**
```
 version |                               description                               
---------+-----------------------------------------------------------------------
      1 | create users table
      ...
     10 | add test data for manual testing
```

**Task 1.2:** Verify migration V11-V18 structure

```bash
# After migrations run automatically, check tables exist
psql -h localhost -U scim -d hexagonal_scim -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name;"
```

**Expected tables:**
```
 orders
 order_items
 product_reviews
 products (modified)
 seller_payouts
 seller_reviews
 transactions
 users (modified)
```

**Effort:** 1h

---

#### **Day 1-2: Create Core Domain Models**

**Task 1.3:** Create Order aggregate root

**File:** `hex-core/src/main/java/com/example/user/model/Order.java`

```java
// ...existing code...
@Value
public class Order {
    Long id;
    Long buyerId;
    Long sellerId;
    List<OrderItem> items;
    OrderStatus status;
    BigDecimal grossAmount;      // Before commission
    BigDecimal commissionAmount; // 5%
    BigDecimal sellerPayout;     // 95%
    Long paymentId;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    // Factory method
    public static Order create(Long buyerId, Long sellerId, List<OrderItem> items) {
        BigDecimal grossAmount = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new Order(
            null, // id (assigned by DB)
            buyerId,
            sellerId,
            items,
            OrderStatus.PENDING_PAYMENT,
            grossAmount,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    // Status transition
    public Order markAsPaid(Long paymentId, BigDecimal commission, BigDecimal payout) {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new OrderException("Can only mark PENDING_PAYMENT orders as paid");
        }
        return new Order(
            this.id, this.buyerId, this.sellerId, this.items,
            OrderStatus.PAID,
            this.grossAmount, commission, payout,
            paymentId,
            this.createdAt,
            LocalDateTime.now()
        );
    }
}
```

**Checklist:**
- [ ] `Order.java` created with value semantics
- [ ] Factory method `create()` validates init state
- [ ] State transitions (markAsPaid, ship, deliver) implemented
- [ ] No mutable methods; only factory returns new instances

**Effort:** 1h

**Task 1.4:** Create OrderItem, Transaction, SellerEarnings, SellerPayout models

**Files:**
- `hex-core/.../model/OrderItem.java` - Line item in order (product, qty, price)
- `hex-core/.../model/Transaction.java` - Financial record with commission split
- `hex-core/.../model/SellerEarnings.java` - Aggregated earnings (total_earned, total_paid_out, pending_payout)
- `hex-core/.../model/SellerPayout.java` - Single payout request with status

**Task 1.5:** Create OrderStatus enum

**File:** `hex-core/src/main/java/com/example/user/model/OrderStatus.java`

```java
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED
}
```

**Effort:** 2h total for all 4 models

---

#### **Day 2-3: Create Value Objects & Exceptions**

**Task 1.6:** Create value objects for marketplace concepts

**Files to create:**
- `hex-core/.../model/SellerProfile.java` - Seller profile info (displayName, bio, rating, etc.)
- `hex-core/.../model/SellerRegistration.java` - Input VO for seller onboarding (displayName, bio with validation)
- `hex-core/.../model/PaymentSplit.java` (in hex-payment-core) - Commission split (gross, commission%, seller%)

**Checklist:**
- [ ] Value objects use @Value (immutable)
- [ ] Validation in constructor
- [ ] Factory methods for safe creation
- [ ] No mutable setters

**Effort:** 1h

**Task 1.7:** Create exception hierarchy

**File:** `hex-core/src/main/java/com/example/user/exception/`

Create specific exception classes:
- `SellerException` - Base seller errors
- `SellerNotFoundException` - Seller not found
- `InsufficientStockException` - Product stock depleted
- `OrderException` - Order state/validation error
- `UnauthorizedSellerOperationException` - Seller trying to access another seller's data
- `CommissionCalculationException` - Commission calculation failed

**Checklist:**
- [ ] Extend `RuntimeException`
- [ ] Include seller_id, order_id, product_id in messages
- [ ] Use structured logging-friendly messages

**Effort:** 30min

---

#### **Day 3: Create Input Ports (Use-Case Interfaces)**

**Task 1.8:** Define 18+ input ports

**File:** `hex-core/src/main/java/com/example/user/port/in/`

**Create ports for each use case:**

```java
// Seller Management
public interface EnableSellerUseCase {
    SellerProfile enableSeller(Long userId, SellerRegistration registration);
}

public interface GetSellerProfileUseCase {
    SellerProfile getSellerProfile(Long sellerId);
}

public interface UpdateSellerProfileUseCase {
    SellerProfile updateSellerProfile(Long sellerId, SellerProfileUpdate updates);
}

// Product Management
public interface CreateProductUseCase {
    Product createProduct(Long sellerId, CreateProductCommand cmd);
}

public interface UpdateProductUseCase {
    Product updateProduct(Long sellerId, Long productId, UpdateProductCommand cmd);
}

public interface DeleteProductUseCase {
    void deleteProduct(Long sellerId, Long productId);
}

public interface GetProductUseCase {
    Product getProduct(Long productId);
}

public interface SearchProductsUseCase {
    List<Product> searchProducts(SearchProductsQuery query);
}

// Order Management
public interface CreateOrderUseCase {
    Order createOrder(Long buyerId, CreateOrderCommand cmd);
}

public interface GetOrderUseCase {
    Order getOrder(Long orderId, Long requestingUserId);
}

public interface ListOrdersUseCase {
    List<Order> listOrders(Long userId, OrderListQuery query);
}

public interface CancelOrderUseCase {
    void cancelOrder(Long orderId, Long buyerId);
}

// Seller Earnings & Payouts
public interface GetSellerEarningsUseCase {
    SellerEarnings getEarnings(Long sellerId);
}

public interface RequestPayoutUseCase {
    SellerPayout requestPayout(Long sellerId, RequestPayoutCommand cmd);
}

public interface GetPayoutHistoryUseCase {
    List<SellerPayout> getPayoutHistory(Long sellerId, Integer limit);
}

// Reviews
public interface SubmitProductReviewUseCase {
    ProductReview submitProductReview(Long buyerId, Long productId, SubmitReviewCommand cmd);
}

public interface GetProductReviewsUseCase {
    List<ProductReview> getProductReviews(Long productId, Integer limit, Integer offset);
}

// Payment (hex-payment-core)
public interface ProcessOrderPaymentUseCase {
    PaymentResult processOrderPayment(Long orderId, PaymentCommand cmd);
}
```

**Checklist:**
- [ ] One interface = one use case (SRP)
- [ ] Methods take domain objects / command objects
- [ ] Return types are domain objects or void
- [ ] Javadoc includes @throws for all documented exceptions
- [ ] No Spring annotations in interfaces

**Effort:** 3h

---

#### **Day 5: Create Output Ports (Repository Interfaces)**

**Task 1.9:** Define repository port interfaces

**File:** `hex-core/src/main/java/com/example/user/port/out/`

```java
// Seller Repository
public interface SellerRepositoryPort {
    void save(SellerProfile seller);
    Optional<SellerProfile> findBySellerId(Long sellerId);
    Optional<SellerProfile> findByDisplayName(String displayName);
}

// Product Repository
public interface ProductRepositoryPort {
    Product save(Product product);
    Optional<Product> findById(Long productId);
    List<Product> findBySellerId(Long sellerId);
    List<Product> findActive(SearchProductsQuery query);
    void delete(Long productId);
}

// Order Repository
public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(Long orderId);
    List<Order> findByBuyerId(Long buyerId);
    List<Order> findBySellerId(Long sellerId);
}

// Transaction Repository
public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);
    Optional<Transaction> findByOrderId(Long orderId);
    List<Transaction> findBySellerId(Long sellerId);
}

// Seller Earnings Repository
public interface SellerEarningsRepositoryPort {
    void save(SellerEarnings earnings);
    Optional<SellerEarnings> findBySellerId(Long sellerId);
    void updatePendingPayout(Long sellerId, BigDecimal amount);
}

// Seller Payout Repository
public interface SellerPayoutRepositoryPort {
    SellerPayout save(SellerPayout payout);
    Optional<SellerPayout> findById(Long id);
    List<SellerPayout> findBySellerId(Long sellerId, Integer limit);
    List<SellerPayout> findByStatus(String status);
}

// Review Repositories
public interface ProductReviewRepositoryPort {
    ProductReview save(ProductReview review);
    Optional<ProductReview> findByProductIdAndBuyerId(Long productId, Long buyerId);
    List<ProductReview> findByProductId(Long productId);
}

public interface SellerReviewRepositoryPort {
    SellerReview save(SellerReview review);
    Optional<SellerReview> findBySellerIdAndBuyerId(Long sellerId, Long buyerId);
    List<SellerReview> findBySellerId(Long sellerId);
}
```

**Checklist:**
- [ ] One port per entity type
- [ ] Methods return domain objects (not DTOs)
- [ ] Optional<T> for queries that may not exist
- [ ] List<T> for queries expecting multiple results
- [ ] void for save/update/delete

**Effort:** 1h

**Total Week 1 Effort: 13h**

---

### **WEEK 2: Implementations & Integration**

#### **Day 1: Payment Core & Configuration**

**Task 2.1:** Create payment models in hex-payment-core

**File:** `hex-payment-core/src/main/java/com/example/user/model/PaymentSplit.java`

```java
@Value
public class PaymentSplit {
    BigDecimal grossAmount;
    BigDecimal platformCommissionRate;  // BigDecimal("0.05") = 5%
    BigDecimal sellerPayoutRate;        // BigDecimal("0.95") = 95%

    public BigDecimal calculateCommission() {
        return grossAmount.multiply(platformCommissionRate)
            .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSellerPayout() {
        return grossAmount.multiply(sellerPayoutRate)
            .setScale(2, RoundingMode.HALF_UP);
    }

    // Validation: commission + payout should equal gross (within rounding)
    public boolean isValid() {
        BigDecimal commission = calculateCommission();
        BigDecimal payout = calculateSellerPayout();
        BigDecimal sum = commission.add(payout);
        return sum.equals(grossAmount) || 
               sum.compareTo(grossAmount) == 0;
    }
}
```

**Effort:** 1h

**Task 2.2:** Create payment processing port

**File:** `hex-payment-core/src/main/java/com/example/user/port/in/ProcessOrderPaymentUseCase.java`

```java
public interface ProcessOrderPaymentUseCase {
    /**
     * Process order payment and record transaction with commission split.
     * 
     * @param orderId the order ID
     * @param command payment details (card, amount)
     * @return PaymentResult with success/failure status
     * @throws OrderNotFoundException if order doesn't exist
     * @throws PaymentProcessingException if payment gateway fails
     */
    PaymentResult processOrderPayment(Long orderId, PaymentCommand command);
}
```

**Effort:** 30min

**Total Day 1 Effort: 1.5h**

---

#### **Day 1-2: Create Database Adapters**

**Task 2.3:** Implement repository adapters in hex-outbound-adapter-db

**Files:** `hex-outbound-adapter-db/src/main/java/com/example/user/adapter/`

**Create 8 adapter classes:**

```java
// OrderRepositoryAdapter.java
@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepositoryPort {
    private final OrderJpaRepository jpaRepository;
    private static final Logger logger = LoggerFactory.getLogger(OrderRepositoryAdapter.class);

    @Override
    public Order save(Order order) {
        logger.debug("flow_stage=ADAPTER_PERSIST operation=order.save orderId={}", order.getId());
        
        OrderEntity entity = orderMapper.toEntity(order);
        OrderEntity saved = jpaRepository.save(entity);
        
        logger.debug("flow_stage=ADAPTER_PERSISTED operation=order.save orderId={}", saved.getId());
        return orderMapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return jpaRepository.findById(orderId)
            .map(orderMapper::toDomain);
    }

    @Override
    public List<Order> findByBuyerId(Long buyerId) {
        return jpaRepository.findByBuyerId(buyerId).stream()
            .map(orderMapper::toDomain)
            .collect(Collectors.toList());
    }
    
    // ... other methods
}
```

**Create corresponding JPA repositories:**
```java
// OrderJpaRepository.java (extends JpaRepository<OrderEntity, Long>)
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByBuyerId(Long buyerId);
    List<OrderEntity> findBySellerId(Long sellerId);
    List<OrderEntity> findByStatus(String status);
}
```

**Create entity-to-domain mappers:**
```java
// OrderMapper.java
@Component
public class OrderMapper {
    public Order toDomain(OrderEntity entity) {
        // Map entity to domain model
    }
    
    public OrderEntity toEntity(Order domain) {
        // Map domain model to entity
    }
}
```

**Adapters to create:**
1. OrderRepositoryAdapter + OrderJpaRepository + OrderMapper
2. TransactionRepositoryAdapter + TransactionJpaRepository + TransactionMapper
3. ProductRepositoryAdapter + ProductJpaRepository + ProductMapper
4. SellerEarningsRepositoryAdapter + SellerEarningsJpaRepository + SellerEarningsMapper
5. SellerPayoutRepositoryAdapter + SellerPayoutJpaRepository + SellerPayoutMapper
6. ProductReviewRepositoryAdapter + ProductReviewJpaRepository + ProductReviewMapper
7. SellerReviewRepositoryAdapter + SellerReviewJpaRepository + SellerReviewMapper

**Checklist:**
- [ ] Each adapter implements its port interface
- [ ] Mappers convert between JPA entities and domain models
- [ ] JPA repositories use Spring Data with custom queries where needed
- [ ] Adapter methods add tracing logs (flow_stage=ADAPTER_PERSIST, etc.)
- [ ] Error handling converts JPA exceptions to domain exceptions

**Effort:** 5h

---

#### **Day 2-3: Implement Use-Case Services**

**Task 2.4:** Implement 7 use-case services in hex-application

**File:** `hex-application/src/main/java/com/example/user/application/`

**Service 1: EnableSellerService**

```java
@Service
@RequiredArgsConstructor
@Transactional
public class EnableSellerService implements EnableSellerUseCase {
    private final UserRepositoryPort userRepository;
    private final SellerRepositoryPort sellerRepository;
    private final SellerEarningsRepositoryPort earningsRepository;
    private static final Logger logger = LoggerFactory.getLogger(EnableSellerService.class);

    @Override
    public SellerProfile enableSeller(Long userId, SellerRegistration registration) {
        // Stage 1: REQUEST_RECEIVED
        logger.info("flow_stage=REQUEST_RECEIVED operation=seller.enable userId={} status=INITIATED", userId);

        // Stage 2: DOMAIN_OPERATION_COMPLETED
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        if (user.isSeller()) {
            throw new SellerException("User is already a seller");
        }

        // Enable seller on user entity
        user.enableSeller(registration.getDisplayName(), registration.getBio());
        User savedUser = userRepository.save(user);

        // Create seller profile
        SellerProfile profile = SellerProfile.from(savedUser);
        sellerRepository.save(profile);

        // Initialize earnings record
        SellerEarnings earnings = SellerEarnings.initialize(savedUser.getId());
        earningsRepository.save(earnings);

        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=seller.enable sellerId={} displayName={} status=SUCCESS",
            savedUser.getId(), registration.getDisplayName());

        // Stage 3: RESPONSE_PREPARED
        logger.info("flow_stage=RESPONSE_PREPARED operation=seller.enable sellerId={} status=COMPLETED", savedUser.getId());

        return profile;
    }
}
```

**Services to implement:**
1. EnableSellerService(EnableSellerUseCase)
2. CreateProductService(CreateProductUseCase)
3. CreateOrderService(CreateOrderUseCase)
4. ProcessPaymentService(ProcessOrderPaymentUseCase)
5. GetSellerEarningsService(GetSellerEarningsUseCase)
6. RequestPayoutService(RequestPayoutUseCase)
7. SearchProductsService(SearchProductsUseCase)

**Checklist for each:**
- [ ] Implements use-case port
- [ ] @Service + @RequiredArgsConstructor + @Transactional
- [ ] Logs 3 flow stages: REQUEST_RECEIVED, DOMAIN_OPERATION_COMPLETED, RESPONSE_PREPARED
- [ ] Throws specific domain exceptions
- [ ] Validates business rules before operations
- [ ] Uses repository ports (not JPA directly)

**Effort:** 6h

---

#### **Day 4: Create REST Controllers**

**Task 2.5:** Implement 8 REST controller adapters in hex-inbound-adapter-web

**File:** `hex-inbound-adapter-web/src/main/java/com/example/user/api/`

**Example: SellerControllerAdapter**

```java
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
@Validated
public class SellerControllerAdapter {
    private final EnableSellerUseCase enableSellerUseCase;
    private final GetSellerProfileUseCase getSellerProfileUseCase;
    private final SellerMapper mapper;
    private static final Logger logger = LoggerFactory.getLogger(SellerControllerAdapter.class);

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SellerResponse registerAsSeller(
        @AuthenticationPrincipal User authenticatedUser,
        @Valid @RequestBody EnableSellerRequest request
    ) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=seller.register userId={}", authenticatedUser.getId());

        SellerProfile profile = enableSellerUseCase.enableSeller(
            authenticatedUser.getId(),
            new SellerRegistration(request.getDisplayName(), request.getBio())
        );

        logger.info("flow_stage=RESPONSE_PREPARED operation=seller.register sellerId={} status=COMPLETED",
            profile.getSellerId());

        return mapper.toResponse(profile);
    }

    @GetMapping("/{sellerId}")
    public SellerResponse getSellerProfile(@PathVariable Long sellerId) {
        SellerProfile profile = getSellerProfileUseCase.getSellerProfile(sellerId);
        return mapper.toResponse(profile);
    }

    @ExceptionHandler(SellerException.class)
    public ResponseEntity<ErrorResponse> handleSellerException(SellerException e) {
        logger.error("Seller operation failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("SELLER_ERROR", e.getMessage()));
    }
}
```

**Controllers to create:**
1. SellerControllerAdapter (/api/v1/sellers)
2. ProductControllerAdapter (/api/v1/sellers/me/products, /api/v1/products)
3. OrderControllerAdapter (/api/v1/orders)
4. SellerEarningsControllerAdapter (/api/v1/sellers/{id}/earnings)
5. PayoutControllerAdapter (/api/v1/sellers/me/payouts)
6. ReviewControllerAdapter (/api/v1/reviews)

**Checklist for each:**
- [ ] @RestController + @RequestMapping
- [ ] Methods take @Valid DTOs, return Response DTOs
- [ ] @AuthenticationPrincipal for authenticated endpoints
- [ ] 3-stage logging pattern
- [ ] @ExceptionHandler maps domain exceptions to HTTP status codes
- [ ] Mapper converts between DTOs and domain objects

**Effort:** 4h

---

#### **Day 5: Integration Tests & Verification**

**Task 2.6:** Write integration tests

**File:** `hex-outbound-adapter-db/src/test/java/.../adapter/`

```java
@SpringBootTest
@Testcontainers
public class OrderRepositoryAdapterIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private OrderRepositoryAdapter adapter;  // Implementation

    @Autowired
    private OrderJpaRepository jpaRepository;

    @Test
    void testSaveAndRetrieveOrder() {
        // Arrange
        Order order = Order.create(1L, 2L, List.of(...));

        // Act
        Order saved = adapter.save(order);
        Optional<Order> retrieved = adapter.findById(saved.getId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(order.getBuyerId(), retrieved.get().getBuyerId());
    }
}
```

**Test files to create:**
- OrderRepositoryAdapterIntegrationTest
- TransactionRepositoryAdapterIntegrationTest
- ProductRepositoryAdapterIntegrationTest
- SellerEarningsRepositoryAdapterIntegrationTest

**Service tests:**
- EnableSellerServiceTest
- CreateOrderServiceTest
- ProcessPaymentServiceTest

**Controller tests:**
- SellerControllerAdapterTest
- OrderControllerAdapterTest

**Test coverage target:** 80%+

**Effort:** 4h

**Task 2.7:** Run full test suite and verify

```bash
mvn clean verify -pl hex-core,hex-outbound-adapter-db,hex-application,hex-inbound-adapter-web
```

**Effort:** 2h

**Total Week 2 Effort: 23h**

---

## ✅ COMPLETION CHECKLIST

### Backend Deliverables
- [ ] V11-V18 migrations created and run successfully
- [ ] 30+ domain models created (Order, Product, Transaction, etc.)
- [ ] 18+ input ports (use-case interfaces) defined
- [ ] 8 repository output ports defined
- [ ] 7 use-case service implementations (hex-application)
- [ ] 8 repository adapters (hex-outbound-adapter-db)
- [ ] 8 REST controller adapters (hex-inbound-adapter-web)
- [ ] Commission calculation logic tested (PaymentSplit)
- [ ] Seller authorization enforced at DB level + app level
- [ ] Order state machine validated
- [ ] Full integration test coverage (80%+)
- [ ] Build passes: `mvn clean verify`
- [ ] No CVE findings: `mvn org.owasp:dependency-check-maven:check`
- [ ] Splunk-ready logging implemented (3-stage pattern)
- [ ] API documentation (OpenAPI/Swagger)
- [ ] All exceptions properly handled with meaningful HTTP status codes

---

## 🚀 NEXT STEPS AFTER PHASE 1

1. **Phase 1 Integration Testing** - Run full end-to-end tests, stress test
2. **Frontend Implementation** - Seller dashboard, buyer shopping flow
3. **Phase 1 to Production** - Deployment verification, production data setup
4. **Phase 2: UX Enhancements** - Persistent cart, messaging, analytics, shipping
5. **Phase 3: Compliance** - KYC/AML, disputes, tax, multi-currency

---

## 📞 DEBUGGING & TROUBLESHOOTING

**Migration failures:**
```bash
psql -h localhost -U scim -d hexagonal_scim -c "SELECT * FROM flyway_schema_history;"
# Check version and error message
```

**Adapter test failures:**
```bash
# Check testcontainers logs
docker ps -a | grep testcontainers
docker logs <container>
```

**Service transactional issues:**
```bash
# Verify @Transactional is working
mvn test -Dtest=EnableSellerServiceTest -X 2>&1 | grep -i transaction
```

**Controller 401 Unauthorized:**
```bash
# Keycloak not accessible; verify JWT configuration
curl http://localhost:8080/actuator/health/livenessState
```

---

## 📖 REFERENCE DOCUMENTATION

- **Hexagonal Architecture:** `/marketplace.md` Section: "Architecture Mapping"
- **Copilot Instructions:** `.github/copilot-instructions.md`
- **SOLID Principles:** `.github/copilot-instructions.md` Section: "SOLID Principles"
- **Marketplace Logging:** `.github/copilot-instructions.md` Section: "Marketplace Logging Patterns"

---

**Status:** Ready to implement Week 1 Day 1  
**Last Updated:** May 17, 2026


