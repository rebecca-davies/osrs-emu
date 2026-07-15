#!/usr/bin/env bash
set -euo pipefail
umask 077

source "$(dirname "${BASH_SOURCE[0]}")/environment.sh"
source "$LOCAL_DEV_DIRECTORY/processes.sh"
source "$LOCAL_DEV_DIRECTORY/stack.sh"

require_command flock
require_command python3
lock_stack -x
failed=0
processes=()

if [[ -d "$STATE_DIR/clients" ]]; then
  for client_dir in "$STATE_DIR"/clients/*; do
    [[ -d "$client_dir" ]] || continue
    processes+=(
      "$client_dir/pid"
      "-Duser.home=$client_dir/home"
      "client $(basename "$client_dir")"
    )
  done
fi
processes+=(
  "$STATE_DIR/server.pid" "emu.server.host.MainKt" "game server"
  "$STATE_DIR/http.pid" "python3 -m http.server" "client configuration server"
)
stop_recorded_processes "${processes[@]}" || failed=1

stop_recorded_processes \
  "$STATE_DIR/netns.pid" "scripts/local-dev/jail.sh" "private server namespace" \
  "$STATE_DIR/slirp.pid" "slirp4netns" "private database link" || failed=1

if [[ -e "$STATE_DIR/database-owned" ]]; then
  if [[ "$(<"$STATE_DIR/database-owned")" != "$COMPOSE_PROJECT" ]]; then
    echo "local-dev: refusing to stop a database with an invalid ownership record" >&2
    failed=1
  elif ! command -v docker >/dev/null 2>&1; then
    echo "local-dev: Docker is unavailable; the owned database was not stopped" >&2
    failed=1
  elif compose_with_timeout "$DOCKER_STOP_TIMEOUT_SECONDS" down --timeout 10; then
    rm -f "$STATE_DIR/database-owned"
  else
    echo "local-dev: timed out or failed while stopping the owned database" >&2
    failed=1
  fi
fi

if (( failed == 0 )); then
  reset_runtime_files
  echo "local-dev: stopped"
else
  fail "one or more owned resources could not be stopped; ownership records were preserved"
fi
