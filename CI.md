# CI/CD and local deployment

The same GitHub Actions workflows run remotely or locally through
[`nektos/act`](https://github.com/nektos/act).

- **CI** (`.github/workflows/ci.yml`) — build + test on every push / pull request.
- **CD build gate** (`.github/workflows/cd.yml`) — runs on pushes to `main`.
- **CD deploy** — runs only on a runner labelled `self-hosted`, `linux`, and `osrsemu`, because it
  requires this host's Docker socket and runtime assets. A GitHub-hosted runner cannot deploy here.
- **Local CD** (`scripts/run-local-cd.sh`) — explicitly runs that same workflow through `act`.

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
2. Preserves the previous deployed image as `osrsemu-gateway:rollback`, then builds an image tagged
   with the source commit rather than mutable `latest`.
3. Brings up the internal PostgreSQL service and gateway. Only the gateway is published, on
   **127.0.0.1:43594** by default; PostgreSQL stays inside the Compose network and cannot collide
   with development databases.
4. Mounts the cache read-only and the RSA key as a Compose secret, then waits for gateway health.
   A timeout or unhealthy container fails deployment and prints the gateway logs.

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

The RSA key must be mode `0600`; deployment rejects group/world-accessible key files.

### Automatic local deployment after merging to main

Git never installs repository hooks by itself. Opt this checkout in once:

```bash
./scripts/install-local-hooks.sh
```

The tracked `post-merge` hook starts `scripts/run-local-cd.sh` only when the checked-out branch is
`main`. It runs in the background, serializes deployments with a lock, and writes its log to
`~/.local/state/osrsemu/local-cd.log`. Merges in feature worktrees do not deploy.

Manage the deployed container:

```bash
docker compose -p osrsemu -f compose.yaml ps
docker compose -p osrsemu -f compose.yaml logs -f gateway
docker compose -p osrsemu -f compose.yaml down     # stop + remove
```

### Running local CD explicitly

`act` runs each job in a container, so the `deploy` job reaches the host Docker daemon
via **docker-out-of-docker**: `act` mounts the host `/var/run/docker.sock` into the runner.
The wrapper validates that it is running from `main`, supplies a main push event, passes absolute
host asset paths, and prevents overlapping runs:

```bash
./scripts/run-local-cd.sh
```

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
  `osrsemu` Compose project, so the unrelated Postgres stacks on this box
  (`rotmgemu`, `uber_scraper`, `uber-tracker`, `osrsemu-save`) are never stopped,
  modified, or removed.
- **RSA private key stays server-side.** It is gitignored, must be host mode `0600`, and is mounted
  as a Compose secret; it is never committed and never enters an image layer.
