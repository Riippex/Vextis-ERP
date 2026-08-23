FROM node:24.15-alpine AS build
RUN corepack enable
WORKDIR /workspace
COPY pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile --filter @vextis/web...
COPY apps/web apps/web
COPY contracts contracts
WORKDIR /workspace/apps/web
RUN pnpm build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY --from=build /workspace/apps/web/dist/web/browser /usr/share/nginx/html
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
