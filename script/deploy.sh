#!/bin/bash
# ==========================================
# NexusERP Docker 双机部署脚本（Linux）
# 主服务器 (159.75.14.226): MySQL, Redis, RabbitMQ, MinIO, Nginx+前端, NexusServer
# 辅助服务器 (117.72.190.225): MonitorAdmin, SnailJobServer
#
# 用法:
#   chmod +x script/deploy.sh
#   ./script/deploy.sh [build|images|up|down|logs|restart|all]
#
# 命令说明:
#   build       - Maven 打包 + 构建镜像（需要源代码和 Maven 环境）
#   images      - 仅构建 Docker 镜像（使用已上传的 jar/dist 文件）
#   up          - 启动主服务器服务
#   up-assist   - 启动辅助服务器服务
#   down        - 停止主服务器服务
#   down-assist - 停止辅助服务器服务
#   logs        - 查看实时日志
#   restart     - 重启主服务器服务
#   all         - 完整流程：检查环境 + 构建镜像 + 启动主服务器（默认）
# ==========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 项目根目录（脚本位于 script/ 下）
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_MAIN="${PROJECT_ROOT}/docker-compose-main.yml"
COMPOSE_ASSIST="${PROJECT_ROOT}/docker-compose-assist.yml"

# 前端目录（默认为 /nexus/nexus-ui，如需修改请更改此路径）
FRONTEND_ROOT="/nexus/nexus-ui"

# 镜像版本
VERSION="5.6.0"

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  NexusERP Docker 双机部署脚本${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# 检查命令参数
COMMAND=${1:-all}

function check_env() {
    echo -e "${YELLOW}[1/3] 检查环境...${NC}"

    if ! command -v docker &> /dev/null; then
        echo -e "${RED}错误：未检测到 Docker，请先安装 Docker${NC}"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo -e "${RED}错误：未检测到 docker-compose，请先安装${NC}"
        exit 1
    fi

    # 判断使用 docker-compose 还是 docker compose
    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
    else
        COMPOSE_CMD="docker-compose"
    fi

    echo -e "${GREEN}环境检查通过，使用命令: ${COMPOSE_CMD}${NC}"
}

function check_jars() {
    echo -e "${YELLOW}检查 jar 文件...${NC}"

    local missing=0

    if [ ! -f "${PROJECT_ROOT}/ruoyi-admin/target/ruoyi-admin.jar" ]; then
        echo -e "${RED}  ✗ 缺少 ruoyi-admin.jar${NC}"
        missing=1
    else
        echo -e "${GREEN}  ✓ ruoyi-admin.jar${NC}"
    fi

    if [ ! -f "${PROJECT_ROOT}/ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar" ]; then
        echo -e "${RED}  ✗ 缺少 ruoyi-monitor-admin.jar${NC}"
        missing=1
    else
        echo -e "${GREEN}  ✓ ruoyi-monitor-admin.jar${NC}"
    fi

    if [ ! -f "${PROJECT_ROOT}/ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar" ]; then
        echo -e "${RED}  ✗ 缺少 ruoyi-snailjob-server.jar${NC}"
        missing=1
    else
        echo -e "${GREEN}  ✓ ruoyi-snailjob-server.jar${NC}"
    fi

    if [ $missing -eq 1 ]; then
        echo ""
        echo -e "${RED}错误：缺少 jar 文件，请先上传打包产物${NC}"
        echo -e "${YELLOW}期望的目录结构：${NC}"
        echo "  ${PROJECT_ROOT}/ruoyi-admin/target/ruoyi-admin.jar"
        echo "  ${PROJECT_ROOT}/ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar"
        echo "  ${PROJECT_ROOT}/ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar"
        exit 1
    fi

    echo -e "${GREEN}所有 jar 文件检查通过${NC}"
}

function check_frontend() {
    echo -e "${YELLOW}检查前端文件...${NC}"

    if [ ! -d "${FRONTEND_ROOT}/dist" ]; then
        echo -e "${RED}  ✗ 缺少前端构建产物: ${FRONTEND_ROOT}/dist${NC}"
        echo -e "${YELLOW}请先上传前端打包文件到 ${FRONTEND_ROOT}/${NC}"
        exit 1
    fi

    if [ ! -f "${FRONTEND_ROOT}/Dockerfile" ]; then
        echo -e "${RED}  ✗ 缺少 Dockerfile: ${FRONTEND_ROOT}/Dockerfile${NC}"
        exit 1
    fi

    if [ ! -f "${FRONTEND_ROOT}/nginx.conf" ]; then
        echo -e "${RED}  ✗ 缺少 nginx.conf: ${FRONTEND_ROOT}/nginx.conf${NC}"
        exit 1
    fi

    echo -e "${GREEN}  ✓ dist/${NC}"
    echo -e "${GREEN}  ✓ Dockerfile${NC}"
    echo -e "${GREEN}  ✓ nginx.conf${NC}"
    echo -e "${GREEN}前端文件检查通过${NC}"
}

function build_backend() {
    echo -e "${YELLOW}[2/4] 构建后端 Jar 包...${NC}"
    cd "${PROJECT_ROOT}"

    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}错误：未检测到 Maven，请先安装 Maven 3.9+ 和 JDK 17+${NC}"
        exit 1
    fi

    # 使用 prod profile 打包，跳过测试
    mvn clean package -DskipTests -Pprod

    echo -e "${GREEN}后端打包完成${NC}"
}

function build_images() {
    echo -e "${YELLOW}[2/3] 构建 Docker 镜像...${NC}"
    cd "${PROJECT_ROOT}"

    # 1. 主后端服务
    echo -e "${YELLOW}→ 构建 nexus-erp:fix-headless${NC}"
    docker build -t nexus-erp:fix-headless ./ruoyi-admin

    # 2. 监控中心
    echo -e "${YELLOW}→ 构建 nexus/nexus-monitor:${VERSION}${NC}"
    docker build -t nexus/nexus-monitor:${VERSION} ./ruoyi-extend/ruoyi-monitor-admin

    # 3. SnailJob 调度中心
    echo -e "${YELLOW}→ 构建 nexus/nexus-snailjob:${VERSION}${NC}"
    docker build -t nexus/nexus-snailjob:${VERSION} ./ruoyi-extend/ruoyi-snailjob-server

    # 4. 前端
    echo -e "${YELLOW}→ 构建 nexus/nexus-ui:${VERSION}${NC}"
    # docker build -t nexus/nexus-ui:${VERSION} "${FRONTEND_ROOT}"  # 前端实际用官方 nginx + volume 挂载 /docker/nginx/html/dist，无需 build

    echo -e "${GREEN}所有镜像构建完成${NC}"
}

function create_dirs() {
    echo -e "${YELLOW}→ 创建数据目录...${NC}"
    sudo mkdir -p /docker/mysql/data /docker/mysql/conf /docker/mysql/init
    sudo mkdir -p /docker/redis/conf /docker/redis/data
    sudo mkdir -p /docker/rabbitmq/data
    sudo mkdir -p /docker/minio/data
    sudo mkdir -p /docker/nginx/cert /docker/nginx/log
    sudo mkdir -p /docker/server/logs /docker/monitor/logs /docker/snailjob/logs

    # 首次部署时，自动复制 Redis 配置（如果不存在）
    if [ ! -f /docker/redis/conf/redis.conf ]; then
        echo -e "${YELLOW}→ 复制默认 Redis 配置...${NC}"
        sudo cp -n "${PROJECT_ROOT}/script/docker/redis/conf/redis.conf" /docker/redis/conf/redis.conf || true
    fi

    # 首次部署时，复制 MySQL 初始化脚本
    if [ ! -f /docker/mysql/init/ry-vue.sql ]; then
        echo -e "${YELLOW}→ 复制 MySQL 初始化脚本到 /docker/mysql/init/（首次启动会自动执行）${NC}"
        sudo cp -n "${PROJECT_ROOT}/script/sql/"*.sql /docker/mysql/init/ || true
    fi
}

function start_main() {
    echo -e "${YELLOW}[3/3] 启动主服务器服务...${NC}"
    cd "${PROJECT_ROOT}"

    create_dirs

    ${COMPOSE_CMD} -f "${COMPOSE_MAIN}" up -d

    echo ""
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}  主服务器 (159.75.14.226) 启动完成${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""
    echo -e "${YELLOW}访问地址：${NC}"
    echo -e "  前端页面:       http://nexuserp.website 或 http://159.75.14.226"
    echo -e "  后端API:        http://159.75.14.226:8080"
    echo -e "  RabbitMQ管理:   http://159.75.14.226:15672  (账号: nexus / <RABBITMQ_PASSWORD>)"
    echo -e "  MinIO控制台:    http://159.75.14.226:9001  (账号: minio / <MINIO_PASSWORD>)"
    echo -e "  MinIO API:      http://159.75.14.226:9005"
    echo ""
    echo -e "${YELLOW}辅助服务器 (117.72.190.225) 需单独部署：${NC}"
    echo -e "  监控中心:       http://117.72.190.225:9090/admin"
    echo -e "  SnailJob控制台:  http://117.72.190.225:8800/snail-job"
    echo ""
    echo -e "${YELLOW}常用命令：${NC}"
    echo -e "  查看日志: ${0} logs"
    echo -e "  停止服务: ${0} down"
    echo -e "  重启服务: ${0} restart"
}

function start_assist() {
    echo -e "${YELLOW}启动辅助服务器服务...${NC}"
    cd "${PROJECT_ROOT}"

    sudo mkdir -p /docker/monitor/logs /docker/snailjob/logs

    ${COMPOSE_CMD} -f "${COMPOSE_ASSIST}" up -d

    echo ""
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}  辅助服务器 (117.72.190.225) 启动完成${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""
    echo -e "${YELLOW}访问地址：${NC}"
    echo -e "  监控中心:       http://117.72.190.225:9090/admin"
    echo -e "  SnailJob控制台:  http://117.72.190.225:8800/snail-job"
}

function stop_main() {
    echo -e "${YELLOW}停止主服务器服务...${NC}"
    cd "${PROJECT_ROOT}"
    ${COMPOSE_CMD} -f "${COMPOSE_MAIN}" down
    echo -e "${GREEN}主服务器服务已停止${NC}"
}

function stop_assist() {
    echo -e "${YELLOW}停止辅助服务器服务...${NC}"
    cd "${PROJECT_ROOT}"
    ${COMPOSE_CMD} -f "${COMPOSE_ASSIST}" down
    echo -e "${GREEN}辅助服务器服务已停止${NC}"
}

function show_logs() {
    cd "${PROJECT_ROOT}"
    # 尝试显示当前服务器上运行的 compose 日志
    if [ -f "${COMPOSE_MAIN}" ]; then
        ${COMPOSE_CMD} -f "${COMPOSE_MAIN}" logs -f 2>/dev/null || true
    fi
    if [ -f "${COMPOSE_ASSIST}" ]; then
        ${COMPOSE_CMD} -f "${COMPOSE_ASSIST}" logs -f 2>/dev/null || true
    fi
}

function restart_main() {
    stop_main
    start_main
}

function export_images() {
    echo -e "${YELLOW}导出辅助服务器镜像...${NC}"
    cd "${PROJECT_ROOT}"

    docker save nexus/nexus-monitor:${VERSION} nexus/nexus-snailjob:${VERSION} -o ./nexus-assist-images.tar

    echo -e "${GREEN}镜像已导出到: ${PROJECT_ROOT}/nexus-assist-images.tar${NC}"
    echo -e "${YELLOW}请传输到辅助服务器：${NC}"
    echo -e "  scp ${PROJECT_ROOT}/nexus-assist-images.tar root@117.72.190.225:/nexus/"
    echo -e "  scp ${PROJECT_ROOT}/docker-compose-assist.yml root@117.72.190.225:/nexus/"
}

# 根据参数执行对应操作
case "${COMMAND}" in
    build)
        check_env
        build_backend
        build_images
        ;;
    images)
        check_env
        check_jars
        check_frontend
        build_images
        ;;
    up|start)
        check_env
        start_main
        ;;
    up-assist)
        check_env
        start_assist
        ;;
    down|stop)
        check_env
        stop_main
        ;;
    down-assist)
        check_env
        stop_assist
        ;;
    logs)
        show_logs
        ;;
    restart)
        check_env
        restart_main
        ;;
    export)
        check_env
        export_images
        ;;
    all|deploy)
        check_env
        check_jars
        check_frontend
        build_images
        start_main
        ;;
    *)
        echo -e "${YELLOW}用法: $0 {build|images|up|up-assist|down|down-assist|logs|restart|export|all}${NC}"
        echo ""
        echo "  build       - Maven 打包 + 构建镜像（需要源代码和 Maven 环境）"
        echo "  images      - 仅构建 Docker 镜像（使用已上传的 jar/dist 文件）"
        echo "  up          - 启动主服务器服务（159.75.14.226）"
        echo "  up-assist   - 启动辅助服务器服务（117.72.190.225）"
        echo "  down        - 停止主服务器服务"
        echo "  down-assist - 停止辅助服务器服务"
        echo "  logs        - 查看实时日志"
        echo "  restart     - 重启主服务器服务"
        echo "  export      - 导出辅助服务器镜像包"
        echo "  all         - 完整流程：检查 + 构建镜像 + 启动主服务器（默认）"
        exit 1
        ;;
esac
