#!/usr/bin/env bash
set -euo pipefail
umask 077

source "$(dirname "${BASH_SOURCE[0]}")/environment.sh"
source "$LOCAL_DEV_DIRECTORY/processes.sh"
source "$LOCAL_DEV_DIRECTORY/stack.sh"

start_database() {
  printf '%s\n' "$COMPOSE_PROJECT" >"$STATE_DIR/database-owned"
  compose up -d
  local container
  container="$(compose ps -q postgres)"
  [[ -n "$container" ]] || fail "local development PostgreSQL was not created"

  local deadline=$((SECONDS + 60))
  while (( SECONDS < deadline )); do
    local health="unavailable"
    health="$(timeout --foreground --kill-after=2s 5s docker inspect \
      --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "$container")" || true
    case "$health" in
      healthy) return 0 ;;
      unhealthy|exited|dead) fail "local development PostgreSQL entered state: $health" ;;
    esac
    sleep 1
  done
  fail "local development PostgreSQL did not become healthy"
}

rollback() {
  local status=$?
  trap - EXIT INT TERM
  if (( status != 0 )); then
    echo "local-dev: startup failed; rolling back owned processes and services" >&2
    "$LOCAL_DEV_DIRECTORY/stop.sh" >&2 || true
  fi
  exit "$status"
}

build_pid=""

cancel_startup() {
  local status="$1"
  trap - INT TERM
  if [[ -n "$build_pid" ]]; then
    kill -KILL -- "-$build_pid" "$build_pid" 2>/dev/null || true
    wait "$build_pid" 2>/dev/null || true
    build_pid=""
  fi
  exit "$status"
}

for command in curl docker flock ip python3 seq slirp4netns stat timeout unshare; do
  require_command "$command"
done
validate_runtime_assets
mkdir -p "$STATE_DIR/clients"
chmod 700 "$STATE_DIR" "$STATE_DIR/clients"
lock_stack -x

if stack_running; then
  echo "local-dev: stack is already running"
  exec "$LOCAL_DEV_DIRECTORY/status.sh"
fi
if runtime_state_exists; then
  echo "local-dev: cleaning an incomplete prior launch"
  "$LOCAL_DEV_DIRECTORY/stop.sh"
fi
reset_runtime_files
trap rollback EXIT
trap 'cancel_startup 130' INT
trap 'cancel_startup 143' TERM

require_positive_integer "Build timeout" "$BUILD_TIMEOUT_SECONDS"
# Keep Gradle in a scoped process group without letting it consume launcher input.
timeout --kill-after=10s "${BUILD_TIMEOUT_SECONDS}s" \
  "$REPOSITORY_ROOT/gradlew" --no-daemon --console=plain :server-host:installDist </dev/null &
build_pid=$!
if wait "$build_pid"; then
  build_pid=""
else
  build_status=$?
  build_pid=""
  exit "$build_status"
fi
start_database

spawn_recorded_process \
  "$STATE_DIR/netns.pid" \
  "scripts/local-dev/jail.sh" \
  "$STATE_DIR/supervisor.log" \
  unshare -rn \
  "$LOCAL_DEV_DIRECTORY/jail.sh" \
  "$STATE_DIR" \
  "$REPOSITORY_ROOT" \
  "$ASSET_ROOT" \
  "$DATABASE_PORT" \
  "$GATEWAY_PORT" \
  "$HTTP_PORT"

wait_for_file "$STATE_DIR/jail-ready" 100 ||
  fail "private namespace did not start; see $STATE_DIR/supervisor.log"
wait_for_recorded_process "$STATE_DIR/netns.pid" "scripts/local-dev/jail.sh" 20 ||
  fail "private namespace lost its recorded process identity"
netns_pid="$(read_pid "$STATE_DIR/netns.pid")" || fail "private namespace PID is unavailable"

spawn_recorded_process \
  "$STATE_DIR/slirp.pid" \
  "slirp4netns" \
  "$STATE_DIR/slirp.log" \
  slirp4netns \
  --configure \
  --disable-dns \
  --enable-sandbox \
  --enable-seccomp \
  "$netns_pid" \
  tap0

wait_for_log "$STATE_DIR/slirp.log" "received tapfd" 100 ||
  fail "private database link did not start; see $STATE_DIR/slirp.log"
wait_for_recorded_process "$STATE_DIR/slirp.pid" "slirp4netns" 20 ||
  fail "private database link lost its recorded process identity"
touch "$STATE_DIR/network-ready"

wait_for_file "$STATE_DIR/ready" 700 ||
  fail "server did not become ready; see $STATE_DIR/supervisor.log and $STATE_DIR/server.log"

echo "local-dev: isolated server is ready on 127.0.0.1:$GATEWAY_PORT"
"$LOCAL_DEV_DIRECTORY/client.sh"
trap - EXIT INT TERM
unlock_stack
