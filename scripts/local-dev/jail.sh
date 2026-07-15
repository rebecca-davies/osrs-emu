#!/usr/bin/env bash
set -euo pipefail
umask 077

if (( $# != 6 )); then
  echo "usage: jail.sh STATE_DIR REPOSITORY_ROOT ASSET_ROOT DATABASE_PORT GATEWAY_PORT HTTP_PORT" >&2
  exit 64
fi

STATE_DIR="$1"
REPOSITORY_ROOT="$2"
ASSET_ROOT="$3"
DATABASE_PORT="$4"
GATEWAY_PORT="$5"
HTTP_PORT="$6"
LOCAL_DEV_DIRECTORY="$REPOSITORY_ROOT/scripts/local-dev"
PROCESS_IDENTITY="$LOCAL_DEV_DIRECTORY/process-identity.py"
source "$LOCAL_DEV_DIRECTORY/processes.sh"
cleanup_started=0
WATCH_PID=""

cleanup() {
  (( cleanup_started == 0 )) || return 0
  cleanup_started=1
  trap - EXIT INT TERM
  rm -f "$STATE_DIR/ready"
  if [[ -n "$WATCH_PID" ]]; then
    kill "$WATCH_PID" 2>/dev/null || true
    wait "$WATCH_PID" 2>/dev/null || true
    WATCH_PID=""
  fi
  stop_recorded_process "$STATE_DIR/server.pid" "emu.server.host.MainKt" "game server" &
  local server_stop=$!
  stop_recorded_process "$STATE_DIR/http.pid" "python3 -m http.server" "client configuration server" &
  local http_stop=$!
  wait "$server_stop" || true
  wait "$http_stop" || true
}

shutdown() {
  exit 0
}

trap cleanup EXIT
trap shutdown INT TERM

mkdir -p "$STATE_DIR"
ip link set lo up
if ip route show default | grep -q .; then
  echo "private namespace unexpectedly has a default route before setup" >&2
  exit 90
fi
if curl --max-time 2 --silent https://www.runescape.com >/dev/null 2>&1; then
  echo "Jagex was reachable before private database setup" >&2
  exit 91
fi
touch "$STATE_DIR/jail-ready"

for _ in $(seq 1 100); do
  [[ -e "$STATE_DIR/network-ready" ]] && break
  sleep 0.1
done
[[ -e "$STATE_DIR/network-ready" ]] || {
  echo "private database link was not configured" >&2
  exit 92
}

ip route del default 2>/dev/null || true
if ip route show default | grep -q .; then
  echo "private namespace retained an external default route" >&2
  exit 93
fi
if curl --max-time 2 --silent https://www.runescape.com >/dev/null 2>&1; then
  echo "Jagex was reachable after private database setup" >&2
  exit 94
fi

SERVER_JAVA_HOME="${OSRS_SERVER_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
SERVER_LAUNCHER="$REPOSITORY_ROOT/server/host/build/install/server-host/bin/server-host"
spawn_recorded_process \
  "$STATE_DIR/server.pid" \
  "emu.server.host.MainKt" \
  "$STATE_DIR/server.log" \
  env \
  JAVA_HOME="$SERVER_JAVA_HOME" \
  OSRS_CACHE_DIR="$ASSET_ROOT/cache-data" \
  OSRS_SERVER_RSA_PROPERTIES="$ASSET_ROOT/server-rsa.properties" \
  OSRS_GATEWAY_BIND_HOST="127.0.0.1" \
  OSRS_GATEWAY_PORT="$GATEWAY_PORT" \
  OSRS_DATABASE_URL="jdbc:postgresql://10.0.2.2:$DATABASE_PORT/osrsemu" \
  OSRS_DATABASE_USER="osrsemu" \
  OSRS_DATABASE_PASSWORD="osrsemu-dev" \
  "$SERVER_LAUNCHER"

spawn_recorded_process \
  "$STATE_DIR/http.pid" \
  "python3 -m http.server" \
  "$STATE_DIR/http.log" \
  python3 -m http.server "$HTTP_PORT" \
  --bind 127.0.0.1 \
  --directory "$ASSET_ROOT/client/patches"

for _ in $(seq 1 120); do
  if grep -q "server listening on .*:$GATEWAY_PORT" "$STATE_DIR/server.log" 2>/dev/null; then
    break
  fi
  if ! recorded_process_matches "$STATE_DIR/server.pid" "emu.server.host.MainKt"; then
    echo "server exited during startup; see $STATE_DIR/server.log" >&2
    exit 95
  fi
  sleep 0.5
done
grep -q "server listening on .*:$GATEWAY_PORT" "$STATE_DIR/server.log" 2>/dev/null || {
  echo "server did not start within 60 seconds; see $STATE_DIR/server.log" >&2
  exit 96
}

recorded_process_matches "$STATE_DIR/http.pid" "python3 -m http.server" || {
  echo "client configuration server exited during startup; see $STATE_DIR/http.log" >&2
  exit 97
}
curl --fail --silent --max-time 3 \
  "http://127.0.0.1:$HTTP_PORT/jav_config.local.ws" \
  >/dev/null
touch "$STATE_DIR/ready"

"$PROCESS_IDENTITY" wait-any \
  "$STATE_DIR/server.pid" \
  "$STATE_DIR/server.identity" \
  "emu.server.host.MainKt" \
  "$STATE_DIR/http.pid" \
  "$STATE_DIR/http.identity" \
  "python3 -m http.server" &
WATCH_PID=$!
wait "$WATCH_PID" || true
WATCH_PID=""
echo "a local development service exited; see logs under $STATE_DIR" >&2
exit 98
