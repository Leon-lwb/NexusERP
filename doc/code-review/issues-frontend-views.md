# 前端 views 页面审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: plus-ui/src/views（295 个 .vue 文件）
> 总计: 52 个问题（CRITICAL 2, HIGH 21, MEDIUM 29）
> 根因分布: 雪花 ID 被 Number()/el-input-number 截断 14 处；工具栏把 MouseEvent 当 row 用/对话框缺失 5 处

## CRITICAL

### 1. erp/bd/routing/form.vue:306-328 — 工序步骤的增删改永远不会保存
- **问题**: handleSave() 只提交 form.value 给 addRouting/updateRouting，stepItems 从不随提交发送。前端 API 无 steps 参数也无单独保存步骤接口；后端 BdRoutingBo 虽有 List<BdRoutingStepBo> steps 字段，但 BdRoutingController 仅有 GET /steps/{routingId}，**无任何写步骤的端点**。
- **影响**: 新增路线时步骤永不落库；编辑步骤修改保存后重开仍是旧数据。
- **建议**: 保存时把 stepItems 并入提交体（后端 add/edit 落库 steps），或新增 PUT /erp/bd/routing/steps 接口。

### 2. mes/ncr/defect/index.vue:51 — 补录缺陷的 NCR ID 经 v-model.number 截断
- **问题**: `<el-input v-model.number="form.ncrId">`，NCR ID 是 18-19 位雪花 Long，number 强制转换末位舍入；后端 insertByBo 不校验 ncrId 存在。
- **影响**: 补录的缺陷明细绑定到错误 ncrId，真实 NCR 详情中查不到——缺陷数据静默丢失。
- **建议**: 去掉 .number 修饰符，字符串透传。

## HIGH（21）

### 3. erp/bd/routing/form.vue:230 — 雪花 ID 经 Number() 截断
- **问题**: `getRoutingSteps(Number(id))`，id 来自 route.query.id 雪花字符串。
- **影响**: 编辑/查看工艺路线时步骤列表按截断 ID 请求，查不到任何步骤。
- **建议**: 去掉 Number() 直接传字符串。

### 4. erp/prd/requirement/form.vue:391 — BOM 导入把雪花 ID 用 Number() 转换
- **问题**: `importBomDetails(Number(bomId), ...)`，后端签名 `importBomDetails(Long bomId, BigDecimal)`。
- **影响**: 导入错误 BOM 的明细（静默数据错误）或报"BOM 不存在"。
- **建议**: 去掉 Number()。

### 5. erp/so/invoice/index.vue:364,580-601 — 发票明细"物料选择"对话框缺失
- **问题**: openMaterialDialog 置 materialDialogVisible=true 但模板无对应 el-dialog；selectMaterial 无绑定。
- **影响**: 明细行点物料搜索图标无弹窗，发票明细无法选择物料。
- **建议**: 补回物料选择弹窗或复用共享组件。

### 6. erp/so/contract/index.vue:317,535-556 — 合同明细"物料选择"对话框缺失
- **问题**: 与 so/invoice 完全相同。
- **影响**: 合同行物料搜索图标无响应，物料字段只能提交空值。
- **建议**: 同上。

### 7. erp/wm/sales-issue/form.vue:670-721 — 删除明细行会把全部行删掉
- **问题**: rowLineIndex 从未给 row.index 赋值；handleDeleteLine 用 checkedLines.includes(item.index)（恒为 undefined）过滤——includes(undefined) 恒 true，选中任意一行即删除全部明细。
- **影响**: 编辑已保存的出库单误删全部明细（含历史行），保存后数据丢失。
- **建议**: rowLineIndex 给 row.index 赋值，或改用 selection 引用。

### 8. erp/wm/receive/index.vue:575-592 — 工具栏"删除"按钮永远无效
- **问题**: `@click="handleDelete"` 无括号传 MouseEvent；handleDelete 第一步 `row.status !== 'DRAFT'` 恒 true，永远弹"当前状态不允许删除"。
- **建议**: 开头加 `if (row instanceof Event) row = undefined`。

### 9. erp/pu/return/index.vue:211-220 — 工具栏"删除"传 undefined，批量删除失败且无 catch
- **问题**: 工具栏点击时 row 是 MouseEvent（truthy），row.returnId undefined → 请求 /erp/pu/return/undefined；Promise 无 .catch 静默失败。
- **建议**: `row?.returnId || ids.value`，补 .catch。

### 10. erp/wm/storagelocation/index.vue:505,664,686,724,745 — 库区/仓库雪花 ID 被 Number() 截断
- **问题**: 多处对雪花 ID 做 Number()（route.query.areaId、keyStr 拆分出的 areaId/warehouseId）。
- **影响**: 新增/编辑库位保存到错误库区、按库区查询查不到、批量生成挂错库区。
- **建议**: 全程字符串传递。

### 11. erp/wm/components/WmLocationStockDialog.vue:72 — locationId 雪花 ID 被 Number() 截断
- **问题**: `listStock({ locationId: Number(props.locationId) })`。
- **影响**: 3D 仓库点库位查看库存明细永远查不到。
- **建议**: 去掉 Number()。

### 12. erp/wm/barcode/index.vue:141-159,189-193 — 多个雪花 ID 绑 el-input-number
- **问题**: configId/bizId/bizLineId/materialId/batchId/stockId 全部 el-input-number。
- **影响**: 条码与单据、物料、批次的关联写坏。
- **建议**: 改 el-input（字符串）。

### 13. erp/wm/sn/index.vue:100,123,126 — 批量生成/绑定库存的雪花 ID 绑 el-input-number
- **问题**: materialId/locationId/stockId 均 el-input-number。
- **影响**: 批量生成 SN 挂错物料、绑定库存挂错库位。
- **建议**: 去 el-input-number。

### 14. erp/pu/invoice/index.vue:211,386,441 — 采购订单 ID/行 ID 绑 el-input-number
- **问题**: form.poId/scope.row.poLineId/genFromPoPoId 均 el-input-number。
- **影响**: 发票与采购订单关联写坏，三单匹配与回写失效。
- **建议**: 字符串输入/选择器回填。

### 15. erp/wm/barcode/config/index.vue:112 — categoryId 绑 el-input-number
- **问题**: 物料类别 ID 雪花 long。
- **影响**: 条码配置的类别关联写坏。
- **建议**: el-tree-select 或字符串回填。

### 16. erp/wm/trace/index.vue:178-189 — 把物料编码当 materialId 传给追溯接口
- **问题**: `materialId: queryParams.materialCode`（注释自认简化），后端 traceForward(@RequestParam Long materialId)。
- **影响**: 传编码字符串，类型转换 400 或查错，追溯不可用。
- **建议**: 先按编码查物料得到 materialId 再调用。

### 17. mes/ncr/8d-report/index.vue:8 — 查询 8D 报告用 el-input-number 绑定雪花 ncrId
- **问题**: queryParams.ncrId 输入 19 位雪花 ID 被截断。
- **影响**: 该页唯一功能恒查不到，页面不可用。
- **建议**: 普通 el-input。

### 18. mes/ncr/rework/index.vue:13 — 按 NCR 过滤返工单的 el-input-number 截断雪花 ID
- **问题**: queryParams.ncrId 用 el-input-number，列表展示列又是完整 ID。
- **影响**: "NCR 编号"筛选形同虚设。
- **建议**: 改 el-input。

### 19. mes/pro/dispatch/components/ScanExecute.vue:76-80 — 扫码开工把雪花操作员 ID 经 Number() 截断
- **问题**: `startStep(..., Number(operatorId))`，workerId 雪花 long。
- **影响**: 工序开工记录写入错误 operatorId，SN 追溯/按操作员查询错乱。
- **建议**: 透传字符串。

### 20. mes/sop/viewer/index.vue:77-81 — SOP 查看器对 route.query 雪花 ID 全部 Number() 截断
- **问题**: processId/stepRecordId/woId/workstationId 全部 Number()。
- **影响**: getSopByProcess/getSopByStep 查不到 SOP（恒显示"未绑定已发布SOP"），recordViewLog 登记错误 ID。
- **建议**: 删除所有 Number()。

### 21. erp/prd/subcontract/index.vue:504,527 — 委外"发料/收货"调用不存在的后端端点，且数量/备注被丢弃
- **问题**: 请求 PUT /erp/prd/subcontract/issue/{id} 与 /receive/{id}，**整个后端不存在这两个端点**；quantity/remark 从未传入。
- **影响**: 每次点发料/收货必然失败；即使补上端点数量也会被丢弃。
- **建议**: 后端补齐端点并接收 quantity/remark，前端一并传参。

### 22. erp/prd/capacity-calendar/index.vue:143,160,200,231 — 对象/班次/计划雪花 ID 绑 el-input-number
- **问题**: objectId/shiftId/planId 全部 el-input-number。
- **影响**: 编辑日历记录挂错车间/工位；"生成"功能生成到错误对象。
- **建议**: el-input 或 el-select 回填字符串。

### 23. ai/model/provider.vue:45 — AI 厂商 API Key 在列表中明文展示
- **问题**: `<el-table-column label="API Key" prop="apiKey">` 明文渲染。
- **影响**: 有列表权限即可看到全部厂商 API Key，凭据泄露可被滥用扣费。
- **建议**: 列表脱敏（sk-***），仅编辑弹窗 show-password。

## MEDIUM（29）

### 24. erp/bd/measure/index.vue:249 — 基本单位选择弹窗双击绑定了错误方法
- **问题**: @row-dblclick="selectBaseUnit" 但选择按钮是 pickBaseUnit(scope.row)；selectBaseUnit 不关弹窗不写表单。
- **建议**: @row-dblclick 改为 pickBaseUnit。

### 25. erp/bd/client/index.vue:426,480,652 — autoGenFlag 未在 reset() 复位
- **问题**: 一旦新增打开过"自动生成"，后续所有编辑弹窗 submitForm 执行 form.clientCode = undefined。
- **影响**: 编辑已有客户保存时编码被置空提交。
- **建议**: reset() 加 autoGenFlag.value = false。

### 26. erp/bd/vendor/index.vue:382,426,587 — autoGenFlag 未复位（同 client）
- **问题**: 与 client 完全相同。
- **建议**: reset() 复位。

### 27. erp/so/report/index.vue:63,75 — 每次查询重复注册 window resize 监听
- **问题**: 匿名回调无法移除，无 onBeforeUnmount，echarts 不 dispose。
- **建议**: onMounted 注册一次，onBeforeUnmount 移除 + dispose。

### 28. erp/pu/report/index.vue:63,75 — resize 监听泄漏（同模式）
- **问题**: 同 so/report。
- **建议**: 同上。

### 29. erp/wm/report/index.vue:139,151,173 — 三处 render 各注册匿名 resize 监听
- **问题**: 每点一次查询累积 3 个监听器。
- **建议**: 统一注册与清理。

### 30. erp/prd/report/index.vue:64,84 — resize 监听泄漏（同模式）
- **问题**: 同上。
- **建议**: 同上。

### 31. erp/wm/arrivalnotice/index.vue:159-160 — 对话框"采购订单编号"绑定字段不一致
- **问题**: 对话框用 prop="poCode"/form.poCode，列表列/独立表单/reset() 用 purOrderCode。
- **影响**: 对话框保存的采购订单编号不落库、编辑回显为空。
- **建议**: 统一字段名 purOrderCode。

### 32. erp/pu/order/index.vue:294-299 — "收货联动"无确认直接创建到货通知单
- **问题**: handleReceive 直接调接口，无确认无 catch。
- **建议**: 加确认弹窗。

### 33. erp/pu/order/form.vue:769-782 — "选择请购单"按钮永远无法使用
- **问题**: handleSelectRequisition 要求 form.requisitionId 已存在，但页面无任何途径设置。
- **建议**: 实现请购单选择弹窗。

### 34. erp/pu/return/form.vue:284-303 — "选择采购订单"按钮永远无法使用
- **问题**: handleSelectOrder 要求 form.orderId 已存在，无途径设置。
- **建议**: 实现订单选择弹窗。

### 35. erp/pu/contract/index.vue:445-448 — 供应商选择是空壳
- **问题**: openVendorDialog 只有 ElMessage.info('待集成')。
- **建议**: 接入供应商选择组件。

### 36. mes/spc/control-chart/index.vue:377-388 — 分析图表渲染与请求完成存在竞态
- **问题**: renderChart 只挂 @opened，请求在弹窗打开后才返回时图表空白。
- **建议**: watch(analyzeResult, renderChart) 或请求后 nextTick 渲染。

### 37. mes/pro/flow-card/detail.vue:302-371 — 进站弹窗采集的工装编码/批次号被静默丢弃
- **问题**: entryForm.toolingCode/lotNo 让操作员填但构造的 ProEntryBo 只有 cardId/stepRecordId/operatorId。
- **建议**: 将 toolingCode/lotNo 随 BO 提交，或移除输入框。

### 38. erp/prd/prdplan/form.vue:657-659 — 生产计划编号被填入销售订单的雪花 ID
- **问题**: `if (!form.value.planNo && line.soId) { form.value.planNo = String(line.soId) }`。
- **影响**: 计划编号是无意义 19 位数字；同订单多计划可能触发唯一约束。
- **建议**: 删除该赋值。

### 39. erp/fin/cost/index.vue:101-108 — 过账操作无确认直接执行
- **问题**: doPost 直接调 postCostTx，同页 doProvision 有确认框。
- **建议**: 加确认。

### 40. erp/fin/closing/monthly.vue:103-109 — 损益结转/外币重估无确认直接生成凭证
- **问题**: doCarryPnl/doRevaluate 无确认。
- **建议**: 补确认框。

### 41. erp/sch/plan/index.vue:437-453 — 发布排班计划无确认
- **问题**: handlePublish 直接置 status='published'，发布后编辑入口禁用（不可逆）。
- **建议**: 加确认框。

### 42. erp/sch/holiday/index.vue:239 — isWorkingDay 严格字符串比较与后端 Integer 不匹配
- **问题**: `holiday.isWorkingDay === '1'`，后端为 Integer 1/0。
- **影响**: 调休上班日在日历上错误显示"休"。
- **建议**: Number() === 1。

### 43. ai/abTest/index.vue:50-53 — el-input-number 绑定模型雪花 ID
- **问题**: form.modelAId/modelBId 用 el-input-number。
- **建议**: el-input 或 el-select 字符串 value。

### 44. report/dashboard/index.vue:12 + report/design/index.vue:12 — JWT token 明文拼进 iframe URL
- **问题**: url = baseUrl?token=token；JWT 进浏览器历史/代理/报表服务器日志。
- **建议**: 改短期一次性 token。

### 45. iot/device/realtime.vue:233 — SSE URL 携带明文 Bearer token
- **问题**: url 拼 ?Authorization=Bearer token（EventSource 无法设 header 的变通）。
- **建议**: 后端签发短时效专用 SSE token。

### 46. system/menu/index.vue:518 — 级联删除空选校验条件恒为 false
- **问题**: `if (menuIds.length < 0)`，length 永远 ≥ 0。
- **影响**: 未勾选直接确定提示"删除成功"。
- **建议**: `if (!menuIds.length)`。

### 47. pay/paytest/index.vue:90,175-198 — 支付轮询 setInterval 未在 onUnmounted 清理
- **问题**: 离开页面定时器继续每 5 秒请求约 2.5 分钟。
- **建议**: onUnmounted stopPolling。

### 48. ai/tokenUsage/index.vue:385-389 — resize 监听未清理、ECharts 未 dispose
- **问题**: onMounted 注册无 onUnmounted 移除。
- **建议**: onUnmounted 移除 + dispose。

### 49. monitor/cache/index.vue:183-186 — resize 监听写在 getList 内且从不移除
- **问题**: 匿名箭头函数无法移除，ECharts 未 dispose。
- **建议**: onMounted 注册保存句柄，onUnmounted 清理。

### 50. system/client/index.vue:53 — OAuth 客户端秘钥明文展示在列表
- **问题**: `<el-table-column prop="clientSecret">` 明文渲染。
- **建议**: 列表脱敏。

### 51. im/chat/index.vue:600-604 — 移除群成员无确认直接执行
- **问题**: onRemoveMember 直接 await removeMember，无确认。
- **建议**: 移除前加确认。

### 52. login.vue:481-491 — "记住密码"把明文密码写入 localStorage
- **问题**: localStorage.setItem('password', 明文)。
- **建议**: 只记住用户名，或加盐哈希。
