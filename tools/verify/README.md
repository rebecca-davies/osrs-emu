# Login-state screenshot verification harness

Purpose: give **AFK visual proof** (a PNG) that an OSRS client actually renders —
and, once the pieces below are in place, that it reaches the login screen against
**our** gateway rather than Jagex's servers.

This harness proves the *screenshot-capture* step today. It deliberately does
**not** attempt a login and does **not** patch the client. See "What's still
blocked" for the remaining pieces.

## (a) Screenshot pipeline — CONFIRMED WORKING

`scrot` captures the live X display `:0` to a PNG.

- Test image: `/tmp/claude-1000/-home-bec-code-osrsemu/1bc21981-5b91-4660-b604-ee720f44d187/scratchpad/shot-test.png`
- Dimensions: **2560x1440**, 8-bit PNG.
- Harness self-test (screenshot-only mode) also succeeded, e.g.
  `tools/verify/verify-shots/login-YYYYMMDD-HHMMSS.png` at 2560x1440.

Available capture tools on this box: `scrot`, `maim`, `import` (ImageMagick),
`ffmpeg`. The harness prefers `scrot`, falls back to `maim`, then `import`.

## (b) Display: headless is BLOCKED — must use the real `:0`

- **Xvfb: not installed.** `apt-get install -y xvfb` fails with
  `Could not open lock file /var/lib/dpkg/lock-frontend ... are you root?`
  (no sudo available — not attempted). So there is **no headless framebuffer**.
- **Xephyr IS installed** (`/usr/bin/Xephyr`) — a *nested* X server. It can host
  a client window inside a window on `:0`, but it still needs the real `:0` (or
  another running X server) as its host, so it does not make things truly
  headless. It could isolate the client's window for a cleaner screenshot if
  desired (`Xephyr :1 -screen 1280x800 & DISPLAY=:1 <client> &` then capture
  `:1`).
- **Consequence:** launching a GUI client renders a window on **the user's real
  screen (`:0`)**. Screenshots capture whatever is on that screen. Run this when
  the user is AFK, as intended.

## (c) How to launch RuneLite (stock)

RuneLite is installed as a **Flatpak** (`net.runelite.RuneLite`, v2.7.5). The
exact, working launch command (from the installed `.desktop` file):

```
flatpak run --branch=stable --arch=x86_64 --command=runelite net.runelite.RuneLite
```

This launches the **stock** client and connects to **Jagex's** OSRS servers — it
proves "a real client renders and is screenshotted", but it is NOT a login
against our gateway.

Notes on other paths found (documented so nobody wastes time on them):
- `~/Scripts/runelite-run.sh` runs `java -jar ~/runelite.jar`, but
  `~/runelite.jar` is a **broken symlink** (`-> /home/bec/code/client-*-shaded.jar`,
  glob never expanded — target does not exist). Do not use as-is.
- Locally built shaded jars DO exist and are runnable directly:
  - `/home/bec/code/runelite/runelite-client/build/libs/client-1.12.33-SNAPSHOT-shaded.jar`
  - `/home/bec/code/runelite/runelite-client/target/client-1.12.10-SNAPSHOT-shaded.jar`
  - Launch with `java -jar <that-jar>`. This local build is the natural base for
    the future **host-patched** client, since we control its source.
- `~/.runelite/repository2/` holds RuneLite's bootstrap-downloaded
  `client-1.12.7.jar` + deps, but running those needs the full RuneLite launcher
  classpath — prefer the flatpak or a shaded jar instead.

## (d) Using `screenshot-login.sh`

```
tools/verify/screenshot-login.sh [-w SECONDS] [-k] [-o DIR] [-d DISPLAY] -- CLIENT CMD...
```

- `-w SECONDS`  wait for the client to render before capturing (default 25).
- `-k`          kill the launched client after the screenshot (default: leave running).
- `-o DIR`      output directory (default `tools/verify/verify-shots/`).
- `-d DISPLAY`  X display to launch on / capture (default `$DISPLAY`, else `:0`).
- Everything after `--` is the client command, launched backgrounded. Omit it
  to just screenshot the current display (pipeline smoke test).

Behaviour: creates the output dir, launches the client, polls during the wait so
an early crash is reported (exit 4) instead of a blind sleep, captures a
timestamped `login-YYYYMMDD-HHMMSS.png`, verifies it is non-empty, and prints its
dimensions. `set -euo pipefail` + EXIT/ERR/INT/TERM traps ensure a failed run
tears down the client it started.

Examples:

```bash
# 1. Prove the pipeline (no client) — captures the current screen:
tools/verify/screenshot-login.sh

# 2. Stock RuneLite (flatpak) — proves a real client renders + is captured:
tools/verify/screenshot-login.sh -w 40 -- \
  flatpak run --branch=stable --arch=x86_64 --command=runelite net.runelite.RuneLite

# 3. Future: host-patched client against our gateway, kill after the shot:
tools/verify/screenshot-login.sh -w 40 -k -- java -jar /path/to/host-patched-client.jar
```

## (e) What's still blocked before we can screenshot a login against OUR server

The screenshot capability is done. To make the screenshot show **our** login
screen (not Jagex's), two things must exist first — neither is in place yet:

1. **The gateway must be running.** Per the JS5 plan
   (`docs/superpowers/plans/2026-07-14-osrs-emulator-js5.md`), the client first
   does a JS5 handshake and downloads the cache from our server on port
   **43594**. Steps: run `:tools:cache-fetch:run` to populate `cache-data/`,
   then start the gateway (`:gateway:run`). At time of writing the gateway module
   only has `CacheStore`/`Js5Response` compiled — the `Main.kt` that binds 43594
   is still to be built.
2. **The client must be host-patched to `127.0.0.1`.** Stock RuneLite connects to
   Jagex. The plan calls for a small **host-only** client patch pointing the world
   host at `127.0.0.1` (the RSA patch is NOT needed just to reach the login
   screen). That patched jar does not exist yet; build it from the local RuneLite
   source (see the shaded jars in (c)) and pass its `java -jar` command to this
   harness.

Once both exist: start the gateway, then
`screenshot-login.sh -w 40 -k -- java -jar <host-patched-client.jar>` and the
resulting PNG is the visual proof that a client reached the login screen against
our server.
