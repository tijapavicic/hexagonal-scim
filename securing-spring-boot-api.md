# Securing Spring Boot APIs — Critical Application Guide

> **Principal Engineer's Note:**  
> "Security is not a feature you bolt on at the end. It's an architecture decision you make at the beginning.  
> Validate everything coming in. Encode everything going out. Trust nothing by default.  
> **assume breach at all times**. Design for containment, not just prevention."

> ⚠️ **Context:** This guide covers production-grade hardening for **critical  systems**  
> subject to **PCI-DSS v4**, **SOX**, **GDPR**, **Basel III**, and ** regulatory frameworks**.  
> Every control here maps to a compliance requirement. Skipping any is a regulatory risk.

---

## Table of Contents

1. [Security Architecture Overview](#security-architecture-overview)
2. [Security Model — Zero Trust](#security-model--zero-trust)
3. [Input Sanitization](#input-sanitization)
4. [Request Validation](#request-validation)
5. [Output Encoding](#output-encoding)
6. [Authentication & Authorization](#authentication--authorization)
7. [Strong Authentication — MFA & FIDO2](#strong-authentication--mfa--fido2)
8. [mTLS & Service-to-Service Security](#mtls--service-to-service-security)
9. [HTTP Security Headers](#http-security-headers)
10. [SQL Injection Prevention](#sql-injection-prevention)
11. [Data Encryption — At Rest & In Transit](#data-encryption--at-rest--in-transit)
12. [PAN Masking, Tokenization & PCI-DSS](#pan-masking-tokenization--pci-dss)
13. [Transaction Signing & Non-Repudiation](#transaction-signing--non-repudiation)
14. [HSM Integration](#hsm-integration)
15. [Secrets Management — HashiCorp Vault](#secrets-management--hashicorp-vault)
16. [Rate Limiting & DoS Protection](#rate-limiting--dos-protection)
17. [Fraud Detection & Anti-Automation](#fraud-detection--anti-automation)
18. [Error Handling & Information Disclosure](#error-handling--information-disclosure)
19. [Logging Security Events & Regulatory Audit Trail](#logging-security-events--regulatory-audit-trail)
20. [Dependency Security](#dependency-security)
21. [Incident Response & Breach Notification](#incident-response--breach-notification)
22. [OWASP Top 10 Mapping](#owasp-top-10-mapping)
23. [PCI-DSS v4 Compliance Controls](#pci-dss-v4-compliance-controls)
24. [Security Testing Integration](#security-testing-integration)
25. [Security Checklist](#security-checklist)

---

## Security Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API REQUEST LIFECYCLE (Zero Trust)                │
│                                                                             │
│  Client ──► [WAF + DDoS Shield] ──► [mTLS Termination] ──► [API Gateway]  │
│                                                                             │
│      ──► [Rate Limiter] ──► [MFA/JWT Validator] ──► [Fraud Score Check]    │
│                                                                             │
│      ──► [Input Sanitizer] ──► [@Valid Validator] ──► [Controller]         │
│                                                                             │
│      ──► [Service Layer] ──► [Encrypted Repository] ──► [Encrypted DB]     │
│                                                                             │
│      ──► [HSM Key Operations] ──► [Transaction Signer]                     │
│                                                                             │
│  Response ◄── [Output Encoder] ◄── [PAN Masker] ◄── [Security Headers]    │
│                                                                             │
│  Every request: ──► [Immutable Audit Log] ──► [SIEM/SOC] ──► [Alerting]   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

Layer                    What It Prevents
─────────────────────────────────────────────────────────────────────────────
WAF + DDoS Shield        Known attack signatures, volumetric DDoS
mTLS Termination         Impersonation, MITM between services
API Gateway              Unauthorized API access, schema violations
Rate Limiter             DoS, brute force, credential stuffing
MFA/JWT Validator        Stolen tokens, session hijacking
Fraud Score Check        Suspicious transaction patterns
Input Sanitizer          XSS, injection via malicious content
Bean Validation          Business rule violations, type errors
Encrypted Repository     Data leakage via DB dump
HSM Key Operations       Key compromise, software-side key extraction
Output Encoder           XSS, data leakage in responses
PAN Masker               PCI-DSS: card numbers in responses
Security Headers         Clickjacking, MIME sniffing, XSS
Immutable Audit Log      Regulatory non-compliance, repudiation
```

---

## Security Model — Zero Trust

### Zero Trust Principles for critical APIs

> **"Never trust, always verify. Assume every request is hostile until proven otherwise."**  
> — NIST SP 800-207 Zero Trust Architecture

```
Traditional Perimeter Model (BROKEN):
  Outside ────[Firewall]──── Inside (trusted)
                               All internal traffic trusted ← WRONG

Zero Trust Model (CORRECT):
  Every request → Verified:
    1. Who are you?       (Identity: JWT + MFA)
    2. What device?       (Device posture: cert, MDM check)
    3. Are you allowed?   (Authorization: RBAC + resource ownership)
    4. Is this normal?    (Behavioral: fraud score, anomaly detection)
    5. Log everything.    (Non-repudiation: immutable audit trail)
```

### Zero Trust Implementation

```java
package com.example.user.config.security;

import org.springframework.stereotype.Component;

/**
 * Zero Trust Request Evaluator — evaluates every request against all trust signals.
 *
 * Requirement:
 * - PCI-DSS 4.0 Req 7: Restrict access to cardholder data by need-to-know
 * - PCI-DSS 4.0 Req 8: Identify users and authenticate access
 * - NIST 800-207: Continuous verification, least privilege
 */
@Component
public class ZeroTrustEvaluator {

    private final JwtValidator jwtValidator;
    private final DevicePostureService devicePostureService;
    private final FraudScoreService fraudScoreService;
    private final BehavioralAnalysisService behavioralAnalysisService;

    /**
     * Evaluate trust for a request. Returns TrustDecision with allow/deny + reason.
     * Called on EVERY request before business logic.
     */
    public TrustDecision evaluate(ZeroTrustContext ctx) {

        // 1. Identity: valid JWT with MFA claim
        if (!jwtValidator.isValid(ctx.token())) {
            return TrustDecision.deny("INVALID_TOKEN");
        }
        if (ctx.isSensitiveOperation() && !jwtValidator.hasMfaClaim(ctx.token())) {
            return TrustDecision.deny("MFA_REQUIRED");
        }

        // 2. Device: certificate-bound token (RFC 8705 DPoP or mTLS binding)
        if (ctx.requiresDeviceBinding() && !devicePostureService.isBound(ctx)) {
            return TrustDecision.deny("DEVICE_NOT_BOUND");
        }

        // 3. Behavioral: anomaly check (geo, time, velocity)
        double fraudScore = fraudScoreService.score(ctx);
        if (fraudScore > 0.85) {
            return TrustDecision.deny("HIGH_FRAUD_SCORE");
        }
        if (fraudScore > 0.60) {
            return TrustDecision.stepUp("STEP_UP_AUTH_REQUIRED"); // Trigger MFA re-auth
        }

        // 4. Resource: least-privilege scopes
        if (!behavioralAnalysisService.isWithinNormalPattern(ctx)) {
            return TrustDecision.flag("ANOMALOUS_PATTERN"); // Allow but alert SOC
        }

        return TrustDecision.allow();
    }

    public record ZeroTrustContext(
        String token,
        String clientIp,
        String deviceId,
        String userAgent,
        String resourceType,
        String action,
        boolean isSensitiveOperation,   // High-value transfers, config changes
        boolean requiresDeviceBinding   // Mobile, admin ops
    ) {}

    public record TrustDecision(
        Decision decision,
        String reason,
        boolean requiresStepUp
    ) {
        enum Decision { ALLOW, DENY, STEP_UP, FLAGGED }

        static TrustDecision allow() { return new TrustDecision(Decision.ALLOW, null, false); }
        static TrustDecision deny(String reason) { return new TrustDecision(Decision.DENY, reason, false); }
        static TrustDecision stepUp(String reason) { return new TrustDecision(Decision.STEP_UP, reason, true); }
        static TrustDecision flag(String reason) { return new TrustDecision(Decision.FLAGGED, reason, false); }
    }
}
```

### Network Segmentation ( Architecture)

```yaml
# docker-compose.yml — network segmentation for critical services
networks:
  # DMZ: public-facing services only
  dmz:
    driver: bridge
    ipam:
      config:
        - subnet: 10.0.1.0/24

  # App tier: business logic, internal APIs
  app-tier:
    driver: bridge
    internal: true                    # No direct internet access
    ipam:
      config:
        - subnet: 10.0.2.0/24

  # Data tier: databases ONLY — most restricted
  data-tier:
    driver: bridge
    internal: true                    # No direct internet access
    ipam:
      config:
        - subnet: 10.0.3.0/24

  # Management: monitoring, vault, admin
  mgmt:
    driver: bridge
    internal: true
    ipam:
      config:
        - subnet: 10.0.4.0/24

services:
  api-gateway:
    networks: [dmz, app-tier]         # Bridges DMZ to app tier

  hex-application:
    networks: [app-tier, data-tier]   # App tier to DB only
    # NO DMZ access — cannot be reached directly from internet

  postgres:
    networks: [data-tier]             # ONLY data-tier — completely isolated
    ports: []                         # NO exposed ports

  vault:
    networks: [app-tier, mgmt]        # App can pull secrets; management can admin
    ports: []                         # NO exposed ports

  splunk:
    networks: [app-tier, mgmt]        # Receives logs; not internet-facing
```

---

## Strong Authentication — MFA & FIDO2

> **Critical Requirement:** PCI-DSS v4.0 Req 8.4 — MFA required for all non-console admin access  
> and all remote access. PSD2 (EU) requires Strong Customer Authentication (SCA) for payments.

### MFA Implementation

```java
package com.example.user.config.security.mfa;

import org.springframework.stereotype.Service;

/**
 * Multi-Factor Authentication Service.
 *
 * Regulation: PCI-DSS 4.0 Req 8.4, PSD2 Article 4 (SCA)
 * Supported factors:
 *   - TOTP (RFC 6238) — Google Authenticator, Authy
 *   - FIDO2/WebAuthn — Hardware keys (YubiKey), biometrics
 *   - SMS OTP — weakest, avoid for high-value ops
 *   - Push notifications — Duo, OKTA Verify
 */
@Service
public class MfaService {

    private final TotpValidator totpValidator;
    private final Fido2Validator fido2Validator;
    private final OtpStore otpStore;

    /**
     * Verify TOTP code (RFC 6238 — time-based one-time password).
     * Uses 30-second window with 1-step tolerance.
     */
    public boolean verifyTotp(String userId, String code) {
        String secret = totpStore.getSecret(userId)
            .orElseThrow(() -> new MfaNotEnrolledException(userId));

        // Validate with ±1 time window tolerance (clock skew)
        return totpValidator.isValid(secret, code, 1);
    }

    /**
     * Verify FIDO2/WebAuthn assertion — hardware key or biometric.
     * Most secure factor; required for privileged operations.
     */
    public boolean verifyFido2(String userId, Fido2AssertionResponse assertion) {
        return fido2Validator.verify(userId, assertion);
    }

    /**
     * Step-up authentication — require MFA re-verification for sensitive operations:
     * - Wire transfers > threshold amount
     * - Beneficiary management
     * - Password/security settings changes
     * - Privileged admin operations
     */
    public StepUpToken issueStepUpToken(String userId, StepUpOperation operation, String mfaProof) {
        if (!isValidMfaProof(userId, mfaProof)) {
            throw new InvalidMfaException("Step-up MFA verification failed");
        }

        // Issue short-lived, operation-scoped token (max 5 minutes)
        return StepUpToken.issue(userId, operation, Duration.ofMinutes(5));
    }

    public enum StepUpOperation {
        WIRE_TRANSFER,
        BENEFICIARY_ADD,
        CHANGE_PASSWORD,
        CHANGE_SECURITY_SETTINGS,
        ADMIN_OPERATION,
        LARGE_PAYMENT          // > configurable threshold
    }
}
```

```java
/**
 * FIDO2/WebAuthn Controller for hardware key registration and authentication.
 * Eliminates phishing — hardware key cannot be used on a fake domain.
 *
 * Use: Admin access, privileged operations, high-value transfers.
 */
@RestController
@RequestMapping("/api/v1/auth/fido2")
public class Fido2Controller {

    @PostMapping("/register/start")
    public PublicKeyCredentialCreationOptions startRegistration(
        @AuthenticationPrincipal User user
    ) {
        return fido2Service.startRegistration(user.id(), user.email());
    }

    @PostMapping("/register/finish")
    public void finishRegistration(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody Fido2RegistrationResponse response
    ) {
        fido2Service.finishRegistration(user.id(), response);
        auditLogger.logSecurityEvent("FIDO2_KEY_REGISTERED", user.id(), "Hardware key registered");
    }

    @PostMapping("/authenticate")
    public JwtResponse authenticate(
        @Valid @RequestBody Fido2AuthenticationRequest request
    ) {
        Fido2AuthResult result = fido2Service.authenticate(request);
        if (!result.isSuccess()) {
            auditLogger.logAuthFailure(request.userId(), "FIDO2_FAILURE");
            throw new AuthenticationException("FIDO2 authentication failed");
        }
        return jwtService.issueToken(result.userId(), MfaMethod.FIDO2);
    }
}
```

### PSD2 Strong Customer Authentication (SCA)

```java
/**
 * PSD2 Strong Customer Authentication for payment initiation.
 *
 * PSD2 Requirement: At least 2 of 3 factors:
 *   - Knowledge: something you know (password, PIN)
 *   - Possession: something you have (hardware token, phone)
 *   - Inherence: something you are (biometric)
 *
 * Dynamic linking: Authentication code must be linked to:
 *   - Payee identity
 *   - Payment amount
 */
@Service
public class ScaService {

    /**
     * Validate that the SCA token is dynamically linked to this specific payment.
     * Prevents attackers from reusing SCA for different amounts/beneficiaries.
     */
    public void validateDynamicLinking(Payment payment, ScaToken scaToken) {
        String expectedBinding = computeBinding(payment.amount(), payment.beneficiaryIban());
        if (!scaToken.binding().equals(expectedBinding)) {
            auditLogger.logSuspiciousActivity(
                payment.userId(),
                "SCA_BINDING_MISMATCH",
                "SCA token not bound to this payment"
            );
            throw new ScaBindingException("SCA not dynamically linked to payment");
        }

        if (scaToken.isExpired()) {
            throw new ScaExpiredException("SCA token expired");
        }
    }

    private String computeBinding(BigDecimal amount, String iban) {
        // HMAC-SHA256 of amount + IBAN — binding to specific transaction
        return hmacSha256(amount.toPlainString() + "|" + iban);
    }
}
```

---

## mTLS & Service-to-Service Security

> ** Requirement:** All inter-service communication must be mutually authenticated.  
> No service trusts another service without a valid client certificate.

### Spring Boot mTLS Configuration

```yaml
# application.yml — mTLS for inter-service communication
server:
  ssl:
    enabled: true
    key-store: classpath:keystore/service.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: hex-application
    trust-store: classpath:keystore/truststore.p12
    trust-store-password: ${TRUSTSTORE_PASSWORD}
    trust-store-type: PKCS12
    client-auth: need                           # REQUIRE client certificates
    protocol: TLS
    enabled-protocols:
      - TLSv1.3                                 # TLS 1.3 only — PCI-DSS req
    ciphers:
      # FIPS 140-2 approved cipher suites only
      - TLS_AES_256_GCM_SHA384
      - TLS_CHACHA20_POLY1305_SHA256
      - TLS_AES_128_GCM_SHA256
```

```java
/**
 * RestTemplate / WebClient with mTLS client certificates.
 * Used for all outbound inter-service calls.
 *
 * Requirement:
 * - PCI-DSS 4.0 Req 4.2.1: Strong cryptography for all cardholder data in transit
 * - TLS 1.3 minimum — no TLS 1.0/1.1/1.2 without compensating controls
 */
@Configuration
public class HttpClientConfig {

    @Value("${client.keystore.path}")
    private String keystorePath;

    @Value("${client.keystore.password}")
    private String keystorePassword;

    @Value("${client.truststore.path}")
    private String truststorePath;

    @Bean
    public RestTemplate mtlsRestTemplate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(keystorePath), keystorePassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(new FileInputStream(truststorePath), keystorePassword.toCharArray());

        SSLContext sslContext = SSLContextBuilder.create()
            .loadKeyMaterial(keyStore, keystorePassword.toCharArray())
            .loadTrustMaterial(trustStore, null)
            .setProtocol("TLSv1.3")
            .build();

        HttpClient httpClient = HttpClients.custom()
            .setSSLContext(sslContext)
            .setSSLHostnameVerifier(SSLConnectionSocketFactory.getDefaultHostnameVerifier())
            .build();

        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        return new RestTemplate(factory);
    }
}
```

### Certificate Rotation (Automated)

```bash
#!/usr/bin/env bash
# scripts/rotate-service-certs.sh
# Automate certificate rotation — never let certs expire silently
# Requirement: PCI-DSS certificates rotated annually minimum, monitoring for expiry

set -euo pipefail

CERT_DIR="/etc/ssl/services"
VAULT_ADDR="${VAULT_ADDR:-https://vault.internal.com:8200}"
SERVICE_NAME="${SERVICE_NAME:-hex-application}"
TTL="${CERT_TTL:-8760h}"  # 1 year max

echo "[INFO] Requesting new certificate from Vault PKI for $SERVICE_NAME"

# Issue cert from Vault PKI CA
vault write -format=json "pki/issue/service-certs" \
    common_name="${SERVICE_NAME}.internal.com" \
    ttl="${TTL}" \
    ip_sans="10.0.2.100" > /tmp/cert.json

jq -r '.data.certificate' /tmp/cert.json > "${CERT_DIR}/${SERVICE_NAME}.crt"
jq -r '.data.private_key' /tmp/cert.json > "${CERT_DIR}/${SERVICE_NAME}.key"
jq -r '.data.ca_chain[]' /tmp/cert.json > "${CERT_DIR}/ca-chain.crt"

# Secure key permissions
chmod 600 "${CERT_DIR}/${SERVICE_NAME}.key"
chmod 644 "${CERT_DIR}/${SERVICE_NAME}.crt"

echo "[INFO] Certificate rotation complete. Reloading service..."
kill -HUP "$(cat /var/run/${SERVICE_NAME}.pid)"

# Alert SIEM
logger -t cert-rotation "event=CERT_ROTATED service=${SERVICE_NAME} ttl=${TTL}"
```

---

## Data Encryption — At Rest & In Transit

> ** Requirement:** All sensitive data (cardholder data, PII, account numbers) must be  
> encrypted at rest with AES-256 and in transit with TLS 1.3.  
> PCI-DSS 4.0 Req 3.5: Protect stored account data with strong cryptography.

### Field-Level Encryption (JPA + AES-256-GCM)

```java
package com.example.user.config.security.encryption;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field-level encryption for sensitive database columns.
 *
 * Why AES-256-GCM:
 * - 256-bit key: FIPS 140-2 approved, NSA Suite B for TOP SECRET
 * - GCM mode: authenticated encryption (detects tampering)
 * - Random IV per encryption: prevents IV reuse attacks
 * - Authentication tag: 128-bit GCM tag prevents bit-flipping
 *
 *  Requirement:
 * - PCI-DSS 4.0 Req 3.5.1: PAN must be rendered unreadable anywhere stored
 * - FIPS 140-2: Approved algorithms only in regulated environments
 */
@Component
public class FieldEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;       // 96-bit IV (GCM recommended)
    private static final int GCM_TAG_LENGTH = 128;     // 128-bit authentication tag
    private static final int KEY_SIZE = 256;

    private final SecretKey encryptionKey;

    public FieldEncryptionService(VaultKeyProvider vaultKeyProvider) {
        // Keys ALWAYS from HSM/Vault — NEVER hardcoded
        this.encryptionKey = vaultKeyProvider.getDataEncryptionKey("field-encryption-key");
    }

    /**
     * Encrypt a sensitive field value.
     * Returns: Base64(IV || CipherText || GCM Tag)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = generateSecureIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey,
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext for storage
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new EncryptionException("Field encryption failed", e);
        }
    }

    /**
     * Decrypt a field value. Verifies GCM authentication tag (detects tampering).
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);

            // Extract IV
            byte[] iv = Arrays.copyOfRange(decoded, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(decoded, GCM_IV_LENGTH, decoded.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey,
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            // GCM will throw AEADBadTagException if data was tampered
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            // DATA TAMPERING DETECTED — alert SOC immediately
            auditLogger.logCriticalSecurityEvent("DATA_TAMPER_DETECTED",
                "GCM authentication tag verification failed — data may be corrupted or tampered");
            throw new DataIntegrityException("Data integrity check failed — possible tampering", e);
        } catch (Exception e) {
            throw new EncryptionException("Field decryption failed", e);
        }
    }

    private byte[] generateSecureIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
```

### JPA Entity with Encrypted Fields

```java
package com.example.user.adapter.entity;

import jakarta.persistence.*;

/**
 * Account entity with AES-256-GCM encrypted sensitive fields.
 *
 * Encrypted columns:
 * - iban: account identifier — encrypted at rest
 * - accountHolderName: PII — encrypted at rest (GDPR Art. 32)
 * - sortCode: routing information — encrypted
 *
 * Non-sensitive (not encrypted):
 * - id, accountType, status, createdAt — safe for query/index
 */
@Entity
@Table(name = "accounts")
@EntityListeners(EncryptionListener.class)
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iban_encrypted", nullable = false, length = 512)
    private String ibanEncrypted;              // Encrypted AES-256-GCM

    @Column(name = "account_holder_name_encrypted", length = 512)
    private String accountHolderNameEncrypted; // Encrypted AES-256-GCM

    @Column(name = "sort_code_encrypted", length = 256)
    private String sortCodeEncrypted;          // Encrypted AES-256-GCM

    @Column(name = "iban_hash", nullable = false, length = 64, unique = true)
    private String ibanHash;                   // HMAC-SHA256 for lookup (not decryptable alone)

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;                // Not sensitive — not encrypted

    @Column(name = "status", nullable = false, length = 20)
    private String status;                     // Not sensitive

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

/**
 * JPA listener that transparently encrypts/decrypts on save/load.
 */
@Component
public class EncryptionListener {

    private static FieldEncryptionService encryptionService;

    @Autowired
    public void setEncryptionService(FieldEncryptionService service) {
        EncryptionListener.encryptionService = service;
    }

    @PrePersist
    @PreUpdate
    public void encrypt(AccountEntity entity) {
        entity.setIbanEncrypted(encryptionService.encrypt(entity.getIban()));
        entity.setAccountHolderNameEncrypted(encryptionService.encrypt(entity.getAccountHolderName()));
        entity.setSortCodeEncrypted(encryptionService.encrypt(entity.getSortCode()));
        entity.setIbanHash(hmacSha256(entity.getIban())); // Deterministic hash for lookups
    }

    @PostLoad
    public void decrypt(AccountEntity entity) {
        entity.setIban(encryptionService.decrypt(entity.getIbanEncrypted()));
        entity.setAccountHolderName(encryptionService.decrypt(entity.getAccountHolderNameEncrypted()));
        entity.setSortCode(encryptionService.decrypt(entity.getSortCodeEncrypted()));
    }
}
```

### Database Encryption at Rest (PostgreSQL TDE)

```sql
-- PostgreSQL Transparent Data Encryption + row-level security

-- Enable TDE (PostgreSQL 16+ with pg_tde extension)
CREATE EXTENSION IF NOT EXISTS pg_tde;
SELECT pg_tde_add_key_provider_file('vault-provider', '/etc/vault/tde-key.json');
SELECT pg_tde_set_principal_key('tde-key', 'vault-provider');

-- Row-Level Security: each service can only see its own data
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY accounts_isolation_policy ON accounts
    USING (service_name = current_user);

-- Audit all access to sensitive tables
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    event_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    session_user TEXT NOT NULL DEFAULT session_user,
    table_name  TEXT NOT NULL,
    operation   TEXT NOT NULL,
    row_id      BIGINT,
    old_values  JSONB,
    new_values  JSONB
) TABLESPACE encrypted_tablespace;  -- On encrypted tablespace

-- Trigger audit on all sensitive table changes
CREATE OR REPLACE FUNCTION audit_trigger() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (table_name, operation, row_id, old_values, new_values)
    VALUES (TG_TABLE_NAME, TG_OP,
            COALESCE(NEW.id, OLD.id),
            row_to_json(OLD),
            row_to_json(NEW));
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER accounts_audit
    AFTER INSERT OR UPDATE OR DELETE ON accounts
    FOR EACH ROW EXECUTE FUNCTION audit_trigger();
```

---

## PAN Masking, Tokenization & PCI-DSS

> **PCI-DSS 4.0 Requirement 3:** Do not store sensitive authentication data after authorization.  
> **Requirement 3.3.1:** SAD (Sensitive Authentication Data) must not be retained after auth.  
> **Requirement 3.5.1:** PAN must be rendered unreadable anywhere stored.

### PAN Masking

```java
package com.example.user.config.security.pci;

import org.springframework.stereotype.Component;

/**
 * PAN (Primary Account Number) masker.
 *
 * PCI-DSS 4.0 Req 3.5.1: Mask PAN when displayed.
 * Allowed: First 6 and last 4 digits maximum (e.g., 4111 11** **** 1111).
 * All other digits must be masked or truncated.
 *
 * NEVER log full PANs. NEVER return full PANs in API responses.
 */
@Component
public class PanMaskingService {

    /**
     * Mask PAN per PCI-DSS: show first 6 and last 4 only.
     * "4111111111111111" → "411111******1111"
     */
    public String maskPan(String pan) {
        if (pan == null || pan.length() < 13 || pan.length() > 19) {
            return "****";
        }
        String digits = pan.replaceAll("[^0-9]", "");
        int len = digits.length();
        return digits.substring(0, 6) +
               "*".repeat(len - 10) +
               digits.substring(len - 4);
    }

    /**
     * Truncate PAN to last 4 digits only (for low-privilege display contexts).
     * "4111111111111111" → "****1111"
     */
    public String truncatePan(String pan) {
        if (pan == null || pan.length() < 4) return "****";
        String digits = pan.replaceAll("[^0-9]", "");
        return "****" + digits.substring(digits.length() - 4);
    }

    /**
     * Mask IBAN: show first 4 (country+check) and last 4 only.
     * "GB29NWBK60161331926819" → "GB29**************6819"
     */
    public String maskIban(String iban) {
        if (iban == null || iban.length() < 8) return "****";
        return iban.substring(0, 4) +
               "*".repeat(iban.length() - 8) +
               iban.substring(iban.length() - 4);
    }

    /**
     * Mask sort code — show only last 2 digits.
     * "206531" → "****31"
     */
    public String maskSortCode(String sortCode) {
        if (sortCode == null || sortCode.length() < 2) return "****";
        return "*".repeat(sortCode.length() - 2) + sortCode.substring(sortCode.length() - 2);
    }
}
```

### Tokenization (Replace PAN with Token)

```java
/**
 * Payment Tokenization Service — replaces PAN with non-sensitive token.
 *
 * Tokenization is BETTER than encryption for reducing PCI scope:
 * - Token has NO mathematical relationship to PAN
 * - Systems that only see tokens are OUT of PCI scope
 * - Token vault is isolated to a minimal PCI-scoped system
 *
 * PCI-DSS 4.0: Tokens may be used as a substitute for PAN to reduce scope.
 *
 * Two strategies:
 * 1. Vault-based: store PAN → token mapping in a secure vault (this service)
 * 2. Format-preserving: use FF3-1 (NIST SP 800-38G) — passes Luhn check, looks like a real card
 */
@Service
public class TokenizationService {

    private final TokenVault tokenVault;  // Isolated, PCI-scoped token storage

    /**
     * Tokenize a PAN — returns opaque token safe to store in non-PCI systems.
     */
    public String tokenize(String pan) {
        validateLuhn(pan);

        // Check if token already exists for this PAN (idempotent)
        return tokenVault.findTokenByPan(pan)
            .orElseGet(() -> {
                String token = generateOpaqueToken();
                tokenVault.store(pan, token, Instant.now().plus(Duration.ofDays(365)));
                auditLogger.log("event=PAN_TOKENIZED maskedPan=" + maskPan(pan));
                return token;
            });
    }

    /**
     * Detokenize — retrieve PAN from token.
     * Only called in PCI-scoped systems (payment processor).
     */
    @PreAuthorize("hasRole('PAYMENT_PROCESSOR')")  // Strict role gate
    public String detokenize(String token) {
        auditLogger.log("event=PAN_DETOKENIZED token=" + token.substring(0, 8) + "...");
        return tokenVault.findPanByToken(token)
            .orElseThrow(() -> new TokenNotFoundException("Token not found or expired"));
    }

    private String generateOpaqueToken() {
        // Cryptographically random, URL-safe token — no relation to PAN
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "tok_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateLuhn(String pan) {
        // Validate card number with Luhn algorithm before tokenizing
        if (!LuhnValidator.isValid(pan)) {
            throw new InvalidPanException("Invalid card number — failed Luhn check");
        }
    }
}
```

---

## Transaction Signing & Non-Repudiation

> ** Requirement:**  
> - Non-repudiation: a customer cannot deny authorizing a transaction  
> - Digital signatures provide cryptographic proof of authorization  
> - Required for high-value transfers, regulatory audit, legal evidence  
> - eIDAS (EU) Regulation: qualified electronic signatures for regulated transactions

```java
package com.example.user.config.security.signing;

import java.security.*;
import java.util.Base64;

/**
 * Transaction Digital Signature Service.
 *
 * Algorithm: ECDSA with P-256 curve (FIPS 186-4, NIST approved)
 *   - Why ECDSA: faster than RSA, smaller signatures, equally secure at 256-bit
 *   - P-256 = 128-bit security level (equivalent to RSA-3072)
 *   - Hash: SHA-256
 *
 *  Requirements:
 * - Non-repudiation: signed transactions cannot be denied
 * - PSD2 Article 4: dynamic linking (signature binds to specific transaction)
 * - eIDAS: qualified electronic signatures for high-value transactions
 * - PCI-DSS 4.0 Req 10.3: Protect audit records from unauthorized changes
 *
 * Key storage: HSM only — private keys NEVER leave the HSM.
 */
@Service
public class TransactionSigningService {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final HsmKeyService hsmKeyService;

    /**
     * Sign a transaction — creates cryptographic proof of authorization.
     * The signing key is stored in HSM and never leaves it.
     */
    public TransactionSignature sign(Transaction transaction) {
        String canonicalForm = buildCanonicalForm(transaction);
        byte[] dataToSign = canonicalForm.getBytes(StandardCharsets.UTF_8);

        // HSM performs signing — private key never exposed
        byte[] signatureBytes = hsmKeyService.sign(
            dataToSign,
            HsmKeyService.KeyAlgorithm.ECDSA_P256,
            transaction.userId() + "-signing-key"
        );

        return new TransactionSignature(
            Base64.getEncoder().encodeToString(signatureBytes),
            transaction.id(),
            Instant.now(),
            "ECDSA-SHA256",
            hsmKeyService.getPublicKeyFingerprint(transaction.userId())
        );
    }

    /**
     * Verify a transaction signature — confirms transaction was authorized.
     * Used during dispute resolution, regulatory audit, fraud investigation.
     */
    public boolean verify(Transaction transaction, TransactionSignature signature) {
        String canonicalForm = buildCanonicalForm(transaction);
        byte[] data = canonicalForm.getBytes(StandardCharsets.UTF_8);
        byte[] sigBytes = Base64.getDecoder().decode(signature.signatureValue());

        PublicKey publicKey = hsmKeyService.getPublicKey(
            transaction.userId() + "-signing-key",
            signature.keyFingerprint()
        );

        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(sigBytes);
        } catch (Exception e) {
            auditLogger.logCriticalSecurityEvent("SIGNATURE_VERIFY_FAILED",
                "Transaction signature verification failed for txId=" + transaction.id());
            return false;
        }
    }

    /**
     * Canonical form — deterministic string representation of transaction.
     * Must be identical for sign and verify to produce matching results.
     * Includes all fields that must be immutable after signing.
     */
    private String buildCanonicalForm(Transaction transaction) {
        return String.join("|",
            "v1",                                     // Version — for future algorithm agility
            transaction.id().toString(),
            transaction.fromAccountId().toString(),
            transaction.toAccountId().toString(),
            transaction.amount().toPlainString(),
            transaction.currency(),
            transaction.timestamp().toString(),
            transaction.userId().toString()
        );
    }
}
```

---

## HSM Integration

> ** Requirement:**  
> PCI-DSS 4.0 Req 3.7.1: Cryptographic keys stored/used in HSMs.  
> regulation: Key Ceremony documentation, dual control, split knowledge for master keys.

```java
package com.example.user.config.security.hsm;

/**
 * Hardware Security Module (HSM) Key Service.
 *
 * Production HSM options:
 * - AWS CloudHSM: FIPS 140-2 Level 3, managed
 * - Azure Dedicated HSM: FIPS 140-2 Level 3, managed
 * - Thales Luna Network HSM: FIPS 140-2 Level 3, on-premise
 * - Utimaco: FIPS 140-2 Level 3, on-premise
 *
 * Key hierarchy ( standard):
 * ┌─────────────────────────┐
 * │   Master Key (MK)       │  ← HSM, never leaves hardware, dual control
 * │     stored in HSM       │
 * └──────────┬──────────────┘
 *            │ encrypts
 * ┌──────────▼──────────────┐
 * │   Key Encryption Key    │  ← HSM, wraps DEKs
 * │   (KEK)                 │
 * └──────────┬──────────────┘
 *            │ encrypts
 * ┌──────────▼──────────────┐
 * │   Data Encryption Key   │  ← Vault, HSM-backed
 * │   (DEK)                 │
 * └──────────┬──────────────┘
 *            │ encrypts
 * ┌──────────▼──────────────┐
 * │   Data (PAN, IBAN...)   │  ← Database
 * └─────────────────────────┘
 */
@Service
public class HsmKeyService {

    private final PKCS11Provider hsmProvider;

    // Key rotation schedule
    private static final Duration KEY_ROTATION_PERIOD = Duration.ofDays(365); // Annual rotation

    /**
     * Sign data using HSM — private key NEVER leaves HSM.
     */
    public byte[] sign(byte[] data, KeyAlgorithm algorithm, String keyLabel) {
        try {
            PrivateKey privateKey = hsmProvider.getPrivateKey(keyLabel);
            Signature sig = Signature.getInstance(algorithm.jcaName(), hsmProvider.getProvider());
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (Exception e) {
            auditLogger.logCriticalSecurityEvent("HSM_SIGN_FAILED",
                "HSM signing operation failed for key=" + keyLabel);
            throw new HsmOperationException("HSM signing failed", e);
        }
    }

    /**
     * Generate a Data Encryption Key in the HSM.
     * Returns an HSM-wrapped key blob — raw key material never exposed.
     */
    public HsmKeyRef generateDataEncryptionKey(String keyLabel) {
        return hsmProvider.generateKey(
            new KeySpec(KeyAlgorithm.AES_256, keyLabel, KEY_ROTATION_PERIOD)
        );
    }

    /**
     * Rotate a key — generate new DEK, re-encrypt all data encrypted with old DEK.
     * This is a background job, NOT inline with user requests.
     */
    @Scheduled(cron = "0 0 2 1 * *")  // First of each month, 2 AM
    public void rotateExpiredKeys() {
        List<HsmKeyRef> expiredKeys = hsmProvider.findExpiredKeys();
        for (HsmKeyRef oldKey : expiredKeys) {
            HsmKeyRef newKey = generateDataEncryptionKey(oldKey.label() + "-v" + oldKey.nextVersion());
            dataRekeyingService.rekeyAllDataForKey(oldKey, newKey);
            hsmProvider.archiveKey(oldKey);
            auditLogger.logSecurityEvent("KEY_ROTATED", "system",
                "Key rotated: " + oldKey.label() + " → " + newKey.label());
        }
    }

    public enum KeyAlgorithm {
        AES_256("AES", 256),
        ECDSA_P256("SHA256withECDSA", 256),
        RSA_4096("SHA256withRSA", 4096);

        private final String jcaName;
        private final int keySize;

        KeyAlgorithm(String jcaName, int keySize) {
            this.jcaName = jcaName;
            this.keySize = keySize;
        }

        public String jcaName() { return jcaName; }
    }
}
```

---

## Secrets Management — HashiCorp Vault

> Replaces the basic `Secrets Management` section with enterprise Vault integration.  
> Requirement: Centralized secrets management, dynamic credentials, auto-rotation.

### Spring Vault Integration

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.vault</groupId>
    <artifactId>spring-vault-core</artifactId>
</dependency>
```

```yaml
# application.yml — HashiCorp Vault configuration
spring:
  cloud:
    vault:
      uri: https://vault.internal.com:8200
      authentication: KUBERNETES               # K8s service account auth
      kubernetes:
        role: hex-application
        kubernetes-path: auth/kubernetes
      generic:
        enabled: false
      kv:
        enabled: true
        backend: secret
        default-context: hex-application
        application-name: hex-application
      database:
        enabled: true
        role: hex-application-db-role          # Dynamic DB credentials
        backend: database
      ssl:
        trust-store: classpath:vault-truststore.jks
        trust-store-password: ${VAULT_TRUSTSTORE_PASSWORD}
      fail-fast: true                          # FAIL if Vault unreachable on startup
      lease-renewal-expiry-threshold: 0.1      # Renew at 10% remaining lifetime
```

```java
/**
 * Dynamic Database Credential Provider.
 *
 * Vault issues short-lived (1-hour) DB credentials per service instance.
 * No long-lived passwords — eliminates credential rotation risk.
 * Each service instance gets unique credentials — full traceability.
 *
 * Requirement:
 * - PCI-DSS 4.0 Req 8.3.9: Passwords changed at least every 90 days
 * - Vault dynamic credentials: effectively changed every lease period
 */
@Configuration
public class VaultDynamicDatasourceConfig {

    @Autowired
    private VaultOperations vaultOperations;

    @Bean
    @RefreshScope  // Refreshes when Vault lease expires
    public DataSource dataSource() {
        // Vault issues fresh credentials on every call
        VaultResponse response = vaultOperations.read("database/creds/hex-application-db-role");

        String username = (String) response.getData().get("username");
        String password = (String) response.getData().get("password");
        Long leaseDuration = response.getLeaseDuration();

        log.info("event=DB_CREDS_OBTAINED username={} leaseDuration={}s", username, leaseDuration);

        return DataSourceBuilder.create()
            .url(System.getenv("DATABASE_URL"))
            .username(username)
            .password(password)
            .build();
    }
}
```

### Vault Policy (Least Privilege)

```hcl
# vault/policies/hex-application.hcl
# Application policy — least privilege per service

# Application secrets (read-only)
path "secret/data/hex-application/*" {
  capabilities = ["read"]
}

# Dynamic database credentials (generate only)
path "database/creds/hex-application-db-role" {
  capabilities = ["read"]
}

# PKI certificate issuance (for mTLS cert rotation)
path "pki/issue/service-certs" {
  capabilities = ["create", "update"]
  allowed_parameters = {
    "common_name" = ["hex-application.internal.com"]
    "ttl"         = []
  }
}

# Transit encryption (for field-level encryption)
path "transit/encrypt/field-encryption-key" {
  capabilities = ["update"]
}
path "transit/decrypt/field-encryption-key" {
  capabilities = ["update"]
}

# Explicitly DENY everything else
path "*" {
  capabilities = ["deny"]
}
```

```java
/**
 * Vault Transit Encryption — delegate encryption to Vault.
 *
 * BETTER than local AES because:
 * - Keys NEVER leave Vault
 * - Audit log of every encrypt/decrypt operation
 * - Automatic key versioning and rotation
 * - Key access controlled by Vault policies
 */
@Service
public class VaultTransitEncryptionService {

    private final VaultOperations vaultOperations;
    private static final String TRANSIT_KEY = "transit/encrypt/field-encryption-key";

    public String encrypt(String plaintext) {
        String base64Plaintext = Base64.getEncoder()
            .encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));

        VaultResponse response = vaultOperations.write(TRANSIT_KEY,
            Map.of("plaintext", base64Plaintext));

        return (String) response.getData().get("ciphertext"); // vault:v1:...
    }

    public String decrypt(String ciphertext) {
        VaultResponse response = vaultOperations.write(
            "transit/decrypt/field-encryption-key",
            Map.of("ciphertext", ciphertext)
        );

        String base64Plaintext = (String) response.getData().get("plaintext");
        return new String(Base64.getDecoder().decode(base64Plaintext), StandardCharsets.UTF_8);
    }
}
```

---

## Fraud Detection & Anti-Automation

> ** Requirement:** Detect and prevent fraudulent transactions in real time.  
> Regulatory expectation: Proactive fraud controls, transaction monitoring.

```java
package com.example.user.config.security.fraud;

/**
 * Fraud Detection Service — real-time transaction risk scoring.
 *
 * Checks:
 * 1. Velocity: unusually high transaction frequency
 * 2. Geo-anomaly: login from unexpected location
 * 3. Amount anomaly: unusually large amounts vs. history
 * 4. New beneficiary: first transfer to this recipient
 * 5. Time anomaly: unusual transaction time (3 AM)
 * 6. Device fingerprint: new/unrecognized device
 * 7. IP reputation: Tor exit node, known fraud IP
 *
 * Risk scoring: 0.0 (safe) to 1.0 (high fraud risk)
 * Thresholds:
 *   > 0.85: DENY automatically
 *   0.60-0.85: STEP-UP (require MFA re-auth)
 *   0.40-0.60: REVIEW (allow but flag for manual review)
 *   < 0.40: ALLOW
 */
@Service
public class FraudDetectionService {

    private final TransactionHistoryService historyService;
    private final IpReputationService ipReputationService;
    private final GeoAnomalyService geoAnomalyService;
    private final DeviceFingerprintService deviceService;

    public FraudScore score(TransactionContext ctx) {

        double score = 0.0;
        List<String> triggers = new ArrayList<>();

        // 1. Velocity check (20% weight)
        int txCountLastHour = historyService.countTransactionsLastHour(ctx.userId());
        if (txCountLastHour > 10) {
            score += 0.20;
            triggers.add("HIGH_VELOCITY:" + txCountLastHour);
        }

        // 2. Amount anomaly (25% weight)
        BigDecimal avgAmount = historyService.averageTransactionAmount(ctx.userId(), 30);
        if (ctx.amount().compareTo(avgAmount.multiply(new BigDecimal("5"))) > 0) {
            score += 0.25;
            triggers.add("AMOUNT_ANOMALY:5x_avg");
        }

        // 3. Geo-anomaly (20% weight)
        if (geoAnomalyService.isAnomalousLocation(ctx.userId(), ctx.clientIp())) {
            score += 0.20;
            triggers.add("GEO_ANOMALY");
        }

        // 4. New beneficiary (15% weight)
        if (!historyService.isKnownBeneficiary(ctx.userId(), ctx.beneficiaryId())) {
            score += 0.15;
            triggers.add("NEW_BENEFICIARY");
        }

        // 5. IP reputation (10% weight)
        IpReputation rep = ipReputationService.check(ctx.clientIp());
        if (rep.isTorExit() || rep.isKnownFraudIp()) {
            score += 0.10;
            triggers.add("BAD_IP:" + rep.category());
        }

        // 6. New device (10% weight)
        if (!deviceService.isKnownDevice(ctx.userId(), ctx.deviceId())) {
            score += 0.10;
            triggers.add("NEW_DEVICE");
        }

        FraudScore result = new FraudScore(Math.min(score, 1.0), triggers);

        if (result.score() > 0.40) {
            auditLogger.logSuspiciousActivity(ctx.userId(), ctx.clientIp(),
                "fraud_score=" + result.score() + " triggers=" + triggers);
        }

        return result;
    }
}
```

### Anti-Automation (Bot Protection)

```java
/**
 * Anti-automation controls for APIs.
 *
 * Requirement: Prevent credential stuffing, account enumeration,
 * automated fraud attempts.
 *
 * Controls:
 * 1. CAPTCHA challenge on repeated failures
 * 2. Progressive delays (exponential backoff)
 * 3. Account lockout with unlock via verified channel
 * 4. Device fingerprinting
 * 5. Behavioral biometrics (typing speed, mouse patterns)
 */
@Service
public class AntiAutomationService {

    private static final int MAX_FAILURES_BEFORE_LOCKOUT = 5;
    private static final int MAX_FAILURES_BEFORE_CAPTCHA = 3;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);

    private final RedisTemplate<String, Integer> redis;

    public AuthChallenge evaluateLoginAttempt(String userId, String ip) {
        int failures = getFailureCount(userId);

        if (failures >= MAX_FAILURES_BEFORE_LOCKOUT) {
            // Lock account — require unlock via email/SMS verified channel
            lockAccount(userId);
            auditLogger.logSuspiciousActivity(userId, ip, "ACCOUNT_LOCKED after " + failures + " failures");
            return AuthChallenge.ACCOUNT_LOCKED;
        }

        if (failures >= MAX_FAILURES_BEFORE_CAPTCHA) {
            return AuthChallenge.CAPTCHA_REQUIRED;
        }

        // Progressive delay: 1s, 2s, 4s, 8s... (exponential)
        if (failures > 0) {
            long delayMs = (long) Math.pow(2, failures) * 1000;
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
        }

        return AuthChallenge.NONE;
    }

    public void recordFailure(String userId) {
        String key = "auth:failures:" + userId;
        redis.opsForValue().increment(key);
        redis.expire(key, LOCKOUT_DURATION);
    }

    public void resetFailures(String userId) {
        redis.delete("auth:failures:" + userId);
    }

    private int getFailureCount(String userId) {
        Integer count = redis.opsForValue().get("auth:failures:" + userId);
        return count != null ? count : 0;
    }

    public enum AuthChallenge { NONE, CAPTCHA_REQUIRED, ACCOUNT_LOCKED, STEP_UP_REQUIRED }
}
```

---

## Logging Security Events & Regulatory Audit Trail

> ** Requirement:**  
> - PCI-DSS 4.0 Req 10: Log and monitor all access to network resources and cardholder data  
> - SOX Section 302/404: Audit trail for critical transaction integrity  
> - GDPR Art. 30: Records of processing activities  
> - Logs must be **immutable**, **tamper-evident**, and retained for **minimum 12 months**

```java
package com.example.user.config.security.audit;

import org.springframework.stereotype.Service;

/**
 * Regulatory Audit Trail Service — immutable, tamper-evident event log.
 *
 * Requirements:
 * - PCI-DSS 4.0 Req 10.2.1: Log all individual user access to cardholder data
 * - PCI-DSS 4.0 Req 10.2.2: Log all actions taken by root/admin
 * - PCI-DSS 4.0 Req 10.2.3: Log access to all audit trails
 * - PCI-DSS 4.0 Req 10.2.5: Log all authentication/authorization failures
 * - PCI-DSS 4.0 Req 10.2.7: Log creation/deletion of system-level objects
 * - SOX: data changes must have before/after values in audit log
 *
 * Tamper evidence:
 * - Each log entry is HMAC-SHA256 signed with HSM key
 * - Log entries form a hash chain (each entry includes hash of previous)
 * - Any tampering breaks the chain — detected on verification
 */
@Service
public class RegulatoryAuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("REGULATORY_AUDIT");

    private final HsmKeyService hsmKeyService;
    private final AuditLogRepository auditLogRepository;
    private final String instanceId = System.getenv("HOSTNAME");

    /**
     * Log a transaction — PCI-DSS Req 10.2, SOX
     */
    public void logTransaction(TransactionAuditEvent event) {
        AuditEntry entry = AuditEntry.builder()
            .eventType("TRANSACTION")
            .eventTime(Instant.now())
            .userId(event.userId())
            .sessionId(event.sessionId())
            .ipAddress(maskIp(event.ipAddress()))
            .resourceType("TRANSACTION")
            .resourceId(event.transactionId().toString())
            .action(event.action())
            .amount(event.amount())                  // SOX: amount
            .currency(event.currency())
            .maskedPan(panMasker.maskPan(event.pan()))  // PCI: masked PAN only
            .beforeState(event.beforeState())         // SOX: before value
            .afterState(event.afterState())           // SOX: after value
            .outcome(event.outcome())
            .instanceId(instanceId)
            .build();

        persistWithIntegrityProof(entry);
    }

    /**
     * Log cardholder data access — PCI-DSS Req 10.2.1
     */
    public void logCardholderDataAccess(String userId, String sessionId, String maskedPan,
                                         String operation, String ipAddress) {
        AuditEntry entry = AuditEntry.builder()
            .eventType("CARDHOLDER_DATA_ACCESS")
            .eventTime(Instant.now())
            .userId(userId)
            .sessionId(sessionId)
            .ipAddress(maskIp(ipAddress))
            .resourceType("CARDHOLDER_DATA")
            .maskedPan(maskedPan)
            .action(operation)
            .build();

        persistWithIntegrityProof(entry);

        // Structured log for SIEM real-time alerting
        AUDIT_LOG.info(
            "event=CARDHOLDER_DATA_ACCESS userId={} sessionId={} maskedPan={} operation={} ip={}",
            userId, sessionId, maskedPan, operation, maskIp(ipAddress)
        );
    }

    /**
     * Persist audit entry with HMAC signature and hash chain.
     * Hash chain: entry_n.hash = HMAC(entry_n.data || entry_{n-1}.hash)
     * Any modification breaks the chain.
     */
    private void persistWithIntegrityProof(AuditEntry entry) {
        // Get hash of previous entry (chain)
        String prevHash = auditLogRepository.getLastHash()
            .orElse("GENESIS");

        String entryJson = toCanonicalJson(entry);
        String chainInput = entryJson + "|" + prevHash;

        // Sign with HSM — cannot be forged without HSM access
        String hmac = hsmKeyService.hmacSha256(chainInput, "audit-log-signing-key");

        entry.setIntegrityHash(hmac);
        entry.setPreviousHash(prevHash);

        // Write to immutable audit log (append-only, no UPDATE/DELETE)
        auditLogRepository.appendEntry(entry);

        // Forward to immutable SIEM (Splunk, Elastic)
        AUDIT_LOG.info("audit_entry={}", entryJson);
    }

    /**
     * Verify audit log integrity — detect tampering.
     * Called by compliance team, regulators, forensics.
     */
    public AuditVerificationReport verifyIntegrity(LocalDate from, LocalDate to) {
        List<AuditEntry> entries = auditLogRepository.findBetween(from, to);
        List<String> violations = new ArrayList<>();

        String expectedPrevHash = "GENESIS";
        for (AuditEntry entry : entries) {
            // Recompute expected hash
            String chainInput = toCanonicalJson(entry) + "|" + expectedPrevHash;
            String expectedHash = hsmKeyService.hmacSha256(chainInput, "audit-log-signing-key");

            if (!expectedHash.equals(entry.getIntegrityHash())) {
                violations.add("TAMPER_DETECTED at entry " + entry.getId()
                    + " time=" + entry.getEventTime());
            }
            expectedPrevHash = entry.getIntegrityHash();
        }

        return new AuditVerificationReport(
            entries.size(),
            violations.isEmpty(),
            violations,
            from,
            to
        );
    }
}
```

---

## Incident Response & Breach Notification

> ** Requirement:**  
> - PCI-DSS 4.0 Req 12.10: Implement an incident response plan  
> - GDPR Art. 33: Notify supervisory authority within 72 hours of breach  
> - PSD2: Notify competent authority of major operational/security incidents  
> - Notify affected customers and regulators per jurisdiction requirements

```java
package com.example.user.config.security.incident;

/**
 * Incident Response Service — automated detection and escalation.
 *
 * Severity levels:
 * P1 CRITICAL: Active breach, data exfiltration, production down
 * P2 HIGH: Suspected compromise, authentication bypass
 * P3 MEDIUM: Failed brute force, anomalous access pattern
 * P4 LOW: Single suspicious request, policy violation
 *
 * Response SLAs:
 * P1: Acknowledge 15 min, Initial response 1 hour, Resolution 4 hours
 * P2: Acknowledge 1 hour, Initial response 4 hours, Resolution 24 hours
 * P3: Acknowledge 4 hours, Initial response 24 hours, Resolution 72 hours
 * P4: Acknowledge 24 hours, Initial response 72 hours, Resolution next sprint
 */
@Service
public class IncidentResponseService {

    @Value("${security.incident.pagerduty-key}")
    private String pagerdutyKey;

    @Value("${security.incident.soc-email}")
    private String socEmail;

    /**
     * Trigger incident response — called automatically by security monitors.
     */
    public void triggerIncident(SecurityIncident incident) {
        log.error("SECURITY_INCIDENT severity={} type={} description={}",
            incident.severity(), incident.type(), incident.description());

        // 1. Immediately contain (P1/P2)
        if (incident.severity() == Severity.P1 || incident.severity() == Severity.P2) {
            contain(incident);
        }

        // 2. Alert SOC
        alertSoc(incident);

        // 3. Create incident ticket
        String ticketId = incidentTracker.createTicket(incident);

        // 4. Start evidence collection
        evidenceCollector.startCollection(incident, ticketId);

        // 5. Assess breach notification requirement (GDPR 72-hour clock)
        if (incident.mayRequireGdprNotification()) {
            gdprNotificationService.startBreachAssessment(incident, ticketId);
        }
    }

    /**
     * Containment actions — isolate affected systems.
     */
    private void contain(SecurityIncident incident) {
        switch (incident.type()) {
            case ACCOUNT_COMPROMISE -> {
                // Lock compromised accounts immediately
                userService.lockAccounts(incident.affectedUserIds());
                // Invalidate all sessions
                sessionService.invalidateAllSessions(incident.affectedUserIds());
                // Revoke all tokens
                tokenService.revokeAllTokens(incident.affectedUserIds());
            }
            case CREDENTIAL_STUFFING -> {
                // Block source IPs
                incident.sourceIps().forEach(firewallService::blockIp);
                // Enable CAPTCHA globally
                securityConfig.enableGlobalCaptcha();
            }
            case DATA_EXFILTRATION_SUSPECTED -> {
                // Block all data export endpoints
                apiGateway.blockDataExportEndpoints();
                // Alert CISO immediately (P1 = CISO must be notified)
                alertCiso(incident);
            }
            case INJECTION_ATTACK_DETECTED -> {
                // Block attacker IP
                incident.sourceIps().forEach(firewallService::blockIp);
            }
        }
    }
}
```

### Breach Notification Runbook

```markdown
## Breach Notification Runbook (GDPR + PCI-DSS)

### Detection → First 15 Minutes
- [ ] Confirm incident is real (not false positive)
- [ ] Assign Incident Commander (IC)
- [ ] Create Slack/Teams war room channel: #incident-YYYY-MM-DD
- [ ] Start documentation log (everything timestamped)
- [ ] Execute containment (`IncidentResponseService.contain()`)

### First Hour
- [ ] Determine scope: What data? How many users? Time window?
- [ ] Preserve evidence: forensic snapshot of logs, DB state
- [ ] Notify CISO and Legal
- [ ] Assess breach notification requirements:
      - GDPR Art. 33: Does this affect EU residents? → 72-hour clock starts NOW
      - PCI-DSS: Does this involve cardholder data? → Notify card brands + acquirer
      - PSD2: Is this an operational/security incident affecting payment services?

### Within 72 Hours (GDPR)
- [ ] File notification to supervisory authority (ICO in UK, etc.)
      Required info: nature, categories/volume of data, contact DPO,
      likely consequences, measures taken
- [ ] If high risk to individuals: notify affected users directly (Art. 34)
- [ ] Document risk assessment: Was breach likely to result in risk to rights/freedoms?

### Post-Incident
- [ ] Root cause analysis (RCA) within 5 business days
- [ ] Update threat model based on how attack succeeded
- [ ] Pen test verification that vulnerability is fixed
- [ ] Review and update incident response plan
- [ ] Report to board/regulator as required
```

---

## PCI-DSS v4 Compliance Controls

> Quick reference mapping Spring Boot controls to PCI-DSS v4.0 requirements.

| PCI-DSS v4 Req | Requirement Description | Spring Boot Control | Implementation |
|----------------|------------------------|---------------------|----------------|
| **3.3.1** | Do not retain SAD | Never store CVV/CVV2/PIN | Validation rejects CVV fields |
| **3.5.1** | PAN unreadable at rest | AES-256-GCM field encryption | `FieldEncryptionService` |
| **3.7.1** | Keys in HSM | HSM for all crypto keys | `HsmKeyService` |
| **4.2.1** | Strong crypto in transit | TLS 1.3 + FIPS ciphers | `application.yml` ssl config |
| **6.2.4** | Prevent common web attacks | Input sanitization + WAF | `InputSanitizer` + WAF |
| **6.3.3** | Security patches | OWASP + EPSS-based patching | `dependency-check` + EPSS |
| **7.2.1** | Least privilege | `denyAll()` + `@PreAuthorize` | `SecurityConfig` |
| **8.3.6** | Password complexity | `@Pattern` on password DTO | `CreateUserRequest` |
| **8.4.2** | MFA for all accounts | TOTP + FIDO2 | `MfaService` |
| **10.2.1** | Log all CHD access | `RegulatoryAuditService` | Cardholder data access log |
| **10.3.2** | Tamper-evident logs | HMAC chain + HSM signing | `persistWithIntegrityProof()` |
| **10.5.1** | Retain logs 12 months | Splunk retention policy | `splunk-config.conf` |
| **11.3.1** | Penetration testing | Annual pen test + DAST | `TESTING_PRESENTATION.md` |
| **12.3.4** | Hardware/software reviewed | SBOM + inventory | `cyclonedx-maven-plugin` |
| **12.10.1** | Incident response plan | `IncidentResponseService` | IR runbook above |

---

## Security Checklist

> Extends the standard checklist with specific requirements.

### Critical Controls — MANDATORY

- [ ] **Zero Trust**: Every request evaluated (identity + device + behavior + fraud score)
- [ ] **MFA**: TOTP or FIDO2 required for all users, hardware key for admins
- [ ] **SCA**: PSD2-compliant Strong Customer Authentication for payments
- [ ] **mTLS**: All inter-service communication uses mutual TLS certificates
- [ ] **TLS 1.3 only**: No TLS 1.2 or below — PCI-DSS requirement
- [ ] **FIPS 140-2**: Only FIPS-approved algorithms (AES-256, SHA-256, ECDSA P-256)
- [ ] **HSM**: All cryptographic keys stored/used in Hardware Security Module
- [ ] **PAN encrypted**: AES-256-GCM field encryption on all PAN storage
- [ ] **PAN masked**: Only show first6+last4 in all API responses and logs
- [ ] **Tokenization**: PAN replaced with opaque tokens in non-PCI systems
- [ ] **Transaction signing**: ECDSA signatures for all transactions
- [ ] **Dynamic linking**: SCA token cryptographically bound to specific transaction
- [ ] **Vault integration**: All secrets via HashiCorp Vault, no static credentials
- [ ] **Dynamic DB creds**: Vault issues short-lived DB credentials per instance
- [ ] **Key rotation**: Automated annual key rotation via HSM
- [ ] **Network segmentation**: DMZ, App tier, Data tier, Management — no direct access
- [ ] **Fraud scoring**: Real-time fraud risk evaluation on all transactions
- [ ] **Account lockout**: After 5 failures, 30-minute lockout, unlock via verified channel
- [ ] **Regulatory audit log**: Immutable, HMAC-signed, hash-chained audit trail
- [ ] **Audit log retention**: Minimum 12 months online, 24 months archive (PCI-DSS)
- [ ] **GDPR compliance**: PII encrypted, masked in logs, retention limits enforced
- [ ] **Incident response plan**: Documented, tested, GDPR 72-hour notification ready
- [ ] **Annual pen test**: External penetration test by qualified QSA
- [ ] **PCI-DSS QSA**: Annual assessment by Qualified Security Assessor
- [ ] **SOX controls**: transaction audit trail with before/after values

### Per-Endpoint Controls

For every endpoint handling critical data:

- [ ] Fraud score evaluated before processing
- [ ] Step-up MFA required for high-value operations
- [ ] Transaction signed before persistence
- [ ] PAN masked in all log statements
- [ ] Cardholder data access logged to regulatory audit trail
- [ ] Amount validated with `@ValidMonetaryAmount` (prevents negative amounts)
- [ ] Currency validated with `@ValidCurrencyCode` (ISO 4217)
- [ ] Beneficiary validation (new beneficiary = step-up auth + fraud check)
- [ ] Response DTO never exposes PAN, CVV, full IBAN, or internal IDs

---

## Quick Reference — Security Commands

```bash
# Security Scanning
mvn clean verify                          # Build + tests + validation
mvn dependency-check:check               # CVE scan (SCA) — fail on CVSS ≥ 7
mvn cyclonedx:makeAggregateBom           # Generate SBOM
bash scripts/epss-check.sh               # EPSS exploitation risk (fail > 30%)
semgrep scan --config=p/owasp-top-ten    # SAST scan
trufflehog git file://. --only-verified  # Secret scan

# Certificate Management
bash scripts/rotate-service-certs.sh     # Rotate mTLS service certificates
openssl x509 -in service.crt -noout -dates  # Check cert expiry

# Vault Operations
vault login -method=kubernetes role=hex-application
vault read database/creds/hex-application-db-role  # Verify dynamic creds work
vault audit list                          # Verify audit logging enabled

# PCI-DSS Verification
grep -r "println\|log.*pan\|log.*card" src/ --include="*.java"  # Find accidental PAN logging
grep -r "sk_live\|password.*=.*['\"]" src/ --include="*.java"   # Find hardcoded secrets

# Audit Log Verification
curl -s http://localhost:8080/api/v1/admin/audit/verify \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"from":"2026-01-01","to":"2026-06-28"}'   # Verify log chain integrity

# Incident Response
bash scripts/incident-contain.sh --severity P1 --type account_compromise --user-ids "123,456"
```

---

*Last Updated: June 29, 2026*  
*Companion documents: [TESTING_PRESENTATION.md](./TESTING_PRESENTATION.md) | [security-governance.md](./security-governance.md)*  
*Regulatory scope: PCI-DSS v4.0 | PSD2 | GDPR | SOX | Basel III | FIPS 140-2*

````
