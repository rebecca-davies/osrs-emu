#!/usr/bin/env bash
set -euo pipefail
umask 077

if (( $# != 7 )); then
  echo "usage: run-client.sh STATE_DIR CLIENT_ID ASSET_ROOT DISPLAY XAUTHORITY JAVA HTTP_PORT" >&2
  exit 64
fi

STATE_DIR="$1"
CLIENT_ID="$2"
ASSET_ROOT="$3"
DISPLAY_VALUE="$4"
XAUTHORITY_VALUE="$5"
CLIENT_JAVA="$6"
HTTP_PORT="$7"
CLIENT_DIR="$STATE_DIR/clients/$CLIENT_ID"
CLIENT_HOME="$CLIENT_DIR/home"

if ip route show default | grep -q .; then
  echo "refusing to launch RuneLite with an external default route" >&2
  exit 90
fi

mkdir -p "$CLIENT_HOME"

exec env \
  HOME="$CLIENT_HOME" \
  XAUTHORITY="$XAUTHORITY_VALUE" \
  DISPLAY="$DISPLAY_VALUE" \
  "$CLIENT_JAVA" \
  -Duser.home="$CLIENT_HOME" \
  -cp "$ASSET_ROOT/client/patches/injected-client-patched.jar:$ASSET_ROOT/client/runelite/runelite-client/build/libs/client-1.12.33-SNAPSHOT-shaded.jar" \
  net.runelite.client.RuneLite \
  --jav_config "http://127.0.0.1:$HTTP_PORT/jav_config.local.ws" \
  --debug
