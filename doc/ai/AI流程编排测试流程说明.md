# AI 流程编排测试流程说明

> 日期：2026-08-09
> 状态：**8 个流程全部 E2E 验证通过**（10 个用例 PASS）
> 位置：流程列表页（AI → 流程编排 → 流程列表）可直接查看/设计/运行

## 流程总览

| # | 流程名称 | flowKey | 节点结构 | 覆盖能力 |
|---|---|---|---|---|
| 1 | 库存预警分析 | `test_stock_alert` | sql → if → set×2 → notify | SQL 查询、IF 分支、站内通知、变量渲染 |
| 2 | 销售排行分析 | `test_sales_ranking` | erp_tool → code | ERP 业务工具调用、SpEL 表达式 |
| 3 | 订单批量处理 | `test_batch_loop` | split → code（loop 回边）→ set | 循环分批（split）、loop 返回边 |
| 4 | 金额分级处理 | `test_branch_merge` | code → if → set×2 → merge → code | 条件分支、merge 多输入合并 |
| 5 | 异常容错处理 | `test_error_port` | code(ERROR_PORT) → set / notify | ERROR_PORT 错误分支、异常通知 |
| 6 | 外部数据获取 | `test_http_fetch` | http → code → set | HTTP 请求、JSON 解析、模板渲染 |
| 7 | 数据转换流水线 | `test_pipeline` | set → code → code → set | 多级转换、SpEL 计算、三元表达式 |
| 8 | Webhook 工单分诊 | `test_webhook_reply` | set → if → notify×2 | Webhook 触发、条件通知 |

## 各流程运行入参与预期

### 1. 库存预警分析（`test_stock_alert`）
- **入参**：`{}`（无入参，查库判断）
- **预期**：查零库存物料 → 10 种零库存 → 走 true 分支 → 站内通知"发现 10 种物料零库存，请及时补货"
- **验证**：✅ SUCCEEDED，通知落库

### 2. 销售排行分析（`test_sales_ranking`）
- **入参**：`{}`（工具参数内置：统计天数 30、Top N 5）
- **预期**：调用 ERP 销售工具返回物料销售排行 Markdown 表格
- **验证**：✅ SUCCEEDED（QC-FP-001 销量 20 金额 2400）
- **注意**：erp_tool 节点 args 键用 **@P 描述**（中文），如 `{"统计天数": "30", "Top N": "5"}`

### 3. 订单批量处理（`test_batch_loop`）
- **入参**：`{"orders": ["A", "B", "C", "D", "E"]}`
- **预期**：5 个订单按 2 个一批循环 3 轮 → done 分支输出"共 5 个订单，2 个一批"
- **验证**：✅ SUCCEEDED（batch_index 0/1/2，末批 1 个）

### 4. 金额分级处理（`test_branch_merge`）
- **入参**：`{"amount": 5000}` → 大额分支（"需审批"）；`{"amount": 500}` → 小额分支（"自动通过"）
- **预期**：IF 分支各自处理 → merge 合并 → 最终输出 inputs
- **验证**：✅ 双用例均 SUCCEEDED

### 5. 异常容错处理（`test_error_port`）
- **入参**：`{}`（node_1 引用不存在的节点必然失败）
- **预期**：node_1 失败 → ERROR_PORT 分支 → 站内通知携带错误信息；正常分支 SKIPPED；流程整体 SUCCEEDED
- **验证**：✅ SUCCEEDED，通知落库含完整错误

### 6. 外部数据获取（`test_http_fetch`）
- **入参**：`{"city": "北京"}`
- **预期**：HTTP GET postman-echo → 200 → 解析 bodyJson.args → 输出"城市 北京 请求成功，HTTP 200"
- **验证**：✅ SUCCEEDED

### 7. 数据转换流水线（`test_pipeline`）
- **入参**：`{"customer": "华为", "product": "服务器", "price": 500, "qty": 3, "vip": true}`
- **预期**：原价 1500 → VIP 8 折 1200 → "VIP大单" → 摘要
- **验证**：✅ SUCCEEDED

### 8. Webhook 工单分诊（`test_webhook_reply`）
- **Webhook URL**：`POST http://<host>/ai/flow/webhook/test_webhook_reply`
- **入参**（body JSON）：
  - 紧急：`{"ticketId": "T-1001", "title": "服务器宕机", "priority": "urgent"}` → 紧急通知
  - 普通：`{"ticketId": "T-1002", "title": "功能咨询", "priority": "normal"}` → 新工单通知
- **验证**：✅ 双用例 SUCCEEDED，通知落库

## Webhook 调用示例

```bash
# 触发紧急工单分诊（无需登录）
curl -X POST http://192.168.1.101:8080/ai/flow/webhook/test_webhook_reply \
  -H "Content-Type: application/json" \
  -d '{"ticketId": "T-1001", "title": "服务器宕机", "priority": "urgent"}'
```

> 流程配置了 `webhookToken` 时需带请求头 `x-flow-token: <token>`（流程列表页 Webhook 按钮可见）

## 设计器操作指引

1. **查看**：流程列表 → 「设计」进入画布查看节点连线
2. **运行**：设计器底部运行面板输入入参 JSON → 点「运行」→ 节点实时高亮
3. **调试**：选中节点 → 「测试此节点」→ 填 mock 状态单独执行
4. **看数据**：运行后双击节点 → 查看该节点 input/output 快照（NDV）
5. **历史**：流程列表 → 「历史」→ 实例列表 → 「详情」只读重放 / 「续跑」从失败节点重跑
