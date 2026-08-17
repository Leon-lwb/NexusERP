# NexusERP MES 刷写能力抽象优化方案

> **文档版本**: v2.0（按用户决策定稿：彻底重命名 + P0-P3 全量交付）
> **制定日期**: 2026-08-17
> **状态**: 待用户审阅剩余决策点 → 进入执行
> **依据**: 2026-08-16 固件刷写 5 次提交代码审查 + 主流智能硬件产线 provisioning 流程 + 用户建议
> **前序文档**: `doc/mes/plans/archive/2026-08-13-mes-firmware-flash-test-plan-v2.0.md`

---

## 〇、用户决策（已定）

| 决策点 | 选择 |
|--------|------|
| 改造策略 | **彻底重命名**：`fw_` 表/包/字段/权限串统一改为通用命名（`mes_flash_*`/`mes_*`），`firmware_id`→`content_ref_id`，一次性消除刷写层全部"固件"硬编码，含数据迁移 |
| 交付范围 | **P0-P3 全量**：抽象地基 + 工序多类型门禁 + 资源池(MAC/SN) + 配置文件类 + 外部REST + 产线工具上报泛化，约 18-20 人天 |

**重命名边界界定**：刷写层（记录/类型/资源池/门禁/上报/包名/权限）彻底通用化、零固件硬编码；**固件档案作为"固件"领域实体管理保留固件语义**（固件确实是固件，类似 `bd_material` 保留物料语义），表名 `fw_firmware`→`mes_firmware`（前缀统一），作为 `firmware_file` 刷写类型的内容源被 `content_ref_type=FIRMWARE` 引用。

---

## 一、背景与动机

### 1.1 现状

MES 固件刷写模块（`com.nexus.mes.fw` 包，4 表 + 5 次提交于 2026-08-16）将"刷写"硬编码为"刷写固件"：

- `fw_flash_record` 写死 `firmware_id`/`firmware_version`/`file_md5`（`FwFlashRecord.java:59,62,65`）；
- `pro_work_order_step.firmware_id` 单一绑定固件（`ProWorkOrderStep.java:38`）；
- 内容来源只有 OSS 文件（`fw_firmware.oss_id`），无资源池/外部系统概念；
- 门禁 `checkFlashStepGate` 直接按 `firmwareId` 查 `FwFlashRecord`（`ProFlowCardServiceImpl.java:331-370`）。

### 1.2 设计与实现的落差（代码审查发现）

v2.0 方案规划了多项能力，但 08-16 落地代码只实现了一部分，且存在死字段：

| v2.0 规划 | 实际落地 | 影响 |
|-----------|---------|------|
| `step_type` 按 process_code 关键字打标 | **`step_type` 列与 `mes_step_type` 字典从未被读写，纯死字段**；`nexus.mes.fw.flash-keywords/test-keywords` 配置与 `FwProperties` 类不存在 | 打标空白 |
| 物料默认 RELEASED 固件自动带出 | **未实现**；`firmware_id` 唯一来源是 ERP 工艺路线步骤 `bd_routing_step.firmware_id` 手工配（`ProWorkOrderServiceImpl.java:417-420`） | 工单不自动带固件 |
| confirmFlash 三层防错 | **未实现**；`buildValidatedRecord` 只校验 SN 非报废 + 固件 RELEASED（`FwFlashRecordServiceImpl.java:239-296`） | 防错缺失 |
| 门禁 FORCE/WARN 双模式 | **未实现**；实际 boolean `mes.flash-step-gate`，FORCE-only（`ProFlowCardServiceImpl.java:82`） | 无降级 |
| `completeDispatch` 门禁钩子 | **未实现**；派工聚合完工（`checkAndCompleteStep`）绕过门禁 | 门禁缺口 |
| SN 作用域"工序级" | 实际取"整卡"（`ProSn::getCardId`，`:348-349`） | 作用域偏粗 |

**结论**：当前是"半成品 + 硬编码固件"。缺失能力本就要补，直接按抽象模型设计 + 彻底重命名一并完成，阻力反而不大。

### 1.3 用户建议与主流流程印证

**用户建议**：把"刷写固件"上抽象为"刷写"，类型可配置（固件/配置文件/MAC/SN…），来源可配置（审批文件绑工单 / 外部资源池 REST 或内部导入，一 SN 绑一资源）；MES 作中心大脑提供资源与接口，灵活编排，支撑产线工具。

**主流产线 provisioning 流程**印证此方向：烧录工位统一处理固件/MAC/SN/配置/证书/校准数据注入，MES 提供 SN/MAC 资源池（原子分配）+ 版本文件 + 追溯，工位客户端 REST 拉取编排。内容类型多样、来源多样、方法多样。

---

## 二、核心抽象模型

"刷写"建模为三个正交维度：

```
刷写（Provisioning）= 在某工序，对某 SN，写入某「内容」

维度1  刷写类型(flash_type)   写什么：固件/配置/MAC/SN/证书/校准数据…
维度2  内容来源(source_type)  从哪来：FILE(审批文件) / POOL(资源池) / EXTERNAL(外部REST) / INLINE(现场上报)
维度3  校验策略(verify)       怎么算PASS：MD5匹配 / 资源已绑定 / 自定义
```

### 2.1 FlashTypeProvider SPI（复用 Flow 模式）

借鉴 `ruoyi-flow` 的 `FlowNodeProvider`（`ruoyi-flow/.../node/FlowNodeProvider.java`）+ `FlowNodeRegistry`（`@PostConstruct` 收集所有 Provider Bean 按 type 注册）：

```java
public interface FlashTypeProvider {
    String getTypeCode();            // firmware_file / config_file / mac / sn / cert…
    String getSourceType();          // FILE / POOL / EXTERNAL / INLINE
    String getDisplayName();
    JsonNode getSourceConfigSchema();// 来源配置schema(前端表单驱动, 仿 NodeParamSchema)
    FlashContent resolveContent(FlashContext ctx);          // 解析"应刷内容"
    FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashReportBo actual); // 校验
    ResourceAllocation allocate(FlashContext ctx);          // 资源分配(POOL/EXTERNAL类)
}

@Component
public class FlashTypeRegistry {  // 仿 FlowNodeRegistry
    @PostConstruct void init() { /* 收集所有 FlashTypeProvider Bean, 按 typeCode 注册 */ }
    FlashTypeProvider getProvider(String typeCode);
}
```

### 2.2 内置 Provider

| Provider | typeCode | sourceType | 内容来源 | 校验策略 |
|----------|----------|-----------|---------|---------|
| `FirmwareFileFlashProvider` | `firmware_file` | FILE | `mes_firmware`（审批发布） | MD5 匹配 |
| `ConfigFileFlashProvider` | `config_file` | FILE | `mes_content_file`（配置文件档案，审批发布） | MD5 匹配 |
| `MacPoolFlashProvider` | `mac` | POOL | `mes_resource_pool`(MAC) 原子分配，一 SN 绑一 MAC | 资源已绑定 |
| `SnPoolFlashProvider` | `sn` | POOL | `mes_resource_pool`(SN) 原子分配 | 资源已绑定 |
| `ExternalRestFlashProvider` | `external_*` | EXTERNAL | 外部 REST（复用 `HttpNodeProvider` SSRF 防护） | 自定义 |

> 开放 SPI：后续"刷校准数据""注入证书"等只需新增一个 Provider Bean，无需改框架。

---

## 三、数据模型改造（彻底重命名 + 数据迁移）

### 3.1 重命名映射总表

| 旧 | 新 | 说明 |
|----|----|------|
| 包 `com.nexus.mes.fw` | `com.nexus.mes.flash` | 全部类迁移 |
| `fw_flash_record` | `mes_flash_record` | 通用刷写记录 |
| `fw_flash_type`（新） | `mes_flash_type` | 刷写类型定义 |
| `fw_firmware` | `mes_firmware` | 固件档案（保留固件语义，前缀统一） |
| `fw_test_item` / `fw_test_record` | `mes_test_item` / `mes_test_record` | 测试项/记录（保留，见决策点3） |
| `fw_resource_pool` / `fw_resource_item`（新） | `mes_resource_pool` / `mes_resource_item` | 资源池 |
| 记录字段 `firmware_id` | `content_ref_id` | 通用内容引用 |
| 记录字段 `firmware_version` | `content_version` | 通用内容版本 |
| 记录字段 `file_md5` | `content_checksum` | 通用校验值 |
| `pro_work_order_step.firmware_id` | `content_ref_type`+`content_ref_id`+`step_flash_config` | 工序多类型绑定 |
| 权限 `mes:fw:*` | `mes:flash:*` | 全部权限串 |
| 审批业务名 `fwFirmwareService` | `mesFirmwareService` | 固件审批回调 |
| 配置 `mes.fw.gate.mode` / `mes.flash-step-gate` | `mes.flash.gate.mode` | 统一门禁开关 |
| 上报路径 `/mes/fw/report/**` | `/mes/flash/report/**` | 产线工具入口 |
| 编码 `MES_FW_CODE`/`MES_FLASH_NO`/`MES_TEST_NO` | `MES_FW_CODE`/`MES_FLASH_NO`/`MES_TEST_NO`（保留） | 编码规则不变 |

### 3.2 `mes_flash_type` — 刷写类型定义（新建）

```sql
CREATE TABLE mes_flash_type (
  flash_type_id    BIGINT       NOT NULL COMMENT '刷写类型ID',
  type_code        VARCHAR(32)  NOT NULL COMMENT '类型标识: firmware_file/config_file/mac/sn/external_*',
  type_name        VARCHAR(64)  NOT NULL COMMENT '显示名: 刷写固件/刷写MAC…',
  source_type      VARCHAR(16)  NOT NULL COMMENT '内容来源: FILE/POOL/EXTERNAL/INLINE',
  source_provider  VARCHAR(64)  NOT NULL COMMENT 'SPI Provider bean名',
  source_config    TEXT         NULL     COMMENT '来源配置JSON(审批流标识/池ID/REST模板)',
  verify_strategy  VARCHAR(16)  NOT NULL DEFAULT 'MD5_MATCH' COMMENT 'MD5_MATCH/RESOURCE_BOUND/CUSTOM',
  gate_enabled     CHAR(1)      NOT NULL DEFAULT '1' COMMENT '是否参与完工门禁',
  status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  sort_order       INT          NOT NULL DEFAULT 0,
  tenant_id        varchar(20)  NOT NULL DEFAULT '000000',
  -- 审计字段(create_dept/by/time, update_by/time, del_flag)同 MES 约定
  PRIMARY KEY (flash_type_id),
  UNIQUE KEY uk_tenant_type_code (tenant_id, type_code)
) COMMENT='刷写类型定义(配置化目录)';
```
种子：5 种内置类型（firmware_file/config_file/mac/sn/external）。

### 3.3 `mes_flash_record` — 通用刷写记录（重命名 + 字段泛化）

```sql
-- 由 fw_flash_record 重命名 + 字段泛化, 数据迁移见 §3.7
CREATE TABLE mes_flash_record (
  flash_record_id   BIGINT       NOT NULL COMMENT '刷写记录ID',
  flash_no          VARCHAR(64)  NOT NULL COMMENT '记录号(MES_FLASH_NO)',
  flash_type_id     BIGINT       NOT NULL COMMENT '刷写类型ID(mes_flash_type)',
  flash_type_code   VARCHAR(32)  NOT NULL COMMENT '刷写类型标识(快照)',
  sn_id             BIGINT       NOT NULL,
  sn_code           VARCHAR(64)  NOT NULL,
  mes_wo_id         BIGINT       NOT NULL,
  wo_no             VARCHAR(64)  NULL,
  card_id           BIGINT       NULL,
  step_record_id    BIGINT       NULL,
  process_id        BIGINT       NULL, process_code VARCHAR(64) NULL, process_name VARCHAR(128) NULL,
  content_ref_type  VARCHAR(16)  NULL COMMENT '内容引用类型: FIRMWARE/CONFIG/MAC_POOL/SN_POOL/EXTERNAL',
  content_ref_id    BIGINT       NULL COMMENT '内容源记录ID(固件=mes_firmware_id, 池=mes_resource_item_id)',
  content_version   VARCHAR(32)  NULL COMMENT '内容版本(固件类=固件版本)',
  content_payload   TEXT         NULL COMMENT '实际写入内容快照JSON(如 {"mac":"00:1A:..."} )',
  content_checksum  VARCHAR(64)  NULL COMMENT '内容校验值(泛化MD5/校验码)',
  equipment_id      BIGINT       NULL, equipment_code VARCHAR(64) NULL,
  operator_id       BIGINT       NULL, operator_name VARCHAR(64) NULL,
  flash_time        DATETIME     NULL,
  result            VARCHAR(8)   NOT NULL COMMENT 'PASS/FAIL',
  fail_reason       VARCHAR(255) NULL,
  retry_count       INT          NOT NULL DEFAULT 0,
  flash_mode        VARCHAR(8)   NOT NULL DEFAULT 'MANUAL' COMMENT 'AUTO/MANUAL',
  tenant_id         varchar(20)  NOT NULL DEFAULT '000000',
  -- 审计字段
  PRIMARY KEY (flash_record_id),
  UNIQUE KEY uk_tenant_flash_no (tenant_id, flash_no),
  KEY idx_tenant_id (tenant_id), KEY idx_sn (sn_id),
  KEY idx_flash_type (tenant_id, flash_type_id), KEY idx_wo (mes_wo_id),
  KEY idx_content_ref (content_ref_type, content_ref_id)
) COMMENT='刷写记录(SN级,通用)';
```
- **零 `firmware_id` 字段**：固件类刷写用 `content_ref_type=FIRMWARE`+`content_ref_id=固件ID`+`content_version`+`content_checksum=MD5`；
- MAC 类：`content_ref_type=MAC_POOL`+`content_ref_id=资源项ID`+`content_payload={"mac":"..."}`；
- 同一 SN 允许多条（FAIL 重刷），判定取最新一条；`idx_content_ref` 支撑召回。

### 3.4 `pro_work_order_step` — 工序多类型刷写绑定（重命名字段）

```sql
ALTER TABLE pro_work_order_step
  DROP COLUMN firmware_id,                                  -- 旧单一固件字段
  ADD COLUMN content_ref_type VARCHAR(16) NULL COMMENT '内容引用类型(单类型快捷绑定)',
  ADD COLUMN content_ref_id   BIGINT      NULL COMMENT '内容源记录ID',
  ADD COLUMN step_flash_config TEXT       NULL COMMENT '多类型绑定JSON: [{"flashTypeCode":"firmware_file","contentRefId":123},{"flashTypeCode":"mac"}]',
  ADD KEY idx_step_type (tenant_id, step_type);
```
- `step_type` 激活（GENERAL/FLASH/TEST），由打标逻辑写入；
- 单类型快捷绑定走 `content_ref_type`+`content_ref_id`（固件类即 FIRMWARE+固件ID）；多类型走 `step_flash_config` JSON；
- 门禁判定从 `firmware_id != null` 改为 `step_flash_config 非空 或 content_ref_type 非空`。

### 3.5 `mes_resource_pool` / `mes_resource_item` — 资源池（新建，P2）

```sql
CREATE TABLE mes_resource_pool (
  pool_id        BIGINT       NOT NULL,
  pool_code      VARCHAR(64)  NOT NULL,
  pool_name      VARCHAR(128) NOT NULL,
  resource_type  VARCHAR(16)  NOT NULL COMMENT 'MAC/SN/CERT…',
  source         VARCHAR(16)  NOT NULL DEFAULT 'INTERNAL' COMMENT 'INTERNAL(导入)/EXTERNAL(REST)',
  external_config TEXT        NULL     COMMENT '外部REST配置JSON(baseUrl/路径/鉴权, 经SSRF校验)',
  alloc_strategy VARCHAR(16)  NOT NULL DEFAULT 'FIFO',
  total_count    INT          NULL, used_count INT NOT NULL DEFAULT 0,
  status         VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  tenant_id      varchar(20)  NOT NULL DEFAULT '000000',
  -- 审计字段
  PRIMARY KEY (pool_id), UNIQUE KEY uk_tenant_pool_code (tenant_id, pool_code)
) COMMENT='刷写资源池(MAC/SN等)';

CREATE TABLE mes_resource_item (
  item_id        BIGINT       NOT NULL,
  pool_id        BIGINT       NOT NULL,
  resource_value VARCHAR(128) NOT NULL COMMENT '资源值(如MAC 00:1A:2B:3C:4D:5E)',
  status         VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/ALLOCATED/USED/SCRAPPED',
  bound_sn_id    BIGINT       NULL COMMENT '已绑定SN(一SN绑一资源)',
  bound_time     DATETIME     NULL,
  tenant_id      varchar(20)  NOT NULL DEFAULT '000000',
  -- 审计字段
  PRIMARY KEY (item_id),
  UNIQUE KEY uk_tenant_pool_value (tenant_id, pool_id, resource_value),
  KEY idx_pool_status (pool_id, status), KEY idx_bound_sn (bound_sn_id)
) COMMENT='资源池明细';
```
- 对应用户建议"外部系统mac资源池，REST获取或导入内部，一SN绑一资源"；
- 原子分配：`UPDATE mes_resource_item SET status=ALLOCATED,bound_sn_id=? WHERE status=AVAILABLE LIMIT 1`（CAS）+ Lock4j 兜底。

### 3.6 `mes_content_file` — 配置文件档案（新建，P2）

配置文件类刷写的内容源，复用固件档案的审批发布范式：

```sql
CREATE TABLE mes_content_file (
  content_file_id BIGINT       NOT NULL,
  content_code    VARCHAR(64)  NOT NULL COMMENT '配置编码(MES_CONFIG_CODE)',
  content_name    VARCHAR(128) NOT NULL,
  content_type    VARCHAR(32)  NOT NULL COMMENT '配置类型: CONFIG_FILE(可扩展校准/证书)',
  material_id     BIGINT       NOT NULL, material_code VARCHAR(64) NULL, material_name VARCHAR(128) NULL,
  version         VARCHAR(32)  NOT NULL,
  oss_id          BIGINT       NULL, file_md5 VARCHAR(64) NULL, file_size BIGINT NULL,
  release_note    TEXT         NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  is_default      CHAR(1)      NOT NULL DEFAULT '0',
  select_flow_id  BIGINT       NULL, published_by BIGINT NULL, publish_time DATETIME NULL,
  tenant_id       varchar(20)  NOT NULL DEFAULT '000000',
  -- 审计字段
  PRIMARY KEY (content_file_id), UNIQUE KEY uk_tenant_content_code (tenant_id, content_code)
) COMMENT='刷写内容档案(配置文件等, 审批发布)';
```
- 与 `mes_firmware` 同构（审批发布、版本、MD5、is_default），由 `ConfigFileFlashProvider` 引用；
- 也可承载未来"校准数据文件""证书文件"等 FILE 类内容。

### 3.7 数据迁移脚本（P0，系统 08-16 刚上线，数据量小）

```sql
-- 1. 表重命名
RENAME TABLE fw_firmware TO mes_firmware;
RENAME TABLE fw_flash_record TO mes_flash_record;
RENAME TABLE fw_test_item TO mes_test_item;
RENAME TABLE fw_test_record TO mes_test_record;

-- 2. mes_flash_record 字段泛化 + 数据回填
ALTER TABLE mes_flash_record
  ADD COLUMN flash_type_id BIGINT NULL AFTER flash_no,
  ADD COLUMN flash_type_code VARCHAR(32) NULL AFTER flash_type_id,
  ADD COLUMN content_ref_type VARCHAR(16) NULL AFTER process_name,
  ADD COLUMN content_ref_id BIGINT NULL AFTER content_ref_type,
  ADD COLUMN content_version VARCHAR(32) NULL AFTER content_ref_id,
  ADD COLUMN content_payload TEXT NULL AFTER content_version,
  ADD COLUMN content_checksum VARCHAR(64) NULL AFTER content_payload,
  ADD KEY idx_flash_type (tenant_id, flash_type_id), ADD KEY idx_content_ref (content_ref_type, content_ref_id);
-- 回填历史固件刷写记录
UPDATE mes_flash_record SET flash_type_code='firmware_file', content_ref_type='FIRMWARE',
  content_ref_id=firmware_id, content_version=firmware_version, content_checksum=file_md5
  WHERE firmware_id IS NOT NULL;
UPDATE mes_flash_record r JOIN mes_flash_type t ON t.type_code='firmware_file' SET r.flash_type_id=t.flash_type_id
  WHERE r.flash_type_code='firmware_file';
ALTER TABLE mes_flash_record DROP COLUMN firmware_id, DROP COLUMN firmware_version, DROP COLUMN file_md5;

-- 3. pro_work_order_step 字段迁移
ALTER TABLE pro_work_order_step ADD COLUMN content_ref_type VARCHAR(16) NULL AFTER step_type,
  ADD COLUMN content_ref_id BIGINT NULL AFTER content_ref_type, ADD COLUMN step_flash_config TEXT NULL;
UPDATE pro_work_order_step SET content_ref_type='FIRMWARE', content_ref_id=firmware_id WHERE firmware_id IS NOT NULL;
ALTER TABLE pro_work_order_step DROP COLUMN firmware_id;

-- 4. 字典/权限/审批业务名/配置键更新(见 §3.8)
```
> 迁移脚本放 `script/sql/update/2026-08-17-mes-flash-abstraction.sql`，幂等 + 回滚预案。历史测试记录的 `firmware_id` 同步迁移为 `content_ref_*`。

### 3.8 字典 / 编码 / 配置 / 权限重命名

| 项 | 旧 | 新 |
|----|----|----|
| 字典 | `mes_fw_status`/`mes_fw_mode`/`mes_fw_result` | `mes_flash_status`/`mes_flash_mode`/`mes_flash_result`（保留固件档案用 mes_fw_status） |
| 字典（新） | — | `mes_flash_type`(刷写类型)/`mes_flash_source`(来源FILE/POOL/EXTERNAL/INLINE) |
| 字典 | `mes_step_type` | 保留，激活（GENERAL/FLASH/TEST） |
| 配置 | `mes.fw.gate.mode`(死)/`mes.flash-step-gate`(bool) | `mes.flash.gate.mode`（FORCE/WARN，统一） |
| 配置（新） | — | `nexus.mes.flash.flash-keywords`/`test-keywords`（FwProperties） |
| 权限 | `mes:fw:firmware:*`/`mes:fw:flash:*`/`mes:fw:testitem:*`/`mes:fw:testrecord:*` | `mes:flash:firmware:*`/`mes:flash:record:*`/`mes:flash:testitem:*`/`mes:flash:testrecord:*`/`mes:flash:pool:*`/`mes:flash:report:*` |
| 审批业务名 | `fwFirmwareService` | `mesFirmwareService` |
| 菜单 | 固件管理(4280) | 刷写管理(4280，下含固件档案/配置档案/刷写作业/测试作业/资源池/测试记录/测试项) |
| security.excludes | `/mes/fw/report/**` | `/mes/flash/report/**` |

---

## 四、工序门禁泛化

### 4.1 checkFlashStepGate 多类型化

`ProFlowCardServiceImpl.checkFlashStepGate`（当前 `:334-370`）改造：

```
原: woStep.getFirmwareId() != null → 按该 firmwareId 查最新一条 PASS
新: 解析 woStep.stepFlashConfig(或 content_ref_type) → 得 flashTypeCode 清单
    → 对每个 typeCode, 查该 SN 该类型最新一条 mes_flash_record(flash_type_code + snCode) 是否 PASS
    → 全部类型 PASS 才放行; 任一缺失/FAIL → 按 mes.flash.gate.mode 决定 FORCE拦截/WARN告警
```
- 注入从 `FwFlashRecordMapper` 改为 `MesFlashRecordMapper`（重命名后）；
- 查询条件 `flash_type_code + snCode`，按 `flash_time DESC LIMIT 1` 取最新。

### 4.2 补全 v2.0 缺失能力

| 缺口 | 改造点（当前代码位置） |
|------|----------------------|
| completeDispatch 门禁 | `ProDispatchServiceImpl.completeDispatch`(`:222-271`)+`checkAndCompleteStep`(`:472-493`) 补门禁 |
| FORCE/WARN 双模式 | 激活 `mes.flash.gate.mode`，WARN 仅 log 告警放行 |
| SN 作用域工序级 | 门禁查询从"整卡 SN"收窄到"该工序作用域 SN"（经 stepRecordId 映射） |
| confirmFlash 三层防错 | `buildValidatedRecord`(`:239-296`) 补：内容物料=SN物料、工序绑定一致、SN当前工序为FLASH且在制 |
| 物料默认固件带出 | `generateStepsFromRouting`(`:406-432`) 补：FLASH 工序自动刷 content_ref_id = 物料 `is_default=1 且 RELEASED` 的固件 |
| process_code 打标 | `generateStepsFromRouting` 按 `FwProperties.flashKeywords/testKeywords` 落 `step_type` |

### 4.3 confirmFlash 路由到 Provider

`MesFlashRecordServiceImpl.confirmFlash`/`buildValidatedRecord` 按 `flash_type_code` 路由：
- `FlashTypeRegistry.getProvider(typeCode).resolveContent(ctx)` 取应刷内容；
- `.verify(ctx, expected, actual)` 校验（替代写死的 RELEASED + MD5 回填）；
- 固件类 RELEASED 校验 + MD5 回填下沉到 `FirmwareFileFlashProvider`。

---

## 五、产线工具上报统一入口

`MesReportController`（重命名自 `FwReportController`，`/mes/flash/report/**`，X-API-KEY 鉴权 + 固定租户，当前 `:54-69`）泛化多类型：

```json
POST /mes/flash/report/flash/auto
{
  "snCode": "SN202608170001",
  "flashTypeCode": "mac",            // 必填, 路由到 Provider
  "processCode": "BURN-MAC",
  "result": "PASS",
  "contentPayload": { "mac": "00:1A:2B:3C:4D:5E" },  // POOL/INLINE类必填
  "contentChecksum": "...",          // FILE类的MD5
  "equipmentCode": "BURN-STN-03",
  "operatorName": "张三",
  "failReason": ""
}
```
服务端：`FlashTypeRegistry.getProvider(flashTypeCode).verify(...)` → 落 `mes_flash_record`。对应"MES 作中心大脑，为产线工具提供接口，灵活编排"。

---

## 六、分期交付计划（P0-P3 全量，约 18-20 人天）

### P0 抽象地基 + 彻底重命名 + 数据迁移（5-6d）

- 增量 SQL `script/sql/update/2026-08-17-mes-flash-abstraction.sql`：表重命名 + 字段泛化 + 数据迁移（§3.7）+ `mes_flash_type`/`mes_content_file` 建表 + 字典/权限/配置重命名；
- 包迁移 `com.nexus.mes.fw` → `com.nexus.mes.flash`（33 类全量重命名，含 Mapper/Service/Bo/Vo/Controller）；
- `FlashTypeProvider` SPI + `FlashTypeRegistry`（仿 `FlowNodeRegistry`）；
- `FirmwareFileFlashProvider`：固件 RELEASED 校验 + MD5 回填从 `FwFlashRecordServiceImpl` 下沉；
- `confirmFlash`/`reportFlash` 按 `flash_type_code` 路由（固件类行为不变）；
- 审批业务名 `fwFirmwareService`→`mesFirmwareService`，流程变量/回调 dataId 同步；
- 前端 `src/views/mes/fw/` → `src/views/mes/flash/`，`src/api/mes/fw/` → `src/api/mes/flash/`，权限串/文案更新；
- 里程碑：✅ 重命名完成、固件刷写经新抽象层运行行为一致、数据迁移校验通过。

### P1 工序多类型绑定 + 门禁泛化 + 补全（5-6d）

- `pro_work_order_step.content_ref_type/content_ref_id/step_flash_config` + `step_type` 激活；
- `FwProperties`(`@ConfigurationProperties`) + `generateStepsFromRouting` 打标 + 物料默认固件带出；
- `checkFlashStepGate` 多类型化 + `completeDispatch`/`checkAndCompleteStep` 补门禁 + FORCE/WARN + SN 作用域工序级；
- `confirmFlash` 补三层防错；
- 前端：工单工序页加 `step_type` 标签 + 刷写绑定多选 UI（`ProWorkOrderStepVo` 补 `stepType`/`stepFlashConfig`，当前 Vo 无 `stepType`）；
- 里程碑：✅ 一工序绑多刷写类型、门禁全 PASS 才放行、v2.0 承诺能力补齐。

### P2 资源池 + MAC/SN + 配置文件类（5-6d）

- `mes_resource_pool`/`mes_resource_item` 表 + 资源池管理页（导入/外部REST配置/原子分配/状态看板）；
- `MacPoolFlashProvider`/`SnPoolFlashProvider`（POOL 来源，一 SN 绑一资源，CAS 原子分配 + Lock4j）；
- `mes_content_file` 表 + 配置文件档案页（审批发布，复用固件范式）+ `ConfigFileFlashProvider`；
- 刷写作业页支持选刷写类型（固件/配置/MAC/SN），按类型动态渲染内容输入；
- 里程碑：✅ 刷 MAC/SN/配置文件全链路、MAC 池原子分配不撞号。

### P3 外部 REST 来源 + 产线工具编排增强（3-4d）

- `ExternalRestFlashProvider`（复用 `HttpNodeProvider` SSRF 防护，`ruoyi-flow/.../node/integration/HttpNodeProvider.java`）；
- `MesReportController` 上报协议泛化多类型 + 对接文档 + mock 服务；
- 产线工位编排：工位客户端按工序拉取应刷内容清单（`GET /mes/flash/station/pending?snCode=` 返回该 SN 当前工序所有待刷类型及应刷内容）；
- 里程碑：✅ 产线工具经统一接口编排多类刷写、外部 REST 来源安全接入。

---

## 七、耦合点改造清单（10 类，当前文件:行号）

| # | 耦合点 | 当前文件:行号 | 改造 |
|---|--------|--------------|------|
| 1 | 门禁按 firmwareId 查 | `ProFlowCardServiceImpl.java:331-370` | 多类型化 + 查 mes_flash_record（§4.1） |
| 2 | 工序下发透传 firmware_id | `ProWorkOrderServiceImpl.java:417-420` | 打标 + 默认固件带出 + content_ref/step_flash_config |
| 3 | archive 字符串SQL查工序表 | `FwFirmwareServiceImpl.java:420-421` | 改查 content_ref_type=FIRMWARE（重命名后） |
| 4 | confirm 强制 RELEASED + MD5回填 | `FwFlashRecordServiceImpl.java:239-296,313-319` | 下沉 FirmwareFileFlashProvider，按type路由 |
| 5 | 上报入口固件专用 | `FwReportController.java:54-69` | 重命名 MesReportController + 多类型（§5） |
| 6 | step_type 死字段 | `ProWorkOrderStep.java:35` + Vo 无 stepType | 激活 + Vo 补字段 + 前端标签 |
| 7 | mes.fw.gate.mode 无消费方 | SQL预置 + `ProFlowCardServiceImpl.java:82` | 激活 mes.flash.gate.mode FORCE/WARN |
| 8 | 前端文案/类型硬编码 | `flash/index.vue:114,255-262`等 | 重命名 mes/flash + 刷写类型下拉 + 多类型展示 |
| 9 | 字典/权限固件专用 | `2026-08-13-mes-firmware.sql:171-188,247` | 重命名 + 新增 mes_flash_type/source + pool 权限 |
| 10 | 包名/类名 fw 前缀 | `com.nexus.mes.fw.*`（33类） | 全量重命名 com.nexus.mes.flash |

---

## 八、风险与应对

| 风险 | 应对 |
|------|------|
| 重命名 + 数据迁移破坏现有固件刷写 | P0 含迁移脚本 + 回滚预案；迁移后回归现有固件 E2E；系统刚上线数据量小 |
| 门禁泛化后误拦截现有工单 | P0 固件类经 Provider 路由行为不变；P1 gate.mode=WARN 灰度 |
| step_flash_config JSON 解析开销 | 工序绑定数据量小，解析缓存；门禁按 flash_type_code+snCode 走索引 |
| 资源池原子分配并发 | `UPDATE ... WHERE status=AVAILABLE LIMIT 1` CAS + Lock4j 分布式锁兜底 |
| 外部 REST 来源安全 | 复用 `HttpNodeProvider` SSRF 防护，URL 白名单 + 内网限制 |
| 重命名跨模块引用（ERP `bd_routing_step.firmware_id`） | ERP 侧 `BdRoutingStep.java:131-143` + `ErpRoutingStepMapper.java:21-24` 同步改名或加兼容视图；MES 桥接层适配 |
| 审批流业务名变更影响在途流程 | 选低峰期切换；`wf_custom_form.business_service` 更名 + 历史在途实例迁移 |

---

## 九、剩余待确认决策点

1. **配置文件类内容载体**：`mes_content_file` 独立表（语义清晰，本方案采用），还是复用 `mes_firmware` 加 `content_type` 区分固件/配置（省表）？建议独立表。
2. **测试模块是否同步泛化**：本方案建议测试模块（`mes_test_item`/`mes_test_record`）本期**仅重命名**，测试项仍按固件版本范围（`content_ref_id` 引用固件）——测试项按固件版本范围本身合理。若需测试基准也泛化为"按刷写类型版本范围"，请说明。
3. **ERP 侧 `bd_routing_step.firmware_id` 处理**：同步重命名为 `content_ref_*`（彻底但跨模块改 ERP），还是 MES 桥接层适配保留 ERP 字段名（隔离改动）？建议桥接层适配。
4. **产线工位编排接口**：P3 的 `GET /mes/flash/station/pending?snCode=`（工位拉取待刷清单）是否本期必须，还是产线工具自行按工序配置拉取？建议本期提供。

---

## 十、验证方式（端到端）

- **P0 回归**：现有固件刷写 E2E（建固件→审批→绑工单→扫码刷写→门禁放行）经重命名 + Provider 路由后行为一致；数据迁移校验（记录数、字段映射抽检）；
- **P1 多类型门禁**：一工序绑 [firmware_file + mac]，两类型都 PASS 才放行；任一 FAIL 按 FORCE/WARN 表现；completeDispatch 路径门禁生效；
- **P2 资源池**：MAC 池导入 100 个 → 100 SN 各分配唯一 MAC → 并发分配不撞号 → 召回按 MAC 反查 SN；配置文件审批发布后可刷写；
- **P3 产线工具**：mock 工位客户端 POST `/mes/flash/report/flash/auto` 多类型上报，按 typeCode 路由校验落库；外部 REST 来源经 SSRF 校验；
- 复用 `doc/mes/scripts/fw-*.js` 现有 E2E 脚本，重命名 + 扩展多类型场景。
