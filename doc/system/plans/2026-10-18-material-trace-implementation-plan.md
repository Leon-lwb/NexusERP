# 物料身份、条码与追溯闭环实施记录

> 2026-10-19实施状态：对应源码与自动化验证已交付，实际结果见[最终验证记录](2026-10-19-material-trace-verification.md)。本计划不作为生产迁移或现场验收通过证明。


- 日期：2026-10-18；用户已批准实施完整优化计划。
- 基线：ERP plus@1ab08b09；PDA master@7a27994；Station codex/station-pass-guard-20261002@0d8ebcb；App main@d9173cc。
- 工作区：E:/Nexus/.worktrees/material-trace-20261018 下四个同名仓库，分支均为 codex/material-trace-20261018。
- 用户决定：同件同号；内部正式SN+外部别名；混批按物料配置且默认允许；完整历史修复与回退；包含PC/PDA/Station/App配套。
- 保留工单最终完工一次入库及原计价策略。号码不回收，原凭据及签署DHR不覆盖，证据不足不得补造历史。
- 不自动提交/推送、启动服务或执行现有业务库写入。实际迁移需明确环境、备份和窗口。

## 阶段检查

- [ ] P0：基线、失败回归、号段/流水SN/追溯/打印/OCR修复。
- [ ] P1：公共契约、身份/别名/批次映射、策略、数据库扩展。
- [ ] P2：ERP全部真实库存写入口、SN明细、版本化过账证明、计价组。
- [ ] P3：MES组件消耗、混批并发、最后一件完工、不可变清单与本地outbox/inbox。
- [ ] P4：统一解析、标签身份/快照、分层追溯与DHR修订。
- [ ] P5：PC/PDA/Station/App真实入口及离线契约适配。
- [ ] 历史修复：只读对账、证据分类、幂等修复、恢复/补偿工具及演练。
- [ ] 验证：非零单测、前端类型/构建、MySQL 5.7约束/并发、隔离跨库故障与现场验收。

## 固定接口契约

解析：POST /erp/wm/code/resolve（PC/App）、/app/pda/code/resolve、/mes/station/code/resolve（设备鉴权）。
请求：rawCode、scene、可选 materialId/supplierId/customerId/sourceDocType/sourceDocId/lineId。
R.data：status=RESOLVED/AMBIGUOUS/NOT_FOUND/INVALID；objectType=SN/BATCH/MATERIAL/DOCUMENT；identityId/localSnId/canonicalSnCode/snCode/materialId/materialCode/batchId/batchCode/policy/candidates/reason。
policy：batchManaged、serialManaged、allowMixedLot、version、stage=AUDIT/STRICT。
ID/数量/金额保持字符串。租户来自认证上下文，候选按权限过滤；歧义不自动选择。
SN写入：commandId、detailVersion、snItems[{identityId,sourceStockId?,targetLocationId?,originalTxId?,batchId?}]；已有URL保留。
稳定身份为ERP wm_sn.sn_id；MES新增identityId映射，现有本地snId含义不变。

## 验收边界

源码/单测通过不代表生产已部署。正式库迁移、四端现场扫码/打印和对应平台发布验收分别记录。
