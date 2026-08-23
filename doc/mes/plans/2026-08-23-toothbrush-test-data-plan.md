# 电动牙刷(TB-100)生产全链路测试数据集方案

> **文档版本**: v1.0
> **制定日期**: 2026-08-23
> **状态**: 已执行（生成器 78/79 项通过，唯一未过项为展示层字段缺失，代码已修复待后端重启生效）
> **生成器**: `doc/mes/scripts/toothbrush-datagen.js`
> **前序文档**: `doc/mes/plans/2026-08-17-mes-flash-fullchain-guide.md`（GW-1000 全链路先例）、`2026-08-22-mes-release-package-plan.md`（发布包功能）

---

## 一、方案要点

### 1.1 生成方式：API 生成器（编号真实走规则）

用户要求"生成编号需根据编号规则生成"——纯 SQL 手工编号会与 `sys_auto_code_result` 计数器脱节
（后续系统生成同前缀编号必撞唯一键，本次执行中 WORKSTATION_CODE 撞 GZZ0002 即为存量脱节实证）。
故采用 **Node.js 调真实 API**（复用 `flash-fullchain-datagen.js` 的登录/加密模板），
所有带规则的编号不传值、由后端 `AutoCodeUtil.genSerialCode` 真实生成并消耗流水。

### 1.2 执行中发现并处理的三个环境问题

| 问题 | 现象 | 处置 |
|------|------|------|
| 旧脚本 RSA 公钥损坏 | `flash-fullchain-datagen.js` 中的请求加密公钥少 1 字符（`AA4NADCBiQ` 应为 `AA4GNADCBiQ`），Node 24 解析直接失败 | 生成器使用 application.yml 注释中"对应前端加密公钥"的正确密钥（与请求解密私钥配对，已验证往返） |
| 编码规则计数器与种子数据脱节 | WORKSTATION/WORKSHOP/CLIENT/WAREHOUSE/WM_AREA/WM_LOCATION 六个非循环规则的计数器远落后实际数据（如工位计数器=2 而实际已到 GZZ0017），生成即撞唯一键 | 按 `MAX(现有编码)` 对齐 `sys_auto_code_result.last_serial_no/last_result`，WM_AREA_CODE 补插结果行（含 gen_index，否则更新路径 NPE） |
| `ProWorkOrderStepVo` 缺 `stepType` 字段 | 实体/DB 有 `step_type` 但 VO 未暴露，工单详情接口不返回工序类型（前端详情页同样受影响），生成器断言取不到 | VO 已补 `stepType` 字段并编译通过；**需后端重启后生效**，届时重跑生成器 79/79 |

另有通用发现：`bd_material/bd_workstation/bd_workshop/bd_category/bd_bom/bd_client` 的 Bo 无 remark 字段
（JSON 传入静默丢弃），幂等匹配谓词改用名称唯一性。

### 1.3 幂等设计（find-or-create + 断点续跑）

- 每个实体先按唯一键查询（名称/编码/版本），存在则复用，否则创建后回查
- 执行链路每步带状态守卫：SO 仅 DRAFT 时确认、计划仅 DRAFT 时确认、需求已有则跳过计算、
  工单按来源需求回查、MES 工单轮询复用、流转卡/SN 已有则复用、工序步骤按状态断点推进、
  刷写已全 PASS 则跳过上报
- 任意一步失败后修复重跑，均从断点继续，不产生重复数据

---

## 二、数据集设计（电动牙刷 TB-100）

### 2.1 编号规则对照（本次实际生成值）

| 域 | 规则(格式) | 生成的编号 |
|----|-----------|-----------|
| 成品物料 | MATERIAL_CODE (IF+日期+3位) | **IF20260823001** 电动牙刷 TB-100 |
| 车间 | WORKSHOP_CODE (CJ+3位) | CJ001 电动牙刷装配车间 |
| 工位 | WORKSTATION_CODE (GZZ+4位) | GZZ0018~GZZ0021 + GZZ0001（5个） |
| 固件 | MES_FW_CODE (FW+日期+3位) | FW20260823001(v1.0.0) / FW20260823002(v1.1.0) |
| 仓库 | WAREHOUSE_CODE (WH+3位) | WH006 电动牙刷仓 |
| 库区 | WM_AREA_CODE (A+4位) | A0008 成品区 / A0009 原料区 |
| 库位 | WM_LOCATION_CODE (L+3位) | L020~L025（6个） |
| 客户 | CLIENT_CODE (C+5位) | C00013 洁白口腔护理科技有限公司 |
| 发布包 | MES_RP_CODE (RP+日期+3位) | RP20260823001 v1.0 |
| 客户订单 | SO_CO_NO (CO+日期+3位) | CO20260823001 |
| 销售订单 | SO_ORDER_CODE (XSDD+日期+流水) | XSDD20260823001 |
| 生产计划 | PRD_PLAN_CODE (SCJH+年+流水) | SCJH20260026 |
| ERP工单 | PRD_WORK_ORDER_CODE (SCGD+日期+4位) | SCGD202608230001 |
| MES工单 | —(继承ERP工单号) | SCGD202608230001 |
| 流转卡 | 卡号规则 | FC-SCGD202608230001-4957 |
| SN | 卡号+序号 | SN-FC-SCGD202608230001-4957-0001~0010 |

手工编码（无后端规则，按仓库惯例）：分类 CARE-DEVICE/CARE-RAW/CARE-SEMI/CARE-PKG、工序 FUNC_TEST/WP_TEST、
工艺路线 RT-TB-001、BOM/MBOM 版本 1.0、排班计划 PBJH2026TB01、工号 SCZX2026082300x、班组 ZPTB01/02。

### 2.2 基础数据清单

- **物料分类**（优先挂既有分类，既有树无合适分类才按需新增）：
  - 既有复用：`原材料>电气材料>电气元件`（无刷马达/充电底座）、`原材料>紧固件>螺栓`（螺丝）
  - 按需新增：`半成品>电子组件`（PCBA）、`原材料>电气材料>电池`（锂电池）、
    `原材料>塑料材料>塑料件`（手柄外壳/刷头，既有子级均为树脂类不适用）、`原材料>化工材料>橡胶制品`（密封圈）
  - 保留：`产成品>个人护理电器`（成品）、`辅助材料>个护包装材料`（彩盒/说明书）
  - 分类与 material_type 严格对齐：密封圈/螺丝由辅料(4)修正为原材料(1)（BOM 部件而非包材）
- **物料**（16 个）：成品"电动牙刷 TB-100"（序列管理）+ 半成品"主控板 PCBA-TB100" + 采购件 14 个
  （无刷马达/锂电池/手柄外壳/刷头/充电底座/密封圈/螺丝/彩盒/说明书 + PCBA 电子元器件 5 个）
- **车间/工位**：电动牙刷装配车间（负责人张明）+ 5 工位（装配A/固件烧录/整机测试/质检/包装A）
- **工序**（2 新 + 4 复用）：FUNC_TEST 整机功能测试、WP_TEST 防水气密测试；ASM/FLASH/QC/PKG 复用
- **工艺路线 RT-TB-001**：1 ASM 装配 → **2 FLASH 固件烧录(绑定固件 v1.0.0)** → 3 FUNC_TEST →
  4 WP_TEST → 5 QC 成品检验(关键工序) → 6 PKG 包装
- **BOM 多层结构**（成品 BOM + 半成品子 BOM 两级）：
  - `TB-100标准版BOM`（成品，10 行）：装配序1 = PCBA(自制半成品)×1 / 马达×1 / 电池×1 / 外壳×1 /
    密封圈×2 / 螺丝×4；包装序6 = 刷头×2 / 充电底座×1 / 彩盒×1 / 说明书×1
  - `PCBA-TB100标准版BOM`（半成品子 BOM，5 行）：PCB裸板 / 主控芯片 MC32F006 / 蓝牙模块 BLE50 /
    充电管理IC TP4056 / 马达驱动IC HR8833 各×1（电子元器件均挂既有`电气元件`分类）
  - MRP 多层展开依据**子项物料的 sourceType**（物料级优先于明细级，`processBomNode` L477-479）：
    PCBA 物料 sourceType=PROCESS → 需求计算递归展开其子 BOM；采购件为叶子不展开
- **MBOM v1.0 ACTIVE**（10 行，工序级物料绑定，MES 工单 BOM 快照数据源；快照仅含顶层直接子件，
  子 BOM 由 PCBA 自身的生产计划/工单展开——多层的计划口径与 ERP 一致）
- **固件**：v1.0.0（RELEASED+物料默认，工艺路线绑定）+ v1.1.0（RELEASED，发布包映射用），
  复用 sys_oss 2088871530275577857（刷写链路不读 OSS 文件）
- **SOP 作业指导书**（6 份覆盖全部工序，MES_SOP_CODE 规则生成 SOP20260823001~006）：
  真实 WORD 文件（HTML 存 .doc 中文直显）经 `/resource/oss/upload` 上传 MinIO，建档即挂首版本 1.0 →
  submit → approve 走完整审批状态机至 PUBLISHED（版本转 CURRENT）；每份含适用工位/工具、
  8~10 步作业步骤表（含判定标准）、注意事项（FLASH 的 SOP 明确"固件版本由系统按发布包自动下发，
  禁止手工选版"）；按工序绑定（isDefault=1），工位端 `/mes/sop/document/by-process/{processId}` 可查看；
  装配/烧录/包装为 PRODUCTION，功能/防水/成品检验为 INSPECTION
- **人员班组**：排班计划 PBJH2026TB01（WEEK）+ 早/晚班次 + 工人5名（SCZX20260823001~005，
  装配×2/测试×1/质检×1/包装×1）+ 班组 TB装配组(ZPTB01)/TB测试组(ZPTB02)（含组长成员）+ 排班条目
  （装配组早班/测试组晚班 × 5 个工作日）
- **仓库**：电动牙刷仓 WH006 + TB成品区/TB原料区 + 库位 TB-C-01~03/TB-R-01~03
- **客户+发布包**：洁白口腔 C00013 + 发布包 RP20260823001（客户产品代号 TB100-WHITE，
  固件映射 FLASH工序→v1.1.0，RELEASED+默认）

### 2.3 执行链路（验证发布包匹配 + 刷写门禁）

```
客户订单 CO20260823001 → 销售订单 XSDD20260823001(DRAFT, 行含客户产品代号 TB100-WHITE)
  → 确认SO → 自动创建生产计划 SCJH20260026(带orderLineId) → 计划确认
  → 物料需求计算 → 生成ERP工单 SCGD202608230001(clientId/代号落库)
  → ERP工单下达 → MQ → MES工单自动创建(发布包匹配 RP20260823001)
  → [验证] FLASH工序 contentRefId=v1.1.0固件 + firmware_source=PACKAGE + BOM快照10行
  → MES工单下达(FLASH门禁通过) → 流转卡FC-...(10台) → SN×10
  → ASM开工完工(良品10) → FLASH开工 → 工位待刷清单(应刷v1.1.0)
  → 刷写上报×10 PASS → FLASH完工(刷写门禁校验通过) → 复查待刷清单(清空)
```

验证结论（DB + 接口双重确认）：
- `pro_work_order_step`：FLASH 步骤 `content_ref_id=FW20260823002(v1.1.0)`、`firmware_source=PACKAGE` ✓
- 工位待刷清单返回的 `contentRefId` 与发布包映射固件一致 ✓
- 刷写上报 10/10 PASS，FLASH 工序完工未被 FORCE 门禁拦截 ✓

---

## 三、运行方式

```bash
# 前置: 后端(localhost:8080, 含RabbitMQ/Redis/MySQL)运行中
node doc/mes/scripts/toothbrush-datagen.js
```

- 登录：admin/admin123，租户 000000（验证码经 Redis 直读）
- 可重复执行（find-or-create + 断点续跑守卫）
- 尾部打印 PASS/FAIL 汇总与全部规则生成的编号清单
- 已知事项：`ProWorkOrderStepVo.stepType` 字段修复需后端重启后生效（重启前该断言项 FAIL，
  数据本身已验证正确；重启后重跑生成器为 79/79）

## 四、遗留事项

1. **后端重启**：使 `ProWorkOrderStepVo.stepType`（及前端工单详情页工序类型展示）生效
2. **旧脚本公钥修复建议**：`flash-fullchain-datagen.js`/`p0-4-e2e-test.py` 中的 RSA 公钥同样缺字符，复用时会失败，建议同步修正
3. **计数器脱节为系统性存量问题**：本次仅对齐了 6 个非循环规则；其余规则（如 SCH_TEAM_CODE 等未启用规则的种子数据）如遇同样撞键问题，按同样方法对齐
