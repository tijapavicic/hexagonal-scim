# crictl Cheatsheet

Practical `crictl` reference for Kubernetes nodes using CRI runtimes (containerd, CRI-O).

## What It Is

- `crictl` is a debugging CLI for the Kubernetes CRI API
- Use it on cluster nodes when `kubectl` is not enough
- Typical targets: `containerd` and `CRI-O`

## Install

### macOS (client use)

```bash
brew install cri-tools
crictl --version
```

Note: macOS installs the CLI, but real CRI debugging usually happens on Linux nodes.

### Linux (recommended)

```bash
VERSION="v1.31.1"
ARCH="amd64"
curl -LO "https://github.com/kubernetes-sigs/cri-tools/releases/download/${VERSION}/crictl-${VERSION}-linux-${ARCH}.tar.gz"
sudo tar -C /usr/local/bin -xzf "crictl-${VERSION}-linux-${ARCH}.tar.gz"
crictl --version
```

Alternative (if distro package exists):

```bash
sudo apt update
sudo apt install -y cri-tools
crictl --version
```

## Configure Runtime Endpoint

Create `/etc/crictl.yaml`:

```yaml
runtime-endpoint: unix:///run/containerd/containerd.sock
image-endpoint: unix:///run/containerd/containerd.sock
timeout: 10
debug: false
```

Common endpoints:

- containerd: `unix:///run/containerd/containerd.sock`
- CRI-O: `unix:///var/run/crio/crio.sock`
- k3s: `unix:///run/k3s/containerd/containerd.sock`

Test config:

```bash
sudo crictl info
```

## 5-Minute Sanity Test

Run this after install to confirm endpoint wiring and node runtime visibility:

```bash
sudo crictl --version
sudo crictl info
sudo crictl pods
sudo crictl ps -a
sudo crictl images
```

If output is empty, that can still be valid on a quiet node. The important part is no CRI socket/permission errors.

Optional endpoint override test (containerd):

```bash
sudo crictl --runtime-endpoint unix:///run/containerd/containerd.sock info
```

## Core Commands

### Runtime and Node Info

```bash
sudo crictl info
sudo crictl version
```

### Pods (sandboxes)

```bash
sudo crictl pods
sudo crictl pods -q
sudo crictl inspectp <POD_ID>
sudo crictl stopp <POD_ID>
sudo crictl rmp <POD_ID>
```

### Containers

```bash
sudo crictl ps
sudo crictl ps -a
sudo crictl inspect <CONTAINER_ID>
sudo crictl logs <CONTAINER_ID>
sudo crictl exec -it <CONTAINER_ID> sh
sudo crictl stop <CONTAINER_ID>
sudo crictl rm <CONTAINER_ID>
```

### Images

```bash
sudo crictl images
sudo crictl pull nginx:1.27-alpine
sudo crictl inspecti nginx:1.27-alpine
sudo crictl rmi nginx:1.27-alpine
```

### Resource Cleanup

```bash
sudo crictl rm -a
sudo crictl rmp -a
sudo crictl rmi --prune
```

## Useful Debug Flows

### Find crashing container logs quickly

```bash
sudo crictl ps -a
sudo crictl logs <CONTAINER_ID>
```

### Map pod sandbox to containers

```bash
sudo crictl pods
sudo crictl ps -a
sudo crictl inspectp <POD_ID>
```

### Check image pull issues

```bash
sudo crictl images
sudo crictl pull <IMAGE>
sudo journalctl -u containerd -n 100 --no-pager
```

## Troubleshooting

- `connect: no such file or directory`: wrong CRI socket path
- timeout errors: runtime unhealthy or endpoint inaccessible
- permission denied: run with `sudo` on node
- no containers shown: verify you are on the right node/runtime

Quick checks:

```bash
sudo systemctl status containerd --no-pager
sudo ls -l /run/containerd/containerd.sock
sudo crictl --runtime-endpoint unix:///run/containerd/containerd.sock info
```

## Safe Practices

- use `crictl` for troubleshooting, not normal app lifecycle management
- avoid force-removing running workloads on production nodes
- prefer `kubectl` for normal operations, reserve `crictl` for node-level debugging
- collect diagnostics before cleanup operations
