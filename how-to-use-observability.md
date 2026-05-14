# How to Use the Observability Stack

> **Audience:** New / junior developers joining the team.  
> **Goal:** Get the full stack running locally, understand what each tool does, and know where to look when something goes wrong.

---

## Table of Contents

1. [What Is Observability?](#1-what-is-observability)
2. [Stack Overview](#2-stack-overview)
3. [Prerequisites](#3-prerequisites)
4. [Starting the Full Stack](#4-starting-the-full-stack)
5. [Service URLs & Credentials](#5-service-urls--credentials)
6. [Grafana — Dashboards & Metrics](#6-grafana--dashboards--metrics)
7. [Prometheus — Raw Metrics](#7-prometheus--raw-metrics)
8. [Splunk — Structured Logs](#8-splunk--structured-logs)
9. [Fluent Bit — Log Shipping](#9-fluent-bit--log-shipping)
10. [Spring Boot Actuator](#10-spring-boot-actuator)
11. [Common Investigation Workflows](#11-common-investigation-workflows)
12. [Stopping the Stack](#12-stopping-the-stack)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What Is Observability?

Observability is the ability to understand what your application is doing **from the outside**, using three pillars:

| Pillar | Question it answers | Tool in this project |
|--------|--------------------|-----------------------|
| **Metrics** | *How many requests? How fast? How many errors?* | Micrometer → Prometheus → Grafana |
| **Logs** | *What exactly happened, and when?* | Logback (JSON) → Fluent Bit → Splunk |
| **Traces** | *Which service caused the slowdown across the whole request path?* | *(OpenTelemetry / Zipkin — planned, see `next-steps.md`)* |

Think of it this way:
- **Metrics** tell you *something is wrong*.
- **Logs** tell you *why it's wrong*.
- **Traces** tell you *exactly where in the call chain it went wrong*.

---

you can run it with 
```bash
docker compose stop splunk && docker compose up splunk -d
```

## 2. Stack Overview


┌─────────────────────────────────────────────────────────────────┐
│                        Your Browser                             │
│  http://localhost:3000   http://localhost:3001  http://localhost:8000  │
│     React SPA               Grafana                 Splunk       │
└──────────┬───────────────────────┬───────────────────┬──────────┘
           │                       │                   │
           ▼                       ▼                   ▲
   ┌───────────────┐     ┌──────────────────┐  ┌──────────────────┐
   │  Spring Boot  │────▶│   Prometheus     │  │   Fluent Bit     │
   │  :8080        │     │   :9090          │  │  (log shipper)   │
   │               │     │  scrapes /       │  │  tails app.log   │
   │  /actuator/   │     │  actuator/       │  └──────────────────┘
   │  prometheus   │     │  prometheus      │           ▲
   └───────┬───────┘     └──────────────────┘           │
           │  writes JSON logs                          │
           ▼                                            │
   ┌───────────────┐                                    │
   │  app_logs     │────────────────────────────────────┘
   │  Docker volume│
   └───────────────┘
```

| Service | What it does |
|---------|--------------|
| **Spring Boot app** | Your Java service; emits metrics via `/actuator/prometheus` and writes JSON logs to a shared Docker volume |
| **Prometheus** | Scrapes metrics from the app every 10 s and stores a time-series database |
| **Grafana** | Visualises Prometheus data in dashboards; auto-provisioned, no setup needed |
| **Fluent Bit** | Lightweight log shipper; reads `app.log` from the shared volume and forwards to Splunk |
| **Splunk** | Full-text searchable log store with a web UI |
| **Keycloak** | Identity provider (OAuth2/OIDC); not strictly observability but required for the app to start |

---

## 3. Prerequisites

Make sure these are installed before you begin:

```bash
# Check Docker version (need 20.10+)
docker --version

# Check Compose version (need 2.0+)
docker compose version

# Check available disk space — Splunk image alone is ~4 GB
df -h ~
```

**Minimum specs:**
- 8 GB RAM free
- 10 GB disk space
- Docker Desktop running

> **Apple Silicon (M1/M2/M3)?** The Splunk image is `linux/amd64` only. Docker Desktop handles this via Rosetta 2 emulation automatically — you just need Rosetta 2 enabled (it is by default on macOS).

---

## 4. Starting the Full Stack

### 4.1 First-time setup (or after a clean)

```bash
# Go to the project root
cd /path/to/hexagonal-scim

# Build and start everything (app + all observability tools)
# docker-compose.override.yml is auto-merged — exposes all ports locally
docker compose up --build
```

> ⏳ **First run takes 3–5 minutes.** Splunk starts slowly (~3 min). The app waits for Keycloak to be healthy before it starts.  
> Grab a coffee — subsequent starts are much faster.

### 4.2 Watch services come up

Open a second terminal and watch the health:

```bash
docker compose ps
```

You want all services to show `healthy`:

```
NAME                         STATUS
hexagonal-scim-app           running (healthy)
hexagonal-scim-db            running (healthy)
hexagonal-scim-frontend      running
hexagonal-scim-grafana       running (healthy)
hexagonal-scim-keycloak      running (healthy)
hexagonal-scim-prometheus    running (healthy)
hexagonal-scim-splunk        running (healthy)
hexagonal-scim-fluent-bit    running (healthy)
```

### 4.3 Without observability (faster startup)

If you just want to run the app without Splunk/Prometheus/Grafana:

```bash
docker compose -f docker-compose.yml up --build
```

---

## 5. Service URLs & Credentials

Once everything is healthy, open these in your browser:

| Service | URL | Username | Password |
|---------|-----|----------|----------|
| **React Frontend** | https://localhost:3000 | *(Keycloak login)* | `password` |
| **Spring Boot API** | http://localhost:8080 | *(Bearer token)* | — |
| **Grafana** | http://localhost:3001 | `admin` | `admin` |
| **Prometheus** | http://localhost:9090 | *(none)* | — |
| **Splunk Web** | http://localhost:8000 | `admin` | `Admin1234!` |
| **Splunk HEC** | https://localhost:8088 | *(token)* | `11111111-1111-1111-1111-111111111111` |
| **Keycloak Admin** | https://localhost:8443 | `admin` | `admin` |
| **PostgreSQL** | localhost:5432 | `scim` | `scim` |

> 🔒 **Keycloak uses a self-signed certificate.** Your browser will warn you — click "Advanced → Proceed" to accept it for local development. Never do this in production.

---

## 6. Grafana — Dashboards & Metrics

**URL:** http://localhost:3001 → Login: `admin` / `gizmo`

### 6.1 Finding the dashboard

1. Click the **grid icon (Dashboards)** in the left sidebar
2. Click **"hexagonal-scim"** — this is the auto-provisioned dashboard

### 6.2 What each panel shows

| Panel | What to look for |
|-------|-----------------|
| **Request Rate** | Number of HTTP requests per second across all endpoints |
| **HTTP Error Rate (5xx)** | Server errors — should be 0 in normal operation |
| **HTTP Error Rate (4xx)** | Client errors (bad requests, not found, unauthorized) |
| **Latency Percentiles** | p50 / p95 / p99 response times — p99 should be under 500ms |
| **JVM Heap Used** | Memory consumption — watch for a steadily growing line (memory leak) |
| **Active DB Connections** | Database connection pool usage |

### 6.3 Changing the time range

At the top right, you can change the time range:
- **Last 15 minutes** — for live debugging
- **Last 1 hour** — for recent incidents
- **Last 24 hours** — for daily trend review

Click the **auto-refresh icon** next to the time range and set it to **10s** when actively debugging.

### 6.4 Useful PromQL queries to try

Click any panel title → **Edit** to see the raw query, or go to **Explore** (compass icon) and try:

```promql
# Overall request rate
rate(http_server_requests_seconds_count{application="hexagonal-scim"}[1m])

# Error rate (4xx + 5xx)
rate(http_server_requests_seconds_count{application="hexagonal-scim", status=~"4..|5.."}[1m])

# 95th percentile latency for the Users API
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket{uri=~"/api/v1/users.*"}[5m])
)

# JVM heap used in MB
jvm_memory_used_bytes{area="heap"} / 1024 / 1024

# Account operations total
account_operations_total

# Account operation error rate
rate(account_operations_total{status="error"}[5m])
```

### 6.5 Editing a dashboard

The dashboards are stored in `docker/grafana/dashboards/hexagonal-scim.json`.  
To add a new panel:
1. Click **Add → Visualization** in Grafana
2. Configure your query
3. Click **Save dashboard**
4. Then copy the JSON from Grafana's "Share → Export" and update the file

> Grafana auto-loads dashboards from that directory — your changes survive container restarts.

---

## 7. Prometheus — Raw Metrics

**URL:** http://localhost:9090

### 7.1 Check the app is being scraped

1. Go to **Status → Targets**
2. You should see `http://app:8080/actuator/prometheus` with state **UP**
3. If it shows DOWN, the app is either not running or not healthy

### 7.2 Browse available metrics

1. Click **Graph** tab
2. In the expression box, start typing `http_server` — autocomplete shows all available metrics
3. Click **Execute** to see the raw values

### 7.3 Key metrics to know

```
# All HTTP request counts
http_server_requests_seconds_count

# JVM memory
jvm_memory_used_bytes
jvm_memory_max_bytes

# Database connection pool
hikaricp_connections_active
hikaricp_connections_idle

# Application-specific counters (custom)
account_operations_total{operation="create", status="success"}
account_operations_total{operation="delete", status="error"}
```

---

## 8. Splunk — Structured Logs

**URL:** http://localhost:8000 → Login: `admin` / `Admin1234!`

> ⏳ Splunk takes 2–3 minutes to fully start on first run. If the page doesn't load, wait a bit and refresh.

### 8.1 Finding your logs

1. From the Splunk home page, click **Search & Reporting**
2. In the search bar, enter:

```spl
index=main sourcetype="hexagonal-scim:json"
```

3. Click the **magnifying glass** or press Enter
4. Change the time picker to **Last 15 minutes**

You'll see live JSON log events from the Spring Boot app.

### 8.2 Understanding log fields

Each log line is a JSON object with these key fields:

| Field | Example | Meaning |
|-------|---------|---------|
| `timestamp` | `2026-05-14T10:23:45.123Z` | When the log was written |
| `level` | `INFO`, `WARN`, `ERROR` | Log severity |
| `logger_name` | `c.e.user.api.AccountControllerAdapter` | Which class wrote the log |
| `message` | `Creating account for userId=42` | Human-readable message |
| `userId` | `42` | MDC context — which user was being processed |
| `accountId` | `7` | MDC context — which account (if applicable) |
| `thread_name` | `http-nio-8080-exec-3` | HTTP request thread |

### 8.3 Useful Splunk searches

```spl
# All ERROR logs
index=main sourcetype="hexagonal-scim:json" level=ERROR

# Logs for a specific user (replace 42 with real userId)
index=main sourcetype="hexagonal-scim:json" userId=42

# Account creation events only
index=main sourcetype="hexagonal-scim:json" message="*Creating account*"

# All 500 errors with stack traces
index=main sourcetype="hexagonal-scim:json" level=ERROR logger_name="*Controller*"

# Count requests per minute
index=main sourcetype="hexagonal-scim:json"
| timechart span=1m count by level

# Errors in the last hour, grouped by logger
index=main sourcetype="hexagonal-scim:json" level=ERROR earliest=-1h
| stats count by logger_name
| sort -count
```

### 8.4 Creating a saved search

1. Run your search
2. Click **Save As → Report**
3. Give it a name (e.g. "Account Errors Last Hour")
4. It will appear under **Reports** for quick access

---

## 9. Fluent Bit — Log Shipping

**There is no web UI for Fluent Bit** — it runs silently in the background.

### 9.1 What it does

1. **Reads** `/app/logs/app.log` from the shared `app_logs` Docker volume
2. **Parses** each JSON line using the config in `docker/fluent-bit/parsers.conf`
3. **Forwards** log events to Splunk via HTTP Event Collector (HEC)

### 9.2 Check it's working

```bash
# View Fluent Bit live logs
docker compose logs -f fluent-bit

# You should see lines like:
# [engine] started
# [output:splunk/splunk.0] ...
```

### 9.3 Check the health endpoint

```bash
curl http://localhost:2020
```

Returns `{"pid":1,...}` if healthy.

### 9.4 Configuration files

| File | Purpose |
|------|---------|
| `docker/fluent-bit/fluent-bit.conf` | Input (tail app.log), filter, output (Splunk HEC) |
| `docker/fluent-bit/parsers.conf` | JSON parser definition |

---

## 10. Spring Boot Actuator

The app exposes health and metrics endpoints directly.

### 10.1 Health check

```bash
curl http://localhost:8080/actuator/health | python3 -m json.tool
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

If any component is `DOWN`, the app has a problem.

### 10.2 Raw Prometheus metrics

```bash
curl http://localhost:8080/actuator/prometheus | grep http_server
```

This is the raw text that Prometheus scrapes every 10 seconds.

### 10.3 Info endpoint

```bash
curl http://localhost:8080/actuator/info | python3 -m json.tool
```

Shows app version and build info.

### 10.4 All available endpoints

```bash
curl http://localhost:8080/actuator | python3 -m json.tool
```

> **Security note:** Only `health`, `info`, `prometheus`, and `metrics` are exposed. This is intentional — never expose `/actuator/env`, `/actuator/beans`, or `/actuator/shutdown` in production.

---

## 11. Common Investigation Workflows

### 🔴 "The app returned a 500 error — what happened?"

1. **Grafana** → Open the dashboard → Check **HTTP Error Rate (5xx)** panel → note the timestamp
2. **Splunk** → Search:
   ```spl
   index=main sourcetype="hexagonal-scim:json" level=ERROR earliest=-15m
   ```
3. Look at the `message` and `stack_trace` fields in Splunk
4. Note the `userId` from MDC — useful for correlating what the user was doing

### 🟡 "Responses are slow — what's taking so long?"

1. **Grafana** → Check **Latency Percentiles** panel → which percentile spiked?
2. **Prometheus** → Run:
   ```promql
   histogram_quantile(0.99,
     rate(http_server_requests_seconds_bucket[5m])
   )
   ```
3. **Splunk** → Look for `WARN` logs that might indicate slow DB queries

### 🟠 "Lots of 4xx errors — are clients sending bad requests?"

1. **Grafana** → Check **HTTP Error Rate (4xx)** panel
2. **Splunk** → Search:
   ```spl
   index=main sourcetype="hexagonal-scim:json" level=WARN
   ```
   (Validation errors are logged at WARN level)

### 🔵 "Memory is climbing — is there a leak?"

1. **Grafana** → Check **JVM Heap Used** panel over the last few hours
2. If it's steadily growing without flattening, escalate to a senior engineer

### ℹ️ "I just deployed — is the new version running?"

```bash
# Check the container is running the new image
docker compose ps

# Check startup logs
docker compose logs app | tail -50

# Confirm health
curl http://localhost:8080/actuator/health
```

---

## 12. Stopping the Stack

```bash
# Stop everything (keeps data volumes — fast restart next time)
docker compose down

# Stop AND delete all data (full reset — slow next start)
docker compose down -v

# Stop a single service
docker compose stop splunk
```

> Use `docker compose down -v` if you want a completely fresh start (e.g. after a major database schema change).

---

## 13. Troubleshooting

### Problem: Splunk UI is not loading

**Cause:** Splunk takes 2–3 minutes to start on first run.  
**Fix:** Wait and refresh. Check:
```bash
docker compose logs splunk | tail -20
```
Look for `"Splunk has started and is accepting connections"`.

---

### Problem: No data in Grafana (empty panels)

**Cause:** Either Prometheus isn't scraping the app, or the app hasn't received any requests.  

**Step 1** — Check Prometheus targets:
```
http://localhost:9090 → Status → Targets
```
The `app:8080/actuator/prometheus` target should be **UP**.

**Step 2** — Send a test request to generate metrics:
```bash
curl http://localhost:8080/actuator/health
```

**Step 3** — Wait 10 seconds (Prometheus scrape interval) and refresh Grafana.

---

### Problem: Logs not appearing in Splunk

**Cause:** Fluent Bit may not be running, or the app hasn't written any logs yet.

```bash
# Check Fluent Bit is healthy
docker compose logs fluent-bit | tail -20

# Check the log file exists and has content
docker compose exec app ls -la /app/logs/

# Trigger a request to generate a log line
curl http://localhost:8080/actuator/health
```

Also make sure `SPRING_PROFILES_ACTIVE=docker` is set (it is in `docker-compose.yml`). This activates the JSON log appender in `logback-spring.xml`.

---

### Problem: "Cannot connect to Docker daemon"

**Fix:** Start Docker Desktop and wait for it to fully initialise (whale icon in menu bar stops animating).

---

### Problem: Port already in use

```bash
# Find what's using port 3001 (Grafana)
lsof -i :3001

# Or use a different port by setting an env var
GRAFANA_PORT=3002 docker compose up
```

---

### Problem: Container keeps restarting

```bash
# Check exit reason
docker compose ps

# Check logs
docker compose logs <service-name> | tail -50
```

---

### Problem: Splunk warning — "Found an empty value for 'allowedDomainList'"

**What it is:** A Splunk security warning that appears in the UI when `allowedDomainList` is not set in `alert_actions.conf`. Without it, Splunk would allow email alerts to be sent to any domain.

**Is it a blocker?** No — email alerts are not used in local development. The warning is cosmetic.

**Fix (already applied):** `docker/splunk/alert_actions.conf` is mounted into the container and restricts email alerts to `localhost` only, suppressing the warning:

```ini
[email]
allowedDomainList = localhost
```

If the warning still shows after restarting, run:

```bash
docker compose down splunk
docker compose up splunk
```

**Production note:** In a real environment, replace `localhost` with your organisation's email domain(s) in the config file, e.g.:

```ini
[email]
allowedDomainList = yourcompany.com, yourdomain.org
```

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────────┐
│  SERVICE       │  URL                      │  CREDENTIALS           │
├─────────────────────────────────────────────────────────────────────┤
│  App (API)     │  http://localhost:8080    │  Bearer token (OAuth2) │
│  Frontend      │  https://localhost:3000   │  Keycloak users        │
│  Grafana       │  http://localhost:3001    │  admin / admin         │
│  Prometheus    │  http://localhost:9090    │  —                     │
│  Splunk Web    │  http://localhost:8000    │  admin / Admin1234!    │
│  Keycloak      │  https://localhost:8443   │  admin / admin         │
└─────────────────────────────────────────────────────────────────────┘

START:   docker compose up --build
STOP:    docker compose down
RESET:   docker compose down -v

LOGS:    docker compose logs -f <service>
HEALTH:  curl http://localhost:8080/actuator/health
METRICS: curl http://localhost:8080/actuator/prometheus
```

---

*For deeper reading, see [`documenttaion/OBSERVABILITY.md`](documenttaion/OBSERVABILITY.md) — architecture diagrams, alert rules, Fluent Bit pipeline details, and Splunk dashboard setup.*

