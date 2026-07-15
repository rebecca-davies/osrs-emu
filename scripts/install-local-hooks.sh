#!/usr/bin/env bash
# Opt this checkout into the tracked post-merge hook that launches local CD after main advances.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
git -C "$ROOT" config core.hooksPath .githooks
echo "installed osrsemu hooks from $ROOT/.githooks"
