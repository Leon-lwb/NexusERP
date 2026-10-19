# 历史在途WO原策略采纳：审批导入与实际生效

该路径补齐UNKNOWN的可实施入口，不采用当前bd_material策略，也不回填过去的工单事实或签署DHR。离线工具只生成新JSON与单ERP库待审SQL；实际业务入口已实现：`GET /erp/prd/work/tracking-policy/preview/{woId}`取得审批来源预览，`POST /erp/prd/work/tracking-policy/adopt`使已导入审批生效。两者均要求专用权限`prd:work:trackingPolicyAdopt`；执行采纳的真实登录用户必须是原独立批准人`approvedBy`。原五表repair白名单不变。

## 工件与门禁

输入必须包含35表完整快照、全部conversion_run_states、真实prd_work_order完整行及其规范SHA256、由ERP来源预览取得的wo_source_json/expected_wo_hash。原始source_json是审批前已归档的完整策略集合，每件material明确origin/reference/documentHash/capturedAt及五项TrackingPolicy。来源只接受ORIGINAL_RELEASE_ARCHIVE、IMMUTABLE_SOURCE_SNAPSHOT、SIGNED_DHR_POLICY_SNAPSHOT；CURRENT_MASTER、缺字段、未来归档及不完整物料范围均拒绝。真实WO/BOM/需求范围由实际服务在应用时再次加锁核对，不能用客户端当前策略代替。

采纳申请明确adoption_id、command_id、tenant/erp_wo、不同的proposed_by与approved_by、approved_at/expires_at及经核实的数据库/JDBC timestamp_zone。request_hash严格复用TraceHashes的UTF16长度分帧算法`WO_POLICY_ADOPTION_V1,tenant,id,command,wo,expectedWoHash,sourceHash,proposedBy,approvedBy,approvedMillis,expiresMillis`。

approval_json必须是独立批准人对原requestHash的APPROVED决定，带approvedBy、原approvedAt及非空reference，并提供精确approval_hash。工具不会替用户生成批准结论。另须有绑定整个计划的SIGNED_REVIEW和PHYSICAL_COUNT工件及approved_by/artifact/hash；实盘与原策略档案真实性仍由审核人负责，不因格式和摘要匹配而声称已验真。

## 实际CLI

输出目录须预先存在、使用新文件名，所有命令排他创建，不覆盖原凭据。合成示例在`script/tools/material_trace/examples/policy-adoption/`，其中T1被替换为真实格式的假租户000001，所有归档/审批reference仍显式synthetic。

```powershell
python -B script/tools/material_trace/cli.py policy-adoption-check --snapshot before.json --plan plan.json --output check-new.json
python -B script/tools/material_trace/cli.py policy-adoption-plan --snapshot before.json --plan plan.json --output journal-new.json --snapshot-output candidate-new.json
python -B script/tools/material_trace/cli.py policy-adoption-emit-sql --snapshot before.json --journal journal-new.json --target erp:fixture_erp --backup-reference SYNTHETIC-BACKUP-ONLY --allow-db-writes --output review-new.sql
```

check拒绝时输出UNKNOWN_REVIEW_REQUIRED差异任务，退出3；不会从当前master补造。plan生成的候选仅新增`wm_work_order_policy_adoption`一条APPROVED/version=1记录，applied_at/snapshot_hash为NULL；policy bank仍为空，应用前reader保持UNKNOWN。journal保留原策略、审批、census、source row及完整前后镜像。

待审SQL绑定目标库/server_uuid/备份，检查UTC审批窗口，使用SERIALIZABLE、原WO全行CAS、全部ERP业务来源及转换审计状态基线。只INSERT该审批表，无UPDATE业务、无DELETE、无SN/凭证发号，不直接写wm_work_order_tracking_policy。同一已导入申请精确重放不重复；已应用、意图冲突、原值或新业务变化拒绝旧导入，不能覆盖审计。

## 实际服务执行与限制

审批准备阶段调用`GET /erp/prd/work/tracking-policy/preview/{woId}`，成功响应为`R<AdoptionSource>`，包含`sourceJson`、`sourceHash`、`materialIds`。将原响应中的`sourceJson`和`sourceHash`分别作为离线计划的`wo_source_json`和`expected_wo_hash`，保留完整物料范围；预览不会创建审批或使策略生效。

独立审批导入后，由原批准人调用`POST /erp/prd/work/tracking-policy/adopt`，JSON请求体为原`adoptionId`、`commandId`和`expectedVersion`（初次应用为1），成功响应为`R<Result>`，其中`Result.snapshotHash`为整组冻结策略摘要。两个接口均校验`prd:work:trackingPolicyAdopt`，应用时还在WO父行锁内核对真实登录用户等于原`approvedBy`，不能仅在请求中填写批准人代替身份认证。

Controller经`PrdWorkOrderPolicyAdoptionEntry`代理进入`@DS("erp")`和`REQUIRED`事务，再调用`PrdWorkOrderPolicyAdoptionService.adoptApproved`的`MANDATORY` writer。服务在真实WO父行锁下重新验证原策略、独立批准、时间窗和当前WO/BOM/需求source hash，再一次性冻结整组SOURCE_POLICY_ADOPTION并CAS审批到APPLIED/version=2。frozen_at/effective_from是实际执行时点，原归档中的过去时间只保留为证据，不冒充过去已经生效；使用原adoptionId、commandId和expectedVersion的请求可由实际服务幂等重放。

本切片的隔离H2互操作证明离线APPROVED行可被当前实际服务消费、reader验证完整组hash并重放；物理ERP守卫和权限authorizer在该证明中为mock，真实鉴权/数据库路由与主业务整合仍由主线程验收。生成SQL另在自有MySQL5.7执行，但未连接现有业务库。实际WO过去的未结消耗、在制归属、缺失归档和逐件流水仍必须凭真实单据处理；采纳不会自动消除它们，也不会自动启用STRICT。
