# TODO

> Items extracted from README. Check off each item as it is completed.  
> Update `## Versions` in `README.md` when a batch is released.

---

## 🔴 Critical — Before Production

- [x] **CRITICAL 1 — Close port 8080 to the host**  
  `docker-compose.yml` (base) uses `expose: "8080"` — port is internal only, not bound to the host.  
  `docker-compose.override.yml` is **auto-loaded by Docker Compose locally** and adds `ports: "8080:8080"`.  
  CI/staging/production run with `docker compose -f docker-compose.yml up` — override is skipped, port stays closed.  
  Local dev runs `docker compose up` — override is auto-merged, port 8080 is open for Postman/curl.

- [ ] **CRITICAL 2 — Replace self-signed TLS certificate**  
  `docker/certs/server.crt` is a self-signed dev cert. Replace before staging/production.  
  Options: Let's Encrypt (Certbot), corporate CA, or cloud LB (ACM / Azure Front Door).  
  Also update `KC_HTTPS_CERTIFICATE_FILE` in `docker-compose.yml` to point to the new cert.

---

## 🟠 High

- [ ] **Rotate all default secrets**  
  `KEYCLOAK_ADMIN_PASSWORD: admin`, `hexagonal-scim-secret` client secret,  
  DB credentials (`scim/scim`, `keycloak/keycloak`) are hardcoded defaults.  
  Move to secrets management: Vault, AWS Secrets Manager, or Kubernetes Secrets.

- [ ] **Restrict actuator exposure**  
  `/actuator/health` and `/actuator/info` are publicly accessible.  
  In production, bind actuator to a separate management port (e.g. `8081`) not exposed externally.  
  Add `management.server.port=8081` and remove actuator paths from the public security permit list.

---

## 🟡 Medium

- [ ] **Enable Keycloak production mode**  
  Keycloak runs with `start-dev` — disables caches, uses in-memory sessions, not hardened.  
  Switch to `start` (production mode) with `KC_HOSTNAME`, `KC_PROXY`, and proper cache config.

- [ ] **Separate Keycloak database**  
  Keycloak and the app share the same PostgreSQL instance (`init-keycloak.sql` creates a second DB).  
  Use separate database instances in production for failure isolation and independent scaling.

---

## 🚀 CI/CD

- [ ] **GitHub Actions pipeline**  
  Build, test, OWASP check (`mvn -B org.owasp:dependency-check-maven:check`),  
  Docker build + push on every PR and merge to main.

- [ ] **Automatic version tagging**  
  Tag a Git release when PR is merged to main. Align with `## Versions` in README.

---

## 📊 Observability

- [ ] **Structured JSON logging**  
  Add `logstash-logback-encoder` to `hex-application`. Emit JSON logs parseable by Datadog / ELK.

- [ ] **Metrics — Prometheus + Grafana**  
  Add Micrometer Prometheus endpoint. Add `prometheus` and `grafana` services to `docker-compose.yml`.

- [ ] **Distributed tracing**  
  Add OpenTelemetry Java agent. Export traces to Jaeger or Zipkin (add to `docker-compose.yml`).

---

## 🧪 Testing

- [ ] **Integration tests with Testcontainers**  
  Replace H2 in integration tests with a real PostgreSQL container.  
  Catches Flyway/dialect issues early (two such bugs were found in production during this session).

- [ ] **Security tests**  
  Verify: 401 without token, 403 wrong role, token expiry, replay attack with revoked token.

---

## 🌐 API

- [ ] **Sorting and filtering on list endpoint**  
  Add `?sort=email,asc` and `?filter=email sw "alice"` query parameters to `GET /api/v1/users`.

- [ ] **ETag / optimistic locking**  
  Return `ETag` header on GET. Require `If-Match` on PUT/PATCH to prevent lost updates.

