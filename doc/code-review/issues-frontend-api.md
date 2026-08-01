# 前端 api 层审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: plus-ui/src/api（308 文件，1,124 个接口调用 vs 1,587 个后端 Controller 映射全量比对）
> 总计: 8 个问题（CRITICAL 0, HIGH 2, MEDIUM 6）

## HIGH

### 1. erp/sch/employee-schedule/index.ts:7,15,22,30,38 — 全部 5 个接口路径大小写与后端不一致，整个员工排班功能不可用
- **问题**: 前端全部用 kebab-case `/erp/sch/employee-schedule/...`，后端 `SchEmployeeScheduleController` 类级映射是 camelCase `@RequestMapping("/erp/sch/employeeSchedule")`。Spring 路径匹配区分大小写。
- **影响**: views/erp/sch/employee-schedule/index.vue 活跃调用 list/get/del，全部 404，排班功能完全不可用。
- **建议**: 统一改为 `/erp/sch/employeeSchedule`（或后端加 kebab 别名）。

### 2. erp/prd/subcontract/index.ts:44,47,50,53 — 4 个接口后端无对应端点，委外订单领料/收料/审批/日志全部 404
- **问题**: issueSubcontractMaterial→PUT /issue/{id}、receiveSubcontractMaterial→PUT /receive/{id}、submitApproval→POST /submit/{id}、getOperationLogs→GET /logs/{id}；后端 PrdSubcontractController 仅有 list/export/{id}/CRUD/release/start/complete/close。
- **影响**: views/erp/prd/subcontract/index.vue 第 504/527/551/568 行真实调用，点击即失败。
- **建议**: 后端补齐 /issue/{id}、/receive/{id}、/submit/{id}、/logs/{id}，或前端改调既有 release/start/complete 链路。

## MEDIUM

### 3. erp/bd/bom/index.ts:68 — updateBomDetail 调用 PUT /erp/bd/bom/detail/{detailId}，后端只有裸 @PutMapping
- **问题**: 前端把 detailId 拼进 URL；BdBomDetailController 只有 @PutMapping（映射 /erp/bd/bom/detail，@RequestBody 传 id），无 @PutMapping("/{id}")。
- **影响**: 一旦调用 404/405；当前 form.vue 只 import 未实际调用，随时会踩的坑。
- **建议**: 改为 url: '/erp/bd/bom/detail'，或后端补 @PutMapping("/{id}")。

### 4. erp/bd/bom/index.ts:91,98 — getBomStructure/getCurrentBom 调用的端点在后端完全不存在
- **问题**: GET /erp/bd/bom/structure/{parentId}、GET /erp/bd/bom/current/{parentId}，全仓库无对应映射（BdRoutingController 只有 /erp/bd/routing/current/{materialId}）。
- **影响**: 调用即 404（当前视图未使用，死代码）。
- **建议**: 后端补端点（复用 explode/whereUsed 逻辑）或删除。

### 5. monitor/cache/index.ts:18-38 — 6 个缓存管理接口后端无对应端点
- **问题**: getCacheNames/getCacheKeys/getCacheValue/clearCacheName/clearCacheKey/clearCacheAll 调 /monitor/cache/getNames 等；后端 CacheController 仅剩 @GetMapping() 一个端点（上游缓存操作端点被移除）。
- **影响**: 调用即 404（当前视图只使用 getCache，其余死代码）。
- **建议**: 删除封装或后端补回。

### 6. erp/wm/batch/index.ts:26 — 导出接口用 data(JSON body) 传过滤条件，后端按 query 绑定，导出忽略筛选
- **问题**: exportBatch(data) POST 传 data；后端 export(WmBatchBo bo) 无 @RequestBody 靠 query 绑定。
- **影响**: 导出永远空条件全量数据，大库导出卡死/文件超大。
- **建议**: 改为 params: query。

### 7. erp/wm/sn/index.ts:26 — 同上，SN 导出用 data 传过滤条件
- **问题**: exportSn(data) 传 data，后端 query 绑定。
- **建议**: 改为 params: query。

### 8. erp/wm/valuation/index.ts:18 — 同上，库存估值导出用 data 传过滤条件
- **问题**: exportValuation(query) 传 data: query，后端 query 绑定。
- **建议**: 改为 params: query。

## 其他维度结论（无问题）
- 精度丢失: 全 api 目录仅 1 处 Number(（警告注释）；ID 均 string | number 透传
- 安全: 无硬编码 token/密钥；isEncrypt 仅 login/register/tenant add/user resetPwd/updatePwd 启用
- 其余 1,100+ 端点（ai/fin/mes/iot/workflow 全模块）URL+HTTP 方法与后端一一匹配（42 个初筛项 34 个为多段拼接误报已排除）
