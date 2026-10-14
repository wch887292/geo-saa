# syntax=docker/dockerfile:1
# ============================================================
# 前端生产镜像（多阶段构建）
#
# 与上游原版的差异（本次修复）：
#  1. node:18-alpine → node:22-alpine。前端用 Vite 8，它要求 Node
#     20.19+ / 22.12+（低版本会直接报 crypto.hash is not a function），
#     在 node:18 上 npm run build 必然失败。
#  2. npm ci → npm install。package-lock.json 由 Windows 生成，其中
#     rollup 等包的 optionalDependencies 按 win32 平台锁定；
#     npm ci 在 linux 上会漏装 @rollup/rollup-linux-x64-gnu 原生模块，
#     构建时报 "Cannot find module"。npm install 在 lock 存在时同样
#     锁定版本，但会为当前平台补装对应原生模块。CI 侧出于同样原因已改用 npm install。
# ============================================================

# ---------- 构建阶段 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 先只拷清单，让依赖层可被缓存复用：仅改 src/ 时不重装依赖
COPY package*.json ./
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build


# ---------- 运行阶段 ----------
FROM nginx:alpine

# healthcheck 用 wget：nginx:alpine 基于 alpine，busybox 自带 wget，
# 不需要额外装包（后端是 JRE 镜像才必须避开 curl）。
# 探测 /healthz（default.conf 中定义的纯静态端点），避免依赖后端。

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=5 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]