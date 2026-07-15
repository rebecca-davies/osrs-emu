# Gateway server image — two stages so the (large) JDK + Gradle build tooling never
# ships in the runtime image, and so no source, cache dump, RSA key, or client tree is
# baked in. The runtime image contains ONLY the gateway's installDist output + a JRE.
#
# Runtime assets are NEVER baked in (CLAUDE.md §12/§12a/§14): the cache dump, the RSA
# private key, and the RuneLite client are all gitignored and excluded via .dockerignore.
# The cache and RSA key are bind-mounted read-only at runtime (see compose.yaml); the
# client is never present and this image never launches it.

# ---- build stage: compile + package the gateway into a self-contained dist ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# installDist produces gateway/build/install/gateway/{bin,lib}. We skip tests here — the
# CI/CD `build` job already ran the full `./gradlew build` (tests) as the deploy gate;
# this stage is packaging only.
RUN ./gradlew :gateway:installDist --no-daemon --stacktrace

# ---- runtime stage: JRE + the gateway dist, nothing else ----
FROM eclipse-temurin:21-jre AS runtime
# Run as an unprivileged user (CLAUDE.md §9 hardening). 43594 is unprivileged so no root needed.
RUN groupadd --system gateway && useradd --system --gid gateway --home-dir /opt/gateway gateway
WORKDIR /opt/gateway
COPY --from=build /src/gateway/build/install/gateway/ ./

# Point the gateway at the bind-mount locations. Main.kt reads these env vars and falls
# back to relative paths otherwise; here they must be absolute container paths.
ENV OSRS_CACHE_DIR=/data/cache-data \
    OSRS_SERVER_RSA_PROPERTIES=/data/server-rsa.properties

EXPOSE 43594
USER gateway
ENTRYPOINT ["/opt/gateway/bin/gateway"]
