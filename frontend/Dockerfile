# ============================================
# 前端 Dockerfile — 运行镜像
# 需先在本地执行: npm run build
# ============================================
FROM nginx:latest

# Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制本地已构建好的 dist 目录
COPY dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
