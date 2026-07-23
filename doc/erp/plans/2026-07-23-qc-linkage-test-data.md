# 质检联动(MES↔ERP) 测试数据与操作手册

> 配套实现方案: `doc/erp/plans/2026-07-23-mes-erp-qc-linkage-completion-plan.md`
> 测试数据脚本: `script/sql/update/2026-07-23-qc-linkage-test-data.sql`(已执行于本地库)

## 一、测试数据清单(已写入本地 `ry-erp` 库,租户 000000)

| 类型 | ID | 编码 | 名称 | 关键属性 |
|------|----|----|------|---------|
| 成品(自制) | 99001 | QC-FP-001 | 质检联动测试成品 | source_type=PROCESS, material_type=3(成品), **inspection_required=1(需OQC)** |
| 原料A(采购) | 99002 | QC-RM-A | 质检测试原料A | source_type=PROCUREMENT, **inspection_required=1(需IQC)** |
| 原料B(采购) | 99003 | QC-RM-B | 质检测试原料B | source_type=PROCUREMENT, inspection_required=0(免检) |
| BOM | 99001 | - | 质检联动测试成品BOM | 父件99001 → 原料A×2 + 原料B×1, is_current=1 |
| 工艺路线 | 99001 | RT-QC-001 | 质检联动测试成品工艺路线 | 3工序, is_current=1 |
| 工序步骤 | 99001/99002/99003 | - | 切割→焊接→包装 | 焊接(步骤20) **quality_required=1, is_key_process=1**(IPQC触发点) |
| 库存 | 99001/99002 | - | 原料A/B 各1000件 | 原料仓库WH004, frozen_flag=0(可用) |

**复用的既有主数据**: 计量单位 件=26; 分类 整机=3/原材料=1; 工序 CUT=1/WELD=2/PKG=5; 工位 2/6/9; 库房 成品仓=1/原料仓=2; 客户 测试=1(信用50万,VIP); 供应商 V00001。

## 二、前置条件

1. 后端运行(`mvn spring-boot:run -P local` 或 IDE 启动 `DromaraApplication`),端口 8080。
2. **RabbitMQ 服务运行中**(三条质检链路均经 MQ 回传,MQ 不通则回写不发生)。
3. 前端运行(`npm run dev`),用 admin 登录(默认租户 000000)。
4. 增量迁移 `2026-07-23-qc-linkage-schema.sql` 已应用(本地库已确认 `inspection_required`/`oqc_status`/`quality_status` 等列存在)。

## 三、全链路操作步骤(销售订单 → 生产 → 质检 → 履行)

### 链路 A: IPQC(过程检验)→ ERP 工单聚合质检状态

1. **ERP 销售订单**: 销售管理 → 客户订单 → 新增
   - 客户: 测试(1); 行物料: **质检联动测试成品(99001)**; 数量: 10; 仓库: 成品仓库(1)
   - 保存 → **确认**。确认后系统自动创建生产计划(因成品 source_type=PROCESS)。
2. **ERP 生产工单**: 生产计划 → 将草稿计划转为工单
   - 物料 99001; 工作车间: 任选(如装配车间#1=3); BOM: 99001(自动带出); 工艺路线: 99001; 仓库: 成品仓库(1)
   - 保存 → **下发**。下发经 MQ 传 MES,MES 创建 `pro_work_order`(带 `erp_wo_id` 回链)。
3. **MES 过程检验(IPQC)**: MES → 质量管理 → IPQC → 新增
   - 工单: 选步骤2下发的 MES 工单(自动带 `mes_wo_id`); 工序: **焊接(WELD)**; 物料 99001; 抽样数: 10
   - 保存 → **检验**: 结果选 `QUALIFIED`(或 `UNQUALIFIED` 验证粘性)。
4. **验证 ERP 回写**(IPQC 检验后,经 MQ 回传 `prd_work_order`):
   ```sql
   SELECT wo_no, quality_status, quality_check_no, inspector_name, inspect_time
     FROM `ry-erp`.prd_work_order WHERE wo_no='<步骤2工单号>';
   -- 期望: quality_status=QUALIFIED, quality_check_no=IPQC单号, inspector_name=当前登录人
   ```
   - **粘性验证**: 若该工单已有一次 UNQUALIFIED,后续工序 QUALIFIED 不会覆盖(quality_status 仍为 UNQUALIFIED)。

### 链路 B: OQC(出货检验)→ ERP 销售订单行 OQC 状态 + 履行硬门禁

5. **MES 工单完工**: MES 工单 → 报工/完工 → 完工数量 10。完工经 MQ 回传 ERP 入库(成品 99001 入成品仓库)。
6. **MES 出货检验(OQC)**: MES → 质量管理 → OQC → 新增
   - 销售订单: **选步骤1创建的销售订单(带 so_id)**; 物料: 99001; 数量: 10; 抽样数: 3
   - 保存 → **检验**: 结果选 `QUALIFIED`。
7. **验证 ERP 回写**(OQC 检验后,经 MQ 回传 `so_sales_order_line`):
   ```sql
   SELECT line_no, material_name, oqc_status, oqc_code, oqc_time
     FROM `ry-erp`.so_sales_order_line WHERE so_id=<步骤1销售订单头ID>;
   -- 期望: oqc_status=QUALIFIED, oqc_code=OQC单号, oqc_time 非空
   ```
8. **ERP 销售出库**: 仓储管理 → 销售出库 → 对该销售订单出库 10 件(使 `delivered_qty` 兜齐 `ordered_qty`)。
9. **ERP 销售订单履行**: 销售订单 → 履行。
   - OQC 全合格 → 履行成功,头状态 → FULFILLED。
   - **硬门禁验证**: 若步骤6 OQC 结果为 `UNQUALIFIED`,履行抛错:
     `存在OQC检验不合格的销售订单行,禁止履行:行1(质检联动测试成品):OQC-xxx`(force=true 亦不可绕过)。

### 链路 C: IQC(来料检验)→ 收货质检(可选,走采购链路)

10. **ERP 采购**: 采购管理 → 采购订单 → 新增(物料 **QC-RM-A 99002**, 供应商 V00001)→ 确认 → 生成到货通知。
    - 到货通知行 `iqc_check` 由物料 `inspection_required=1` 自动派生为 `"1"`(原料B 99003 则为 `"0"`)。
11. **ERP 收货**: 到货通知 → 收货单 → 审批通过。审批后经 MQ 通知 MES 创建 IQC(因存在 iqc_check=1 行)。
12. **MES 来料检验(IQC)**: MES → 质量管理 → IQC → 检验(QUALIFIED)。
13. **验证 ERP 回写**:
    ```sql
    -- 到货通知行 iqc_check 派生
    SELECT material_code, iqc_check FROM `ry-erp`.wm_arrival_notice_line
      WHERE material_id IN (99002,99003) ORDER BY notice_id DESC LIMIT 5;
    -- IQC 检验后收货单质检状态(具体列名见 ErpIqcCompletedConsumer 回写目标)
    ```

## 四、验证总览(三条链路回写点)

| 链路 | MES 动作 | MQ 消息 | ERP 回写表.列 | 验证查询 |
|------|---------|---------|--------------|---------|
| IPQC | IPQC检验 | `erp.ipqc.completed` | `prd_work_order.quality_status` 等6列 | 见步骤4 |
| OQC | OQC检验 | `erp.oqc.completed` | `so_sales_order_line.oqc_status` 等4列 | 见步骤7 |
| IQC | 收货审批→IQC创建→IQC检验 | `erp.iqc.completed` | 收货单质检状态 | 见步骤13 |

## 五、清理测试数据

```sql
USE `ry-erp`;
DELETE FROM wm_material_stock WHERE stock_id IN (99001,99002);
DELETE FROM bd_routing_step WHERE routing_id = 99001;
DELETE FROM bd_routing WHERE routing_id = 99001;
DELETE FROM bd_bom_detail WHERE bom_id = 99001;
DELETE FROM bd_bom WHERE bom_id = 99001;
DELETE FROM bd_material WHERE material_id IN (99001,99002,99003);
```

## 六、注意事项

- **MQ 必须通畅**: 三条链路均依赖 RabbitMQ,若回写未发生,先查 `mq_message_record`(master 库)与 ERP 消费者日志。
- **租户上下文**: 消费端从消息恢复 tenantId(000000),若数据查不到,确认测试数据的 `tenant_id='000000'`。
- **OQC 单需手工关联 soId**: OQC 在 MES 手工创建(非自动),建单时务必选择销售订单,否则 `so_id` 为空会跳过回写(见 `QcOqcServiceImpl.inspect` 的 soId null 检查)。
- **IPQC 单需关联 MES 工单**: `mes_wo_id` 为 NOT NULL,建单时必须选工单;且该 MES 工单须有 `erp_wo_id`(即由 ERP 工单下发而来),否则跳过回写。
- **工艺路线自动命中(无需工单表单指定)**: ERP 下发消息不带 routingId,MES `createFromErpMessage` 通过 `MesErpBridgeService.getDefaultRoutingId(materialId)` 回查 ERP `bd_routing`(material_id 匹配 + is_current=1 + status=1),即 routing 99001 会被自动命中并生成 MES 工序步骤(`pro_work_order_step`,含焊接关键工序)。故工单表单无需手工选工艺路线。
- **BOM 关联**: 生产计划确认时自动关联 ERP BOM 99001(is_current=1)。MES 侧 BOM 快照(`pro_work_order_bom_line`)依赖 MES 编制的 MBOM(`mes_mbom`),未编制则跳过快照(非致命,不影响质检联动)。
- **erpWoId 回链**: MES 工单创建时 `pro_work_order.erp_wo_id` = ERP 工单 ID(下发消息 woId),这是 IPQC 回写 ERP 工单的关键链路。
