# 批次 4：物料上料防错（C19/C20 + 客户端上料页，M3 收官）

## 现状结论（两路探索）

- **比对源选型**：工单 MBOM 快照 `pro_work_order_bom_line` 是唯一同时具备"工位+工序维度（冗余 process_code/workstation_code）"与"工单生命周期内不可变"的源（替代候选 bd_bom/MesMbom 是活数据会漂移，wm_production_issue 无工位维度且属账务视图）。定位链复用 reportBatch 既有语义：工位工序码 → pro_flow_card_step 唯一 IN_PROGRESS → pro_flow_card.mes_wo_id → 快照行按 process_code 过滤（NULL 工序行并入全部，兜底 MBOM 未绑工序的行）
- **批次码两套体系按前缀分流**：`B+yyyyMMdd+4位流水` → wm_batch（唯一索引）；`RC+yyyyMMdd+6位流水` → wm_barcode（含 material_id/batch_id/batch_code）。均为纯 ASCII 可直接扫码。MES lt_lot 第三套不纳入（边界）
- **替代料**：`bd_bom_alternative`（bom_detail_id → alt_material_id/priority/substitution_type/effective/expiry/status）全库从未被消费——按快照行 bom_detail_id 跨 erp 库现查扩展允许集合（早期工单 bom_detail_id 为 NULL 则无替代料可查，直接跳过）
- 上料/料站/feeder 在全仓库零实现，纯新建

## 一、后端（ruoyi-station，新增契约 C19/C20）

1. **新表 `mes_material_load` 上料台账**（ry-mes，实体/Mapper 放 ruoyi-station 对齐 mes_station_report 模式）：
   `load_id PK / tenant_id / mes_wo_id(idx) / wo_no / station_code(idx) / process_code / workstation_code / material_id / material_code / material_name / batch_id / batch_code / feeder_slot(料站号可空) / is_alternative(Y/N) / operator_code / operator_name / load_time(idx) / status(ACTIVE/REPLACED) / remark / 审计列`
   增量脚本 `2026-08-21-mes-material-load.sql` + 全量 ry-mes.sql 同步 + 本地执行
2. **新增 `StationErpBatchMapper`**（@DS("erp")，对齐 StationSchWorkerMapper 模式）：
   - `selectBatchByCode(batchCode)` → wm_batch 行（batch_id/code/material_id/code/name/spec/expiry_date）
   - `selectBarcodeByCode(barcodeCode)` → wm_barcode 行（batch_id/batch_code/material_id/material_code?）
   - `selectAlternativesByDetailIds(detailIds)`（script foreach IN）→ bd_bom_alternative 行，过滤 status=1 且生效期含当日（NULL 不限）
3. **新增 `StationMaterialService`**（@DS("mes")，注入 StationPassService/ProFlowCardStepMapper/ProFlowCardMapper/ProWorkOrderBomLineMapper/MesMaterialLoadMapper/StationErpBatchMapper）：
   - `load(dto)`（C19）：① 工位码归一化 + `StationPassService.resolveProcessCode` ② 扫码按前缀分流解析（B/RC，其余抛"无法识别的条码"）③ 定位在制工序（唯一 IN_PROGRESS，同 reportBatch 语义；无在制 → pass=false 带提示）④ 快照按 `mes_wo_id AND (process_code=当前 OR process_code IS NULL)` 取允许物料行 + 替代料扩展 ⑤ 匹配 → **写台账 + 换料语义**（同工单+工位+物料旧 ACTIVE 置 REPLACED）返回 `{pass:true, matched:{物料/批次/用量/工序/是否替代料}}`；不匹配 → `{pass:false, allowed:[{materialCode,materialName,quantityPerParent,processCode}]}`（红屏展示允许清单）
   - `current(stationCode)`（C20）：同链定位 → `required` 应上物料清单（快照行）逐项合并台账 ACTIVE 批次（已上=批次号/时间/操作员，未上=缺料提示）+ `loads` 当前台账
4. **新增 `StationMaterialController`**（`/mes/station/material/**`，独立控制器，X-API-KEY + 租户 000000）：`POST /load` + `GET /current`（操作员事务外解析同既例——本服务只读解析无事务写跨库问题，operatorCode 直接传递）
5. **单测 `StationMaterialServiceTest`**：B 前缀解析+主料匹配登记/换料旧记录置 REPLACED、RC 前缀解析、替代料匹配（is_alternative=Y）、不匹配返回 allowed 清单、无在制工序 pass=false、无法识别条码抛错、current 应上/已上合并（Mockito 对齐既有风格）

## 二、客户端（nexus-station）

6. types.ts 加 `MaterialLoadResult`/`MaterialCurrentState` 类型；client.ts 加 `materialLoad(req)`/`materialCurrent(stationCode)`（404 降级 null）
7. IPC `material:load`（操作员随行注入）/`material:current`；preload/api 五层补齐
8. 新增 `views/material.vue`（套 pass.vue 红绿屏模式）：
   - 扫批次码 → 绿屏（物料/批次/工单/单位用量/工序 + 替代料标记）或红屏（"该物料不属于本工位工序" + 允许上料清单）
   - 下方「当前上料状态」卡：应上物料清单（已上=批次+时间，缺料高亮）+ 手动刷新
   - feedback 语音音效（匹配"上料成功"/不匹配"物料不匹配"）
9. 路由 `/material`；home.vue 工具入口第四卡「上料防错」

## 三、文档与验证

10. README 契约表补 C19/C20 + 状态行 C1-C20；总计划批次 4 标注交付 + 里程碑判定（M3 四模块全闭环）
11. 验证：后端 compile + StationMaterialServiceTest + station 模块全量回归；客户端 vitest + typecheck；增量 SQL 本地执行；两仓库分别提交

## 明确边界（不做）

- 用量计量/耗尽提醒（需报工数量×BOM 反算消耗，后续按需）
- MES lt_lot 批次码（LOT 前缀）解析分支
- 卸料（下料）操作与耗尽确认——台账只做 ACTIVE/REPLACED 换料语义
- PC 端上料台账查询页（后续按需，台账表已留全维度冗余可查）