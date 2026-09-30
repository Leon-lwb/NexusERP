/** 任务3a：只在 ERP 数据源逐条读取验收证据，严禁整批执行。 */
/** 每个问号均须通过数据库驱动绑定位置参数，禁止拼接 SQL 文本。 */
/** 清单中的雪花主键按精确字符串绑定，由驱动和数据库转换为大整数； */
/** 租户必须与清单完全一致；仅使用已核验的只读连接执行。 */
/** 每条查询单独执行；空结果、未知值或数量不符均不得当作验收成功。 */
/** 客户订单关联核验，参数依次为客户订单主键、租户。 */
SELECT so_id, so_no, co_id, order_status FROM so_sales_order_header WHERE co_id = ? AND tenant_id = ?;
/** 销售订单存在性核验，参数依次为销售订单主键、租户。 */
SELECT COUNT(*) FROM so_sales_order_header WHERE so_id = ? AND tenant_id = ?;
/** 生产计划到工单的来源核验，参数依次为计划主键、租户。 */
SELECT wo_id, wo_no, plan_id, wo_status FROM prd_work_order WHERE plan_id = ? AND tenant_id = ?;
/** 生产工单存在性核验，参数依次为工单主键、租户。 */
SELECT COUNT(*) FROM prd_work_order WHERE wo_id = ? AND tenant_id = ?;
/** 工单自动生成的生产领料单核验，参数依次为工单主键、租户。 */
SELECT issue_id, issue_code, wo_id, status FROM wm_production_issue WHERE wo_id = ? AND tenant_id = ?;
/** 销售发货单来源核验，参数依次为销售订单主键、租户。 */
SELECT issue_id, issue_code, so_id, status FROM wm_sales_issue WHERE so_id = ? AND tenant_id = ?;
/** 库存数量核验，参数依次为物料主键、租户；数量本身不能证明已完成分配。 */
SELECT COALESCE(SUM(stock_quantity), 0) FROM wm_material_stock WHERE material_id = ? AND tenant_id = ?;
/** 库存流水核验，参数依次为物料主键、租户；必须另核出入库方向。 */
SELECT COUNT(*) FROM wm_material_stock_tx WHERE material_id = ? AND tenant_id = ?;
