#!/usr/bin/env bash
# Deploy the osrsemu server to the local single host: (re)build its Docker image and
# (re)start ONLY the server container. Idempotent — safe to run repeatedly.
#
# This is the deploy primitive invoked by .github/workflows/cd.yml after a push to main and by
# scripts/run-local-cd.sh after an opted-in local merge. It is also runnable directly.
#
# SAFETY (CLAUDE.md):
#   §12a — This deploys the SERVER only. It never launches the RuneLite client and never
#          reaches Jagex's network. There is deliberately no client step anywhere here.
#   §14  — The RSA key and cache dump are bind-mounted read-only from the host at runtime;
#          they are never copied into the image (.dockerignore enforces this).
#   Isolation — every docker action is scoped to the `osrsemu` Compose project, so the
#          unrelated stacks on this box (rotmgemu / uber_scraper / uber-tracker) are never
#          touched. Brings up postgres + server together (server depends_on postgres).
set -euo pipefail

PROJECT="osrsemu"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/compose.yaml"

# Runtime assets. Defaults suit a direct host run from a checkout that has them. When this
# runs inside an act container (docker-out-of-docker), these MUST be absolute HOST paths —
# the host Docker daemon, not this process, resolves the bind-mount sources.
export OSRS_CACHE_DIR="${OSRS_CACHE_DIR:-$REPO_ROOT/cache-data}"
export OSRS_SERVER_RSA_PROPERTIES="${OSRS_SERVER_RSA_PROPERTIES:-$REPO_ROOT/server-rsa.properties}"
export OSRS_IMAGE_TAG="${OSRS_IMAGE_TAG:-$(git -C "$REPO_ROOT" rev-parse --short=12 HEAD 2>/dev/null || printf 'dev')}"
export OSRS_RUNTIME_UID="${OSRS_RUNTIME_UID:-$(stat -c '%u' "$OSRS_SERVER_RSA_PROPERTIES" 2>/dev/null || printf '1000')}"
export OSRS_RUNTIME_GID="${OSRS_RUNTIME_GID:-$(stat -c '%g' "$OSRS_SERVER_RSA_PROPERTIES" 2>/dev/null || printf '1000')}"
DEPLOY_HEALTH_TIMEOUT_SECONDS="${DEPLOY_HEALTH_TIMEOUT_SECONDS:-90}"

log() { printf '[deploy] %s\n' "$*"; }

# Pick Compose v2 (`docker compose`) if present, else the legacy `docker-compose`.
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  log "ERROR: neither 'docker compose' nor 'docker-compose' is available." >&2
  exit 1
fi

# Validate the runtime assets exist. Skippable for the docker-out-of-docker case where the
# host paths are not visible to this process (set OSRS_ASSETS_ON_HOST=1 to trust the caller).
if [ "${OSRS_ASSETS_ON_HOST:-0}" = "1" ]; then
  log "OSRS_ASSETS_ON_HOST=1 — trusting caller-supplied host paths without a local existence check."
else
  if [ ! -d "$OSRS_CACHE_DIR" ]; then
    log "ERROR: cache dir not found: $OSRS_CACHE_DIR" >&2
    log "       Set OSRS_CACHE_DIR to the host cache-data path (see CI.md)." >&2
    exit 1
  fi
  if [ ! -f "$OSRS_SERVER_RSA_PROPERTIES" ]; then
    log "ERROR: RSA key file not found: $OSRS_SERVER_RSA_PROPERTIES" >&2
    log "       Generate it with: ./gradlew :tools:client-patch:run  (see CI.md)." >&2
    exit 1
  fi
  KEY_MODE="$(stat -c '%a' "$OSRS_SERVER_RSA_PROPERTIES")"
  if (( (8#$KEY_MODE & 8#077) != 0 )); then
    log "ERROR: RSA key must not be group/world accessible: $OSRS_SERVER_RSA_PROPERTIES (mode $KEY_MODE)" >&2
    log "       Fix with: chmod 600 '$OSRS_SERVER_RSA_PROPERTIES'" >&2
    exit 1
  fi
fi

log "project=$PROJECT"
log "image   =osrsemu-server:$OSRS_IMAGE_TAG"
log "user    =$OSRS_RUNTIME_UID:$OSRS_RUNTIME_GID (RSA asset owner)"
log "cache   =$OSRS_CACHE_DIR (bind-mounted read-only)"
log "rsa key =$OSRS_SERVER_RSA_PROPERTIES (bind-mounted read-only)"
log "Building server image and (re)starting the container..."

# Preserve the currently deployed immutable image as an explicit rollback target before Compose
# replaces the container. The tag is local to this host and never contains secrets or runtime data.
if PREVIOUS_IMAGE_ID="$(docker inspect --format '{{.Image}}' osrsemu-server 2>/dev/null)"; then
  docker image tag "$PREVIOUS_IMAGE_ID" osrsemu-server:rollback
  log "preserved previous image as osrsemu-server:rollback"
fi

# `up -d --build` rebuilds the image, then recreates the single `server` container only if
# its image/config changed. No client is ever started (there is no client service).
"${COMPOSE[@]}" -p "$PROJECT" -f "$COMPOSE_FILE" up -d --build

SERVER_CONTAINER_ID="$("${COMPOSE[@]}" -p "$PROJECT" -f "$COMPOSE_FILE" ps -q server)"
if [ -z "$SERVER_CONTAINER_ID" ]; then
  log "ERROR: Compose did not create the server container." >&2
  exit 1
fi

log "Waiting up to ${DEPLOY_HEALTH_TIMEOUT_SECONDS}s for server health..."
deadline=$((SECONDS + DEPLOY_HEALTH_TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$SERVER_CONTAINER_ID")"
  case "$status" in
    healthy)
      log "server is healthy"
      break
      ;;
    unhealthy|exited|dead)
      log "ERROR: server entered terminal state: $status" >&2
      "${COMPOSE[@]}" -p "$PROJECT" -f "$COMPOSE_FILE" logs --tail=200 server >&2
      exit 1
      ;;
  esac
  sleep 1
done

if [ "${status:-unknown}" != "healthy" ]; then
  log "ERROR: server did not become healthy within ${DEPLOY_HEALTH_TIMEOUT_SECONDS}s" >&2
  "${COMPOSE[@]}" -p "$PROJECT" -f "$COMPOSE_FILE" logs --tail=200 server >&2
  exit 1
fi

log "Deployed. Current state:"
"${COMPOSE[@]}" -p "$PROJECT" -f "$COMPOSE_FILE" ps

log "Logs:  ${COMPOSE[*]} -p $PROJECT -f $COMPOSE_FILE logs -f server"
log "Stop:  ${COMPOSE[*]} -p $PROJECT -f $COMPOSE_FILE down"
