# NexusERP — IoT 阶段一 · 第一周（Week 33）开发计划

> 关联文档：`doc/iot/NexusERP_IoT文档.md`（第十八节 18.1 Week 33）
> 计划制定：2026-06-17
> 工作量基准：3 人天（与文档 Week 33 一致）
> 范围边界：**仅后端**。前端（产品/设备页面、实时仪表盘）属 Week 35-36，不在本周。

---

## 0. 本周目标（一句话）

让 `ruoyi-iot` 模块可编译、可启动：`ry-iot` 库 + 3 张核心表就绪，`@DS("iot")` 数据源可切换，Moquette 内嵌 Broker 监听 1883 并完成设备鉴权，产品/设备 CRUD 接口可用。

**不在本周范围**（属 Week 34+）：MQTT 消息消费→Redis Streams→MySQL 数据管道、SSE 实时推送、SnailJob 定时任务、告警、前端页面。

---

## 1. 前置对齐决策（制定计划时已审，需你确认）

在动手前，必须先修正 IoT 文档 DDL 与项目既有规范的偏差。**这是本周最关键的隐性风险**——若直接照搬文档 DDL，CRUD 一跑就会因列不匹配报错。

| # | IoT 文档 DDL | 项目既有规范（ERP/MES/TenantEntity/BaseEntity） | 本计划处理方式 |
|---|---|---|---|
| A | `tenant_id BIGINT NOT NULL DEFAULT 0` | `tenant_id VARCHAR(20) NOT NULL DEFAULT '000000'`，实体继承 `TenantEntity`（`String tenantId`） | **改为 VARCHAR(20) DEFAULT '000000'**，对齐 MES/ERP |
| B | 无 `del_flag` | 全局 `enableLogicDelete: true`，业务表均带 `del_flag` + `@TableLogic` | `iot_product`/`iot_device` **补 `del_flag`**（业务表，宜软删）；`iot_device_data` 不加（高频时序数据，物理删除） |
| C | `iot_device_data` 仅有 `create_time` | `BaseEntity` 自动填充 `create_dept/create_by/create_time/update_by/update_time` | `iot_device_data` 实体 **不继承** `BaseEntity`/`TenantEntity`，独立声明 `tenantId`+`createTime` 字段，避免 INSERT 填充不存在的列 |
| D | `iot_product`/`iot_device` 有 `create_dept` 但无 `del_flag`、无 `update_by` 等 | `TenantEntity→BaseEntity` 需 `create_dept/create_by/create_time/update_by/update_time` 全套 | DDL **补齐全套审计列 + del_flag**，实体继承 `TenantEntity` |

> 结论：本周会产出一份**修正版 DDL 脚本** `doc/iot/sql/archive/iot-week33-01-init.sql`，与文档 DDL 有上述差异，并在脚本头注释中标注“对齐项目规范”。文档原文不改（留作设计意图记录），以脚本为准。

---

## 2. 任务分解（按执行顺序）

### T1 · 创建 ruoyi-iot Maven 模块骨架（0.3d）
- 新建 `ruoyi-modules/ruoyi-iot/pom.xml`，parent = `ruoyi-modules`，参照 `ruoyi-mes/pom.xml`。
- 依赖（最小集）：
  - `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-tenant`
  - `ruoyi-common-web`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-security`
  - `ruoyi-common-idempotent`、`ruoyi-common-sensitive`
  - `ruoyi-common-redis`（Week 34 管道用，本周先引入避免后续改 pom）
  - `ruoyi-common-sse`（同上，Week 34/35 用）
  - `ruoyi-common-job`（Week 36 定时任务用，先引入）
  - `ruoyi-system`
  - `org.eclipse.moquette:moquette-broker:0.17`（显式版本，见 T6）
  - `spring-boot-starter-test`（test）
- 不引入 `ruoyi-flowable`（IoT 无工作流）、不引入 `ruoyi-common-mq`（用 Redis Streams，走 ruoyi-common-redis）。
- 包结构：`com/nexus/iot/{device,mqtt,pipeline,realtime,task,common}`（pipeline/realtime/task 本周建空包占位，下周填充）。
- **验收**：模块独立 `mvn compile` 通过。

### T2 · 注册模块到聚合 pom（0.1d）
- `ruoyi-modules/pom.xml` 增加 `<module>ruoyi-iot</module>`。
- 根 `pom.xml` 的 `<modules>` 已包含 `ruoyi-modules`，无需改根 pom（与 MES 一致）。
- **验收**：根目录 `mvn -pl ruoyi-modules/ruoyi-iot -am compile` 通过。

### T3 · 配置 iot 数据源 + 建库脚本（0.4d）
- `ruoyi-admin/src/main/resources/application-dev.yml`：在 `spring.datasource.dynamic.datasource` 下增加 `iot` 数据源，url=`jdbc:mysql://localhost:3306/ry-iot?...`（参数照 mes 段复制），账号密码同 master（dev 环境复用 root）。
- 同步在 `application-prod.yml` 增加 iot 段（占位，凭证留 TODO）。
- 产出建库脚本 `doc/iot/sql/archive/iot-week33-01-init.sql` 头部：`CREATE DATABASE IF NOT EXISTS \`ry-iot\` ...` + `USE \`ry-iot\`;`。
- **验收**：应用启动无数据源报错；`strict: true` 模式下 `@DS("iot")` 可解析（T10 联调验证）。

### T4 · 修正版 3 张核心表 DDL（0.4d）
写入 `doc/iot/sql/archive/iot-week33-01-init.sql`（脚本头注释说明与文档 DDL 的差异及原因）：
- `iot_product`：`tenant_id VARCHAR(20) DEFAULT '000000'`；补 `del_flag CHAR(1) DEFAULT '0'`；保留 `create_dept/create_by/create_time/update_by/update_time`；`uk_code(product_code, tenant_id)`。
- `iot_device`：同上规范；`mes_equipment_id BIGINT NULL`（无外键，仅字段）；`uk_code(device_code, tenant_id)`；`idx_product`、`idx_status`。
- `iot_device_data`：`tenant_id VARCHAR(20) DEFAULT '000000'`；**不补** del_flag/审计列（仅 `create_time`）；`idx_device_time`、`idx_property`。
- **验收**：在本地 ry-iot 库执行成功，`DESC` 三表列结构符合预期。

### T5 · 实体 / BO / VO / Mapper（0.5d）
- `IotProduct`、`IotDevice` 继承 `TenantEntity`（含 `@TableLogic` del_flag 字段），`@TableName` 对应表。
- `IotDeviceData` **不继承**基类，独立 `@TableName("iot_device_data")`，显式声明 `tenantId`、`createTime` 等字段。
- `bo/IotProductBo`、`bo/IotDeviceBo`（查询/新增/编辑入参，含校验注解）。
- `vo/IotProductVo`、`vo/IotDeviceVo`（响应，`@AutoMapper` 走 MapStruct Plus，参照 BdClientVo）。
- `mapper/IotProductMapper`、`mapper/IotDeviceMapper`（继承 `BaseMapperPlus`）。
- **验收**：编译通过；mapper 被 `com.nexus.**.mapper` 扫描识别。

### T6 · 引入 Moquette 依赖并验证无冲突（0.3d）
- `ruoyi-iot/pom.xml` 加 `moquette-broker:0.17`。
- Moquette 传递依赖 Netty/Netty-codec。**风险点**：ruoyi 体系可能已有 Netty（如 grpc-bom、redisson）。运行 `mvn dependency:tree -pl ruoyi-modules/ruoyi-iot` 检查 Netty 版本收敛，必要时在 iot pom 用 `<exclusions>` 或在根 bom 统一 Netty 版本。
- **验收**：`dependency:tree` 无 Netty 版本冲突；`mvn compile` 通过。

### T7 · Moquette Broker 配置类（0.5d）
- `mqtt/MqttBrokerConfig.java`（`@Configuration`）：
  - 读 `nexus.iot.mqtt.*` 属性（`enabled`、`port=1883`、`host`），`enabled=false` 时不启动（便于测试/端口占用时关闭）。
  - `@Bean` 启动内嵌 `Server`，`IConfig` 监听 1883。
  - 注册 `MqttMessageListener`（本周仅做“收到消息→打日志”，**不**写 Redis Stream，那是 Week 34）。
  - 注册设备连接/断开回调 → 更新 Redis `iot:status:{deviceId}` + DB `connection_status`（用 `IotDeviceService`）。
- 新增 `nexus.iot.mqtt` 配置段到 `application.yml`（默认 enabled、port）。
- **验收**：启动日志见 Moquette 监听 1883；`mosquitto_pub` 连接可触发 listener 日志。

### T8 · 设备认证（deviceCode + deviceSecret）（0.4d）
- `mqtt/IotDeviceAuthenticator`：实现 Moquette 的认证接口，按 ClientID=deviceCode 查 `iot_device`，校验 `device_secret`。
- 设备新增/重置时生成 `device_secret`（UUID 或 SecureRandom），存库。
- 认证失败拒绝连接；成功 → 标记 ONLINE。
- **验收**：正确凭证连接成功，错误凭证被拒（用 mosquitto_pub 验证）。

### T9 · 产品 / 设备 CRUD Service + Controller（0.6d）
- `service/IIotProductService` + `impl/IotProductServiceImpl`：分页查询、详情、新增、编辑、删除（逻辑删）。
- `service/IIotDeviceService` + `impl/IotDeviceServiceImpl`：同上 + 新增时自动生成 `device_secret` + `product_name` 回填。
- `controller/IotProductController`：`/iot/product`，权限标识 `iot:product:*`，含 list/export/info/add/edit/remove。
- `controller/IotDeviceController`：`/iot/device`，权限标识 `iot:device:*`。
- 完全参照 `BdClientController` / `IBdClientService` 的写法（`@SaCheckPermission`、`@Log`、`TableDataInfo`、`R`）。
- **验收**：Swagger/Postman 走通产品、设备的增删改查；`@DS("iot")` 切库正确（数据落在 ry-iot）。

### T10 · 菜单权限 SQL（0.2d）
- 产出 `doc/iot/sql/archive/iot-week33-02-menu.sql`（目标库 `ry-vue`，写 `sys_menu`）。
- ID 段：**6000~6999**（MES 占用 4000 段，ERP 占 2000 段，实际执行改为 6000 段空闲）。
- 一级目录 `IoT平台`(6000) → 二级 `设备管理`(6001) → 菜单 `产品管理`(6010, `iot/product/index`)、`设备管理`(6020, `iot/device/index`)，及对应按钮权限 `iot:product:list/add/edit/remove/export`、`iot:device:*`。
- 前端路由/页面本周不做，仅埋菜单，供 Week 35 前端对接。
- **验收**：SQL 在 ry-vue 执行成功，菜单可见（需分配角色权限后）。

### T11 · 集成自测与文档收尾（0.3d）
- 联调顺序：建库→建表→启动应用（无 ERROR）→ Moquette 监听 1883 → mosquitto 连接鉴权 → 产品/设备 CRUD（数据落 ry-iot）→ 关闭 enabled 验证可降级启动。
- 更新 `NexusERP_IoT文档.md` 第十四节检查清单状态（`ruoyi-iot 模块`/`iot 数据源`/`@DS`/`ry-iot 库`/`3 张 DDL`/`Moquette 依赖` → ✅；Redis Streams 消费者组保持 ❌，属 Week 34）。
- 在 `doc/iot/sql/archive/iot-week33-01-init.sql` 头部记录“本周已完成项 / 待 Week 34 项”。
- **验收**：对照文档第十九节 19.1 中本周可验收的项（模块启动、数据源切换、Broker、设备认证）全部通过。

---

## 3. 交付物清单

| 类型 | 路径 |
|---|---|
| 模块 pom | `ruoyi-modules/ruoyi-iot/pom.xml` |
| 聚合 pom | `ruoyi-modules/pom.xml`（加 module） |
| 数据源配置 | `application-dev.yml`、`application-prod.yml`（iot 段） |
| MQTT 配置 | `application.yml`（`nexus.iot.mqtt` 段） |
| 建库+建表脚本 | `doc/iot/sql/archive/iot-week33-01-init.sql` |
| 菜单脚本 | `doc/iot/sql/archive/iot-week33-02-menu.sql` |
| 后端代码 | `ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/{device,mqtt,...}` |
| 文档更新 | `doc/iot/NexusERP_IoT文档.md`（检查清单状态） |

---

## 4. 自审查记录（计划制定者自查）

**已识别并处理的偏差：**
1. ✅ DDL `tenant_id` 类型/默认值与项目规范不符 → 改 VARCHAR(20) DEFAULT '000000'（第 1 节 A）。
2. ✅ 业务表缺 `del_flag` → product/device 补 del_flag（第 1 节 B）。
3. ✅ `iot_device_data` 若继承 `BaseEntity` 会触发不存在的审计列填充 → 改为独立实体（第 1 节 C/D）。
4. ✅ Moquette 传递 Netty，可能与现有 Netty 冲突 → T6 显式 `dependency:tree` 校验。
5. ✅ Moquette 1883 端口随应用启动，测试/端口占用场景需可关 → `nexus.iot.mqtt.enabled` 开关。
6. ✅ 菜单 ID 段需避开 MES(4000)/ERP(2000) → 计划选 5000 段，实际执行时改为 6000 段（已在 SQL 文件中修正）。
7. ✅ Week 33 文档任务含“Redis Streams 消费者组创建”，但消费者组属 Week 34 数据管道 → 本周不创建，避免无消费者的空 group；检查清单该项保持 ❌。
8. ✅ 文档 Week 33 提到 `MqttMessageListener`，但完整消息消费（XADD）在 Week 34 → 本周 listener 仅落日志，明确边界，避免提前实现下周逻辑。

**遗留风险/需你拍板：**
- **R1**：`ry-iot` 数据库的**实际创建**需在你的本地 MySQL 执行（我可产出脚本，但不会连你的库执行）。是否同意由你执行建库语句，或你授权我用本地 mysql 客户端执行？
- **R2**：Moquette 0.17 与项目 Netty 若冲突无法靠 exclusion 解决，可能需升级/降级 Moquette 或统一 Netty。T6 会先探明，若冲突严重我会停下来报告，不擅自改全局 Netty 版本。
- **R3**：`device_secret` 生成策略——UUID（简单）vs SecureRandom hex（更安全）。计划默认 UUID，可改。

---

## 5. 验收标准（本周完成定义）

1. `mvn -pl ruoyi-modules/ruoyi-iot -am compile` 通过，无依赖冲突。
2. 应用启动：日志无 ERROR，Moquette 监听 1883，iot 数据源初始化成功。
3. `@DS("iot")` 生效：产品/设备 CRUD 数据真实落入 `ry-iot` 库。
4. MQTT 鉴权：正确凭证可连、错误凭证被拒；连接/断开 → 设备 ONLINE/OFFLINE 状态变更（Redis + DB）。
5. 菜单脚本可在 `ry-vue` 执行，IoT 菜单树生成。
6. 文档检查清单本周项状态更新为 ✅。
