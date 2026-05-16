# 🛍️ Online Marketplace Platform - Architecture Plan

**Date Created:** May 17, 2026  
**Status:** Planning Phase  
**Target Completion:** Phase 1 MVP (2 weeks)

---

## 📋 Vision

Transform the hexagonal-scim platform into a full-featured online marketplace where:
- ✅ Users can register as sellers
- ✅ Sellers list and manage their products
- ✅ Buyers discover and purchase products from multiple sellers
- ✅ Platform takes 5% commission per transaction
- ✅ Sellers receive 95% payout after commission deduction
- ✅ Complete order lifecycle management (pending → paid → shipped → delivered)

---

## 🎯 PHASE 1: MVP - Core Marketplace (Weeks 1-2)

### **IMPLEMENTATION PREREQUISITES**

Before starting backend work, ensure:
1. **Environment Setup:** Local PostgreSQL running, Keycloak configured
2. **Dependencies Review:** Audit Spring Boot, JPA, Hibernate versions
3. **Testing Framework:** JUnit 5 ready with test containers for integration tests
4. **Git Workflow:** Feature branches follow `feature/marketplace-*` pattern
5. **Code Quality:** SonarQube / PMD configured for marketplace code

---

### **BACKEND ENHANCEMENTS**

#### **1. Seller/Vendor Evolution**

**What to build:**
- Extend `User` entity with seller capabilities
- Allow users to self-register as sellers
- Track seller verification status and rating
- Implement role-based access control for seller operations

**Database Changes (Migration: `V11__add_seller_capabilities_to_users.sql`):**
```sql
-- Add seller-specific columns to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_seller BOOLEAN DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_verified_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_display_name VARCHAR(255) UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_bio TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_rating DECIMAL(3,2) DEFAULT 0.0 CHECK (seller_rating >= 0 AND seller_rating <= 5);
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_review_count INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS seller_joined_at TIMESTAMP;

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_users_is_seller ON users(is_seller);
CREATE INDEX IF NOT EXISTS idx_users_seller_rating ON users(seller_rating DESC) WHERE is_seller = true;
```

**New Domain Models (hex-core):**

```java
// File: hex-core/src/main/java/com/example/user/model/SellerProfile.java
@Value
public class SellerProfile {
    Long sellerId;
    String displayName;
    String bio;
    BigDecimal rating;
    Integer reviewCount;
    LocalDateTime verifiedAt;
    LocalDateTime joinedAt;
    Boolean isActive; // is_seller = true
    
    // Factory method for safe creation
    public static SellerProfile from(User user) {
        if (!user.isSeller()) {
            throw new SellerException("User is not a seller");
        }
        return new SellerProfile(
            user.getId(),
            user.getSellerDisplayName(),
            user.getSellerBio(),
            user.getSellerRating(),
            user.getSellerReviewCount(),
            user.getSellerVerifiedAt(),
            user.getSellerJoinedAt(),
            true
        );
    }
}

// Value Object for Seller onboarding validation
@Value
public class SellerRegistration {
    String displayName;  // Required, length 3-100
    String bio;          // Optional, max 500 chars
    
    // Validation during construction
    public SellerRegistration {
        if (displayName == null || displayName.length() < 3 || displayName.length() > 100) {
            throw new ValidationException("displayName must be 3-100 characters");
        }
        if (bio != null && bio.length() > 500) {
            throw new ValidationException("bio must not exceed 500 characters");
        }
    }
}
```

**New Input Ports (hex-core - `port/in` package):**

```java
// File: hex-core/src/main/java/com/example/user/port/in/EnableSellerUseCase.java
public interface EnableSellerUseCase {
    /**
     * Enables a user to become a seller.
     * 
     * @param userId authenticated user ID
     * @param registration seller display name + bio
     * @return created SellerProfile
     * @throws SellerException if user already a seller
     * @throws ValidationException if displayName/bio invalid
     * @throws UserNotFoundException if user doesn't exist
     */
    SellerProfile enableSeller(Long userId, SellerRegistration registration);
}

// File: hex-core/src/main/java/com/example/user/port/in/GetSellerProfileUseCase.java
public interface GetSellerProfileUseCase {
    /**
     * Retrieves a seller's public profile.
     * 
     * @param sellerId the seller's user ID
     * @return seller profile (public view)
     * @throws SellerNotFoundException if seller doesn't exist or not verified
     */
    SellerProfile getSellerProfile(Long sellerId);
}

// File: hex-core/src/main/java/com/example/user/port/in/UpdateSellerProfileUseCase.java
public interface UpdateSellerProfileUseCase {
    /**
     * Allows an authenticated seller to update their profile.
     * 
     * @param sellerId authenticated seller ID
     * @param updates partial updates (displayName, bio)
     * @return updated SellerProfile
     * @throws SellerNotFoundException if seller doesn't exist
     * @throws AccessDeniedException if user not allowed to update
     * @throws ValidationException if updates invalid
     */
    SellerProfile updateSellerProfile(Long sellerId, SellerProfileUpdate updates);
}
```

**Implementation Classes (hex-application):**

```java
// File: hex-application/src/main/java/com/example/user/application/EnableSellerService.java
@Service
@RequiredArgsConstructor
public class EnableSellerService implements EnableSellerUseCase {
    private final UserRepositoryPort userRepository;
    private final SellerRepositoryPort sellerRepository;
    
    private static final Logger logger = LoggerFactory.getLogger(EnableSellerService.class);
    
    @Override
    @Transactional
    public SellerProfile enableSeller(Long userId, SellerRegistration registration) {
        // Stage 1: REQUEST_RECEIVED
        logger.info("flow_stage=REQUEST_RECEIVED operation=seller.enable userId={} status=INITIATED", userId);
        
        // Validate registration
        registration.validate(); // value object validates itself
        
        // Retrieve user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Check if already a seller
        if (user.isSeller()) {
            throw new SellerException("User is already a seller");
        }
        
        // Enable seller account
        user.enableSeller(registration.getDisplayName(), registration.getBio());
        User savedUser = userRepository.save(user);
        
        // Create seller earnings record (with 0 balance initially)
        SellerEarnings earnings = new SellerEarnings(
            savedUser.getId(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
            // pending_payout = total_earned - total_paid_out = 0 - 0 = 0
        );
        sellerRepository.saveEarnings(earnings);
        
        // Stage 2: DOMAIN_OPERATION_COMPLETED
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=seller.enable userId={} sellerId={} displayName={} status=SUCCESS",
            userId, savedUser.getId(), registration.getDisplayName());
        
        // Stage 3: RESPONSE_PREPARED
        logger.info("flow_stage=RESPONSE_PREPARED operation=seller.enable sellerId={} status=COMPLETED", savedUser.getId());
        
        return SellerProfile.from(savedUser);
    }
}
```

**Outbound Adapter (hex-outbound-adapter-db):**

```java
// File: hex-outbound-adapter-db/src/main/java/com/example/user/adapter/SellerRepositoryAdapter.java
@Repository
@RequiredArgsConstructor
public class SellerRepositoryAdapter implements SellerRepositoryPort {
    private final SellerEarningsJpaRepository earningsRepository;
    private final SellerPayoutJpaRepository payoutRepository;
    
    @Override
    public void saveEarnings(SellerEarnings earnings) {
        SellerEarningsEntity entity = new SellerEarningsEntity();
        entity.setSellerId(earnings.getSellerId());
        entity.setTotalEarned(earnings.getTotalEarned());
        entity.setTotalPaidOut(earnings.getTotalPaidOut());
        entity.setPendingPayout(earnings.getPendingPayout());
        earningsRepository.save(entity);
    }
    
    @Override
    public Optional<SellerEarnings> getEarnings(Long sellerId) {
        return earningsRepository.findBySellerId(sellerId)
            .map(this::toDomain);
    }
    
    private SellerEarnings toDomain(SellerEarningsEntity entity) {
        return new SellerEarnings(
            entity.getSellerId(),
            entity.getTotalEarned(),
            entity.getTotalPaidOut(),
            entity.getPendingPayout()
        );
    }
}

// JPA Entity
@Entity
@Table(name = "seller_earnings")
@Data
public class SellerEarningsEntity {
    @Id
    private Long sellerId;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal totalEarned = BigDecimal.ZERO;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal totalPaidOut = BigDecimal.ZERO;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal pendingPayout = BigDecimal.ZERO;
    
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime lastPayoutAt;
    
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
```

**REST Controller (hex-inbound-adapter-web):**

```java
// File: hex-inbound-adapter-web/src/main/java/com/example/user/api/SellerControllerAdapter.java
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerControllerAdapter {
    private final EnableSellerUseCase enableSeller;
    private final GetSellerProfileUseCase getSellerProfile;
    private final UpdateSellerProfileUseCase updateSeller;
    
    private static final Logger logger = LoggerFactory.getLogger(SellerControllerAdapter.class);
    
    // Request DTOs
    @Data
    public static class EnableSellerRequest {
        @NotBlank(message = "displayName is required")
        @Length(min = 3, max = 100, message = "displayName must be 3-100 characters")
        private String displayName;
        
        @Length(max = 500, message = "bio must not exceed 500 characters")
        private String bio;
    }
    
    // Response DTOs
    @Data
    public static class SellerProfileResponse {
        private Long sellerId;
        private String displayName;
        private String bio;
        private BigDecimal rating;
        private Integer reviewCount;
        private LocalDateTime verifiedAt;
        private LocalDateTime joinedAt;
    }
    
    @PostMapping("/enable")
    public ResponseEntity<SellerProfileResponse> enableSeller(
        @AuthenticationPrincipal User principal,
        @Valid @RequestBody EnableSellerRequest request
    ) {
        SellerRegistration registration = new SellerRegistration(
            request.getDisplayName(),
            request.getBio()
        );
        
        SellerProfile profile = enableSeller.enableSeller(principal.getId(), registration);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toResponse(profile));
    }
    
    @GetMapping("/{sellerId}")
    public ResponseEntity<SellerProfileResponse> getSeller(@PathVariable Long sellerId) {
        SellerProfile profile = getSellerProfile.getSellerProfile(sellerId);
        return ResponseEntity.ok(toResponse(profile));
    }
    
    private SellerProfileResponse toResponse(SellerProfile profile) {
        SellerProfileResponse response = new SellerProfileResponse();
        response.setSellerId(profile.getSellerId());
        response.setDisplayName(profile.getDisplayName());
        response.setBio(profile.getBio());
        response.setRating(profile.getRating());
        response.setReviewCount(profile.getReviewCount());
        response.setVerifiedAt(profile.getVerifiedAt());
        response.setJoinedAt(profile.getJoinedAt());
        return response;
    }
}
```

**Unit Tests:**

```java
// File: hex-core/src/test/java/com/example/user/model/SellerRegistrationTest.java
@DisplayName("SellerRegistration Value Object Tests")
class SellerRegistrationTest {
    
    @Test
    @DisplayName("should create valid registration")
    void shouldCreateValidRegistration() {
        // When
        SellerRegistration reg = new SellerRegistration("My Store", "Premium goods");
        
        // Then
        assertThat(reg.getDisplayName()).isEqualTo("My Store");
    }
    
    @Test
    @DisplayName("should reject displayName too short")
    void shouldRejectShortDisplayName() {
        // When/Then
        assertThrows(ValidationException.class, 
            () -> new SellerRegistration("AB", "bio"));
    }
    
    @Test
    @DisplayName("should reject displayName too long")
    void shouldRejectLongDisplayName() {
        // When/Then
        String longName = "A".repeat(101);
        assertThrows(ValidationException.class, 
            () -> new SellerRegistration(longName, "bio"));
    }
}

// File: hex-application/src/test/java/com/example/user/application/EnableSellerServiceTest.java
@SpringBootTest
@DisplayName("EnableSellerService Integration Tests")
class EnableSellerServiceTest {
    
    @Autowired
    private EnableSellerService service;
    
    @Autowired
    private UserRepositoryPort userRepository;
    
    @Autowired
    private SellerRepositoryPort sellerRepository;
    
    private User testUser;
    
    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("seller@test.com");
        testUser.setIsSeller(false);
    }
    
    @Test
    @Transactional
    @DisplayName("should enable user as seller")
    void shouldEnableUserAsSeller() {
        // Given
        userRepository.save(testUser);
        SellerRegistration registration = new SellerRegistration("Test Store", "My bio");
        
        // When
        SellerProfile profile = service.enableSeller(testUser.getId(), registration);
        
        // Then
        assertThat(profile.getDisplayName()).isEqualTo("Test Store");
        assertThat(profile.getVerifiedAt()).isNotNull();
        
        // Verify earnings created
        Optional<SellerEarnings> earnings = sellerRepository.getEarnings(testUser.getId());
        assertThat(earnings).isPresent();
        assertThat(earnings.get().getPendingPayout()).isEqualTo(BigDecimal.ZERO);
    }
    
    @Test
    @DisplayName("should throw exception if already seller")
    void shouldThrowIfAlreadySeller() {
        // Given
        testUser.setIsSeller(true);
        testUser.setSellerDisplayName("Existing Store");
        userRepository.save(testUser);
        
        // When/Then
        SellerRegistration registration = new SellerRegistration("New Store", "bio");
        assertThrows(SellerException.class, 
            () -> service.enableSeller(testUser.getId(), registration));
    }
}
```

**Rationale (Hexagonal):**
Seller is a role/status, not a separate bounded context. Keep seller attributes in `users` table. Use value objects (SellerProfile, SellerRegistration) for type safety and encapsulation. All business logic lives in services (application layer). Database queries encapsulated in adapters.

---

#### **2. Product Ownership & Inventory Management**

**What to build:**
- Link products to sellers (product.seller_id) with FK constraint
- Add product lifecycle (active/inactive) with soft-delete pattern
- Implement stock management (with validation at purchase time)
- Allow sellers to manage only their own products (with authorization)

**Database Changes (Migration: `V12__enhance_products_with_seller_and_status.sql`):**
```sql
-- Enhance products table with seller and lifecycle fields
ALTER TABLE products ADD COLUMN IF NOT EXISTS seller_id BIGINT REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;
ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_quantity INTEGER DEFAULT 0 CHECK (stock_quantity >= 0);
ALTER TABLE products ADD COLUMN IF NOT EXISTS published_at TIMESTAMP DEFAULT NOW();

-- For existing products, set seller_id to NULL or default seller (handle data migration)
-- CREATE TABLE temp_unowned_products AS
--   SELECT id FROM products WHERE seller_id IS NULL;

-- Create indices
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_is_active ON products(is_active);
CREATE INDEX IF NOT EXISTS idx_products_seller_active ON products(seller_id, is_active);
```

**Domain Models (hex-core):**

```java
// File: hex-core/src/main/java/com/example/user/model/Product.java
@Value
public class Product {
    Long id;
    Long sellerId;
    String name;
    String description;
    BigDecimal price;
    String currency;
    Integer stockQuantity;
    Boolean isActive;
    LocalDateTime publishedAt;
    LocalDateTime createdAt;
    
    // Business logic: check if product is available for purchase
    public boolean isAvailableForPurchase() {
        return isActive && stockQuantity > 0;
    }
    
    // Business logic: check if seller owns this product
    public boolean isOwnedBy(Long userId) {
        return sellerId.equals(userId);
    }
    
    // Factory for new product creation
    public static Product createNew(
        Long sellerId,
        String name,
        String description,
        BigDecimal price,
        String currency,
        Integer initialStock
    ) {
        validateCreation(name, description, price, initialStock);
        return new Product(
            null, // id will be assigned by DB
            sellerId,
            name,
            description,
            price,
            currency,
            initialStock,
            true, // isActive = true by default
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
    
    private static void validateCreation(String name, String desc, BigDecimal price, Integer stock) {
        if (name == null || name.trim().isBlank() || name.length() > 255) {
            throw new ValidationException("Product name is required and must be ≤255 chars");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Product price must be positive");
        }
        if (stock == null || stock < 0) {
            throw new ValidationException("Stock quantity cannot be negative");
        }
    }
}

// Exception for product not found (return 404 to buyer, not 403)
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}

// Exception for seller authorization
public class SellerNotProductOwnerException extends RuntimeException {
    public SellerNotProductOwnerException(Long productId, Long sellerId) {
        super("Seller " + sellerId + " does not own product " + productId);
    }
}
```

**Input Ports (hex-core):**

```java
// File: hex-core/src/main/java/com/example/user/port/in/CreateProductUseCase.java
public interface CreateProductUseCase {
    /**
     * Creates a new product for a seller.
     * 
     * @param sellerId authenticated seller ID
     * @param request product creation data
     * @return created Product with generated ID
     * @throws SellerNotFoundException if seller doesn't exist
     * @throws SellerException if user is not a verified seller
     * @throws ValidationException if request data invalid
     */
    Product createProduct(Long sellerId, CreateProductRequest request);
}

// File: hex-core/src/main/java/com/example/user/port/in/UpdateProductUseCase.java
public interface UpdateProductUseCase {
    /**
     * Updates an existing product.
     * 
     * @param productId the product to update
     * @param sellerId authenticated seller ID (must own product)
     * @param request partial update data
     * @return updated Product
     * @throws ProductNotFoundException if product doesn't exist
     * @throws SellerNotProductOwnerException if seller doesn't own product
     * @throws ValidationException if update data invalid
     */
    Product updateProduct(Long productId, Long sellerId, UpdateProductRequest request);
}

// File: hex-core/src/main/java/com/example/user/port/in/DeleteProductUseCase.java
public interface DeleteProductUseCase {
    /**
     * Soft-deletes a product (marks as inactive).
     * Product remains in DB for audit/reporting but won't appear to buyers.
     * 
     * @param productId the product to delete
     * @param sellerId authenticated seller ID (must own product)
     * @throws ProductNotFoundException if product doesn't exist
     * @throws SellerNotProductOwnerException if seller doesn't own product
     */
    void deleteProduct(Long productId, Long sellerId);
}

// File: hex-core/src/main/java/com/example/user/port/in/GetSellerProductsUseCase.java
public interface GetSellerProductsUseCase {
    /**
     * Retrieves all products (active and inactive) owned by a seller.
     * Only the seller themselves can see inactive products.
     * 
     * @param sellerId the seller's ID
     * @param page pagination page (0-based)
     * @param size page size
     * @return paginated list of seller's products
     * @throws SellerNotFoundException if seller doesn't exist
     */
    Page<Product> getSellerProducts(Long sellerId, int page, int size);
}

// File: hex-core/src/main/java/com/example/user/port/in/GetPublicProductsUseCase.java
public interface GetPublicProductsUseCase {
    /**
     * Retrieves active products visible to buyers.
     * Buyers see only is_active=true products.
     * Can filter by seller or perform text search.
     * 
     * @param criteria search/filter criteria
     * @return paginated list of products
     */
    Page<Product> getPublicProducts(ProductSearchCriteria criteria);
}

// Value object for product search
@Value
public class ProductSearchCriteria {
    Integer page;
    Integer size;
    String searchQuery; // Optional: search name + description
    Long sellerId;      // Optional: filter by seller
    BigDecimal priceMin; // Optional: min price filter
    BigDecimal priceMax; // Optional: max price filter
    String sortBy;      // published_at, price, name
    String sortOrder;   // ASC, DESC
    
    public ProductSearchCriteria {
        Objects.requireNonNull(page, "page is required");
        Objects.requireNonNull(size, "size is required");
        if (page < 0 || size <= 0) {
            throw new ValidationException("Invalid pagination parameters");
        }
    }
}
```

**Service Implementation (hex-application):**

```java
// File: hex-application/src/main/java/com/example/user/application/ProductService.java
@Service
@RequiredArgsConstructor
public class ProductService implements CreateProductUseCase, UpdateProductUseCase, 
                                       DeleteProductUseCase, GetSellerProductsUseCase,
                                       GetPublicProductsUseCase {
    
    private final ProductRepositoryPort productRepository;
    private final UserRepositoryPort userRepository;
    
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    
    @Override
    @Transactional
    public Product createProduct(Long sellerId, CreateProductRequest request) {
        // Stage 1
        logger.info("flow_stage=REQUEST_RECEIVED operation=product.create sellerId={} productName={} status=INITIATED",
            sellerId, request.getName());
        
        // Verify seller exists and is verified
        User seller = userRepository.findById(sellerId)
            .orElseThrow(() -> new SellerNotFoundException("Seller not found: " + sellerId));
        
        if (!seller.isSeller()) {
            throw new SellerException("User must be a verified seller to create products");
        }
        
        // Create product with validation
        Product product = Product.createNew(
            sellerId,
            request.getName(),
            request.getDescription(),
            request.getPrice(),
            request.getCurrency(),
            request.getInitialStock()
        );
        
        // Persist
        Product saved = productRepository.save(product);
        
        // Stage 2
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=product.create productId={} sellerId={} price={} status=SUCCESS",
            saved.getId(), sellerId, saved.getPrice());
        
        // Stage 3
        logger.info("flow_stage=RESPONSE_PREPARED operation=product.create productId={} status=COMPLETED", saved.getId());
        
        return saved;
    }
    
    @Override
    @Transactional
    public Product updateProduct(Long productId, Long sellerId, UpdateProductRequest request) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=product.update productId={} sellerId={} status=INITIATED",
            productId, sellerId);
        
        // Retrieve product (return 404 if not found - security: don't reveal ownership)
        Product existing = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        
        // Verify seller owns this product
        if (!existing.isOwnedBy(sellerId)) {
            throw new SellerNotProductOwnerException(productId, sellerId);
        }
        
        // Apply updates (only allowed fields)
        Product updated = existing.withUpdates(request);
        Product saved = productRepository.save(updated);
        
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=product.update productId={} status=SUCCESS", productId);
        logger.info("flow_stage=RESPONSE_PREPARED operation=product.update productId={} status=COMPLETED", productId);
        
        return saved;
    }
    
    @Override
    @Transactional
    public void deleteProduct(Long productId, Long sellerId) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=product.delete productId={} sellerId={} status=INITIATED",
            productId, sellerId);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        
        if (!product.isOwnedBy(sellerId)) {
            throw new SellerNotProductOwnerException(productId, sellerId);
        }
        
        // Soft delete: mark inactive instead of hard delete
        Product deleted = product.withInactive();
        productRepository.save(deleted);
        
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=product.delete productId={} status=SUCCESS", productId);
        logger.info("flow_stage=RESPONSE_PREPARED operation=product.delete productId={} status=COMPLETED", productId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Product> getSellerProducts(Long sellerId, int page, int size) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=product.seller_list sellerId={} page={} size={} status=INITIATED",
            sellerId, page, size);
        
        // Verify seller exists
        if (!userRepository.existsById(sellerId)) {
            throw new SellerNotFoundException("Seller not found");
        }
        
        Page<Product> products = productRepository.findBySellerIdOrderByPublishedAtDesc(
            sellerId, 
            PageRequest.of(page, size)
        );
        
        logger.info("flow_stage=RESPONSE_PREPARED operation=product.seller_list sellerId={} count={} status=COMPLETED",
            sellerId, products.getNumberOfElements());
        
        return products;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<Product> getPublicProducts(ProductSearchCriteria criteria) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=product.public_search query={} page={} status=INITIATED",
            criteria.getSearchQuery(), criteria.getPage());
        
        Pageable pageable = PageRequest.of(
            criteria.getPage(),
            criteria.getSize(),
            Sort.by(Sort.Direction.fromString(criteria.getSortOrder()), criteria.getSortBy())
        );
        
        Page<Product> results = productRepository.searchPublicProducts(
            criteria.getSearchQuery(),
            criteria.getSellerId(),
            criteria.getPriceMin(),
            criteria.getPriceMax(),
            pageable
        );
        
        logger.info("flow_stage=RESPONSE_PREPARED operation=product.public_search count={} status=COMPLETED",
            results.getTotalElements());
        
        return results;
    }
}
```

**Outbound Adapter (hex-outbound-adapter-db):**

```java
// File: hex-outbound-adapter-db/src/main/java/com/example/user/adapter/ProductRepositoryAdapter.java
@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepositoryPort {
    
    private final ProductJpaRepository jpaRepository;
    
    @Override
    public Product save(Product product) {
        ProductEntity entity = toPersistence(product);
        ProductEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }
    
    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }
    
    @Override
    public Page<Product> findBySellerIdOrderByPublishedAtDesc(Long sellerId, Pageable pageable) {
        return jpaRepository.findBySellerIdOrderByPublishedAtDesc(sellerId, pageable)
            .map(this::toDomain);
    }
    
    @Override
    public Page<Product> searchPublicProducts(String query, Long sellerId, 
                                              BigDecimal priceMin, BigDecimal priceMax,
                                              Pageable pageable) {
        // Use custom query with only active products
        return jpaRepository.searchPublicProducts(query, sellerId, priceMin, priceMax, pageable)
            .map(this::toDomain);
    }
    
    private Product toDomain(ProductEntity entity) {
        return new Product(
            entity.getId(),
            entity.getSellerId(),
            entity.getName(),
            entity.getDescription(),
            entity.getPrice(),
            entity.getCurrency(),
            entity.getStockQuantity(),
            entity.getIsActive(),
            entity.getPublishedAt(),
            entity.getCreatedAt()
        );
    }
    
    private ProductEntity toPersistence(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.setId(product.getId());
        entity.setSellerId(product.getSellerId());
        entity.setName(product.getName());
        entity.setDescription(product.getDescription());
        entity.setPrice(product.getPrice());
        entity.setCurrency(product.getCurrency());
        entity.setStockQuantity(product.getStockQuantity());
        entity.setIsActive(product.getIsActive());
        entity.setPublishedAt(product.getPublishedAt());
        entity.setCreatedAt(product.getCreatedAt());
        return entity;
    }
}

// File: hex-outbound-adapter-db/src/main/java/com/example/user/adapter/ProductJpaRepository.java
@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
    
    // Query active products by seller
    Page<ProductEntity> findBySellerIdOrderByPublishedAtDesc(Long sellerId, Pageable pageable);
    
    // Custom query for public product search
    @Query("""
        SELECT p FROM ProductEntity p
        WHERE p.isActive = true
        AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                              OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:sellerId IS NULL OR p.sellerId = :sellerId)
        AND (:priceMin IS NULL OR p.price >= :priceMin)
        AND (:priceMax IS NULL OR p.price <= :priceMax)
        """)
    Page<ProductEntity> searchPublicProducts(
        @Param("query") String query,
        @Param("sellerId") Long sellerId,
        @Param("priceMin") BigDecimal priceMin,
        @Param("priceMax") BigDecimal priceMax,
        Pageable pageable
    );
}

// JPA Entity
@Entity
@Table(name = "products")
@Data
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long sellerId;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(length = 3)
    private String currency = "EUR";
    
    @Column(nullable = false)
    private Integer stockQuantity = 0;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime publishedAt = LocalDateTime.now();
    
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

**REST Controller (hex-inbound-adapter-web):**

```java
// File: hex-inbound-adapter-web/src/main/java/com/example/user/api/ProductControllerAdapter.java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@PreAuthorize("permitAll()")  // Some endpoints require auth, see each method
public class ProductControllerAdapter {
    
    private final CreateProductUseCase createProduct;
    private final UpdateProductUseCase updateProduct;
    private final DeleteProductUseCase deleteProduct;
    private final GetSellerProductsUseCase getSellerProducts;
    private final GetPublicProductsUseCase getPublicProducts;
    
    // Request/Response DTOs
    @Data
    public static class CreateProductRequest {
        @NotBlank(message = "Product name is required")
        @Length(max = 255)
        private String name;
        
        private String description;
        
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        private BigDecimal price;
        
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter code")
        private String currency = "EUR";
        
        @Min(0)
        private Integer initialStock = 0;
    }
    
    @PostMapping
    @PreAuthorize("hasRole('SELLER')")  // Requires authenticated seller
    public ResponseEntity<ProductResponse> createProduct(
        @AuthenticationPrincipal User seller,
        @Valid @RequestBody CreateProductRequest request
    ) {
        Product product = createProduct.createProduct(seller.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toResponse(product));
    }
    
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Long sellerId,
        @RequestParam(required = false) BigDecimal priceMin,
        @RequestParam(required = false) BigDecimal priceMax,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "published_at") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortOrder
    ) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
            page, size, query, sellerId, priceMin, priceMax, sortBy, sortOrder
        );
        Page<Product> results = getPublicProducts.getPublicProducts(criteria);
        return ResponseEntity.ok(results.map(this::toResponse));
    }
    
    @GetMapping("/seller/{sellerId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Page<ProductResponse>> getSellerProducts(
        @PathVariable Long sellerId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal User user
    ) {
        // Only seller can see their own products (including inactive)
        if (!user.getId().equals(sellerId) && !user.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        
        Page<Product> products = getSellerProducts.getSellerProducts(sellerId, page, size);
        return ResponseEntity.ok(products.map(this::toResponse));
    }
    
    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ProductResponse> updateProduct(
        @PathVariable Long productId,
        @AuthenticationPrincipal User seller,
        @Valid @RequestBody UpdateProductRequest request
    ) {
        Product updated = updateProduct.updateProduct(productId, seller.getId(), request);
        return ResponseEntity.ok(toResponse(updated));
    }
    
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> deleteProduct(
        @PathVariable Long productId,
        @AuthenticationPrincipal User seller
    ) {
        deleteProduct.deleteProduct(productId, seller.getId());
        return ResponseEntity.noContent().build();
    }
    
    private ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSellerId(product.getSellerId());
        response.setName(product.getName());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setIsActive(product.getIsActive());
        response.setPublishedAt(product.getPublishedAt());
        return response;
    }
}
```

**Exception Handler:**

```java
// File: hex-inbound-adapter-web/src/main/java/com/example/user/api/config/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
        logger.warn("Product not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(SellerNotProductOwnerException.class)
    public ResponseEntity<ErrorResponse> handleSellerNotOwner(SellerNotProductOwnerException ex) {
        logger.warn("Seller authorization failed: {}", ex.getMessage());
        // Return 404 (not 403) for security: don't reveal ownership
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PRODUCT_NOT_FOUND", "Product not found"));
    }
    
    @Data
    public static class ErrorResponse {
        String code;
        String message;
        LocalDateTime timestamp = LocalDateTime.now();
    }
}
```

---

#### **3. Commission & Transaction Tracking**

**What to build:**
- Record every transaction (buyer, seller, amount, commission)
- Calculate 5% commission automatically
- Track seller earnings and payouts

**Database Schema:**
```sql
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    buyer_id BIGINT NOT NULL REFERENCES users(id),
    seller_id BIGINT NOT NULL REFERENCES users(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL,
    product_price_at_purchase DECIMAL(10,2) NOT NULL,
    gross_amount DECIMAL(10,2) NOT NULL,  -- quantity * price
    platform_commission DECIMAL(10,2) NOT NULL,  -- 5% of gross (will be stored as 0.05 * gross)
    seller_payout DECIMAL(10,2) NOT NULL,  -- 95% of gross (0.95 * gross)
    payment_id BIGINT REFERENCES payments(id),
    status VARCHAR(50),  -- PENDING, COMPLETED, REFUNDED
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE seller_earnings (
    seller_id BIGINT PRIMARY KEY REFERENCES users(id),
    total_earned DECIMAL(10,2) DEFAULT 0,  -- sum of seller_payout where status=COMPLETED
    total_paid_out DECIMAL(10,2) DEFAULT 0,  -- sum of approved payouts
    pending_payout DECIMAL(10,2) DEFAULT 0,  -- total_earned - total_paid_out
    last_payout_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT NOW()
);
```

**New Domain Models (hex-core + hex-payment-core):**
- `Transaction` — records buyer/seller/product/amount/commission
- `SellerEarnings` — aggregated earnings per seller
- `CommissionCalculation` — value object (price → gross → commission → payout)

**New Ports (hex-payment-core):**
- `RecordTransactionPort` — after payment succeeds, record transaction
  - Input: buyerId, sellerId, productId, quantity, paymentId, priceAtPurchase
  - Output: Transaction
  - Business Logic:
    - Calculate gross = quantity * price
    - Commission = gross * 0.05
    - SellerPayout = gross * 0.95
    - Insert transaction record
    - Update seller_earnings table
    
- `CalculateCommissionPort` — pure calculation
  - Input: grossAmount
  - Output: {commission, payout}
  - Returns: {5%, 95%} split

**New Ports (hex-core):**
- `GetSellerEarningsPort` — seller views their balance
  - Input: sellerId
  - Output: SellerEarnings
  
- `GetPlatformRevenuePort` — admin views total commission
  - Input: dateRange (optional)
  - Output: {totalCommission, transactionCount, topSellers}

**Outbound Adapter (hex-outbound-adapter-db):**
- TransactionRepositoryAdapter
- SellerEarningsRepositoryAdapter

---

#### **4. Order Lifecycle Management**

**What to build:**
- Create orders (cart → checkout flow)
- Track order status (pending payment → paid → shipped → delivered)
- Support individual line items (future: multi-seller cart)

**Database Schema:**
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    buyer_id BIGINT NOT NULL REFERENCES users(id),
    seller_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(50) DEFAULT 'PENDING_PAYMENT',  -- PENDING_PAYMENT, PAID, SHIPPED, DELIVERED, CANCELLED
    total_gross DECIMAL(10,2) NOT NULL,
    total_commission DECIMAL(10,2) NOT NULL,
    total_seller_payout DECIMAL(10,2) NOT NULL,
    payment_id BIGINT REFERENCES payments(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL  -- quantity * unit_price
);
```

**New Domain Models (hex-core + hex-payment-core):**
- `Order` — buyer's order
- `OrderItem` — individual product line items
- `OrderStatus` enum — PENDING_PAYMENT, PAID, SHIPPED, DELIVERED, CANCELLED

**New Ports (hex-core):**
- `CreateOrderPort` — buyer creates order from cart
  - Input: buyerId, items: [{productId, quantity}...]
  - Output: Order
  - Business Logic:
    - Validate seller exists and product is_active
    - Validate stock availability
    - Create order with PENDING_PAYMENT status
    - Reserve stock (decrement temporarily)
    
- `GetOrderPort` — retrieve order details
  - Input: orderId, userId (validate ownership)
  - Output: Order + OrderItems
  - Access: Buyer sees own orders, Seller sees orders they received
  
- `CancelOrderPort` — cancel unpaid order
  - Input: orderId, userId
  - Output: void
  - Business Logic: Release reserved stock, set status=CANCELLED

**New Ports (hex-payment-core):**
- `ProcessOrderPaymentPort` — after payment succeeds
  - Input: orderId, paymentId
  - Output: void
  - Business Logic:
    - Update order.status = PAID
    - Confirm stock deduction (was temporary)
    - Record transaction for each order_item
    - Update seller_earnings
    
- `ShipOrderPort` — seller marks order as shipped
  - Input: orderId, sellerId
  - Output: Order
  - Access: Only seller who received order
  - Updates: status = SHIPPED, updated_at = NOW()
  
- `DeliverOrderPort` — buyer confirms delivery
  - Input: orderId, buyerId
  - Output: Order
  - Access: Only buyer who purchased
  - Updates: status = DELIVERED, updated_at = NOW()

**Outbound Adapter (hex-outbound-adapter-db):**
- OrderRepositoryAdapter
- OrderItemRepositoryAdapter

---

#### **5. Seller Payout System**

**What to build:**
- Sellers can request withdrawal of earnings
- Admin approves payouts
- Track payout history

**Database Schema:**
```sql
CREATE TABLE seller_payouts (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL REFERENCES users(id),
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    payout_method VARCHAR(50) NOT NULL,  -- BANK_TRANSFER, WIRE, PAYPAL, etc.
    request_reason TEXT,
    requested_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    transaction_reference VARCHAR(255)  -- external payout ID from payment processor
);
```

**New Domain Models:**
- `SellerPayout` — payout request record
- `PayoutStatus` enum — PENDING, PROCESSING, COMPLETED, FAILED
- `PayoutMethod` enum — BANK_TRANSFER, WIRE, PAYPAL, etc.

**New Ports (hex-core):**
- `RequestPayoutPort` — seller requests withdrawal
  - Input: sellerId, amount, payoutMethod
  - Output: SellerPayout
  - Business Logic:
    - Validate seller has pending_payout >= amount
    - Validate payout_method is valid
    - Create payout record with PENDING status
    - Lock funds from pending_payout
    
- `GetPayoutHistoryPort` — seller views past payouts
  - Input: sellerId, page, size
  - Output: PagedSellerPayouts

**New Ports (admin, hex-core):**
- `ApprovePayoutPort` — admin approves payout
  - Input: payoutId
  - Output: SellerPayout
  - Updates: status = PROCESSING
  
- `CompletePayoutPort` — mark as paid (after bank transfer succeeds)
  - Input: payoutId, transactionReference
  - Output: SellerPayout
  - Updates: status = COMPLETED, completed_at = NOW()
  - Updates: seller_earnings.total_paid_out += amount
  - Updates: seller_earnings.pending_payout -= amount

**Outbound Adapter:**
- SellerPayoutRepositoryAdapter

---

#### **6. Search & Discovery**

**What to build:**
- Full-text search on product name + description
- Filtering by price, seller, category
- Sorting by newest, price, popularity

**Enhancement to Existing:**
- Add to `ProductRepositoryAdapter`:
  ```sql
  SELECT * FROM products 
  WHERE is_active = true 
    AND (LOWER(name) LIKE ? OR LOWER(description) LIKE ?)
    AND seller_id = ? (optional)
    AND price BETWEEN ? AND ? (optional)
  ORDER BY published_at DESC
  LIMIT ? OFFSET ?;
  ```

**New Ports (hex-core):**
- `SearchProductsPort` — discover products
  - Input: query, filters: {sellerId?, priceMin?, priceMax?, sortBy?, page, size}
  - Output: PagedProducts
  - Supports: text search, filtering, pagination

---

#### **7. Rating & Review System (Phase 1.5)**

**Database Schema:**
```sql
CREATE TABLE product_reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    buyer_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE seller_reviews (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL REFERENCES users(id),
    buyer_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**New Domain Models:**
- `ProductReview` — product rating + comment
- `SellerReview` — seller service rating + comment

**New Ports:**
- `SubmitProductReviewPort` — buyer reviews product (after delivery)
  - Input: orderId, productId, rating, comment
  - Output: ProductReview
  - Validates: buyer owns order, order.status = DELIVERED
  
- `SubmitSellerReviewPort` — buyer reviews seller
  - Input: orderId, sellerId, rating, comment
  - Output: SellerReview
  
- `GetProductReviewsPort` — retrieve product reviews
  - Input: productId, page, size
  - Output: PagedProductReviews
  
- `GetSellerReviewsPort` — aggregate seller rating
  - Input: sellerId
  - Output: {avgRating, totalReviews, reviews: [...]}

---

### **FRONTEND ENHANCEMENTS**

#### **1. Seller Dashboard (New Page: `/seller`)**

**Purpose:** Central hub for sellers to manage their business

**Components to build:**

**`seller-home.ts`** — Dashboard overview
- 4 metric cards:
  1. Total Sales (this month) — number of orders
  2. Revenue Before Commission — sum of gross amounts
  3. Pending Payout — seller_earnings.pending_payout
  4. Average Rating — average of seller reviews
- Quick actions: "Add Product", "View Orders", "Request Payout"
- Recent orders list (last 5)
- Chart: Sales trend (last 30 days)

**`seller-products.ts`** — Product management
- Table of seller's products:
  - Product name
  - Price
  - Stock quantity
  - Status (Active/Inactive)
  - Sales count
  - Actions: Edit, Delete, View Analytics
- "Create New Product" button → form dialog
- Filter: Active/Inactive toggle
- Search: product name search

**`seller-orders.ts`** — Order management
- Table of incoming orders:
  - Order ID
  - Buyer name
  - Order date
  - Items (qty)
  - Total
  - Status (Pending Payment, Paid, Shipped, Delivered)
  - Actions: View Details, Mark Shipped
- Filter by status
- "Mark as Shipped" button → confirm + update status to SHIPPED
- Order details modal:
  - Buyer contact info
  - Item list + quantities
  - Total breakdown (gross, commission, payout)
  - Timeline: created → paid → shipped → delivered

**`seller-earnings.ts`** — Financial dashboard
- Summary card:
  - Total Earned (all-time)
  - Total Paid Out
  - Pending Payout (with "Request Payout" button)
- Commission transparency:
  - Show: "You keep 95%, platform takes 5%"
  - Example calculation visible
- Payout history table:
  - Date requested
  - Amount
  - Status (Pending, Processing, Completed, Failed)
  - Payment method
- "Request Payout" button → modal:
  - Amount input (validate: <= pending_payout)
  - Payment method dropdown (BANK_TRANSFER, PAYPAL, etc.)
  - Confirm button

**`seller-profile-settings.ts`** — Edit seller info
- Form fields:
  - Seller Display Name
  - Bio/Description
  - Avatar upload (optional)
- Save button
- Delete account option (with confirmation)

---

#### **2. Buyer Shopping Experience**

**Enhanced Products Page (`products-page.ts`):**
- Add seller information card (below product description):
  - Seller name (clickable → seller profile)
  - ⭐ Rating (avg) + review count (clickable → reviews modal)
  - "Contact Seller" button (future: messaging)
- Show stock status:
  - "In Stock" (green)
  - "Only 2 left" (orange) if stock < 5
  - "Out of Stock" (red) if stock = 0
- Replace "Buy Now" button with "Add to Cart" button
- Add to Cart success toast notification

**New Pages:**

**`cart-page.ts`** — Shopping cart
- Cart items table:
  - Product name + seller name
  - Unit price
  - Quantity selector (with +/- buttons)
  - Subtotal
  - Remove button
- Cart summary card:
  - Subtotal
  - Estimated total (no shipping in MVP)
  - "Proceed to Checkout" button
  - "Continue Shopping" button
- Empty cart message if no items
- Session storage: handle cart persistence in localStorage initially (upgrade to DB in Phase 2)

**`checkout-page.ts`** — Purchase flow
- 3-step process:
  1. **Review Order**
     - Items recap (product, seller, qty, price)
     - Subtotal
     - Commission transparency: "No hidden fees" or "Platform fee included"
  2. **Payment Method**
     - Radio buttons: Credit Card, PayPal, Bank Transfer, iDEAL
     - Reuse existing payment form component
  3. **Confirm & Pay**
     - Final summary
     - "Place Order" button
     - Redirects to payment provider
- Success page:
  - "Order Confirmed!" message
  - Order ID
  - "View Order Status" button → order tracking

**`order-history-page.ts`** — Track purchases
- Orders list:
  - Order ID
  - Date
  - Seller name(s)
  - Items count
  - Total
  - Status badge (Pending Payment, Paid, Shipped, Delivered)
  - Actions: View Details, Download Invoice, Leave Review (if DELIVERED)
- Order details modal:
  - Full order summary
  - Timeline: order created → payment confirmed → shipped → delivered
  - Item list with individual seller breakdown
  - Leave review button (if eligible)

**`product-reviews-modal.ts`** — View/submit reviews
- Reviews feed (paginated):
  - Buyer name (optional)
  - ⭐ Rating
  - Comment
  - Date posted
- "Submit Review" button (if user purchased):
  - Rating selector (1-5 stars)
  - Comment textarea
  - Submit button

---

#### **3. Seller Profile Page (Public: `/sellers/{sellerId}`)**

**New Component: `seller-profile.ts`**
- Header section:
  - Seller name
  - ⭐ Overall rating (e.g., "4.7 out of 5 stars")
  - Total reviews count
  - Joined date
  - Bio/description
- Tabs:
  1. **Products** (featured products grid)
     - Show active products only
     - Filter/sort options
  2. **Reviews** (customer feedback)
     - Paginated seller reviews
     - Filter by rating
  3. **Contact** (future: messaging button)
- "Contact Seller" button (future phase)

---

#### **4. Product Discovery Enhancement**

**Upgrade Products Page with filters:**
- Search box (top):
  - Real-time search on product name/description
  - Autocomplete suggestions
- Left sidebar filters:
  - **Price Range:**
    - Slider from min to max available prices
    - Or text inputs for custom range
  - **Seller Filter:**
    - Dropdown or autocomplete
  - **Rating Filter:**
    - Checkboxes: 4+ stars, 3+ stars, etc.
  - **Stock Status:**
    - In Stock / Only Few Left / Out of Stock
- Right side: products grid
- Sort dropdown (top-right):
  - Newest (published_at DESC)
  - Price: Low to High
  - Price: High to Low
  - Best Rated (avg rating DESC)
  - Most Purchases (sales count DESC)

---

#### **5. Become a Seller Flow**

**New Dialog Component: `become-seller-dialog.ts`**
- Steps:
  1. **Welcome**
     - "Start selling on our platform"
     - Key benefits listed
  2. **Terms & Conditions**
     - Checkbox: "I agree to Terms"
     - Link to full T&C
  3. **Seller Information**
     - Seller Display Name input
     - Bio textarea
     - Avatar upload (optional)
  4. **Confirmation**
     - Summary of info
     - "Submit" button
- Success message:
  - "Congratulations! You're now a seller!"
  - "Go to Seller Dashboard" button
- Accessible via:
  - Home page dropdown → "Become a Seller"
  - Navigation menu for non-sellers

---

#### **6. Navigation Updates**

**Main Navigation Bar:**
- For logged-in non-sellers:
  - Home | Products | Cart | Orders | Profile | "Become a Seller" (button)
  
- For logged-in sellers:
  - Home | Products | Cart | Orders | **Seller Dashboard** | Profile

**Header dropdown (user profile):**
- View Profile
- My Orders
- (If seller) Seller Dashboard
- Become a Seller (if not seller)
- Logout

---

## 🏗️ **HEXAGONAL ARCHITECTURE MAPPING**

```
hex-core/
├── model/
│   ├── User (enhanced)
│   │   ├── isSeller: boolean
│   │   ├── sellerDisplayName: String
│   │   ├── sellerBio: String
│   │   └── sellerRating: BigDecimal
│   ├── Seller (NEW)
│   ├── Product (enhanced)
│   │   ├── sellerId: Long
│   │   ├── isActive: boolean
│   │   └── publishedAt: LocalDateTime
│   ├── Order (NEW)
│   ├── OrderItem (NEW)
│   ├── Transaction (NEW)
│   ├── SellerEarnings (NEW)
│   ├── ProductReview (NEW)
│   └── SellerReview (NEW)
├── port/
│   └── in/
│       ├── EnableSellerPort
│       ├── CreateProductAsSellerPort
│       ├── UpdateProductAsSellerPort
│       ├── DeleteProductAsSellerPort
│       ├── GetSellerProductsPort
│       ├── GetPublicProductsPort
│       ├── CreateOrderPort
│       ├── GetOrderPort
│       ├── CancelOrderPort
│       ├── SearchProductsPort
│       ├── GetSellerEarningsPort
│       ├── RequestPayoutPort
│       ├── GetPayoutHistoryPort
│       ├── GetSellerProfilePort
│       ├── SubmitProductReviewPort
│       ├── GetProductReviewsPort
│       ├── SubmitSellerReviewPort
│       ├── GetSellerReviewsPort
│       └── GetSellerRecommendationsPort (future)

hex-payment-core/
├── model/
│   ├── Payment (enhanced)
│   ├── PaymentSplit (NEW)
│   │   ├── grossAmount: BigDecimal
│   │   ├── commission: BigDecimal (5%)
│   │   └── payout: BigDecimal (95%)
│   ├── OrderPayment (NEW)
│   └── TransactionRecord (NEW)
├── port/
│   └── in/
│       ├── ProcessOrderPaymentPort
│       ├── CalculateCommissionPort
│       ├── ShipOrderPort
│       ├── DeliverOrderPort
│       ├── ApprovePayoutPort
│       └── CompletePayoutPort

hex-inbound-adapter-web/
├── api/
│   ├── SellerControllerAdapter (NEW) → /api/v1/sellers
│   │   ├── POST /enable (enable seller)
│   │   ├── GET /{sellerId} (get profile)
│   │   ├── PUT /{sellerId} (update profile)
│   │   └── DELETE /{sellerId} (optional)
│   ├── ProductControllerAdapter (enhanced) → /api/v1/products
│   │   ├── GET / (get all active products, with search/filter)
│   │   ├── GET /search (search endpoint)
│   │   ├── POST / (create as seller)
│   │   ├── PUT /{id} (update as seller)
│   │   ├── DELETE /{id} (delete as seller)
│   │   └── GET /seller/{sellerId} (get seller's products)
│   ├── OrderControllerAdapter (NEW) → /api/v1/orders
│   │   ├── POST / (create order)
│   │   ├── GET /{id} (get order)
│   │   ├── POST /{id}/cancel (cancel order)
│   │   ├── GET (list buyer's orders)
│   │   ├── POST /{id}/ship (seller ships)
│   │   └── POST /{id}/deliver (buyer confirms)
│   ├── CartControllerAdapter (NEW) → /api/v1/cart
│   │   ├── GET / (get cart)
│   │   ├── POST /items (add item)
│   │   ├── PUT /items/{id} (update qty)
│   │   └── DELETE /items/{id} (remove)
│   ├── TransactionControllerAdapter (NEW) → /api/v1/transactions
│   │   ├── GET (list transactions, admin only)
│   │   └── GET /{id} (get transaction details)
│   ├── SellerEarningsControllerAdapter (NEW) → /api/v1/sellers/{sellerId}/earnings
│   │   ├── GET / (get seller earnings)
│   │   ├── GET /history (payout history)
│   │   └── POST /payout-request (request payout)
│   ├── ReviewControllerAdapter (NEW) → /api/v1/reviews
│   │   ├── POST /products (submit product review)
│   │   ├── POST /sellers (submit seller review)
│   │   ├── GET /products/{id} (get product reviews)
│   │   └── GET /sellers/{id} (get seller reviews)
│   └── AdminPayoutControllerAdapter (NEW) → /api/v1/admin/payouts
│       ├── GET / (list payouts)
│       ├── POST /{id}/approve (approve payout)
│       └── POST /{id}/complete (mark complete)

hex-inbound-adapter-payment-web/
└── (existing payment endpoints unchanged)

hex-outbound-adapter-db/
├── SellerRepositoryAdapter (NEW)
├── OrderRepositoryAdapter (NEW)
├── OrderItemRepositoryAdapter (NEW)
├── TransactionRepositoryAdapter (NEW)
├── SellerEarningsRepositoryAdapter (NEW)
├── SellerPayoutRepositoryAdapter (NEW)
├── ProductReviewRepositoryAdapter (NEW)
├── SellerReviewRepositoryAdapter (NEW)
└── EnhancedProductRepositoryAdapter (updated)

frontend/
├── src/pages/
│   ├── seller-dashboard-page.ts (NEW)
│   ├── seller-products-page.ts (NEW)
│   ├── seller-orders-page.ts (NEW)
│   ├── seller-earnings-page.ts (NEW)
│   ├── seller-settings-page.ts (NEW)
│   ├── seller-profile-page.ts (NEW, public)
│   ├── cart-page.ts (NEW)
│   ├── checkout-page.ts (NEW)
│   ├── order-history-page.ts (NEW)
│   └── products-page.ts (enhanced)
├── src/components/
│   ├── seller-profile.ts (NEW)
│   ├── product-card.ts (enhanced)
│   ├── review-card.ts (NEW)
│   └── become-seller-dialog.ts (NEW)
└── src/api/
    ├── sellers.ts (NEW)
    ├── orders.ts (NEW)
    ├── carts.ts (NEW)
    ├── reviews.ts (NEW)
    └── products.ts (enhanced)
```

---

## 💾 **DATABASE MIGRATIONS**

**New migration files to create:**

```
V11__add_seller_capabilities_to_users.sql
V12__enhance_products_with_seller_and_status.sql
V13__create_orders_and_order_items_tables.sql
V14__create_transactions_table.sql
V15__create_seller_earnings_table.sql
V16__create_seller_payouts_table.sql
V17__create_product_reviews_table.sql
V18__create_seller_reviews_table.sql
```

---

## 🎯 **RECOMMENDED START: MVP Implementation - DETAILED STEP-BY-STEP**

### **Pre-Implementation Checklist**

- [ ] **Code Review Process:** Establish peer review for hexagonal boundaries
- [ ] **Database Backups:** Ensure backup strategy in place before migrations
- [ ] **Feature Flags:** Set up feature flags for gradual rollout (Seller feature disabled by default)
- [ ] **Monitoring:** Configure Splunk dashboards for seller operations
- [ ] **Test Data:** Prepare seed data (test sellers, products, orders)
- [ ] **Documentation:** Update API documentation (Swagger/OpenAPI)

---

### **Week 1: Backend Foundation (40 hours)**

#### **Day 1: Database Design & Migrations (8 hours)**

**Tasks:**

```yaml
1. Create Migration Files (1 hour)
   Files:
   - V11__add_seller_capabilities_to_users.sql
   - V12__enhance_products_with_seller_and_status.sql
   - V13__create_orders_and_order_items_tables.sql
   - V14__create_transactions_table.sql
   - V15__create_seller_earnings_table.sql
   - V16__create_seller_payouts_table.sql
   - V17__create_product_reviews_table.sql
   - V18__create_seller_reviews_table.sql
   
   Each migration should:
   - Use IF NOT EXISTS for idempotency
   - Include proper constraints (FK, CHECK, NOT NULL)
   - Create indices for performance
   - Add comments explaining business rules

2. Local Database Testing (2 hours)
   Steps:
   - Run migrations locally: mvn flyway:migrate -Dflyway.configFiles=...
   - Verify schema: SELECT * FROM information_schema.tables WHERE table_schema='public'
   - Test rollback: mvn flyway:undo (if using commercial Flyway)
   - Verify data integrity with manual queries
   - Document any data migration scripts needed

3. Create JPA Entity Classes (3 hours)
   Files:
   - hex-outbound-adapter-db/.../entity/SellerEarningsEntity.java
   - hex-outbound-adapter-db/.../entity/OrderEntity.java
   - hex-outbound-adapter-db/.../entity/OrderItemEntity.java
   - hex-outbound-adapter-db/.../entity/TransactionEntity.java
   - Enhance ProductEntity with seller_id, is_active, published_at
   
   Validation checklist:
   - All BigDecimal fields use @Column(precision=10, scale=2)
   - All timestamps use @Temporal(TemporalType.TIMESTAMP)
   - FK constraints match database
   - @Index annotations on frequently queried columns

4. Update JPA Repositories (2 hours)
   Methods needed:
   - findBySellerIdOrderByPublishedAtDesc(Long sellerId, Pageable)
   - findByBuyerIdAndStatusOrderByCreatedAtDesc(Long buyerId, OrderStatus)
   - findBySellerIdAndStatusOrderByCreatedAtDesc(Long sellerId, OrderStatus)
   - Custom @Query for transaction search with filters

**Verification:**
- [ ] All 8 migrations run without errors
- [ ] Schema matches expected design (check column names, types, constraints)
- [ ] Indices created successfully
- [ ] Run: mvn -B clean test-compile (should compile without errors)
```

#### **Days 2-3: Domain Models & Ports (12 hours)**

**Tasks:**

```yaml
1. Create Domain Models (5 hours)
   Files (all under hex-core/.../model/):
   - Seller.java (wrapper for seller profile)
   - SellerProfile.java (public view)
   - SellerRegistration.java (value object for onboarding)
   - Order.java (order aggregate root)
   - OrderItem.java (value object)
   - OrderStatus.java (enum)
   - Transaction.java (financial record)
   - SellerEarnings.java (seller balance tracking)
   - CommissionCalculation.java (value object with business rules)
   - SellerPayout.java (payout request)
   - PayoutStatus.java (enum)
   
   For each model:
   - Add Javadoc comments
   - Define validation in constructor
   - Include factory methods (e.g., createNew, from)
   - Implement equals/hashCode for value objects
   - Add business logic methods (isOwnedBy, isAvailable, etc.)

2. Create Input Port Interfaces (4 hours)
   Files (all under hex-core/.../port/in/):
   - EnableSellerUseCase.java
   - GetSellerProfileUseCase.java
   - UpdateSellerProfileUseCase.java
   - CreateProductUseCase.java
   - UpdateProductUseCase.java
   - DeleteProductUseCase.java
   - GetSellerProductsUseCase.java
   - GetPublicProductsUseCase.java
   - CreateOrderUseCase.java
   - GetOrderUseCase.java
   - CancelOrderUseCase.java
   - GetSellerEarningsUseCase.java
   - RequestPayoutUseCase.java
   
   For each interface:
   - Write comprehensive Javadoc
   - Define exceptions that can be thrown
   - Document validation rules
   - Specify authorization rules (who can call)

3. Create Output Port Interfaces (2 hours)
   Files (all under hex-core/.../port/out/):
   - SellerRepositoryPort.java
   - ProductRepositoryPort.java
   - OrderRepositoryPort.java
   - TransactionRepositoryPort.java
   - SellerPayoutRepositoryPort.java
   
   Note: These will be implemented by adapters

4. Create Custom Exceptions (1 hour)
   Files (all under hex-core/.../exception/):
   - SellerException.java
   - SellerNotFoundException.java
   - ProductNotFoundException.java
   - SellerNotProductOwnerException.java
   - InsufficientStockException.java
   - PayoutException.java

**Verification:**
- [ ] All domain models compile without errors
- [ ] All ports defined with clear contracts
- [ ] Javadoc coverage ≥ 90%
- [ ] Run: mvn -B clean test-compile
```

#### **Day 4: Application Services & Unit Tests (10 hours)**

**Tasks:**

```yaml
1. Implement Service Classes (5 hours)
   Files (all under hex-application/.../application/):
   - EnableSellerService implements EnableSellerUseCase
   - ProductService implements Create/Update/Delete/Get product use cases
   - OrderService implements order-related use cases
   - TransactionService for recording transactions
   
   Requirements:
   - @Service with @Transactional
   - Constructor injection of ports
   - Structured logging with flow_stage
   - Exception handling (throw domain exceptions, not runtime)
   - Business logic validation
   
   Example methods:
   - enableSeller(userId, registration): SellerProfile
   - createProduct(sellerId, request): Product
   - createOrder(buyerId, items): Order
   - processPayment(orderId, payment): void

2. Write Unit Tests (4 hours)
   Test coverage:
   - Domain model tests (value object validation)
   - Service logic tests (happy path + edge cases)
   - Exception handling tests
   
   Test template:
   ```java
   @DisplayName("ServiceName Tests")
   class ServiceNameTest {
       @BeforeEach void setup() { ... }
       
       @Test @DisplayName("should do X") void shouldDoX() {
           // Given
           // When
           // Then
       }
   }
   ```
   
   Minimum test cases:
   - [ ] Happy path (service completes successfully)
   - [ ] Validation failure (invalid input)
   - [ ] Authorization failure (user not allowed)
   - [ ] Resource not found (throws 404)
   - [ ] Idempotence (calling twice has same effect)

3. Review & Refactor (1 hour)
   - Check for code duplication
   - Verify naming conventions (camelCase for methods, PascalCase for classes)
   - Ensure all TODOs are converted to issues
   - Code formatting: run `mvn fmt:format`

**Verification:**
- [ ] All services compile without errors
- [ ] Unit test coverage ≥ 80% for services
- [ ] All tests pass: mvn -B test -Dgroups=unit
```

#### **Day 5: Outbound Adapters & Integration Tests (10 hours)**

**Tasks:**

```yaml
1. Implement Repository Adapters (4 hours)
   Files (all under hex-outbound-adapter-db/.../adapter/):
   - SellerRepositoryAdapter
   - ProductRepositoryAdapter
   - OrderRepositoryAdapter
   - TransactionRepositoryAdapter
   - SellerPayoutRepositoryAdapter
   
   Each adapter should:
   - Implement corresponding port interface
   - Convert domain objects ↔ JPA entities (mappers)
   - Use custom @Query for complex searches
   - Handle null cases gracefully
   - Log database operations (debug level)

2. Write Integration Tests (4 hours)
   Test containers:
   - Use @SpringBootTest(webEnvironment = NONE)
   - Use @Testcontainers with PostgreSQL
   - @Transactional to rollback after each test
   
   Test scenarios:
   - Create entity and retrieve
   - Update entity fields
   - Search with filters
   - Soft delete (mark inactive)
   - Pagination
   - FK constraint violation (expect exception)

3. Database Performance Check (2 hours)
   - Verify indices created: SELECT * FROM pg_indexes WHERE tablename='orders'
   - Test query plans: EXPLAIN ANALYZE SELECT ...
   - Ensure no full table scans on large tables
   - Document slow queries (>200ms) and optimize

**Verification:**
- [ ] All adapters compile and implement contracts
- [ ] Integration tests pass: mvn -B verify -Dgroups=integration
- [ ] Code coverage reported by Jacoco
- [ ] No database constraint violations
```

---

### **Week 2: API & Frontend (30 hours)**

#### **Days 1-2: REST Controllers & Exception Handling (10 hours)**

**Tasks:**

```yaml
1. Implement REST Controllers (5 hours)
   Files (all under hex-inbound-adapter-web/.../api/):
   - SellerControllerAdapter
   - ProductControllerAdapter (enhanced)
   - OrderControllerAdapter (NEW)
   - TransactionControllerAdapter
   - SellerEarningsControllerAdapter
   - ReviewControllerAdapter
   
   Endpoints to create:
   POST   /api/v1/sellers/enable
   GET    /api/v1/sellers/{sellerId}
   PUT    /api/v1/sellers/{sellerId}
   POST   /api/v1/products              (seller)
   GET    /api/v1/products/search       (public)
   PUT    /api/v1/products/{id}         (seller)
   DELETE /api/v1/products/{id}         (seller)
   POST   /api/v1/orders                (buyer)
   GET    /api/v1/orders/{id}           (buyer/seller)
   GET    /api/v1/orders                (buyer)
   POST   /api/v1/orders/{id}/cancel    (buyer)
   
   Best practices:
   - Use @RequestBody for JSON
   - Use @PathVariable for path params
   - Use @RequestParam for query params
   - Always have @Valid on request DTOs
   - Return appropriate HTTP status codes (201 for POST, etc.)
   - Include @ApiOperation and @ApiResponse for Swagger

2. Define Request/Response DTOs (2 hours)
   Pattern:
   - Request: @Data, @NoArgsConstructor, validation annotations
   - Response: @Data, @NoArgsConstructor, include all public fields
   - Never expose internal IDs or passwords
   - Always include timestamps in responses
   
   Example DTOs:
   CreateSellerRequest { displayName: String, bio: String }
   SellerResponse { id, displayName, rating, joinedAt }

3. Implement Exception Handler (2 hours)
   @RestControllerAdvice for global exception mapping:
   - SellerNotFoundException → 404
   - ProductNotFoundException → 404
   - ValidationException → 400 with field errors
   - AccessDeniedException → 403
   - Exception → 500 (never expose stack trace)
   
   Response format:
   {
     "code": "PRODUCT_NOT_FOUND",
     "message": "Product not found",
     "timestamp": "2026-05-17T10:30:00Z",
     "field_errors": {...}  // for 400 responses
   }

4. Add Structured Logging (1 hour)
   For each endpoint:
   - REQUEST_RECEIVED: log input params, user ID
   - DOMAIN_OPERATION_COMPLETED: log IDs and key results
   - RESPONSE_PREPARED: log response status
   - EXCEPTION: log stack trace (ERROR level only)

**Verification:**
- [ ] All controllers compile without errors
- [ ] Endpoints respond with correct HTTP status codes
- [ ] Request validation works (send invalid data, expect 400)
- [ ] Exception handling works (send bad ID, expect 404)
- [ ] Swagger docs generated: curl http://localhost:8080/v3/api-docs
```

#### **Days 3-4: Frontend Pages (12 hours)**

**Tasks (TypeScript/Web Components):**

```yaml
1. Build Cart Page (4 hours)
   File: frontend/src/pages/cart-page.ts
   - Component extends LitElement
   - localStorage for persistence
   - Import API client for cart operations
   - Features:
     * Display cart items in table
     * +/- buttons for quantity
     * Remove button per item
     * Summary card with subtotal
     * "Proceed to Checkout" button
     * Empty state when no items
   
   Template:
   <cart-page>
     <div class="cart-items">
       <table>
         <tr *ngFor="let item of cartItems">
           <td>{{item.productName}}</td>
           <td><input [(ngModel)]="item.quantity"></td>
           <td>{{item.subtotal | currency}}</td>
           <td><button (click)="remove(item)">Remove</button></td>
         </tr>
       </table>
     </div>
     <div class="summary">
       <strong>Subtotal:</strong> {{cart.subtotal | currency}}
       <button (click)="proceedToCheckout()">Proceed to Checkout</button>
     </div>
   </cart-page>

2. Build Checkout Flow (4 hours)
   File: frontend/src/pages/checkout-page.ts
   - 3-step flow (form wizard)
   - Step 1: Review & confirm items
   - Step 2: Select payment method
   - Step 3: Enter payment details
   - Success confirmation with order ID
   
   State management:
   - currentStep: 1-3
   - selectedPaymentMethod: enum
   - order data (cached from cart)
   
   After payment success:
   - Call API to create order
   - Record transaction
   - Clear cart
   - Show confirmation

3. Build Order History Page (4 hours)
   File: frontend/src/pages/order-history-page.ts
   - Fetch user's orders from API: GET /api/v1/orders
   - Display orders table:
     * Order ID
     * Date
     * Seller names
     * Total
     * Status badge
   - Click to expand order details modal
   - "Leave Review" button (if delivered)
   
   API integration:
   - GET /api/v1/orders?page=0&size=20
   - GET /api/v1/orders/{orderId}
   - Pagination: previous/next buttons

**Verification:**
- [ ] Cart page compiles with TypeScript strict mode
- [ ] Checkout flow can create order (call backend API)
- [ ] Order history fetches and displays orders
- [ ] Error states handled (show error message for failed API calls)
```

#### **Day 5: Seller Dashboard & Testing (8 hours)**

**Tasks:**

```yaml
1. Build Seller Home Dashboard (4 hours)
   File: frontend/src/pages/seller-dashboard-page.ts
   - 4 metric cards:
     * Total Sales (this month): number of orders
     * Revenue Before Commission: SUM(order.totalGross)
     * Pending Payout: SellerEarnings.pending_payout
     * Average Rating: AVG(seller_reviews.rating)
   - Recent orders table (last 5)
   - Sales trend chart (last 30 days)
   
   API calls:
   - GET /api/v1/sellers/{sellerId}/earnings
   - GET /api/v1/sellers/{sellerId}/products?status=active
   - Custom endpoint for dashboard metrics (or aggregate in frontend)

2. Build Seller Products Page (2 hours)
   File: frontend/src/pages/seller-products-page.ts
   - Table of seller's products:
     * Product name
     * Price
     * Stock
     * Status (active/inactive)
     * Actions (edit, delete)
   - "Create New Product" button → form dialog
   - Filter: active/inactive toggle
   - Search: product name
   
   Features:
   - Edit button → pre-fill form, save updates
   - Delete button → confirm, soft-delete
   - Create form validation (required fields, price > 0)

3. Test Coverage (2 hours)
   - Run e2e tests: npx playwright test
   - Manual testing: create order → checkout → confirm
   - Check Splunk logs: verify flow_stage logs appear
   - Performance: check bundle size (npm run build)

**Verification:**
- [ ] Seller dashboard displays metrics correctly
- [ ] Products page allows CRUD operations
- [ ] Cart → Checkout → Order flow completes
- [ ] All errors display user-friendly messages
- [ ] Frontend tests pass: npm test
```

---

### **End of Phase 1: Verification Checklist**

Before moving to Phase 2:

```yaml
Full Build & Test Suite:
  - [ ] mvn -B clean verify (all modules)
  - [ ] npm test (frontend)
  - [ ] npm run build (no warnings)
  - [ ] Docker build succeeds
  - [ ] Security check: mvn owasp:check
  
API Verification:
  - [ ] All endpoints documented in Swagger
  - [ ] Manual Postman tests for order flow
  - [ ] Error responses formatted consistently
  - [ ] Logging appears in console/Splunk
  
Database Verification:
  - [ ] All migrations run successfully
  - [ ] Schema matches design
  - [ ] Indices created and query plans optimized
  - [ ] Foreign key constraints validated
  
Frontend Verification:
  - [ ] Seller can list products
  - [ ] Buyer can add to cart
  - [ ] Checkout flow completes
  - [ ] Order appears in history
  
Documentation:
  - [ ] API endpoints documented
  - [ ] Domain models documented (Javadoc)
  - [ ] Database schema documented
  - [ ] Deployment instructions updated
```

---

## 🧪 **TESTING STRATEGIES**

### **Unit Tests (hex-core)**

**Target Coverage:** ≥ 80% of service and domain classes

```java
// Test category: Domain Model Validation
@Test void shouldRejectInvalidProductName() { }         // Empty name
@Test void shouldRejectNegativePrice() { }             // Price ≤ 0
@Test void shouldRejectNegativeStock() { }             // Stock < 0

// Test category: Business Logic
@Test void shouldCalculateCommissionCorrectly() {      // 5% of gross
    BigDecimal gross = new BigDecimal("100.00");
    BigDecimal commission = CommissionCalculation.calculateCommission(gross);
    assertThat(commission).isEqualByComparingTo(new BigDecimal("5.00"));
}

@Test void shouldRejectOrderWithInsufficientStock() { } // Stock validation
@Test void shouldCancelOrderAndReleaseStock() { }       // Stock release

// Test category: Authorization
@Test void shouldRejectUpdateByNonOwner() { }           // Seller not owner
@Test void shouldRejectBuyerAccessToSellerOrder() { }   // Buyer sees own only
```

**Running Unit Tests:**
```bash
# Run all unit tests
mvn -B test -Dgroups=unit

# Run specific test class
mvn -B test -Dtest=EnableSellerServiceTest

# Run with coverage
mvn -B test jacoco:report
open target/site/jacoco/index.html
```

---

### **Integration Tests (hex-outbound-adapter-db)**

**Test Environment:** PostgreSQL in Docker (via Testcontainers)

```java
@SpringBootTest(webEnvironment = NONE)
@Testcontainers
@DisplayName("Order Repository Integration Tests")
class OrderRepositoryAdapterIT {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @BeforeEach
    void setup() {
        // Run migrations
        flywayMigration();
        // Seed test data
    }
    
    @Test
    @Transactional
    @DisplayName("should create and retrieve order")
    void shouldCreateAndRetrieveOrder() {
        // Given: seller, buyer, product
        // When: create order
        // Then: order persisted and retrievable
    }
    
    @Test
    @DisplayName("should enforce FK constraint on seller_id")
    void shouldEnforceForeignKeyConstraint() {
        // When: insert order with non-existent seller_id
        // Then: throw DataIntegrityViolationException
    }
}
```

**Running Integration Tests:**
```bash
# Run all integration tests (requires Docker)
mvn -B verify -Dgroups=integration

# Skip integration tests if no Docker
mvn -B clean install -DskipITs
```

---

### **API/Controller Tests (hex-inbound-adapter-web)**

**Test Environment:** MockMvc with @WebMvcTest

```java
@WebMvcTest(SellerControllerAdapter.class)
@DisplayName("Seller API Controller Tests")
class SellerControllerAdapterTest {
    
    @Autowired private MockMvc mockMvc;
    @MockBean private EnableSellerUseCase enableSellerUseCase;
    
    @Test
    @DisplayName("should return 201 when seller enabled successfully")
    void shouldReturn201OnSuccess() throws Exception {
        // Given
        EnableSellerRequest request = new EnableSellerRequest("My Store", "bio");
        SellerProfile profile = SellerProfile.builder()
            .sellerId(1L)
            .displayName("My Store")
            .build();
        when(enableSellerUseCase.enableSeller(anyLong(), any()))
            .thenReturn(profile);
        
        // When
        mockMvc.perform(post("/api/v1/sellers/enable")
            .with(user("user1"))
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            
        // Then
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sellerId").value(1))
            .andExpect(jsonPath("$.displayName").value("My Store"));
    }
    
    @Test
    @DisplayName("should return 400 for invalid displayName")
    void shouldReturn400ForInvalidDisplayName() throws Exception {
        // Given: displayName too short
        EnableSellerRequest request = new EnableSellerRequest("AB", "bio");
        
        // When/Then
        mockMvc.perform(post("/api/v1/sellers/enable")
            .with(user("user1"))
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
```

---

### **End-to-End Tests (Frontend)**

**Test Framework:** Playwright

```typescript
// File: frontend/e2e/marketplace.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Marketplace Order Flow', () => {
  
  test('buyer can add product to cart and checkout', async ({ page }) => {
    // Given: logged in buyer, products available
    await page.goto('/');
    await login(page, 'buyer@test.com', 'password');
    
    // When: browse products
    await page.goto('/products');
    const productCard = page.locator('[data-testid="product-card"]').first();
    await expect(productCard).toContainText('Product Name');
    
    // And: add to cart
    await productCard.locator('button[name="add-to-cart"]').click();
    await expect(page).toHaveURL('/cart');
    
    // And: proceed to checkout
    await page.locator('button[name="checkout"]').click();
    
    // And: enter payment
    await page.fill('[name="card-number"]', '4111111111111111');
    await page.fill('[name="expiry"]', '12/25');
    await page.click('button[name="place-order"]');
    
    // Then: order created
    await expect(page).toHaveURL('/order/**');
    await expect(page.locator('[data-testid="order-confirmed"]')).toBeVisible();
    const orderId = await page.locator('text=Order #').textContent();
    console.log(`Order created: ${orderId}`);
  });
  
  test('seller can list products in dashboard', async ({ page }) => {
    // Given: logged in seller
    await page.goto('/');
    await login(page, 'seller@test.com', 'password');
    
    // When: navigate to seller dashboard
    await page.click('[data-testid="seller-dashboard-link"]');
    
    // Then: see products
    await expect(page.locator('[data-testid="product-table"]')).toBeVisible();
    const rows = page.locator('[data-testid="product-table"] tbody tr');
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
  });
});
```

**Running E2E Tests:**
```bash
# Install browsers
npx playwright install

# Run tests
npx playwright test

# Run in headed mode (visible browser)
npx playwright test --headed

# Debug single test
npx playwright test marketplace.spec.ts:12 --debug
```

---

## 🔒 **SECURITY & VALIDATION CHECKLIST**

### **Database Level**

- [ ] All FK constraints with ON DELETE RESTRICT (prevent accidental deletes)
- [ ] CHECK constraints for enums (status IN (...))
- [ ] NOT NULL on required fields
- [ ] UNIQUE constraints (seller_display_name, email)
- [ ] Indexes on foreign keys (for join performance)

### **Application Level**

- [ ] Input validation on all request DTOs (@Valid, @NotNull, @Pattern, etc.)
- [ ] Authorization checks (verify user owns resource before updating)
- [ ] Seller verification (is_seller=true before creating products)
- [ ] Stock validation (sufficient quantity before deducting)
- [ ] Commission calculation (always 5%, never configurable in MVP)

### **API Security**

- [ ] Authentication required for all seller endpoints (@PreAuthorize)
- [ ] CORS configured: allow frontend origin only
- [ ] Rate limiting on payment endpoints (to prevent brute force)
- [ ] Input size limits (prevent large payloads)
- [ ] SQL injection prevention (use parameterized queries / JPA)

### **Dangerous Patterns to AVOID**

```java
// ❌ DON'T: Hardcoded commission in calculation
commission = amount * 5 / 100;  // Brittle, non-reusable

// ✅ DO: Use value object
BigDecimal commission = CommissionCalculation.calculateCommission(amount);

// ❌ DON'T: Return 403 when resource not found (leaks ownership info)
throw new AccessDeniedException("You don't own this product");

// ✅ DO: Return 404 always (security: don't reveal ownership)
throw new ProductNotFoundException("Product not found");

// ❌ DON'T: Calculate commission asynchronously (race conditions)
payment.complete();
updateSellerEarningsLater();  // May never run if app crashes

// ✅ DO: Calculate as part of transaction
@Transactional
void processPayment() {
    payment.complete();
    transaction.record();
    earnings.update();
}
```

---

## 📦 **DEPENDENCY MANAGEMENT**

### **New Dependencies to Add (pom.xml)**

```xml
<!-- Already present, verify versions: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <version>${spring-boot.version}</version>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.0</version>
</dependency>

<!-- New: For validation -->
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>

<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
</dependency>

<!-- New: For testing -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>

<!-- New: For mapping (optional, if using MapStruct) -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5</version>
</dependency>

<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5</version>
    <scope>provided</scope>
</dependency>
```

**Frontend Dependencies (package.json):**
```json
{
  "dependencies": {
    "lit": "^3.1.0",
    "lit-html": "^3.1.0"
  },
  "devDependencies": {
    "@playwright/test": "^1.40.0",
    "vitest": "^1.0.0",
    "typescript": "^5.3.0"
  }
}
```

**CVE Checks:**
```bash
# Backend
mvn -B org.owasp:dependency-check-maven:check

# Frontend
npm audit
```

---

## ⚠️ **CRITICAL DESIGN DECISIONS**

| Decision | Chosen Approach | Rationale | Alternative |
|----------|---|---|---|
## 🔍 **IMPLEMENTATION PATTERNS & CODE EXAMPLES**

### **Commission Calculation Pattern (Verified & Immutable)**

Always use this pattern in hex-payment-core:

```java
// Value Object - immutable, reusable
@Value
public class CommissionCalculation {
    private static final BigDecimal RATE = new BigDecimal("0.05");
    
    BigDecimal grossAmount;
    BigDecimal commission;    // = gross * 0.05
    BigDecimal sellerPayout;  // = gross * 0.95
    
    public CommissionCalculation(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
        this.commission = grossAmount.multiply(RATE)
            .setScale(2, RoundingMode.HALF_UP);
        this.sellerPayout = grossAmount.subtract(commission)
            .setScale(2, RoundingMode.HALF_UP);
    }
    
    // Verification: ensure payout + commission = gross
    public boolean isValid() {
        return commission.add(sellerPayout)
            .compareTo(grossAmount) == 0;
    }
}

// Usage in service
@Transactional
public void recordTransaction(Order order, Payment payment) {
    CommissionCalculation calc = new CommissionCalculation(order.getTotalGross());
    assert calc.isValid() : "Commission calculation mismatch";
    
    Transaction transaction = new Transaction(
        order.getBuyerId(),
        order.getSellerId(),
        order.getTotalGross(),
        calc.getCommission(),
        calc.getSellerPayout(),
        payment.getId()
    );
    
    transactionRepository.save(transaction);
    sellerEarnings.addEarnings(calc.getSellerPayout());
}
```

---

### **Authorization Pattern (Seller Ownership Verification)**

For all seller operations, verify ownership and return 404 (not 403):

```java
// Pattern: Verify ownership, return 404 if not owner (security)
@PreAuthorize("hasRole('SELLER')")
@PutMapping("/products/{productId}")
public ResponseEntity<ProductResponse> updateProduct(
    @PathVariable Long productId,
    @AuthenticationPrincipal User seller,
    @Valid @RequestBody UpdateProductRequest request
) {
    // ✅ Correct: returns 404 (doesn't reveal ownership)
    try {
        Product updated = productService.updateProduct(productId, seller.getId(), request);
        return ResponseEntity.ok(toResponse(updated));
    } catch (ProductNotFoundException e) {
        // Logs: "Product not found" (same message for not-found AND not-owned)
        return ResponseEntity.notFound().build();
    }
}

// Service method
@Transactional
public Product updateProduct(Long productId, Long sellerId, UpdateProductRequest req) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new ProductNotFoundException("Product not found"));
    
    // Check ownership AFTER checking existence
    // If not owner, throw same exception (looks like not-found)
    if (!product.isOwnedBy(sellerId)) {
        throw new ProductNotFoundException("Product not found");  // Same exception
    }
    
    return productRepository.save(product.withUpdates(req));
}
```

---

### **Order Creation Pattern (Stock Validation & Atomicity)**

```java
@Transactional(isolation = SERIALIZABLE)  // Prevent stock race conditions
public Order createOrder(Long buyerId, CreateOrderRequest request) {
    // Validate items exist and are available
    List<OrderItem> items = new ArrayList<>();
    BigDecimal totalGross = BigDecimal.ZERO;
    
    for (CartItem cartItem : request.getItems()) {
        Product product = productRepository.findById(cartItem.getProductId())
            .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        
        // Check availability (must be active AND have stock)
        if (!product.isAvailableForPurchase()) {
            if (product.getStockQuantity() <= 0) {
                throw new InsufficientStockException(
                    "Product " + product.getId() + " out of stock"
                );
            } else {
                throw new ProductUnavailableException("Product not available");
            }
        }
        
        // Validate requested quantity
        if (cartItem.getQuantity() > product.getStockQuantity()) {
            throw new InsufficientStockException(
                "Not enough stock. Available: " + product.getStockQuantity() +
                ", Requested: " + cartItem.getQuantity()
            );
        }
        
        OrderItem orderItem = new OrderItem(
            product.getId(),
            product.getSellerId(),
            product.getPrice(),
            cartItem.getQuantity(),
            product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()))
        );
        items.add(orderItem);
        totalGross = totalGross.add(orderItem.getSubtotal());
    }
    
    // Create order with PENDING_PAYMENT status
    Order order = new Order(
        buyerId,
        items.get(0).getSellerId(),  // MVP: single seller per order
        items,
        OrderStatus.PENDING_PAYMENT,
        totalGross,
        null  // paymentId assigned after payment succeeds
    );
    
    // Persist order (atomically with items)
    return orderRepository.save(order);
    
    // Note: Stock is NOT decremented yet
    // After payment succeeds → stock decremented via ProcessOrderPaymentUseCase
}
```

---

### **Order Payment Processing Pattern**

```java
@Transactional
public void processOrderPayment(Long orderId, Payment payment) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("Order not found"));
    
    // Verify order status (must be PENDING_PAYMENT)
    if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
        throw new OrderException("Order is not in PENDING_PAYMENT status: " + order.getStatus());
    }
    
    // Update order status
    order.markAsPaid(payment.getId());
    
    // Record transaction for each item (for commission split)
    for (OrderItem item : order.getItems()) {
        CommissionCalculation calc = new CommissionCalculation(item.getSubtotal());
        
        Transaction transaction = new Transaction(
            order.getBuyerId(),
            item.getSellerId(),
            item.getProductId(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getSubtotal(),
            calc.getCommission(),
            calc.getSellerPayout(),
            payment.getId(),
            TransactionStatus.COMPLETED
        );
        transactionRepository.save(transaction);
        
        // Update seller earnings
        SellerEarnings earnings = sellerEarningsRepository
            .findById(item.getSellerId())
            .orElseThrow(() -> new SellerNotFoundException("Seller earnings not found"));
        earnings.addEarnings(calc.getSellerPayout());
        sellerEarningsRepository.save(earnings);
    }
    
    // Decrement stock
    for (OrderItem item : order.getItems()) {
        Product product = productRepository.findById(item.getProductId())
            .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        product.decrementStock(item.getQuantity());
        productRepository.save(product);
    }
    
    // Save order
    orderRepository.save(order);
    
    logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=order.pay " +
        "orderId={} grossAmount={} commission={} payout={} status=SUCCESS",
        order.getId(), order.getTotalGross(), order.getTotalCommission(), order.getTotalPayout());
}
```

---

## 📡 **API REQUEST/RESPONSE EXAMPLES**

### **1. Enable Seller**

```http
POST /api/v1/sellers/enable HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "displayName": "My Premium Store",
  "bio": "Quality goods at best prices"
}

---

200 OK
Content-Type: application/json

{
  "sellerId": 42,
  "displayName": "My Premium Store",
  "bio": "Quality goods at best prices",
  "rating": 0.0,
  "reviewCount": 0,
  "verifiedAt": "2026-05-17T10:30:00Z",
  "joinedAt": "2026-05-17T10:30:00Z"
}
```

### **2. Create Product**

```http
POST /api/v1/products HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Wireless Headphones",
  "description": "Best quality headphones with 30-hour battery",
  "price": 129.99,
  "currency": "EUR",
  "initialStock": 50
}

---

201 CREATED
Content-Type: application/json

{
  "id": 123,
  "sellerId": 42,
  "name": "Wireless Headphones",
  "price": 129.99,
  "currency": "EUR",
  "stockQuantity": 50,
  "isActive": true,
  "publishedAt": "2026-05-17T10:35:00Z",
  "createdAt": "2026-05-17T10:35:00Z"
}
```

### **3. Create Order (from Cart)**

```http
POST /api/v1/orders HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "items": [
    {
      "productId": 123,
      "quantity": 2
    }
  ]
}

---

201 CREATED
Content-Type: application/json

{
  "id": 5001,
  "buyerId": 99,
  "sellerId": 42,
  "items": [
    {
      "productId": 123,
      "quantity": 2,
      "unitPrice": 129.99,
      "subtotal": 259.98
    }
  ],
  "status": "PENDING_PAYMENT",
  "totalGross": 259.98,
  "totalCommission": 13.00,
  "totalSellerPayout": 246.98,
  "paymentId": null,
  "createdAt": "2026-05-17T11:00:00Z",
  "updatedAt": "2026-05-17T11:00:00Z"
}
```

### **4. Record Payment & Commission**

```http
POST /api/v1/orders/5001/pay HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "paymentId": 7001,
  "method": "CREDIT_CARD",
  "amount": 259.98
}

---

200 OK
Content-Type: application/json

{
  "orderId": 5001,
  "status": "PAID",
  "paymentId": 7001,
  "commission": {
    "rate": "5%",
    "amount": 13.00
  },
  "sellerPayout": 246.98,
  "transactionsRecorded": 1,
  "timestamp": "2026-05-17T11:01:00Z"
}
```

### **5. Get Seller Earnings**

```http
GET /api/v1/sellers/42/earnings HTTP/1.1
Authorization: Bearer <token>

---

200 OK
Content-Type: application/json

{
  "sellerId": 42,
  "totalEarned": 246.98,
  "totalPaidOut": 0.00,
  "pendingPayout": 246.98,
  "lastPayoutAt": null,
  "commission": {
    "rate": 0.05,
    "interpretation": "You keep 95%, platform keeps 5%"
  },
  "updatedAt": "2026-05-17T11:01:00Z"
}
```

### **6. Request Payout**

```http
POST /api/v1/sellers/42/payout-request HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "amount": 200.00,
  "payoutMethod": "BANK_TRANSFER"
}

---

201 CREATED
Content-Type: application/json

{
  "payoutId": 3001,
  "sellerId": 42,
  "amount": 200.00,
  "status": "PENDING",
  "payoutMethod": "BANK_TRANSFER",
  "requestedAt": "2026-05-17T11:05:00Z",
  "processingAt": null,
  "completedAt": null,
  "transactionReference": null
}
```

---

## 🚨 **ROLLBACK & RECOVERY PROCEDURES**

### **If Migration Fails**

```bash
# Step 1: Check Flyway history
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;

# Step 2: Identify failed migration
# E.g.: V14__create_transactions_table.sql at step 5

# Step 3: Manual rollback (if necessary)
--/ Manually drop created objects /--
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS transactions_backup CASCADE;

# Step 4: Fix migration SQL, verify syntax locally
# Step 5: Re-run migration
mvn flyway:migrate -Dflyway.configFiles=...
```

### **If Data Corruption Occurs**

```bash
# Step 1: Restore from backup
# (Requires daily automated backups from DevOps)

# Step 2: Re-run migrations from last known good state
# Step 3: Resync data from payment system for transactions table
```

### **If Application Crashes During Payment**

```java
// Recovery pattern: Idempotent payment processing
@Transactional
public void processOrderPayment(Long orderId, Long paymentId) {
    // Check if transaction already recorded (idempotence)
    Optional<Transaction> existing = transactionRepository.findByOrderIdAndPaymentId(orderId, paymentId);
    if (existing.isPresent()) {
        logger.info("Transaction already recorded for orderId={} paymentId={}", orderId, paymentId);
        return;  // Skip duplicate processing
    }
    
    // ... normal processing ...
}
```

---

## 📊 **MONITORING & OBSERVABILITY**

### **Key Metrics to Collect**

```yaml
Seller Metrics:
  - Total sellers (platform growth)
  - New sellers this week
  - Active sellers (≥1 product)
  - Avg products per seller
  
Order Metrics:
  - Orders created (daily, weekly)
  - Orders completed (percentage)
  - Avg order value
  - Abandoned carts
  
Revenue Metrics:
  - Total GMV (Gross Merchandise Value)
  - Commission collected (5%)
  - Pending seller payouts
  - Payout success rate
  
Performance Metrics:
  - Order creation latency (target: <200ms)
  - Payment processing latency (target: <500ms)
  - Database query latency (p95: <100ms)
  - API endpoint response time (p95: <500ms)
```

### **Splunk Queries (Marketplace-Specific)**

```spl
# Orders created per hour
index=marketplace operation=order.create flow_stage=DOMAIN_OPERATION_COMPLETED
| stats count by date_hour

# Commission collected today
index=marketplace operation=payment.process commission=*
| stats sum(commission) as total_commission, sum(payout) as total_payout by sellerId

# Seller earnings this month
index=marketplace operation=seller.earnings
| stats values(pending_payout) as pending, values(total_earned) as earned by seller_id

# Failed transactions
index=marketplace status=ERROR operation=*
| stats count by operation, error_code
```

---

## ⚠️ **CRITICAL DESIGN DECISIONS**

| Decision | Chosen Approach | Rationale | Alternative | Risk Mitigation |
|----------|---|---|---|---|
| **Seller as separate entity?** | No — seller is User role | Minimal coupling, reuse auth | Separate Seller entity (complex) | Add seller_id FK to products; audit user table changes |
| **Commission calculation timing** | At transaction record time | Real-time accurate reporting | Deferred calculation (error-prone) | Use immutable CommissionCalculation VO; verify calc in tests |
| **Order → Cart relationship** | Separate cart (ephemeral) localStorage | Simple, stateless | Persistent cart in DB (Phase 2) | Validate cart contents at checkout (products may change) |
| **Payment integration** | Use existing Payment flow | Leverage built system | New payment system (risky) | Document assumptions; write integration tests |
| **Multi-seller checkout** | Phase 2 (separate orders per seller) | Simplifies MVP, matches fulfillment | Single transaction (Phase 2) | Support upgrade path in API design (order → orders[]) |
| **Shipping** | No shipping in MVP | Digital/pickup model first | Add shipping tracking (Phase 2) | Design order model to accept shipping fields later |
| **Real-time updates** | Polling/reload | Simpler, works | WebSockets (Phase 2) | Add @Scheduled refreshes; consider WebSocket upgrade path |
| **Stock reservation** | At order creation; decremented at payment | Prevents overselling | No reservation (races) | Use SERIALIZABLE isolation; test concurrent orders |

---

## 🚀 **QUICK REFERENCE: Implementation Checklist**

### **Backend (Estimated: 40 hours)**
- [ ] Database design
- [ ] Domain models (8 hours)
- [ ] Ports & interfaces (6 hours)
- [ ] Outbound adapters (10 hours)
- [ ] Inbound REST controllers (8 hours)
- [ ] Integration tests (5 hours)
- [ ] Logging & monitoring (3 hours)

### **Frontend (Estimated: 30 hours)**
- [ ] Cart page (4 hours)
- [ ] Checkout flow (5 hours)
- [ ] Order history (4 hours)
- [ ] Seller dashboard (8 hours)
- [ ] Product filters & search (5 hours)
- [ ] Reviews UI (2 hours)
- [ ] Testing & polish (2 hours)

**Total MVP: ~70 hours (~2 weeks with 1 engineer)**

---

## 📚 **PHASE 2: Enhanced UX (Weeks 3-4)**

### **Backend:**
- [ ] Persistent shopping cart in DB
- [ ] Wishlist/favorites feature
- [ ] Seller messaging system
- [ ] Advanced analytics (page views, conversion rate)
- [ ] Multi-seller unified checkout
- [ ] Shipping integration (labels, tracking)

### **Frontend:**
- [ ] Real-time order notifications (WebSocket)
- [ ] Seller onboarding wizard
- [ ] Advanced search with Elasticsearch
- [ ] Seller analytics dashboard
- [ ] Messaging UI between buyers/sellers
- [ ] Responsive mobile design

---

## 📊 **PHASE 3: Compliance & Scale (Weeks 5+)**

### **Backend:**
- [ ] KYC/AML document verification
- [ ] Seller dispute resolution system
- [ ] Tax calculation engine (per-region VAT/GST)
- [ ] Multi-currency support
- [ ] Fraud detection
- [ ] GDPR compliance tools

### **Frontend:**
- [ ] Admin dashboard (revenue, payouts, disputes)
- [ ] Mobile app (React Native/Flutter)
- [ ] Advanced seller analytics
- [ ] API rate limiting UI

---

## ❓ **CLARIFICATION QUESTIONS ANSWERED**

**Q: Do you want physical shipping?**
- **MVP:** No — digital/local pickup model
- **Phase 2:** Add shipping addresses, carrier API, tracking

**Q: Commission model fixed at 5%?**
- **MVP:** Yes, hard-coded at 5%
- **Phase 2:** Admin configurable per category/tier

**Q: Payout frequency?**
- **MVP:** On-demand (seller requests when ready)
- **Phase 2:** Batch weekly/monthly payouts

**Q: Multi-vendor cart?**
- **MVP:** One seller per order (simpler)
- **Phase 2:** Multiple sellers, group by seller

---

## 🔗 **RELATED DOCUMENTATION**

- `.github/copilot-instructions.md` — Coding standards and hexagonal architecture conventions
- `ADMIN_USERS_FEATURE.md` — Role-based access control pattern (seller authorization)
- `TESTING.md` — Test conventions and frameworks (JUnit 5, Testcontainers)
- `LOGGING_IMPLEMENTATION.md` — Splunk-ready structured logging patterns

---

## 📋 **SUMMARY: KEY TAKEAWAYS**

### **Architecture Decisions**
1. **Hexagonal Boundaries:** Seller is a User role (not separate entity) → reuse authentication
2. **Commission Immutability:** Always 5%, calculated at transaction time using value object
3. **Authorization:** Return 404 for not-found AND not-owned (security)
4. **Stock Safety:** SERIALIZABLE isolation to prevent overselling in concurrent scenarios

### **Implementation Focus Areas**
1. **Database Integrity:** FK constraints, CHECKs, and indices are **critical** for data safety
2. **Transactionality:** Use @Transactional(isolation=SERIALIZABLE) for payment processing
3. **Structured Logging:** Every endpoint must log REQUEST_RECEIVED → OPERATION_COMPLETED → RESPONSE_PREPARED
4. **Test Coverage:** Aim for ≥80% on services; integration tests for repositories

### **Common Pitfalls to AVOID**
- ❌ Configurable commission rates in MVP (will create auditing nightmare)
- ❌ Non-idempotent payment processing (app crash → duplicate charges)
- ❌ Synchronous stock updates without locking (race conditions)
- ❌ Selling inactive/deleted products (validation at checkout)
- ❌ Exposing 403 for not-owned resources (security leak; use 404)

### **Success Criteria (End of Phase 1)**
- [ ] Seller can list 5 products in dashboard
- [ ] Buyer can add 3 items to cart and complete checkout
- [ ] Order appears with correct commission split (95/5)
- [ ] Seller sees earned amount in "Pending Payout"
- [ ] All endpoints logged in Splunk with flow stages
- [ ] No database constraint violations in logs
- [ ] Unit & integration tests pass with >80% coverage

---

## 🎯 **NEXT IMMEDIATE STEPS (Day 1)**

1. **Create Git Branch:** `feature/marketplace-phase1`
2. **Create Migrations:** V11-V18 SQL files (start with V11, test each independently)
3. **Create Domain Models:** Seller, Product enhanced, Order, Transaction classes
4. **Define Input Ports:** EnableSellerUseCase, CreateProductUseCase, CreateOrderUseCase
5. **Write Unit Tests:** For each domain model (validation tests first)
6. **Update README:** Add marketplace onboarding instructions

**Estimated Time:** 2 weeks for MVP Phase 1 with 1 engineer (40 hours backend + 30 hours frontend)

---

**Document Status:** ✅ Ready for Implementation  
**Last Updated:** May 17, 2026  
**Next Review:** After Phase 1 completion  
**Contact:** Refer to `.github/copilot-instructions.md` for team guidelines

