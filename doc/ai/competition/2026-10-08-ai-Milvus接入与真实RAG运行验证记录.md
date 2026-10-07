# Milvus 接入与真实 RAG 运行验证记录

> 创建日期：2026-10-08；运行执行主要发生于 2026-10-07 晚，最终 API 回读于 2026-10-08 00:00。
> 环境：已获授权的本地开发测试环境，Docker Desktop Linux 引擎，IDE 启动的后端。
> 分支：`codex/full-flow-acceptance-20260928`；开始整理时 HEAD：`e28c5cc3ea65749bd1f0d8be84c6a88b70b9fb89`。
> 范围：用户指定聚合平台 Embedding 模型与 Milvus 本地持久化接入；不是全模块验收或生产部署。

## 一、本轮结论

✅ **Milvus 已部署，且新模型→向量存储→检索→RAG 聊天→普通 RAG/LLM Flow 已真实运行。**

🚧 **历史知识片段的租户与文档归属仍有差异，不能签完整来源认证。**

本轮没有 Java/Vue 业务源码修改，没有重跑上轮后端 7873 或前端 694 用例，不能把历史统计当成这次的新测试结果。

## 二、部署与应用绑定

| 项目 | 本地实测状态 |
| --- | --- |
| 镜像 | `milvusdb/milvus:v2.5.27` |
| 本地镜像内容摘要 | `sha256:ea3b924dfb2129fa2daca569eab657b4fcebc4baaed5706cf30d51e19d99b0c9` |
| 容器 / Compose 项目 | `nexus-milvus` / `nexus-milvus-local` |
| 存储方式 | 单容器、内嵌 etcd、local 存储；数据挂载于仓库同级 `nexus-milvus/data` |
| 应用连接地址 | `http://localhost:19530`，仅本机回环绑定 |
| 健康检查 | 宿主 `127.0.0.1:19531` → 容器 `9091`，HTTP 200，容器 `healthy` |
| 已有 MinIO | 不改配置；保留其原 `9091`，没有端口冲突 |
| 构建产物中的 Java SDK | `milvus-sdk-java-2.5.9` |
| 用户配置的模型名称 | `qwen3.7-text-embedding`；该名称按本地聚合平台配置记载，不据此宣称官方型号发布 |
| 实际模型连接测试 | 成功，返回 **1024 维**，447 ms |
| 知识库 | `ERP知识库`，ID `2065090090526457857` |
| 知识库模型与向量后端 | 已通过正常 PUT 绑定新模型及 `milvus`，申请索引重建 |
| 索引状态 | `READY`，进度 100；实际重建日志为 18/18 |
| 集合 | `AiKnowledge_2065090090526457857` |

没有把只切换“默认 Embedding”视为旧库已迁移；本轮明确更新了这个知识库的模型绑定。没有承诺其他旧库均已迁移。

可复用配置及操作说明见 [Milvus 本地部署模板](../../../script/docker/milvus/README.md)。仅提交 Compose、两份中文注释配置、数据忽略规则及说明；不提交向量、etcd 运行目录、Token、密码或 API Key，不修改根目录生产 Compose。

## 三、真实执行与验证

| 检查 | 本轮证据与结果 | 边界 |
| --- | --- | --- |
| 原生向量存储 | Milvus `describe` / `get_stats` / `query` 均 code 0；18 行，FloatVector 维度 1024，抽样向量长度 1024 | 不是只有配置字段写了 milvus；但行数与抽样不替代逐片段来源认证 |
| 持久化 | 专门重启新 Milvus 容器，前后均 18 行；健康 HTTP 200 | 只重启 Milvus，不重启 ERP；记录包含其他容器 ID 不变布尔值，未保留前后 ID 数组，不补签不存在的历史差分 |
| 实际知识检索 | 正常 `/ai/knowledge/retrieval` 返回业务 200、**5 个片段**，约 0.781 秒 | 没降相似度阈值；本轮收尾未重复调用模型或重跑检索 |
| RAG 聊天 | 显式指定知识库；正常会话 `2107859623115452417`，回答对应集成性、实时性、标准化与全局优化四理念 | 技术链路成功不等于资料来源/租户全部认证成功 |
| 普通 Flow | `ERP知识检索与解读`，Flow `2107859634050002945`；实例 `2107859634578485249` 为 `SUCCEEDED` | 是普通 RAG→LLM，不是工业决策采购审批/执行闭环 |
| Flow 两节点 | `knowledge_lookup` / RAG、`knowledge_explanation` / LLM 均 `SUCCEEDED`，retryCount 均 0 | 没有用反复重跑来制造通过率 |
| ID 完整性 | 回读配置内知识库 ID、模型 ID 为精确字符串 | 不将单次检查冒称全部前端雪花 ID 操作已验收 |
| 最终运行态回读 | 2026-10-07 23:57：镜像、容器、端口、绑定目录、18 行及 1024 维核对通过；2026-10-08 00:00：GET 确认新绑定、READY100 及 Flow 两节点成功 | 收尾仅只读核对，不重复业务写入、模型调用或服务重启 |
| 工作区保护 | 六项原有未提交改动的 SHA-256 与保护基线一致 | 不把这些用户改动纳入本次提交 |

配置验证命令（仓库根目录）：

```powershell
docker compose --project-name nexus-milvus-local -f ./script/docker/milvus/docker-compose.yml config --quiet
git diff --check
git diff --cached --check
```

仓库模板与实际部署 Compose 均解析成功。运行态核对为 7 个非零断言，加上 API 回读的 4 个非零断言；这是部署/运行验证，不是 Java/Vue 单测替代品。

## 四、未关闭问题与后续边界

### 4.1 历史知识片段来源、租户及文档关联

- 当前知识库的物理租户为 `000000`，历史片段行的物理租户为 `0`。
- 原生只读查询仍可见 **27 行**旧片段；正常 API 分块列表在重建后及最终回读均为 **0 行**；Milvus 集合为 **18 行**向量。
- 当前文档列表只显示一个旧文档，chunkCount 为 9；历史文档关联也需逐一核对。
- 保留原重建回读中的 `sourceUnchanged=false`、`passed=false`，不把技术运行成功改写为来源认证成功。
- 本轮没有直接 SQL 修改旧片段/文档租户，没有强行把 `0` 升级成 `000000`，也没有清空旧内容来消除差异。

**后续应先明确历史资料归属，再修复必要的实体映射、读视图或数据关联，并用正常导入资料复验；不能因查询命中就宣布来源和租户边界闭合。**

### 4.2 ERP F04 与原业务验收

本次部署不能覆盖此前 ERP 可信来源预览失败。单次 `/erp/ai/certified-source/preview` 仍为业务 500、25.484 秒，未重发。只读诊断确认：同一历史领料行涉及两个库存批次，原行的非空 `batch_id` 与其中一条关联流水的库存批次不等，认证校验拒绝。历史多批领料是否符合业务规则、原行批次为何固定及阶段耗时仍未闭合；未据此直接改数据或放宽校验。

新计划生成工单、新的工业决策分析/审批/采购执行、本轮正式单据执行对账、40 个独立业务来源及 SLA 尚未完成。不能将本报告称为全流程完成或竞赛全部验收通过。

## 五、证据位置

证据保存在本机、Git 忽略的 `.codex/full-flow-20260928/20261007-competition-rehearsal/`，仅列无秘密结果文件：

- `selected-embedding-connection-test-once.json`
- `knowledge-switch-model-and-rebuild-once.json`
- `knowledge-new-model-rebuild-readback.json`（保留来源核对失败状态）
- `milvus-native-storage-evidence.json`
- `milvus-persistence-after-restart.json`
- `retrieval-after-milvus-new-model-outcome.json`
- `chat-rag-new-model-milvus-readback.json`
- `normal-rag-llm-flow-execution-readback.json`
- `knowledge-current-source-counts.json`
- `milvus-final-runtime-check.json`
- `milvus-final-api-readback.json`
- `milvus-independent-verification.json`（独立只读审查：技术通过，归属/来源与 F04 边界未关闭）
- `f04-readonly-diagnosis.json`

私有认证会话、原始配置与业务备份不纳入公开报告或 Git。
