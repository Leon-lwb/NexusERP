# DHR 批记录前端实现报告（2026-09-20）

> 对应审计: doc/audit/2026-09-20-audit-mes-tg-sop.md §4 第 7 项 | 分支: feature/erp-mes-iot-audit-fix

本报告只覆盖 **DHR（Device History Record / 批记录）前端**部分（API 层 + 列表/详情/签署页面）。
后端实体、Controller、Service、菜单与字典种子由并行的后端子代理实现，本报告不对其做任何断言。
冻结契约中的端点路径、字段名、权限码**一字未改**。

## 1. 实现清单

| 页面/文件 | 要点 | 依赖的端点与权限码 |
| --- | --- | --- |
| `plus-ui/src/api/mes/dhr/types.ts` | 类型定义：`DhrRecordVo` / `DhrSectionVo` / `DhrVerifyVo` / `DhrRecordQuery`（extends `PageQuery`）/ `DhrRecordForm` / `DhrSignForm` / `DhrRejectForm`；`DhrSignatureVo` 直接 `type` 复用系统签名链的 `SignatureVO`（`@/api/system/sign/record`），避免与后端 `SignatureVo` 出现两份漂移的字段表。所有 ID 字段声明为 `string` | — |
| `plus-ui/src/api/mes/dhr/index.ts` | 13 个请求函数，全部走 `@/utils/request` 封装；导出走 `download()`（内部为 POST + blob） | `listDhr` GET `/mes/dhr/list`；`getDhr` GET `/mes/dhr/{dhrId}`；`listDhrSections` GET `/mes/dhr/{dhrId}/sections`；`listDhrSignatures` GET `/mes/dhr/{dhrId}/signatures`；`verifyDhr` GET `/mes/dhr/{dhrId}/verify`；`buildDhr` POST `/mes/dhr/build`；`rebuildDhr` PUT `/mes/dhr/{dhrId}/rebuild`；`delDhr` DELETE `/mes/dhr/{dhrId}`；`submitDhr` PUT `/mes/dhr/{dhrId}/submit`；`releaseDhr` PUT `/mes/dhr/{dhrId}/release`；`rejectDhr` PUT `/mes/dhr/{dhrId}/reject`；`archiveDhr` PUT `/mes/dhr/{dhrId}/archive`；`exportDhr` POST `/mes/dhr/export` |
| `plus-ui/src/views/mes/dhr/record/index.vue` | 列表：批记录编号 / 工单号 / 产出物料（编码+名称）/ 批次号 / SN / 计划·完工·报废数量 / 状态（`dict-tag` + 本地兜底）/ 冻结时间 / 放行时间 / 创建时间；筛选：批记录编号、工单号、物料编码、批次号、SN、状态、创建时间区间（`addDateRange` → `params.beginTime/endTime`） | 列表 `mes:dhr:record:list`（页面由菜单权限控制进入）；查询类按钮 `mes:dhr:record:query` |
| 同上 · 顶部操作 | `生成批记录`（弹窗：工单选择 → 可选批次号 / SN / 备注 → `buildDhr`）、`导出`（`exportDhr`） | `mes:dhr:record:add`、`mes:dhr:record:export` |
| 同上 · 详情抽屉 | `getDhr` 取基本信息（含快照哈希，截断 + tooltip + `v-copyText` 复制）；`sections` 缺失时回落 `listDhrSections`；分册 `el-collapse` 按 `sortOrder` 升序，标题 `sectionName` + `itemCount` 条；`contentJson` 解析为对象数组后用 `el-table` 动态列渲染（键名经中文映射表，未命中显示原键名；空值显示 `—`，嵌套对象 JSON 化）；每册展示 `contentHash`（截断 + 复制） | `mes:dhr:record:query` |
| 同上 · 行操作 | `重建`(DRAFT) / `删除`(DRAFT) / `提交复核`(DRAFT) / `放行`(SUBMITTED) / `驳回`(SUBMITTED) / `归档`(RELEASED) / `校验`(任意) / `签名链`(任意)，前六项按 `status` 条件渲染 | `edit` / `remove` / `submit` / `release` / `reject` / `archive`；`校验`、`签名链` 归入 `query` |
| 同上 · 签署接入 | 复用 `@/components/sign/SignAuthDialog.vue`（未改动该组件）。提交复核 / 放行直接开弹窗；驳回先用 `ElMessageBox.prompt` 收集必填原因再开弹窗。`@confirmed` 后 `setLoading(true)` 调接口；后端拒绝时**保持弹窗打开**、仅 `setLoading(false)` 允许重试（不再本地 toast，避免与 `utils/request.ts` 拦截器重复提示）；成功后 `$modal.msgSuccess` + 关弹窗 + 刷新列表。凭证只随本次请求体发送，**不写 Pinia / localStorage / URL / console** | `submit` / `release` / `reject` |
| 同上 · 校验弹窗 | `verifyDhr` 展示 `snapshotHash` / `recomputedHash` / `hashMatched` / `chainVerify` / `verdict`；`hashMatched === false` 时置顶 `el-alert type="error"` 醒目告警 | `mes:dhr:record:query` |
| 同上 · 签名链弹窗 | `listDhrSignatures` 表格：签署人 / 签署含义 / 认证方式（`PASSWORD`→登录密码、`LOGIN_ONLY`→仅登录态，未知值原样显示）/ 签署时间 / IP / 链哈希（截断 + tooltip + 复制） | `mes:dhr:record:query` |
| 同上 · 字典 | `useDict('mes_dhr_status', 'mes_dhr_section')`，状态列优先 `dict-tag`；字典为空时回落到本地映射（DRAFT/SUBMITTED/RELEASED/REJECTED/ARCHIVED → 草稿/已提交/已放行/已驳回/已归档），分册名回落到 `sectionName` → 字典 → 10 个分册编码的中文兜底。**未编辑 `ry-vue.sql`** | — |
| 同上 · 工单选择 | 复用既有 `listWorkOrder`（`@/api/mes/pro/workorder`，`GET /mes/pro/work/list`）与 `ProWorkOrderVO`（`@/api/mes/pro/types`），未新写工单接口。选择时取 `row.mesWoId` 作为契约里的 `workOrderId`（主代理已核实二者同义） | — |

新增文件 3 个，未修改任何既有文件（`SignAuthDialog.vue`、`ry-vue.sql`、后端 Java、其它模块 vue 均未触碰）。

## 2. 未实现 / 待确认项

1. **`buildDhr` 的响应体语义未在契约中冻结**：契约只写 `POST /mes/dhr/build`，未定义返回值。当前实现只做「提示成功 + 刷新列表」，**不会**在生成后自动打开详情抽屉。若后端返回新建 `dhrId`，建议后续补「生成后直达详情」的交互（需要后端明确返回 `dhrId` 还是 `DhrRecordVo`）。
2. **`workOrderId` 语义**：按主代理核实结论取 `ProWorkOrderVO.mesWoId`（`pro_work_order.mes_wo_id`）。若后端实际期望的是 ERP 工单主键（`erpWoId`），选择器与 `buildDhr` 入参需同步改；契约字段名不动，只改取值来源。
3. **`DhrVerifyVo.chainVerify` 的类型**：契约未说明是布尔还是文案。前端按 `string | boolean` 双兼容处理（布尔渲染「通过/未通过」，字符串原样展示）。若后端固定为布尔，可收紧类型。
4. **`submitDhr` / `releaseDhr` 的 `remark` 未提供 UI 入口**：契约含 `remark` 字段但未要求页面收集，当前不传（后端按可选处理）。若复核备注为合规必需项，需补一个备注输入（可挂在 `SignAuthDialog` 之外的小弹窗，避免改动该组件）。
5. **`contentJson` 明细列的中文映射是启发式**：`COLUMN_LABEL` 覆盖了工序 / 物料 / 设备 / 工装 / SOP / 检验 / 采集 / SN / NCR 等常见键名，**未与后端 10 个分册的实际 JSON schema 逐字段核对**（后端尚未落地）。未命中的键名原样展示，不会丢数据；建议后端定型后按实际 schema 补齐映射表。
6. **`mes:dhr:record:list` 权限码未落到 `v-hasPermi`**：列表页可见性由菜单权限控制（RuoYi 约定），页面内不再对「列表本身」加指令。菜单与权限码种子属后端/主脚本职责。
7. **分册内容不提供编辑入口**：批记录是冻结快照，只读展示。若后续需要「人工补录分册」（GMP 常见），需新增后端补录端点，前端再加编辑表单——本次不做。
8. **列表行操作按钮较多**（DRAFT 行最多 6 个），操作列宽 380px 且 `fixed="right"`；窄屏下表格需要横向滚动。未改为「更多」下拉，以避免 `v-hasPermi` 作用于 teleport 后 dropdown-item 的行为不确定性。

## 3. 主脚本合并清单（如有）

无。本任务未新增/修改任何 SQL 主脚本；`ry-vue.sql` 按约束未编辑。
字典 `mes_dhr_status`、`mes_dhr_section` 与菜单（组件路径 `mes/dhr/record/index`）的种子由后端代理负责。

## 4. DDL 登记（如有）

无 DDL 变更。

## 5. 验证记录

```
# 在 plus-ui 目录执行
npx eslint --fix src/api/mes/dhr/index.ts src/api/mes/dhr/types.ts src/views/mes/dhr/record/index.vue
→ 无输出（0 error / 0 warning，exit 0）

npx vue-tsc --noEmit
→ （结果见下方「vue-tsc 结果」）
```

### vue-tsc 结果

```text
$out = npx vue-tsc --noEmit 2>&1; $code = $LASTEXITCODE
EXITCODE=0
LINES=0
```

全仓类型检查通过（exit code 0，输出 0 行），新增的 3 个文件未引入任何类型错误。
（首次执行时用管道 `| Select-Object` 会吞掉原生命令退出码，故重跑一次直接捕获 `$LASTEXITCODE` 以取得权威结论。）


未执行 `npm run build`（按环境硬约束禁止）。

## 6. 风险与回滚说明

- **回滚**：本任务只新增 3 个文件（`src/api/mes/dhr/types.ts`、`src/api/mes/dhr/index.ts`、`src/views/mes/dhr/record/index.vue`），无既有文件改动、无 DDL、无 SQL。回滚即删除这 3 个文件；若菜单已种入，需同时移除对应菜单行，否则会命中「组件路径不存在」的空白页。
- **契约漂移风险**：页面完全按冻结契约书写。后端若改名（字段/路径/权限码），前端会静默失败（字段显示空、请求 404/403）。建议后端合并后按 `§1` 清单逐端点对一遍。
- **签署安全**：凭证仅存在于 `SignAuthDialog` 组件内存与本次请求体；弹窗开/关均清空（该组件既有行为）。前端无任何落盘/日志/URL 泄漏路径，`console.error` 只记录错误对象，不打印凭证。
- **哈希展示**：列表与面板对哈希做 20 字符截断 + tooltip + 复制按钮，完整值不丢失。
- **字典未同步时的降级**：状态列与分册名有本地兜底映射，不会整列空白；但**筛选下拉的取值来自本地兜底**，若后端字典枚举与本地映射不一致（例如新增状态），筛选值会与后端不匹配——这是刻意的降级取舍，字典同步后自动回到字典驱动。
- **未验证项**：无真实后端可用，所有接口的联调（尤其 `buildDhr` 入参、`verifyDhr` 返回结构、`listDhr` 是否返回 `rows/total`）均未做运行时验证。列表按 RuoYi `TableDataInfo`（`rows`/`total`）解析。
