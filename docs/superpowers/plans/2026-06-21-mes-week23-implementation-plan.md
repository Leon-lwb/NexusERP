# MES Phase 2 Week 23 开发实施计划 — 安灯系统（Andon + SSE 实时推送 + IoT 联动）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。

> **实施日期**: 2026-06-21
> **阶段**: MES Phase 2 — 安灯系统
> **状态**: ✅ 已完成（代码交付 + 静态验证通过）

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

主计划《NexusERP_MES文档》§10.2 将 Phase 2 划为：

| 周次 | 任务 |
|------|------|
| Week 22-23 | 流转卡引擎 |
| Week 24-25 | 派工 + 工序执行 |
| Week 26 | 安灯系统 |
| Week 27 | SN 追溯 |

但实际执行进度**领先**于主计划（流转卡 Week 20 完成、派工 Week 22 完成，详见 `docs/superpowers/plans/2026-06-20-mes-week22-implementation-plan.md` 的"明确排除项"：安灯 Week 23、SN Week 24）。因此 **Week 23 = 安灯系统**，对应主文档 §六（安灯系统）、§2.1 `pro_andon` 表、§14.4 `/mes/pro/andon` 接口、§12.4 MES↔IoT 安灯联动预留接口。

### 1.2 本周要解决的问题

1. 车间异常（质量/物料/设备/其他）目前无统一呼叫与可视通道。需落地 `pro_andon` 表 + 状态机（CALLED→RESPONDED→RESOLVED/CANCELLED），让操作员可发起呼叫、响应人可响应/解决。
2. 安灯状态变更需**实时**推送到前端安灯看板，避免人工刷新。复用 `ruoyi-common-sse` 的 Redis Pub/Sub 广播通道。
3. 主文档 §12.4 / §九预留的 **IoT→MES 安灯联动**接口 `IMesAndonService.createAndonFromAlert(IotAlert)` 已在 `ruoyi-common-iot` 中定义，且 IoT 侧 `IotAlertNotifier` 已留 `// TODO Phase2: mesAndonService.createAndonFromAlert(alert)` 调用点，目前由 `IotMesAndonServiceNoop`（`@ConditionalOnMissingBean`）兜底。本周由 `ruoyi-mes` 提供 `@Primary` 真实实现并**打通该 TODO**，闭合设备告警→安灯呼叫链路。

### 1.3 预期结果

- 车间操作员可在安灯看板/列表页发起呼叫、响应、解决、取消；状态机正确流转。
- 安灯任一状态变更通过 SSE（`type=ANDON`）实时推送到同租户在线用户的安灯看板，看板颜色编码区分呼叫类型。
- IoT 设备告警经 `IMesAndonService` 自动创建 `EQUIPMENT` 类型安灯呼叫，并在看板实时刷新。
- `mvn compile` 与前端 `eslint` 通过。

---

## 二、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | pro_andon DDL | 高 | `ry-mes-week23.sql` | 表结构对齐 Week22 约定 |
| 2 | ProAndon Entity/BO/VO/Mapper | 高 | 4 个 Java 文件 | 字段对齐 DDL |
| 3 | IProAndonService + 实现（状态机 + SSE 推送） | 高 | 接口 + 实现类 | call/respond/resolve/cancel + SSE 同租户广播 |
| 4 | MesAndonServiceImpl（实现 IMesAndonService，IoT 联动） | 高 | Java + pom 改动 | `createAndonFromAlert` + `@Primary` 覆盖 Noop |
| 5 | ProAndonController | 高 | REST 控制器 | list/get/stats/call/respond/resolve/cancel |
| 6 | 打通 IoT TODO | 中 | 改 `IotAlertNotifier` | 限频通过后联动安灯（try-catch 隔离） |
| 7 | 前端 andon API + 看板页 + sse.ts | 高 | 3 个文件 | SSE 实时刷新 + 颜色编码 + 新呼叫通知 |
| 8 | 菜单权限 SQL | 中 | `ry-vue-mes-menu-week23.sql` | 安灯菜单 4070-4075 |
| 9 | 编译 + lint 验证 | 高 | - | `mvn compile` + `eslint` 通过 |

---

## 三、交付物汇总（实际完成）

| 交付物 | 类型 | 文件路径 |
|--------|------|----------|
| `ry-mes-week23.sql` | SQL | `script/sql/ry-mes-week23.sql` |
| `ProAndon.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/domain/ProAndon.java` |
| `ProAndonBo.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/domain/bo/ProAndonBo.java` |
| `ProAndonVo.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/domain/vo/ProAndonVo.java` |
| `ProAndonMapper.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/mapper/ProAndonMapper.java` |
| `IProAndonService.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/service/IProAndonService.java` |
| `ProAndonServiceImpl.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/service/impl/ProAndonServiceImpl.java` |
| `MesAndonServiceImpl.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/service/impl/MesAndonServiceImpl.java` |
| `ProAndonController.java` | Java | `ruoyi-modules/ruoyi-mes/.../pro/controller/ProAndonController.java` |
| `ruoyi-mes/pom.xml`（+common-iot） | 改 | `ruoyi-modules/ruoyi-mes/pom.xml` |
| `IotAlertNotifier.java`（启用 TODO） | 改 | `ruoyi-modules/ruoyi-iot/.../IotAlertNotifier.java` |
| `andon.ts` | TS | `plus-ui/src/api/mes/pro/andon.ts` |
| `andon/index.vue` | Vue | `plus-ui/src/views/mes/pro/andon/index.vue` |
| `utils/sse.ts`（+ANDON 分支） | 改 | `plus-ui/src/utils/sse.ts` |
| `ry-vue-mes-menu-week23.sql` | SQL | `script/sql/ry-vue-mes-menu-week23.sql` |

---

## 四、实施中相对计划的两处细化

1. **`callAndon` 移除 `@Lock4j`**：原计划按 `#cardId` 加锁，但 IoT 路径 `cardId` 为 null 会触发 Lock4j 空键问题；呼叫是纯插入操作无并发危险，故仅在 `respondAndon`/`resolveAndon`（按 `andonId`，非空）保留锁防状态转换并发。
2. **`MesAndonServiceImpl` 增 try-catch**：`createAndonFromAlert` 内部捕获异常返回 null，确保 IoT 侧即使安灯创建失败也不阻断告警推送主流程。

---

## 五、验证结果

| 验证项 | 命令 | 结果 |
|--------|------|------|
| MES 后端编译 | `mvn compile -pl ruoyi-mes -am` | ✅ EXIT 0 |
| IoT 后端编译 | `mvn compile -pl ruoyi-iot -am` | ✅ EXIT 0 |
| 前端 Lint（全 MES 范围） | `eslint src/views/mes src/api/mes src/utils/sse.ts` | ✅ EXIT 0 |

---

**文档生成时间**: 2026-06-21
**执行者**: AI Agent (Subagent-Driven Development)
**静态验证**: ✅ 通过
**运行时验证**: ⏳ 待人工执行
