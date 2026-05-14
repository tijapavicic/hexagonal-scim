# Observability Stack — Developer & Engineering Guide

> **Stack**: Prometheus · Grafana · Splunk · Fluent Bit · Micrometer
> **Environment coverage**: Local Docker Compose · Staging · Production
> **Last updated**: May 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Service Access (local)](#3-service-access-local)
4. [What Was Changed and Why](#4-what-was-changed-and-why)
5. [Metrics Pipeline: Spring Boot → Prometheus → Grafana](#5-metrics-pipeline-spring-boot--prometheus--grafana)
6. [Logging Pipeline: Spring Boot → Fluent Bit → Splunk](#6-logging-pipeline-spring-boot--fluent-bit--splunk)
7. [Multi-Environment Strategy](#7-multi-environment-strategy)
8. [Starting the Stack](#8-starting-the-stack)
9. [Grafana Dashboard Guide](#9-grafana-dashboard-guide)
10. [Splunk Log Search Guide](#10-splunk-log-search-guide)
11. [Prometheus Query Reference](#11-prometheus-query-reference)
12. [Prometheus Alerting Rules](#12-prometheus-alerting-rules)
13. [Account Operations Metrics](#13-account-operations-metrics)
14. [File Reference](#14-file-reference)
15. [Extending the Stack](#15-extending-the-stack)
16. [Production Checklist](#16-production-checklist)
17. [Troubleshooting](#17-troubleshooting)
18. [Glossary](#18-glossary)

---

## 1. Overview

This service uses a **two-track observability approach**:

| Track | Tool | What it answers |
|---|---|---|
| **Metrics** | Prometheus + Grafana | *"How is the system performing right now and over time?"* |
| **Logs** | Fluent Bit + Splunk | *"What happened, exactly, and when?"* |

Both tracks are **fully automated** — no manual setup in the Grafana UI, no Splunk HEC token to copy, no Prometheus scrape config to write by hand. Everything is provisioned from files in this repository.

---

## 2. Architecture

```
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                         hexagonal-net (Docker bridge)                    │
  │                                                                          │
  │  ┌─────────────────────────┐      ┌───────────────────────────────────┐  │
  │  │  hexagonal-scim-app     │      │  METRICS TRACK                    │  │
  │  │  Spring Boot 3          │      │                                   │  │
  │  │                         │◄─────│  Prometheus  :9090                │  │
  │  │  /actuator/prometheus   │ pull │  scrapes every 10s                │  │
  │  │  /actuator/health       │      └─────────────┬─────────────────────┘  │
  │  │  /actuator/info         │                    │ query                   │
  │  │                         │                    ▼                         │
  │  │  logback-spring.xml     │      ┌───────────────────────────────────┐  │
  │  │  (docker profile)       │      │  Grafana  :3001                   │  │
  │  │  writes JSON to         │      │  auto-provisioned dashboards       │  │
  │  │  /app/logs/app.log      │      │  Spring Boot 3 / JVM dashboard    │  │
  │  └────────────┬────────────┘      └───────────────────────────────────┘  │
  │               │ Docker named volume: app_logs                             │
  │               ▼                                                           │
  │  ┌─────────────────────────┐      ┌───────────────────────────────────┐  │
  │  │  app_logs Docker volume │      │  LOGGING TRACK                    │  │
  │  │  /app/logs/app.log      │◄─────│  Fluent Bit  :2020 (health)       │  │
  │  │  (JSON, one obj/line)   │ tail │  tails the volume file            │  │
  │  └─────────────────────────┘      │  enriches: service/sourcetype     │  │
  │                                   │  retries on failure               │  │
  │                                   └─────────────┬─────────────────────┘  │
  │                                                 │ HEC push               │
  │                                                 ▼                         │
  │                                   ┌───────────────────────────────────┐  │
  │                                   │  Splunk  :8000 (UI) :8088 (HEC)  │  │
  │                                   │  index=main                       │  │
  │                                   │  sourcetype=hexagonal-scim:json   │  │
  │                                   └───────────────────────────────────┘  │
  └──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Service Access (local)

After running `docker compose up`:

| Service | URL | Credentials |
|---|---|---|
| Spring Boot API | `http://localhost:8080` | Bearer JWT (see README) |
| Spring Boot Actuator | `http://localhost:8080/actuator` | none |
| Prometheus | `http://localhost:9090` | none |
| Grafana | `http://localhost:3001` | admin / admin |
| Splunk Web UI | `http://localhost:8000` | admin / Admin1234! |
| Splunk HEC | `https://localhost:8088` | token: `11111111-1111-1111-1111-111111111111` |
| Fluent Bit monitor | `http://localhost:2020` | none |

---

## 4. What Was Changed and Why

### 4.1 `pom.xml` (root)

Added `<logstash-logback-encoder.version>8.0</logstash-logback-encoder.version>` to centralise the version.

### 4.2 `hex-application/pom.xml`

Two new dependencies:

```xml
<!-- Prometheus metrics registry — exposes /actuator/prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Structured JSON logging for Fluent Bit → Splunk pipeline -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

**Why `micrometer-registry-prometheus`?**
Spring Boot 3 ships with Micrometer pre-wired. Adding `micrometer-registry-prometheus` is all that's needed to activate the Prometheus exposition format at `/actuator/prometheus`. No code changes required.

**Why `logstash-logback-encoder`?**
Standard Logback `%msg` output is plain text — Splunk can index it but you can't search structured fields. The Logstash encoder emits each log event as a single-line JSON object (`@timestamp`, `level`, `logger`, `message`, `stack_trace`, plus any MDC context). This means Splunk can search by field:
```
index=main level=ERROR service=hexagonal-scim
```

### 4.3 `application.yml` and `application-docker.yml`

Added:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      application: hexagonal-scim   # labels every metric — used in Prometheus queries
    distribution:
      percentiles-histogram:
        http.server.requests: true  # generates histogram buckets — required for P95/P99
      slo:
        http.server.requests: 50ms,100ms,200ms,500ms,1s,2s  # SLO boundary buckets
```

**Why `percentiles-histogram: true`?**
Without this, Prometheus only receives counter and gauge metrics. To calculate P50/P95/P99 latency in Grafana you need histogram buckets (`_bucket` suffix). The `histogram_quantile()` PromQL function requires them.

### 4.4 `logback-spring.xml` (new)

Profile-aware Logback configuration:

| Profile | Output | Format |
|---|---|---|
| (none — local mvn run) | Console | Human-readable coloured text |
| `docker` | Console + `/app/logs/app.log` | JSON (logstash-logback-encoder) |

The `docker` profile writes JSON to both stdout (visible in `docker compose logs`) and a rolling file on the `app_logs` Docker volume (read by Fluent Bit).

### 4.5 `docker-compose.yml`

Added `app_logs` named volume:
```yaml
app:
  volumes:
    - app_logs:/app/logs   # Spring Boot writes JSON logs here

volumes:
  app_logs:                # Shared with fluent-bit container
    driver: local
```

### 4.6 `docker-compose.override.yml`

Added four new services: `prometheus`, `grafana`, `splunk`, `fluent-bit`. These are **auto-loaded locally** but **never run in production** (override is skipped when `-f docker-compose.yml` is passed explicitly).

### 4.7 `docker-compose.observability.yml` (new)

For staging environments that need observability but not local-dev port bindings. Uses environment variables for credentials instead of hardcoded values.

---

## 5. Metrics Pipeline: Spring Boot → Prometheus → Grafana

### How it works — step by step

```
① Spring Boot starts
   └─ Micrometer initializes, registers metric binders:
      • JVM memory, GC, threads
      • HTTP server request timer (auto-wired by Spring MVC)
      • HikariCP connection pool
      • Custom metrics (if added via MeterRegistry)

② Micrometer serialises metrics to Prometheus format
   └─ Available at GET /actuator/prometheus
      Example output:
        http_server_requests_seconds_count{...} 42.0
        http_server_requests_seconds_sum{...} 2.3
        http_server_requests_seconds_bucket{le="0.05",...} 38.0
        jvm_memory_used_bytes{area="heap",...} 45678912.0

③ Prometheus scrapes /actuator/prometheus every 10 seconds
   └─ Stores time-series data in /prometheus volume (7-day retention locally)

④ Grafana queries Prometheus via HTTP API
   └─ Renders dashboards — auto-provisioned from docker/grafana/dashboards/
```

### Verify the metrics endpoint

```bash
# Should return Prometheus text format
curl -s http://localhost:8080/actuator/prometheus | head -20

# Check what Prometheus scraped
# In browser: http://localhost:9090/targets
# All targets should show state=UP
```

### Key metric names

| Metric | Description |
|---|---|
| `http_server_requests_seconds_count` | Total HTTP request count (by method, uri, status, outcome) |
| `http_server_requests_seconds_sum` | Total time spent in all requests |
| `http_server_requests_seconds_bucket` | Histogram buckets for P50/P95/P99 |
| `jvm_memory_used_bytes{area="heap"}` | Heap memory in use |
| `jvm_memory_max_bytes{area="heap"}` | Maximum heap size |
| `process_cpu_usage` | JVM process CPU (0.0–1.0) |
| `jvm_threads_live_threads` | Live thread count |
| `jvm_gc_pause_seconds_sum` | Time spent in GC pauses |
| `hikaricp_connections_active` | Active DB connections |

---

## 6. Logging Pipeline: Spring Boot → Fluent Bit → Splunk

### How it works — step by step

```
① Spring Boot (docker profile) writes JSON logs to /app/logs/app.log
   └─ Each line is one JSON object:
      {
        "@timestamp": "2026-05-11T10:15:30.123Z",
        "level": "INFO",
        "logger": "com.example.user.api.UserControllerAdapter",
        "thread": "http-nio-8080-exec-1",
        "message": "GET /api/v1/users → 200 OK",
        "service": "hexagonal-scim",
        "environment": "docker"
      }

② Fluent Bit tails /app/logs/app.log (shared Docker volume)
   └─ Parses each line with the json parser (extracts @timestamp as event time)
   └─ Adds: host, sourcetype, index metadata
   └─ Buffers events; retries up to 10 times if Splunk is unavailable

③ Fluent Bit ships events to Splunk HEC
   └─ POST https://splunk:8088/services/collector/event
      Authorization: Splunk 11111111-1111-1111-1111-111111111111

④ Splunk indexes the event
   └─ Searchable immediately after indexing
   └─ Fields extracted automatically from JSON (field discovery)
```

### Why this log shipping approach?

| Alternative | Problem |
|---|---|
| Fluent Bit reads `/var/lib/docker/containers` | Not accessible inside Docker Desktop for Mac (host path is inside a VM) |
| Spring Boot → Splunk Logback appender directly | Tight coupling between app and Splunk — app crashes if Splunk is down |
| Docker `splunk` logging driver | `docker compose logs` stops working — bad developer experience |
| **Shared volume + Fluent Bit** (our approach) | Platform-independent, zero app coupling, logs always visible in docker compose logs |

### Verify the logging pipeline

```bash
# 1. Check Fluent Bit health
curl http://localhost:2020

# 2. Check logs are being written
docker exec hexagonal-scim-app tail -f /app/logs/app.log

# 3. Check Fluent Bit is reading and shipping
docker logs hexagonal-scim-fluent-bit --tail=20

# 4. In Splunk → Search → run:
# index=main sourcetype="hexagonal-scim:json" | head 10
```

---

## 7. Multi-Environment Strategy

### Local development (auto-loaded)

```bash
docker compose up          # Loads docker-compose.yml + docker-compose.override.yml
docker compose up --build  # Same, rebuilds images
```

All 8 services start: `db`, `keycloak`, `app`, `frontend`, `prometheus`, `grafana`, `splunk`, `fluent-bit`.

### Production (no observability containers)

```bash
docker compose -f docker-compose.yml up --build
```

Only 4 services: `db`, `keycloak`, `app`, `frontend`. No Prometheus, no Grafana, no Splunk (these should be managed infrastructure in production — see below).

### Staging (observability, no local ports)

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up --build
```

Runs all services but observability tools are not bound to host ports. In staging:
- Grafana sits behind Nginx/Traefik
- Splunk HEC endpoint is the corporate Splunk Cloud URL (set `SPLUNK_HOST` env var)
- Prometheus can be federated to a central Thanos/Mimir

### Production observability recommendations

| Tool | Replace local with |
|---|---|
| Prometheus | Managed Prometheus (AWS Managed Prometheus, Grafana Cloud, Thanos) |
| Grafana | Grafana Cloud or self-hosted behind a reverse proxy with SSO |
| Splunk | Splunk Cloud or Splunk Enterprise on dedicated hosts |
| Fluent Bit | Same — Fluent Bit is production-grade, just point to external HEC URL |

---

## 8. Starting the Stack

### First start (everything from scratch)

```bash
# Pull all images first (Splunk is 4GB — do this on good network)
docker compose pull

# Start everything
docker compose up --build

# Expected startup order (due to healthchecks):
#   db → healthy
#   keycloak → healthy (takes 60-90s)
#   app → healthy (depends on db + keycloak)
#   prometheus → healthy (depends on app)
#   grafana → healthy (depends on prometheus)
#   splunk → healthy (takes 2-3 min on first start)
#   fluent-bit → healthy (depends on splunk + app)
```

### After Splunk initialises (3 min)

Open `http://localhost:8000` → admin / Admin1234!

Splunk will prompt you to change the password on first login. **Leave it as `Admin1234!` in local dev** or update the docker-compose.override.yml if you change it.

### Without Splunk (faster startup for quick dev sessions)

Create a local override that disables Splunk:

```bash
# Quick start without Splunk + Fluent Bit
docker compose up db keycloak app frontend prometheus grafana
```

### Without all observability (fastest)

```bash
docker compose -f docker-compose.yml up --build
```

---

## 9. Grafana Dashboard Guide

### Opening the dashboard

1. Go to `http://localhost:3001`
2. Login: admin / admin
3. Left sidebar → **Dashboards** → **Hexagonal SCIM** folder → **Hexagonal SCIM — Spring Boot 3 / JVM**

### Dashboard panels explained

| Panel | What to look for |
|---|---|
| **Request Rate** | Drops to 0 = app is down |
| **Error Rate (5xx)** | Any value > 0 needs investigation |
| **P95 Latency** | >500ms = too slow |
| **JVM Heap Usage** | >90% = imminent OutOfMemoryError — increase heap or find memory leak |
| **HTTP Request Rate by Status** | GREEN=success, ORANGE=client error, RED=server error |
| **HTTP Latency Percentiles** | p99 should be < 2s (matches SLO boundary configured) |
| **JVM Memory — Heap** | "Used" steadily approaching "Max" = memory leak |
| **CPU Usage** | "JVM Process" > 80% = compute-bound — profile the app |
| **JVM Threads** | Steadily increasing count without requests = thread leak |
| **GC Pause Duration** | >100ms/s = GC pressure — tune heap or reduce allocations |
| **HTTP Request Rate by URI** | Find which endpoints drive most traffic |

### Importing community dashboards

Grafana's community (grafana.com/dashboards) has excellent Spring Boot dashboards:

```
Dashboard ID 17175 — Spring Boot 3.x Statistics (Micrometer)
Dashboard ID 4701  — JVM Micrometer (classic)
Dashboard ID 11378 — Hikari Connection Pool
```

To import:
1. Grafana → Dashboards → **Import**
2. Enter the dashboard ID
3. Select the **Prometheus** datasource
4. **Import**

### Modifying the provisioned dashboard

The dashboard JSON lives at `docker/grafana/dashboards/hexagonal-scim.json`.
Grafana's `updateIntervalSeconds: 30` means it hot-reloads from disk every 30 seconds.
Edit the JSON → save → Grafana picks it up automatically.

---

## 10. Splunk Log Search Guide

### Opening Splunk Search

1. `http://localhost:8000` → admin / Admin1234!
2. Top nav → **Search & Reporting**

### Basic searches

```splunk
# All app events
index=main sourcetype="hexagonal-scim:json"

# Last 15 minutes
index=main sourcetype="hexagonal-scim:json" earliest=-15m

# Only ERROR and above
index=main sourcetype="hexagonal-scim:json" level="ERROR" OR level="WARN"

# Specific logger
index=main sourcetype="hexagonal-scim:json" logger="com.example.user.api.UserControllerAdapter"

# Messages containing "exception"
index=main sourcetype="hexagonal-scim:json" message="*exception*"

# Count events by level (last hour)
index=main sourcetype="hexagonal-scim:json"
| stats count by level
| sort -count
```

### Useful SPL queries for this service

```splunk
# Request timeline (1-minute buckets)
index=main sourcetype="hexagonal-scim:json"
| timechart span=1m count by level

# Top error messages
index=main sourcetype="hexagonal-scim:json" level=ERROR
| top limit=20 message

# Logs per logger (identify noisy components)
index=main sourcetype="hexagonal-scim:json"
| stats count by logger
| sort -count
```

### Setting up a Splunk Alert

1. Run a search (e.g. `index=main level=ERROR | stats count as errors`)
2. Click **Save As → Alert**
3. Set trigger condition: `errors > 0`
4. Configure action: email / Slack webhook

---

## 11. Prometheus Query Reference

Quick PromQL reference for the Spring Boot app. Use these in Grafana panels or the Prometheus UI (`http://localhost:9090`).

```promql
# ─── Request throughput ───────────────────────────────────────────────────────

# Total requests per second
sum(rate(http_server_requests_seconds_count{application="hexagonal-scim"}[2m]))

# Requests per second by HTTP status
sum(rate(http_server_requests_seconds_count{application="hexagonal-scim"}[2m])) by (status)

# Error rate (5xx only)
sum(rate(http_server_requests_seconds_count{application="hexagonal-scim",outcome="SERVER_ERROR"}[2m]))
/
sum(rate(http_server_requests_seconds_count{application="hexagonal-scim"}[2m]))

# ─── Latency ─────────────────────────────────────────────────────────────────

# P50 (median)
histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{application="hexagonal-scim"}[5m])) by (le))

# P95
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="hexagonal-scim"}[5m])) by (le))

# P99
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="hexagonal-scim"}[5m])) by (le))

# Average response time
sum(rate(http_server_requests_seconds_sum{application="hexagonal-scim"}[2m]))
/
sum(rate(http_server_requests_seconds_count{application="hexagonal-scim"}[2m]))

# ─── JVM Memory ──────────────────────────────────────────────────────────────

# Heap used (bytes)
sum(jvm_memory_used_bytes{application="hexagonal-scim",area="heap"})

# Heap usage % (0–1)
sum(jvm_memory_used_bytes{application="hexagonal-scim",area="heap"})
/
sum(jvm_memory_max_bytes{application="hexagonal-scim",area="heap"})

# ─── CPU and Threads ─────────────────────────────────────────────────────────

process_cpu_usage{application="hexagonal-scim"}
jvm_threads_live_threads{application="hexagonal-scim"}

# ─── Account operations ───────────────────────────────────────────────────────

# Rate of account operations per second by type
sum(rate(account_operations_total{application="hexagonal-scim"}[2m])) by (operation)

# Total cumulative account operations
sum(account_operations_total{application="hexagonal-scim"}) by (operation)
```

---

## 12. Prometheus Alerting Rules

Alerting rules are defined in `docker/prometheus/rules/app-alerts.yml` and loaded by Prometheus at startup.

### Enabled rules

| Alert | Condition | Severity | Fire after |
|---|---|---|---|
| `HighServerErrorRate` | 5xx rate > 5% (5m window) | **critical** | 2 min |
| `HighClientErrorRate` | 4xx rate > 20% (5m window) | warning | 5 min |
| `HighP95Latency` | P95 latency > 1 s | warning | 5 min |
| `AppDown` | No HTTP metrics scraped | **critical** | 1 min |
| `HighJvmHeapUsage` | Heap used > 90% | warning | 5 min |
| `CriticalJvmHeapUsage` | Heap used > 95% | **critical** | 2 min |
| `HighGcPressure` | GC time > 100 ms/s | warning | 5 min |
| `AccountOperationsStalled` | Zero account ops for 15 min | info | 15 min |

### View firing alerts

Open `http://localhost:9090/alerts` in the Prometheus UI. Alerts move from `PENDING` to `FIRING` once the `for:` duration is exceeded.

### Connect Prometheus AlertManager

To route alerts to Slack / PagerDuty / email, deploy [AlertManager](https://prometheus.io/docs/alerting/latest/alertmanager/) and add to `prometheus.yml`:

```yaml
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

---

## 13. Account Operations Metrics

`AccountControllerAdapter` emits a Micrometer **Counter** for every successful API call.

### Metric details

| Property | Value |
|---|---|
| Metric name (Micrometer) | `account.operations` |
| Metric name (Prometheus) | `account_operations_total` |
| Type | Counter |
| Tags | `operation` (create / get-all / get-by-id / delete), `application` (hexagonal-scim) |

### Example Prometheus output

```
# HELP account_operations_total
# TYPE account_operations_total counter
account_operations_total{application="hexagonal-scim",operation="create"} 42.0
account_operations_total{application="hexagonal-scim",operation="get-all"} 157.0
account_operations_total{application="hexagonal-scim",operation="get-by-id"} 83.0
account_operations_total{application="hexagonal-scim",operation="delete"} 19.0
```

### Grafana panels (Row 6)

Two panels were added to the **Hexagonal SCIM — Spring Boot 3 / JVM** dashboard:

| Panel | Type | Query |
|---|---|---|
| **Account Operations Rate** | Time series | `sum(rate(account_operations_total[2m])) by (operation)` |
| **Total Account Operations** | Stat | `sum(account_operations_total) by (operation)` |

### PromQL queries

```promql
# Rate per second — see which operations are called most
sum(rate(account_operations_total{application="hexagonal-scim"}[2m])) by (operation)

# Cumulative totals
sum(account_operations_total{application="hexagonal-scim"}) by (operation)

# Only create operations
rate(account_operations_total{application="hexagonal-scim",operation="create"}[5m])
```

---

## 14. File Reference

| File | Purpose |
|---|---|
| `hex-application/pom.xml` | Adds `micrometer-registry-prometheus` + `logstash-logback-encoder` |
| `hex-inbound-adapter-web/pom.xml` | Adds `micrometer-core` for `MeterRegistry` injection in adapters |
| `hex-application/src/main/resources/application.yml` | Exposes `/actuator/prometheus`, enables histograms |
| `hex-application/src/main/resources/application-docker.yml` | Same for docker profile + `environment=docker` metric tag |
| `hex-application/src/main/resources/logback-spring.xml` | Profile-aware Logback: plain text locally, JSON file in docker |
| `hex-inbound-adapter-web/src/main/java/.../api/AccountControllerAdapter.java` | Emits `account.operations` Micrometer counter per API call |
| `docker/prometheus/prometheus.yml` | Prometheus scrape config — scrapes `app:8080/actuator/prometheus` |
| `docker/prometheus/rules/app-alerts.yml` | Prometheus alerting rules (HTTP errors, JVM heap, latency, account ops) |
| `docker/grafana/provisioning/datasources/prometheus.yml` | Auto-provisions Prometheus datasource in Grafana |
| `docker/grafana/provisioning/dashboards/dashboards.yml` | Tells Grafana where to find dashboard JSON files |
| `docker/grafana/dashboards/hexagonal-scim.json` | Custom Spring Boot 3 / JVM Grafana dashboard |
| `docker/fluent-bit/fluent-bit.conf` | Fluent Bit pipeline: tail → enrich → Splunk HEC |
| `docker/fluent-bit/parsers.conf` | JSON parser for logstash-logback-encoder output |
| `docker-compose.yml` | Adds `app_logs` volume to `app` service |
| `docker-compose.override.yml` | Adds Prometheus, Grafana, Splunk, Fluent Bit for local dev |
| `docker-compose.observability.yml` | Same stack but for explicit staging use (no host port bindings) |

---

## 15. Extending the Stack

### Add a custom metric to Spring Boot

```java
@RestController
public class UserControllerAdapter {

    private final Counter userCreationCounter;

    public UserControllerAdapter(MeterRegistry meterRegistry) {
        // Register a counter — automatically visible in Prometheus and Grafana
        this.userCreationCounter = Counter.builder("users.created.total")
                .description("Total number of users created successfully")
                .tag("application", "hexagonal-scim")
                .register(meterRegistry);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        UserResponse response = // ... use case call ...;
        userCreationCounter.increment();
        return ResponseEntity.status(201).body(response);
    }
}
```

After the next Prometheus scrape, query: `users_created_total{application="hexagonal-scim"}`

### Add a Prometheus alerting rule

Create `docker/prometheus/rules/app-alerts.yml`:

```yaml
groups:
  - name: hexagonal-scim
    rules:
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{application="hexagonal-scim",outcome="SERVER_ERROR"}[5m]))
          /
          sum(rate(http_server_requests_seconds_count{application="hexagonal-scim"}[5m]))
          > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High 5xx error rate (> 5%)"
          description: "Error rate is {{ $value | humanizePercentage }}"

      - alert: HighJvmHeap
        expr: |
          sum(jvm_memory_used_bytes{application="hexagonal-scim",area="heap"})
          /
          sum(jvm_memory_max_bytes{application="hexagonal-scim",area="heap"})
          > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM heap usage above 90%"
```

Uncomment the `rule_files` section in `docker/prometheus/prometheus.yml` and point it to this file.

### Add MDC context to logs (request tracing)

Add a Spring MVC filter to populate MDC for every request:

```java
@Component
public class MdcLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            MDC.put("method",  req.getMethod());
            MDC.put("uri",     req.getRequestURI());
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

All log events for that request will now include `requestId`, `method`, `uri` — searchable in Splunk:
```splunk
index=main requestId="abc-123-..."
```

### Add Keycloak metrics

Uncomment `KC_METRICS_ENABLED: "true"` in `docker-compose.yml` and uncomment the Keycloak job in `docker/prometheus/prometheus.yml`.

---

## 16. Production Checklist

### Security

- [ ] Replace hardcoded `SPLUNK_PASSWORD: Admin1234!` with a secret (Vault, AWS Secrets Manager, K8s Secret)
- [ ] Replace hardcoded `SPLUNK_HEC_TOKEN` with a secret
- [ ] Replace hardcoded `GF_SECURITY_ADMIN_PASSWORD: admin` with a secret
- [ ] Restrict `/actuator/prometheus` to the internal monitoring network only (not publicly accessible)
- [ ] Enable TLS on Fluent Bit → Splunk HEC with full certificate verification (`TLS.Verify On`)
- [ ] Enable Grafana authentication (LDAP, OAuth, SSO) — disable anonymous access

### Retention

- [ ] Configure Prometheus `--storage.tsdb.retention.time=30d` (or use Thanos/Mimir for long-term)
- [ ] Configure Splunk index retention policy via Index Settings
- [ ] Configure Logback `maxHistory` and `totalSizeCap` for log volume management

### Reliability

- [ ] Deploy Prometheus with at least 2 replicas (HA Prometheus pair) for staging/production
- [ ] Use Grafana persistence (PostgreSQL backend recommended over SQLite for production)
- [ ] Deploy Fluent Bit as a DaemonSet in Kubernetes (one per node)

### Alerting

- [ ] Configure Prometheus AlertManager with routing to PagerDuty / Slack
- [ ] Set up Splunk alerts for ERROR-level log spikes
- [ ] Set up Grafana alert panels on SLO-breach panels

---

## 17. Troubleshooting

### Prometheus shows `app` target as DOWN

```bash
# Verify the actuator endpoint is accessible
curl http://localhost:8080/actuator/prometheus | head -5

# Check Prometheus can reach the app
docker exec hexagonal-scim-prometheus \
  wget -qO- http://app:8080/actuator/prometheus | head -5
```

### Grafana shows "No data"

1. Confirm Prometheus target is UP: `http://localhost:9090/targets`
2. In Grafana panel → **Edit** → **Query Inspector** → check the PromQL error
3. Verify metric name with: `http://localhost:9090/graph` → type `http_server` → see autocomplete

### Splunk shows no events

```bash
# Check Fluent Bit is running and shipping
docker logs hexagonal-scim-fluent-bit --tail=30

# Verify log file exists in the shared volume
docker exec hexagonal-scim-app ls -la /app/logs/

# Check if Splunk HEC is accepting events manually
curl -k https://localhost:8088/services/collector/event \
  -H "Authorization: Splunk 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"event": "test event from curl", "sourcetype": "test"}'
# Expected: {"text":"Success","code":0}
```

### Splunk is not starting (healthcheck fails)

Splunk's first startup is slow (~3 minutes). Wait. If still failing:

```bash
docker logs hexagonal-scim-splunk | tail -30

# Common issue: insufficient memory
# Splunk requires ≥ 2GB RAM. Increase Docker Desktop memory limit to 6GB+
```

### Fluent Bit won't connect to Splunk

Fluent Bit has `Retry_Limit 10` and `restart: unless-stopped`. Once Splunk is healthy, Fluent Bit will reconnect automatically. All events written to the log file while Fluent Bit was retrying are preserved (the `tail` position is tracked in `/var/lib/fluent-bit/app.db`).

### Log file not in JSON format

Verify the correct Spring profile is active:

```bash
# Should show SPRING_PROFILES_ACTIVE=docker
docker inspect hexagonal-scim-app | jq '.[0].Config.Env[]' | grep SPRING_PROFILES

# Check log format
docker exec hexagonal-scim-app tail -1 /app/logs/app.log | python3 -m json.tool
```

---

## 18. Glossary

| Term | Definition |
|---|---|
| **Micrometer** | Vendor-neutral metrics facade for JVM apps — the Spring Boot equivalent of SLF4J for metrics |
| **Prometheus** | Pull-based time-series metrics database. Scrapes endpoints every N seconds, stores locally |
| **PromQL** | Prometheus Query Language — functional query language for time-series data |
| **Grafana** | Metrics visualization platform. Queries Prometheus, renders dashboards |
| **Histogram** | Metric type that counts observations in configurable buckets — required for percentile calculation |
| **Quantile / Percentile** | P95 = 95% of requests completed faster than this value |
| **SLO bucket** | Pre-configured histogram bucket aligned to SLO targets (e.g. 200ms, 500ms) |
| **Logstash encoder** | Logback encoder that formats each log event as a single JSON object |
| **Fluent Bit** | Lightweight log and metrics processor/forwarder — ~20MB RAM footprint |
| **Splunk HEC** | HTTP Event Collector — Splunk's REST endpoint for receiving log events via HTTP POST |
| **MDC** | Mapped Diagnostic Context — thread-local key-value store in Logback, added to every log event |
| **Docker named volume** | Persistent storage managed by Docker — survives container restarts and recreations |
| **Tail input** | Fluent Bit's file reading plugin — monitors a file for new lines, like `tail -f` |
| **Time-series database** | Optimised for storing sequences of (timestamp, value) pairs — Prometheus is one |

---

*Document maintained by the engineering team. Update when new metrics, logs, or dashboards are added.*

