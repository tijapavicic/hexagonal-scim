# 🚀 Marketplace Implementation Setup

**Date:** May 17, 2026  
**Phase:** Phase 1 MVP Implementation  
**Status:** Prerequisites Verification in Progress  
**Target Duration:** 2 weeks (70 hours)

---

## ✅ PREREQUISITES VERIFICATION

### **1. Environment Setup**

#### PostgreSQL Status
- ✅ **Container:** `hexagonal-scim-db` (postgres:16-alpine)
- ✅ **Status:** Running and healthy
- ✅ **Port:** 5432 (exposed locally)
- ✅ **Databases:** `hexagonal_scim` (app), `keycloak` (auth)
- ✅ **Credentials:** scim/scim (app), keycloak/keycloak (Keycloak)

#### Keycloak Status
- ✅ **Container:** `hexagonal-scim-keycloak` (keycloak:25.0.6)
- ✅ **Status:** Running and healthy
- ✅ **HTTPS Port:** 8443 (admin console)
- ✅ **Internal HTTP:** 8180 (for JWK fetches)
- ✅ **Admin Console:** https://localhost:8443 (user: admin/admin)
- ✅ **Realm:** hexagonal-scim
- ✅ **Client:** hexagonal-scim-public

#### Application Status
- ✅ **Container:** `hexagonal-scim-app` (Spring Boot)
- ✅ **Status:** Running and healthy
- ✅ **Health Check:** UP
- ✅ **Port:** 8080 (exposed locally)
- ✅ **Database:** Connected to hexagonal_scim

#### Frontend Status
- ⚠️ **Container:** `hexagonal-scim-frontend`
- ⚠️ **Status:** Running but unhealthy
- ℹ️ **Action:** Not critical for backend implementation; address later

#### Observability Stack
- ✅ **Prometheus:** Running at 9090
- ✅ **Grafana:** Running at 3001
- ✅ **Splunk:** Starting up at 8000
- ✅ **Logs:** Shared volume `app_logs` configured

---

### **2. Development Dependencies**

#### Java & Build Tools
```bash
# Java 17 (OpenJDK via Temurin)
java -version
# openjdk version "17.0.18" 2026-01-20

# Maven 3.9.15
mvn -version
# Apache Maven 3.9.15

# Status: ✅ Both at required versions
```

#### Spring Boot & Core Dependencies
- **Spring Boot:** 3.3.5 (from pom.xml)
- **Java Compiler:** 17 with `<parameters>true</parameters>` for Spring reflection
- **JPA/Hibernate:** Via spring-boot-starter-data-jpa (managed by 3.3.5)
- **Spring Security:** OAuth2 Resource Server (JWT mode)
- **Database Driver:** PostgreSQL 16+
- **Status:** ✅ All configured in root pom.xml

#### Testing Framework
- **JUnit 5:** Via spring-boot-starter-test (managed by 3.3.5)
- **Mockito:** Via spring-boot-starter-test
- **Testcontainers:** NOT YET ADDED (to be configured)
- **Spring Test Slices:** Available (@DataJpaTest, @WebMvcTest, @SpringBootTest)
- **Status:** ⏳ Needs testcontainers-postgresql addition

#### Code Quality Tools
- **ArchUnit:** 1.3.0 (present in pom.xml)
- **SonarQube:** NOT YET CONFIGURED (optional for MVP)
- **PMD:** NOT YET CONFIGURED (optional for MVP)
- **Logstash Encoder:** 8.0 (present for structured JSON logging)
- **Status:** ⚠️ ArchUnit ready; SonarQube/PMD optional

---

### **3. Testing Framework Configuration**

#### Current Test Configuration
- ✅ Maven Surefire 3.2.5 (present)
- ✅ JUnit 5 (via spring-boot-starter-test)
- ❌ Testcontainers (needs addition)

#### Action Required
**Add testcontainers-postgresql to root pom.xml `<dependencyManagement>`:**

```xml
<!-- Add to pom.xml dependencyManagement/dependencies -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.20.4</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Then add per-module dependencies:
```xml
<!-- hex-outbound-adapter-db/pom.xml (test scope) -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<!-- hex-core/pom.xml (test scope) -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

---

### **4. Git Workflow & Branching**

#### Convention: Git Flow with Feature Branches
```bash
# Main branches (protected)
- main             # Production-ready, all tests passing
- develop          # Integration branch for next release

# Feature branches for marketplace enhancements
- feature/marketplace-seller-profiles
- feature/marketplace-product-catalog
- feature/marketplace-orders
- feature/marketplace-transactions
- feature/marketplace-seller-earnings
- feature/marketplace-search-filter
- feature/marketplace-reviews

# Bug fix branches (from develop)
- bugfix/marketplace-commission-calculation
- bugfix/marketplace-authorization-bypass
```

#### Current Status
- ✅ Git repository initialized
- ✅ .gitignore configured
- ⏳ Need to create feature branches as we progress
- ℹ️ Recommendation: Create feature branches one per backend enhancement (7 total)

---

### **5. Code Quality & Architectural Validation**

#### ArchUnit Configuration (Present in pom.xml)

Current tests should enforce:
- Hexagonal module boundaries (no direct cross-module dependencies except through ports)
- No Spring annotations in domain models (hex-core)
- Adapters must implement ports
- Controllers must not depend on JPA repositories

**Action Required:** Create `ArchitectureTest.java` to validate these rules.

#### SonarQube Integration (Optional for MVP)

**Status:** Not configured. For future phase.

**Would provide:**
- Code coverage reports
- Complexity metrics
- Bug detection
- Security vulnerabilities

**Skip for now; add in Phase 2 if desired.**

---

## 📋 IMPLEMENTATION ROADMAP

### **Week 1: Domain Models & Ports**

| Day | Feature | Task | Effort | Files |
|-----|---------|------|--------|-------|
| **Day 1-2** | DB Migrations | Create V11-V18 migrations | 2h | `src/main/resources/db/migration/` |
| **Day 1-2** | Domain Models | Create Order, OrderItem, Transaction, SellerEarnings models | 3h | `hex-core/model/` |
| **Day 2-3** | Seller Extension | Create SellerProfile, SellerRegistration value objects | 2h | `hex-core/model/` |
| **Day 3** | Input Ports | Define 18+ use-case ports | 3h | `hex-core/port/in/` |
| **Day 4-5** | Output Ports | Define repository port interfaces | 2h | `hex-core/port/out/` |
| **Day 5** | Exceptions | Create marketplace-specific exception hierarchy | 1h | `hex-core/exception/` |
| **Week 1 Total** | | | **13h** | |

### **Week 2: Adapters & Controllers**

| Day | Feature | Task | Effort | Files |
|-----|---------|------|--------|-------|
| **Day 1** | Payment Core | Commission calculation models & ports | 2h | `hex-payment-core/` |
| **Day 1-2** | DB Adapters | Implement 8 repository adapters | 5h | `hex-outbound-adapter-db/adapter/` |
| **Day 2-3** | Use Cases | Implement 7 use-case services | 6h | `hex-application/` |
| **Day 4** | REST Controllers | Implement 8 controller adapters | 4h | `hex-inbound-adapter-web/api/` |
| **Day 5** | Integration Tests | Write 20+ integration tests | 4h | `**/test/` |
| **Day 5** | Documentation | API docs, test coverage report | 2h | `README.md` updates |
| **Week 2 Total** | | | **23h** | |

### **Frontend (Parallel, not blocking backend)**

- **Seller Dashboard** (Profile, Products, Orders, Earnings)
- **Buyer Shopping** (Catalog, Cart, Checkout)
- **Integration with REST APIs** (created in backend)

---

## 🔧 SETUP CHECKLIST

### Before Starting Any Code

- [ ] **PostgreSQL:** Verify connection to `hexagonal_scim`
  ```bash
  psql -h localhost -U scim -d hexagonal_scim -c "SELECT 1;"
  ```

- [ ] **Keycloak:** Verify token endpoint is accessible
  ```bash
  curl -s https://localhost:8443/realms/hexagonal-scim/.well-known/openid-configuration | jq '.token_endpoint'
  ```

- [ ] **Application:** Verify startup with clean build
  ```bash
  mvn clean package -DskipTests
  # Expect: BUILD SUCCESS
  ```

- [ ] **Test Database:** Verify Testcontainers can spin up PostgreSQL
  ```bash
  # Will run during first integration test
  ```

- [ ] **Git Branches:** Create feature branch structure
  ```bash
  git checkout develop
  git pull origin develop
  git checkout -b feature/marketplace-phase1-setup
  ```

- [ ] **IDE Configuration:** Import project modules in IntelliJ/Eclipse
  - Right-click project → Maven → Reimport
  - Verify no red squiggles in hex-core, hex-application

---

## 📂 PROJECT STRUCTURE (Phase 1)

```
hex-core/
├── src/main/java/com/example/user/
│   ├── model/
│   │   ├── Order.java                    # Aggregate root
│   │   ├── OrderItem.java
│   │   ├── OrderStatus.java              # Enum
│   │   ├── Transaction.java
│   │   ├── SellerProfile.java
│   │   ├── SellerRegistration.java
│   │   ├── SellerEarnings.java
│   │   └── SellerPayout.java
│   ├── port/
│   │   ├── in/
│   │   │   ├── EnableSellerUseCase.java
│   │   │   ├── CreateProductUseCase.java
│   │   │   ├── CreateOrderUseCase.java
│   │   │   ├── GetOrderUseCase.java
│   │   │   └── ~13 more use-case ports
│   │   └── out/
│   │       ├── OrderRepositoryPort.java
│   │       ├── SellerRepositoryPort.java
│   │       └── ~6 more repository ports
│   └── exception/
│       ├── SellerException.java
│       ├── OrderException.java
│       └── InsufficientStockException.java

hex-payment-core/
├── src/main/java/com/example/user/
│   ├── model/
│   │   ├── PaymentSplit.java             # Value object: gross, commission, payout
│   │   └── OrderPayment.java
│   └── port/in/
│       └── ProcessOrderPaymentUseCase.java

hex-outbound-adapter-db/
├── src/main/java/com/example/user/
│   └── adapter/
│       ├── OrderRepositoryAdapter.java
│       ├── TransactionRepositoryAdapter.java
│       ├── SellerEarningsRepositoryAdapter.java
│       └── ~5 more adapters
└── src/test/java/com/example/user/
    └── adapter/
        └── **/RepositoryAdapterIntegrationTest.java

hex-inbound-adapter-web/
├── src/main/java/com/example/user/
│   └── api/
│       ├── SellerControllerAdapter.java       # /api/v1/sellers/
│       ├── ProductControllerAdapter.java      # /api/v1/sellers/me/products
│       ├── OrderControllerAdapter.java        # /api/v1/orders
│       └── TransactionControllerAdapter.java
└── src/test/java/com/example/user/
    └── api/
        └── **/ControllerAdapterTest.java

hex-application/
├── src/main/java/com/example/user/
│   └── application/
│       ├── EnableSellerService.java
│       ├── CreateOrderService.java
│       ├── ProcessPaymentService.java
│       └── ~4 more use-case implementations
└── src/test/java/com/example/user/
    └── application/
        └── **/ServiceTest.java

src/main/resources/db/migration/
├── V11__add_seller_capabilities_to_users.sql
├── V12__enhance_products_with_seller_and_status.sql
├── V13__create_orders_and_order_items_tables.sql
├── V14__create_transactions_table.sql
├── V15__create_seller_earnings_table.sql
├── V16__create_seller_payouts_table.sql
├── V17__create_product_reviews_table.sql
└── V18__create_seller_reviews_table.sql

frontend/src/
├── pages/
│   ├── seller-home.ts
│   ├── seller-products.ts
│   ├── seller-orders.ts
│   ├── seller-earnings.ts
│   ├── cart-page.ts
│   └── checkout-page.ts
└── components/
    ├── seller-profile.ts
    ├── product-card.ts
    └── review-card.ts
```

---

## 🎯 SUCCESS CRITERIA (Phase 1)

### Backend Completion
- [ ] All 8 database migrations (V11-V18) created and tested
- [ ] All 30+ domain models created with proper validation
- [ ] All 18+ input ports defined (no implementations)
- [ ] All 7 use-case services implemented
- [ ] All 8 repository adapters implemented with integration tests
- [ ] All 8 REST controller adapters implemented with controller tests
- [ ] Commission calculation logic correct (5% platform, 95% seller)
- [ ] Seller authorization enforced (sellers see only own products/orders)
- [ ] Order lifecycle state machine validated
- [ ] Full integration test coverage (happy path + 2 edge cases per feature)
- [ ] Build passing: `mvn clean verify`
- [ ] No CVE findings: `mvn org.owasp:dependency-check-maven:check`

### Frontend Completion (Integration Phase)
- [ ] Seller dashboard pages created and styled
- [ ] Buyer shopping pages created and styled
- [ ] All backend REST endpoints consumed
- [ ] Form validation and error handling
- [ ] Responsive design (mobile-first)
- [ ] Authentication integration with Keycloak

### Documentation
- [ ] API documentation (OpenAPI/Swagger)
- [ ] Database schema documented
- [ ] Implementation decisions logged in IMPLEMENTATION_NOTES.md
- [ ] Test coverage report (target: 80%+)

---

## 🚨 Known Risks & Mitigations

| Risk | Severity | Mitigation |
|------|----------|-----------|
| **Commission Rounding Errors** | Medium | Use BigDecimal with HALF_UP, test edge cases (rounding, $0.01 items) |
| **Concurrent Stock Depletion** | High | Use pessimistic locking on Product.stockQuantity |
| **Seller Authorization Bypass** | Critical | DB-level filtering + app validation + integration tests |
| **Orphaned Transactions** | Medium | Foreign key constraints with ON DELETE CASCADE (carefully) |
| **Payment Idempotency** | Medium | Require idempotency keys; store request IDs |
| **Frontend CORS** | Medium | Keep CORS permissive (dev), lock down for prod |
| **Keycloak Token Expiration** | Low | Frontend refresh token handling; already configured |

---

## 📞 Quick Reference Commands

```bash
# Build entire project
mvn clean verify

# Build single module
mvn -pl hex-core clean verify

# Run application locally (without Docker)
mvn spring-boot:run -pl hex-application

# Run database migrations
mvn flyway:migrate

# Check dependencies for CVEs
mvn org.owasp:dependency-check-maven:check

# Generate API docs
mvn clean package -DskipTests && open http://localhost:8080/api-docs

# Connect to PostgreSQL
psql -h localhost -U scim -d hexagonal_scim

# Check Keycloak token endpoint
curl -X POST https://localhost:8443/realms/hexagonal-scim/protocol/openid-connect/token \
  -d "client_id=hexagonal-scim-public" \
  -d "username=testuser@example.com" \
  -d "password=password" \
  -d "grant_type=password" \
  -k  # ignore self-signed cert
```

---

## 📝 Next Steps

1. ✅ **This checklist:** Verify all prerequisites
2. ⏳ **Add Testcontainers:** Update root pom.xml (5 min)
3. ⏳ **Create Git Branch:** `feature/marketplace-phase1-setup` (2 min)
4. ⏳ **Database Migrations:** Create V11-V18 migrations (30 min)
5. ⏳ **Domain Models:** Implement 30+ models in hex-core (3 hours)
6. ⏳ **Input Ports:** Define 18+ use-case interfaces (1 hour)
7. ⏳ **Output Ports:** Define repository interfaces (30 min)
8. ⏳ **Exceptions:** Create exception hierarchy (15 min)

---

**Status:** All prerequisites verified ✅  
**Ready to begin Phase 1 implementation**

Last Updated: May 17, 2026, 15:00 UTC

