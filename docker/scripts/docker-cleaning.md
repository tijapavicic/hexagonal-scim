# Docker Cleaning

If Docker "builds" still appear after a normal cleanup, the leftover data is usually one of these:

- Build cache
- `buildx` cache
- Unused images
- Unused containers, networks, or volumes
- Docker Desktop UI history that is no longer taking disk space

## Check What Is Taking Space

```bash
docker system df
```

This shows Docker disk usage for images, containers, local volumes, and build cache.

## Remove Standard Build Cache

```bash
docker builder prune
```

Remove all unused build cache, not just dangling cache:

```bash
docker builder prune -a
```

Skip the confirmation prompt:

```bash
docker builder prune --all --force
```

## Remove `buildx` Cache

Sometimes cache is still stored through `buildx` / BuildKit:

```bash
docker buildx prune --all --force
```

## Remove Unused Images

```bash
docker image prune -a
```

## Remove Almost Everything Unused

```bash
docker system prune -a --volumes -f
```

Be careful: this removes unused containers, networks, images, and volumes too.

## Inspect Builders

List all configured builders:

```bash
docker buildx ls
```

If extra builders still exist, remove them:

```bash
docker buildx rm <builder-name>
```

## Recommended Full Cleanup Sequence

Run these in order:

```bash
docker system df
docker buildx prune --all --force
docker builder prune --all --force
docker system prune -a --volumes -f
docker buildx ls
```

If `docker buildx ls` still shows builders you no longer need:

```bash
docker buildx rm <builder-name>
```

## Important Note About Docker Desktop

If "builds" still show in Docker Desktop after cleanup, they may only be history records in the UI rather than real cache or images still consuming disk space.

## Quick Difference Between Commands

- `docker builder prune`: removes build cache
- `docker buildx prune`: removes BuildKit / `buildx` cache
- `docker image prune -a`: removes unused images
- `docker system prune -a --volumes`: removes most unused Docker resources

