#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if timeout --foreground --kill-after=2s 5s docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "local development verification requires Docker Compose" >&2
  exit 1
fi

bash -n scripts/local-dev.sh scripts/local-dev/*.sh
CONFIG="$(timeout --foreground --kill-after=5s 30s \
  "${COMPOSE[@]}" -f compose.local-dev.yaml config --format json)"
python3 - "$CONFIG" <<'PY'
import json
import sys

services = json.loads(sys.argv[1])["services"]
assert set(services) == {"postgres"}, f"local development Compose must own only PostgreSQL: {services}"
ports = services["postgres"].get("ports", [])
assert len(ports) == 1, f"expected one local development PostgreSQL port, found {ports!r}"
port = ports[0]
assert port.get("host_ip") == "127.0.0.1", f"local development PostgreSQL must bind loopback: {port!r}"
assert port.get("published") == "54331", f"unexpected local development PostgreSQL port: {port!r}"
assert port.get("target") == 5432, f"unexpected PostgreSQL container port: {port!r}"
PY

STATE_DIR="$(mktemp -d)"
trap 'rm -rf "$STATE_DIR"' EXIT

HELP="$(./scripts/local-dev.sh help)"
grep -q 'local-dev.sh start' <<<"$HELP"
grep -q 'local-dev.sh client' <<<"$HELP"
grep -q 'local-dev.sh status' <<<"$HELP"
grep -q 'local-dev.sh stop' <<<"$HELP"

STATUS_STATE="$STATE_DIR/status-state"
STATUS="$(OSRS_LOCAL_STATE_DIR="$STATUS_STATE" ./scripts/local-dev.sh status)"
grep -q 'stopped' <<<"$STATUS"
[[ "$(stat -c '%a' "$STATUS_STATE")" == 700 ]]
[[ "$(stat -c '%a' "$STATUS_STATE/stack.lock")" == 600 ]]

if OSRS_LOCAL_STATE_DIR="$STATE_DIR/stopped" ./scripts/local-dev.sh client >/dev/null 2>&1; then
  echo "local development client command must reject a stopped stack" >&2
  exit 1
fi

RSA_TEST_ROOT="$STATE_DIR/rsa-assets"
mkdir -p \
  "$RSA_TEST_ROOT/cache-data" \
  "$RSA_TEST_ROOT/client/patches" \
  "$RSA_TEST_ROOT/client/runelite/runelite-client/build/libs"
install -m 0600 /dev/null "$RSA_TEST_ROOT/server-rsa.properties"
install -m 0644 /dev/null "$RSA_TEST_ROOT/client/patches/injected-client-patched.jar"
install -m 0644 /dev/null \
  "$RSA_TEST_ROOT/client/runelite/runelite-client/build/libs/client-1.12.33-SNAPSHOT-shaded.jar"
OSRS_CLIENT_JAVA=/bin/true OSRS_LOCAL_ASSET_ROOT="$RSA_TEST_ROOT" bash -c \
  'source scripts/local-dev/environment.sh; validate_runtime_assets'
chmod 0644 "$RSA_TEST_ROOT/server-rsa.properties"
if OSRS_CLIENT_JAVA=/bin/true OSRS_LOCAL_ASSET_ROOT="$RSA_TEST_ROOT" bash -c \
  'source scripts/local-dev/environment.sh; validate_runtime_assets' \
  >/dev/null 2>&1; then
  echo "local development accepted an RSA private key with group/world access" >&2
  exit 1
fi

grep -q 'unshare -rn' scripts/local-dev/start.sh
grep -q -- '--disable-dns' scripts/local-dev/start.sh
grep -q '^GATEWAY_PORT=43594$' scripts/local-dev/environment.sh
grep -q 'ip route del default' scripts/local-dev/jail.sh
grep -q -- '-Duser.home=' scripts/local-dev/run-client.sh
grep -q 'drop-client-privileges.sh' scripts/local-dev/client.sh
grep -q 'wait-any' scripts/local-dev/jail.sh
grep -q 'flock' scripts/local-dev/client.sh
grep -q 'flock -w.*CLIENT_LOCK_FD' scripts/local-dev/client.sh
grep -q 'flock -w.*STACK_LOCK_FD' scripts/local-dev/stack.sh
grep -q 'umask 077' scripts/local-dev/status.sh
grep -q 'chmod 700.*STATE_DIR' scripts/local-dev/stack.sh
grep -q 'chmod 600.*stack.lock' scripts/local-dev/stack.sh
grep -q 'rollback' scripts/local-dev/start.sh
grep -q 'database-owned' scripts/local-dev/start.sh
grep -q 'database-owned' scripts/local-dev/stop.sh
grep -q -- '--kill-after=' scripts/local-dev/environment.sh
grep -q -- '--kill-after=' scripts/local-dev/start.sh
grep -q 'gradlew.*--no-daemon' scripts/local-dev/start.sh
if grep -q -- '--fork' scripts/local-dev/client.sh; then
  echo "client launch must retain the PID of the process it starts" >&2
  exit 1
fi
grep -q 'pidfd_open' scripts/local-dev/process-identity.py
grep -q 'pidfd_send_signal' scripts/local-dev/process-identity.py
python3 <<'PY'
from pathlib import Path

start = Path("scripts/local-dev/start.sh").read_text(encoding="utf-8")
client = start.rindex('"$LOCAL_DEV_DIRECTORY/client.sh"')
commit = start.rindex("trap - EXIT")
unlock = start.rindex("unlock_stack")
assert client < commit < unlock, "start must retain its generation lock through first-client success"
PY
if grep -Eq '\b(pkill|killall)\b' scripts/local-dev.sh scripts/local-dev/*.sh; then
  echo "local development scripts must stop only their recorded, identity-checked PIDs" >&2
  exit 1
fi
