# Nexus-ERP 系统总体架构图

- 日期：2026-10-09（Asia/Shanghai）
- 类型：Archify `architecture`，中文逻辑总览，默认静态展示。
- 代码基线：`515206db702a52ec1fb4ee3e167af386a610a915`。
- 范围：业务用户、PC 管理端、Nginx 静态目录部署、同进程 NexusServer、工业设备与工位、业务数据源、Redis、RabbitMQ、对象存储、模型与视觉服务。

## 打开与查看

直接用桌面浏览器打开本目录的 `nexus-erp-architecture.html`，无需启动 ERP 后端。
页面支持浅色/深色切换，图中保留来源文件和行号；向下滚动可阅读制造业务、AI/Flow、平台能力与运行边界说明卡片。
`nexus-erp-architecture-preview.png` 是 2048×1320 浅色浏览器预览，不是完整页面截图或已验证的导出格式。

## 文件说明

| 文件 | 用途 |
|---|---|
| `nexus-erp-architecture.html` | 最终交互式架构图 |
| `nexus-erp-architecture-preview.png` | 浅色预览图 |
| `candidate.json` | 可继续编辑的最终图定义 |
| `candidate.before-layout.json` | 一次布局调整前的定义备份 |
| `handoff.json` | 当前规格、产物哈希及验收结果 |
| `visual-review.json` | Agent 查看截图后的独立视觉复核记录 |
| `repository-snapshot.json` | 固定提交与工作区状态记录 |
| `review-2/` | **最终轮次**的完整校验、浏览器证据和四张主题/视口截图 |
| 根目录的旧 finalize/browser 记录及 `browser-edge/` | 先前轮次记录，不能用于当前 HTML 的通过声明 |

## 校验结果

- `finalize`：`validate / deliver / check / browser-check` 四个阶段均通过。
- Showcase：**9/9 检查，0 错误，0 警告**。
- 仓库证据：29 条来源引用，按固定提交的已提交字节验证；不使用其他工作区未提交文件充当证据。
- 浏览器：使用本机 Microsoft Edge 的 Chromium 内核；检查 1440×900、1600×1000、1920×1080、2048×1320，均无横向溢出。
- 捕获：四张浅色/深色、1440×900 / 2048×1320 截图生成成功。
- Agent 截图复核：已查看 1440×900 浅色及 2048×1320 浅色/深色截图。主链路、边界、文字和方向可辨；这不等于用户人工业务验收。
- 一次仅调整节点位置的布局修订，将已处理交叉从 4 处减到 1 处；剩余交叉可区分，不表示连接节点。S3 路线保留 3 个弯折。

最终回执：`review-2/nexus-erp-architecture.finalize-summary.json`。

## 图示边界

1. ERP、MES、IoT、AI、Flow、Station 等由 `ruoyi-admin` 组装，不把它们误画为独立微服务。
2. MySQL 5.7 是仓库根 Compose 的镜像基线，不是对某个生产数据库版本的现场确认。
3. Nginx 的实际 API 转发配置来自部署挂载文件，本图只画静态目录关系，不推断未纳入仓库的路由。
4. 模型与 Vision 是按配置接入的能力，本次未调用这些外部服务。
5. 辅助编排声明 MonitorAdmin 与 SnailJobServer；总览以说明卡片提及，不展开为双机部署拓扑。
6. 当前工作区原有未提交文件全部保留。只新增本目录，没有改业务代码、配置或数据库，也没有提交或推送。
7. 图是逻辑总览，不是完整模块依赖矩阵、数据库 ER 图、生产部署验收或现场真机验收。

## 复核命令

以下为生成和捕获最终产物时执行的命令；运行目录是 `E:\Nexus\nexus-erp`。

```powershell
$env:ARCHIFY_CHROME = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'doc/system/archify-nexus-erp-architecture-20261009-101409/candidate.json' 'doc/system/archify-nexus-erp-architecture-20261009-101409/nexus-erp-architecture.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --out-dir 'doc/system/archify-nexus-erp-architecture-20261009-101409/review-2' --json
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' visual-check 'doc/system/archify-nexus-erp-architecture-20261009-101409/nexus-erp-architecture.html' --summary --require-provenance --out-dir 'doc/system/archify-nexus-erp-architecture-20261009-101409/review-2'
```

如果以后修改 `candidate.json`，完整重跑 `finalize` 并为新轮次选择未使用的 `--out-dir`，再捕获和查看新截图；不要用旧截图代替新图复核。
本次只生成文档产物，未运行 Maven 或前端全量构建，没有应用代码测试通过的声明。
