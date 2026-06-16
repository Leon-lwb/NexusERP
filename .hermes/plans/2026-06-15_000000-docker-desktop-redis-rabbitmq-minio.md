# Docker Desktop + Redis/RabbitMQ/MinIO 本地化部署计划

> **For Hermes:** 当前仅制定计划，不执行安装、下载、拉取镜像或启动容器；待用户确认后再执行。

**Goal:** 在 Windows 本机将 Docker Desktop 下载到 E 盘指定目录，并使用 Docker 容器替代手动启动的 Redis、RabbitMQ、MinIO，端口、账号、密码对齐项目当前配置。

**Architecture:** Docker Desktop 负责提供 Docker Engine；在项目内新增/使用本地开发专用 compose 文件编排 3 个基础服务。容器使用本机端口映射，Spring Boot 仍按 `localhost` 访问，无需改后端业务代码。

**Tech Stack:** Windows 10、Docker Desktop、Docker Compose、Redis、RabbitMQ Management、MinIO。

---

## 1. 已从项目配置识别出的服务参数

### Redis

来源：`ruoyi-admin/src/main/resources/application-dev.yml:63-71`、`docker-compose-main.yml:55-70`

- Host：`localhost`
- Port：`6379`
- Database：`0`
- Password：`ruoyi123`
- 建议镜像：`redis:7.2.8`

### RabbitMQ

来源：`ruoyi-admin/src/main/resources/application-dev.yml:227-235`、`docker/docker-compose-rabbitmq.yml:4-13`、`docker-compose-main.yml:86-100`

- Host：`localhost`
- AMQP Port：`5672`
- Management Port：`15672`
- Username：`nexus`
- Password：`nexus`
- Virtual Host：`/erp`
- 建议镜像：`rabbitmq:3.13-management-alpine`

### MinIO

来源：`script/sql/ry-vue.sql` 中 `sys_oss_config` 初始化记录，以及现有 Docker compose 示例。

发现存在三套 MinIO 凭据/端口示例：

1. 当前 `script/sql/ry-vue.sql` 的 `sys_oss_config` 默认记录：
   - `config_key=minio`
   - `access_key=minio`
   - `secret_key=minio123`
   - `bucket_name=ruoyi`
   - `endpoint=127.0.0.1:9005`
   - `is_https=N`
2. `script/docker/docker-compose.yml` 示例：
   - `MINIO_ROOT_USER=ruoyi`
   - `MINIO_ROOT_PASSWORD=<项目示例密码，计划中不展开>`
   - `9000/9001`
3. `docker-compose-main.yml` 生产/主机示例：
   - `MINIO_ROOT_USER=nexus`
   - `MINIO_ROOT_PASSWORD=<项目示例密码，计划中不展开>`
   - `9000/9001`

**本计划建议优先匹配当前数据库初始化配置：**

- API Endpoint 给后端访问：`127.0.0.1:9005`
- Console：建议映射到 `9006`，避免与 API 端口混淆
- Root/User：`minio`
- Root/Password：`minio123`
- Bucket：`ruoyi`
- 建议镜像：优先使用项目已有的稳定官方镜像 `minio/minio:RELEASE.2024-06-13T22-53-53Z`

> 待确认点：如果你当前数据库 `sys_oss_config` 不是 `127.0.0.1:9005 / minio / minio123`，执行前应改为你的数据库实际值，或将容器端口/账号改成数据库实际值。

---

## 2. 计划创建/使用的目录

### Docker Desktop 下载与安装文件

- 下载目录：`E:\DockerDesktop\installer\`
- 安装目录候选：`E:\DockerDesktop\app\`

说明：Docker Desktop 在 Windows 上仍可能创建系统服务、WSL/Hyper-V 组件以及部分系统级数据目录；即使指定 E 盘安装目录，也可能有少量文件位于 `C:\Program Files`、`C:\ProgramData` 或用户目录。执行时会优先尝试官方静默安装参数指定 E 盘。

### 容器数据目录

建议避免复用你现有单独服务目录，防止 Docker 容器和本地服务同时写同一份数据导致损坏。

- Redis：`E:\Nexus\docker-data\redis\data\`
- RabbitMQ：`E:\Nexus\docker-data\rabbitmq\data\`
- MinIO：`E:\Nexus\docker-data\minio\data\`

### Compose 文件

计划创建：

- `E:\Nexus\nexus-erp\docker-compose.local-infra.yml`

该文件只包含 Redis、RabbitMQ、MinIO，不包含 MySQL、Nginx、后端服务。

---

## 3. 拟写入的 compose 内容

执行阶段会创建 `docker-compose.local-infra.yml`，内容预计如下：

```yaml
name: nexus-local-infra

services:
  redis:
    image: redis:7.2.8
    container_name: nexus-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    command: ["redis-server", "--requirepass", "ruoyi123", "--appendonly", "yes"]
    volumes:
      - E:/Nexus/docker-data/redis/data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "ruoyi123", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: nexus-rabbitmq
    restart: unless-stopped
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      TZ: Asia/Shanghai
      RABBITMQ_DEFAULT_USER: nexus
      RABBITMQ_DEFAULT_PASS: nexus
      RABBITMQ_DEFAULT_VHOST: /erp
    volumes:
      - E:/Nexus/docker-data/rabbitmq/data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 30s

  minio:
    image: minio/minio:RELEASE.2024-06-13T22-53-53Z
    container_name: nexus-minio
    restart: unless-stopped
    ports:
      - "9005:9000"
      - "9006:9001"
    environment:
      TZ: Asia/Shanghai
      MINIO_ROOT_USER: minio
      MINIO_ROOT_PASSWORD: minio123
    command: server /data --address ":9000" --console-address ":9001"
    volumes:
      - E:/Nexus/docker-data/minio/data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 10s
      retries: 3
```

---

## 4. 执行步骤（待确认后执行）

### Task 1：确认本机环境和端口占用

**Objective:** 确认 Windows、Docker/WSL 状态、6379/5672/15672/9005/9006 是否被本地旧服务占用。

**Commands:**

```powershell
where.exe docker
where.exe wsl
netstat -ano | findstr ":6379 :5672 :15672 :9005 :9006"
```

**Expected:**

- 如 Docker 未安装，则继续安装 Docker Desktop。
- 如果端口已被 `D:\Redis\redis-windows-8.6.1`、`E:\RabbitMQ`、`E:\Minio` 的进程占用，需要先停止旧服务再启动容器。

### Task 2：下载 Docker Desktop 安装包到 E 盘

**Objective:** 将官方 Docker Desktop Windows 安装包下载到 `E:\DockerDesktop\installer\`。

**Commands:**

```powershell
New-Item -ItemType Directory -Force -Path "E:\DockerDesktop\installer"
Invoke-WebRequest -Uri "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe" -OutFile "E:\DockerDesktop\installer\Docker Desktop Installer.exe"
```

**Validation:**

```powershell
Get-Item "E:\DockerDesktop\installer\Docker Desktop Installer.exe"
```

### Task 3：安装 Docker Desktop 到 E 盘候选目录

**Objective:** 静默安装 Docker Desktop，并尽量指定安装目录到 E 盘。

**Commands:**

```powershell
Start-Process -FilePath "E:\DockerDesktop\installer\Docker Desktop Installer.exe" -ArgumentList "install --quiet --accept-license --installation-dir=E:\DockerDesktop\app" -Wait -Verb RunAs
```

**Validation:**

```powershell
docker --version
docker compose version
```

**Notes:**

- 可能需要管理员权限。
- 可能需要启用 WSL2/虚拟化并重启 Windows。
- 如果安装器参数不被当前版本支持，改用 Docker Desktop 官方安装器默认流程，但安装包仍保留在 E 盘。

### Task 4：启动 Docker Desktop / Docker Engine

**Objective:** 确保 Docker Engine 可用。

**Commands:**

```powershell
Start-Process "E:\DockerDesktop\app\Docker\Docker Desktop.exe"
docker info
```

**Expected:** `docker info` 能输出 Server 信息。

### Task 5：创建本地 compose 文件和数据目录

**Objective:** 创建 `docker-compose.local-infra.yml` 与容器数据目录。

**Files:**

- Create: `docker-compose.local-infra.yml`
- Create directories:
  - `E:\Nexus\docker-data\redis\data`
  - `E:\Nexus\docker-data\rabbitmq\data`
  - `E:\Nexus\docker-data\minio\data`

**Validation:** 文件存在且 YAML 可被 Docker Compose 解析。

```powershell
docker compose -f docker-compose.local-infra.yml config
```

### Task 6：拉取镜像

**Objective:** 拉取 Redis、RabbitMQ、MinIO 镜像。

**Commands:**

```powershell
docker pull redis:7.2.8
docker pull rabbitmq:3.13-management-alpine
docker pull minio/minio:RELEASE.2024-06-13T22-53-53Z
```

**Validation:**

```powershell
docker images redis:7.2.8
docker images rabbitmq:3.13-management-alpine
docker images minio/minio:RELEASE.2024-06-13T22-53-53Z
```

### Task 7：启动 3 个基础服务

**Objective:** 使用 Docker Compose 后台启动 Redis、RabbitMQ、MinIO。

**Commands:**

```powershell
docker compose -f docker-compose.local-infra.yml up -d
```

**Validation:**

```powershell
docker compose -f docker-compose.local-infra.yml ps
docker ps --filter "name=nexus-"
```

### Task 8：验证 Redis 可用

**Objective:** 验证密码、端口符合项目配置。

**Commands:**

```powershell
docker exec nexus-redis redis-cli -a ruoyi123 ping
```

**Expected:** `PONG`

### Task 9：验证 RabbitMQ 可用

**Objective:** 验证 RabbitMQ 账号、密码、vhost、管理后台可用。

**Commands:**

```powershell
docker exec nexus-rabbitmq rabbitmqctl list_vhosts
Invoke-WebRequest -Uri "http://localhost:15672/api/overview" -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("nexus:nexus")) }
```

**Expected:**

- vhost 列表包含 `/erp`
- Management API 返回 HTTP 200

### Task 10：验证 MinIO 可用，并创建 bucket

**Objective:** 验证 MinIO API/控制台，并创建项目需要的 `ruoyi` bucket。

**Commands:**

```powershell
docker exec nexus-minio mc alias set local http://localhost:9000 minio minio123
docker exec nexus-minio mc mb --ignore-existing local/ruoyi
docker exec nexus-minio mc ls local
Invoke-WebRequest -Uri "http://localhost:9005/minio/health/live"
```

**Expected:**

- `local/ruoyi` bucket 存在
- `http://localhost:9005/minio/health/live` 返回 HTTP 200
- 浏览器可打开控制台：`http://localhost:9006`，账号 `minio`，密码 `minio123`

### Task 11：给项目后端做一次连通性验证

**Objective:** 用当前项目配置启动后端或至少验证端口，确认三项基础设施可被访问。

**Commands:**

```powershell
mvn spring-boot:run -P local
```

或在 IDE 中运行 `ruoyi-admin/src/main/java/org/dromara/DromaraApplication.java`。

**Expected:** 后端启动日志中不再出现 Redis/RabbitMQ/MinIO 连接失败；RabbitMQ 初始化成功；上传文件时能访问 MinIO。

---

## 5. 风险、取舍与待确认问题

1. **Docker Desktop 安装目录限制**：Windows 版 Docker Desktop 可能无法完全安装到 E 盘，部分系统组件仍会在系统盘。
2. **端口冲突**：你当前单独启动的 Redis/RabbitMQ/MinIO 如果仍在运行，会占用 `6379/5672/15672/9005/9006`，容器启动会失败。执行前建议停止旧服务。
3. **MinIO 配置存在歧义**：项目 compose 示例和 SQL 初始化记录不一致。本计划按 `sys_oss_config` 当前记录使用 `9005 + minio/minio123`。请确认这是否是你当前本地数据库实际配置。
4. **数据隔离**：计划使用新的 Docker 数据目录，不直接复用 `D:\Redis\redis-windows-8.6.1`、`E:\RabbitMQ`、`E:\Minio`，以降低数据损坏风险。
5. **是否保留旧服务**：旧服务目录不会删除；只是在端口上由 Docker 容器接管。

---

## 6. 用户确认后我将执行的具体产物

- 下载 Docker Desktop 安装包：`E:\DockerDesktop\installer\Docker Desktop Installer.exe`
- 尝试安装 Docker Desktop 到：`E:\DockerDesktop\app\`
- 新增 compose 文件：`E:\Nexus\nexus-erp\docker-compose.local-infra.yml`
- 拉取镜像：
  - `redis:7.2.8`
  - `rabbitmq:3.13-management-alpine`
  - `minio/minio:RELEASE.2024-06-13T22-53-53Z`
- 启动容器：
  - `nexus-redis`
  - `nexus-rabbitmq`
  - `nexus-minio`
- 完成 Redis/RabbitMQ/MinIO 的实际连通性验证，并报告真实命令输出。
