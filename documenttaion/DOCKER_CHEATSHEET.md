# Docker Cheatsheet

Quick reference for common Docker and Docker Compose commands.

---

## Images

```bash
# List all images
docker images

# List images with filter
docker images | grep hexagonal

# Pull an image
docker pull eclipse-temurin:17-jre-jammy

# Remove a specific image
docker rmi <IMAGE_ID_OR_NAME>

# Remove all unused images (not referenced by any container)
docker image prune -f

# Remove ALL images including used ones
docker rmi -f $(docker images -q)

# Inspect an image
docker inspect <IMAGE_ID_OR_NAME>

# Show image layers/history
docker history <IMAGE_ID_OR_NAME>
```

---

## Containers

```bash
# List running containers
docker ps

# List all containers (running + stopped)
docker ps -a

# Run a container
docker run -d -p 8080:8080 --name myapp myimage

# Run interactively (and remove after exit)
docker run --rm -it ubuntu bash

# Stop a container
docker stop <CONTAINER_ID_OR_NAME>

# Start a stopped container
docker start <CONTAINER_ID_OR_NAME>

# Restart a container
docker restart <CONTAINER_ID_OR_NAME>

# Remove a stopped container
docker rm <CONTAINER_ID_OR_NAME>

# Force remove a running container
docker rm -f <CONTAINER_ID_OR_NAME>

# Remove all stopped containers
docker container prune -f

# Execute a command inside a running container
docker exec -it <CONTAINER_ID_OR_NAME> bash
docker exec -it <CONTAINER_ID_OR_NAME> sh          # Alpine-based images

# Copy files to/from a container
docker cp ./file.txt <CONTAINER>:/app/file.txt
docker cp <CONTAINER>:/app/file.txt ./file.txt
```

---

## Logs

```bash
# View logs
docker logs <CONTAINER_ID_OR_NAME>

# Follow logs (live tail)
docker logs -f <CONTAINER_ID_OR_NAME>

# Last 100 lines
docker logs --tail 100 <CONTAINER_ID_OR_NAME>

# Logs with timestamps
docker logs -t <CONTAINER_ID_OR_NAME>

# Follow last 50 lines with timestamps
docker logs -f --tail 50 -t <CONTAINER_ID_OR_NAME>
```

---

## Volumes

```bash
# List volumes
docker volume ls

# Inspect a volume
docker volume inspect <VOLUME_NAME>

# Create a named volume
docker volume create mydata

# Remove a volume
docker volume rm <VOLUME_NAME>

# Remove all unused volumes
docker volume prune -f
```

---

## Networks

```bash
# List networks
docker network ls

# Inspect a network
docker network inspect <NETWORK_NAME>

# Create a network
docker network create mynetwork

# Remove a network
docker network rm <NETWORK_NAME>

# Remove all unused networks
docker network prune -f
```

---

## Build

```bash
# Build an image from Dockerfile in current directory
docker build -t myapp:latest .

# Build with a specific Dockerfile
docker build -f Dockerfile.prod -t myapp:prod .

# Build without cache
docker build --no-cache -t myapp:latest .

# Build for a specific platform (cross-compile)
docker build --platform linux/amd64 -t myapp:amd64 .
docker build --platform linux/arm64 -t myapp:arm64 .

# Multi-platform build (requires buildx)
docker buildx build --platform linux/amd64,linux/arm64 -t myapp:latest .
```

---

## System Cleanup

```bash
# Show disk usage
docker system df

# Remove all stopped containers, unused networks, dangling images, build cache
docker system prune -f

# Remove everything including unused images and volumes (DESTRUCTIVE)
docker system prune -a --volumes -f
```

---

## Docker Compose

```bash
# Start all services (build if needed)
docker compose up --build

# Start in background (detached)
docker compose up -d --build

# Stop all services
docker compose down

# Stop and remove volumes (wipes database data)
docker compose down -v

# Stop, remove volumes AND images
docker compose down -v --rmi all

# View logs for all services
docker compose logs -f

# View logs for a specific service
docker compose logs -f keycloak
docker compose logs -f app
docker compose logs -f frontend

# Show running service status
docker compose ps

# Execute command inside a service container
docker compose exec app bash
docker compose exec keycloak bash

# Restart a single service
docker compose restart app

# Scale a service (run multiple instances)
docker compose up -d --scale app=3

# Pull latest images
docker compose pull

# Validate compose file
docker compose config
```

---

## This Project — hexagonal-scim

```bash
# Full clean start — LOCAL DEV (loads docker-compose.override.yml automatically)
# Port 8080 (backend) and 5432 (PostgreSQL) are exposed to the host
docker compose down -v && docker compose up --build

# PRODUCTION / CI — base file only, no override, port 8080 closed
docker compose -f docker-compose.yml down -v && docker compose -f docker-compose.yml up --build

# Start in background
docker compose down -v && docker compose up -d --build

# Watch all logs
docker compose logs -f

# Watch specific service logs
docker compose logs -f keycloak
docker compose logs -f app
docker compose logs -f frontend

# Check container status
docker compose ps

# Shell into Spring Boot container
docker compose exec app bash

# Shell into Keycloak container
docker compose exec keycloak bash

# Verify Nginx config (no running containers needed)
docker run --rm \
  -v $(pwd)/frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  -v $(pwd)/docker/certs:/etc/nginx/certs:ro \
  nginx:1.27-alpine nginx -t

# Quick resource usage stats
docker stats
```

---

## Service URLs (Docker Compose)

| Service | URL | Notes |
|---------|-----|-------|
| Frontend | `https://localhost:3000` | React SPA (Nginx + TLS) |
| Backend API | `http://localhost:8080` | Spring Boot |
| Keycloak admin | `https://localhost:8443` | `admin / admin` |
| PostgreSQL | `localhost:5432` | `hexuser / hexpassword / hexdb` |

> **Self-signed cert warning**: visit `https://localhost:8443` first → Accept → then `https://localhost:3000`.

---

## Inspect Running Containers

```bash
# Low-level info (IP, mounts, env vars, etc.)
docker inspect <CONTAINER>

# Resource usage (CPU, memory, network I/O)
docker stats

# Running processes inside a container
docker top <CONTAINER>

# Port mappings
docker port <CONTAINER>
```

---

## Useful One-liners

```bash
# Stop all running containers
docker stop $(docker ps -q)

# Remove all stopped containers
docker rm $(docker ps -aq -f status=exited)

# Remove all dangling (untagged) images
docker rmi $(docker images -f dangling=true -q)

# Get IP address of a container
docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <CONTAINER>

# Export container filesystem as tar
docker export <CONTAINER> > mycontainer.tar

# Save image as tar
docker save myapp:latest | gzip > myapp.tar.gz

# Load image from tar
docker load < myapp.tar.gz
```

