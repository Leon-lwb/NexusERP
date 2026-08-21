# NexusERP STATION 设备通道接口开发计划（nexus-station 工位终端后端支持）

> **文档版本**: v1.0
> **制定日期**: 2026-08-21
> **状态**: 🔨 执行中 — C1 已交付，P0 前置已完成
> **需求来源**: nexus-station M2 计划表 1（契约权威定义）+ 2026-08-21 后端缺口审查
> **服务对象**: nexus-station 工位智能终端（三形态工位：FLASH 烧录 / TEST 综测 / GENERAL 过站）
> **前置文档**: `nexus-erp/doc/2026-08-19-Nexus客户端生态规划.md`、`nexus-station/doc/plans/2026-08-20-M2-过站综测安灯开发计划.md`

---

## 一、背景

nexus-station 工位终端（Electron）已按契约完成客户端开发（42 测试全绿、NSIS 安装包已产出），但后端仅有 `FlashReportController` 3 个端点（`flash/auto`、`test/auto`、`station/pending`），M2 契约 C1-C10、C12 全部缺失，客户端全部工作在降级模式下。本计划在**新建的 `ruoyi-station` 模块**中补齐这些设备通道接口。

### 1.1 模块定位

```
ruoyi-station（新建，2026-08-21）
└── com/nexus/station/
    ├── controller/StationDeviceController   # 设备通道统一入口(/mes/station/**)
    └── mapper/StationSchWorkerMapper        # 跨库只读查询(@DS 切库)
```

- 与 ruoyi-mes 的关系：物理独立模块，不依赖 ruoyi-erp/ruoyi-mes；跨库表访问通过 `@DS` + 只读 `@Select` Mapper（对齐 ruoyi-mes 的 `ErpWorkstationMapper` 跨库模式）
- URL 保持 `/mes/station/**` 前缀（客户端已按此契约开发，不改）；`security.excludes` 已放行
- 鉴权：X-API-KEY 请求头，配置项 `mes.api-key`（与 FlashReportController 共用），fail-closed（未配置即拒绝）
- 租户：固定 000000（`TenantHelper.dynamic` 包裹，多租户插件自动注入 tenant_id 条件）

### 1.2 已完成（2026-08-21）

| 项 | 内容 |
|----|------|
| P0-1 | `ruoyi-station` 模块创建并注册（根 pom dependencyManagement + modules pom + admin pom），包名 `com.nexus.station` |
| P0-2 | `security.excludes` 追加 `/mes/station/**` |
| P0-3 | **C1 上岗接口交付**：`GET /mes/station/operator?code=`，两步校验（sch_worker 工号档案 → 在岗校验 → 按 user_id 查 sys_user 账号状态），返回 `{userId, userName, employeeId, workshopName}` |

---

## 二、接口契约清单（C1-C12）

全部为设备通道接口：请求头 `X-API-KEY`，租户固定 000000。字段以 nexus-station M2 计划表 1 为权威。

| # | 端点 | 方法 | 请求 | 响应 data | 实现要点 | 状态 |
|---|------|------|------|-----------|----------|------|
| C1 | `/mes/station/operator?code=` | GET | 工号条码 | `{userId, userName, employeeId?, workshopName?}` | sch_worker(@DS("erp")) → sys_user 两步校验 | ✅ |
| C2 | `/mes/station/pass/validate` | POST | `{snCode, stationCode}` | `{pass, snInfo:{woNo,cardNo,materialName,currentProcessName}, checks:[{code:PREV_STEP\|STATION_MATCH\|DUPLICATE\|QUALITY_HOLD, ok, message}]}` | 聚合查 pro_flow_card_step/pro_sn/mes_test_record(@DS("mes"))，四重校验一次返回 | 🔶 |
| C3 | `/mes/station/pass/report` | POST | `{snCode, stationCode, operatorId?, operatorCode?, result:PASS\|FAIL, defectCodes?, clientTs}` | `{}` | completeStep 设备通道版；**幂等键 snCode+stationCode+clientTs，落唯一索引兜底**（离线队列回放去重依赖） | 🔶 |
| C4 | `/mes/station/pass/report-batch` | POST | `{stationCode, operatorCode?, goodQty, defects:[{code,qty}], clientTs}` | `{}` | 批量计数报工，同样幂等 | 🔶 |
| C5 | `/mes/station/sn/bind` | POST | `{parentSnCode, componentSnCodes:[], operatorCode?}` | `{boundQty}` | 包装 ProSnService 绑定逻辑，批量 + 防环校验（A绑B后B不可再绑A） | 🔶 |
| C6 | `/mes/station/sn/unbind` | POST | `{componentSnCode, newComponentSnCode?, reason, operatorCode?}` | `{}` | 解绑留痕，支持换板「解绑+重绑」一步完成 | 🔶 |
| C7 | `/mes/station/andon/call` | POST | `{callType:MATERIAL\|REPAIR\|PERSON\|QUALITY, callReason?, stationCode, operatorCode?, woNo?, cardNo?}` | `{andonNo}` | **包装既有 `/mes/pro/andon/call`**（ProAndonController/MesAndonServiceImpl 已完整） | 🔶 |
| C8 | `/mes/station/andon/latest?stationCode=` | GET | — | `{andonNo, callType, andonStatus:CALLED\|RESPONDED\|RESOLVED, callTime, responderName}` | 查 ProAndon 该工位最新一条(@DS("mes")) | 🔶 |
| C9 | `/mes/station/defect-codes` | GET | — | `[{code, label}]` | sys_dict 或常量表 | 🔶 |
| C10 | `/mes/flash/report/station/heartbeat` | POST | `{deviceCode, stationCode, appVersion, stationTime}` | `{}` | **需新建 `mes_station_device` 台账表**，upsert 更新 last_heartbeat | 🔶 |
| C11 | `/mes/flash/report/test/auto` | POST | 综测上报数组 | 批量结果 | 已存在（FlashReportController） | ✅ |
| C12 | 刷写/测试上报 BO 增加字段 | — | `operatorId/operatorCode` | — | FlashReportBo/TestReportBo 加字段，缺失容忍（旧版客户端不传） | 🔶 |

### 2.1 M1 遗留契约

| # | 端点 | 说明 | 状态 |
|---|------|------|------|
| M1-a | `POST /mes/flash/report/station/prepare` | 内容预取/池值预分配，幂等；客户端 404 时降级为直接刷写 | 🔶 |
| M1-b | `GET /mes/flash/report/station/firmware/{id}/download` | 固件文件下载流（X-API-KEY），客户端做 MD5 校验；mes_firmware 表已有，**需确认文件存储后端（OSS/本地）** | 🔶 |

### 2.2 新建表（ry-mes 库）

```sql
-- C10 设备心跳台账（可连带承载工位绑定 stationCode/processCode 关联，见设计文档 4.1）
CREATE TABLE mes_station_device (
  device_id       bigint       NOT NULL COMMENT '设备ID',
  tenant_id       varchar(20)  NULL DEFAULT '000000',
  device_code     varchar(64)  NOT NULL COMMENT '设备编码(工控机)',
  station_code    varchar(64)  NULL COMMENT '绑定工位编码',
  process_code    varchar(64)  NULL COMMENT '绑定工序编码',
  app_version     varchar(32)  NULL COMMENT '终端版本',
  last_heartbeat  datetime     NULL COMMENT '最后心跳时间',
  create_time     datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (device_id),
  UNIQUE INDEX uk_device_code (device_code)
) COMMENT '工位终端设备台账';
```

---

## 三、开发排期（按解锁 station 冒烟项的杠杆排序）

| 批次 | 内容 | 预估 | 解锁的 station 冒烟项 |
|------|------|------|----------------------|
| ✅ 批次0 | P0 前置（模块/excludes）+ C1 上岗 | 0.5d | 冒烟#2 扫工号上岗（降级标签消失） |
| 批次1 | C7 + C8 安灯（纯包装既有 ProAndon 逻辑） | 1d | 冒烟#9 安灯四键呼叫 |
| 批次2 | C2 + C3 过站校验/单件报工（四重校验聚合查询 + 幂等唯一索引） | 3-5d | 冒烟#3 过站红绿屏、#4 不良上报、#6 离线回放幂等验证 |
| 批次3 | C4 + C12 批量报工 + BO 字段 | 1-2d | 冒烟#5 批量计数 |
| 批次4 | C5 + C6 关键件绑定/解绑（防环校验） | 2d | 冒烟#8 关键件绑定 |
| 批次5 | C10 心跳 + mes_station_device 建表 | 1d | 冒烟#10 心跳落库 |
| 批次6 | C9 缺陷代码 + M1-a/M1-b prepare/固件下载（先确认存储方案） | 1-2d | 刷写闭环完整形态 |
| **合计** | | **10-13d** | 10 项冒烟全部脱离降级模式 |

## 四、技术约定（本模块开发必读）

1. **多库 @DS 映射**：sch_worker→`@DS("erp")`；pro_*/mes_*→`@DS("mes")`；sys_user→master 默认。跨库 Mapper 一律放 `com.nexus.station.mapper`，类名加 `Station` 前缀（避免与业务模块 Mapper bean 名冲突——C1 开发时已踩过 `SchWorkerMapper` 同名冲突坑）。
2. **幂等设计**：C3/C4 的幂等键 `snCode+stationCode+clientTs` 必须落数据库唯一索引（如 `uk_sn_station_cts`），不能只靠应用层判重——离线队列可能数小时后重放。
3. **鉴权复用**：`checkApiKey` 逻辑与 FlashReportController 一致（fail-closed），后续端点在 StationDeviceController 内复用私有方法即可。
4. **租户上下文**：所有跨租户插件表的查询/写入必须 `TenantHelper.dynamic("000000", ...)` 包裹。
5. **客户端兼容**：新增响应字段向后兼容（客户端忽略未知字段）；请求新字段一律可空容忍（station 旧版本不传）。

## 五、验收标准

- 每个契约端点交付后：编译通过 + nexus-station 冒烟清单对应项真机验证通过
- C3 幂等：同一 `snCode+stationCode+clientTs` 重复上报，库中仅一条记录（冒烟#6 的后端验收项）
- 全部交付后：station 10 项冒烟清单脱离降级模式，M2 后端依赖闭环
