#!/usr/bin/env bash
set -euo pipefail

COMMAND_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")/local-dev" && pwd)"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/local-dev.sh start    Build and start the isolated server plus client 1
  ./scripts/local-dev.sh client   Add another isolated RuneLite client
  ./scripts/local-dev.sh status   Show only processes owned by this development stack
  ./scripts/local-dev.sh stop     Stop this development stack and its clients
  ./scripts/local-dev.sh help     Show this help

RuneLite always runs with a per-client home inside a network namespace that has no
external default route. This launcher never searches for or stops unrelated clients.
EOF
}

case "${1:-help}" in
  start|client|status|stop) exec "$COMMAND_DIRECTORY/$1.sh" ;;
  help|-h|--help) usage ;;
  *) usage >&2; exit 64 ;;
esac
