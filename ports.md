# Port Management Cheatsheet

> Quick reference for checking, killing, and debugging ports on macOS.

---

## Table of Contents

1. [Check What's Using a Port](#1-check-whats-using-a-port)
2. [List All Used Ports](#2-list-all-used-ports)
3. [Kill a Process on a Port](#3-kill-a-process-on-a-port)
4. [Check if a Port is Open / Reachable](#4-check-if-a-port-is-open--reachable)
5. [Project Port Reference](#5-project-port-reference)
6. [Docker Port Conflicts](#6-docker-port-conflicts)
7. [One-Liners Cheatsheet](#7-one-liners-cheatsheet)

---

## 1. Check What's Using a Port

### `lsof` — most common tool on macOS

```bash
# Who is using port 8080?
lsof -i :8080

# Who is using port 3001?
lsof -i :3001

# Show PID, command name, and user
lsof -i :8080 -n -P
```

Output explanation:
```
COMMAND   PID  USER   FD   TYPE  DEVICE SIZE/OFF NODE NAME
java     1234  copor  123u IPv6  ...         TCP *:8080 (LISTEN)
#  ↑       ↑    ↑
#  app    PID  your user  ← use PID to kill
```

### `netstat` — shows all active connections

```bash
# All listening ports (macOS)
netstat -an | grep LISTEN

# Filter for a specific port
netstat -an | grep 8080

# Show PID too (requires sudo)
sudo netstat -anp | grep 8080
```

### `ss` — faster alternative (if installed via brew)

```bash
# All listening ports with PID
ss -tlnp

# Filter
ss -tlnp | grep 8080
```

---

## 2. List All Used Ports

```bash
# All listening ports — shows command, PID, port
lsof -i -P -n | grep LISTEN

# Only TCP listening ports
lsof -iTCP -sTCP:LISTEN -n -P

# Sort by port number
lsof -iTCP -sTCP:LISTEN -n -P | sort -k9

# Compact: just port numbers
lsof -iTCP -sTCP:LISTEN -n -P | awk '{print $9}' | sort
```

---

## 3. Kill a Process on a Port

### Option A — find PID first, then kill

```bash
# Step 1: find the PID
lsof -ti :8080

# Step 2: kill it (graceful SIGTERM)
kill $(lsof -ti :8080)

# Step 3: force kill if still running (SIGKILL)
kill -9 $(lsof -ti :8080)
```

### Option B — one command kill

```bash
# Graceful kill
kill $(lsof -ti :8080)

# Force kill
kill -9 $(lsof -ti :8080)

# Kill multiple ports at once
kill -9 $(lsof -ti :8080 -ti :9090 -ti :3001)
```

### Option C — using `fuser` (if installed)

```bash
# Kill whatever is on port 8080
fuser -k 8080/tcp

# Force kill
fuser -k -9 8080/tcp
```

> ⚠️ `kill -9` forces the process to stop immediately without cleanup. Prefer `kill` (no flag) first — only use `-9` if the process doesn't stop after a few seconds.

---

## 4. Check if a Port is Open / Reachable

### `nc` (netcat) — test if port is accepting connections

```bash
# Is port 8080 open on localhost? (exits immediately)
nc -zv localhost 8080

# With a timeout (2 seconds)
nc -zv -w 2 localhost 8080

# Test remote host
nc -zv 192.168.1.100 5432
```

Output:
- `Connection to localhost port 8080 [tcp/*] succeeded!` → port is open ✅
- `Connection refused` → nothing is listening ❌

### `curl` — check HTTP services

```bash
# Is the Spring Boot app responding?
curl -fs http://localhost:8080/actuator/health

# Is Grafana up?
curl -fs http://localhost:3001/api/health

# Check with verbose output for debugging
curl -v http://localhost:8080/actuator/health
```

### `telnet` — old-school port check

```bash
telnet localhost 8080
# Press Ctrl+] then type quit to exit
```

### `nmap` — scan multiple ports (install: `brew install nmap`)

```bash
# Scan common ports on localhost
nmap localhost

# Scan specific ports
nmap -p 8080,9090,3001,8000,5432 localhost

# Check if port is open/closed/filtered
nmap -p 8080 localhost
```

---

## 5. Project Port Reference

All ports used by this project locally (via `docker-compose.override.yml`):

| Port | Service | Protocol | Notes |
|------|---------|----------|-------|
| `3000` | React Frontend (Nginx) | HTTPS | Main app UI |
| `8080` | Spring Boot API | HTTP | Direct API access (bypasses Nginx) |
| `8443` | Keycloak | HTTPS | Admin console + token endpoint |
| `5432` | PostgreSQL | TCP | DB access for tooling (pgAdmin, psql) |
| `9090` | Prometheus | HTTP | Metrics UI |
| `3001` | Grafana | HTTP | Dashboards UI |
| `8000` | Splunk Web | HTTP | Log search UI |
| `8088` | Splunk HEC | HTTPS | Log ingestion endpoint (Fluent Bit → Splunk) |
| `2020` | Fluent Bit | HTTP | Health check endpoint (internal) |

### Check all project ports at once

```bash
for port in 3000 8080 8443 5432 9090 3001 8000 8088; do
  result=$(nc -zv -w 1 localhost $port 2>&1)
  if echo "$result" | grep -q "succeeded"; then
    echo "✅ $port — OPEN"
  else
    echo "❌ $port — closed"
  fi
done
```

---

## 6. Docker Port Conflicts

Docker containers bind ports to the host. If a port is already in use, Docker will fail with:

```
Error response from daemon: Ports are not available: exposing port TCP 0.0.0.0:8080 -> 0.0.0.0:0: listen tcp 0.0.0.0:8080: bind: address already in use
```

### Find and fix the conflict

```bash
# Step 1: find what's on the conflicting port
lsof -i :8080

# Step 2: if it's another Docker container, stop it
docker ps                          # list running containers
docker stop <container_name>       # stop the conflicting container

# Step 3: if it's a native process (Java, node, etc.), kill it
kill -9 $(lsof -ti :8080)

# Step 4: retry
docker compose up
```

### Stop ALL Docker containers (nuclear option)

```bash
# Stop all running containers
docker stop $(docker ps -q)

# Remove all containers (keeps images/volumes)
docker rm $(docker ps -aq)
```

### Check which ports Docker is currently binding

```bash
# All port bindings from all running containers
docker ps --format "table {{.Names}}\t{{.Ports}}"

# For a specific container
docker port hexagonal-scim-app
```

---

## 7. One-Liners Cheatsheet

```bash
# ── Find ───────────────────────────────────────────────────────────────────────
lsof -i :PORT                        # who is on PORT
lsof -iTCP -sTCP:LISTEN -n -P        # all listening TCP ports
lsof -i -P -n | grep LISTEN          # all listening ports with details
netstat -an | grep LISTEN            # all listening (no PIDs)

# ── Kill ───────────────────────────────────────────────────────────────────────
kill $(lsof -ti :PORT)               # graceful kill on PORT
kill -9 $(lsof -ti :PORT)            # force kill on PORT
kill -9 $(lsof -ti :8080 -ti :9090)  # kill multiple ports

# ── Test ───────────────────────────────────────────────────────────────────────
nc -zv localhost PORT                # is PORT open?
curl -fs http://localhost:PORT       # is HTTP service responding?
nmap -p PORT localhost               # port scan (needs brew install nmap)

# ── Docker ────────────────────────────────────────────────────────────────────
docker ps --format "table {{.Names}}\t{{.Ports}}"   # ports by container
docker port CONTAINER                               # ports for one container
docker stop $(docker ps -q)                         # stop all containers

# ── Project specific ──────────────────────────────────────────────────────────
curl -fs http://localhost:8080/actuator/health      # is Spring Boot healthy?
curl -fs http://localhost:9090/-/ready              # is Prometheus ready?
curl -fs http://localhost:3001/api/health           # is Grafana ready?
curl -f http://localhost:2020                       # is Fluent Bit alive?
```

---

*See also [`how-to-use-observability.md`](how-to-use-observability.md) for service-specific troubleshooting when a port is open but the service isn't responding correctly.*

