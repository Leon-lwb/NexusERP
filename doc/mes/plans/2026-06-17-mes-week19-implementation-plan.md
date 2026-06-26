# MES Phase 1 Week 19 开发实施计划

> **实施日期**: 2026-06-18
> **阶段**: MES Phase 1 - 只读前端完善 + Flowable 审批流准备
> **状态**: 待审核

**目标：** 完善 MES 侧主数据只读前端页面（物料/BOM/工艺路线），为 MES 工单集成 Flowable 审批流做准备（接口扩展、前端审批组件、submit/proc 接口），并补充工单编辑弹窗。

**前置条件：** Week 18 已完成 MES 工单 CRUD 全链路 + ERP 桥接服务 + 工单列表/详情前端。

---

## 一、任务清单

| 任务 | 优先级 | 交付物 | 验收标准 |
|------|--------|--------|----------|
| MES 主数据只读页面（物料） | 高 | `mes/md/material/readonly.vue` | 调 ERP API 显示物料列表，支持搜索 |
| MES 主数据只读页面（BOM） | 高 | `mes/md/bom/readonly.vue` | 调 ERP API 显示 BOM 列表 |
| MES 主数据只读页面（工艺路线） | 高 | `mes/md/routing/readonly.vue` | 调 ERP API 显示工艺路线列表 |
| Service 接口扩展 Flowable | 高 | `IProWorkOrderService extends WfCallBackServiceI` | 新增 submit/getProcInsId/afterFlowHandle |
| Service 实现 Flowable 回调 | 高 | `ProWorkOrderServiceImpl` 扩展 | 实现 4 个回调方法 + submit + getProcInsId |
| Controller 新增审批接口 | 高 | `ProWorkOrderController` 扩展 | POST /{id}/submit + GET /{id}/proc |
| 前端审批组件集成 | 中 | 工单详情页增加审批区 | 审批流选择器 + 提交按钮 + 查看流程按钮 |
| 工单编辑弹窗 | 中 | `workorder/form.vue` 弹窗版 | 支持新增/编辑工单（手动创建场景） |
| 前端 API 补充 | 中 | `workorder.ts` 补充 submit/proc 接口 | 前端可调用审批接口 |
| 菜单权限补充 SQL | 低 | `ry-vue-mes-menu-week19.sql` | 补充主数据只读菜单 + submit/proc 按钮 |
| 编译验证 | 高 | `mvn compile` 通过 | 无编译错误 |

---

## 二、详细任务分解

### 任务 1：MES 主数据只读页面（物料/BOM/工艺路线）

**思路：** MES 前端直接调用 ERP 已有 API，不走 MES 后端桥接。页面为只读浏览，无增删改操作。

**Files:**
- Create: `plus-ui/src/api/mes/md/material.ts`
- Create: `plus-ui/src/api/mes/md/bom.ts`
- Create: `plus-ui/src/api/mes/md/routing.ts`
- Create: `plus-ui/src/views/mes/md/material/index.vue`
- Create: `plus-ui/src/views/mes/md/bom/index.vue`
- Create: `plus-ui/src/views/mes/md/routing/index.vue`

**API 文件：** 封装 ERP 已有接口：
- 物料: `GET /erp/bd/material/list`
- BOM: `GET /erp/bd/bom/list`
- 工艺路线: `GET /erp/bd/routing/list`

**页面功能：**
- 列表展示（分页）
- 搜索栏（编码/名称）
- 只读，无编辑/删除按钮

---

### 任务 2：IProWorkOrderService 接口扩展 Flowable

**Files:**
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/IProWorkOrderService.java`

**改动内容：**

```java
public interface IProWorkOrderService extends WfCallBackServiceI {
    // ... 已有方法 ...

    /** 提交审批 */
    boolean submit(String mesWoIdStr, String selectFlowId);

    /** 查询流程实例ID */
    String getProcInsId(String mesWoIdStr);
}
```

---

### 任务 3：ProWorkOrderServiceImpl 实现 Flowable 回调

**Files:**
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImpl.java`

**新增依赖注入：**
- `IWfProcessService processService`
- `WfMyBusinessServiceImpl wfMyBusinessServiceImpl`
- `RuntimeService runtimeService`
- `HistoryService historyService`

**实现方法：**

| 方法 | 逻辑 |
|------|------|
| `submit(mesWoIdStr, selectFlowId)` | 校验状态(CREATED) → 构建变量(dataId, requireTaskOnStart) → 调用 `processService.startProcessByDataId()` |
| `getProcInsId(mesWoIdStr)` | 查 wf_my_business → 查 runtime → 查 history |
| `afterFlowHandle(business)` | 根据 ActStatus 更新 woStatus: doing→APPROVING, pass→RELEASED, reject→CREATED |
| `getBusinessDataById(dataId)` | `selectById(Long.parseLong(dataId))` |
| `flowValuesOfTask()` | 返回 null（暂不需要） |
| `flowCandidateUsernamesOfTask()` | 返回 null（暂不需要） |

**状态映射：**
```
ActStatus.doing  → woStatus = "APPROVING" (新增状态)
ActStatus.pass   → woStatus = "RELEASED" (可开始生产)
ActStatus.reject → woStatus = "CREATED" (退回草稿)
ActStatus.revoke → woStatus = "CREATED"
ActStatus.stop   → woStatus = "CREATED"
ActStatus.recall → woStatus = "CREATED"
```

**注意：** 需要在 `IProWorkOrderService` 中新增 `STATUS_APPROVING = "APPROVING"` 常量。

---

### 任务 4：Controller 新增审批接口

**Files:**
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProWorkOrderController.java`

**新增接口：**

```java
/** 提交审批 */
@PostMapping("/{mesWoId}/submit")
public R<Void> submit(@PathVariable Long mesWoId,
                      @RequestParam(required = false) String selectFlowId) {
    proWorkOrderService.submit(String.valueOf(mesWoId), selectFlowId);
    return R.ok();
}

/** 查询流程实例ID */
@GetMapping("/{mesWoId}/proc")
public R<String> getProcInsId(@PathVariable Long mesWoId) {
    return R.ok(proWorkOrderService.getProcInsId(String.valueOf(mesWoId)));
}
```

---

### 任务 5：前端审批组件集成

**Files:**
- Modify: `plus-ui/src/views/mes/pro/workorder/detail.vue`
- Modify: `plus-ui/src/api/mes/pro/workorder.ts`

**详情页增加审批区：**
- 审批流下拉选择器（仅 CREATED 状态显示）
- "提交审批"按钮（仅 CREATED 状态可用）
- "查看流程"按钮（已有流程时显示）
- 状态显示增加"审批中"

**API 补充：**
```typescript
export const submitWorkOrderFlow = (mesWoId: string | number, selectFlowId?: string) =>
  request({ url: `/mes/pro/work/${mesWoId}/submit`, method: 'post', params: { selectFlowId } });

export const getWorkOrderProcId = (mesWoId: string | number) =>
  request({ url: `/mes/pro/work/${mesWoId}/proc`, method: 'get' });
```

---

### 任务 6：工单编辑弹窗

**Files:**
- Create: `plus-ui/src/views/mes/pro/workorder/form.vue`

**功能：**
- 新增工单表单（手动创建场景，ERP 工单通过 MQ 自动创建）
- 编辑工单（修改计划日期、优先级、备注等）
- 表单校验（物料ID、计划数量必填）

---

### 任务 7：菜单权限补充 SQL

**Files:**
- Create: `doc/mes/sql/mes-week19-12-menu.sql`

**内容：**
- 补充主数据只读菜单（物料/BOM/工艺路线）
- 补充 submit/proc 按钮权限
- 前端路由配置指引

---

### 任务 8：编译验证

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过
- [ ] 前端 `npm run dev` 启动无报错

---

## 三、Week 19 交付物汇总

| 交付物 | 类型 | 说明 |
|--------|------|------|
| `mes/md/material/index.vue` | 前端页面 | 物料只读列表 |
| `mes/md/bom/index.vue` | 前端页面 | BOM 只读列表 |
| `mes/md/routing/index.vue` | 前端页面 | 工艺路线只读列表 |
| `workorder/form.vue` | 前端页面 | 工单编辑弹窗 |
| `workorder/detail.vue` | 前端页面(修改) | 增加审批区 |
| `IProWorkOrderService` | Java(修改) | 扩展 WfCallBackServiceI |
| `ProWorkOrderServiceImpl` | Java(修改) | 实现 Flowable 回调 |
| `ProWorkOrderController` | Java(修改) | 新增 submit/proc 接口 |
| `ry-vue-mes-menu-week19.sql` | SQL | 菜单权限补充 |

---

## 四、Week 20 预告

| 任务 | 关键内容 |
|------|----------|
| Flowable 审批流配置 | 创建 MES 工单审批流 BPMN 定义 + WfCustomForm 绑定 |
| 审批流端到端测试 | ERP 工单下达 → MES 工单创建 → 提交审批 → 通过 → RELEASED |
| 流转卡 DDL 执行 | pro_flow_card + pro_flow_card_step 表创建 |
| 工单状态联动完善 | APPROVING 状态在前端展示，审批通过后自动跳转 |

---

## 五、风险与注意事项

| 风险 | 说明 | 应对 |
|------|------|------|
| Flowable 跨数据源 | Flowable 表在 master 库，MES 工单在 mes 库 | submit 中调用 processService 时需切数据源，或依赖 processService 自身的 @DS("master") |
| WfCustomForm 未配置 | MES 工单没有对应的审批流定义 | Week 20 配置，Week 19 仅准备接口 |
| 前端路由冲突 | MES 主数据路由可能与 ERP 路由冲突 | MES 路由前缀统一用 `/mes/` |
| APPROVING 状态新增 | DDL 中无 APPROVING 状态 | 需更新 ry-mes DDL 或确认 wo_status 字段可变长字符串即可 |

---

**文档生成时间**: 2026-06-17
**执行者**: AI Agent
**审核状态**: 待人工确认
