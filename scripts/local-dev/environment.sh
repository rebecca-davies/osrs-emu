#!/usr/bin/env bash

LOCAL_DEV_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$LOCAL_DEV_DIRECTORY/../.." && pwd)"
ASSET_ROOT="${OSRS_LOCAL_ASSET_ROOT:-$REPOSITORY_ROOT}"
STATE_DIR="${OSRS_LOCAL_STATE_DIR:-${XDG_RUNTIME_DIR:-/tmp}/osrsemu-local-dev-${UID}}"
COMPOSE_FILE="$REPOSITORY_ROOT/compose.local-dev.yaml"
COMPOSE_PROJECT="osrsemu-local-dev"
DATABASE_PORT="${OSRS_LOCAL_DATABASE_PORT:-54331}"
GATEWAY_PORT=43594
HTTP_PORT="${OSRS_LOCAL_HTTP_PORT:-8080}"
CLIENT_JAVA="${OSRS_CLIENT_JAVA:-/usr/lib/jvm/java-11-openjdk-amd64/bin/java}"
DOCKER_TIMEOUT_SECONDS="${OSRS_LOCAL_DOCKER_TIMEOUT_SECONDS:-120}"
DOCKER_STOP_TIMEOUT_SECONDS="${OSRS_LOCAL_DOCKER_STOP_TIMEOUT_SECONDS:-30}"
PROCESS_IDENTITY="$LOCAL_DEV_DIRECTORY/process-identity.py"
LOCK_TIMEOUT_SECONDS="${OSRS_LOCAL_LOCK_TIMEOUT_SECONDS:-10}"
BUILD_TIMEOUT_SECONDS="${OSRS_LOCAL_BUILD_TIMEOUT_SECONDS:-300}"
export OSRS_LOCAL_DATABASE_PORT="$DATABASE_PORT"

fail() {
  echo "local-dev: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

require_positive_integer() {
  [[ "$2" =~ ^[1-9][0-9]*$ ]] || fail "$1 must be a positive integer: $2"
}

compose_with_timeout() {
  local timeout_seconds="$1"
  shift
  require_positive_integer "Docker timeout" "$timeout_seconds"
  require_command timeout
  if timeout --foreground --kill-after=2s 5s docker compose version >/dev/null 2>&1; then
    timeout --foreground --kill-after=5s "${timeout_seconds}s" \
      docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    timeout --foreground --kill-after=5s "${timeout_seconds}s" \
      docker-compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" "$@"
  else
    fail "Docker Compose is required"
  fi
}

compose() {
  compose_with_timeout "$DOCKER_TIMEOUT_SECONDS" "$@"
}

validate_runtime_assets() {
  require_command stat
  [[ -d "$ASSET_ROOT/cache-data" ]] || fail "cache-data is missing beneath $ASSET_ROOT"
  [[ -r "$ASSET_ROOT/server-rsa.properties" ]] || fail "server-rsa.properties is missing or unreadable beneath $ASSET_ROOT"
  [[ "$(stat -c '%a' "$ASSET_ROOT/server-rsa.properties")" == 600 ]] ||
    fail "server-rsa.properties must have mode 0600 beneath $ASSET_ROOT"
  [[ -r "$ASSET_ROOT/client/patches/injected-client-patched.jar" ]] || fail "patched client jar is missing beneath $ASSET_ROOT"
  [[ -r "$ASSET_ROOT/client/runelite/runelite-client/build/libs/client-1.12.33-SNAPSHOT-shaded.jar" ]] ||
    fail "RuneLite shaded jar is missing beneath $ASSET_ROOT"
  [[ -x "$CLIENT_JAVA" ]] || fail "Java 11 client runtime is unavailable: $CLIENT_JAVA"
}
