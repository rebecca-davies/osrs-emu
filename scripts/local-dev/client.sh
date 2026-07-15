#!/usr/bin/env bash
set -euo pipefail
umask 077

source "$(dirname "${BASH_SOURCE[0]}")/environment.sh"
source "$LOCAL_DEV_DIRECTORY/processes.sh"
source "$LOCAL_DEV_DIRECTORY/stack.sh"

for command in flock grep ip nsenter python3 setpriv; do
  require_command "$command"
done
lock_stack -s
stack_running || fail "stack is stopped; run ./scripts/local-dev.sh start first"
[[ -e "$STATE_DIR/ready" ]] || fail "stack is still starting; see $STATE_DIR/supervisor.log"
validate_runtime_assets
netns_pid="$(read_pid "$STATE_DIR/netns.pid")" || fail "network namespace PID is unavailable"

exec {CLIENT_LOCK_FD}>"$STATE_DIR/client.lock"
require_positive_integer "Lock timeout" "$LOCK_TIMEOUT_SECONDS"
flock -w "$LOCK_TIMEOUT_SECONDS" -x "$CLIENT_LOCK_FD" ||
  fail "timed out allocating a local development client"
client_id=1
if [[ -r "$STATE_DIR/next-client" ]]; then
  client_id="$(<"$STATE_DIR/next-client")"
fi
[[ "$client_id" =~ ^[1-9][0-9]*$ ]] || client_id=1
printf '%s\n' "$((client_id + 1))" >"$STATE_DIR/next-client"
client_dir="$STATE_DIR/clients/$client_id"
mkdir -p "$client_dir/home"
rm -f "$client_dir/pid" "$client_dir/identity"
flock -u "$CLIENT_LOCK_FD"
exec {CLIENT_LOCK_FD}>&-

client_marker="-Duser.home=$client_dir/home"
cleanup_failed_client() {
  local status=$?
  trap - EXIT
  if (( status != 0 )) && [[ -e "$client_dir/pid" ]]; then
    if stop_recorded_process "$client_dir/pid" "$client_marker" "client $client_id"; then
      rm -f "$client_dir/pid" "$client_dir/identity"
    fi
  fi
  exit "$status"
}
trap cleanup_failed_client EXIT

display_value="${DISPLAY:-:0}"
xauthority_value="${XAUTHORITY:-${HOME:?}/.Xauthority}"
spawn_recorded_process \
  "$client_dir/pid" \
  "$client_marker" \
  "$client_dir/client.log" \
  nsenter \
  --target "$netns_pid" \
  --user \
  --net \
  --preserve-credentials \
  "$LOCAL_DEV_DIRECTORY/drop-client-privileges.sh" \
  "$LOCAL_DEV_DIRECTORY/run-client.sh" \
  "$STATE_DIR" \
  "$client_id" \
  "$ASSET_ROOT" \
  "$display_value" \
  "$xauthority_value" \
  "$CLIENT_JAVA" \
  "$HTTP_PORT"

wait_for_recorded_process "$client_dir/pid" "$client_marker" 50 ||
  fail "client $client_id exited; see $client_dir/client.log"
client_pid="$(read_pid "$client_dir/pid")" || fail "client $client_id published an invalid PID"
trap - EXIT
unlock_stack
echo "local-dev: client $client_id started (PID $client_pid)"
