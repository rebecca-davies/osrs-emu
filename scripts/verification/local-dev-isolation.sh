#!/usr/bin/env bash
# Host-only integration check: nested network namespaces are unavailable inside act containers.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

for command in grep ip setpriv unshare; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "local client isolation verification requires $command" >&2
    exit 1
  }
done

unshare -rn scripts/local-dev/drop-client-privileges.sh bash -c '
  set -euo pipefail
  [[ "$(awk "/^CapEff:/ { print \$2 }" /proc/self/status)" == 0000000000000000 ]]
  [[ "$(awk "/^CapBnd:/ { print \$2 }" /proc/self/status)" == 0000000000000000 ]]
  [[ "$(awk "/^NoNewPrivs:/ { print \$2 }" /proc/self/status)" == 1 ]]
  [[ -z "$(ip route show default)" ]]
  if ip link add forbidden-link type dummy >/dev/null 2>&1; then
    echo "capability-free client created a network link" >&2
    exit 1
  fi
  if ip route add default dev lo >/dev/null 2>&1; then
    echo "capability-free client restored a default route" >&2
    exit 1
  fi
'
