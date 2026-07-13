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
# RSA patch (Task 5): if client/patches/injected-client-patched.jar exists (built by
# `./gradlew :tools:client-patch:run`, which byte-patches a COPY of the cached injected-client jar
# so its login RSA modulus is ours instead of Jagex's — see client/HOST-PATCH-NOTES.md §5), this
# script puts it FIRST on the JVM classpath ahead of the shaded jar. The shaded jar bundles its
# own (unpatched) copy of the same classes; standard JVM classpath resolution loads the first
# matching class it finds across -cp entries in order, so our patched `bg.class` wins over the
# shaded jar's copy without ever touching the shaded jar or the ~/.gradle cache. This requires
# `-cp` + an explicit main class instead of `-jar` (per the `java` docs, `-jar` ignores -cp
# entirely). If the patched jar is absent, this falls back to the original `-jar` launch (login
# RSA is then still Jagex's — fine for JS5/login-screen-only milestones, not for the login block).
#
# Prereqs:
#   - Our gateway running:  (cd <repo> && ./gradlew :gateway:run)   # listens on 43594
#   - The RuneLite shaded jar built:
#       (cd client/runelite && ./gradlew :client:shadowJar)
#       -> client/runelite/runelite-client/build/libs/client-<ver>-shaded.jar
#   - IMPORTANT: clear/relocate any pre-existing OSRS cache so the client does a clean download
#     from our gateway instead of validating our groups against a stale Jagex cache:
#       mv ~/.runelite/jagexcache ~/.runelite/jagexcache.bak
#   - (optional, for the RSA patch) generate our server keypair + patched jar once:
#       ./gradlew :tools:client-patch:run
#
# Usage:  run-local-client.sh /path/to/client-<ver>-shaded.jar [/path/to/patched-injected-client.jar]
set -euo pipefail

JAR="${1:?usage: run-local-client.sh /path/to/client-<ver>-shaded.jar [/path/to/patched-injected-client.jar]}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT=8080
RUNELITE_MAIN_CLASS="net.runelite.client.RuneLite"
PATCHED_JAR="${2:-$DIR/injected-client-patched.jar}"

# Serve the local jav_config over HTTP from this directory.
( cd "$DIR" && python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/jav_config_http.log 2>&1 ) &
HTTPD=$!
trap 'kill "$HTTPD" 2>/dev/null || true' EXIT
sleep 1

echo "[run-local-client] serving jav_config at http://127.0.0.1:${PORT}/jav_config.local.ws"
echo "[run-local-client] launching RuneLite -> JS5 to 127.0.0.1:43594"

if [[ -f "$PATCHED_JAR" ]]; then
    echo "[run-local-client] RSA-patched injected-client found: $PATCHED_JAR"
    echo "[run-local-client] classpath override: patched jar first, then shaded jar (login RSA = OUR modulus)"
    exec java -cp "${PATCHED_JAR}:${JAR}" "$RUNELITE_MAIN_CLASS" \
        --jav_config "http://127.0.0.1:${PORT}/jav_config.local.ws" --debug
else
    echo "[run-local-client] no patched injected-client jar at $PATCHED_JAR — launching UNPATCHED (Jagex login RSA)"
    echo "[run-local-client] run './gradlew :tools:client-patch:run' to generate one"
    exec java -jar "$JAR" --jav_config "http://127.0.0.1:${PORT}/jav_config.local.ws" --debug
fi
