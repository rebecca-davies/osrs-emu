# Local CI/CD with nektos/act

This repo runs its GitHub Actions workflows **locally** with
[`nektos/act`](https://github.com/nektos/act), which executes the workflows inside Docker
containers on this machine — no GitHub-hosted runners, no pushing required.

- **CI** (`.github/workflows/ci.yml`) — build + test on every push / pull request.
- **CD** (`.github/workflows/cd.yml`) — on merge to `main`: build + test, then deploy the
  gateway to the local single host as a Docker container.

Everything below is designed so `./gradlew build` stays the source of truth and the
RuneLite client is **never** launched by automation (see [Safety](#safety-boundaries)).

## Prerequisites

- **Docker** running locally.
- **`act`** on `PATH`. Installed no-sudo into `~/.local/bin`:

  ```bash
  curl -sfL https://raw.githubusercontent.com/nektos/act/master/install.sh \
    | bash -s -- -b "$HOME/.local/bin"
  act --version    # verified with 0.2.89
  ```

  Ensure `~/.local/bin` is on your `PATH`.

The repo's [`.actrc`](.actrc) pins the runner image (`catthehacker/ubuntu:act-latest`) so
`act` never drops into its interactive "which image size?" prompt.

## Run CI locally

The `build` job compiles every module and runs the full test suite (`./gradlew build`).

```bash
# push event
act push -W .github/workflows/ci.yml -j build

# pull_request event
act pull_request -W .github/workflows/ci.yml -j build
```

`-W` is required because both workflows happen to define a job called `build`; it points
`act` at the specific workflow file.

Notes:

- **JDK 21**: `actions/setup-java@v4` installs Temurin 21 inside the runner; Gradle's
  toolchain then uses it, so no second JDK is downloaded.
- **First run is slow**: the runner downloads the Gradle distribution and all
  dependencies from Maven Central (the runner uses host networking, so it has internet).
  Subsequent runs reuse them if you keep the container (`--reuse`).
- **Gradle caching**: `actions/setup-java`'s `cache: gradle` caches `~/.gradle`. On
  GitHub-hosted runners this uses the Actions cache; under `act` it uses act's built-in
  local cache server (it saves/restores a ~300 MB gradle cache between runs) — either way
  it never fails the job.
- **Skipped tests are expected**: tests that need the gitignored runtime assets (the
  cache dump, `server-rsa.properties`) skip themselves when those are absent. CI does not
  provide them, and the build is still green.

Speed-ups (optional):

- `--reuse` keeps the runner container between runs so Gradle/JDK downloads persist.
- `--pull=false` skips re-checking the runner image once you have pulled it
  (`docker pull catthehacker/ubuntu:act-latest`).

## Deploy locally (CD)

Deploy is **containerized**: the gateway runs as the `osrsemu-gateway` Docker container,
defined by [`Dockerfile`](Dockerfile) + [`compose.yaml`](compose.yaml) and driven by
[`scripts/deploy.sh`](scripts/deploy.sh).

### What deploy concretely does

`scripts/deploy.sh` (idempotent):

1. Builds the gateway image (multi-stage: Gradle `:gateway:installDist` → a JRE-only
   runtime image). No source, cache dump, RSA key, or client tree is baked in
   (`.dockerignore` enforces this).
2. `docker compose -p osrsemu up -d --build` — brings up the `osrsemu` stack: the
   `postgres` service and the `gateway` container (the gateway `depends_on` postgres being
   healthy), publishing gateway host port **43594** and postgres **127.0.0.1:54330**.
3. Bind-mounts the cache dump and RSA key **read-only** from the host into the gateway
   container (`/data/cache-data`, `/data/server-rsa.properties`), which `Main.kt` reads via
   `OSRS_CACHE_DIR` / `OSRS_SERVER_RSA_PROPERTIES`; the gateway reaches Postgres over the
   compose network at `OSRS_DATABASE_URL=jdbc:postgresql://postgres:5432/osrsemu`.

Running it directly on the host (the paths must point at a location that has `cache-data/`
and `server-rsa.properties` — e.g. the main checkout):

```bash
OSRS_CACHE_DIR=/abs/host/path/cache-data \
OSRS_SERVER_RSA_PROPERTIES=/abs/host/path/server-rsa.properties \
  ./scripts/deploy.sh
```

Run from a checkout that already contains the assets and the defaults suffice:

```bash
./scripts/deploy.sh
```

Manage the deployed container:

```bash
docker compose -p osrsemu -f compose.yaml ps
docker compose -p osrsemu -f compose.yaml logs -f gateway
docker compose -p osrsemu -f compose.yaml down     # stop + remove
```

### Running the CD deploy via act

`act` runs each job in a container, so the `deploy` job reaches the host Docker daemon
via **docker-out-of-docker**: `act` mounts the host `/var/run/docker.sock` into the runner
by default, so `docker compose` inside the job creates the gateway container as a sibling
on the host. Because the **host** daemon resolves bind-mount sources, the asset paths must
be **absolute host paths**, passed through the environment. The `deploy` job is also gated
`if: github.ref == 'refs/heads/main'`, so pass a `main` ref event to exercise it:

```bash
# event payload that presents a main ref
printf '{ "ref": "refs/heads/main" }' > /tmp/push-main.json

act push -W .github/workflows/cd.yml -j deploy \
  --eventpath /tmp/push-main.json \
  --env OSRS_ASSETS_ON_HOST=1 \
  --env OSRS_CACHE_DIR=/abs/host/path/cache-data \
  --env OSRS_SERVER_RSA_PROPERTIES=/abs/host/path/server-rsa.properties
```

- `OSRS_ASSETS_ON_HOST=1` tells `deploy.sh` the asset paths are host paths not visible
  inside the runner, so it skips its local existence check and trusts them.
- `act -j deploy` also runs the `build` job first (it is a `needs:` dependency), so this
  single command exercises the full **build + test then deploy** pipeline.

On a real **self-hosted** runner installed on this box, the same `deploy` job runs
unchanged (the assets are local, the socket is local), which is the intended production
path for a single-host deployment.

### Deploy design decision: container, not host process

`act` jobs are inherently containerized, so "restart a host process" has no clean,
safe escape hatch. Deploying the gateway as its **own container** (built via DooD,
started with a fixed Compose project name) is the pragmatic, idempotent, isolated
approach: it restarts cleanly, mounts the host-only runtime assets read-only, and cannot
disturb the unrelated stacks on this box.

## Safety boundaries

These are enforced, not aspirational (CLAUDE.md §12/§12a/§14):

- **The client is never launched.** Deploy starts the **server only**. There is no client
  step anywhere in `cd.yml`, `deploy.sh`, `Dockerfile`, or `compose.yaml`, and no relaunch
  loop. The gateway process never reaches Jagex's network.
- **No secrets or large/gitignored assets in the image.** `.dockerignore` excludes
  `client/`, `cache-data/`, `server-rsa.properties`, build output, and `.git`. The cache
  dump and RSA key are bind-mounted **read-only** at runtime instead of copied in.
- **Isolation from other containers.** Every `docker compose` action is scoped to the
  `osrsemu-gateway` Compose project, so the unrelated Postgres stacks on this box
  (`rotmgemu`, `uber_scraper`, `uber-tracker`, `osrsemu-save`) are never stopped,
  modified, or removed.
- **RSA private key stays server-side.** It is gitignored and only ever bind-mounted from
  the host; it is never committed and never enters an image layer.
