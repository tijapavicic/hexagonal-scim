# Keytool Presentation for Junior Developers

## Slide 1 - Title

**Keytool 101: TLS Certificates for Java Apps**

- Audience: Developers
- Goal: Learn the keytool workflows you will use in local dev and team environments
- Outcome: You can create, inspect, and use keystores/truststores in Spring Boot

> Mentor note: Ask learners what they think SSL/TLS does before showing commands.

---

## Slide 2 - What Is `keytool`?

`keytool` is the Java utility for:

- creating key pairs and self-signed certs
- managing keystores (`.p12`, `.jks`)
- creating CSRs for CA-signed certificates
- importing certificate chains

Quick check:

```bash
keytool -help | head -n 20
java -version
```

---

## Slide 3 - Core Concepts

- **Private key**: secret, never share
- **Certificate**: public identity info + public key
- **Keystore**: usually contains private key + certificate (`app-keystore.p12`)
- **Truststore**: contains trusted CA/public certs (`app-truststore.p12`)
- **Alias**: friendly name for an entry in a store

> Mentor note: Emphasize "keystore proves who we are; truststore defines who we trust."

---

## Slide 4 - Keystore vs Truststore

| Store | Contains | Typical Use |
|---|---|---|
| Keystore | Private key + server cert | Server-side TLS identity |
| Truststore | CA/intermediate/public certs | Validating remote TLS certs |

Common in projects:

- `app-keystore.p12` for inbound HTTPS
- `app-truststore.p12` for outbound HTTPS calls

---

## Slide 5 - Generate a Local Keystore (Self-Signed)

```bash
keytool -genkeypair \
  -alias app-local \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -validity 825 \
  -storetype PKCS12 \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -keypass '<keypass>' \
  -dname 'CN=localhost, OU=Dev, O=Example, L=City, ST=State, C=US' \
  -ext 'SAN=dns:localhost,ip:127.0.0.1'
```

Why SAN matters: browsers/clients validate hostnames against SAN, not only CN.

---

## Slide 6 - Inspect and Verify Entries

List all entries:

```bash
keytool -list -v \
  -keystore app-keystore.p12 \
  -storepass '<storepass>'
```

List a specific alias:

```bash
keytool -list -v \
  -alias app-local \
  -keystore app-keystore.p12 \
  -storepass '<storepass>'
```

---

## Slide 7 - Export Certificate and Create CSR

Export public cert (PEM):

```bash
keytool -exportcert \
  -rfc \
  -alias app-local \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -file app-local.crt
```

Create CSR for CA signing:

```bash
keytool -certreq \
  -alias app-local \
  -file app-local.csr \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -keypass '<keypass>' \
  -ext 'SAN=dns:localhost,dns:app.local,ip:127.0.0.1'
```

---

## Slide 8 - Import CA Chain and Signed Certificate

1) Import root/intermediate certs:

```bash
keytool -importcert \
  -alias my-ca \
  -file ca.crt \
  -keystore app-truststore.p12 \
  -storetype PKCS12 \
  -storepass '<storepass>' \
  -noprompt
```

2) Import signed cert reply to existing key alias:

```bash
keytool -importcert \
  -alias app-local \
  -file signed-cert.crt \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -keypass '<keypass>'
```

> Mentor note: Most chain errors happen when signed cert is imported before CA chain.

Note: Follow CA instructions for chain order; import intermediate/root certs before importing the certificate reply for the key alias.

---

## Slide 9 - Spring Boot TLS Configuration

`application.yml`:

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:tls/app-keystore.p12
    key-store-type: PKCS12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-alias: app-local
```

Set environment variable in terminal:

```bash
export SSL_KEYSTORE_PASSWORD='<storepass>'
```

---

## Slide 10 - Truststore at Runtime (Outbound TLS)

```bash
java \
  -Djavax.net.ssl.trustStore=app-truststore.p12 \
  -Djavax.net.ssl.trustStoreType=PKCS12 \
  -Djavax.net.ssl.trustStorePassword='<storepass>' \
  -jar app.jar
```

Use this when your app calls internal HTTPS services with private CA certs.

---

## Slide 11 - Common Errors and Fast Fixes

- `Alias <x> does not exist`
  - check aliases with `keytool -list -keystore <file>`
- `Keystore was tampered with, or password incorrect`
  - verify password and `-storetype`
- `Failed to establish chain from reply`
  - import root/intermediate first
- hostname mismatch
  - regenerate cert with correct SAN values

Debug commands:

```bash
keytool -printcert -v -file app-local.crt
keytool -printcertreq -v -file app-local.csr
```

---

## Slide 12 - Security Do and Do Not

Do:

- store passwords in env vars or secret manager
- rotate certs before expiry
- separate dev/test/prod certificates
- lock permissions:

```bash
chmod 600 app-keystore.p12
chmod 600 app-truststore.p12
```

Do not:

- commit `.p12` or `.jks` to git
- paste private key files in tickets/chat
- reuse production certs in local environments

---

## Slide 13 - Hands-On Lab (20 Minutes)

### Task A (8 min)

- Generate `app-keystore.p12` with SAN for `localhost`
- Verify alias and expiry

### Task B (6 min)

- Export cert to `app-local.crt`
- Print cert details and identify CN + SAN

### Task C (6 min)

- Configure Spring Boot SSL properties
- Start app and validate HTTPS endpoint locally

Success criteria:

- keystore exists and can be listed
- cert has expected SANs
- app starts with TLS enabled

---

## Slide 14 - Quick Reference

Use the full command reference in:

- `keytool-cheatsheet.md`

Recommended naming conventions:

- keystore file: `app-keystore.p12`
- truststore file: `app-truststore.p12`
- local alias: `app-local`

---

## Slide 15 - Q&A Prompts

- When should we use a truststore at runtime?
- Why can SAN break local TLS even if CN looks correct?
- What is the difference between importing CA certs and cert reply?
- Which files should never be committed?

> Mentor note: End by asking each learner to explain keystore vs truststore in one sentence.

---

## Advanced Section - Senior Developers

## Slide 16 - Enterprise PKI Strategy

- Define trust boundaries: public edge, private east-west, and third-party integrations
- Standardize on certificate profiles (key usage, extended key usage, SAN policy)
- Enforce short-lived certs for workloads; automate rotation
- Prefer ECDSA P-256 for performance-sensitive internal services, RSA where compatibility is required

> Mentor note: Ask where manual cert issuance still exists and mark it as automation debt.

---

## Slide 17 - Keystore/Truststore Lifecycle Automation

Automate creation and import steps in CI/CD (non-interactive):

```bash
keytool -genkeypair \
  -alias service-a \
  -keyalg EC \
  -groupname secp256r1 \
  -sigalg SHA256withECDSA \
  -validity 90 \
  -storetype PKCS12 \
  -keystore service-a-keystore.p12 \
  -storepass "$KEYSTORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname 'CN=service-a.internal, OU=Platform, O=Example, C=US' \
  -ext 'SAN=dns:service-a.internal,dns:service-a'
```

Automation rules:

- Never hardcode secrets in pipelines
- Use sealed secrets / vault-backed injection
- Fail pipeline if cert expiry is below policy threshold
- Use RSA 2048/3072 when legacy client compatibility is required

---

## Slide 18 - Mutual TLS (mTLS) Patterns

mTLS design goals:

- Both client and server present certificates
- Service identity verified at transport layer
- Reduced reliance on static API keys in internal traffic

Spring Boot (server-side mTLS):

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:tls/server-keystore.p12
    key-store-type: PKCS12
    key-store-password: ${SERVER_KEYSTORE_PASSWORD}
    trust-store: classpath:tls/server-truststore.p12
    trust-store-type: PKCS12
    trust-store-password: ${SERVER_TRUSTSTORE_PASSWORD}
    client-auth: need
```

Client runtime example:

```bash
java \
  -Djavax.net.ssl.keyStore=client-keystore.p12 \
  -Djavax.net.ssl.keyStoreType=PKCS12 \
  -Djavax.net.ssl.keyStorePassword="$CLIENT_KEYSTORE_PASSWORD" \
  -Djavax.net.ssl.trustStore=client-truststore.p12 \
  -Djavax.net.ssl.trustStoreType=PKCS12 \
  -Djavax.net.ssl.trustStorePassword="$CLIENT_TRUSTSTORE_PASSWORD" \
  -jar client-app.jar
```

---

## Slide 19 - Kubernetes and Service Mesh Integration

Production patterns:

- Offload cert issuance to cert-manager + ClusterIssuer (internal CA)
- Keep app-level TLS where end-to-end encryption is mandated
- Align cert SAN with Kubernetes DNS (`svc.namespace.svc.cluster.local`)
- If using mesh (Istio/Linkerd), define boundary between mesh mTLS and app mTLS

Operational checks:

- Alert on certificate expiry (`< 14 days`)
- Validate handshake success rates per workload
- Ensure rolling restarts pick up renewed secrets

---

## Slide 20 - HSM, FIPS, and Crypto Policy

When compliance requires stronger controls:

- Keep private keys in HSM/KMS-backed systems where feasible
- Use PKCS#11 providers for key operations (sign/decrypt) without key export
- Validate approved algorithms/sizes against org policy (FIPS/NIST)
- Track crypto agility plan for future algorithm migration

> Mentor note: Connect this slide to regulatory contexts (PCI-DSS, SOC2, internal policy).

---

## Slide 21 - Revocation, Rotation, and Incident Response

Senior-level runbook requirements:

- Define revocation strategy (CRL/OCSP availability and fallback behavior)
- Rotate keys/certs on schedule and on incident trigger
- Support emergency truststore updates without full redeploy where possible
- Maintain inventory: owner, environment, expiry, issuer, last rotation date

Fast response checklist:

- Identify impacted cert aliases
- Reissue + redeploy
- Revoke compromised certs
- Verify handshake recovery in telemetry

---

## Slide 22 - Observability and Governance

TLS observability metrics:

- handshake failures by reason (unknown_ca, expired, bad_certificate)
- certificate expiry horizon by service
- mTLS adoption ratio by namespace/module
- truststore drift between environments

Governance controls:

- Policy-as-code for minimum key size, SAN presence, max validity
- PR checks that block committed `.p12`/`.jks` artifacts
- Audit logs for cert issuance/import actions

Example git pre-commit guard:

```bash
git diff --cached --name-only | grep -E '\.(p12|jks)$' && {
  echo 'ERROR: keystore files must not be committed'
  exit 1
}
```

---

## Slide 23 - Advanced Lab (Senior Track, 45 Minutes)

### Task A (15 min)

- Create separate server/client keystores and truststores
- Enable mTLS between two local Spring Boot services

### Task B (15 min)

- Simulate cert expiry using short validity certs
- Add alert logic (script or monitoring rule) for near-expiry certs

### Task C (15 min)

- Implement CI check for forbidden artifacts (`.p12`, `.jks`)
- Add cert inventory markdown with owner and rotation date

Success criteria:

- mTLS handshake works and fails predictably on invalid trust
- expiry warning is generated before cutoff
- repository guard blocks sensitive keystore commits
