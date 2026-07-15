# Server image with build tooling excluded from the runtime stage.
#
# Runtime assets are NEVER baked in (CLAUDE.md §12/§12a/§14): the cache dump, the RSA
# private key, and the RuneLite client are all gitignored and excluded via .dockerignore.
# The cache and RSA key are bind-mounted read-only at runtime (see compose.yaml); the
# client is never present and this image never launches it.

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# installDist produces server/app/build/install/server-app/{bin,lib}. We skip tests here — the
# CI/CD `build` job already ran the full `./gradlew build` (tests) as the deploy gate;
# this stage is packaging only.
RUN ./gradlew :server-app:installDist --no-daemon --stacktrace

# ---- runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
# Run as an unprivileged user (CLAUDE.md §9 hardening). 43594 is unprivileged so no root needed.
RUN groupadd --system server && useradd --system --gid server --home-dir /opt/server server
WORKDIR /opt/server
COPY --from=build /src/server/app/build/install/server-app/ ./

# Point the server at the bind-mount locations. ServerConfig reads these env vars and falls
# back to relative paths otherwise; here they must be absolute container paths.
ENV OSRS_CACHE_DIR=/data/cache-data \
    OSRS_SERVER_RSA_PROPERTIES=/data/server-rsa.properties

EXPOSE 43594
USER server
ENTRYPOINT ["/opt/server/bin/server-app"]
