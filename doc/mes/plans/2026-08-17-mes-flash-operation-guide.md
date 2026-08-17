# NexusERP MES 刷写能力操作流程指南

> **版本**: v1.0
> **日期**: 2026-08-17
> **适用**: feature/mes-flash-abstraction 分支(P0-P3 + 增强项)
> **配套**: 测试数据 `doc/mes/scripts/flash-seed-data.sql`、冒烟脚本 `doc/mes/scripts/flash-smoke-test.js`

---

## 一、概述

MES 刷写能力将"刷写固件"上抽象为通用"刷写"，支持智能硬件产线多种刷写类型与内容来源，MES 作为中心大脑提供资源、接口，灵活编排支撑产线工具。

### 1.1 三正交维度

```
刷写 = 在某工序, 对某SN, 写入某「内容」

维度1  刷写类型(flash_type)   写什么: 固件/配置/MAC/SN/外部REST
维度2  内容来源(source_type)  从哪来: FILE(审批文件) / POOL(资源池) / EXTERNAL(REST) / INLINE(现场)
维度3  校验策略(verify)       怎么算PASS: MD5匹配 / 资源已绑定 / 自定义
```

### 1.2 5 个内置 Provider（FlashTypeProvider SPI）

| Provider | typeCode | 来源 | 内容源 | 校验 |
|----------|----------|------|--------|------|
| FirmwareFileFlashProvider | firmware_file | FILE | mes_firmware(审批发布) | RELEASED + 物料匹配 + MD5 |
| ConfigFileFlashProvider | config_file | FILE | mes_content_file(审批发布) | RELEASED + 物料匹配 + MD5 |
| MacPoolFlashProvider | mac | POOL | mes_resource_pool(MAC) 原子分配 | 资源已绑定 |
| SnPoolFlashProvider | sn | POOL | mes_resource_pool(SN) 原子分配 | 资源已绑定 |
| ExternalRestFlashProvider | external_rest | EXTERNAL | 外部REST(SSRF防护) | 自定义 |

> 新增刷写类型只需实现 FlashTypeProvider + 注册 mes_flash_type，无需改框架。

### 1.3 核心数据表（ry-mes 库）

| 表 | 用途 |
|----|------|
| mes_flash_type | 刷写类型定义(配置化目录, 5 类型种子) |
| mes_firmware | 固件档案(审批发布, 版本, MD5) |
| mes_content_file | 配置文件档案(审批发布) |
| mes_resource_pool / mes_resource_item | 资源池(MAC/SN, 原子分配) |
| mes_flash_record | 刷写记录(SN级, flash_type_code + content_ref + content_payload) |
| pro_work_order_step | 工单工序(step_type + content_ref + step_flash_config 多类型绑定) |

---

## 二、数据准备

### 2.1 固件档案（firmware_file 刷写内容源）

**路径**: 刷写管理 → 固件档案

1. 新增固件：填名称/适用物料/版本号，上传固件文件（.bin/.hex 等，服务端算 MD5/大小）
2. 提交审批：DRAFT → IN_REVIEW（Flowable 审批流，或管理员兜底 approve）
3. 审批通过：IN_REVIEW → RELEASED（记录发布人/时间）
4. 设为默认：物料内 is_default=1 唯一（工单生成时自动带出）

> 仅 RELEASED 固件可被刷写/绑定工单。归档(ARCHIVED)后不可再绑定。

**API**: `POST /mes/flash/firmware`(新增) `POST /mes/flash/firmware/upload`(上传) `PUT /mes/flash/firmware/approve/{id}`(兜底通过) `PUT /mes/flash/firmware/default/{id}`(设默认)

### 2.2 配置文件档案（config_file 刷写内容源）

**路径**: 刷写管理 → 配置档案

1. 新增配置：填名称/适用物料/版本，上传配置文件
2. 发布：DRAFT → RELEASED（管理员直接发布，P2 简化未接 Flowable）

**API**: `POST /mes/flash/content`(新增) `PUT /mes/flash/content/release/{id}`(发布)

### 2.3 资源池（MAC/SN, POOL 刷写内容源）

**路径**: 刷写管理 → 资源池管理

1. 新增资源池：选资源类型(MAC/SN/CERT)、来源(INTERNAL内部导入/EXTERNAL外部REST)
2. 导入资源项：文本框粘贴（换行/逗号分隔，如 MAC 地址），池内自动去重
3. 状态看板：总数/已用，资源项状态 AVAILABLE/ALLOCATED/USED/SCRAPPED

> 刷写时 Provider 自动 allocate（CAS 原子分配，一SN绑一资源），无需手动分配。

**API**: `POST /mes/flash/pool`(建池) `POST /mes/flash/pool/import/{poolId}`(导入) `POST /mes/flash/pool/allocate`(手动分配, 测试用)

### 2.4 刷写类型（mes_flash_type, 已预置）

5 种类型种子（P0/P2/P3 SQL 已插入）：firmware_file / config_file / mac / sn / external_rest，均 ENABLED。前端刷写类型下拉由该表驱动。

---

## 三、工单配置

### 3.1 工序打标（step_type）

ERP 工单下发 MES 时，`generateStepsFromRouting` 按 `process_code` 关键字自动打标：
- `nexus.mes.flash.flash-keywords`（默认 FLASH,BURN）→ step_type=FLASH
- `nexus.mes.flash.test-keywords`（默认 FCT,TEST,FUNC）→ step_type=TEST
- 未命中 → GENERAL

> 计划员可在工单工序页改标。step_type 为工序分类标签，门禁实际依赖刷写绑定（content_ref/step_flash_config）。

### 3.2 刷写绑定

工序绑定刷写内容两种方式：
- **单类型快捷绑定**：`content_ref_type=FIRMWARE` + `content_ref_id=固件ID`（固件类）
- **多类型绑定**：`step_flash_config` JSON 数组
  ```json
  [{"flashTypeCode":"firmware_file","contentRefId":2091000000000006101},
   {"flashTypeCode":"mac"}]
  ```

FLASH 工序生成时自动带出物料默认 RELEASED 固件（is_default=1）为 content_ref。

> 工单详情页"工序步骤"tab 显示 step_type 标签 + 刷写内容列。

---

## 四、刷写作业（confirmFlash）

**路径**: 刷写管理 → 刷写作业（或产线上报）

### 4.1 操作流程（UI）

1. 扫码/搜索 SN
2. 选**刷写类型**（firmware_file/config_file/mac/sn）
3. 按类型渲染：
   - firmware_file/config_file：选固件/配置文件下拉（contentRefId）
   - mac/sn：显示"从资源池自动分配"提示（无需选内容）
4. 选设备、填结果（PASS/FAIL）、失败原因（FAIL 必填）
5. 确认刷写 → 后端按 flashTypeCode 路由 Provider

### 4.2 后端校验（buildValidatedRecord + Provider）

1. SN 存在且非报废/非已发运（SCRAPPED/SHIPPED 拒）
2. 按 flashTypeCode 路由 FlashTypeProvider：
   - **FirmwareFileFlashProvider**：查 mes_firmware，校验 RELEASED + **物料匹配**(fw.materialId = sn.materialId) + 取 version/md5
   - **MacPoolFlashProvider**：按 resource_type=MAC 找启用池，**allocate 原子分配**一 MAC 给 SN
   - 其他 Provider 同理
3. Provider.verify 校验（固件类 MD5 匹配；上报 PASS 但校验 FAIL → 拦截）
4. 落 mes_flash_record（flash_type_code + content_ref_type/id + content_version + content_checksum + content_payload）

**三层防错**（固件类）：SN 在制 + 固件 RELEASED + 固件物料=SN物料 + MD5 匹配。

**API**: `POST /mes/flash/flash/confirm`

### 4.3 各类型刷写示例

| 类型 | 入参 | 后端行为 |
|------|------|---------|
| firmware_file | {snCode, flashTypeCode:'firmware_file', contentRefId:固件ID, result} | 校验固件 RELEASED+物料+MD5 |
| config_file | {snCode, flashTypeCode:'config_file', contentRefId:配置ID, result} | 校验配置 RELEASED+物料+MD5 |
| mac | {snCode, flashTypeCode:'mac', result} | 从MAC池分配一MAC, 落 content_payload |
| sn | {snCode, flashTypeCode:'sn', result} | 从SN池分配一SN资源 |

---

## 五、产线工具对接

### 5.1 上报接口（report/flash/auto, X-API-KEY 鉴权）

产线工位/上位机刷写后回调，批量上报，按 flashTypeCode 路由 Provider，宽松模式（单条失败不中断）。

```http
POST /mes/flash/report/flash/auto
Header: X-API-KEY: nexus-mes-api-2026
Content-Type: application/json

[
  {"snCode":"SN202606200001", "flashTypeCode":"firmware_file", "contentRefId":2091000000000006101, "result":"PASS"},
  {"snCode":"SN202606200003", "flashTypeCode":"mac", "result":"PASS"}
]
```

响应：`{success: N, failed: [{snCode, reason}]}`

> 密钥配置 `mes.api-key`（application-dev.yml），生产务必修改。security.excludes 放行 `/mes/flash/report/**`。

### 5.2 工位编排接口（station/pending）

产线工位拉取 SN 待刷清单（含已刷结果），驱动工位编排。

```http
GET /mes/flash/report/station/pending?snCode=SN202606200001
Header: X-API-KEY: nexus-mes-api-2026
```

响应：
```json
{"code":200,"data":[
  {"stepType":"FLASH","flashTypeCode":"firmware_file","contentRefId":"2091000000000006101",
   "processName":"切割","sequenceNum":10,"latestResult":"PASS","flashTime":"..."},
  {"stepType":"FLASH","flashTypeCode":"mac","contentRefId":"2091000000000006119",
   "latestResult":"PASS","flashTime":"..."}
]}
```

> 工位按清单逐项刷写，latestResult=null 表示未刷。

---

## 六、完工门禁

### 6.1 门禁逻辑（checkFlashStepGate）

流转卡工序完工（completeStep / 派工聚合完工 completeDispatch→checkAndCompleteStep）时触发：
1. 解析工序刷写绑定（step_flash_config 多类型优先，否则单类型 content_ref）
2. 对每个绑定项 × 卡上每个在制 SN：查 mes_flash_record 最新一条
3. 任一未 PASS → 按 `mes.flash.gate.mode` 处理：
   - **FORCE**（默认）：抛异常拦截完工
   - **WARN**：log 告警放行
   - 其他：关闭门禁

### 6.2 配置

- `mes.flash.gate.mode`（sys_config / application-dev.yml）：FORCE/WARN/关闭
- 无 SN 的批量工单：门禁放行

> 门禁覆盖 completeStep + completeDispatch 两条完工路径（P1 补全）。

---

## 七、追溯与召回

- **正向**：SN 详情 → 刷写记录（flash_type_code + content_ref + content_payload，如 {"mac":"00:1A:..."})
- **反向召回**：按 mes_flash_record.content_ref_id 反查 SN 清单（固件类=固件ID，MAC类=MAC资源项ID），联 pro_sn.sn_status（COMPLETED在库/IN_PRODUCTION在制/SHIPPED已发运/SCRAPPED报废）
- 标准值快照：刷写记录冗余 content_version/content_checksum，历史追溯不受固件/配置修改影响

---

## 八、冒烟测试

### 8.1 测试数据

`doc/mes/scripts/flash-seed-data.sql`（ry-mes 库，幂等）：
- 固件 GW-1000主控 v1.0.0 RELEASED（material_id=1001）
- 配置 GW-1000出厂配置 v1.0 RELEASED
- MAC 池 10 个（1 已分配）+ SN 池 10 个
- 工序1 绑固件+MAC（step_flash_config 多类型）
- 刷写记录：SN202606200001 固件PASS + MAC PASS

执行：`mysql -u root -proot -D ry-mes --default-character-set=utf8mb4 < doc/mes/scripts/flash-seed-data.sql`

### 8.2 冒烟脚本

`doc/mes/scripts/flash-smoke-test.js`（node, 加密登录+redis验证码）：
```
A. 登录(加密+验证码) → B. 固件list → C. MAC建池/导入/分配
→ D. 固件类物料防错被拒 → E. MAC类刷写PASS(Provider路由) → F. 多类型上报
```
执行：`node doc/mes/scripts/flash-smoke-test.js`（10/10 通过）

### 8.3 工位编排验证

```bash
curl "http://localhost:8080/mes/flash/report/station/pending?snCode=SN202606200001" -H "X-API-KEY: nexus-mes-api-2026"
# 返回固件+MAC 2条, latestResult=PASS
```

---

## 九、配置项

| 键 | 位置 | 默认 | 说明 |
|----|------|------|------|
| `mes.flash.gate.mode` | sys_config / application-dev.yml | FORCE | 门禁: FORCE拦截/WARN放行/关闭 |
| `mes.api-key` | application-dev.yml | nexus-mes-api-2026 | 产线上报/工位接口 X-API-KEY |
| `nexus.mes.flash.flash-keywords` | FwProperties | FLASH,BURN | 工序打标刷写关键字 |
| `nexus.mes.flash.test-keywords` | FwProperties | FCT,TEST,FUNC | 工序打标测试关键字 |
| `security.excludes` | application.yml | /mes/flash/report/** | 上报接口放行(免登录) |
| `api-decrypt.enabled` | application.yml | true | API加密(前后端一致) |

---

## 十、附录：API 清单

| 模块 | 路径 | 说明 |
|------|------|------|
| 固件档案 | `/mes/flash/firmware/**` | CRUD/上传/审批/归档/设默认 |
| 配置档案 | `/mes/flash/content/**` | CRUD/发布 |
| 资源池 | `/mes/flash/pool/**` | CRUD/导入/分配 |
| 刷写记录 | `/mes/flash/flash/**` | list/confirm/补录/纠错/SN搜索 |
| 测试项/记录 | `/mes/flash/test-item/**` `/mes/flash/test-record/**` | 测试项CRUD/记录begin/report |
| 产线上报 | `/mes/flash/report/flash/auto` | 批量刷写上报(X-API-KEY) |
| 产线上报 | `/mes/flash/report/test/auto` | 批量测试上报 |
| 工位编排 | `/mes/flash/report/station/pending` | SN待刷清单(X-API-KEY) |

> 权限串 `mes:flash:*`（菜单 4280-4307）。审批业务名 `mesFirmwareService`（固件发布审批）。
