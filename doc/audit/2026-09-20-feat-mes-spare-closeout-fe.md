# MES 备件↔ERP 打通收口（前端）实现报告（2026-09-20）

> 对应: `doc/audit/2026-09-20-feat-mes-spare-erp.md` §5 | 分支: `feature/erp-mes-iot-audit-fix`
> 范围: 仅 `plus-ui/**`（前端）。后端由并行子代理实现，**端点路径 / 字段名 / 权限码一字未改**。
> 约定: 新增/修改文件均通过 `npx eslint --fix` 与 `npx vue-tsc --noEmit` 校验（见 §5）。

## 1. 实现清单

| 文件 | 要点 | 依赖端点与权限码 |
|---|---|---|
| `plus-ui/src/api/mes/eq/spare-part.ts`（改，**只追加/扩字段**） | ① `EqSparePartVO` 增 `materialId`/`warehouseId`(string)/`erpAvailableStock`/`erpMapped`/`lowStockBasis`/`unitCost`；`EqSparePartForm` 增 `materialId`/`warehouseId`/`unitCost`。② 新增 `EqSpareErpSyncVO`（字段与 `EqSpareErpSync` 实体逐一对齐）与 `EqSparePurchaseRequest`。③ 追加 4 个函数：`purchaseRequest` / `erpSyncList` / `retryErpSync` / `backfillWarehouse`（**既有 6 个函数签名未动**） | `POST /mes/eq/spare-part/purchase-request`（`mes:eq:spare:purchase`）、`GET /mes/eq/spare-part/erp-sync`（`mes:eq:spare:query`）、`POST /mes/eq/spare-part/erp-sync/{syncId}/retry`（`mes:eq:spare:stock`）、`POST /mes/eq/spare-part/backfill-warehouse`（`mes:eq:spare:edit`） |
| `plus-ui/src/views/mes/eq/spare-part/index.vue`（改，定点增量） | §5-1/§5-2：① 表单新增「ERP 物料」（`listMaterial` + `status=1`）、「ERP 仓库」（`listWarehouse` + `status=1`）、「参考单价」(`unitCost`, precision 4)。② 列表新增列 `ERP物料`（已映射显示「编码 - 名称」，未映射橙色高亮「未映射 ERP 物料，无法出入库」）、`ERP仓库`、`ERP可用量`(`erpAvailableStock`)、`参考单价`。③ `当前库存` 列按 `lowStockBasis` 追加口径提示（`NOT_MAPPED` / `ERP_UNAVAILABLE`）。④ 顶部「回填默认仓库」按钮（二次确认 + 影响行数 + 后端错误原样提示）。⑤ 新增 `initForm` 字段 `materialId/warehouseId/unitCost` | 同上（下拉：`GET /erp/bd/material/list`、`GET /erp/wm/warehouse/list`） |
| `plus-ui/src/views/mes/eq/monitor/index.vue`（改，定点增量） | §5-3：低库存视图（唯一消费 `/lowStock` 的页面，「低库存告警」卡片）新增「一键生成采购申请」按钮：未映射备件先在前端拦截并**列出编码**，通过后二次确认 → `purchaseRequest({spareIds, remark})` → 提示「已提交 ERP 采购申请生成请求（N 行）」并弹确认框引导跳转 `/erp/pu/requisition`。同时该卡片补 `ERP可用量` 列与 `lowStockBasis` 口径提示 | `POST /mes/eq/spare-part/purchase-request`（`mes:eq:spare:purchase`）；跳转路由 `/erp/pu/requisition`（已 read `src/router/index.ts` 确认存在，`activeMenu=/erp/pu/requisition`） |
| `plus-ui/src/views/mes/eq/spare-erp-sync/index.vue`（**新建**，组件路径冻结 `mes/eq/spare-erp-sync/index`） | §5-4 过账台账页：列 `备件编码`/`业务幂等键`（CSS 截断 + `v-copyText` 复制）/`变动类型`/`数量`/`同步状态`(标签色)/`重投次数`/`最近投递时间`/`ERP过账后可用量`/`失败原因`/`创建时间`/`操作`；筛选 `同步状态`；顶部 `刷新`；行操作 `重投`（二次确认；返回 `true` → 「已确认 ERP 落账」，`false` → 「仍未确认 ERP 落账，请检查失败原因」）。台账为 `R<List>` 无分页参数，故本地切片分页；同步状态/变动类型用**本地文案映射**（不依赖字典种子） | `GET /mes/eq/spare-part/erp-sync?syncStatus=`（`mes:eq:spare:query`）、`POST .../{syncId}/retry`（`v-hasPermi="['mes:eq:spare:stock']"`） |
| `plus-ui/src/api/mes/eq/spare-return/index.ts`（**新建**） | 6 个函数：`listSpareReturn`/`getSpareReturn`/`addSpareReturn`/`updateSpareReturn`/`delSpareReturn`/`confirmSpareReturn` | `GET /mes/eq/spare-return/list`、`GET|POST|PUT|DELETE /mes/eq/spare-return[/{id}]`、`PUT /mes/eq/spare-return/{returnId}/confirm` |
| `plus-ui/src/api/mes/eq/spare-return/types.ts`（**新建**） | `SpareReturnVo`（含 `lines`）、`SpareReturnLineVo`、`SpareReturnQuery`（`returnNo/status/returnType/beginTime/endTime` + 分页）、`SpareReturnForm`；`returnType`/`status` 取值注释按冻结契约 | — |
| `plus-ui/src/views/mes/eq/spare-return/index.vue`（**新建**，组件路径冻结 `mes/eq/spare-return/index`） | 列表（单号/日期/类型/仓库/状态/合计数量/确认人/确认时间/创建时间）+ 筛选（单号、状态、类型、日期区间 `beginTime/endTime`）；弹窗新增/修改，**明细行可增删**（备件 → 数量 → 可选单价 → 备注，自动合计 `totalQty`）；行操作 `确认退库`（仅 DRAFT，二次确认「确认后不可撤销，将按入库方向过账到 ERP 库存」）、`编辑`/`删除`（仅 DRAFT）；字典 `useDict` + `dict-tag`，并带**本地兜底文案**（字典未同步也不空白） | `mes:eq:spare:return:list/query/add/edit/remove/confirm` |

**改动规模**：修改 3 个既有文件（均为定点增量，`spare-part/index.vue` 未整文件重写）、新增 4 个文件、新增报告 1 个。

### 1.1 冻结契约核对（逐条）

| 契约项 | 前端落地 | 结论 |
|---|---|---|
| `purchaseRequest(data)` → `R<number>`，权限 `mes:eq:spare:purchase` | `spare-part.ts` 同名函数；body `{spareIds, remark}` | ✅ 一致（已 read 后端 `EqSparePartController#purchaseRequest`，路径/权限码一致） |
| `erpSyncList(syncStatus)` → `R<EqSpareErpSync[]>`，权限 `mes:eq:spare:query` | `erpSyncList(syncStatus?)`，params 仅在非空时携带 | ✅ 一致 |
| `retryErpSync(syncId)` → `R<boolean>`，权限 `mes:eq:spare:stock` | `retryErpSync(syncId)`；`false` 文案按契约 | ✅ 一致 |
| `backfillWarehouse()` → `R<number>`，权限 `mes:eq:spare:edit` | 已补函数 + 页面按钮；后端端点由并行代理实现 | ⚠️ 前端就绪，**后端未就绪**（当前 `EqSparePartController` 无该端点，属并行代理范围） |
| `EqSpareErpSync` 字段与 `syncStatus`/`transactionType` 取值 | `EqSpareErpSyncVO` 逐字段对齐（含 `erpAvailableAfter`/`lastPublishTime`/`retryCount`/`lastError`/`bizKey`/`logId`）；状态 4 值 + 类型 4 值本地映射 | ✅ 一致 |
| 退库单 6 端点与类型 | 完全按契约命名（`SpareReturnVo`/`SpareReturnLineVo` 字段名一致） | ✅ 一致 |
| 组件路径 `mes/eq/spare-erp-sync/index`、`mes/eq/spare-return/index` | 文件物理路径即冻结路径 | ✅ 一致 |

## 2. 未实现 / 待确认项（含理由与建议）

| 项 | 状态 | 理由与建议 |
|---|---|---|
| **§5-5 库存流水页显示同步状态** | **未实现（待确认）** | 已 read `EqSpareStockLogVo.java`：字段仅 `logId/spareId/spareCode/spareName/transactionType/quantity/beforeQty/afterQty/refType/refId/operatorId/operatorName/operateTime/remark`，**无 `erpSyncStatus`**；且现有 `GET /mes/eq/spare-part/erp-sync` 只支持 `syncStatus` 过滤，**没有按 `logId` 批量查询的入口**，前端无法按 `logId` 关联台账。按任务约束「若后端流水 VO 未含该字段，就不要硬做」故不做。建议（二选一，均属后端）：① 流水 VO 增 `erpSyncStatus` 冗余列；② `erp-sync` 端点增加 `logIds` 入参，前端在流水页按页内 `logId` 二次查询后合并展示。 |
| **后端 `backfill-warehouse` 端点尚未落地** | 前端已就绪 | 前端按钮/函数/错误原样提示均已完成；端点由并行子代理实现。**若最终未实现，按钮会 404**（`ignoreErrorMsg` 下提示「系统接口 404 异常」）。建议合并前确认端点存在；若取消该端点，删除 `spare-part/index.vue` 的「回填默认仓库」按钮与 `backfillWarehouse` 函数即可（无其它引用）。 |
| **后端 `unitCost`（参考单价）字段缺失** | 前端已按契约提交 | 全量 grep `ruoyi-modules/ruoyi-mes/.../eq/**` **未发现任何 `unitCost`/`unit_cost`**（`EqSparePart`/`EqSparePartBo`/`EqSparePartVo` 均无该字段）。前端表单已提交 `unitCost`、列表已展示 `unitCost`；若后端未同步新增该字段，值会被静默丢弃、列表恒显示 `-`（不会报错）。建议后端确认 `eq_spare_part.unit_cost` 列 + BO/VO 字段。 |
| **`lowStockBasis` 第三个取值 `ERP_UNAVAILABLE`** | 前端已防御性支持 | `EqSparePartVo.lowStockBasis` 的 javadoc 只声明了 `ERP_AVAILABLE` / `NOT_MAPPED`，而任务与 §5 提到 `ERP_UNAVAILABLE`（ERP 不可达降级）。**字段名以 Java 为准（`lowStockBasis`，已确认）**；前端对三值都做了展示（`ERP_UNAVAILABLE` → 「ERP 不可达，已降级为 MES 派生缓存口径」），未知值不显示提示。建议后端确认是否真的产出 `ERP_UNAVAILABLE`。 |
| **菜单种子（两个新页面）** | 未做（不在前端所有权内） | `mes/eq/spare-erp-sync/index`（查看权限 `mes:eq:spare:query`）与 `mes/eq/spare-return/index`（`mes:eq:spare:return:list/query/add/edit/remove/confirm`）需要 `sys_menu` 种子，否则页面无法从侧边栏进入、非超管 403。**我不编辑 `script/sql/**`**，请后端/主代理在菜单种子里一并补（含 `mes:eq:spare:purchase` 按钮，见原报告 §3.2）。 |
| **字典种子 `mes_spare_return_status` / `mes_spare_return_type`** | 前端已兜底 | 后端代理负责种子；前端 `useDict` + `dict-tag`，**字典未同步时用本地兜底文案**（`DRAFT=草稿/CONFIRMED=已确认/CANCELLED=已取消`、`REPAIR_RETURN=维修退库/EXCESS_RETURN=余料退库/OTHER=其他`），筛选下拉同样兜底，不会空白。 |
| **「一键生成采购申请」按钮位置** | 已实现于 monitor 页 | §5-3 写「低库存页」，仓库中**唯一消费 `/lowStock` 的视图**是 `views/mes/eq/monitor/index.vue` 的「低库存告警」卡片，故落在此处（备件主数据页列表并非 `/lowStock` 数据源）。若产品希望入口在备件页，可平移该按钮与 `handlePurchaseRequest`（约 30 行，无额外依赖）。 |
| **ERP 物料下拉的 500 条上限** | 已实现（有降级） | 列表「ERP物料/ERP仓库」列依赖下拉选项做 ID→编码名称映射，选项只拉前 500 条（`pageSize: 500`，与 `mes/pro/mbom` 同做法）。超出范围的行显示 `#<ID>` 而非空白。若物料规模较大，建议后端在 `EqSparePartVo` 补 `materialCode/materialName/warehouseCode/warehouseName` 冗余字段，前端即可去掉下拉依赖。 |
| **库存流水页 / 备件页的出库上限口径** | 未改（有意） | 备件页出库弹窗仍以 `currentStock`（MES 派生缓存）做**前端预校验**，权威判定仍在后端（ERP 可用量）。未改是为控制 diff；如需前端也按 `erpAvailableStock` 预校验，属独立小改。 |
| **`erpMapped` 字段** | 已声明未强依赖 | VO 的 `erpMapped` 已加入类型定义，但前端判定「已映射」用 `materialId` 是否为空（更直观且不依赖后端填充时机）；`erpMapped` 保留备用。 |

## 3. 主脚本合并清单（如有）

**本代理未编辑任何 SQL**（`script/sql/**` 属独占禁区）。需要主脚本/种子合并的前端相关项：

1. `sys_menu`：`mes/eq/spare-erp-sync/index` 菜单（`perms='mes:eq:spare:query'`，`menu_type='C'`）。
2. `sys_menu`：`mes/eq/spare-return/index` 菜单 + 6 个按钮权限（`mes:eq:spare:return:list|query|add|edit|remove|confirm`）。
3. `sys_dict_type` / `sys_dict_data`：`mes_spare_return_status`、`mes_spare_return_type`（后端代理声明由其一并种子，前端已兜底）。
4. `sys_menu`：`mes:eq:spare:purchase` 按钮（原报告 §3.2 已给 `menu_id=4128` 方案）。

## 4. DDL 登记（如有）

**无**。本次改动全部为 `plus-ui/**` 前端代码，不涉及库表结构；`eq_spare_part.material_id/warehouse_id/unit_cost`、`eq_spare_erp_sync`、退库单相关表的 DDL 由后端代理登记。

## 5. 验证记录（eslint / vue-tsc 命令与结果）

```powershell
# 1) ESLint（在 plus-ui 目录）
npx eslint --fix src/api/mes/eq/spare-part.ts src/api/mes/eq/spare-return/index.ts src/api/mes/eq/spare-return/types.ts `
  src/views/mes/eq/spare-part/index.vue src/views/mes/eq/spare-erp-sync/index.vue `
  src/views/mes/eq/spare-return/index.vue src/views/mes/eq/monitor/index.vue
# → 无输出（0 error / 0 warning，未产生自动修复差异）

# 2) 类型检查（仅最后跑一次；输出重定向到 .gitignore 内的 vue-tsc-out.txt）
npx vue-tsc --noEmit > vue-tsc-out.txt 2>&1
# → EXIT=0，ERROR_LINES=0
```

- **未运行** `npm run build`（按硬约束禁止）。
- **未执行**任何 SQL、未运行任何 git 命令。
- 未改 `ruoyi-modules/**`、`script/sql/**`、`application*.yml`、`doc/system/数据库增量变更记录.md`。
- 说明：`vue-tsc-out.txt` 为 `type-check:changed` 脚本约定的本地临时文件，已在 `plus-ui/.gitignore:32` 忽略，不影响工作区干净度。

## 6. 风险与回滚说明

| 风险 | 说明 | 缓解 / 回滚 |
|---|---|---|
| **R1 后端未就绪导致按钮报错** | `backfill-warehouse`（并行代理）、退库单 6 端点、`purchase-request` 的 `unitCost` 若未落地，对应按钮/页面会提示 404 或字段被丢弃 | 前端为纯增量：删除对应按钮/页面即可。**合并顺序建议**：后端端点 + 菜单/字典种子先上，前端后上（前端页面本身不写库，先上也不会坏，只是入口无权限看不到） |
| **R2 菜单/权限种子缺失** | 新页面无菜单项 → 无法进入；`mes:eq:spare:purchase` 缺失 → 非超管看不到按钮（`v-hasPermi` 隐藏，不报错） | 见 §3；超管账号可直接访问 `/mes/eq/spare-return`、`/mes/eq/spare-erp-sync` 验证功能 |
| **R3 错误提示重复** | `purchaseRequest` / `retryErpSync` / `backfillWarehouse` / `confirmSpareReturn` 4 个调用使用 `headers: { ignoreErrorMsg: 'true' }`（仓库既有约定，见 `api/ai/knowledge.ts`），由页面用 `proxy.$modal.msgError` **原样展示后端信息**，避免统一 toast + 页面 toast 双弹 | 若希望回到统一提示：去掉 API 里的 `headers` 并删除页面 `catch` 中的 `msgError` |
| **R4 台账量级** | `GET /erp-sync` 一次性返回全部台账（后端无分页），前端本地切片分页；台账长期累积后首屏会变重 | 属后端接口形态问题；建议后续给端点加 `PageQuery`（会改契约，需双方确认）。当前 `同步状态` 筛选可显著收敛（如只看 `FAILED`） |
| **R5 本地兜底文案与字典漂移** | 字典种子文案若与本地兜底不一致，字典生效后以字典为准（`dict-tag` 优先） | 兜底仅用于「字典未同步」；文案差异不影响数据（`value` 才是契约） |
| **R6 物料下拉 500 条上限** | 超出部分显示 `#ID`（见 §2） | 见 §2 建议（后端补冗余编码/名称字段） |
| **R7 退库确认不可逆** | 确认后按入库方向过账 ERP，前端仅做二次确认文案 | 文案已明确「确认后不可撤销」；真正的状态守卫在后端（仅 `DRAFT` 可确认，前端同时把非 DRAFT 行的按钮置灰） |

**整体回滚**：本次全部为前端文件，按文件粒度回退即可 —— 删除 4 个新增文件（`views/mes/eq/spare-erp-sync/`、`views/mes/eq/spare-return/`、`api/mes/eq/spare-return/`），并回退 3 个修改文件（`api/mes/eq/spare-part.ts`、`views/mes/eq/spare-part/index.vue`、`views/mes/eq/monitor/index.vue`）。无数据迁移、无不可逆操作。
