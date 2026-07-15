#!/usr/bin/env bash
# Run the same build-gated CD workflow used for main pushes, but against the current local checkout.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ "$(git branch --show-current)" != "main" ]; then
  echo "local CD only runs from main" >&2
  exit 1
fi
if ! command -v act >/dev/null 2>&1; then
  echo "local CD requires nektos/act" >&2
  exit 1
fi

LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/osrsemu-local-cd.lock"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "another osrsemu local CD run is already active" >&2
  exit 0
fi

RSA_KEY="${OSRS_SERVER_RSA_PROPERTIES:-$ROOT/server-rsa.properties}"
if [ ! -f "$RSA_KEY" ]; then
  echo "RSA key not found: $RSA_KEY" >&2
  exit 1
fi
KEY_MODE="$(stat -c '%a' "$RSA_KEY")"
if (( (8#$KEY_MODE & 8#077) != 0 )); then
  echo "RSA key must be mode 600, found $KEY_MODE: $RSA_KEY" >&2
  exit 1
fi

EVENT_FILE="$(mktemp)"
trap 'rm -f "$EVENT_FILE"' EXIT
printf '{ "ref": "refs/heads/main" }\n' > "$EVENT_FILE"

act push -W .github/workflows/cd.yml -j deploy \
  --eventpath "$EVENT_FILE" \
  --env OSRS_ASSETS_ON_HOST=1 \
  --env OSRS_CACHE_DIR="${OSRS_CACHE_DIR:-$ROOT/cache-data}" \
  --env OSRS_SERVER_RSA_PROPERTIES="$RSA_KEY" \
  --pull=false
