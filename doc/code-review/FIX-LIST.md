# 严重问题修复清单（全量代码审查汇总）

> 审查时间: 2026-08-01，范围: 全部后端模块 + 前端 plus-ui
> 总计 163 问题（CRITICAL 15, HIGH 55, MEDIUM 93）→ 分模块明细见 issues-*.md
> 修复优先级: 先 CRITICAL（短信验证码除外，用户确认测试功能暂留）

## 修复进度

| # | 问题 | 严重度 | 模块 | 状态 |
|---|------|--------|------|------|
| C1 | generator/online 任意 SQL 执行（UnifiedGenController:355 + TableDesignController:104） | CRITICAL | generator/online | ✅ 已修（白名单+权限注解） |
| C2 | online 动态 SQL 跨租户 IDOR（OnlineMapper.xml:7） | CRITICAL | online | ✅ 已修（全部动态 SQL 强制 tenant_id） |
| C3 | 短信验证码明文返回（AppAuthController:71） | CRITICAL | admin | ⏸ 暂缓（测试用，用户确认） |
| C4 | 微信支付回调未校验订单号/金额（WxPayServiceImpl:110） | CRITICAL | pay | ✅ 已修 |
| C5 | 支付宝回调验签失败返回 success/无幂等（AliPayServiceImpl:110） | CRITICAL | pay | ✅ 已修 |
| C6 | 返工入库幂等键错配（ReworkReceiveStrategy:93） | CRITICAL | wm | ✅ 已修 |
| C7 | 盘点过账双通道无幂等（WmStockTakingPlanServiceImpl:238） | CRITICAL | wm | ✅ 已修 |
| C8 | 批次号 JVM 计数器集群重复（WmBatchServiceImpl:261） | CRITICAL | wm | ✅ 已修 |
| C9 | 采购暂估入账无过滤（CostController:52） | CRITICAL | fin | ✅ 已修 |
| C10 | 工艺路线步骤永不保存（routing/form.vue:306） | CRITICAL | 前端 | ✅ 已修 |
| C11 | NCR ID v-model.number 截断（ncr/defect/index.vue:51） | CRITICAL | 前端 | ✅ 已修 |
| C12 | IM 登出缓存不清除（im-cache.ts:232） | CRITICAL | 前端 | ✅ 已修 |
| C13 | AI 回复 v-html XSS（AiChatDrawer.vue:24） | CRITICAL | 前端 | ✅ 已修 |
| C14 | AI SQL 租户注入绕过（ExecuteSqlQueryTool:110） | CRITICAL | ai | ✅ 已修 |
| C15 | 收钱吧回调不更新订单状态（SQBServiceImpl:309） | HIGH(按CRITICAL处理) | pay | ⏳ 待修 |

## 修复记录

### ✅ C1+C2 已完成（2026-08-01）
- **C1**: `UnifiedGenServiceImpl.executeCreateTableSql` + `TableDesignServiceImpl.createTables` 增加 CREATE TABLE 白名单校验（拒绝 DROP/ALTER/UPDATE/DELETE 等）；`UnifiedGenController.createTable/importToGen` + `TableDesignController.create-table/import-to-gen` 加 `@SaCheckPermission`（tool:gen:design / online:design:create）
- **C2**: `OnlineMapper.xml` 6 个动态语句（dynamicUpdate/deleteDynamicTable/selectDynamicTableById/deleteSubByForeignKey/selectSubTableByFk/dynamicDelete）+ Mapper 接口 6 方法 + `DynamicSqlProvider.buildDeleteSql` + `batchDelete` 全部强制 `AND tenant_id = #{tenantId}`；FormServiceImpl 9 处调用点 + UnifiedGenServiceImpl 2 处 jdbcTemplate 查询传 `TenantHelper.getTenantId()`
- 编译验证: generator+online 通过

### ✅ C4+C5 已完成（2026-08-01）
- **C4 微信**: notify 增加 (1) out_trade_no 与路径订单号一致校验 (2) 回调金额与本地订单金额（分）一致校验 (3) 终态重复回调幂等忽略；任一不符返回 "fail"
- **C5 支付宝**: notify 重构为 订单号缺失/订单不存在→fail；**验签失败→fail**（不再返回 success）；已支付/已退款/已关闭重复回调幂等忽略；仅 TRADE_SUCCESS/TRADE_FINISHED 终态才更新；total_amount 与本地金额比对不符→fail
- 编译验证: pay 通过

### ✅ C6-C9 已完成（2026-08-01）
- **C6 返工入库幂等**: ReworkReceiveStrategy 幂等检查与流水插入统一用 `PRD_REWORK_ORDER_RECEIVE`，与出库 `PRD_REWORK_ORDER` 区分，重复入库被拦截、出库不再被误判
- **C7 盘点过账**: postAdjustment + executeCountAdjust 双入口都加状态条件更新抢占（仅 GENERATED/APPROVING → POSTING），行级跳过已 POSTED/BALANCED
- **C8 批次号**: generateBatchCode 改为 Redis RAtomicLong 原子自增（`erp:batch:seq:{yyyyMMdd}`），首次初始化按当日最大已有批次号兜底，跨实例/重启唯一
- **C9 暂估入账**: CostController 仅查 SHELVED/FINISHED 收货单；CostService.provision 按 PuInvoiceLine.grLineId 排除已开票收货单
- 编译验证: erp 通过

### ✅ C10-C13 已完成（2026-08-01）
- **C10 步骤保存**: routing/form.vue handleSave 把 stepItems 并入提交体（后端 insert/update 已支持 saveSteps 先删后插）；types.ts RoutingForm 补 steps 字段
- **C11 NCR ID**: defect/index.vue 去掉 v-model.number，ncrId 保持字符串透传（queryParams/form/reset 同步改 string）
- **C12 IM 登出**: user.ts logout 改为 try/catch 容错 + 调用 imCache.clearAllCache() + 重置用户全字段；顺带修复 C4(token 过期重登录死循环) 根因——logout 接口失败不再阻塞本地清理
- **C13 XSS**: AiChatDrawer renderMarkdown 返回前调 sanitizeHtml（与 assistant/index.vue 对齐）
- 验证: eslint 通过

### ✅ C14 已完成（2026-08-01）
- **C14 AI SQL 租户注入绕过**: ExecuteSqlQueryTool 第 4-7 步（租户注入、LIMIT、EXPLAIN 校验、执行）统一改用**清洗注释后的 SQL**；`/* tenant_id */`、`-- WHERE` 注释绕过全部失效（判断与执行同一份干净文本）
- 编译验证: ai 通过
