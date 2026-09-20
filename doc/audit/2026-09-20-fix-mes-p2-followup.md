# MES P2 遗留收口 修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-fix-mes-p2.md` §2（未修复项 3/4/5/6）+ §6.1 第 7/8 条、`doc/audit/2026-09-20-audit-mes-flash.md` 的 **MES-TEST-05** / **MES-STATION-01**、`2026-09-20-audit-mes-spc-dc-lt.md` 的 **MES-SPC-03**（前端 method）
> 分支: `feature/erp-mes-iot-audit-fix` | 增量脚本: **无**（本批次无 DDL、无数据变更）
> 执行方式: 单修复子代理；前端 4 个文件 + 后端 2 个文件 + 2 个测试类。

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| MES-SPC-03（前端收口） | `analyzeControlChart` 由 `method:'get'` 改 **`method:'post'`**（后端已改 POST，原前端必然 405）；`SpcControlChartVO`/`SpcAnalyzeResult` 补 `rUcl/rCl/rLcl/pp/ppk/capabilityInsufficient/capabilityInsufficientReason`，`SpcDataPointVO` 补 `subValue` | `plus-ui/src/api/mes/spc/control-chart.ts:5-23,58-104,133-135` | 否 | 前端：`vue-tsc --noEmit` 0 error（无单测框架覆盖 api 层） |
| MES-SPC-02/03（前端展示） | 列表页新增「次级图限(RUCL/RCL/RLCL)」「Pp/Ppk」列（沿用既有"总数/已用"合并列风格）；分析弹窗补次级图控制限/次级图点数/Pp/Ppk 描述项、`capabilityInsufficient` 警示条（含原因文案）、新增 **次级图(R/S/MR) ECharts**（用 `subValue` 序列 + 独立 `rUcl/rCl/rLcl` 控制限，实例随主图 dispose/resize） | `plus-ui/src/views/mes/spc/control-chart/index.vue:57-78,171-225,409-419,426-439,509-566,600-605` | 否 | 同上 |
| MES-TEST-05 | `plus-ui/src/api/mes/flash/pool.ts` 补 `allocatePoolResource(poolId, snId, snCode?)` → **`POST /mes/flash/pool/allocate`**（`params`，对齐 Controller `@RequestParam`；返回 `R<MesResourceItem>`）+ `MesResourceItemVO` 类型；资源池列表页新增行内「手工分配」入口（权限 `mes:flash:pool:edit`，种子已存在）+ 分配弹窗（SN 远程搜索复用 `/mes/flash/flash/sn-options`，分配后回显资源值与状态并刷新列表） | `plus-ui/src/api/mes/flash/pool.ts:1-2,37-70`、`plus-ui/src/views/mes/flash/pool/index.vue:75-83,146-190,282-337` | 否 | 前端：`vue-tsc --noEmit` 0 error |
| MES-STATION-01 | `bind` 的条件更新读返回值：**`rows == 0` 抛 `ServiceException("组件 SN %s 已被并发绑定, 请刷新重试")`，不写 BIND 日志、不累加 `boundQty`**；同一根因在 `unbind` 的换板重绑 CAS（同为 `isNull(parent_sn_id)` 守卫）一并收口（0 行抛异常、不写 REBIND 日志，事务回滚保证"解绑+重绑"原子） | `ruoyi-modules/ruoyi-station/src/main/java/com/nexus/station/service/StationSnService.java:85-98,141-156` | 否（索引可选，见 §2） | `StationSnServiceTest#bind_casAffectsZeroRowsThrowsAndWritesNoLog`、`#unbind_rebindCasAffectsZeroRowsThrowsAndWritesNoLog`；既有 `#bind_componentsAttachedToParent` 追加断言"日志 parentSnId == CAS 目标父SN" |
| 刷写门禁 `gate_enabled`（`2026-09-20-fix-mes-p2.md` §2.4） | `checkFlashStepGate` 的绑定项按刷写类型 `gate_enabled` 过滤：显式 `'0'` 的类型**不参与完工门禁、不拦截完工**；类型行缺失或 `'1'`/空按"参与门禁"处理（与 flash 侧 `FlashRecordServiceImpl.filterGateEnabledBindings` 语义逐字对齐，含 `FORCE`/`WARN` 模式不变）。注入 `MesFlashTypeMapper`，新增私有 `filterGateEnabledBindings(List<FlashBinding>)` | `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java:16-19,97-99,391-397,455-475` | 否 | `ProFlowCardServiceImplTest#checkFlashStepGate_skipsGateDisabledType`（放行且不查刷写记录）、`#checkFlashStepGate_blocksWhenGateEnabledTypeHasNoPassRecord`（反向对照：`'1'` 时仍拦截） |

**测试类分组标签**：新增用例位于既有类 `StationSnServiceTest`、`ProFlowCardServiceImplTest`，二者均已带 `@Tag("local")`/`@Tag("dev")`（`ProFlowCardServiceImplTest` 原本就有；`StationSnServiceTest` 所在模块 `ruoyi-station` 的 surefire `groups` 未过滤，实测已执行）。

## 2. 未修复 / 部分修复项（含理由与建议）

1. **`FlashRecordServiceImpl.java:651-652` 的「待办(pro 包)」注释已过时**：该注释写着"权威门禁 `ProFlowCardServiceImpl.checkFlashStepGate` 的绑定遍历仍无该过滤"，本次已在 pro 侧补齐，注释与行为不再一致。因 `ruoyi-mes/flash/**` 不在本次文件所有权内**未改动**，请主代理顺手删除该 `<p><b>待办(pro 包)</b>…</p>` 段（仅注释，无逻辑变更）。
2. **MES-STATION-01 的可选对账索引未加**：审计建议的 `mes_sn_bind_log(tenant_id, parent_sn_id, component_sn_id, action, create_time)` 索引未创建（本批次无 DDL 授权）。不加索引不影响正确性（重复日志的根因已消除），如需加速追溯查询可按规范走增量脚本。
3. **`mes_sn_bind_log` 仍无唯一约束**：本次用"CAS 0 行即失败"从写入侧保证日志与 `parent_sn_id` 一致；若后续仍有跨路径写入（如 MQ/脚本补录），建议再加唯一键兜底（需评估存量重复数据）。
4. **前端 SPC 次级图仅在数据点含 `sub_value` 时渲染**：`spc_data_point.sub_value` 是 MES-SPC-01 新增列，**存量历史数据点该列为 NULL**，此时弹窗显示"次级图点数 0"且不渲染次级图（主图与描述项正常）。属期望语义（历史点未记录组内离散度），需重跑一次"分析"才会按 `sample_group` upsert 回填 `sub_value`。
5. **手工分配的权限码沿用 `mes:flash:pool:edit`**：与后端 `@SaCheckPermission("mes:flash:pool:edit")` 一致且菜单种子已存在，**无需 DDL**；但意味着"能编辑资源池"即可手工分配（审计建议即如此）。
6. **`unbind` 的首个解绑更新仍是无条件更新**（仅按 `sn_id`，无 `isNull` 守卫）：同一事务内刚 `selectById` 过，0 行仅可能是行被物理删除，非本次审计点，未改动以控制 diff。

## 3. 主脚本合并清单（目标文件 + 锚点 + 待插入行）

**无**。本批次未修改 `script/sql/ry-*.sql`，未新增增量脚本。

## 4. DDL 登记（供主代理写入 doc/system/数据库增量变更记录.md）

**无**。本批次 4 项均为代码/前端改动，无表结构变更。

## 5. 验证记录（命令 + 结果，含新增测试类与用例数）

### 5.1 后端单测（命令照抄规范 §3）

```powershell
mvn -pl ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-station -am test -DskipTests=false "-Dtest=StationSnServiceTest,ProFlowCardServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

**结果：`BUILD SUCCESS`，`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`**

| 测试类 | 用例数 | 结果 | 本次新增用例 |
|---|---|---|---|
| `ProFlowCardServiceImplTest`（ruoyi-mes） | 8 | 全通过 | `checkFlashStepGate_skipsGateDisabledType`、`checkFlashStepGate_blocksWhenGateEnabledTypeHasNoPassRecord` |
| `StationSnServiceTest`（ruoyi-station） | 9 | 全通过 | `bind_casAffectsZeroRowsThrowsAndWritesNoLog`、`unbind_rebindCasAffectsZeroRowsThrowsAndWritesNoLog` |

> 测试侧两处必要适配：① `ProFlowCardServiceImplTest` 补 `@Mock MesFlashTypeMapper` + `ReflectionTestUtils.setField(service,"flashGateMode","FORCE")`（`@Value` 字段纯单测不注入，否则门禁整体提前 return，用例会"假通过"）+ `TableInfoHelper.initTableInfo(ProSn/MesFlashType)`；② 既有 `unbind_withNewComponent_rebindsInOneStep` 补 `when(proSnMapper.update(...)).thenReturn(1)`（Mockito 默认返回 0，现在 0 行会抛异常）。

### 5.2 前端

```powershell
cd plus-ui
npx eslint --fix src/api/mes/spc/control-chart.ts src/api/mes/flash/pool.ts src/views/mes/spc/control-chart/index.vue src/views/mes/flash/pool/index.vue
npx vue-tsc --noEmit
```

- `npx eslint --fix`：**0 error / 0 warning，退出码 0**（4 个文件）。
- `npx vue-tsc --noEmit`：**0 error，退出码 0**（无输出）。
- 未运行 `npm run build`（规范 §1.4 禁止）。

### 5.3 未覆盖的验证

- 未做端到端联调：`POST /mes/flash/pool/allocate` 的真实返回、SPC 分析弹窗次级图渲染需在浏览器里点一次（需运行环境 + 已登录）。
- 未跑 MES 全模块回归（主代理统一跑）。

## 6. 风险与回滚说明

### 6.1 风险

1. **MES-STATION-01 行为收紧（前端需知会）**：并发/双击场景下第二个请求由"静默成功（boundQty 虚增）"变为**抛业务异常"组件 SN 已被并发绑定, 请刷新重试"**。若产线终端对 500/异常提示不友好，会表现为"绑定失败"弹窗——属期望语义（原为脏数据）。
2. **`unbind` 换板路径新增失败分支**：解绑+重绑在同一事务内，重绑 CAS 0 行时**整体回滚**（旧组件仍绑定在原父SN 上），不会出现"解绑成功但新组件没绑上"的半成品状态。
3. **刷写门禁放宽**：`gate_enabled='0'` 的刷写类型不再拦截完工——这正是修复目标，但**若生产上存在误把 `gate_enabled` 配成 `'0'` 的类型**（本意是要拦的），该工序会立刻失去门禁保护。上线前建议核对 `SELECT type_code, gate_enabled FROM mes_flash_type`。
4. **SPC 分析按钮改 POST**：与后端一致；若浏览器/网关对 POST 有额外 CSRF 或幂等要求需留意（项目既有 POST 接口无 CSRF，风险低）。
5. **次级图数据来源**：`subValue` 依赖 MES-SPC-01 的 DDL（`spc_data_point.sub_value`）**已先行落库**；若该 DDL 未执行，后端 analyze 会报 `Unknown column`（属既有批次风险，非本批次引入）。

### 6.2 回滚

- **前端**：回退 4 个文件即可（`control-chart.ts`、`views/mes/spc/control-chart/index.vue`、`api/mes/flash/pool.ts`、`views/mes/flash/pool/index.vue`）。回退 `control-chart.ts` 会把 analyze 退回 GET（**必须与后端 `SpcControlChartController` 的 POST 同批回退**，否则 405）。
- **后端**：`StationSnService.java` 回退即恢复"不校验 rows"的旧行为（不推荐，会重现脏日志）；`ProFlowCardServiceImpl.java` 回退需同时移除 `MesFlashTypeMapper` 注入与 import。
- **测试**：`StationSnServiceTest`/`ProFlowCardServiceImplTest` 的回退须同步回退 `thenReturn(1)` 与 `ReflectionTestUtils` 两处适配，否则用例失败。
- 无数据库回滚项。
