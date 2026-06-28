# Testing Strategy & Security Engineering Guide

**Engineering-Oriented Testing Handbook**  
> "Testing is not about finding bugs; it's about building confidence in your system."

---

## Table of Contents

1. [Testing Types](#testing-types)
   - [Unit Testing](#unit-testing)
   - [Static Code Analysis](#static-code-analysis)
   - [Software Bill of Materials (SBOM)](#software-bill-of-materials-sbom)
2. [Security Testing - Shift Left](#security-testing---shift-left)
   - [Threat Modeling](#threat-modeling)
   - [Secret Scanner](#secret-scanner)
   - [SAST - Static Application Security Testing](#sast---static-application-security-testing)
   - [SCA - Software Composition Analysis](#sca---software-composition-analysis)
   - [EPSS - Exploit Prediction Scoring System](#epss---exploit-prediction-scoring-system)
   - [DAST - Dynamic Application Security Testing](#dast---dynamic-application-security-testing)
3. [CISO Bookshelf](#ciso-bookshelf)
4. [Engineering Resources](#engineering-resources)
5. [Implementation Checklist](#implementation-checklist)

---

## Testing Types

### Unit Testing

**Definition:** Testing individual components in isolation to verify they work as expected.

**Why It Matters:**
- **Fast feedback loop** — catch bugs before they propagate
- **Refactoring safety net** — change code with confidence
- **Living documentation** — tests describe expected behavior
- **Design feedback** — hard-to-test code is often poorly designed

#### JUnit 5 Best Practices (Java/Spring Boot)

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentGateway paymentGateway;
    
    @InjectMocks
    private OrderService orderService;
    
    @Test
    @DisplayName("Should calculate 5% commission on order total")
    void shouldCalculateCommission() {
        // Given
        BigDecimal grossAmount = new BigDecimal("100.00");
        Order order = Order.builder()
            .grossAmount(grossAmount)
            .build();
        
        // When
        BigDecimal commission = orderService.calculateCommission(order);
        
        // Then
        assertThat(commission)
            .isEqualByComparingTo(new BigDecimal("5.00"));
    }
    
    @ParameterizedTest
    @CsvSource({
        "100.00, 5.00",
        "50.00, 2.50",
        "0.00, 0.00"
    })
    @DisplayName("Should calculate commission for various amounts")
    void shouldCalculateCommissionForVariousAmounts(
        BigDecimal gross, 
        BigDecimal expectedCommission
    ) {
        // Given
        Order order = Order.builder().grossAmount(gross).build();
        
        // When
        BigDecimal commission = orderService.calculateCommission(order);
        
        // Then
        assertThat(commission).isEqualByComparingTo(expectedCommission);
    }
    
    @Test
    @DisplayName("Should throw exception when order not found")
    void shouldThrowExceptionWhenOrderNotFound() {
        // Given
        Long orderId = 999L;
        when(orderRepository.findById(orderId))
            .thenReturn(Optional.empty());
        
        // When / Then
        assertThatThrownBy(() -> orderService.getOrder(orderId))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessageContaining("Order not found: 999");
    }
}
```

#### Test Pyramid

```
       /\
      /  \     E2E Tests (5-10%)
     /____\    Slow, brittle, expensive
    /      \
   /  API   \  Integration Tests (20-30%)
  /__________\ Medium speed, test boundaries
 /            \
/   Unit Tests \ Unit Tests (60-75%)
/________________\ Fast, isolated, cheap
```

**Key Metrics:**
- **Code Coverage:** Aim for 80%+ on business logic
- **Test Execution Time:** Unit tests < 10ms each
- **Test Readability:** Given-When-Then pattern
- **Test Independence:** Each test can run in isolation

#### Tools & Frameworks

| Tool | Purpose | Command |
|------|---------|---------|
| **JUnit 5** | Test framework | `mvn test` |
| **Mockito** | Mocking framework | `@Mock`, `@InjectMocks` |
| **AssertJ** | Fluent assertions | `assertThat(x).isEqualTo(y)` |
| **JaCoCo** | Code coverage | `mvn jacoco:report` |
| **ArchUnit** | Architecture tests | Test hexagonal boundaries |
| **Testcontainers** | Integration tests | Real DB/Kafka in Docker |

**Run Tests:**
```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=OrderServiceTest

# Run with coverage
mvn clean verify jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

**Architecture Tests (Hexagonal Enforcement):**
```java
@AnalyzeClasses(packages = "com.example.user")
public class HexagonalArchitectureTest {
    
    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
        classes()
            .that().resideInAPackage("..model..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..model..", "java..");
    
    @ArchTest
    static final ArchRule adapters_should_not_depend_on_each_other =
        noClasses()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..");
}
```

---

### Static Code Analysis

**Definition:** Automated examination of source code without executing it to find bugs, code smells, and security vulnerabilities.

**Why It Matters:**
- **Catch bugs before runtime** — find null pointer dereferences, resource leaks
- **Enforce code standards** — consistent style across team
- **Security vulnerabilities** — SQL injection, XSS, hardcoded secrets
- **Technical debt tracking** — quantify code quality over time

#### SonarQube Integration

```yaml
# .github/workflows/sonar.yml
name: SonarQube Analysis
on:
  push:
    branches: [main, develop]
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  sonar:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Full history for blame
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Cache SonarQube packages
        uses: actions/cache@v4
        with:
          path: ~/.sonar/cache
          key: ${{ runner.os }}-sonar
      
      - name: Cache Maven packages
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      
      - name: Build and analyze
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        run: |
          mvn clean verify sonar:sonar \
            -Dsonar.projectKey=hexagonal-scim \
            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

**SonarQube Quality Gates:**
```properties
# sonar-project.properties
sonar.projectKey=hexagonal-scim
sonar.projectName=Hexagonal SCIM Marketplace
sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes
sonar.java.test.binaries=target/test-classes
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml

# Quality Gate thresholds
sonar.qualitygate.wait=true
sonar.qualitygate.timeout=300

# Threshold values
sonar.coverage.minimum=80
sonar.duplications.maximum=3
sonar.maintainability.rating=A
sonar.reliability.rating=A
sonar.security.rating=A
```

#### SpotBugs (Static Analysis)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.3.1</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <xmlOutput>true</xmlOutput>
        <failOnError>true</failOnError>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>1.13.0</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Run SpotBugs:**
```bash
# Find bugs
mvn spotbugs:check

# Generate report
mvn spotbugs:spotbugs
open target/spotbugsXml.xml
```

#### Checkstyle (Code Style)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>google_checks.xml</configLocation>
        <consoleOutput>true</consoleOutput>
        <failsOnError>true</failsOnError>
        <violationSeverity>warning</violationSeverity>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Run Checkstyle:**
```bash
mvn checkstyle:check
```

#### PMD (Code Quality)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.21.2</version>
    <configuration>
        <rulesets>
            <ruleset>/rulesets/java/quickstart.xml</ruleset>
        </rulesets>
        <failOnViolation>true</failOnViolation>
        <printFailingErrors>true</printFailingErrors>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Key Metrics Tracked:**
- **Cyclomatic Complexity** — aim for < 10 per method
- **Cognitive Complexity** — aim for < 15 per method
- **Code Duplication** — < 3% duplicated blocks
- **Code Coverage** — > 80% line coverage
- **Technical Debt Ratio** — < 5%

**Tools Comparison:**

| Tool | Focus | Speed | Best For |
|------|-------|-------|----------|
| **SonarQube** | Comprehensive | Medium | Enterprise quality gates |
| **SpotBugs** | Bug patterns | Fast | CI/CD integration |
| **Checkstyle** | Code style | Very Fast | Style enforcement |
| **PMD** | Code quality | Fast | Code smells |
| **ErrorProne** | Compiler plugin | Fast | Compile-time checks |

---

### Software Bill of Materials (SBOM)

**Definition:** A complete inventory of all components, libraries, and dependencies in your application, including licenses and versions.

**Why It Matters:**
- **Supply chain security** — know what's in your software
- **License compliance** — avoid legal issues
- **Vulnerability management** — track affected components
- **Incident response** — quickly identify exposure to CVEs (e.g., Log4Shell)

#### Generate SBOM with CycloneDX

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <version>2.7.11</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>makeAggregateBom</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <projectType>application</projectType>
        <schemaVersion>1.5</schemaVersion>
        <includeBomSerialNumber>true</includeBomSerialNumber>
        <includeCompileScope>true</includeCompileScope>
        <includeProvidedScope>true</includeProvidedScope>
        <includeRuntimeScope>true</includeRuntimeScope>
        <includeSystemScope>true</includeSystemScope>
        <includeTestScope>false</includeTestScope>
        <includeLicenseText>false</includeLicenseText>
        <outputReactorProjects>true</outputReactorProjects>
        <outputFormat>all</outputFormat>
        <outputName>bom</outputName>
    </configuration>
</plugin>
```

**Generate SBOM:**
```bash
# Generate SBOM
mvn cyclonedx:makeAggregateBom

# Output files:
# - target/bom.json (CycloneDX JSON)
# - target/bom.xml (CycloneDX XML)

# View SBOM
cat target/bom.json | jq '.components[] | {name: .name, version: .version, licenses: .licenses}'
```

**Example SBOM Output:**
```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "serialNumber": "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79",
  "version": 1,
  "metadata": {
    "timestamp": "2026-06-28T10:00:00Z",
    "component": {
      "type": "application",
      "name": "hexagonal-scim",
      "version": "1.0.0"
    }
  },
  "components": [
    {
      "type": "library",
      "name": "spring-boot-starter-web",
      "group": "org.springframework.boot",
      "version": "3.2.5",
      "licenses": [
        {
          "license": {
            "id": "Apache-2.0"
          }
        }
      ],
      "purl": "pkg:maven/org.springframework.boot/spring-boot-starter-web@3.2.5"
    }
  ]
}
```

#### SBOM Scanning with Grype

```bash
# Install Grype (vulnerability scanner)
brew install grype

# Scan SBOM for vulnerabilities
grype sbom:target/bom.json

# Generate report
grype sbom:target/bom.json -o json > vulnerability-report.json

# Fail CI on HIGH/CRITICAL
grype sbom:target/bom.json --fail-on high
```

#### SBOM in CI/CD

```yaml
# .github/workflows/sbom.yml
name: SBOM Generation & Scanning
on:
  push:
    branches: [main]
  release:
    types: [published]

jobs:
  sbom:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Generate SBOM
        run: mvn cyclonedx:makeAggregateBom
      
      - name: Scan SBOM for vulnerabilities
        uses: anchore/scan-action@v3
        with:
          sbom: "target/bom.json"
          fail-build: true
          severity-cutoff: high
      
      - name: Upload SBOM as artifact
        uses: actions/upload-artifact@v4
        with:
          name: sbom
          path: target/bom.json
      
      - name: Attach SBOM to release
        if: github.event_name == 'release'
        uses: softprops/action-gh-release@v1
        with:
          files: target/bom.json
```

**SBOM Standards:**
- **CycloneDX** — OWASP standard, JSON/XML, rich vulnerability data
- **SPDX** — Linux Foundation standard, license-focused
- **SWID** — ISO standard, software identification tags

**Tools:**
- **CycloneDX Maven Plugin** — Generate SBOM during build
- **Syft** — Generate SBOM from containers/filesystems
- **Grype** — Scan SBOM for vulnerabilities
- **Dependency-Track** — SBOM analysis platform

---

## Security Testing - Shift Left

**Shift Left Philosophy:**
> "Find and fix security issues **early** in the development lifecycle, not in production."

```
Traditional: Code → Build → Test → Security → Production
Shift Left:  Security → Code → Test → Security → Build → Security → Production
                ↑
           (Threat Model)
```

**Cost of Fixing Bugs:**
- **Development:** $100
- **QA/Testing:** $1,000
- **Production:** $10,000+

**Shift Left = Move security testing to the left (earlier) in SDLC**

---

### Threat Modeling

**Definition:** Structured approach to identifying security threats before writing code.

**Why It Matters:**
- **Proactive security** — find flaws in design, not code
- **Cost-effective** — cheaper to fix design than code
- **Shared understanding** — security becomes everyone's job
- **Compliance** — required for SOC 2, ISO 27001

#### STRIDE Threat Model

**STRIDE Framework:**
| Threat | Description | Example | Mitigation |
|--------|-------------|---------|------------|
| **S**poofing | Impersonating someone/something | Fake JWT tokens | Strong authentication (OAuth2/OIDC) |
| **T**ampering | Modifying data without authorization | SQL injection | Input validation, parameterized queries |
| **R**epudiation | Denying actions | User claims "I didn't order that" | Audit logs, digital signatures |
| **I**nformation Disclosure | Exposing sensitive data | Leaking PII in logs | Encryption, data classification |
| **D**enial of Service | Making system unavailable | Rate limiting bypass | Rate limiting, circuit breakers |
| **E**levation of Privilege | Gaining unauthorized access | Privilege escalation | Least privilege, RBAC |

#### Threat Model Example: Marketplace Payment Flow

```
┌──────────┐         ┌──────────┐         ┌──────────┐         ┌──────────┐
│  Buyer   │─────────│   API    │─────────│ Payment  │─────────│  Stripe  │
│  (Web)   │         │ Gateway  │         │ Service  │         │   API    │
└──────────┘         └──────────┘         └──────────┘         └──────────┘
     │                     │                     │                     │
     │ 1. POST /orders    │                     │                     │
     │────────────────────>│                     │                     │
     │                     │ 2. Create order     │                     │
     │                     │────────────────────>│                     │
     │                     │                     │ 3. Charge card      │
     │                     │                     │────────────────────>│
     │                     │                     │ 4. Payment success  │
     │                     │                     │<────────────────────│
     │                     │ 5. Record transaction│                    │
     │                     │<────────────────────│                     │
     │ 6. Order confirmed  │                     │                     │
     │<────────────────────│                     │                     │
```

**Threats Identified:**

| # | Threat Type | Threat | Risk | Mitigation |
|---|-------------|--------|------|------------|
| 1 | Spoofing | Attacker impersonates buyer | HIGH | OAuth2 + JWT validation |
| 2 | Tampering | Modify order amount in transit | CRITICAL | HTTPS + request signing |
| 3 | Repudiation | Buyer denies making order | MEDIUM | Audit logs with IP/user-agent |
| 4 | Information Disclosure | Card numbers in logs | CRITICAL | PCI-DSS compliance, no card storage |
| 5 | DoS | Mass order creation | HIGH | Rate limiting (10 orders/min) |
| 6 | Elevation of Privilege | Buyer accesses seller earnings | HIGH | Authorization checks on all endpoints |

**Threat Modeling Tools:**
- **Microsoft Threat Modeling Tool** — Free, visual DFD-based modeling
- **OWASP Threat Dragon** — Open-source, web-based
- **IriusRisk** — Commercial, automated threat modeling
- **Threagile** — YAML-based, code-first threat modeling

**Process:**
1. **Diagram** — Draw data flow diagram (DFD)
2. **Identify** — Apply STRIDE to each component/flow
3. **Mitigate** — Design controls for each threat
4. **Validate** — Review with security team
5. **Track** — Add mitigations to backlog

---

### Secret Scanner

**Definition:** Automated tools that detect hardcoded secrets (API keys, passwords, tokens) in code.

**Why It Matters:**
- **Prevent credential leaks** — 90% of breaches involve stolen credentials
- **Compliance** — Required for SOC 2, PCI-DSS
- **Incident prevention** — Stop secrets from reaching production

#### GitGuardian / TruffleHog

```yaml
# .github/workflows/secrets.yml
name: Secret Scanner
on:
  push:
    branches: ['**']
  pull_request:
    branches: [main, develop]

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Full history
      
      - name: TruffleHog Secret Scan
        uses: trufflesecurity/trufflehog@main
        with:
          path: ./
          base: ${{ github.event.repository.default_branch }}
          head: HEAD
          extra_args: --only-verified
      
      - name: GitGuardian Secret Scan
        uses: GitGuardian/ggshield-action@v1
        env:
          GITGUARDIAN_API_KEY: ${{ secrets.GITGUARDIAN_API_KEY }}
```

**Pre-commit Hook (Local):**
```bash
# Install TruffleHog
brew install trufflesecurity/trufflehog/trufflehog

# Add to .git/hooks/pre-commit
#!/bin/bash
trufflehog git file://. --only-verified --fail
```

**Common Secret Patterns Detected:**
- AWS Access Keys (`AKIA...`)
- GitHub Personal Access Tokens (`ghp_...`)
- Private SSH Keys (`-----BEGIN RSA PRIVATE KEY-----`)
- Stripe API Keys (`sk_live_...`)
- Database URLs with passwords (`jdbc:postgresql://user:pass@...`)
- JWT Secrets (high-entropy strings)

**Best Practices:**
- ✅ Use environment variables
- ✅ Use secret management (Vault, AWS Secrets Manager)
- ✅ Rotate secrets regularly
- ✅ Never commit `.env` files
- ❌ Never hardcode secrets in code

```java
// ❌ BAD: Hardcoded secret
String apiKey = "sk_live_51HqN...";

// ✅ GOOD: Environment variable
String apiKey = System.getenv("STRIPE_API_KEY");

// ✅ BETTER: Spring Boot configuration
@Value("${stripe.api-key}")
private String apiKey;
```

---

### SAST - Static Application Security Testing

**Definition:** Automated security testing of source code without executing it (white-box testing).

**Why It Matters:**
- **Early detection** — Find vulnerabilities before runtime
- **No production risk** — Test in dev environment
- **Fast feedback** — Results in minutes, not days

#### Semgrep (Modern SAST)

```yaml
# .github/workflows/sast.yml
name: SAST - Semgrep
on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  semgrep:
    runs-on: ubuntu-latest
    container:
      image: returntocorp/semgrep
    steps:
      - uses: actions/checkout@v4
      
      - name: Run Semgrep
        run: |
          semgrep scan \
            --config=auto \
            --config=p/owasp-top-ten \
            --config=p/spring-boot \
            --config=p/java \
            --json \
            --output=semgrep-report.json
      
      - name: Upload SARIF
        if: always()
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: semgrep-report.json
```

**Semgrep Custom Rule Example:**
```yaml
# .semgrep/rules/marketplace-security.yml
rules:
  - id: marketplace-commission-validation
    pattern: |
      $ORDER.setCommission($AMOUNT)
    message: "Commission amount must be validated before setting"
    severity: WARNING
    languages: [java]
    metadata:
      cwe: "CWE-20: Improper Input Validation"
      owasp: "A03:2021 - Injection"
  
  - id: sql-injection-prevention
    pattern: |
      jdbcTemplate.query("SELECT * FROM users WHERE id = " + $ID, ...)
    message: "Potential SQL injection. Use parameterized queries."
    severity: ERROR
    languages: [java]
    fix: |
      jdbcTemplate.query("SELECT * FROM users WHERE id = ?", $ID, ...)
```

#### Snyk Code (SAST)

```bash
# Install Snyk CLI
npm install -g snyk

# Authenticate
snyk auth

# Test code for vulnerabilities
snyk code test

# Monitor code continuously
snyk code monitor
```

#### SonarQube Security Hotspots

```bash
# Run SonarQube with security focus
mvn sonar:sonar \
  -Dsonar.qualitygate.wait=true \
  -Dsonar.security.hotspots.review=true
```

**SAST Tools Comparison:**

| Tool | Language Support | Speed | Best For |
|------|------------------|-------|----------|
| **Semgrep** | 30+ languages | Fast | Custom rules, polyglot repos |
| **Snyk Code** | 10+ languages | Fast | Developer-friendly UX |
| **Checkmarx** | 25+ languages | Slow | Enterprise compliance |
| **Fortify** | 27+ languages | Medium | Regulated industries |
| **SonarQube** | 29+ languages | Medium | Comprehensive quality + security |

**Common Vulnerabilities Detected:**
- SQL Injection (CWE-89)
- Cross-Site Scripting (CWE-79)
- Path Traversal (CWE-22)
- Hardcoded Credentials (CWE-798)
- Insecure Deserialization (CWE-502)
- XML External Entity (CWE-611)

---

### SCA - Software Composition Analysis

**Definition:** Automated security testing of third-party dependencies and open-source libraries.

**Why It Matters:**
- **80% of code** is third-party libraries
- **Known vulnerabilities** — CVEs in dependencies
- **License compliance** — avoid GPL in commercial software
- **Supply chain attacks** — detect malicious packages

#### OWASP Dependency-Check

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.2.0</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFiles>
            <suppressionFile>owasp-suppressions.xml</suppressionFile>
        </suppressionFiles>
        <formats>
            <format>HTML</format>
            <format>JSON</format>
            <format>JUNIT</format>
        </formats>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Run SCA:**
```bash
# Scan dependencies for CVEs
mvn dependency-check:check

# View report
open target/dependency-check-report.html

# Fail build on HIGH/CRITICAL
mvn dependency-check:check -DfailBuildOnCVSS=7
```

**Suppression File (False Positives):**
```xml
<!-- owasp-suppressions.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <suppress>
        <notes>False positive - not applicable to our usage</notes>
        <cve>CVE-2024-12345</cve>
    </suppress>
</suppressions>
```

#### Snyk Open Source (SCA)

```yaml
# .github/workflows/sca.yml
name: SCA - Snyk
on:
  push:
    branches: [main]
  pull_request:
  schedule:
    - cron: '0 0 * * 0'  # Weekly

jobs:
  snyk:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Run Snyk to check for vulnerabilities
        uses: snyk/actions/maven@master
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          args: --severity-threshold=high --fail-on=all
      
      - name: Upload Snyk report
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: snyk.sarif
```

#### Dependabot (GitHub Native)

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
    reviewers:
      - "security-team"
    labels:
      - "dependencies"
      - "security"
    commit-message:
      prefix: "deps"
    # Auto-merge patch updates
    automerge:
      - dependency-type: "development"
        update-type: "semver:patch"
```

**SCA Tools Comparison:**

| Tool | CVE Database | License Check | Auto-fix | Cost |
|------|--------------|---------------|----------|------|
| **OWASP Dependency-Check** | NVD | ✅ | ❌ | Free |
| **Snyk** | Proprietary + NVD | ✅ | ✅ | Freemium |
| **Dependabot** | GitHub Advisory | ❌ | ✅ | Free |
| **Mend (WhiteSource)** | Proprietary | ✅ | ✅ | Commercial |
| **JFrog Xray** | JFrog Security | ✅ | ⚠️ | Commercial |

**Example CVE Alert:**
```
[WARNING] CVE-2024-38816 (CVSS 7.5) - spring-webmvc-6.1.10
[INFO] Vulnerability: Denial of Service via malformed multipart request
[INFO] Fixed in: spring-webmvc >= 6.1.11
[ACTION] Run: mvn versions:use-latest-versions -Dincludes=org.springframework:*
```

---

### EPSS - Exploit Prediction Scoring System

**Definition:** EPSS (Exploit Prediction Scoring System) is an open, data-driven effort to estimate the probability that a software vulnerability will be exploited in the wild within the next 30 days.

**Why It Matters:**
- **Prioritize what to patch** — thousands of CVEs are published each year; EPSS tells you which ones attackers are actually exploiting
- **Complement CVSS** — a CVSS 9.8 vulnerability with 0.01% EPSS is less urgent than a CVSS 6.5 with 85% EPSS
- **Reduce alert fatigue** — focus remediation effort where real-world risk is highest
- **Risk-based SLAs** — define patch timelines based on exploitation likelihood, not just severity score

#### CVSS vs. EPSS: Know the Difference

| Score | What It Measures | Who Produces It | Range | Use For |
|-------|-----------------|-----------------|-------|---------|
| **CVSS** | Severity of a vulnerability (impact if exploited) | NVD / NIST | 0–10 | Classify severity |
| **EPSS** | Probability of exploitation in the next 30 days | FIRST.org | 0–1 (0%–100%) | Prioritize patching |

> **Rule of thumb:** CVSS answers *"How bad would it be?"* — EPSS answers *"How likely is it to happen?"*

#### EPSS Score Interpretation

```
EPSS Score    Risk Level      Recommended Action
──────────────────────────────────────────────────────────────
> 0.70 (70%)  CRITICAL        Patch immediately (< 24 hours)
0.30 – 0.70   HIGH            Patch within sprint (< 1 week)
0.10 – 0.30   MEDIUM          Schedule for next release
0.01 – 0.10   LOW             Monitor; patch in quarterly cycle
< 0.01 (1%)   INFORMATIONAL   Low urgency; track in backlog
```

#### EPSS + CVSS Combined Prioritization Matrix

```
                   CVSS Score
              Low (0-3)  Med (4-6)  High (7-8)  Critical (9-10)
           ┌──────────┬──────────┬──────────┬──────────┐
High EPSS  │  Monitor  │  Patch   │  Urgent  │ Emergency │
(> 30%)    │           │  Soon    │  < 1 wk  │  < 24 hr  │
           ├──────────┼──────────┼──────────┼──────────┤
Low EPSS   │  Backlog  │  Backlog │  Schedule│  Plan     │
(< 10%)    │           │          │  release │  sprint   │
           └──────────┴──────────┴──────────┴──────────┘
```

**Quadrant Model (CISA KEV-aligned):**
- 🔴 **High CVSS + High EPSS** → Emergency patch
- 🟠 **High CVSS + Low EPSS** → Plan patch (lower urgency)
- 🟡 **Low CVSS + High EPSS** → Patch soon (actively exploited)
- 🟢 **Low CVSS + Low EPSS** → Backlog / monitor

#### Querying EPSS via API

The FIRST.org EPSS API is free and publicly available:

```bash
# Get EPSS score for a single CVE
curl -s "https://api.first.org/data/1.0/epss?cve=CVE-2024-38816" \
  | jq '.data[] | {cve: .cve, epss: .epss, percentile: .percentile}'

# Expected output:
# {
#   "cve": "CVE-2024-38816",
#   "epss": "0.00097",
#   "percentile": "0.39126"
# }

# Batch query multiple CVEs
curl -s "https://api.first.org/data/1.0/epss?cve=CVE-2024-38816,CVE-2021-44228,CVE-2023-44487" \
  | jq '.data[] | {cve: .cve, epss: (.epss | tonumber * 100 | round), percentile: .percentile}'

# Get top 100 highest-scored CVEs today
curl -s "https://api.first.org/data/1.0/epss?order=!epss&limit=100" \
  | jq '.data[] | select(.epss | tonumber > 0.5) | {cve: .cve, epss: .epss}'
```

#### Integrating EPSS into Your Build Pipeline

```bash
#!/usr/bin/env bash
# scripts/epss-check.sh — Enriches OWASP Dependency-Check output with EPSS scores
# Fails build if any CVE has EPSS > threshold

set -euo pipefail

REPORT="target/dependency-check-report.json"
EPSS_THRESHOLD="${EPSS_THRESHOLD:-0.30}"   # 30% exploitation probability
EPSS_API="https://api.first.org/data/1.0/epss"

if [ ! -f "$REPORT" ]; then
  echo "[ERROR] Dependency-Check report not found. Run mvn dependency-check:check first."
  exit 1
fi

# Extract unique CVE IDs from the report
CVES=$(jq -r '.dependencies[].vulnerabilities[]?.name // empty' "$REPORT" \
  | grep -E '^CVE-' | sort -u | tr '\n' ',' | sed 's/,$//')

if [ -z "$CVES" ]; then
  echo "[INFO] No CVEs found in dependency report."
  exit 0
fi

echo "[INFO] Querying EPSS scores for CVEs: $CVES"

# Fetch EPSS scores in batch
EPSS_DATA=$(curl -sf "${EPSS_API}?cve=${CVES}" | jq -r '.data[]')

FAIL=0
while IFS= read -r line; do
  CVE=$(echo "$line" | jq -r '.cve')
  SCORE=$(echo "$line" | jq -r '.epss')
  PERCENTILE=$(echo "$line" | jq -r '.percentile')

  # Compare as floating point
  if awk "BEGIN { exit !($SCORE >= $EPSS_THRESHOLD) }"; then
    echo "[CRITICAL] $CVE — EPSS: $(awk "BEGIN {printf \"%.1f\", $SCORE * 100}")% (p${PERCENTILE}) — EXCEEDS threshold ${EPSS_THRESHOLD}"
    FAIL=1
  else
    echo "[OK]       $CVE — EPSS: $(awk "BEGIN {printf \"%.1f\", $SCORE * 100}")% (p${PERCENTILE})"
  fi
done < <(echo "$EPSS_DATA" | jq -c '.')

if [ "$FAIL" -eq 1 ]; then
  echo ""
  echo "[FAIL] One or more CVEs exceed EPSS threshold of ${EPSS_THRESHOLD}. Patch immediately."
  exit 1
fi

echo "[PASS] All CVEs are below the EPSS exploitation threshold."
```

**Wire it into Maven:**
```xml
<!-- pom.xml — run EPSS check after dependency-check -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.3.0</version>
    <executions>
        <execution>
            <id>epss-check</id>
            <phase>verify</phase>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <executable>bash</executable>
                <arguments>
                    <argument>scripts/epss-check.sh</argument>
                </arguments>
                <environmentVariables>
                    <EPSS_THRESHOLD>0.30</EPSS_THRESHOLD>
                </environmentVariables>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### EPSS in GitHub Actions CI/CD

```yaml
# .github/workflows/epss.yml
name: EPSS Vulnerability Prioritization
on:
  push:
    branches: [main, develop]
  pull_request:
  schedule:
    - cron: '0 6 * * 1'  # Every Monday 6 AM (scores refresh daily)

jobs:
  epss-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run OWASP Dependency-Check
        run: mvn -B dependency-check:check -DfailBuildOnCVSS=10  # Don't fail yet; EPSS will decide

      - name: Install jq
        run: sudo apt-get install -y jq

      - name: Enrich CVEs with EPSS scores
        env:
          EPSS_THRESHOLD: '0.30'
        run: bash scripts/epss-check.sh

      - name: Upload enriched report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: dependency-check-report
          path: target/dependency-check-report.html
```

#### EPSS for the Marketplace

Given the marketplace handles payment data, apply tighter EPSS thresholds for payment-related dependencies:

```bash
# Marketplace-specific EPSS policy
#
# Payment-critical libs (stripe, jackson, spring-security, keycloak-adapter):
#   Threshold: EPSS > 0.10 → FAIL build
#
# All other libs:
#   Threshold: EPSS > 0.30 → FAIL build
#
# Rationale: Financial data + PCI-DSS scope = lower tolerance for exploitation risk

PAYMENT_LIBS="stripe|keycloak|spring-security|jackson-databind|bouncycastle"
PAYMENT_EPSS_THRESHOLD=0.10
DEFAULT_EPSS_THRESHOLD=0.30
```

#### EPSS Resources

| Resource | URL | Description |
|----------|-----|-------------|
| **FIRST EPSS** | https://www.first.org/epss/ | Official EPSS model documentation |
| **EPSS API** | https://api.first.org/data/1.0/epss | Free REST API (no auth required) |
| **EPSS Data Downloads** | https://epss.cyentia.com/ | Daily CSV snapshots of all CVEs |
| **CISA KEV Catalog** | https://www.cisa.gov/known-exploited-vulnerabilities-catalog | Known Exploited Vulnerabilities (highest priority) |
| **EPSS FAQ** | https://www.first.org/epss/faq | Common questions about the model |

> 💡 **Pro tip:** Combine EPSS with the **CISA KEV catalog**. Any CVE in the KEV list is already being exploited in the wild — patch immediately regardless of EPSS score.

---

### DAST - Dynamic Application Security Testing

**Definition:** Automated security testing of running applications (black-box testing).

**Why It Matters:**
- **Runtime vulnerabilities** — find issues only visible when app is running
- **Configuration flaws** — detect misconfigurations (open ports, weak TLS)
- **Business logic flaws** — test actual workflows, not just code

#### OWASP ZAP (Zed Attack Proxy)

```yaml
# .github/workflows/dast.yml
name: DAST - OWASP ZAP
on:
  push:
    branches: [main]
  schedule:
    - cron: '0 2 * * 1'  # Monday 2 AM

jobs:
  zap:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Start application
        run: |
          docker-compose up -d
          sleep 30  # Wait for app to start
      
      - name: ZAP Baseline Scan
        uses: zaproxy/action-baseline@v0.12.0
        with:
          target: 'http://localhost:8080'
          rules_file_name: '.zap/rules.tsv'
          cmd_options: '-a'  # Include AJAX spider
      
      - name: ZAP Full Scan
        uses: zaproxy/action-full-scan@v0.10.0
        with:
          target: 'http://localhost:8080'
          rules_file_name: '.zap/rules.tsv'
          cmd_options: '-j'  # AJAX spider
      
      - name: Upload ZAP Report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: zap-report
          path: report_html.html
```

**ZAP Rules Configuration:**
```tsv
# .zap/rules.tsv
# Rule ID    IGNORE    WARN    INFO
10010       IGNORE    # Cookie No HttpOnly Flag (ok for CSRF token)
10011       FAIL      # Cookie Without Secure Flag
10015       FAIL      # Incomplete or No Cache-control Header Set
10017       FAIL      # Cross-Domain JavaScript Source File Inclusion
10020       FAIL      # X-Frame-Options Header Not Set
10021       FAIL      # X-Content-Type-Options Header Missing
10023       FAIL      # Information Disclosure - Debug Error Messages
10027       FAIL      # Information Disclosure - Suspicious Comments
10054       FAIL      # Cookie Without SameSite Attribute
10055       FAIL      # CSP: script-src unsafe-inline
10096       FAIL      # Timestamp Disclosure - Unix
```

#### Nuclei (Headless DAST)

```bash
# Install Nuclei
go install -v github.com/projectdiscovery/nuclei/v3/cmd/nuclei@latest

# Run Nuclei scan
nuclei -u https://your-app.com \
  -t cves/ \
  -t vulnerabilities/ \
  -t exposures/ \
  -severity critical,high \
  -o nuclei-report.txt

# Custom template for marketplace
cat > marketplace-nuclei.yaml <<EOF
id: marketplace-commission-bypass
info:
  name: Commission Bypass via Price Manipulation
  author: security-team
  severity: critical
requests:
  - method: POST
    path:
      - "{{BaseURL}}/api/v1/orders"
    headers:
      Authorization: "Bearer {{token}}"
    body: |
      {
        "items": [
          {
            "productId": 1,
            "quantity": 1,
            "price": -100.00
          }
        ]
      }
    matchers:
      - type: word
        words:
          - "commission"
          - "negative"
        condition: and
      - type: status
        status:
          - 200
EOF

nuclei -u https://your-app.com -t marketplace-nuclei.yaml
```

**DAST Tools Comparison:**

| Tool | Coverage | Speed | Best For |
|------|----------|-------|----------|
| **OWASP ZAP** | High | Slow | Comprehensive scans |
| **Burp Suite** | Highest | Slow | Manual + automated |
| **Nuclei** | Medium | Fast | Headless CI/CD |
| **Arachni** | Medium | Medium | Web app scanning |
| **Acunetix** | High | Medium | Commercial, GUI |

**DAST Best Practices:**
- ✅ Run against staging/pre-prod (not production)
- ✅ Use authenticated scans (provide test credentials)
- ✅ Exclude destructive tests (DELETE endpoints)
- ✅ Run weekly + on release
- ✅ Integrate with SIEM for alerting

**Shift Left vs. Shift Right:**
```
┌────────────────────────────────────────────────────────────┐
│ Shift Left (Pre-Production)          Shift Right (Post)   │
├────────────────────────────────────────────────────────────┤
│ Threat Modeling                       DAST (Production)    │
│ Secret Scanner                        Pentesting           │
│ SAST                                  Bug Bounty           │
│ SCA                                   SIEM/SOC             │
│ DAST (Staging)                        Incident Response    │
└────────────────────────────────────────────────────────────┘
         ↑ Cheaper to fix              ↑ More accurate
```

---

## CISO Bookshelf

**Essential Reading for Security Engineers**

### Books

1. **"The Phoenix Project" by Gene Kim**  
   https://itrevolution.com/product/the-phoenix-project/  
   *DevOps novel - understand the business impact of security.*

2. **"The Unicorn Project" by Gene Kim**  
   https://itrevolution.com/product/the-unicorn-project/  
   *Developer-focused sequel to Phoenix Project.*

3. **"Accelerate" by Nicole Forsgren, Jez Humble, Gene Kim**  
   https://itrevolution.com/product/accelerate/  
   *Data-driven approach to building high-performing tech organizations.*

4. **"Web Application Security" by Andrew Hoffman**  
   https://www.oreilly.com/library/view/web-application-security/9781492053101/  
   *Practical OWASP Top 10 guide for developers.*

5. **"Security Engineering" by Ross Anderson**  
   https://www.cl.cam.ac.uk/~rja14/book.html  
   *Comprehensive security engineering bible (free online).*

6. **"The DevOps Handbook" by Gene Kim**  
   https://itrevolution.com/product/the-devops-handbook/  
   *Practical guide to DevOps transformation.*

7. **"Threat Modeling: Designing for Security" by Adam Shostack**  
   https://shostack.org/books/threat-modeling-book  
   *Definitive guide to practical threat modeling.*

8. **"The Basics of Hacking and Penetration Testing" by Patrick Engebretson**  
   https://www.amazon.com/Basics-Hacking-Penetration-Testing-Ethical/dp/0124116442  
   *Hands-on ethical hacking introduction.*

9. **"Building Secure and Reliable Systems" by Google**  
   https://sre.google/books/building-secure-reliable-systems/  
   *Free book on SRE + security from Google.*

10. **"Alice and Bob Learn Application Security" by Tanya Janca**  
    https://www.wiley.com/en-us/Alice+and+Bob+Learn+Application+Security-p-9781119687351  
    *Friendly, practical AppSec guide.*

### Frameworks & Standards

11. **OWASP Top 10 (2021)**  
    https://owasp.org/www-project-top-ten/  
    *Top 10 web application security risks.*

12. **OWASP ASVS (Application Security Verification Standard)**  
    https://owasp.org/www-project-application-security-verification-standard/  
    *Security requirements checklist.*

13. **CWE Top 25 Most Dangerous Software Weaknesses**  
    https://cwe.mitre.org/top25/archive/2023/2023_top25_list.html  
    *Most impactful software vulnerabilities.*

14. **NIST Cybersecurity Framework**  
    https://www.nist.gov/cyberframework  
    *Risk-based cybersecurity framework.*

15. **SANS Top 25 Software Errors**  
    https://www.sans.org/top25-software-errors/  
    *Most dangerous programming errors.*

### Blogs & Newsletters

16. **Krebs on Security**  
    https://krebsonsecurity.com/  
    *In-depth security journalism.*

17. **Troy Hunt's Blog**  
    https://www.troyhunt.com/  
    *Creator of Have I Been Pwned, web security.*

18. **Schneier on Security**  
    https://www.schneier.com/  
    *Renowned cryptographer and security expert.*

19. **Google Project Zero**  
    https://googleprojectzero.blogspot.com/  
    *Zero-day vulnerability research.*

20. **InfoSec Taylor Swift**  
    https://swiftonsecurity.com/  
    *Sysadmin humor + practical security tips.*

### Tools & Cheat Sheets

21. **OWASP Cheat Sheet Series**  
    https://cheatsheetseries.owasp.org/  
    *Concise security guidance for developers.*

22. **PayloadAllTheThings**  
    https://github.com/swisskyrepo/PayloadsAllTheThings  
    *Payload and bypass cheat sheets.*

23. **HackTricks**  
    https://book.hacktricks.xyz/  
    *Pentesting methodology and techniques.*

24. **GTFOBins**  
    https://gtfobins.github.io/  
    *Unix binaries for privilege escalation.*

25. **Exploit Database**  
    https://www.exploit-db.com/  
    *Archive of exploits and vulnerable software.*

### Training Platforms

26. **TryHackMe**  
    https://tryhackme.com/  
    *Interactive cybersecurity training.*

27. **HackTheBox**  
    https://www.hackthebox.com/  
    *Pentesting labs and challenges.*

28. **PortSwigger Web Security Academy**  
    https://portswigger.net/web-security  
    *Free web security training (Burp Suite creators).*

29. **PentesterLab**  
    https://pentesterlab.com/  
    *Hands-on pentesting exercises.*

30. **OWASP WebGoat**  
    https://owasp.org/www-project-webgoat/  
    *Deliberately insecure app for learning.*

---

## Engineering Resources

### CVE Databases

- **NVD (National Vulnerability Database)** — https://nvd.nist.gov/
- **CVE Details** — https://www.cvedetails.com/
- **Snyk Vulnerability Database** — https://security.snyk.io/
- **GitHub Advisory Database** — https://github.com/advisories

### Security Scanners

- **Trivy** (Container/IaC/SBOM) — https://github.com/aquasecurity/trivy
- **Grype** (Vulnerability scanner) — https://github.com/anchore/grype
- **Syft** (SBOM generator) — https://github.com/anchore/syft
- **TruffleHog** (Secret scanner) — https://github.com/trufflesecurity/trufflehog
- **Semgrep** (SAST) — https://semgrep.dev/
- **Snyk** (SCA/SAST/Container) — https://snyk.io/
- **OWASP ZAP** (DAST) — https://www.zaproxy.org/
- **Nuclei** (Headless DAST) — https://github.com/projectdiscovery/nuclei

### CI/CD Security

- **GitHub Actions Security** — https://docs.github.com/en/actions/security-guides
- **GitLab CI/CD Security** — https://docs.gitlab.com/ee/ci/security/
- **Jenkins Security** — https://www.jenkins.io/doc/book/security/

### Cloud Security

- **AWS Security Best Practices** — https://aws.amazon.com/security/best-practices/
- **Azure Security Benchmarks** — https://learn.microsoft.com/en-us/security/benchmark/azure/
- **GCP Security Best Practices** — https://cloud.google.com/security/best-practices

### Compliance & Standards

- **PCI DSS** — https://www.pcisecuritystandards.org/
- **SOC 2** — https://www.aicpa.org/soc4so
- **ISO 27001** — https://www.iso.org/isoiec-27001-information-security.html
- **GDPR** — https://gdpr.eu/
- **HIPAA** — https://www.hhs.gov/hipaa/

### Container Security

- **Docker Security** — https://docs.docker.com/engine/security/
- **Kubernetes Security** — https://kubernetes.io/docs/concepts/security/
- **CIS Benchmarks** — https://www.cisecurity.org/cis-benchmarks/

### API Security

- **OWASP API Security Top 10** — https://owasp.org/www-project-api-security/
- **REST API Security Cheat Sheet** — https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html

### Developer Tools

- **git-secrets** (Prevent committing secrets) — https://github.com/awslabs/git-secrets
- **pre-commit** (Git hook framework) — https://pre-commit.com/
- **EditorConfig** (Code style consistency) — https://editorconfig.org/

---

## Implementation Checklist

### Phase 1: Foundation (Week 1)

- [ ] **Unit Testing**
  - [ ] Set up JUnit 5 + Mockito + AssertJ
  - [ ] Configure JaCoCo for code coverage
  - [ ] Set coverage threshold to 80%
  - [ ] Add ArchUnit for hexagonal architecture tests
  - [ ] Run: `mvn clean verify`

- [ ] **Static Code Analysis**
  - [ ] Configure SonarQube quality gates
  - [ ] Add SpotBugs + FindSecBugs plugin
  - [ ] Add Checkstyle (Google style)
  - [ ] Add PMD code quality checks
  - [ ] Run: `mvn clean verify sonar:sonar`

- [ ] **SBOM Generation**
  - [ ] Add CycloneDX Maven plugin
  - [ ] Generate SBOM on every build
  - [ ] Attach SBOM to GitHub releases
  - [ ] Run: `mvn cyclonedx:makeAggregateBom`

### Phase 2: Security Shift Left (Week 2)

- [ ] **Threat Modeling**
  - [ ] Create data flow diagram (DFD)
  - [ ] Apply STRIDE to each component
  - [ ] Document threats in `THREAT_MODEL.md`
  - [ ] Review with security team

- [ ] **Secret Scanner**
  - [ ] Add TruffleHog to CI/CD
  - [ ] Configure pre-commit hook
  - [ ] Scan git history for secrets
  - [ ] Rotate any exposed secrets

- [ ] **SAST**
  - [ ] Add Semgrep to CI/CD
  - [ ] Configure OWASP Top 10 rules
  - [ ] Add custom marketplace security rules
  - [ ] Fail build on HIGH/CRITICAL

- [ ] **SCA**
  - [ ] Add OWASP Dependency-Check
  - [ ] Configure Dependabot
  - [ ] Set up Snyk monitoring
  - [ ] Fail build on CVSS >= 7

- [ ] **EPSS**
  - [ ] Add `scripts/epss-check.sh` to repository
  - [ ] Wire EPSS check into Maven `verify` phase via exec-maven-plugin
  - [ ] Set EPSS threshold to 0.30 (general) / 0.10 (payment-critical libs)
  - [ ] Add EPSS check step to GitHub Actions CI/CD workflow
  - [ ] Schedule weekly EPSS re-evaluation (scores refresh daily)
  - [ ] Cross-reference CVEs against CISA KEV catalog
  - [ ] Document EPSS SLAs in `security-governance.md`

### Phase 3: Runtime Security (Week 3)

- [ ] **DAST**
  - [ ] Set up OWASP ZAP baseline scan
  - [ ] Configure ZAP full scan (weekly)
  - [ ] Add Nuclei for headless scans
  - [ ] Run against staging environment

- [ ] **Container Security**
  - [ ] Scan Docker images with Trivy
  - [ ] Use distroless base images
  - [ ] Run as non-root user
  - [ ] Sign images with Cosign

- [ ] **Observability**
  - [ ] Set up structured logging (Splunk)
  - [ ] Add security event monitoring
  - [ ] Configure alerts for security events
  - [ ] Implement audit logging

### Phase 4: Continuous Monitoring (Ongoing)

- [ ] **Weekly**
  - [ ] Review Dependabot PRs
  - [ ] Review DAST scan results
  - [ ] Check SonarQube quality gates
  - [ ] Update SBOM

- [ ] **Monthly**
  - [ ] Review and update threat model
  - [ ] Rotate secrets/credentials
  - [ ] Review security incidents
  - [ ] Update security documentation

- [ ] **Quarterly**
  - [ ] External penetration test
  - [ ] Security architecture review
  - [ ] Update security training
  - [ ] Bug bounty program review

---

## Quick Reference Commands

```bash
# Unit Testing
mvn clean test                          # Run all tests
mvn test -Dtest=OrderServiceTest       # Run specific test
mvn verify jacoco:report               # Generate coverage report

# Static Code Analysis
mvn spotbugs:check                      # Find bugs
mvn checkstyle:check                   # Code style
mvn pmd:check                          # Code quality
mvn sonar:sonar                        # SonarQube scan

# SBOM
mvn cyclonedx:makeAggregateBom         # Generate SBOM
grype sbom:target/bom.json             # Scan SBOM

# Security Scanning
mvn dependency-check:check             # CVE scan
trufflehog git file://. --only-verified # Secret scan
semgrep scan --config=auto             # SAST scan

# EPSS
bash scripts/epss-check.sh                                    # Check EPSS for all CVEs in report
curl -s "https://api.first.org/data/1.0/epss?cve=CVE-2024-38816" | jq '.data[]'  # Single CVE lookup
curl -s "https://api.first.org/data/1.0/epss?order=!epss&limit=10" | jq '.data[]' # Top 10 exploitable CVEs

# Container Security
docker build -t app:latest .           # Build image
trivy image app:latest                 # Scan image
docker run --user 1000:1000 app        # Run as non-root

# DAST
zap-baseline.py -t http://localhost:8080  # ZAP scan
nuclei -u https://app.com -severity high  # Nuclei scan
```

---

## Conclusion

**Security is not a checkbox — it's a culture.**

By implementing these testing and security practices, you:
- ✅ **Shift security left** — find issues early in SDLC
- ✅ **Build quality in** — prevent defects, don't detect them
- ✅ **Automate everywhere** — make security invisible to developers
- ✅ **Fail fast** — block insecure code from reaching production
- ✅ **Continuous improvement** — measure, learn, iterate

**Remember:**
> "The only system which is truly secure is one which is switched off and unplugged, locked in a titanium lined safe, buried in a concrete bunker, and is surrounded by nerve gas and very highly paid armed guards. Even then, I wouldn't stake my life on it."  
> — Gene Spafford

**Start small, automate everything, and make security boring.**

---

*Last Updated: June 28, 2026*  
*For questions, contact: security-team@example.com*

