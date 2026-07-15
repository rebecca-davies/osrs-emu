#!/usr/bin/env bash
# Deploy the osrsemu gateway to the local single host: (re)build its Docker image and
# (re)start ONLY the gateway container. Idempotent — safe to run repeatedly.
#
# This is the deploy primitive invoked by .github/workflows/cd.yml on merge to main. It
# is also runnable directly on the host (`./scripts/deploy.sh`).
#
# SAFETY (CLAUDE.md):
#   §12a — This deploys the SERVER only. It never launches the RuneLite client and never
#          reaches Jagex's network. There is deliberately no client step anywhere here.
#   §14  — The RSA key and cache dump are bind-mounted read-only from the host at runtime;
#          they are never copied into the image (.dockerignore enforces this).
#   Isolation — every docker action is scoped to the `osrsemu` Compose project, so the
#          unrelated stacks on this box (rotmgemu / uber_scraper / uber-tracker) are never
#          touched. Brings up postgres + gateway together (gateway depends_on postgres).
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
fi

log "project=$PROJECT"
log "cache   =$OSRS_CACHE_DIR (bind-mounted read-only)"
log "rsa key =$OSRS_SERVER_RSA_PROPERTIES (bind-mounted read-only)"
log "Building gateway image and (re)starting the container..."

# `up -d --build` rebuilds the image, then recreates the single `gateway` container only if
# its image/config changed. No client is ever started (there is no client service).
"${COMPOSE[@]}" -p "$PROJECT" -f "$COMPOSE_FILE" up -d --build

log "Deployed. Current state:"
"${COMPOSE[@]}" -p "$PROJECT" -f "$COMPOSE_FILE" ps

log "Logs:  ${COMPOSE[*]} -p $PROJECT -f $COMPOSE_FILE logs -f gateway"
log "Stop:  ${COMPOSE[*]} -p $PROJECT -f $COMPOSE_FILE down"
