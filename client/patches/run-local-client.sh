#!/usr/bin/env bash
#
# run-local-client.sh — point a RuneLite injected-client at OUR local gateway.
#
# This needs NO source patch to RuneLite. RuneLite's injected client derives the JS5/game-server
# host from the `codebase` URL in the jav_config it fetches, and RuneLite accepts a `--jav_config`
# URL on the command line. So we:
#   1. serve a jav_config whose codebase points at 127.0.0.1 (jav_config.local.ws, next to this
#      script) over HTTP — okhttp only accepts http/https, not file://;
#   2. launch RuneLite with --jav_config pointing at it.
# The client then JS5-connects to 127.0.0.1:43594 (our gateway) instead of Jagex.
#
# Prereqs:
#   - Our gateway running:  (cd <repo> && ./gradlew :gateway:run)   # listens on 43594
#   - The RuneLite shaded jar built:
#       (cd client/runelite && ./gradlew :client:shadowJar)
#       -> client/runelite/runelite-client/build/libs/client-<ver>-shaded.jar
#   - IMPORTANT: clear/relocate any pre-existing OSRS cache so the client does a clean download
#     from our gateway instead of validating our groups against a stale Jagex cache:
#       mv ~/.runelite/jagexcache ~/.runelite/jagexcache.bak
#
# Usage:  run-local-client.sh /path/to/client-<ver>-shaded.jar
set -euo pipefail

JAR="${1:?usage: run-local-client.sh /path/to/client-<ver>-shaded.jar}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT=8080

# Serve the local jav_config over HTTP from this directory.
( cd "$DIR" && python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/jav_config_http.log 2>&1 ) &
HTTPD=$!
trap 'kill "$HTTPD" 2>/dev/null || true' EXIT
sleep 1

echo "[run-local-client] serving jav_config at http://127.0.0.1:${PORT}/jav_config.local.ws"
echo "[run-local-client] launching RuneLite -> JS5 to 127.0.0.1:43594"
exec java -jar "$JAR" --jav_config "http://127.0.0.1:${PORT}/jav_config.local.ws" --debug
