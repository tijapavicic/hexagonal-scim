# nerdctl Cheatsheet

Practical `nerdctl` reference for containerd users (Docker-like UX).

## What It Is

- `nerdctl` is a Docker-compatible CLI for `containerd`
- Best for Kubernetes/containerd hosts and rootless setups
- Supports Compose, BuildKit, image namespaces, and snapshots

## Install

### macOS

#### Option A: Rancher Desktop (fastest)

1. Install Rancher Desktop from the official site.
2. Set container runtime to `containerd` in settings.
3. Verify:

```bash
nerdctl version
nerdctl info
```

#### Option B: Lima + nerdctl

```bash
brew install lima
brew install nerdctl
limactl start
nerdctl version
```

Note: On macOS, `nerdctl` typically runs against a Lima VM-backed containerd.

### Linux (Ubuntu/Debian example)

#### Option A: Full bundle (recommended)

```bash
curl -fsSL -o nerdctl.tgz \
  https://github.com/containerd/nerdctl/releases/latest/download/nerdctl-full-$(uname -m).tar.gz
sudo tar -C /usr/local -xzf nerdctl.tgz
nerdctl version
```

#### Option B: package manager (if available in your distro)

```bash
sudo apt update
sudo apt install -y nerdctl
nerdctl version
```

If `containerd` is not running:

```bash
sudo systemctl enable --now containerd
sudo systemctl status containerd --no-pager
```

## Verify Setup

```bash
nerdctl version
nerdctl info
sudo ctr version
```

## 5-Minute Sanity Test

Run this after installation to confirm image pull, container run, logs, exec, and cleanup work end-to-end:

```bash
nerdctl pull nginx:1.27-alpine
nerdctl run --name nerdctl-smoke -d -p 18080:80 nginx:1.27-alpine
nerdctl ps
curl -I http://localhost:18080
nerdctl logs nerdctl-smoke | head -n 20
nerdctl exec -it nerdctl-smoke sh -c 'nginx -v'
nerdctl stop nerdctl-smoke
nerdctl rm nerdctl-smoke
```

Expected quick outcome:

- `nerdctl ps` shows `nerdctl-smoke` while running
- `curl -I` returns `HTTP/1.1 200 OK`
- container stops and removes cleanly

## Core Commands

### Images

```bash
nerdctl pull nginx:alpine
nerdctl images
nerdctl tag nginx:alpine my-nginx:dev
nerdctl rmi my-nginx:dev
```

### Containers

```bash
nerdctl run --name web -d -p 8080:80 nginx:alpine
nerdctl ps
nerdctl logs -f web
nerdctl exec -it web sh
nerdctl stop web
nerdctl rm web
```

### Build

```bash
nerdctl build -t myapp:local .
nerdctl run --rm myapp:local
```

### Volumes and Networks

```bash
nerdctl volume create app-data
nerdctl network create app-net
nerdctl run -d --name web --network app-net -v app-data:/data nginx:alpine
```

### Compose (if enabled)

```bash
nerdctl compose up -d
nerdctl compose ps
nerdctl compose logs -f
nerdctl compose down
```

## Kubernetes/Namespaces Notes

containerd stores objects in namespaces (default is often `default`, k8s uses `k8s.io`).

```bash
nerdctl --namespace k8s.io ps -a
nerdctl --namespace k8s.io images
```

## Rootless Mode (Linux)

```bash
containerd-rootless-setuptool.sh install
systemctl --user start containerd
nerdctl --namespace default info
```

## Troubleshooting

- `cannot connect to containerd`: ensure service is running
- empty results in Kubernetes node: use `--namespace k8s.io`
- permission denied: try `sudo nerdctl ...` or configure rootless properly
- compose command missing: install full nerdctl bundle

Quick checks:

```bash
sudo systemctl status containerd --no-pager
nerdctl --namespace k8s.io ps -a
```

## Safe Practices

- pin image tags (`nginx:1.27-alpine`) instead of `latest`
- scan/sign images in CI before deploy
- avoid running privileged containers unless required
- clean old artifacts periodically:

```bash
nerdctl system prune -a
```
