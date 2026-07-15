#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "deployment verification requires Docker Compose" >&2
  exit 1
fi

bash -n scripts/deploy.sh scripts/run-local-cd.sh scripts/install-local-hooks.sh .githooks/post-merge

CONFIG="$(${COMPOSE[@]} -f compose.yaml config --format json)"
python3 - "$CONFIG" <<'PY'
import json
import sys

config = json.loads(sys.argv[1])
services = config["services"]

postgres_ports = services["postgres"].get("ports", [])
assert not postgres_ports, f"PostgreSQL must stay internal to Compose, found {postgres_ports!r}"

gateway_ports = services["gateway"].get("ports", [])
assert len(gateway_ports) == 1, f"expected one gateway port, found {gateway_ports!r}"
port = gateway_ports[0]
assert port.get("host_ip") == "127.0.0.1", f"gateway must bind loopback, found {port!r}"
assert port.get("published") == "43594", f"unexpected default gateway port: {port!r}"

image = services["gateway"].get("image", "")
assert image == "osrsemu-gateway:dev", f"default image must be explicitly tagged, found {image!r}"
assert services["gateway"].get("user") == "1000:1000", "gateway must run as the default asset owner"

secret_path = services["gateway"]["environment"].get("OSRS_SERVER_RSA_PROPERTIES")
assert secret_path == "/run/secrets/server_rsa", f"RSA key is not using a Compose secret: {secret_path!r}"
mounted_secrets = {entry["source"] for entry in services["gateway"].get("secrets", [])}
assert "server_rsa" in mounted_secrets, "gateway does not mount server_rsa"
health_test = " ".join(services["gateway"]["healthcheck"]["test"])
assert "test -r /run/secrets/server_rsa" in health_test, "health check does not require readable RSA"
PY

echo "deployment configuration verified"
