# Container Runtime Quickstart

A short runbook for choosing and using `docker`, `nerdctl`, and `crictl` in this project.

## Tool Selection Matrix

| Task | Preferred Tool | Why |
|---|---|---|
| Local app stack (`docker compose up`) | `docker` | This repo already uses Docker Compose workflows |
| containerd workflows outside Docker | `nerdctl` | Docker-like UX on top of containerd |
| Kubernetes node runtime debugging | `crictl` | Talks directly to CRI (containerd/CRI-O) |

## Use by Environment

## Local laptop (macOS/Linux dev)

- Use `docker compose` for this repository
- Use `nerdctl` when testing containerd-native behavior
- Use `crictl` only if you are debugging a real CRI endpoint

## Kubernetes worker node

- Use `kubectl` first for normal operations
- Use `crictl` for node-level issues (image pulls, pod sandbox/container state)
- Use `nerdctl --namespace k8s.io` for containerd object visibility when needed

## k3s node

- Typical endpoint: `unix:///run/k3s/containerd/containerd.sock`
- Configure `/etc/crictl.yaml` or pass `--runtime-endpoint` explicitly

## Quick Commands

## nerdctl smoke check

```bash
nerdctl version
nerdctl info
nerdctl pull nginx:1.27-alpine
nerdctl run --name nerdctl-smoke -d -p 18080:80 nginx:1.27-alpine
curl -I http://localhost:18080
nerdctl stop nerdctl-smoke
nerdctl rm nerdctl-smoke
```

## crictl smoke check (node)

```bash
sudo crictl --version
sudo crictl info
sudo crictl pods
sudo crictl ps -a
sudo crictl images
```

## Docs in This Repo

- `nerdctl-cheatsheet.md`
- `crictl-cheatsheet.md`
- `keytool-cheatsheet.md`

## Practical Guidance

- Prefer `docker compose` for day-to-day local development in this project
- Use `nerdctl` when your target runtime is `containerd`
- Use `crictl` for incident/debug scenarios on cluster nodes
- Avoid destructive cleanup commands on production nodes unless approved

