# 直接使用已打包的 dist 目录，无需 npm install
FROM nginx:1.23.4-alpine

LABEL maintainer="NexusERP"

# 复制构建产物到 Nginx 默认目录
COPY dist /usr/share/nginx/html

# 复制自定义 nginx 配置（包含后端反向代理）
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
