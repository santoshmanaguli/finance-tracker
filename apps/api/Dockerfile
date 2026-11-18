# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Multi-stage build. Build context = repository root (pnpm workspace):
#   docker build -f apps/api/Dockerfile -t finance-api .
#
#   Stage 1 (build):   all dependencies + TypeScript compiler -> dist/
#   Stage 2 (runtime): only production dependencies + dist/. No compiler, no tests, no source.
# Smaller image, smaller attack surface.
# ---------------------------------------------------------------------------
FROM node:24-alpine AS build
RUN corepack enable
WORKDIR /repo

# 1) Only the files dependency resolution needs. Docker caches this layer, so the slow
#    install is skipped on rebuilds unless the lockfile or a package.json changes.
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/api/package.json apps/api/
RUN --mount=type=cache,id=pnpm,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @finance/api...

# 2) Source, then compile. Tests run in CI (they need Docker), not inside the image build.
COPY apps/api apps/api
RUN pnpm --filter @finance/api build

# 3) A self-contained folder with production node_modules + only the files listed in
#    package.json "files" (dist/). No source, tests or tsconfig end up in the image.
RUN pnpm --filter @finance/api deploy --prod --legacy /out

# ---------------------------------------------------------------------------
FROM node:24-alpine AS runtime
ENV NODE_ENV=production
WORKDIR /app

# The official image ships a non-root "node" user. Never run as root inside a container:
# a compromised app would be root in it.
COPY --from=build --chown=node:node /out ./
USER node

EXPOSE 8080

# Exec form (JSON array): node is PID 1 and receives SIGTERM directly, which triggers the
# graceful shutdown in server.ts. (A shell-form CMD would swallow the signal.)
CMD ["node", "dist/server.js"]
