# NexusERP Docker 双机部署方案

> 文档核对：2026-09-28。**部署编排只从仓库根目录读取**：[主服务器 Compose](../../../docker-compose-main.yml)、[辅助服务器 Compose](../../../docker-compose-assist.yml)。原 `doc/system/deployment/docker/` 主服务器副本缺少根目录现有的安全环境变量，已撤销；不要从旧文档副本启动容器。以下主机、端口和命令为历史环境范例，实际执行前核对 `.env`、密钥、数据库版本与备份；本次文档整理未进行生产部署或验收。

> **服务器信息**
> - 主服务器：`159.75.14.226`（4核4G）—— 部署 MySQL 5.7、Redis、RabbitMQ、MinIO、Nginx+前端、NexusServer
> - 辅助服务器：`117.72.190.225`（2核2G）—— 部署 MonitorAdmin、SnailJobServer
> - 域名：`nexuserp.website`（已解析到 159.75.14.226）

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
|  域名: nexuserp.website / IP: 159.75.14.226        |
+---------------------------------------------------+
                      |
                      v
+---------------------------------------------------+
|  主服务器  159.75.14.226 (4C4G)                     |
|  +---------------------------------------------+  |
|  | Nginx:80 (前端 + 反向代理)                   |  |
|  |   /prod-api/   -> NexusServer:8080          |  |
|  |   /admin/      -> MonitorAdmin:9090 (代理)   |  |
|  |   /snail-job/  -> SnailJob:8800 (代理)       |  |
|  +---------------------------------------------+  |
|  +----------------+  +-------------------------+  |
|  | NexusServer    |  | MySQL 5.7:3306          |  |
|  |   port: 8080   |  |   系统库: ry-vue        |  |
|  |   JVM: 512M    |  |   业务库: ry-erp        |  |
|  | (snail-job:off)|  |                         |  |
|  +----------------+  +-------------------------+  |
|  +----------------+  +-------------------------+  |
|  | Redis:6379     |  | RabbitMQ:5672/15672     |  |
|  |   password     |  |   user/pass: nexus      |  |
|  |                |  |           / ******      |  |
|  +----------------+  +-------------------------+  |
|  +----------------+  +-------------------------+  |
|  | MinIO:9005/9001|  |                           |  |
|  |   user/pass    |  |                           |  |
|  |   minio/       |  |                           |  |
|  |   ******       |  |                           |  |
|  +----------------+  +-------------------------+  |
+---------------------------------------------------+
                      |
      MySQL JDBC      |   (SnailJob 调度中心在辅助服务器)
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

### 5.0 准备私有生产配置（2026-10-16更新）

主服务JAR只包含公共/开发/本地配置，不包含真实prod/secret及示例文件。首次部署从源码中的两个示例准备外部配置，**不要覆盖已有私有文件**：

```powershell
# 在仓库根目录执行；已有文件先对照新示例审核迁移
New-Item -ItemType Directory -Path config -Force | Out-Null
if (-not (Test-Path config/application-prod.yml)) {
    Copy-Item ruoyi-admin/src/main/resources/application-prod.example.yml config/application-prod.yml
}
if (-not (Test-Path config/application-secret.yml)) {
    Copy-Item ruoyi-admin/src/main/resources/application-secret.example.yml config/application-secret.yml
}
```

根目录Compose将 `./config/` 只读挂载到 `/ruoyi/server/config/`，显式选择 `prod`，并将该目录内的application-prod.yml设为必需的额外配置文件，缺失时立即拒绝启动。外部生产配置会覆盖JAR中的公共值；密钥文件按 `NEXUS_CONFIG_DIR` 可选导入。

| 必需变量 | 本仓库Compose传递方式 |
|----------|----------------------|
| `MYSQL_ROOT_PASSWORD` | 数据库容器密码，默认作为应用的 `PROD_DB_PASSWORD`；独立应用账户通过 `PROD_DB_USERNAME/PROD_DB_PASSWORD` 覆盖 |
| `PROD_REDIS_PASSWORD` | Redis服务及应用密码保持一致 |
| `RABBITMQ_DEFAULT_PASS` | 映射为应用的 `PROD_MQ_PASSWORD`；账号、vhost同样需要一致 |
| `SA_TOKEN_JWT_SECRET_KEY` | 独立的生产JWT签名密钥 |
| `JASYPT_PASSWORD` | 加密配置所用主密钥，不沿用开发默认值 |
| `API_RSA_PUBLIC_KEY` / `API_RSA_PRIVATE_KEY` | 按既有API加密协议准备并与前端匹配 |
| `MES_API_KEY` | 工位/设备通道生产密钥 |
| `MONITOR_PASSWORD` | Actuator Basic鉴权密码；监控客户端即使关闭，鉴权仍读取该配置 |

上述真实值通过受保护的服务器 `.env` 或等效部署环境传入，不写入文档、Git或构建日志。`.env`只供Compose插值，不会自动将全部变量传入容器；当前environment列出的变量才会传递。邮件、短信、支付或第三方登录密钥可填写在私有secret文件中，或显式扩展应用environment映射。

`PROD_DB_HOST/PORT/USERNAME/SSL`、Redis和MQ的地址/端口等支持Compose环境覆盖，默认适配现行host网络。示例默认请求数据库SSL，需要核对实际MySQL TLS能力；更改连接安全参数应明确审核。默认监控/调度/支付等可选集成关闭；现行Compose还通过JAVA_OPTS强制关闭SnailJob客户端，启用时须同时核对系统属性优先级。确需短信/第三方登录时使用 `NEXUS_PROFILES=prod,sms` / `prod,social` 并准备对应厂商凭证。

**旧文件迁移注意：** 原来源码目录的私有prod可能仍写着必需的 `classpath:application-secret.yml`。新JAR不再携带该文件，不能原样外置后假定可启动。请对照新示例保留个人连接与业务参数，将密钥导入迁移为示例中的可选外部路径，并审核必需凭证；本次代码不会改写现有私有文件。

系统初始化SQL已包含Flowable引擎表，生产示例关闭自动改表；既有环境仍需单独核对引擎结构与版本。配置样例、构建成功和隔离测试不代表生产连接、迁移或端到端业务验收完成。

直接运行JAR时，从包含config目录的工作目录执行，或显式指定路径，例如：

```bash
java -jar ruoyi-admin.jar --spring.profiles.active=prod --spring.config.additional-location=file:/etc/nexus/config/application-prod.yml --NEXUS_CONFIG_DIR=/etc/nexus/config
```

服务器上先审核配置，再执行后续镜像部署。配置文件与镜像分开交付，密钥变更通常无需重打JAR。


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

### 5.2 上传到主服务器 (159.75.14.226)

首次部署先完成7.1节的目录准备；上传私有文件前确认目标内容和覆盖授权。

```powershell
# 上传后端配置文件
ssh root@159.75.14.226 "mkdir -p /nexus/nexus-erp/config"
scp docker-compose-main.yml root@159.75.14.226:/nexus/nexus-erp/
scp docker-compose-assist.yml root@159.75.14.226:/nexus/nexus-erp/
scp -r script/ root@159.75.14.226:/nexus/nexus-erp/script/
# 私有配置单独交付到只读挂载的目录；先确认服务器目标文件及覆盖授权
scp config/application-prod.yml root@159.75.14.226:/nexus/nexus-erp/config/
scp config/application-secret.yml root@159.75.14.226:/nexus/nexus-erp/config/

# 上传 Dockerfile
scp ruoyi-admin/Dockerfile root@159.75.14.226:/nexus/nexus-erp/ruoyi-admin/
scp ruoyi-extend/ruoyi-monitor-admin/Dockerfile root@159.75.14.226:/nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/
scp ruoyi-extend/ruoyi-snailjob-server/Dockerfile root@159.75.14.226:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/

# 上传 jar 文件
scp ruoyi-admin/target/ruoyi-admin.jar root@159.75.14.226:/nexus/nexus-erp/ruoyi-admin/target/
scp ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar root@159.75.14.226:/nexus/nexus-erp/ruoyi-extend/ruoyi-monitor-admin/target/
scp ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar root@159.75.14.226:/nexus/nexus-erp/ruoyi-extend/ruoyi-snailjob-server/target/

# 上传前端文件
scp plus-ui/Dockerfile plus-ui/nginx.conf plus-ui/.dockerignore root@159.75.14.226:/nexus/nexus-ui/
scp -r plus-ui/dist root@159.75.14.226:/nexus/nexus-ui/
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

## 六、生产配置与凭证

### 6.1 数据源、基础依赖与必需环境变量

以5.0节的外部生产示例为基线，核对四个数据源分别指向系统、ERP、MES、IoT库，再准备Redis与RabbitMQ。数据库初始化/迁移必须按环境和授权执行；应用启动不会替代这些步骤。

真实值写在受保护的服务器 `.env` 或等效部署环境中。当前Compose将数据库容器的 `MYSQL_ROOT_PASSWORD` 默认映射为应用 `PROD_DB_PASSWORD`；使用独立应用账号时设置 `PROD_DB_USERNAME/PROD_DB_PASSWORD` 并预先授予对应业务权限，应用密码不必等于root密码。`PROD_REDIS_PASSWORD` 与Redis服务配置匹配，MQ用户名、密码与vhost也需一致。

必需的JWT/Jasypt/API/设备/运维凭证及传递方式见5.0节。不要把开发默认值或示例中的空值作为生产凭证；API加密密钥须与前端共同配置。旧私有配置使用ENC密文时，应保留相匹配的Jasypt主密钥，避免仅改密码后无法解密。

验证Compose语法时使用下列命令，避免将完整解析配置及真实凭证打印到终端：

```bash
docker compose --env-file .env -f docker-compose-main.yml config --quiet
```

### 6.2 可选集成密钥与私有文件

主服务从运行目录 `config/application-prod.yml` 加载生产契约，按需加载同目录 `application-secret.yml`。从两个example准备文件的方法见5.0节；源码目录内现存的私有prod/secret不作为新JAR的交付入口。

邮件、支付默认关闭；短信/第三方登录还需额外的sms/social profile。不开这些功能时，不需要填写对应厂商密钥。启用前填写私有secret中的匹配字段，或显式将环境变量传入应用容器；本地.env存在不代表所有变量都会进入Spring。

根目录 `config/application*.yml` 与 `.env` 均被Git忽略。服务器限制这些文件的访问权限，Compose以只读方式挂载。配置变更按发布流程重新创建/启动应用容器使其读取新值，不需要仅为修改外置配置重打JAR；代码变更仍需构建并发布新镜像。

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
mkdir -p /nexus/nexus-erp/config/
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
http://159.75.14.226
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
sudo ufw allow 9005     # MinIO API
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
docker exec -it nexus-mysql mysql -uroot -p<your-mysql-password> -e "
  CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED WITH mysql_native_password BY '<your-mysql-password>';
  GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
  FLUSH PRIVILEGES;
"
```

---

## 十一、访问地址汇总

| 服务 | 访问地址 | 说明 |
|------|---------|------|
| 前端页面 | `http://nexuserp.website` | 通过 Nginx 80 端口 |
| 后端 API | `http://159.75.14.226:8080` | Swagger 接口文档 |
| 监控中心 | `http://nexuserp.website/admin/` | Nginx 代理到辅助服务器 |
| SnailJob 控制台 | `http://nexuserp.website/snail-job/` | Nginx 代理到辅助服务器 |
| RabbitMQ 管理 | `http://159.75.14.226:15672` | 默认账号 `nexus` / `<见 .env>` |
| MinIO 控制台 | `http://159.75.14.226:9001` | 账号 `minio` / `<见 .env>` |
| MinIO API | `http://159.75.14.226:9005` | S3 兼容 API |

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
scp ruoyi-admin/target/ruoyi-admin.jar root@159.75.14.226:/nexus/nexus-erp/ruoyi-admin/target/

# 如果修改了配置文件，也需要上传
# 私有配置单独交付到只读挂载的目录；先确认服务器目标文件及覆盖授权
scp config/application-prod.yml root@159.75.14.226:/nexus/nexus-erp/config/
scp config/application-secret.yml root@159.75.14.226:/nexus/nexus-erp/config/
```

**主服务器执行：**

```bash
cd /nexus/nexus-erp

# 重新构建镜像
docker build -t nexus-erp:fix-headless ./ruoyi-admin

# 重建容器（docker restart 不会使用新镜像，必须 --force-recreate）
docker compose -f docker-compose-main.yml up -d --force-recreate nexus-server

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

# 打包 dist 目录（保留 dist/ 层级，与服务器 nginx root=/usr/share/nginx/html/dist 对齐）
tar -czf dist.tar.gz dist
scp dist.tar.gz root@159.75.14.226:/tmp/

# 如果修改了 nginx.conf，上传到 nginx 配置目录（volume 挂载）
scp nginx.conf root@159.75.14.226:/docker/nginx/conf/nginx.conf
```

**主服务器执行：**

```bash
# 前端静态文件通过 volume 挂载，nginx root 指向 /usr/share/nginx/html/dist
rm -rf /docker/nginx/html/dist
tar -xzf /tmp/dist.tar.gz -C /docker/nginx/html/
rm -f /tmp/dist.tar.gz

# 重新加载 nginx 配置
docker exec nexus-nginx nginx -s reload
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

# 重建容器（docker restart 不会使用新镜像，必须 --force-recreate）
docker compose -f docker-compose-assist.yml up -d --force-recreate nexus-monitor nexus-snailjob
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
docker compose -f ${PROJECT_ROOT}/docker-compose-main.yml up -d --force-recreate nexus-server
echo "✓ 后端服务已更新"

if [ "$1" = "all" ]; then
    echo ""
    echo "=== 更新前端服务 ==="
    # 前端静态文件通过 volume 挂载，nginx root 指向 /usr/share/nginx/html/dist
    # 前置：在 plus-ui 目录 `tar -czf dist.tar.gz dist` 并 scp 到 /tmp/dist.tar.gz
    rm -rf /docker/nginx/html/dist
    tar -xzf /tmp/dist.tar.gz -C /docker/nginx/html/
    rm -f /tmp/dist.tar.gz
    docker exec nexus-nginx nginx -s reload
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

**解决方案**：核对外部 `config/application-prod.yml` 的 `snail-job.enabled` 及Compose的JAVA_OPTS；临时关闭后按发布流程重新创建应用容器，不需要仅为外置配置变更重打JAR。

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

**原因**：Nginx 配置没有生效。新方案 nginx.conf 与 html 均通过 volume 挂载（非打包进镜像），常见于 `/docker/nginx/conf/nginx.conf` 未正确挂载或配置有语法错误。

**解决方案**：校验并重载配置（无需构建镜像）：
```bash
# 检查配置语法
docker exec nexus-nginx nginx -t
# 重新加载
docker exec nexus-nginx nginx -s reload
# 若 nginx.conf 改动未生效，确认 volume 挂载后强制重建容器
docker compose -f /nexus/nexus-erp/docker-compose-main.yml up -d --force-recreate nexus-nginx
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
| `config/application-prod.yml` | 主服务私有生产配置，独立于JAR并只读挂载 | 主服务器 |
| `config/application-secret.yml` | 私有可选集成密钥，不入Git/JAR | 主服务器 |
| `ruoyi-admin/src/main/resources/application-prod.example.yml` | 脱敏生产契约，首次准备配置时参考 | 源码/运维工作区 |
| `ruoyi-admin/src/main/resources/application-secret.example.yml` | 完整密钥结构示例，需实际填值且不会自动启用功能 | 源码/运维工作区 |
| `ruoyi-extend/ruoyi-snailjob-server/src/main/resources/application-prod.yml` | SnailJob 正式环境配置 | 辅助服务器 |
| `script/docker/redis/conf/redis.conf` | Redis 配置文件 | 主服务器 |
| `script/sql/ry-vue.sql` | MySQL 系统库初始化脚本 | 主服务器 |
| `script/sql/ry-erp.sql` | MySQL 业务库初始化脚本（含 CREATE DATABASE） | 主服务器 |

---

*文档版本：v2.2*
*更新日期：2026-10-16*
*本次更新：外置生产配置、脱敏示例、凭证注入、只读挂载及JAR分发边界。*

*历史更新（2026-07-26）：主服务器迁移至 159.75.14.226（4C4G / MySQL 5.7 / MinIO 9005-minio / RabbitMQ / JVM 512M）；修正 `docker restart` 不换镜像（改用 `--force-recreate`）与前端 volume 挂载两个部署坑；新增 optimized.yml 说明*
