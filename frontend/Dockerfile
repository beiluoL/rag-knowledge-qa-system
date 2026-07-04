# ============================================
# 前端 Dockerfile — 多阶段构建
# 阶段1: Node.js 编译 Vue 项目
# 阶段2: Nginx 托管静态文件 + API 反向代理
# 使用 Debian 基础镜像（ARM64 兼容性好）
# ============================================

# --- 阶段 1：编译 ---
FROM node:20 AS builder

WORKDIR /build

# 利用 Docker 缓存：先复制依赖文件
COPY package.json package-lock.json* ./
RUN npm ci --registry=https://registry.npmmirror.com 2>/dev/null || npm install --registry=https://registry.npmmirror.com

# 复制源码并构建
COPY . .
# Docker 模式下 API 使用相对路径（由 Nginx 反向代理）
ENV VITE_API_BASE_URL=/api
RUN npm run build

# --- 阶段 2：运行（Nginx） ---
FROM nginx:latest

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制构建产物到 Nginx 静态目录
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
