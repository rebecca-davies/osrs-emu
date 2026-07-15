#!/usr/bin/env bash
set -euo pipefail
umask 077

source "$(dirname "${BASH_SOURCE[0]}")/environment.sh"
source "$LOCAL_DEV_DIRECTORY/processes.sh"
source "$LOCAL_DEV_DIRECTORY/stack.sh"

require_command flock
require_command python3
lock_stack -s
if ! stack_running; then
  if runtime_state_exists; then
    echo "local-dev: incomplete state (run ./scripts/local-dev.sh stop)"
  else
    echo "local-dev: stopped"
  fi
  exit 0
fi

netns_pid="$(read_pid "$STATE_DIR/netns.pid")"
echo "local-dev: running (network namespace PID $netns_pid)"
if server_pid="$(read_pid "$STATE_DIR/server.pid")" &&
  recorded_process_matches "$STATE_DIR/server.pid" "emu.server.host.MainKt"; then
  echo "  server:  PID $server_pid"
else
  echo "  server:  unavailable"
fi
if [[ -d "$STATE_DIR/clients" ]]; then
  for client_dir in "$STATE_DIR"/clients/*; do
    [[ -d "$client_dir" ]] || continue
    if client_pid="$(read_pid "$client_dir/pid")" &&
      recorded_process_matches "$client_dir/pid" "-Duser.home=$client_dir/home"; then
      echo "  client $(basename "$client_dir"): PID $client_pid"
    fi
  done
fi
echo "  logs:    $STATE_DIR"
