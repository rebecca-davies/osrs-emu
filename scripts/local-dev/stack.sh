#!/usr/bin/env bash

stack_running() {
  recorded_process_matches "$STATE_DIR/netns.pid" "scripts/local-dev/jail.sh"
}

runtime_state_exists() {
  local file
  for file in database-owned http.pid netns.pid server.pid slirp.pid; do
    [[ -e "$STATE_DIR/$file" ]] && return 0
  done
  if [[ -d "$STATE_DIR/clients" ]]; then
    find "$STATE_DIR/clients" -mindepth 2 -maxdepth 2 -name pid -print -quit | grep -q .
    return
  fi
  return 1
}

wait_for_file() {
  local file="$1"
  local attempts="$2"
  for _ in $(seq 1 "$attempts"); do
    [[ -e "$file" ]] && return 0
    sleep 0.1
  done
  return 1
}

wait_for_log() {
  local file="$1"
  local expected="$2"
  local attempts="$3"
  for _ in $(seq 1 "$attempts"); do
    grep -q "$expected" "$file" 2>/dev/null && return 0
    sleep 0.1
  done
  return 1
}

lock_stack() {
  local mode="$1"
  [[ "${OSRS_LOCAL_STACK_LOCK_HELD:-0}" == 1 ]] && return 0
  mkdir -p "$STATE_DIR"
  chmod 700 "$STATE_DIR"
  exec {STACK_LOCK_FD}>"$STATE_DIR/stack.lock"
  chmod 600 "$STATE_DIR/stack.lock"
  require_positive_integer "Lock timeout" "$LOCK_TIMEOUT_SECONDS"
  flock -w "$LOCK_TIMEOUT_SECONDS" "$mode" "$STACK_LOCK_FD" ||
    fail "timed out acquiring the local development stack lock"
  export OSRS_LOCAL_STACK_LOCK_HELD=1
}

unlock_stack() {
  [[ "${OSRS_LOCAL_STACK_LOCK_HELD:-0}" == 1 ]] || return 0
  if [[ -n "${STACK_LOCK_FD:-}" ]]; then
    flock -u "$STACK_LOCK_FD"
    exec {STACK_LOCK_FD}>&-
  fi
  unset OSRS_LOCAL_STACK_LOCK_HELD
}

reset_runtime_files() {
  rm -f \
    "$STATE_DIR/http.identity" \
    "$STATE_DIR/http.pid" \
    "$STATE_DIR/jail-ready" \
    "$STATE_DIR/netns.identity" \
    "$STATE_DIR/netns.pid" \
    "$STATE_DIR/network-ready" \
    "$STATE_DIR/ready" \
    "$STATE_DIR/server.identity" \
    "$STATE_DIR/server.pid" \
    "$STATE_DIR/slirp.identity" \
    "$STATE_DIR/slirp.pid"
  if [[ -d "$STATE_DIR/clients" ]]; then
    find "$STATE_DIR/clients" -mindepth 2 -maxdepth 2 \
      \( -name pid -o -name identity \) -delete
  fi
  printf '1\n' >"$STATE_DIR/next-client"
}
