#!/usr/bin/env bash

identity_file_for() {
  printf '%s.identity\n' "${1%.pid}"
}

read_pid() {
  local file="$1"
  [[ -r "$file" ]] || return 1
  local pid
  pid="$(<"$file")"
  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
  printf '%s\n' "$pid"
}

spawn_recorded_process() {
  local pid_file="$1"
  local marker="$2"
  local output="$3"
  shift 3
  "$PROCESS_IDENTITY" spawn \
    --marker="$marker" \
    "$pid_file" \
    "$(identity_file_for "$pid_file")" \
    "$output" \
    -- \
    "$@"
}

recorded_process_matches() {
  local pid_file="$1"
  local marker="$2"
  "$PROCESS_IDENTITY" matches \
    --marker="$marker" \
    "$pid_file" \
    "$(identity_file_for "$pid_file")" \
    >/dev/null 2>&1
}

wait_for_recorded_process() {
  local pid_file="$1"
  local marker="$2"
  local attempts="$3"
  for _ in $(seq 1 "$attempts"); do
    recorded_process_matches "$pid_file" "$marker" && return 0
    sleep 0.1
  done
  return 1
}

stop_recorded_process() {
  local pid_file="$1"
  local marker="$2"
  local label="$3"
  "$PROCESS_IDENTITY" stop \
    --marker="$marker" \
    "$pid_file" \
    "$(identity_file_for "$pid_file")" \
    "$label"
}

stop_recorded_processes() {
  local records=()
  while (( $# > 0 )); do
    local pid_file="$1"
    local marker="$2"
    local label="$3"
    records+=("$pid_file" "$(identity_file_for "$pid_file")" "$marker" "$label")
    shift 3
  done
  "$PROCESS_IDENTITY" stop-many -- "${records[@]}"
}
