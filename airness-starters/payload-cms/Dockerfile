# The standalone server is built on the host by `mvn clean verify` and only copied here: a build stage inside the
# image would install the packages again from a registry, and run the build without the harness that verified it.
# `next.config.ts` enables the standalone output, which carries the server, the dependencies it was traced to
# need and the public files; the static client assets are copied beside it.
#
# Pinned by digest so an upstream rebuild of the tag cannot change what is built here; resolve a new digest with
# `docker buildx imagetools inspect node:26-alpine` when bumping.
FROM node:26.13.1-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5
# The server runs on Node alone, so the npm the base image carries, with the packages it bundles, is removed rather
# than shipped unused: a vulnerability in one of them would otherwise be the image's own.
RUN addgroup -S -g 10001 starter && adduser -S -u 10001 -G starter starter \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx
WORKDIR /application
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
COPY --chown=10001:10001 .next/standalone/ ./
COPY --chown=10001:10001 .next/static/ ./.next/static/
USER 10001:10001
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD ["wget", "--quiet", "--spider", "http://127.0.0.1:3000/"]
# The container reads its configuration when it starts, and applies the pending migrations before it serves;
# AGENTS.md, section Deployment, describes it.
CMD ["node", "server.js"]
