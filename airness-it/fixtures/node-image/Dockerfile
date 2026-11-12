# The server needs nothing but Node, which runs it as it strips its types, so the build reads no registry. It brings
# the empty database it is given to its schema before it serves.
FROM node:26.8.2-alpine@sha256:ef24c5053d50fdc3e4e56eb4e7ddb7861874ab0fdc797046ba897581deb8e868
WORKDIR /app
COPY package.json ./
COPY src ./src
USER node
ENV PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD ["wget", "--quiet", "--spider", "http://127.0.0.1:3000/"]
CMD ["node", "src/server.ts"]
