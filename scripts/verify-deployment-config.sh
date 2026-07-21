#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFICATION_DIRECTORY="$ROOT/scripts/verification"

bash -n "$0" "$VERIFICATION_DIRECTORY"/*.sh
"$VERIFICATION_DIRECTORY/production-deployment.sh"
"$VERIFICATION_DIRECTORY/local-dev-configuration.sh"
"$VERIFICATION_DIRECTORY/local-dev-processes.sh"

echo "deployment configuration verified"
