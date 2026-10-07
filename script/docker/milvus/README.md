# 本地 Milvus 向量存储

> 用途：IDE 运行 Nexus-ERP 时的本地开发、RAG 检索与演示排练；不是生产编排。
> 版本：`milvusdb/milvus:v2.5.27`，固定版本，不自动跟随 `latest`。

## 部署约定

- 采用官方单容器方式：内嵌 etcd，`COMMON_STORAGETYPE=local`。
- 不依赖或改动已有 MinIO，不启停 ERP、Redis、RabbitMQ 等服务。
- 向量、索引及 etcd 元数据全部挂载到部署目录的 `data/`；配置文件只读挂载。
- Milvus 地址：`http://127.0.0.1:19530`。
- 健康检查地址：`http://127.0.0.1:19531/healthz`；宿主 `9091` 留给已有 MinIO。
- 只绑定本机回环地址，未配置对外认证；不要直接开放到局域网或公网。
- 当前应用运行依赖包含 `milvus-sdk-java-2.5.9`。本模板的 2.5.27 已做实际接入验证，不表示完成全部 SDK 功能兼容验收。

## 首次部署

在仓库根目录执行以下 PowerShell 命令。默认部署到仓库的同级 `nexus-milvus` 目录，运行数据不放进 Git 工作区。

```powershell
$repo = (Get-Location).Path
$deployment = Join-Path (Split-Path -Parent $repo) 'nexus-milvus'
New-Item -ItemType Directory -Path $deployment -Force | Out-Null
foreach ($name in @('docker-compose.yml', 'embedEtcd.yaml', 'user.yaml')) {
    $target = Join-Path $deployment $name
    if (Test-Path -LiteralPath $target) {
        throw "已有部署配置，先核对，不自动覆盖：$target"
    }
    Copy-Item -LiteralPath (Join-Path $repo "script/docker/milvus/$name") -Destination $target
}
$compose = Join-Path $deployment 'docker-compose.yml'
docker compose --project-name nexus-milvus-local -f $compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Milvus配置校验失败，停止部署' }
docker compose --project-name nexus-milvus-local -f $compose pull
if ($LASTEXITCODE -ne 0) { throw 'Milvus镜像拉取失败，停止部署' }
docker compose --project-name nexus-milvus-local -f $compose up -d --wait --wait-timeout 180
if ($LASTEXITCODE -ne 0) { throw 'Milvus未通过启动健康检查，请查看本容器日志' }
```

**已有部署不要重新执行“首次部署”来覆盖配置。** 后续查询使用原部署目录：

```powershell
$deployment = Join-Path (Split-Path -Parent (Get-Location).Path) 'nexus-milvus'
$compose = Join-Path $deployment 'docker-compose.yml'
docker compose --project-name nexus-milvus-local -f $compose ps
Invoke-WebRequest -Uri 'http://127.0.0.1:19531/healthz' -UseBasicParsing
```

不要改用仓库模板目录执行 `up`，否则相对 `./data` 会指向另一目录；也不要删除、清空或覆盖既有 `data/`。停止容器不等于备份，运行数据备份另行处理。

## Nexus-ERP 接入

1. 在 AI 配置中设置向量存储类型 `milvus`、地址 `http://localhost:19530`、集合前缀 `AiKnowledge`。
2. 新 Embedding 模型为用户配置的 `qwen3.7-text-embedding`，通过正常模型连接测试确认实际返回维度。本次聚合平台返回 **1024**，向量存储维度与之保持一致。
3. 逐个知识库核对 Embedding 绑定；只修改默认模型不会自动改变旧库的绑定。
4. 在知识库更新时申请索引重建，等待 `READY / 100` 后再检索。不得只改模型配置而保留旧模型向量，也不得混用不同维度。
5. 在聊天或 RAG 节点显式选择知识库，分别验证实际命中、回答和 Flow 节点运行，不用一般知识回答冒充检索成功。

已接入的本地知识库集合是 `AiKnowledge_2065090090526457857`。其他知识库需分别核对来源、绑定及重建状态；本次不保证所有旧库已迁移。

## 参考与验收边界

- [Milvus 官方单容器部署文档](https://milvus.io/docs/v2.5.x/install_standalone-docker.md)
- [Milvus 2.5.27 官方发布记录](https://github.com/milvus-io/milvus/releases/tag/v2.5.27)

本地验收证明包括模型实际返回维度、18 条向量入库、容器重启后保留、5 片段真实检索和 RAG→LLM Flow 执行；它们不替代历史知识片段租户/文档归属对账、全量资料认证或生产安全验收。
