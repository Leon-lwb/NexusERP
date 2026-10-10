# AI 与工业决策架构图 — 文档交接

## 范围与固定来源

- 仓库：`https://gitee.com/helloworld_wh18/nexus-erp.git`。
- 固定 revision：`5b66fbeeff5a58028f248c68705e29b0bf968dd1`。所有源码证据读取 **`git show revision:path` 的 committed bytes**；未把现有未提交修改当作该提交的证据。
- 图类型 `architecture`；`zh-CN`；`showcase`；源链接 `local-only`。无显式 viewBox / visual_preset / subtitle / branding。
- 仅写入 `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733`。未改应用代码 / 配置、未提交或推送、未启动用户服务、未调用业务端点、未连接或写数据库、未生成代理。现有修改和未跟踪工作未被清理。
- 这是运行时职责与有来源的调用 / 接口实现图，**不是 POM 编译依赖图，也不是已部署拓扑图**。

## 入口与职责

1. 普通 AI：Vue 助手 → `AiServiceFacade`；会话归属、模式路由与异步上下文；`ChatModelFactory` 从模型 / 厂商配置装配和缓存协议客户端。Agent 仅注入已启用的分类内置工具，MCP 按请求 / 会话选择并挂独立角色；不能把所有 MCP 工具泛称只读。
2. RAG：按路由 / 知识库增强提示词，校验可见性和索引就绪；向量按知识库配置选 Milvus 或 InMemory，代码兜底默认 `inmemory`。**不声称现场 Milvus 已安装或在线**。
3. 工业决策：实际发起入口在 ERP 工单 / 销售订单 / 排程 / 缺料页；AI decision 页负责查询 / 详情 / 审阅。控制器按 run/query/approve/execute 权限和服务端登录身份驱动。
4. `common-ai` 定义 DTO、`IndustrialDecisionDataPort`、`DecisionWorkflowPort`，并映射 `ai_decision_run/evidence/action`；接口 / 实体定义不等于执行权威。
5. ERP 工业 adapter 是可信来源快照、APS / 缺料仿真和受控业务动作的权威；AI 服务负责确定性风险 / 排名、证据覆盖、策略、审批和幂等执行状态。
6. `ruoyi-flow` 桥接并校验受保护的、已发布的规范图及注册节点；调度和工作节点绑定租户 / 身份，工作结束清理。通用 Flow 入口不能任意替换工业图。
7. 工业 LLM 只做定性解释，不发明 KPI / 证据或直接写业务；模型 / 制度知识库配置决定可选节点；校验或模型失败回退服务端模板。
8. `DecisionApprovalService` 校验实时权限、runVersion、snapshotHash 与来源新鲜度，批准事务提交后启动**新的受保护执行 Flow**。这不是独立 `/workflow/task` 的 Flowable BPMN 审批。
9. 执行只认批准的冻结动作与实时业务权限；状态写入用 tenant/version CAS。采购先按原键恢复永久 command 再核新鲜度，未确认提交保留断点。源码实际结果：采购 DRAFT/v0；排程方案 COMPLETED，不能仅按 action 名里的 draft 推断状态。

## 当前交付与验收证据

- HTML：`E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html`。
- Candidate：`E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\candidate.json`。
- **权威 finalize summary**：`E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.finalize-summary.json`；完整回执 `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.finalize.json`。
- `validate / deliver / check / browser-check` 全 pass；**9/9 showcase，0 错误、0 警告**。
- 22 个组件、22 条关系；Archify 验证 **65 条 primary 源引用**，每节点 ≤3；单独证据记录 **131 个闭区间、62 个 committed 源文件**，末行和 SHA256 再次核对。
- Edge：`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`。自动浏览器检查 4 个 desktop viewport；当前原生 capture 4 张（1440/2048 × light/dark）。采用正常页面纵向滚动，无横向溢出。
- 已用 `view_image` 检查原生 1440 light、2048 light/dark，并补看完整页和无 hover 的工业审批 / 执行区域；观察范围及图像 SHA256 见 `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\visual-review.json`。视觉结论针对静态文档图，不是业务验收。
- `preview.png` 是权威原生 1440 light 捕获的**逐字节复制**；仅展示顶部 viewport。`preview-full.png` 是补充本地 Edge 全页浅色捕获的水平去边距版本，无缩放 / 重绘；不修改原生证据。复制 / 裁剪证据见 `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\preview-provenance.json`。
- 文档任务未运行 Maven/npm 应用构建、单测或业务集成测试；不把文档门禁写成应用测试通过。

## SHA256 绑定

```text
specification_sha256: abee4580e855addcb2226fe387fadd11f7afe4665995f8804898d77384d4b5d9
artifact_sha256:      77192ad390586c7e0a9a22f638ac016d2e10d6aff3084e8d79a23bfc621bd19c
```

`E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\hash-binding.json` 核对 candidate、当前 HTML、delivery、权威 finalize、browser、visual、standalone check 及补充捕获的绑定；`E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\handoff.json` 是机器交接。

## 有界修订与未验证边界

- 首稿机器通过但建议检查 1 处已解析交叉；唯一一次 positions/sizes-only 尝试令交叉数变 2，恢复并留存 review-2 / review-3。**没有第二轮位置调整**。
- 视觉发现 READ 会隐藏 tag，随后只把有来源的条件提升到可见副标题，保留布局、节点、关系、方向与源证据；当前以 **review-4** 为准。根目录旧 finalize/browser 及 review-2/review-3 是历史，不是当前交付证明；根目录 delivery 是最新 provenance。
- 仍有 1 处 resolved crossover 与 1 条三弯折 WorkflowPort 路由的非阻断阅读提示；`properCrossings=0`，但**不声称零交叉**。
- **未验证生产部署、数据库 / 迁移、模型 / Milvus 在线可用性、真实业务结果或现场人工审批**。这类活动被本任务禁止，未执行。源码中存在的保护机制不等于生产运行或验收结论。

## 精确命令与结果

工作目录：`E:\Nexus\nexus-erp`。每个 CLI 浏览器 exec 会话显式设置：

```powershell
$env:ARCHIFY_CHROME = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
```

### 首稿 finalize

```powershell
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\candidate.json' 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --json
```

exit 0；四道门禁通过；9/9，0 错误、0 警告；1 resolved crossover。

### 一次仅位置调整

```powershell
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\candidate.json' 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --out-dir 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-2' --json
```

exit 0；crossovers 增至 2，按约定恢复首稿，不再做位置调整。

### 恢复后 finalize

```powershell
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\candidate.json' 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --out-dir 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3' --json
```

exit 0；恢复原 SHA256；crossovers=1。

### 恢复稿视觉捕获

```powershell
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' visual-check 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html' --summary --require-provenance --out-dir 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3'
```

exit 0；4 张原生截图，保留为历史。

### 可见条件修订后 finalize（权威）

```powershell
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\candidate.json' 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --out-dir 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4' --json
```

exit 0；9/9 showcase，0 错误、0 警告；四门禁全 pass。

### 当前视觉捕获（权威）

```powershell
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' visual-check 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html' --summary --require-provenance --out-dir 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4'
```

exit 0；containment/readability/viewerChrome/themeStates/captures 全 pass；4 张原生截图。

### 补充本地静态页捕获

```powershell
python 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\capture-supplemental.py'
```

最终执行 exit 0；本地 file: HTML、offline Edge，全页 light/dark 与工业区域；首个上下文选择器尝试失败，未影响自动门禁。

### 无 hover 工业区域补看

```powershell
python 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\capture-industrial.py'
```

exit 0；scrollY=1000；只生成补充证据，不改 HTML。

### 最终 standalone check

```powershell
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' check 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html' --require-provenance --json
```

exit 0；9/9，provenance=current；结果保存 final-check.json。

### 哈希 / 范围再核对

```powershell
python 'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\write-handoff.py'
```

exit 0；8 项 hash-binding checks；62 个 committed 文件、131 个闭区间复核。

源码证据核验方式：`git show 5b66fbeeff5a58028f248c68705e29b0bf968dd1:<repo-relative-source-path>`；完整路径 / 范围 / committed 文件与区间散列保存在 source-evidence.json。

## 本任务写入路径（绝对路径）

所有文件均位于唯一授权目录。机器列表为 `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\write-paths.json`。

- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\README.md`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.browser-check.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.delivery.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.finalize-summary.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.finalize.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\ai-industrial.html`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\author-candidate.py`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\candidate.initial.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\candidate.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\capture-industrial.py`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\capture-supplemental.py`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\final-check.exit.txt`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\final-check.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\handoff.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\hash-binding.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\inspect-source.py`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\layout-repair.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\preview-full.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\preview-provenance.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\preview.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\repair-visible-conditions.py`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-2\ai-industrial.browser-check.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-2\ai-industrial.finalize-summary.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-2\ai-industrial.finalize.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-2\ai-industrial.snapshot.html`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-2\candidate.snapshot.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.browser-check.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.finalize-summary.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.finalize.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.snapshot.html`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.visual-check.1440x900.dark.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.visual-check.1440x900.light.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.visual-check.2048x1320.dark.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.visual-check.2048x1320.light.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.visual-check.html`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\ai-industrial.visual-check.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-3\candidate.snapshot.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.browser-check.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.finalize-summary.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.finalize.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.supplemental-2.1440.read.dark.full.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.supplemental-2.1440.read.light.full.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.supplemental-2.1440.read.light.industrial.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.supplemental-3.1440.read.light.industrial-plain.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.supplemental-capture.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.supplemental.1440.read.light.full.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.visual-check.1440x900.dark.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.visual-check.1440x900.light.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.visual-check.2048x1320.dark.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.visual-check.2048x1320.light.png`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.visual-check.html`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\review-4\ai-industrial.visual-check.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\source-evidence.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\visibility-repair.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\visual-review.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\workspace-baseline.json`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\write-handoff.py`
- `E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733\write-paths.json`

## 交付前独立复核（2026-10-09）

- 完整浅色图面预览：`ai-industrial-full-diagram-preview.png`。它只截取完整 SVG，不包含页尾说明卡片/导航控件；已使用图像查看工具检查，原生门禁回执保持不变。
- 补充预览来源与 SHA256：`supplemental-diagram-preview.json`。
- 独立源码及产物绑定复核：`independent-verification.json`；最后一次严格 `check --require-provenance --json`：`independent-final-check.json`，9/9、0 错误、0 警告。
- 观察到工作区 HEAD 已前进至 `2d8abf814df33c6f6433897264df072a202536a3`，本图仍固定到原绘制基线 `5b66fbeeff5a58028f248c68705e29b0bf968dd1`；已比对新增提交，被引用文件及全部 POM 未发生修改。新增的其他功能不声称已完整绘入本图。
- 本轮只补充预览及交接/验证记录，没有改写图定义或 HTML，没有应用构建、数据库连接、业务接口调用或生产验收。
