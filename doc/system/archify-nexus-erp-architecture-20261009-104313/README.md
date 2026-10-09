# Nexus-ERP 系统总体架构图 — PDA / Milvus 修订版

日期：2026-10-09（Asia/Shanghai）。类型：`architecture`，中文逻辑总览。

本版另存为新文件夹，前一版 `archify-nexus-erp-architecture-20261009-101409` 保持原样。

## 打开

用桌面浏览器直接打开 `nexus-erp-architecture.html`，无需启动后端。
可切换深浅主题、查看来源信息、缩放；向下滚动查看下方数据设施及说明卡片。
`nexus-erp-architecture-preview.png` 是从 2048×1320 浅色截图裁去周围留白的预览，不是重新绘图或完整页面导出。

## 这次补充与更正

### PDA 是独立业务前端，不是 MQTT 设备

`E:\Nexus\nexus-pda` 是 uni-app + Vue 3 的手持作业工程，面向仓储、产线、质检与设备人员。
扫码输入、业务页面和 HTTP 请求在 PDA 项目内；后端契约在 NexusServer 内。
例如收货 `/app/pda/wm/receive/**`、生产报工 `/app/pda/mes/report/**`、质量检验 `/app/pda/mes/qc/**`。
请求封装注入 Bearer 与 clientid；后端接口进行权限校验，写操作采用 `PdaIdempotent`。
图中 PDA 与 PC 管理端同属业务前端，但与 MQTT 设备认证、Station 工位密钥通道分开。
图示不代表 Android PDA 真机或离线重放的人工验收已完成。

### 知识库的三个存储职责

| 存储 | 在图中的职责 | 代码依据 |
|---|---|---|
| MinIO / S3 | 知识库原始文件、业务文件与图片 | `KnowledgeProcessService` 150–154 行从 OSS 读取原文 |
| MySQL | 业务数据、知识文档与分块元数据 | `KnowledgeProcessService` 174–195 行保存分块并更新文档 |
| Milvus | 分块向量与关联文本/元数据、相似检索 | `VectorStoreService` 242–266 行写入；424–437 行检索 |

Embedding 模型负责生成向量，后端负责将向量写入或查询 Milvus；图中不画成 LLM 直接管理数据库。
Milvus Collection 名称按 knowledgeId 隔离。知识库选择优先于全局向量后端配置；代码同时保留 `inmemory` 模式，全局代码默认值为 `inmemory`。
因此关系标注为 **milvus 模式读写**，不把配置能力当成已核实的线上部署事实。
根双机 Compose 未声明 Milvus，本次未推断其主机归属或查询线上运行配置。

## 证据基线与多仓库边界

- Nexus-ERP：`515206db702a52ec1fb4ee3e167af386a610a915`。
- Nexus-PDA：`6f75c4891e696bf7b9b1487020d1ddaea3d9f9a8`。
- Archify 当前来源结构只绑定一个仓库；`candidate.json` 的 35 条引用验证 ERP 代码，PDA 节点内的引用明确标为后端契约。
- PDA 客户端实现另在 `pda-evidence.json` 固定提交、范围、文件及片段哈希，8 条引用均来自 PDA 已提交字节，未伪装成 ERP 仓库文件。
- `repository-snapshot.json` 记录两个工作区状态；原有改动不用于固定提交的代码证据。

## 文件与校验

| 文件 | 用途 |
|---|---|
| `nexus-erp-architecture.html` | 最终交互图 |
| `nexus-erp-architecture-preview.png` | 裁去视口周围留白的浅色预览 |
| `candidate.json` | 最终可编辑图定义 |
| `candidate.before-readability.json` | 三列重排前的备份 |
| `handoff.json` | 规格/产物哈希、自动检查及复核状态 |
| `pda-evidence.json` | 第二仓库的客户端证据 |
| `revision-notes.json` | 前一版路径与哈希、补充范围 |
| `visual-review.json` | Agent 截图复核记录，非用户人工验收 |
| `preview-derivation.json` | 预览截图来源、裁切范围与哈希 |
| `visual-check/` | 四张主题/视口截图及捕获回执 |

`finalize` 的 validate / deliver / check / browser-check 全部通过。
Showcase **9/9 检查，0 错误，0 警告**；最终产物已处理交叉数为 **0**。
Edge 浏览器检查覆盖 1440×900、1600×1000、1920×1080、2048×1320，无横向溢出。
已查看 1440×900 浅色及 2048×1320 浅色/深色截图；小视口需要正常页面纵向滚动。
模型调用 / S3 路线仍保留 4 / 3 个弯折，端点和标签可区分，不宣称全部直线。

## 复核命令

工作目录为 `E:\Nexus\nexus-erp`。

```powershell
$env:ARCHIFY_CHROME = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'doc/system/archify-nexus-erp-architecture-20261009-104313/candidate.json' 'doc/system/archify-nexus-erp-architecture-20261009-104313/nexus-erp-architecture.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --json
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' visual-check 'doc/system/archify-nexus-erp-architecture-20261009-104313/nexus-erp-architecture.html' --summary --require-provenance --out-dir 'doc/system/archify-nexus-erp-architecture-20261009-104313/visual-check'
```

后续修改图定义时选择新的 `--out-dir` 完整重跑 finalize，再捕获和复核，不复用旧截图代替新证据。
本次只新增本图目录；没有改两个项目的业务代码、配置或数据库，没有调用真实设备/模型/向量服务，没有提交或推送。
没有运行应用 Maven/前端构建，因此不存在应用测试或生产验收通过的声明。
