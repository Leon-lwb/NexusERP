# NexusERP Docker 双机部署方案

> **服务器信息**
> - 主服务器：`81.68.78.136`（2核2G）—— 部署 MySQL、Redis、RabbitMQ、MinIO、Nginx+前端、NexusServer
> - 辅助服务器：`117.72.190.225`（2核2G）—— 部署 MonitorAdmin、SnailJobServer
> - 域名：`nexuserp.website`

---

## 目录

- [一、部署架构](#一部署架构)
- [二、环境要求](#二环境要求)
- [三、前置准备](#三前置准备)
- [四、安装 Docker](#四安装-docker)
- [五、打包与上传](#五打包与上传)
- [六、配置文件准备](#六配置文件准备)
- [七、主服务器部署](#七主服务器部署)
- [八、辅助服务器部署](#八辅助服务器部署)
- [九、防火墙与网络配置](#九防火墙与网络配置)
- [十、MySQL 远程访问授权](#十mysql-远程访问授权)
- [十一、访问地址汇总](#十一访问地址汇总)
- [十二、服务更新指南](#十二服务更新指南)
- [十三、常用运维命令](#十三常用运维命令)
- [十四、部署实战问题汇总](#十四部署实战问题汇总)
- [十五、配置文件说明](#十五配置文件说明)

---

## 一、部署架构

```
+---------------------------------------------------+
|  用户浏览器                                        |
|  域名: nexuserp.website / IP: 81.68.78.136        |
+---------------------------------------------------+
                      |
                      v
+---------------------------------------------------+
|  主服务器  81.68.78.136 (2C2G)                     |
|  +---------------------------------------------+  |
|  | Nginx:80 (前端 + 反向代理)                   |  |
|  |   /prod-api/   -> NexusServer:8080          |  |
|  |   /admin/      -> MonitorAdmin:9090 (代理)   |  |
|  |   /snail-job/  -> SnailJob:8800 (代理)       |  |
|  +---------------------------------------------+  |
|  +----------------+  +-------------------------+  |
|  | NexusServer    |  | MySQL:3306              |  |
|  |   port: 8080   |  |   系统库: ry-vue        |  |
|  |   JVM: 384M    |  |   业务库: ry-erp        |  |
|  +----------------+  +-------------------------+  |
|  +----------------+  +-------------------------+  |
|  | Redis:6379     |  | RabbitMQ:5672/15672     |  |
|  |   password     |  |   user/pass: nexus      |  |
|  +----------------+  +-------------------------+  |
|  +----------------+  +-------------------------+  |
|  | MinIO:9000/9001|  |                           |  |
|  |   user/pass    |  |                           |  |
|  |   nexus/       |  |                           |  |
|  |   nexus123456  |  |                           |  |
|  +----------------+  +-------------------------+  |
+---------------------------------------------------+
                      |
      MySQL JDBC      |   SnailJob Client
      +---------------+---------------+
      |                               |
      v                               v
+---------------------------------------------------+
|  辅助服务器  117.72.190.225 (2C2G)                  |
|  +----------------+  +-------------------------+  |
|  | MonitorAdmin   |  | SnailJobServer          |  |
|  |   port: 9090   |  |   port: 8800/17888      |  |
|  |   JVM: 192M    |  |   JVM: 192M             |  |
|  +----------------+  +-------------------------+  |
+---------------------------------------------------+
```

---

## 二、环境要求

| 组件 | 版本要求 |
|------|---------|
| Docker | 24.x 或更高 |
| Docker Compose | 2.x 或更高 |
| JDK | OpenJDK 17 |
| Maven | 3.9.x |
| 操作系统 | Ubuntu 20.04+ / CentOS 7+ / OpenCloudOS / Rocky Linux |

---

## 三、前置准备

### 3.1 配置 Swap（2G 内存必须）

两台服务器均需执行：

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 3.2 停用旧服务（主服务器）

如果之前手动部署过 MySQL、Redis、Nginx，先停止并禁用：

```bash
# Ubuntu/Debian
sudo systemctl stop mysql    && sudo systemctl disable mysql
sudo systemctl stop redis    && sudo systemctl disable redis
sudo systemctl stop nginx    && sudo systemctl disable nginx

# 如果 systemctl 无效，直接 kill 进程
sudo ss -tlnp | grep 3306
sudo kill <pid>
```

### 3.3 确认端口未被占用

```bash
sudo ss -tlnp | grep -E '3306|6379|5672|15672|9000|9001|80|8080|9090|8800|17888'
```

---

## 四、安装 Docker

根据服务器操作系统选择对应的安装方式：

### 方式 A：Ubuntu / Debian 系统（国内镜像源）

```bash
# 1. 卸载旧版本
sudo apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null

# 2. 安装依赖
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

# 3. 添加阿里云 Docker GPG 密钥
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 4. 使用阿里云镜像仓库
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 5. 安装 Docker Engine
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 6. 启动并设置开机自启
sudo systemctl start docker
sudo systemctl enable docker
```

### 方式 B：CentOS / RHEL / OpenCloudOS / Rocky Linux 系统

```bash
# 1. 卸载旧版本
sudo yum remove -y docker docker-engine docker.io containerd runc 2>/dev/null

# 2. 安装依赖
sudo yum install -y yum-utils

# 3. 使用阿里云 Docker 镜像仓库
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# 4. 安装 Docker Engine
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. 启动并设置开机自启
sudo systemctl start docker
sudo systemctl enable docker
```

### 配置 Docker 镜像加速（必须）

> **重要**：不配置镜像加速会导致拉取镜像超时失败！

使用 vim 编辑 `/etc/docker/daemon.json`：

```bash
sudo vim /etc/docker/daemon.json
```

粘贴以下内容（一行）：
```json
{"registry-mirrors":["https://mirror.ccs.tencentyun.com","https://registry.docker-cn.com","https://docker.m.daocloud.io"]}
```

重启 Docker：
```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
```

> **提示**：可通过 `cat /etc/os-release` 命令查看服务器操作系统类型。

---

## 五、打包与上传

### 5.1 本地打包

在 Windows 本地执行：

```powershell
# 进入项目目录
cd E:\Nexus\nexus-erp

# 打包后端（3个 jar）
mvn clean package -DskipTests -Pprod

# 打包前端
cd plus-ui
npm run build:prod
cd ..
```

### 5.2 上传到主服务器 (81.68.78.136)

```powershell
# 上传后端配置文件
scp docker-compose-main.yml root@81.68.78.136:/nexus/nexus-erp/
scp docker-compose-assist.yml root@81.68.78.136:/nexus/nexus-erp/
scp -r script/ root@81.68.78.136:/nexus/nexus-erp/script/
scp ruoyi-admin/src/main/resources/application-prod.yml root@81.68.78.136:/nexus/nexus-erp/ruoyi-admin/src/main/resources/

# 上传 Dockerfile
scp ruoyi-admin/Dockerfile root@81.68.78.136:/nexus/nexus-erp/ruoyi-admin/
scp ruoyi-extend/ruoyi-monitor-admin/Dockerfile root@81.68.78.136:/nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/
scp ruoyi-extend/ruoyi-snailjob-server/Dockerfile root@81.68.78.136:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/

# 上传 jar 文件
scp ruoyi-admin/target/ruoyi-admin.jar root@81.68.78.136:/nexus/nexus-erp/ruoyi-admin/target/
scp ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar root@81.68.78.136:/nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/target/
scp ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar root@81.68.78.136:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/target/

# 上传前端文件
scp plus-ui/Dockerfile plus-ui/nginx.conf plus-ui/.dockerignore root@81.68.78.136:/nexus/nexus-ui/
scp -r plus-ui/dist root@81.68.78.136:/nexus/nexus-ui/
```

### 5.3 上传到辅助服务器 (117.72.190.225)

```powershell
# 上传 docker-compose
scp docker-compose-assist.yml root@117.72.190.225:/nexus/

# 上传 Dockerfile
scp ruoyi-extend/ruoyi-monitor-admin/Dockerfile root@117.72.190.225:/nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/
scp ruoyi-extend/ruoyi-snailjob-server/Dockerfile root@117.72.190.225:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/

# 上传 jar 文件
scp ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar root@117.72.190.225:/nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/target/
scp ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar root@117.72.190.225:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/target/

# 上传配置文件
scp ruoyi-extend/ruoyi-snailjob-server/src/main/resources/application-prod.yml root@117.72.190.225:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/src/main/resources/
```

---

## 六、配置文件准备

### 6.1 数据库密码配置

在 `application-prod.yml` 中配置数据库密码（明文或 Jasypt 加密）：

**方式 A：明文密码（简单，适合内网环境）**

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          username: root
          password: MIS@5mis@5  # 明文密码
        erp:
          username: root
          password: MIS@5mis@5  # 明文密码
```

**方式 B：Jasypt 加密（安全，适合生产环境）**

加密算法：`PBEWITHHMACSHA512ANDAES_256`

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          username: root
          password: ENC(IbQ7QDkGO3jJKpujQv/j+wRrg8BBZvYxiPx/BgweywjGzv8D4otQxGK+YyW1dRXk)
```

使用 Jasypt 加密时，需要在 `docker-compose-main.yml` 中配置密钥：
```yaml
environment:
  JASYPT_PASSWORD: "your-jasypt-key-here"
```

> **注意**：`docker-compose-main.yml` 中的 `MYSQL_ROOT_PASSWORD` 必须与 `application-prod.yml` 中的密码一致！

### 6.2 配置 application-secret.yml

在主服务器上创建：

```bash
mkdir -p /nexus/nexus-erp/ruoyi-admin/src/main/resources/

cat > /nexus/nexus-erp/ruoyi-admin/src/main/resources/application-secret.yml << 'EOF'
sms:
  config1:
    access-key-id: ""
    access-key-secret: ""
    signature: ""
    sdk-app-id: ""

mail:
  from: ""
  user: ""
  pass: ""

pay:
  alipay:
    app-id: ""
    app-private-key: ""
    alipay-public-key: ""

justauth:
  gitee:
    client-id: ""
    client-secret: ""
EOF
```

### 6.3 配置 SSL 证书（可选）

参考 nginx.conf 中的 HTTPS 配置说明。

---

## 七、主服务器部署

### 7.1 创建目录结构

```bash
# 创建必要的目录
mkdir -p /nexus/nexus-erp/ruoyi-admin/target/
mkdir -p /nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/target/
mkdir -p /nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/target/
mkdir -p /nexus/nexus-erp/script/sql/
mkdir -p /nexus/nexus-erp/script/docker/redis/conf/
mkdir -p /nexus/nexus-erp/ruoyi-admin/src/main/resources/
```

### 7.2 执行部署

```bash
cd /nexus/nexus-erp
chmod +x script/deploy.sh

# 构建镜像（使用已上传的 jar 文件）
./script/deploy.sh images

# 启动服务
./script/deploy.sh up
```

### 7.3 验证服务

```bash
# 查看容器状态
docker ps

# 测试后端 API
curl http://localhost:8080/system/user/getInfo

# 访问前端
http://81.68.78.136
```

---

## 八、辅助服务器部署

### 8.1 创建目录

```bash
ssh root@117.72.190.225

mkdir -p /nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/target/
mkdir -p /nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/target/
mkdir -p /nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/src/main/resources/
mkdir -p /nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/src/main/resources/
mkdir -p /docker/monitor/logs
mkdir -p /docker/snailjob/logs
```

### 8.2 构建并启动

```bash
# 构建镜像
cd /nexus/nexus-erp
docker build -t nexus/nexus-monitor:5.6.0 ./ruoyi-extend/ruoyi-monitor-admin
docker build -t nexus/nexus-snailjob:5.6.0 ./ruoyi-extend/ruoyi-snailjob-server

# 启动服务
cd /nexus
docker compose -f docker-compose-assist.yml up -d

# 查看状态
docker ps
```

### 8.3 验证服务

| 服务 | 访问地址 |
|------|---------|
| MonitorAdmin | http://117.72.190.225:9090/admin |
| SnailJob | http://117.72.190.225:8800/snail-job |

---

## 九、防火墙与网络配置

### 9.1 主服务器开放端口

```bash
sudo ufw allow 3306     # MySQL（供辅助服务器连接）
sudo ufw allow 80       # Nginx HTTP
sudo ufw allow 8080     # NexusServer API
sudo ufw allow 5672     # RabbitMQ AMQP
sudo ufw allow 15672    # RabbitMQ 管理后台
sudo ufw allow 9000     # MinIO API
sudo ufw allow 9001     # MinIO 控制台
```

### 9.2 辅助服务器开放端口

```bash
sudo ufw allow 9090     # MonitorAdmin
sudo ufw allow 8800     # SnailJob Web
sudo ufw allow 17888    # SnailJob Server RPC
```

### 9.3 云服务器安全组

如果使用云服务器（如腾讯云、阿里云），还需在**安全组/防火墙**控制台放通上述端口。

---

## 十、MySQL 远程访问授权

主服务器 MySQL 启动后，执行以下命令允许辅助服务器连接：

```bash
docker exec -it nexus-mysql mysql -uroot -pMIS@5mis@5 -e "
  CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED WITH mysql_native_password BY 'MIS@5mis@5';
  GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
  FLUSH PRIVILEGES;
"
```

---

## 十一、访问地址汇总

| 服务 | 访问地址 | 说明 |
|------|---------|------|
| 前端页面 | `http://nexuserp.website` | 通过 Nginx 80 端口 |
| 后端 API | `http://81.68.78.136:8080` | Swagger 接口文档 |
| 监控中心 | `http://nexuserp.website/admin/` | Nginx 代理到辅助服务器 |
| SnailJob 控制台 | `http://nexuserp.website/snail-job/` | Nginx 代理到辅助服务器 |
| RabbitMQ 管理 | `http://81.68.78.136:15672` | 默认账号 `nexus` / `nexus` |
| MinIO 控制台 | `http://81.68.78.136:9001` | 账号 `nexus` / `nexus123456` |
| MinIO API | `http://81.68.78.136:9000` | S3 兼容 API |

---

## 十二、服务更新指南

### 12.1 更新后端服务

**本地打包并上传：**

```powershell
# 本地 PowerShell
cd E:\Nexus\nexus-erp

# 重新打包
mvn clean package -DskipTests -Pprod

# 上传 jar 文件
scp ruoyi-admin/target/ruoyi-admin.jar root@81.68.78.136:/nexus/nexus-erp/ruoyi-admin/target/

# 如果修改了配置文件，也需要上传
scp ruoyi-admin/src/main/resources/application-prod.yml root@81.68.78.136:/nexus/nexus-erp/ruoyi-admin/src/main/resources/
```

**主服务器执行：**

```bash
cd /nexus/nexus-erp

# 重新构建镜像
docker build -t nexus/nexus-server:5.6.0 ./ruoyi-admin

# 重启服务
docker restart nexus-server

# 查看日志
docker logs -f nexus-server
```

### 12.2 更新前端服务

**本地打包并上传：**

```powershell
# 本地 PowerShell
cd E:\Nexus\nexus-erp\plus-ui

# 打包前端
npm run build:prod

# 上传 dist 目录
scp -r dist root@81.68.78.136:/nexus/nexus-ui/

# 如果修改了 nginx.conf，也需要上传
scp nginx.conf root@81.68.78.136:/nexus/nexus-ui/
```

**主服务器执行：**

```bash
# 重新构建镜像
docker build -t nexus/nexus-ui:5.6.0 /nexus/nexus-ui/

# 重启 nginx
docker restart nexus-nginx
```

### 12.3 更新辅助服务器

**本地打包并上传：**

```powershell
# 本地 PowerShell
cd E:\Nexus\nexus-erp

# 重新打包
mvn clean package -DskipTests -Pprod

# 上传 jar 文件
scp ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar root@117.72.190.225:/nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/target/
scp ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar root@117.72.190.225:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/target/
```

**辅助服务器执行：**

```bash
cd /nexus/nexus-erp

# 重新构建镜像
docker build -t nexus/nexus-monitor:5.6.0 ./ruoyi-extend/ruoyi-monitor-admin
docker build -t nexus/nexus-snailjob:5.6.0 ./ruoyi-extend/ruoyi-snailjob-server

# 重启服务
docker restart nexus-monitor
docker restart nexus-snailjob
```

### 12.4 一键更新脚本

在主服务器创建更新脚本：

```bash
vim /nexus/nexus-erp/script/update.sh
```

内容：

```bash
#!/bin/bash
set -e

VERSION="5.6.0"
PROJECT_ROOT="/nexus/nexus-erp"
FRONTEND_ROOT="/nexus/nexus-ui"

echo "=== 更新后端服务 ==="
docker build -t nexus/nexus-server:${VERSION} ${PROJECT_ROOT}/ruoyi-admin
docker restart nexus-server
echo "✓ 后端服务已更新"

if [ "$1" = "all" ]; then
    echo ""
    echo "=== 更新前端服务 ==="
    docker build -t nexus/nexus-ui:${VERSION} ${FRONTEND_ROOT}
    docker restart nexus-nginx
    echo "✓ 前端服务已更新"
fi

echo ""
echo "=== 查看状态 ==="
docker ps --format "table {{.Names}}\t{{.Status}}"

echo ""
echo "=== 后端日志（最后10行）==="
docker logs nexus-server --tail 10
```

使用方法：

```bash
chmod +x /nexus/nexus-erp/script/update.sh

# 只更新后端
/nexus/nexus-erp/script/update.sh

# 更新后端+前端
/nexus/nexus-erp/script/update.sh all
```

---

## 十三、常用运维命令

### 查看容器状态
```bash
docker ps
docker stats
```

### 查看日志
```bash
# 主服务器
docker logs -f nexus-server
docker logs -f nexus-mysql
docker logs -f nexus-nginx

# 辅助服务器
docker logs -f nexus-monitor
docker logs -f nexus-snailjob
```

### 重启单个服务
```bash
docker restart nexus-server
```

### 进入容器调试
```bash
docker exec -it nexus-server sh
```

### 停止所有服务
```bash
# 主服务器
cd /nexus/nexus-erp
./script/deploy.sh down

# 辅助服务器
cd /nexus
docker compose -f docker-compose-assist.yml down
```

---

## 十四、部署实战问题汇总

### 问题 1：heredoc (EOF) 命令卡死

**现象**：执行 `cat > file << 'EOF'` 或 `sudo tee` 命令时终端卡死。

**解决方案**：使用 vim 编辑文件，或者用 echo 逐行写入。

### 问题 2：Docker 镜像拉取超时

**现象**：`ERROR: failed to solve: failed to resolve source metadata for docker.io/...`

**原因**：国内网络无法访问 Docker 官方镜像源。

**解决方案**：配置 Docker 镜像加速器（见第四章）。

### 问题 3：MySQL 启动失败 - query-cache-size 错误

**现象**：`unknown variable 'query-cache-size=0'`

**原因**：MySQL 8.0 已移除查询缓存功能。

**解决方案**：从 `docker-compose-main.yml` 的 MySQL command 中移除 `--query-cache-size=0`。

### 问题 4：MySQL 启动失败 - 数据目录损坏

**现象**：`Table 'mysql.plugin' doesn't exist`

**解决方案**：
```bash
# 停止服务
./script/deploy.sh down

# 清空数据目录
sudo rm -rf /docker/mysql/data/*

# 重新启动
./script/deploy.sh up
```

### 问题 5：Redis 启动失败 - 权限问题

**现象**：`Can't open or create append-only dir appendonlydir: Permission denied`

**解决方案**：
```bash
# 修复权限
sudo chmod -R 777 /docker/redis/data

# 或者禁用 AOF（编辑 redis.conf，删除 appendonly yes 相关配置）
```

### 问题 6：NexusServer 启动失败 - 密码错误

**现象**：`Access denied for user 'root'@'127.0.0.1' (using password: YES)`

**原因**：`application-prod.yml` 中的密码与 `docker-compose-main.yml` 中的 `MYSQL_ROOT_PASSWORD` 不一致。

**解决方案**：确保两者密码相同。如果使用 Jasypt 加密，需要配置 `JASYPT_PASSWORD` 环境变量。

### 问题 7：NexusServer 启动失败 - Metaspace 内存不足

**现象**：`java.lang.OutOfMemoryError: Metaspace`

**解决方案**：增加 `JAVA_OPTS` 中的 `MaxMetaspaceSize`：
```yaml
JAVA_OPTS: "-Xms256m -Xmx384m -XX:MaxMetaspaceSize=256m -XX:+UseZGC"
```

### 问题 8：NexusServer 启动失败 - gRPC 依赖缺失

**现象**：`java.lang.NoClassDefFoundError: io/grpc/InternalGlobalInterceptors`

**解决方案**：临时禁用 SnailJob（在 `application-prod.yml` 中设置 `snail-job.enabled: false`），然后重新打包。

### 问题 9：容器 unhealthy

**现象**：服务日志显示启动成功，但 Docker 显示 unhealthy。

**原因**：healthcheck 配置问题（容器内没有 curl 或 start_period 太短）。

**解决方案**：禁用 healthcheck 或增加 start_period：
```yaml
# healthcheck:
#   test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
#   interval: 30s
#   timeout: 10s
#   retries: 10
#   start_period: 180s
```

### 问题 10：前端 API 返回 404

**现象**：前端页面能访问，但 API 请求返回 404。

**原因**：Nginx 配置没有生效（nginx.conf 打包在镜像中）。

**解决方案**：重新构建前端镜像：
```bash
docker build -t nexus/nexus-ui:5.6.0 /nexus/nexus-ui/
docker restart nexus-nginx
```

---

## 十五、配置文件说明

| 文件 | 作用 | 所在服务器 |
|------|------|-----------|
| `docker-compose-main.yml` | 主服务器所有服务的编排配置 | 主服务器 |
| `docker-compose-assist.yml` | 辅助服务器服务的编排配置 | 辅助服务器 |
| `script/deploy.sh` | 一键构建/部署脚本 | 主服务器 |
| `script/update.sh` | 一键更新脚本 | 主服务器 |
| `plus-ui/nginx.conf` | Nginx 反向代理配置（含域名、SSL 预留） | 主服务器 |
| `plus-ui/Dockerfile` | 前端镜像构建 | 主服务器 |
| `plus-ui/.dockerignore` | 前端 Docker 构建忽略文件 | 主服务器 |
| `ruoyi-admin/Dockerfile` | 主后端服务镜像构建 | 主服务器 |
| `ruoyi-extend/ruoyi-monitor-admin/Dockerfile` | 监控中心镜像构建 | 辅助服务器 |
| `ruoyi-extend/ruoyi-snailjob-server/Dockerfile` | SnailJob 镜像构建 | 辅助服务器 |
| `ruoyi-admin/src/main/resources/application-prod.yml` | 主服务正式环境配置 | 主服务器 |
| `ruoyi-admin/src/main/resources/application-secret.yml` | 敏感配置（短信、支付、第三方登录） | 主服务器 |
| `ruoyi-extend/ruoyi-snailjob-server/src/main/resources/application-prod.yml` | SnailJob 正式环境配置 | 辅助服务器 |
| `script/docker/redis/conf/redis.conf` | Redis 配置文件 | 主服务器 |
| `script/sql/ry-vue.sql` | MySQL 系统库初始化脚本 | 主服务器 |
| `script/sql/ry-erp.sql` | MySQL 业务库初始化脚本（含 CREATE DATABASE） | 主服务器 |

---

*文档版本：v2.0*
*更新日期：2026-06-12*
*更新内容：添加服务更新指南、添加部署实战问题汇总、完善部署步骤*
