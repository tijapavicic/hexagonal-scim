#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./build-cleanup.sh [--dry-run] [--system] [--help]

Cleans Docker build history and Buildx cache across all discovered builders.

Options:
  --dry-run  Print the cleanup commands without executing them.
  --system   Also run `docker builder prune --all --force` and
             `docker system prune -a --force` after the builder cleanup.
  --help     Show this help message.
EOF
}

dry_run=0
system_prune=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      dry_run=1
      ;;
    --system)
      system_prune=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI is not installed or not in PATH." >&2
  exit 1
fi

run() {
  printf '+'
  printf ' %q' "$@"
  printf '\n'

  if [[ "$dry_run" -eq 0 ]]; then
    "$@"
  fi
}

if ! docker buildx version >/dev/null 2>&1; then
  echo "Docker Buildx is not available." >&2
  exit 1
fi

history_supported=0
if docker buildx history rm --help >/dev/null 2>&1; then
  history_supported=1
fi

contexts="$(docker context ls --format '{{.Name}}' 2>/dev/null || true)"

builders="$(
  docker buildx ls --format '{{json .}}' | awk '
    {
      name = ""
      driver = ""
      endpoint = ""

      if (match($0, /"Name":"[^"]+"/)) {
        name = substr($0, RSTART + 8, RLENGTH - 9)
      }
      if (match($0, /"Driver":"[^"]+"/)) {
        driver = substr($0, RSTART + 10, RLENGTH - 11)
      }
      if (match($0, /"Endpoint":"[^"]+"/)) {
        endpoint = substr($0, RSTART + 12, RLENGTH - 13)
      }

      if (name != "") {
        print name "|" driver "|" endpoint
      }
    }
  ' | awk '!seen[$0]++'
)"

if [[ -z "$builders" ]]; then
  echo "No Buildx builders were found." >&2
  exit 1
fi

echo "Discovered builders:"
while IFS='|' read -r name driver endpoint; do
  [[ -n "$name" ]] || continue

  if [[ -n "$endpoint" ]]; then
    echo " - $name (driver=$driver, endpoint=$endpoint)"
  else
    echo " - $name (driver=$driver)"
  fi
done <<< "$builders"

echo
echo "Cleaning Docker build history and Buildx cache..."

while IFS='|' read -r name driver endpoint; do
  [[ -n "$name" ]] || continue

  docker_cmd=(docker)
  if [[ "$driver" == "docker" && -n "$endpoint" ]] && grep -Fxq "$endpoint" <<< "$contexts"; then
    docker_cmd+=(--context "$endpoint")
  fi

  echo
  echo "Builder: $name"

  if [[ "$history_supported" -eq 1 ]]; then
    run "${docker_cmd[@]}" buildx history rm --builder "$name" --all
  else
    echo "Skipping build history removal because this Buildx version does not support it."
  fi

  run "${docker_cmd[@]}" buildx prune --builder "$name" --all --force
done <<< "$builders"

if [[ "$system_prune" -eq 1 ]]; then
  echo
  echo "Running broader Docker cleanup..."
  run docker builder prune --all --force
  run docker system prune -a --force
fi

echo
echo "Docker build cleanup complete."
echo "If Docker Desktop still shows old builds, fully quit and reopen it to refresh the Builds view."
