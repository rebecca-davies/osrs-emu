#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

STATE_DIR="$(mktemp -d)"
FAKE_BIN="$STATE_DIR/bin"
DOCKER_LOG="$STATE_DIR/docker.log"
UNRELATED_PID=""
HELPER_PID_FILE="$STATE_DIR/helper.pid"
HELPER_IDENTITY_FILE="$STATE_DIR/helper.identity"
HELPER_VALID_IDENTITY="$STATE_DIR/helper.valid.identity"
SECOND_HELPER_PID_FILE="$STATE_DIR/helper-two.pid"
SECOND_HELPER_IDENTITY_FILE="$STATE_DIR/helper-two.identity"
LOCK_HOLDER_PID=""

cleanup() {
  if [[ -e "$HELPER_VALID_IDENTITY" ]]; then
    cp "$HELPER_VALID_IDENTITY" "$HELPER_IDENTITY_FILE"
    scripts/local-dev/process-identity.py stop \
      --marker=sleep "$HELPER_PID_FILE" "$HELPER_IDENTITY_FILE" "verification helper" \
      >/dev/null 2>&1 || true
  fi
  scripts/local-dev/process-identity.py stop \
    --marker=sleep "$SECOND_HELPER_PID_FILE" "$SECOND_HELPER_IDENTITY_FILE" "second verification helper" \
    >/dev/null 2>&1 || true
  if [[ -n "$UNRELATED_PID" ]]; then
    kill "$UNRELATED_PID" 2>/dev/null || true
  fi
  if [[ -n "$LOCK_HOLDER_PID" ]]; then
    kill "$LOCK_HOLDER_PID" 2>/dev/null || true
    wait "$LOCK_HOLDER_PID" 2>/dev/null || true
  fi
  rm -rf "$STATE_DIR"
}
trap cleanup EXIT

mkdir -p "$FAKE_BIN"
cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"${OSRS_TEST_DOCKER_LOG:?}"
[[ "${1:-}" == compose && "${2:-}" == version ]]
EOF
chmod +x "$FAKE_BIN/docker"

CONTENTION_STATE="$STATE_DIR/contention"
mkdir -p "$CONTENTION_STATE"
python3 - "$CONTENTION_STATE/stack.lock" "$CONTENTION_STATE/lock-ready" <<'PY' &
import fcntl
import pathlib
import sys
import time

with pathlib.Path(sys.argv[1]).open("w") as lock:
    fcntl.flock(lock, fcntl.LOCK_EX)
    pathlib.Path(sys.argv[2]).touch()
    time.sleep(30)
PY
LOCK_HOLDER_PID=$!
for _ in $(seq 1 50); do
  [[ -e "$CONTENTION_STATE/lock-ready" ]] && break
  sleep 0.1
done
[[ -e "$CONTENTION_STATE/lock-ready" ]]
set +e
LOCK_OUTPUT="$(timeout --foreground --kill-after=1s 3s \
  env OSRS_LOCAL_LOCK_TIMEOUT_SECONDS=1 OSRS_LOCAL_STATE_DIR="$CONTENTION_STATE" \
  ./scripts/local-dev.sh status 2>&1)"
LOCK_STATUS=$?
set -e
if (( LOCK_STATUS == 0 || LOCK_STATUS == 124 || LOCK_STATUS == 137 )); then
  echo "local development stack lock did not fail within its own deadline" >&2
  exit 1
fi
grep -q 'timed out acquiring' <<<"$LOCK_OUTPUT"
kill "$LOCK_HOLDER_PID"
wait "$LOCK_HOLDER_PID" 2>/dev/null || true
LOCK_HOLDER_PID=""

mkdir -p "$STATE_DIR/clients/1"
sleep 30 &
UNRELATED_PID=$!
printf '%s\n' "$UNRELATED_PID" >"$STATE_DIR/clients/1/pid"
if PATH="$FAKE_BIN:$PATH" \
  OSRS_TEST_DOCKER_LOG="$DOCKER_LOG" \
  OSRS_LOCAL_STATE_DIR="$STATE_DIR" \
  ./scripts/local-dev.sh stop >/dev/null 2>&1; then
  echo "local development stop must reject a PID with the wrong identity" >&2
  exit 1
fi
kill -0 "$UNRELATED_PID" 2>/dev/null || {
  echo "local development stop signalled an unrelated process" >&2
  exit 1
}
[[ ! -e "$DOCKER_LOG" ]] || {
  echo "a stop request from an unrelated state directory touched Docker Compose" >&2
  exit 1
}
kill "$UNRELATED_PID"
wait "$UNRELATED_PID" 2>/dev/null || true
UNRELATED_PID=""

scripts/local-dev/process-identity.py spawn \
  --marker=sleep \
  "$HELPER_PID_FILE" \
  "$HELPER_IDENTITY_FILE" \
  "$STATE_DIR/helper.log" \
  -- sleep 30
scripts/local-dev/process-identity.py matches \
  --marker=sleep "$HELPER_PID_FILE" "$HELPER_IDENTITY_FILE"
cp "$HELPER_IDENTITY_FILE" "$HELPER_VALID_IDENTITY"
python3 - "$HELPER_IDENTITY_FILE" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
identity = json.loads(path.read_text(encoding="utf-8"))
identity["start_time"] += 1
path.write_text(json.dumps(identity), encoding="utf-8")
PY
if scripts/local-dev/process-identity.py stop \
  --marker=sleep "$HELPER_PID_FILE" "$HELPER_IDENTITY_FILE" "verification helper" \
  >/dev/null 2>&1; then
  echo "process control accepted a stale process identity" >&2
  exit 1
fi
HELPER_PID="$(<"$HELPER_PID_FILE")"
kill -0 "$HELPER_PID" 2>/dev/null || {
  echo "process control signalled a process with a stale identity" >&2
  exit 1
}
cp "$HELPER_VALID_IDENTITY" "$HELPER_IDENTITY_FILE"
scripts/local-dev/process-identity.py spawn \
  --marker=sleep \
  "$SECOND_HELPER_PID_FILE" \
  "$SECOND_HELPER_IDENTITY_FILE" \
  "$STATE_DIR/helper-two.log" \
  -- sleep 30
scripts/local-dev/process-identity.py stop-many -- \
  "$HELPER_PID_FILE" "$HELPER_IDENTITY_FILE" sleep "verification helper" \
  "$SECOND_HELPER_PID_FILE" "$SECOND_HELPER_IDENTITY_FILE" sleep "second verification helper"
if scripts/local-dev/process-identity.py matches \
  --marker=sleep "$HELPER_PID_FILE" "$HELPER_IDENTITY_FILE"; then
  echo "process control did not stop its verified process" >&2
  exit 1
fi
if scripts/local-dev/process-identity.py matches \
  --marker=sleep "$SECOND_HELPER_PID_FILE" "$SECOND_HELPER_IDENTITY_FILE"; then
  echo "batched process control did not stop every verified process" >&2
  exit 1
fi
rm -f "$HELPER_VALID_IDENTITY"
