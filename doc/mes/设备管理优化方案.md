# MES 设备管理优化方案

> 文档版本: v1.3 ｜ 更新日期: 2026-07-16
> 适用模块: `ruoyi-mes` / `eq` 子包 ｜ 前端: `plus-ui/src/views/mes/eq`

## 一、现状速览

### 1.1 已具备能力（基础扎实）

| 能力 | 实现状态 | 说明 |
|------|----------|------|
| 设备台账管理 | 完整 | CRUD + 编码唯一校验 + 分页查询 |
| 设备状态机 | 基本完整 | 4 态(NORMAL/MAINTENANCE/FAULT/SCRAPPED) + 报修/维修自动联动 + 终态保护 |
| 维修流程闭环 | 完整 | 报修→开工→完成, CAS 防并发 |
| 预防性保养计划 | 部分 | 周期计划 + 自动计算下次日期 + 到期 SnailJob 提醒, 但不生成工单 |
| 设备点检 | 基本 | JSON 配置点检项 + 结果提交 + 不合格触发安灯, 但无点检计划/排程 |
| IoT 设备关联 | 完整 | 双向关联 + 实时状态/数据查询 |
| 安灯联动 | 完整 | 点检不合格/保养到期/IoT 告警均触发安灯 |
| 多租户隔离 | 完整 | TenantEntity + @DS("mes") + TenantHelper.dynamic |
| 操作日志/权限 | 完整 | 所有写操作带 @Log + @SaCheckPermission |

### 1.2 核心缺失（对标标准 MES）

| 能力域 | 状态 | 关键差距 |
|--------|------|----------|
| OEE / 稼动率 / MTBF / MTTR | ❌ 完全缺失 | 有 `downtime_hours` 却无聚合计算 |
| 备件管理 | ❌ 完全缺失 | 维修成本仅金额, 无备件明细/库存 |
| 保养工单闭环 | ⚠️ 半成品 | 计划到期只提醒, `completePlan` 不生成执行记录, Controller 未暴露 |
| 实时监控 | ⚠️ 仅快照 | 项目有 `IotRealtimeSseManager` 但 eq 页面未集成 SSE |
| 故障分析 | ❌ 缺失 | 有 `fault_type` 无统计/RCA/知识库 |
| 设备-工单联动 | ❌ 缺失 | 工单不关联设备, 故障不影响排产 |
| MQ 事件广播 | ❌ 缺失 | 状态变更无事件, 他模块无法异步感知 |
| 巡检 / 校准 / 能耗 | ❌ 缺失 | - |

### 1.3 前后端共性问题（技术债）

- 状态/类型硬编码未走字典系统（5 处自标注"技术债"）
- 设备 ID / 工作中心 ID 手动数字输入
- IoT 状态 N+1 查询（逐行串行 `getIotStatus`）
- 维修完成 3 连 `prompt` 弹窗
- 无设备详情页 / 无导出
- 点检项 JSON 在 textarea 编辑
- 多租户定时任务硬编码 `000000`
- `collectOnlineUsers()` 返回空集致 SSE 不工作
- 异常用 `RuntimeException` 而非 `ServiceException`

---

## 二、方案总览（三层递进）

```
P0 基础完善(补短板、修技术债)  ── 低风险, 1-3 周
   └─ 字典化 / 选择器 / 详情页 / 导出 / N+1 / 保养工单闭环 / MQ事件 / 多租户定时任务

P1 核心能力(对标标准 MES)     ── 中风险, 2-4 周
   └─ OEE&稼动率看板 / 备件管理 / 巡检管理 / 故障统计 / 实时监控大屏 / 设备-工单联动

P2 高级分析(智能化)           ── 较高, 4+ 周
   └─ MTBF/MTTR / 停机原因分析 / 预测性维护PdM / 故障知识库 / 能耗管理
```

---

## 三、P0 基础完善（补短板 + 修技术债）

### P0-1 状态/类型字典化（消除 5 处技术债）

**问题**: 设备状态、设备类型、保养类型、维修状态、故障类型在查询/编辑/列表三处硬编码 el-tag。

**方案**:
- 后端在 `sys_dict_type`/`sys_dict_data` 新增字典: `mes_equipment_status`、`mes_equipment_type`、`mes_maintenance_type`、`mes_repair_status`、`mes_fault_type`、`mes_downtime_reason`。
- 前端 4 个页面统一改用 `<dict-tag>` + `useDict` 组合式 API, 查询/表单/列三处复用同一字典。
- 落地: `script/sql/update/` 新增 `eq-dict.sql`。

### P0-2 设备/工作中心选择器（消除手动输 ID）

**问题**: 报修、保养计划、采集点均 `el-input v-model.number` 手动输设备 ID; 设备档案表单手动输工作中心 ID。

**方案**:
- 封装通用 `<equipment-select>` 组件（remote 搜索 + 弹窗表格）。
- 封装 `<workcenter-select>`, 后端复用 `ErpWorkstationMapper`, 新增 `/mes/eq/equipment/workcenters` 只读接口（`@DS("erp")`）。
- 替换 repair/maintenance/dc-collection-point 表单。

### P0-3 IoT 状态批量查询（消除 N+1）

**问题**: `equipment/index.vue` 对每行设备串行调 `getIotStatus`, 10 行最多 10 次请求。

**方案**:
- 后端新增 `GET /mes/eq/equipment/iotStatus/batch?equipmentIds=1,2,3` 返回 `Map<Long,String>`。
- `IotDeviceDataQueryService` 增 `batchStatus` 方法（内部并发查询）。
- 前端列表加载后单次批量请求填充。

### P0-4 保养工单闭环（补全半成品）

**问题**: `IEqMaintenancePlanService.completePlan` 存在但 Controller 未暴露; 执行无记录留痕; 到期不自动生成工单。

**方案**:
- 新增实体 `EqMaintenanceRecord`（保养执行记录）: `record_id, plan_id, equipment_id, executor_id, executor_name, execute_time, content, result(NORMAL/ABNORMAL), remark, del_flag`。表 `eq_maintenance_record`。
- `MaintenanceDueTask` 到期时自动生成一条 `PENDING` 保养工单（`PENDING→DOING→DONE`）, 触发安灯 + SSE。
- 暴露 `POST /mes/eq/maintenance-plan/{planId}/complete` 接口, 写执行记录 + 推进 `nextDate` + 异常则置设备状态。
- 前端 maintenance 页面增"执行保养"按钮 → 表单对话框。

### P0-5 设备状态变更 MQ 事件广播

**问题**: 设备故障/恢复无 MQ 事件, 排产/工单/安灯无法异步感知。

**方案**:
- `QueueConstants` 新增 `MES_EQUIPMENT_STATUS_QUEUE` + Exchange。
- `EqEquipmentServiceImpl` 状态变更点（报修置 FAULT、维修完成恢复、点检不合格、手动改状态）发 `EquipmentStatusEvent{equipmentId, oldStatus, newStatus, reason, time}`。
- MQ 消费者写多租户表必须 `TenantHelper.dynamic` 恢复上下文。

### P0-6 维修完成表单化 + 维修记录显示设备名

**问题**: 维修完成连续 3 次 `prompt` 弹窗; 列表不显示设备名称。

**方案**:
- 前端"完成"改为 `<el-dialog>` 表单: 停机时长、维修成本、故障原因（字典）、备注。
- `EqRepairVo` 增加 `equipmentName`/`equipmentCode` 字段, Service 批量补填。
- 列表新增"设备名称"列。

### P0-7 其余技术债批量清理

| 项 | 方案 |
|----|------|
| 设备详情页 | 新增 `equipment/detail.vue`: 基本信息 + IoT 实时 + 最近点检 + 维修/保养历史 tabs |
| 导出 | 三个 Controller 补 `@PostMapping("/export")` + FastExcel; 前端补导出按钮 |
| 点检页面分页 | `inspection/index.vue` queryParams 补 `pageNum/pageSize` |
| 点检项结构化编辑 | 设备表单中点检项改为动态增删行表格, 替代 textarea |
| 批量操作 | 4 个表格加 selection 列 + 批量删除/导出按钮 |
| 异常规范 | `RuntimeException` → `ServiceException` |
| 多租户定时任务 | `MaintenanceDueTask` 改为遍历有效租户列表 |
| SSE 修复 | `collectOnlineUsers()` 接入真实在线用户集, 或移除占位代码 |

---

## 四、P1 核心能力（对标标准 MES）

### 4.1 P1 实施原则（先共用骨架, 后分项落地）

P1 不建议把 OEE、备件、实时监控、故障统计、巡检、工单联动六个主题并行散做, 而应先补一层设备域共用骨架, 再按业务价值和依赖顺序逐项推进。

**统一骨架建议**:
- 统一查询维度: 设备 / 工作中心 / 时间范围 / 班次。
- 统一统计口径: 设备状态、故障、停机时间段、当前工单上下文。
- 统一前端图表 DTO: KPI 卡片、趋势、分布、TOP、明细表, 便于多页面复用。
- 统一实时事件语义: 设备状态变化、IoT 最新参数、故障告警、工单关联变化。

**优先复用现有能力**:
- `plus-ui/src/components/EquipmentSelect/index.vue`
- `plus-ui/src/views/mes/eq/equipment/index.vue`
- `plus-ui/src/views/mes/eq/maintenance/index.vue`
- `plus-ui/src/views/mes/pro/andon/index.vue`
- `plus-ui/src/views/mes/spc/control-chart/index.vue`
- `plus-ui/src/views/mes/pro/workorder/detail.vue`
- `ruoyi-modules/ruoyi-iot/.../IotRealtimeSseManager.java`
- `ruoyi-modules/ruoyi-mes/.../MesMqSendService.java`

### P1-1 OEE & 稼动率看板（最高价值, 第一迭代）

**目标**: 可用率 × 表现率 × 质量率 = OEE, 支持设备级 / 工作中心级 / 时段级看板。

**落地策略**: 首版采用"稼动率优先, OEE 渐进增强", 不强求一步到位的完整公式, 先把现有数据源串起来形成稳定看板。

**首版数据源**（利用现有能力, 避免重复采集）:
- 运行时长 / 停机时长: 优先复用设备状态变化、`eq_repair.downtime_hours`、保养执行记录, 后续再补停机事件表。
- 计划生产时长: 来自工单或排产班次。
- 产量 / 不良品: 来自工单报工或质量结果, 数据不完整时可先隐藏表现率/质量率, 优先输出稼动率与停机分析。

**新增表**: `eq_oee_record`（日 / 班次粒度聚合）。

**后端**:
- 新增 `EqOeeService` + SnailJob 定时聚合任务。
- 在 `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq` 下新增 OEE 领域实体、Mapper、Service、Controller。
- 若需要工单计划时长 / 产量归因, 优先复用 `pro` 包现有工单/报工服务, 避免重复写统计 SQL。

**前端**:
- 新增 `views/mes/eq/oee/index.vue`。
- 统计卡片布局复用 `mes/pro/andon` 页面模式。
- 图表交互复用 `mes/spc/control-chart` 页面中的 ECharts 分析模式。

### P1-2 备件管理（第二迭代）

**定位**: 首版做成"设备侧轻库存闭环", 不一开始就扩成完整 WMS 子系统。

**新增表**:
- `eq_spare_part`（主数据）
- `eq_spare_stock`（库存 / 流水, 或按项目情况复用 wm）
- `eq_repair_spare`（维修领用明细）

**首版业务闭环**:
- 新增备件主数据
- 备件入库 / 出库 / 查询库存余额
- 维修完成表单增加"更换备件"子表
- 维修领用后自动扣减库存

**联动扩展**（可作为 1.1 / 1.2 增强）:
- 领用触发 ERP 出库
- 低于安全库存触发安灯 / 采购申请

**前端建议**:
- 新增 `views/mes/eq/spare-part/index.vue`
- 新增 `views/mes/eq/spare-stock/index.vue`
- 在 `views/mes/eq/repair/index.vue` 的完成维修表单中扩展备件明细子表

### P1-3 巡检管理（区别于点检, 第四迭代之一）

点检 = 班前班后开机检查（已有）; 巡检 = 按路线定时巡查（新增）。

**边界原则**:
- 当前 `inspection/index.vue` 是设备级点检流程, 仅适合班前班后自检。
- 巡检不能直接复用 `inspectionItems` 的 JSON 嵌入式模型, 必须独立建模。

**新增表**:
- `eq_patrol_route`
- `eq_patrol_task`
- `eq_patrol_record`

支持拍照（存 `ossId`, 展示时通过 VO / 翻译层解析 URL）。

**落地方式**:
- 路线 / 任务 / 记录三层结构拆开实现。
- 执行页可借鉴保养计划页"计划 + 执行弹窗"模式。
- 巡检异常优先复用现有 repair / andon 闭环, 不另造一套异常处理流。

### P1-4 实时监控大屏（激活已有 SSE 能力, 第三迭代之一）

新增 `views/mes/eq/monitor/index.vue`: 设备状态总览 + 绑定 IoT 设备实时参数（订阅 SSE）+ 故障高亮一键报修 + 可选当前工单上下文。

**关键原则**:
- 复用 `IotRealtimeSseManager`, 不再新造 SSE / WebSocket 通道。
- 页面首屏走快照接口, 增量变化走 SSE 事件刷新。
- 前端事件监听模式复用 `mes/pro/andon` 页面现有 CustomEvent + Notification 机制。

**推荐内容块**:
- 设备状态统计卡
- 异常设备清单
- 工作中心设备分布
- 最新 IoT 参数概览
- 当前故障 / 报修入口

### P1-5 故障统计分析（第三迭代之二）

新增 `GET /mes/eq/repair/statistics`。

**首版统计口径**:
- 以维修记录为第一权威数据源
- 输出故障趋势折线、故障类型 Pareto、设备故障 TOP10、平均维修时长、MTBF / MTTR 雏形

**前端建议**:
- 统计图表优先复用 `mes/spc/control-chart` 中的 ECharts 渲染模式
- 结果页可作为 repair 模块独立统计页, 或在 `repair/index.vue` 增加"故障分析"入口

### P1-6 设备-工单联动（第四迭代之二）

**实施顺序**: 关系模型需尽早定义, 但完整功能可放在 P1 末段实施。

**首版目标**:
- `ProWorkOrder` 增加设备关联能力（`equipment_id` 字段或独立关联表）
- 支持工单详情与设备当前工单上下文双向查询
- 设备故障事件可映射为工单"设备异常"提示

**后续增强**:
- 排产考虑设备产能日历
- 设备 FAULT（MQ 事件）→ 排产 / 工单标记受影响状态
- 若后续要细化到工序步骤, 建议接口一开始就预留可扩展结构, 不锁死在单表头字段语义上

---

## 五、P2 高级分析（智能化）

| 项 | 方案要点 |
|----|----------|
| MTBF/MTTR 正式计算 | 基于 `eq_repair` 故障间隔与维修时长, Service 聚合, 看板展示 |
| 停机原因分析（5M1E） | `eq_downtime_event` 表, 停机原因按设备/物料/人员/方法/环境分类, Pareto 分析 |
| 预测性维护 PdM | 基于 IoT 历史数据 + 阈值规则引擎, 触发预警工单; 后续接 AI 异常检测 |
| 故障知识库 / RCA | 维修记录沉淀为知识库, 关联 `fault_type`; 接 AI 模块做"故障→处置建议"检索 |
| 设备校准管理 | `eq_calibration_plan` + `eq_calibration_record` |
| 能耗管理 | IoT 采集电量 → 设备能耗分析, 与 OEE 关联 |
| Flowable 审批流 | 大额/外协维修走 Flowable 审批 |

---

## 六、数据模型扩展汇总（新增表）

```
eq_maintenance_record   保养执行记录      (P0-4)
eq_spare_part           备件主数据        (P1-2)
eq_spare_stock          备件库存          (P1-2, 或复用 wm)
eq_repair_spare         维修领用明细      (P1-2)
eq_patrol_route         巡检路线          (P1-3)
eq_patrol_task          巡检任务          (P1-3)
eq_patrol_record        巡检记录          (P1-3)
eq_oee_record           OEE 聚合记录      (P1-1)
eq_downtime_event       停机事件          (P2)
eq_calibration_plan     校准计划          (P2)
eq_calibration_record   校准记录          (P2)
```

均继承 `TenantEntity`、`@DS("mes")`、雪花 ID、逻辑删除。新增 MQ 通道集中声明于 `QueueConstants`。

---

## 七、落地路线图

```
Week 1-2  (P0)  字典化 + 选择器 + 详情页 + 导出 + N+1 + 异常规范 + 多租户定时任务 + SSE 修复
Week 3    (P0)  保养工单闭环 + MQ 事件广播 + 维修表单化
─────────── 基础完善闭合, 设备管理达到"合格 MES"水准 ───────────
Week 4-5  (P1)  OEE 看板(数据源接入 + 聚合任务 + ECharts 大屏)
Week 6    (P1)  备件管理(主数据 + 库存 + 维修领用联动)
Week 7    (P1)  实时监控大屏 + 故障统计
Week 8    (P1)  巡检管理 + 设备-工单联动
─────────── 核心能力闭合, 达到"标准 MES 设备管理"水准 ───────────
Week 9+   (P2)  MTBF/MTTR + 停机分析 + PdM + 知识库 + 校准 + 能耗(按需)
```

---

## 八、关键避坑（项目特性约束）

1. **多租户上下文**: 所有 SnailJob 定时任务、MQ 消费者写多租户表必须 `TenantHelper.dynamic(tenantId, ...)` 遍历租户, 不可硬编码 `000000`。
2. **`@DS("mes")` 切换**: 必须标注在被实际调用的 Service 方法上, 同类内部方法调用不触发 AOP 切换; 跨库查 ERP 工作中心用独立 Mapper + `@DS("erp")`。
3. **雪花 ID**: 全局 `BigNumberSerializer` 已根治前端精度, 新接口无需处理, 切勿在局部 ObjectMapper 绕过。
4. **OSS 存储照片**: 存 `ossId` 不存 URL, VO 用 `@Translation(OSS_ID_TO_URL)` 解析。
5. **MQ 通道**: 新增设备状态队列在 `QueueConstants` 集中声明, 与 ERP 现有消费者风格一致。
6. **异常处理**: 用 `ServiceException` 而非 `RuntimeException`; 新增 `@RestControllerAdvice` 注意 `@Order` 优先级。
7. **API 加密**: 新增接口默认受 API 加密保护, 前后端配置需一致。
8. **vue-tsc 校验**: 改动后用 `grep` 自己文件路径校验, 不能凭退出码判断（baseline 约 1580 既有错误）。

---

## 九、起步建议

P0 全部为低风险技术债清理 + 闭环补全, 1-3 周内可闭合, 且为 P1（OEE 数据源、设备-工单联动）铺路。

**优先级**: P0-4 保养工单闭环 > P0-5 MQ 事件 > P0-1 字典化 > 其余。

---

## 十、代码审查遗留项（2026-07-14 P0/P1 审查）

本轮 P0/P1 代码审查共发现并修复 **2 Critical + 8 Medium + 1 Low**（后端 `mvn compile` EXIT 0、前端 `eslint` EXIT 0）。以下为本轮审查中**评估后暂缓**的遗留项，按是否值得后续投入排序。

### 10.1 已于本轮修复（曾列入"暂缓"，现已落地）

| 项 | 文件 | 说明 |
|----|------|------|
| M6 报修号并发 | `EqRepairServiceImpl.generateNo` | 改用 `SequenceUtils.getNextId`（Redisson 原子发号器，按日 key 次日重置），消除 `selectCount+1` 在并发报修下的序号碰撞（此前仅靠 uk 唯一索引兜底重试） |
| M1 MQ 事件事务前发送 | `EqEquipmentServiceImpl.updateStatus` | 抽取 `publishStatusEvent`，注册 `TransactionSynchronization.afterCommit`，仅事务提交后发 MQ，消除回滚幻影事件（对齐 `QcIqcServiceImpl` 既有模式；租户 ID 注册前捕获） |
| M3 设备恢复 TOCTOU | `EqEquipmentMapper.resetToNormalIfNoActiveRepair` + `completeRepair` | 单条 `UPDATE ... NOT EXISTS` 原子恢复 NORMAL，消除"查活跃报修数 -> 更新设备状态"两步竞态（完成瞬间插入新报修导致误恢复）。`equipment_id` 雪花全局唯一，子查询按设备维度天然隔离租户 |

> 运行时验证状态: 后端编译 EXIT 0；M6/M1 需重启 `ruoyi-admin` + 有真实报修流量验证；M3 残留 READ COMMITTED 下的未提交幻影（同设备完成瞬间恰好有未提交的新报修插入）窗口极窄，已属可接受残留。

### 10.2 建议后续开发（有明确价值，需独立工时）

#### 10.2.1 P0-FE #5 保养计划状态字典化
- **现状**: `maintenance/index.vue` 保养计划 status、设备类型/故障类型等在查询/表单/列三处硬编码 `el-tag` 颜色映射（P0-1 已规划但 eq 模块尚未落地）。
- **为什么遗留**: 需新增 `sys_dict_type`/`sys_dict_data`，且**主键走雪花算法** —— 增量 SQL 必须用 `@var:=@var+1` 动态分配 `dict_code`，硬编码小 ID 必冲突（见避坑 §八.3）。
- **建议方案**: 落地 `script/sql/update/eq-dict.sql`，新增 `mes_maintenance_status`/`mes_equipment_type`/`mes_fault_type`/`mes_repair_status` 等字典；前端 4 页面统一 `dict-tag` + `useDict`。
- **工作量**: 中（SQL + 4 页面改造）。**优先级**: 中。

#### 10.2.2 monitor 监控大屏 500 行上限
- **现状**: `monitor/index.vue` 的 `loadAll` 用 `listRepair` 拉取维修列表做看板统计，接口默认分页上限 500 行；维修量增长后活跃报修/故障统计会漏算。本轮已先做"按状态分流两次查询"缓解漏算，但上限未根治。
- **为什么遗留**: 需后端新增按状态分组的 count 汇总接口（不能复用分页 list），前端拆"列表展示"与"统计计数"两条数据通道。
- **建议方案**: `EqRepairController` 增 `GET /mes/eq/repair/summary` 返回 `Map<status,count>`；monitor 用 summary 做统计、用 list 做明细。
- **工作量**: 小（1 接口 + 前端拆通道）。**优先级**: 中。

#### 10.2.3 P1-FE M7 巡检拍照功能
- **现状**: 巡检记录（`eq_patrol_record`）无照片字段，巡检异常无法留影像证据。
- **为什么遗留**: 属**新功能**而非 bug；需 DB 迁移 + 照片字段 + FE 上传组件。
- **建议方案**: `eq_patrol_record` 增 `photo_oss_ids`（JSON 数组存多个 ossId）；VO 用 `@Translation(OSS_ID_TO_URL)` 解析；前端巡检执行页加 `el-upload` 多图上传。**关键坑**: OSS 桶 private，字段必须存 ossId 不能存 URL（见避坑 §八.4）。
- **工作量**: 中（DB + BE + FE 组件）。**优先级**: 低（下一迭代特性）。

### 10.3 保持搁置（当前无独立价值）

#### 10.3.1 L4 collectOnlineUsers SSE 占位
- **现状**: 安灯/监控看板的"在线用户"统计 `collectOnlineUsers()` 返回空集，SSE 在线推送依赖此占位（见 §1.3 技术债）。
- **为什么搁置**: 安灯看板的实际刷新触发靠页面主动轮询/SSE 事件，在线用户集当前无独立消费方；为占位建一套在线用户追踪收益低。
- **建议**: 待 P1-4 实时监控大屏真正需要"在线人数"展示时再接入真实在线用户集，或移除占位代码。**当前不动**。

---

## 十一、R7-R11 + R1-R6 落地与剩余项（2026-07-16）

经前后端+SQL 三层盘点，P0/P1 主体（DB 表/后端 Service·Controller/前端页面）均已落地。2026-07-16 分两批完成收尾/技术债清理与核心能力补全：第一批 R7-R11（字典/选择器/导出/批量等收尾），第二批 R1-R6（维修-备件联动/监控 summary/设备-工单/MQ 消费者/设备详情/巡检拍照）。剩余仅 R8 延期项 + 两项有意简化。

### 11.1 第一批已完成（R7-R11）

| 编号 | 功能点 | 落地内容 |
|------|--------|----------|
| R7 | 故障统计入口 | 修复"故障分析"菜单挂载点：原挂在 C 型"维修记录"(4120) 下，`buildMenus` 对 C 型父菜单不递归生成子路由致其路由丢失；移至目录型"设备管理"(4003) 后作为 `/mes/eq/statistics` 正常访问。`repair/index.vue` 新增"故障分析"按钮跳转。 |
| R8 | 字典化收尾 | 新增 `mes_maintenance_status` 字典(启用/停用)；`maintenance` 页状态查询/列/表单三处硬编码改 `dict-tag`+字典；`monitor` 状态列去 `statusTagType/statusLabel` 硬编码改 `dict-tag`。 |
| R9 | 选择器收尾 | `dc/collection-point` 表单换 `<equipment-select>`(消除手动输设备 ID)；新增 `WorkcenterSelect` 组件，`equipment` 页内联工作中心远程搜索改用组件(同时简化编辑回显逻辑)。 |
| R10 | 导出补齐 | `maintenance-record` / `spare-stock-log` 后端补 `export` 接口 + Service 增 `selectList`/`selectStockLogList`；前端补导出按钮 + API；新增 `mes:eq:maintenance:record:export` / `mes:eq:spare:log:export` 权限。 |
| R11 | 批量操作 | `equipment` / `maintenance` 表格加 selection 列 + 批量删除按钮。`repair`(状态流，无删除语义)/`inspection`(点检执行页，无删除) 不适用批量删除，已排除。 |

**SQL**：`ry-vue.sql` 同步字典/菜单/权限变更；`script/sql/update/2026-07-16-eq-r7-r8-r10-fixes.sql` 供已部署环境增量执行（主键用 `MAX+1` 动态分配，避免雪花 ID 冲突）。

**验证**：后端 `mvn -pl ruoyi-modules/ruoyi-mes -am compile` EXIT 0；前端 `eslint`（改动文件）EXIT 0；vue-tsc 见 §11.3。

### 11.2 第二批已完成（R1-R6，2026-07-16）

| 编号 | 功能点 | 落地内容 |
|------|--------|----------|
| R1 | 维修-备件联动闭环 | `completeRepair` 改 `@RequestBody EqRepairCompleteBo`(含 spares)，事务内调 `EqRepairSpareService.batchAdd`(已含 REPAIR_USE 扣库存 + 写 eq_repair_spare)；前端维修完成对话框加"更换备件"子表(备件下拉+数量)。 |
| R2 | 监控大屏 summary | 后端 `GET /repair/summary` + `GET /equipment/summary`(全量 COUNT，不受 pageSize 限制)；前端 monitor 统计卡片改用 summary(修复 >500 台/ >100 单漏算)，明细列表保留轮询。**SSE 评估后不接入**：`IotRealtimeSseManager` 为按设备单连接(单设备实时视图)，不适合多设备大屏；全局 SSE 推送依赖 `collectOnlineUsers`(§10.3.1 占位待修)。 |
| R3 | 设备-工单联动收尾 | `IEqEquipmentService.currentWorkOrders` + `GET /equipment/{id}/currentWorkOrder`，查 `pro_work_order` 中 equipment_id 匹配且 wo_status in (RELEASED, IN_PROGRESS) 的工单。 |
| R4 | MQ 事件消费者 | 新增 `EquipmentStatusConsumer`(继承 `BaseMessageConsumer`)，监听 `MES_EQUIPMENT_STATUS_QUEUE`；设备转 FAULT 时查受影响进行中工单，有则触发 EQUIPMENT 安灯(每设备 1h 限频防抖动)；租户上下文从消息恢复。 |
| R5 | 设备详情页 | `equipment/index.vue` 新增"详情"按钮 + el-drawer 多 Tab(基本信息/IoT 实时/最近点检/维修历史/保养历史)，复用 `getEquipment`/`getIotLatest`/`listRepair`/`listMaintenanceRecordByEquipment`。(以 drawer 实现，避免单独路由/菜单开销) |
| R6 | 巡检拍照上传 | `EqPatrolRecordVo` 增 `photoUrl` + `@Translation(OSS_ID_TO_URL)`(BO 已有 `photoOssId`)；`patrol-task` 执行对话框加 ImageUpload(单张, 存 ossId)；`patrol-record` 列表加照片缩略图(el-image 预览)。OSS 桶 private，存 ossId 不存 URL。 |

### 11.3 剩余待办（下次继续）

#### 11.3.1 R8 延期项：维修完成表单接入 `mes_downtime_reason`
- **现状**：`mes_downtime_reason` 字典已存在(5M1E：设备/物料/人员/方法/环境/其他)，但 `eq_repair` 表无 `downtime_reason` 列，维修完成表单未接入。
- **延期原因**：需 `ALTER eq_repair` 加列 + `EqRepair` 实体/Bo/Vo + `completeRepair` 流程改动，属 DB 迁移 + 关键流程改动，与 P2 停机原因分析(5M1E)强相关，留待 P2 一并落地更合理。
- **建议**：P2 停机原因分析时落地（`eq_repair` 加 `downtime_reason` + 完成表单加停机原因字典字段 + Pareto 统计）。

#### 11.3.2 两项有意简化（评估后维持现状/按需补全）
- **P0-4 保养工单状态机**：现为"到期 SnailJob 提醒 + 人工 `completePlan` 写 `eq_maintenance_record`"，未实现 `PENDING->DOING->DONE` 自动工单流。当前闭环可用，按需补全。
- **P0-3 IoT 真批量**：前端已单次 HTTP 请求（消除前端 N+1），但 IoT 层 `IotDeviceDataQueryService` 仍逐条 `getOnlineStatus`。设备量大时在 IoT 层补 `batchStatus` 真批量方法。

#### 11.3.3 R2 SSE 增强（可选）
- monitor 实时推送待 `collectOnlineUsers`(§10.3.1) 修复后，由 `EquipmentStatusConsumer` 额外发 SSE(type=EQUIPMENT_STATUS) -> 前端订阅刷新，替代 30s 轮询。当前 summary + 轮询已保证计数准确，SSE 为锦上添花。

### 11.4 验证状态
- 后端：`mvn -pl ruoyi-modules/ruoyi-mes -am compile` EXIT 0（R1/R3/R4 + R2/R6 BE 全部通过）。
- 前端：`eslint` 全部改动文件 EXIT 0；`vue-tsc --noEmit` 对改动文件路径无新增报错（按 §八.8 以路径 grep 校验；既有 baseline 报错为 `proxy.resetForm` 类型 + 雪花 ID `string|number` 摩擦，与本轮改动无关）。
- 运行时：R4 消费者需重启 `ruoyi-admin` + 真实设备 FAULT 流量验证；R1 维修完成备件扣库存需真实备件数据验证；R6 照片上传依赖 OSS 配置；菜单/权限变更需刷新路由 + 角色分配。
