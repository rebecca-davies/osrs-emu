#!/usr/bin/env bash
set -euo pipefail

exec setpriv \
  --bounding-set=-all \
  --inh-caps=-all \
  --ambient-caps=-all \
  --securebits=+noroot,+noroot_locked,+no_setuid_fixup,+no_setuid_fixup_locked \
  --no-new-privs \
  "$@"
