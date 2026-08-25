# syntax=docker/dockerfile:1.9
ARG NODE_IMAGE=docker.io/library/node:22.19.0-bookworm-slim@sha256:4a4884e8a44826194dff92ba316264f392056cbe243dcc9fd3551e71cea02b90
ARG NGINX_IMAGE=docker.io/nginxinc/nginx-unprivileged:1.29-alpine@sha256:0c79d56aee561a1d81c63f00eee5fb5fe29279560cdc55e91425133104c7fbe6

FROM ${NODE_IMAGE} AS builder
WORKDIR /workspace/data-platform-web
COPY data-platform-web/package.json data-platform-web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts
COPY data-platform-web ./
RUN test "$(node --version)" = "v22.19.0" \
    && test "$(npm --version)" = "10.9.3" \
    && npm run build

FROM ${NGINX_IMAGE} AS runtime
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/data-platform-web/dist /usr/share/nginx/html
EXPOSE 8080
