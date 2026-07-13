#!/usr/bin/env bash
#
# screenshot-login.sh — login-state screenshot verification harness
#
# Launches an OSRS client (any command you pass), waits for it to render, then
# captures the current X display to a timestamped PNG under verify-shots/.
# This gives AFK visual proof that a client reached the login screen.
#
# It does NOT attempt a login and does NOT modify the client. The client command
# is fully parameterized so the SAME harness proves the pipeline with stock
# RuneLite today and screenshots a login against OUR gateway once the client is
# host-patched to 127.0.0.1.
#
# Usage:
#   screenshot-login.sh [-w SECONDS] [-k] [-o DIR] [-d DISPLAY] -- CLIENT CMD...
#   screenshot-login.sh [options]            # no command: screenshot-only
#
# Options:
#   -w SECONDS   how long to wait for the client to render (default: 25)
#   -k           kill the launched client after the screenshot (default: leave running)
#   -o DIR       output directory for shots (default: <script-dir>/verify-shots)
#   -d DISPLAY   X display to capture (default: current $DISPLAY, else :0)
#   -h           show this help
#
# Everything after `--` is the client command to launch (backgrounded). If no
# command is given, the harness just captures the current display — useful for
# smoke-testing the screenshot pipeline itself.
#
# Examples:
#   # Prove the screenshot pipeline against whatever is on screen right now:
#   ./screenshot-login.sh
#
#   # Stock RuneLite (flatpak) — proves a real client renders + is captured:
#   ./screenshot-login.sh -w 40 -- flatpak run --branch=stable --arch=x86_64 \
#       --command=runelite net.runelite.RuneLite
#
#   # Future: host-patched client pointed at our gateway (kill after shot):
#   ./screenshot-login.sh -w 40 -k -- java -jar /path/to/host-patched-client.jar

set -euo pipefail

WAIT=25
KILL_AFTER=0
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${SCRIPT_DIR}/verify-shots"
CAP_DISPLAY="${DISPLAY:-:0}"

usage() { sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while getopts ":w:ko:d:h" opt; do
  case "$opt" in
    w) WAIT="$OPTARG" ;;
    k) KILL_AFTER=1 ;;
    o) OUT_DIR="$OPTARG" ;;
    d) CAP_DISPLAY="$OPTARG" ;;
    h) usage; exit 0 ;;
    \?) echo "ERROR: unknown option -$OPTARG" >&2; exit 2 ;;
    :) echo "ERROR: option -$OPTARG requires an argument" >&2; exit 2 ;;
  esac
done
shift $((OPTIND - 1))

# Everything remaining is the client command.
CLIENT_CMD=("$@")

CLIENT_PID=""
cleanup() {
  # Only kill the client we launched, and only if requested (or on error).
  if [[ -n "$CLIENT_PID" ]] && kill -0 "$CLIENT_PID" 2>/dev/null; then
    if [[ "$KILL_AFTER" -eq 1 || -n "${_HARNESS_FAILED:-}" ]]; then
      echo "[harness] stopping client pid=$CLIENT_PID"
      kill "$CLIENT_PID" 2>/dev/null || true
      # give it a moment, then hard-kill any survivors in the tree
      sleep 2
      kill -9 "$CLIENT_PID" 2>/dev/null || true
    else
      echo "[harness] leaving client running (pid=$CLIENT_PID); stop it yourself when done"
    fi
  fi
}
trap '_HARNESS_FAILED=1; cleanup' ERR INT TERM
trap 'cleanup' EXIT

echo "[harness] display    : $CAP_DISPLAY"
echo "[harness] wait        : ${WAIT}s"
echo "[harness] kill after  : $([[ $KILL_AFTER -eq 1 ]] && echo yes || echo no)"
echo "[harness] output dir  : $OUT_DIR"

mkdir -p "$OUT_DIR"

# Pick a screenshot tool: prefer scrot, fall back to maim, then imagemagick.
SHOOTER=""
for cand in scrot maim import; do
  if command -v "$cand" >/dev/null 2>&1; then SHOOTER="$cand"; break; fi
done
if [[ -z "$SHOOTER" ]]; then
  echo "ERROR: no screenshot tool found (need scrot, maim, or import)" >&2
  exit 3
fi
echo "[harness] shooter     : $SHOOTER"

if [[ "${#CLIENT_CMD[@]}" -gt 0 ]]; then
  echo "[harness] launching   : ${CLIENT_CMD[*]}"
  # Launch on the target display, backgrounded.
  DISPLAY="$CAP_DISPLAY" "${CLIENT_CMD[@]}" &
  CLIENT_PID=$!
  echo "[harness] client pid  : $CLIENT_PID"

  # Poll during the wait so we notice an early crash instead of blindly sleeping.
  for ((i = 0; i < WAIT; i++)); do
    if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
      echo "ERROR: client exited before render (after ${i}s). Check its stderr above." >&2
      CLIENT_PID=""   # nothing left to clean up
      exit 4
    fi
    sleep 1
  done
else
  echo "[harness] no client command given — capturing current display only"
fi

TS="$(date +%Y%m%d-%H%M%S)"
SHOT="${OUT_DIR}/login-${TS}.png"

echo "[harness] capturing   : $SHOT"
case "$SHOOTER" in
  scrot)  DISPLAY="$CAP_DISPLAY" scrot "$SHOT" ;;
  maim)   DISPLAY="$CAP_DISPLAY" maim "$SHOT" ;;
  import) DISPLAY="$CAP_DISPLAY" import -window root "$SHOT" ;;
esac

if [[ ! -s "$SHOT" ]]; then
  echo "ERROR: screenshot was not written or is empty: $SHOT" >&2
  exit 5
fi

# Report dimensions if imagemagick's identify is around.
if command -v identify >/dev/null 2>&1; then
  DIMS="$(identify -format '%wx%h' "$SHOT" 2>/dev/null || echo '?')"
  echo "[harness] captured OK : $SHOT ($DIMS)"
else
  echo "[harness] captured OK : $SHOT ($(stat -c%s "$SHOT") bytes)"
fi

echo "[harness] done"
