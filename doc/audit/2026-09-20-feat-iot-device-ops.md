# IoT 设备管理与消息能力补齐（分组/标签 + 生命周期 + 事件上报 + 下行指令 + 数据转发）修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-iot.md` 的 **IOT-PIPE-02**（`event/post` / `property/set` 被丢弃）、
> **IOT-DEV-01**（设备 `status` 是死状态）与 §4「功能缺失清单」第 **2（分组/标签）、6（数据转发）、7（设备生命周期）、8（下行通道）、9（设备事件上报）** 项
> 分支: `feature/erp-mes-iot-audit-fix` | 批次: **B10-b** | 工作目录: `D:\workspace\nexus-erp`
> 参照同模块已完成的告警能力（`2026-09-20-feat-iot-alert.md`）分层/命名/测试写法，**未重复实现告警功能**，
> 仅在事件链路上复用其 `IIotAlertRecordService.trigger` 做轻量联动。
> 未使用任何 git 命令；未编辑 `ry-vue.sql` / `ry-erp.sql` / `ry-mes.sql` / `application*.yml` /
> `doc/system/数据库增量变更记录.md` / `plus-ui/**` / `ruoyi-modules/ruoyi-{mes,erp,station}/**`。

---

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| §4#2 分组 | 新增 `iot_device_group`（树形，`parent_id` + `ancestors` 物化路径，与 `sys_dept` 同构）+ `iot_device_group_rel`（**N:N 关系表，而非 `iot_device.group_id` 单列**，理由见 §1.1）。分组 CRUD（平铺列表 + `deviceCount`）、父级变更时**级联重写后代 `ancestors`**、删除前置校验（有子分组/已挂设备则拒绝）、`PUT /iot/deviceGroup/assign` 批量挂载/移出（幂等）、`GET /iot/deviceGroup/{id}/deviceIds?includeSubGroup=` 子树设备ID | `device/group/**`（12 文件）、`ry-iot.sql:245-280`、`update/2026-09-20-iot-device-ops.sql §1-2` | 是（2 新表） | `IotDeviceGroupTagServiceTest` 8 例（ancestors 计算/后代重写/删除校验/幂等挂载/子树收集） |
| §4#2 标签 | 新增 `iot_device_tag` + `iot_device_tag_rel`（多值维度用关系表而非 JSON 列，可索引/可集合筛选）；标签 CRUD、`PUT /iot/deviceTag/bind` 批量绑定/解绑（幂等）、删除标签级联清理关系、`GET /iot/deviceTag/device/{deviceId}` 查设备标签 | `device/group/**`、`ry-iot.sql:282-322` | 是（2 新表） | `IotDeviceGroupTagServiceTest` 4 例（幂等绑定/级联删除/标签缺失拒绝） |
| §4#2 筛选 | 设备列表支持 **按分组（含子树）+ 按标签（多标签交集）** 筛选：`IotDeviceFilterResolver` 把条件解析为设备ID集合后**下推 `device_id IN (...)`**；命中空集翻译为 `1 = 0`（不忽略条件返回全量） | `device/group/service/IotDeviceFilterResolver.java`、`IotDeviceServiceImpl.buildQueryWrapper`、`IotDeviceBo`（+`groupId/includeSubGroup/tagIds`） | 否 | `IotDeviceGroupTagServiceTest#resolveDeviceIds_*`、`IotDeviceLifecycleTest#selectPageList_emptyGroupFilter_producesNoMatchCondition` |
| **IOT-DEV-01** / §4#7 | 设备生命周期端点 `PUT /iot/device/{deviceId}/lifecycle`（ACTIVE/INACTIVE/**DECOMMISSIONED**），**条件更新 CAS**（0 行抛"已被其他操作变更"）；状态机：ACTIVE⇄INACTIVE、→DECOMMISSIONED，**注销为终态不可复活**；禁用/注销同时置 `connection_status=OFFLINE`；状态语义收敛到 `IotDeviceStatus`（`canConnect` 与 MQTT 认证共用口径） | `device/domain/IotDeviceStatus.java`、`IotDeviceStatusBo.java`、`IIotDeviceService.changeStatus`、`IotDeviceServiceImpl.changeStatus`、`IotDeviceController:changeLifecycle` | 否（复用既有 `status` 列） | `IotDeviceLifecycleTest` 6 例（CAS 成功/冲突/终态拒绝/同状态拒绝/非法值拒绝） |
| **IOT-DEV-01** | **禁用后 MQTT 认证必须拒绝**：`IotMqttAuthenticator` 既有的 `ACTIVE` 校验现在真正可达（此前无任何写入 INACTIVE 的入口），并用测试锁定"禁用→认证拒绝 / 激活+正确密钥→通过" | `mqtt/IotMqttAuthenticator.java`（未改逻辑）、测试锁定 | 否 | `IotDeviceLifecycleTest#mqttAuth_inactiveDevice_rejected`、`#mqttAuth_activeDeviceWithCorrectSecret_passed` |
| **IOT-DEV-01** | **服务端字段白名单**：`insertByBo` 固定 `status=ACTIVE`+`connectionStatus=OFFLINE`；`updateByBo` 强制清空 `status`/`connectionStatus`/`deviceSecret`（前端直改状态/连接状态/密钥被堵死，状态只能走 CAS 端点，密钥只能走重置端点） | `IotDeviceServiceImpl.insertByBo/updateByBo` | 否 | `IotDeviceLifecycleTest#updateByBo_serverSideWhitelist_clearsAuthoritativeFields`、`#insertByBo_forcesActiveAndOffline` |
| §4#7 运维字段 | 新增 `GET /iot/device/{deviceId}/ops` 聚合：在线状态、最后在线时间、**最后上报时间**、固件版本、IP/位置、MES 关联、分组与标签。**在线状态/最后在线时间/固件版本复用既有列（未重复建列）**；"最后上报时间"由消费端节流写 Redis（`iot:lastreport:{deviceId}`，同设备 30s 最多一次）避免逐条 UPDATE 设备表 | `device/domain/vo/IotDeviceOpsVo.java`、`pipeline/IotDeviceReportTimeCache.java`、`IIotDeviceService.selectOps`、`IotStreamConsumer.afterPersist` | 否 | `IotDeviceLifecycleTest#selectOps_aggregatesOpsFields` |
| **IOT-PIPE-02** 上行 | `IotMqttTopicParser` 增加 `set_reply` 动作（+`isReply()`）；`IotMessagePipelineService` 由"仅 `property/post`，其余 `log.debug` 丢弃"改为**分流**：`property/post`→数据流（原逻辑不变）、`event/post`→事件流、`property/set_reply`→事件流（`entry_type=COMMAND_REPLY`），并新增 `unsupportedTopicCount`（**不再静默丢弃**，INFO 级 + 计数） | `pipeline/payload/IotMqttTopicParser.java`、`pipeline/IotMessagePipelineService.java` | 否 | 编译期 + `IotMqttTopicParserTest`（既有，未回归破坏）；路由分支由 `IotDeviceEventServiceTest` 与消费端用例覆盖（见 §2 说明） |
| **IOT-PIPE-02** 事件 | 新增 `iot_device_event`（租户/设备/事件码/名称/级别/事件时间/载荷 JSON/处理状态/联动告警ID/`source_msg_id` 幂等键）+ 事件流消费者 `IotEventStreamConsumer`（独立消费者组，结构对齐 `IotStreamConsumer`：pending 回收→租户交叉校验→幂等→毒消息重试上限）；事件查询/详情/**处理状态 CAS**/导出端点（`/iot/event/**`）；**告警类事件**（级别 ERROR/CRITICAL/FATAL/ALARM/FAULT 或事件码含 ALARM/FAULT/ERROR/FAIL/OVER）联动 `iot_alert_record`（来源 `EVENT`），非告警事件仅留档 | `event/**`（7 文件）、`alert/domain/IotAlertConstants.java`（+`SOURCE_EVENT`）、`pipeline/IotPipelineProperties.java`、`ry-iot.sql:324-360` | 是（1 新表） | `IotDeviceEventServiceTest` 8 例（落库字段/PENDING/幂等/告警联动/非告警不联动/缺编码跳过/处理 CAS） |
| **IOT-PIPE-02** 下行 | 新增 `iot_device_command` + `IotMqttPublisher`（封装 Moquette `Server.internalPublish`，`ObjectProvider` 取 Broker，未启用时返回 false **不伪造成功**）；`POST /iot/command/propertySet`（`property/set`）、`POST /iot/command/serviceInvoke`（`event/set`）、`PUT /iot/command/{id}/resend`、列表/详情查询。**先留痕后下发**（先 insert 拿雪花指令号→组包→发布→回写 SENT/FAILED），载荷带 `commandId/msgId/type/service/params/ts`；禁用/注销设备拒绝下发；**设备回执**（`property/set_reply`）经事件流 → `handleReply` 以 `ack_msg_id`(Stream id) 唯一键 + CAS 幂等更新 SUCCESS/FAILED、回执时间与载荷 | `command/**`（6 文件）、`mqtt/IotMqttPublisher.java`、`event/IotEventStreamConsumer.java`、`ry-iot.sql:362-395` | 是（1 新表） | `IotDeviceCommandServiceTest` 11 例（留痕+Topic/服务调用/缺服务名/禁用拒绝/发布失败落 FAILED/回执更新/回执幂等/无 commandId 回落/未匹配/重发拒绝/重发成功） |
| §4#6 数据转发 | 新增 `iot_forward_rule`（目标 URL/方法/请求头/超时/最大重试/产品·设备·属性范围）+ `iot_forward_log`（投递留痕：状态/重试次数/HTTP 码/失败原因/下次重试时间）；`IotForwardDispatcher` 在 `afterPersist` 后匹配规则 → 落留痕 → 线程池异步投递 → 失败按退避（30s×次数，上限 5min）写 `next_retry_time`，`@Scheduled(scheduler="iotTaskScheduler")` 每分钟 CAS 领取重试（多实例安全）；规则 CRUD + 留痕查询端点（`/iot/forwardRule/**`）。**SSRF 防护**：`IotForwardUrlValidator` 对齐 `HttpNodeProvider.sanitizeUrl`（仅 http/https、解析全部 A/AAAA、拒绝回环/私有/链路本地/组播/any），并额外封堵 CGNAT `100.64/10`、`0.0.0.0/8`、IPv6 ULA `fc00::/7`、拒绝带 userinfo 的 URL；**保存时 + 投递前两次校验**（防 DNS rebinding） | `forward/**`（10 文件）、`pipeline/IotStreamConsumer.afterPersist`、`ry-iot.sql:397-441` | 是（2 新表） | `IotForwardDispatcherTest` 14 例（SSRF 黑/白名单、规则与属性匹配、内网 URL 不发起请求、成功/失败/超限留痕、投递前二次校验、重试领取与 CAS 冲突、产品范围、缺租户跳过） |
| 权限码 | `sys_menu` 权限码种子 **25 条**：设备状态变更 6075、分组 6050-6054/6090、标签 6055-6059/6091、事件 6060-6063、指令 6070-6073、转发 6080-6085（ID 已逐个校验空闲，见 §3） | `update/2026-09-20-iot-device-ops.sql §9` | 是（ry-vue DML） | 无（种子脚本，见 §3 校验 SQL） |

### 1.1 两个设计取舍（按要求说明理由）

1. **设备-分组关系用 `iot_device_group_rel`（N:N）而非 `iot_device.group_id`**：
   - 现场一台设备常需同时归属「车间 / 产线 / 工艺段」多个视角，单列无法表达；
   - `script/sql/ry-iot.sql` 中 `iot_device` 的 24 行存量数据是**无列名位序 INSERT**（`INSERT INTO iot_device VALUES (…22 个值…)`），
     给该表加列会使这些 INSERT 全部因"列数不匹配"而失败（需同步改写 24 行），风险与收益不成比例；
   - 关系表天然支持"批量挂载/移出"与唯一键幂等（`uk_device_group(tenant_id, device_id, group_id)`）。
2. **标签用 `iot_device_tag` + `iot_device_tag_rel` 而非设备表 JSON 列**：标签需要按集合筛选（多标签交集）、统计与索引，
   JSON 列只能全表扫描/内存过滤，无法复用租户隔离与索引。
3. **"最后上报时间"不落列而落 Redis**：设备上报可达 Hz 级，逐条 UPDATE 设备表会造成写放大与行锁竞争；
   故消费端节流写 `iot:lastreport:{deviceId}`（30s/设备），运维详情端点聚合返回；该键 TTL 7 天可自然过期。

---

## 2. 未修复 / 部分修复项（含理由与建议）

| 项 | 状态 | 理由与建议 |
|---|---|---|
| **前端页面**（分组树、标签、事件列表、指令下发、转发规则） | **未做**（本批次明确排除） | 见 §7「前端需求」；后端端点与 25 个权限码种子已就绪，前端可直接对接。 |
| **OTA 固件升级** | **未做**（单独批次） | 见 §7.4「OTA 可复用资产与建议」。 |
| **设备影子 / 期望值（desired/reported + version 乐观锁）** | **未做**（审计 §4#3，不在本批次任务范围） | 本批次已铺好其**前置底座**：`property/set` 下行通道 + 指令留痕 + 设备回执落库（`iot_device_command`）。建议后续批次新增 `iot_device_shadow`（device_id + desired/reported JSON + version），以本批次的 `property/set` 下发与 `set_reply` 回执作为写入/确认通道。 |
| **指令超时自动判定（TIMEOUT）** | **部分** | 状态枚举含 `TIMEOUT` 语义位但未实现自动置位：需要"设备在线状态 + 定时扫描未回执指令"配合。当前靠 `sent_time` + 查询页人工判断/重发。建议后续在 `IotDeviceHeartbeatTask` 或独立任务中扫描 `status='SENT' AND sent_time < now - timeout` 置 TIMEOUT。 |
| **MQTT 下行发布的"在线投递确认"** | **部分** | 采用 Moquette `internalPublish`：返回 `RoutingResults` 但未据此判定"设备是否在线订阅"。当前语义为"已提交 Broker"（状态 SENT），真实执行结果依赖设备回执（SUCCESS/FAILED）。若需严格在线校验，可在发布前查 `Server.listConnectedClients()` 或 `IotDeviceStatusCache`。 |
| **事件→告警联动的规则化** | **部分（刻意轻量）** | 仅按"事件级别/事件码语义"判定告警类事件并联动告警记录，未做多条件规则引擎（审计 §4#5 的完整规则引擎仍待立项）。 |
| **转发目标类型仅 HTTP** | **部分** | 表含 `target_type` 但仅实现 `HTTP`（BO `@Pattern` 白名单限制）；MQ/RabbitMQ 转发可复用 `ruoyi-common-mq` 的 `QueueConstants`，建议后续批次扩展。 |
| **转发重定向二次校验** | **未做** | Hutool 默认不跟随重定向，因此 3xx 不会被自动跟随（已降低风险）；若目标服务恶意返回 302，客户端会拿到 3xx 并记 FAILED。更强保证需"固定 IP + Host 头"投递，见 `IotForwardUrlValidator` 类注释。 |
| **`IotStreamConsumer` 的转发/上报时间钩子用字段注入** | **刻意为之** | 为**保持既有构造器签名不变**（`IotStreamConsumerTest` 按 7 参构造），新增的 `IotForwardDispatcher`/`IotDeviceReportTimeCache` 用 `@Autowired(required=false)` 字段注入并做 null 守卫。若评审要求统一构造器注入，需同步改 `IotStreamConsumerTest` 的构造调用。 |
| **静态工具收敛为可注入组件** | **刻意为之（非功能变更）** | 4 个服务改用注入的 `Converter`、2 个类改用注入的 `ObjectMapper`，替代静态 `MapstructUtils`/`JsonUtils`：这两个静态类初始化依赖 Spring 容器（`SpringUtils.getBean`），**单测无法打桩**（与告警批次 `RedisUtils` 同类问题，参照其 `IotAlertStateStore` 的收敛做法）。运行时行为与原来完全一致（注入的即容器中同一个 Bean）。 |
| **事件/指令/转发的端点集成测试** | **未做** | 按"单测覆盖核心断言"交付；权限注解、参数绑定、CAS SQL 的真实库行为建议由主代理在最终全量回归时验证。 |
| **`sys_dict` 字典**（事件级别/指令类型/指令状态/转发状态） | **未做** | 属 `ry-vue.sql` 独占文件；当前用 BO `@Pattern` 白名单校验，前端可先硬编码常量（见 §7.5 建议）。 |

---

## 3. 主脚本合并清单

### 3.1 `script/sql/ry-iot.sql`（**本批次独占，已直接修改，无需主代理合并**）

在文件末尾 `SET FOREIGN_KEY_CHECKS = 1;` 之前追加 **8 张新表** DDL（全部为新增表，**未 ALTER 任何存量表**，因此不影响
`iot_device` 的位序 INSERT 存量行）：
`iot_device_group`、`iot_device_group_rel`、`iot_device_tag`、`iot_device_tag_rel`、`iot_device_event`、
`iot_device_command`、`iot_forward_rule`、`iot_forward_log`。

### 3.2 `script/sql/ry-vue.sql`（**独占文件，本代理未编辑**，请主代理串行合并）

- **锚点**（`ry-vue.sql:92759`，紧邻的已存在整行）：
  ```sql
  INSERT INTO `sys_menu` VALUES (6045, '规则启停', 6040, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:changeStatus', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '启用/停用规则(1/0)');
  ```
- **待插入的行**：`script/sql/update/2026-09-20-iot-device-ops.sql` **第 9 节**（25 条 `INSERT IGNORE`，可重复执行），
  内容为 menu_id `6050-6059`、`6060-6063`、`6070-6073`、`6075`、`6080-6085`、`6090-6092`。
- **menu_id 占用校验记录**（从 `script/sql/ry-vue.sql` 提取的 6000-6999 段全部 ID）：
  `6000,6010,6011,6012,6013,6014,6015,6020,6021,6022,6023,6024,6025,6026,6030,6031,6032,6033,6034,6040,6041,6042,6043,6044,6045,6100-6136,6140-6146,6161-6178,6181-6187,6190,6201`
  → 本批次使用的 `6050-6059 / 6060-6063 / 6070-6073 / 6075 / 6080-6085 / 6090-6092` **全部空闲**（6000-6045 已占用，6100+ 已占用）。
- **前端组件路径约定**（合并菜单时需与前端实现一致）：
  `iot/deviceGroup/index`、`iot/deviceTag/index`、`iot/event/index`、`iot/command/index`、`iot/forwardRule/index`。
- 父菜单：设备状态变更挂在 **6020**（IoT 设备管理，排序 7）；分组/标签/事件/指令/转发挂在 **6000**（IoT 平台，排序 5~9）。

---

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| # | 库 | 对象 | 变更 | 幂等/前置 | 需清 Redis |
|---|---|---|---|---|---|
| 1 | `ry-iot` | `iot_device_group` | 新建表（树形分组：`parent_id`/`ancestors`/`group_code`/`status` + 审计 + `del_flag`；`uk_code(tenant_id, group_code)`） | `CREATE TABLE IF NOT EXISTS`；无前置数据 | 否 |
| 2 | `ry-iot` | `iot_device_group_rel` | 新建表（设备-分组 N:N；`uk_device_group(tenant_id, device_id, group_id)`） | 同上 | 否 |
| 3 | `ry-iot` | `iot_device_tag` | 新建表（标签：`tag_name`/`tag_color`；`uk_name(tenant_id, tag_name)`） | 同上 | 否 |
| 4 | `ry-iot` | `iot_device_tag_rel` | 新建表（设备-标签 N:N；`uk_device_tag(tenant_id, device_id, tag_id)`） | 同上 | 否 |
| 5 | `ry-iot` | `iot_device_event` | 新建表（设备事件：事件码/级别/时间/载荷/处理状态/联动告警ID；`uk_source_msg_id` 幂等） | 同上 | 否 |
| 6 | `ry-iot` | `iot_device_command` | 新建表（下行指令留痕：类型/Topic/载荷/状态/回执；`uk_ack_msg_id` 幂等） | 同上 | 否 |
| 7 | `ry-iot` | `iot_forward_rule` | 新建表（转发规则：范围/URL/方法/头/超时/重试；`uk_rule_scope(tenant_id, product_id, device_id, rule_name)`） | 同上 | 否 |
| 8 | `ry-iot` | `iot_forward_log` | 新建表（投递留痕：状态/重试/HTTP码/失败原因/下次重试；`idx_retry(tenant_id, status, next_retry_time)`） | 同上 | 否 |
| 9 | `ry-vue` | `sys_menu` | `INSERT` 25 条权限码（设备状态变更 6075；分组 6050-6054/6090；标签 6055-6059/6091；事件 6060-6063；指令 6070-6073；转发 6080-6085） | `INSERT IGNORE`；主键被占用需改 ID | 否 |

- 增量脚本：`script/sql/update/2026-09-20-iot-device-ops.sql`（**可重复执行**；无 ALTER、无存量数据清洗，不需要停机窗口）。
- 主脚本 `script/sql/ry-iot.sql` 已同步（新环境按主脚本导入即可，无需执行增量脚本）。
- 建议清理 Redis：**不需要**（无历史键冲突）；新增键 `iot:lastreport:{deviceId}`（TTL 7 天）可自然过期。
- 字符集核对：8 张新表均显式声明 `CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci`、`ENGINE=InnoDB`、`ROW_FORMAT=Dynamic`，
  `tenant_id varchar(20) NOT NULL DEFAULT '000000'`，符合 `doc/system/specs/charset-encoding-standard.md` §4/§5。

---

## 5. 验证记录

### 5.1 编译 + 单测

```powershell
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false `
  "-Dtest=IotDeviceGroupTagServiceTest,IotDeviceLifecycleTest,IotDeviceEventServiceTest,IotDeviceCommandServiceTest,IotForwardDispatcherTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

结果：**见下方"实测结果"**（`-am` 上游 27 个模块全部编译通过；`ruoyi-iot` 主源码 + 测试源码编译通过）。

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- com.nexus.iot.command.service.IotDeviceCommandServiceTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0 -- com.nexus.iot.device.group.service.IotDeviceGroupTagServiceTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- com.nexus.iot.device.service.IotDeviceLifecycleTest
[INFO] Tests run:  8, Failures: 0, Errors: 0, Skipped: 0 -- com.nexus.iot.event.service.IotDeviceEventServiceTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 -- com.nexus.iot.forward.IotForwardDispatcherTest
[INFO] Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

另跑了一次**本模块既有测试的回归**（含 `IotMqttAuthenticatorTest`/`IotMqttTopicParserTest`/`IotMessagePipelineServiceTest`/
`IotStreamConsumerTest`/`IotAlert*Test` 等 11 个既有类，因本批次改了 `IotMqttTopicParser`、`IotMessagePipelineService`、
`IotStreamConsumer`）：**见 §5.6**。

> 过程中修正的编译/测试问题（供后续代理避坑）：
> ① `IotStreamConsumer` 新增字段 `IotForwardDispatcher` 缺少 import（首轮编译失败）；
> ② `PageQuery` 无无参构造（`new PageQuery(1, 10)`）；
> ③ `mockStatic` 的 `MapstructUtils`/`JsonUtils` 静态初始化依赖 Spring 容器（`SpringUtils.getBean(...)`），
>    **无法打桩**（`Cannot instrument class ... could not be initialized`）→ 已把这两个静态依赖收敛为**可注入组件**（见 §5.4）；
> ④ 在测试方法里裸用 `anyString()`/`eq()`（不在 stubbing/verify 内）会污染 Mockito 匹配器栈，
>    导致**同一类中后续用例**报 `InvalidUseOfMatchersException`（已删除该自检用例）。

### 5.2 新增测试类与用例数

| 测试类 | 用例数 | 覆盖的必测项 |
|---|---|---|
| `com.nexus.iot.device.group.service.IotDeviceGroupTagServiceTest` | 17 | 分组树 ancestors（根/子）、父级变更后代重写（含直接子代前缀修复）、删除前置校验（子分组/已挂设备）、批量挂分组幂等与移出、设备缺失拒绝、标签绑定幂等/级联删除/缺失拒绝、分组子树+标签交集、子树边界、deviceCount 填充 |
| `com.nexus.iot.device.service.IotDeviceLifecycleTest` | 14 | CAS 成功/冲突/终态拒绝/同状态拒绝/非法值、**禁用后 MQTT 认证拒绝**、激活+正确密钥通过、`updateByBo` 字段白名单、新增强制 ACTIVE/OFFLINE、运维详情聚合、空集筛选下推、租户桩自检 |
| `com.nexus.iot.event.service.IotDeviceEventServiceTest` | 8 | 事件落库字段 + PENDING、唯一键幂等、告警类事件联动（SOURCE_EVENT/HIGH 映射）、非告警不联动、缺事件码跳过、处理状态 CAS 冲突/非法/成功 |
| `com.nexus.iot.command.service.IotDeviceCommandServiceTest` | 11 | 先留痕后下发（PENDING→SENT）+ Topic 规约 + 载荷含 commandId、服务调用 event/set、缺服务名拒绝、禁用设备拒绝、发布失败落 FAILED、回执更新/幂等/无 commandId 回落/未匹配、重发拒绝/成功 |
| `com.nexus.iot.forward.IotForwardDispatcherTest` | 16 | SSRF 黑名单（回环/私网/CGNAT/链路本地/ftp/userinfo）与白名单、规则+属性匹配、内网 URL 不发起请求、成功/失败/超限留痕与 next_retry_time、投递前二次校验、重试 CAS 领取与冲突、产品范围匹配、缺租户跳过 |
| **合计（新增）** | **66** | 任务要求的 5 项必测（分组/标签 CRUD 与筛选、禁用后认证被拒、事件落库与查询、指令下发留痕与回执更新、转发失败重试）全部覆盖 |

### 5.3 测试分组

5 个测试类均标注 `@Tag("local")` + `@Tag("dev")`；`ruoyi-iot/pom.xml` 已覆盖根 pom 的 `<groups>` 过滤（`iot.groups` 为空 = 全部执行），
故 `local`/`dev` profile 下都会真实执行。

### 5.4 测试期注意事项（供后续代理避坑）

- `MapstructUtils.convert` 是静态工具（内部 `SpringUtils.getBean`），单测必须 `mockStatic(MapstructUtils.class)` 并自行拷贝字段；
- `LambdaQueryWrapper/LambdaUpdateWrapper` 需先 `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), X.class)`；
- 断言"条件更新写入了哪个值"可用 `((LambdaUpdateWrapper<T>) w).getParamNameValuePairs().containsValue("SUCCESS")`；
- SSRF 用例**不要用域名**（离线环境 DNS 解析失败会被判为拒绝，掩盖真实语义），用公网字面量 IP（如 `93.184.216.34`）；
- **`MapstructUtils` / `JsonUtils` 不可静态打桩**（与 `RedisUtils` 同类问题：静态字段 `SpringUtils.getBean(...)` 需要 Spring 容器，
  初始化失败后该类在 JVM 内永久报 `Could not initialize class`）。本批次已按告警批次的 `IotAlertStateStore` 做法把它们
  收敛为**可注入组件**：
  - 4 个服务（`IotDeviceGroupServiceImpl`/`IotDeviceTagServiceImpl`/`IotDeviceServiceImpl`/`IotForwardRuleServiceImpl`）
    改为注入 mapstruct-plus 的 `Converter`（Spring 容器本就注册该 Bean，运行时行为与 `MapstructUtils.convert` 完全一致）；
  - `IotDeviceCommandServiceImpl` 与 `IotForwardDispatcher` 改为注入全局 `ObjectMapper`（与 `JsonUtils` 用的是同一个 Bean，
    仍复用全局 long→String 序列化配置）。
  后续代理在本模块新增单测时，**不要**再对这两个静态类使用 `mockStatic`。
- **不要**在测试方法体内裸用 Mockito 匹配器（`anyString()`/`eq(x)` 等）做"自检"，否则匹配器栈残留会让**同类后续用例**报
  `InvalidUseOfMatchersException`（本轮踩过一次）。

### 5.6 本模块既有测试回归（因本批次改了 `IotMqttTopicParser`/`IotMessagePipelineService`/`IotStreamConsumer`）

```powershell
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false `
  "-Dtest=IotDeviceGroupTagServiceTest,IotDeviceLifecycleTest,IotDeviceEventServiceTest,IotDeviceCommandServiceTest,IotForwardDispatcherTest,IotMqttAuthenticatorTest,IotMqttAuthorizatorPolicyTest,IotMqttTopicParserTest,IotMessagePipelineServiceTest,IotStreamConsumerTest,IotDataValueResolverTest,IotAlertCheckerTest,IotAlertRecordServiceTest,IotAlertRuleServiceTest,IotDeviceDataLimitTest,IotSchedulerFallbackTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

结果：**BUILD SUCCESS，157/157 通过（Failures 0 / Errors 0 / Skipped 0）**，16 个测试类全部执行 ——
即本批次改动**未破坏**既有 MQTT 认证/授权、Topic 解析、消息管道、Stream 消费、告警规则/记录、数据 LIMIT 下推与定时任务兜底测试。

### 5.5 SQL 校验（人工/脚本核对）

```sql
-- 表结构（ry-iot）
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION, ROW_FORMAT FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'ry-iot'
   AND TABLE_NAME IN ('iot_device_group','iot_device_group_rel','iot_device_tag','iot_device_tag_rel',
                      'iot_device_event','iot_device_command','iot_forward_rule','iot_forward_log');
-- 期望：8 行，均 InnoDB / utf8mb4_unicode_ci / Dynamic

-- 唯一键（含 tenant_id 维度）
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA='ry-iot'
   AND TABLE_NAME IN ('iot_device_group','iot_device_group_rel','iot_device_tag','iot_device_tag_rel',
                      'iot_device_event','iot_device_command','iot_forward_rule')
   AND NON_UNIQUE=0
 GROUP BY TABLE_NAME, INDEX_NAME;
-- 期望：uk_code(tenant_id,group_code) / uk_device_group(tenant_id,device_id,group_id)
--       uk_name(tenant_id,tag_name)   / uk_device_tag(tenant_id,device_id,tag_id)
--       uk_source_msg_id(source_msg_id)  ← 技术键（Stream entry id，全局唯一，tenant_id 是其函数）
--       uk_ack_msg_id(ack_msg_id)        ← 同上
--       uk_rule_scope(tenant_id,product_id,device_id,rule_name)

-- 权限码
SELECT menu_id, menu_name, perms FROM `ry-vue`.`sys_menu` WHERE menu_id BETWEEN 6050 AND 6099 ORDER BY menu_id;
-- 期望：25 行
```

---

## 6. 风险与回滚说明

| 风险 | 说明 | 回退方式 |
|---|---|---|
| 主脚本 `ry-iot.sql` 已改 | 新环境导入会多出 8 张表；不影响既有 3 张表（**未 ALTER 存量表**，位序 INSERT 不受影响） | `DROP TABLE iot_device_group, iot_device_group_rel, iot_device_tag, iot_device_tag_rel, iot_device_event, iot_device_command, iot_forward_rule, iot_forward_log;` + 删除主脚本对应段落（改动集中在文件末尾，diff 干净） |
| `updateByBo` 忽略前端传入的 `status`/`connectionStatus` | 若某前端页面原本依赖"编辑设备时改状态"，改动后该字段静默失效（需改走 `PUT /iot/device/{id}/lifecycle`）。审计已确认前端当前**没有**状态编辑控件，风险低 | 还原 `IotDeviceServiceImpl.updateByBo` 中 3 行 `setXxx(null)` |
| `insertByBo` 固定 `status=ACTIVE` | 若存在"导入已停用设备"的场景，需先新增再禁用（两步） | 同上 |
| 事件/回执分流到新 Stream `iot:event:stream` | 新增消费者组（NEWEST 起消费）；**升级瞬间的存量事件流为空**，无历史积压问题。若设备端已在发 `event/post`，升级前的事件仍按旧逻辑被丢弃（不可恢复） | 停止 `IotEventStreamConsumer`（`nexus.iot.pipeline.event-consumer-enabled=false`）+ 还原 `IotMessagePipelineService` 分流分支 |
| 下行发布依赖内嵌 Broker | `nexus.iot.mqtt.enabled=false` 时指令落 FAILED（留痕写明原因），不会静默丢失；多实例部署下 `internalPublish` 只作用于**本实例**的 Broker 会话，若 Broker 非全实例内嵌，需改为外部 Broker 客户端发布（见 §8 未完成项） | 还原 `IotDeviceCommandServiceImpl.send` 中的发布调用（保留登记） |
| 事件→告警联动 | 告警类事件会写入 `iot_alert_record`（`property_code` = 事件码）。若某设备高频上报同一告警事件，按告警记录"同设备+属性唯一未恢复"语义只合并更新，**不会刷屏**；但会持续 UPDATE 该记录 | 关闭联动（删除 `IotDeviceEventServiceImpl.linkAlert` 调用）或提高事件级别门槛 |
| 数据转发外发 | 规则启用后会真实外发设备数据（含租户/设备/属性/值）；**SSRF 已双重校验**，但目标服务由运维配置，误配公网地址会把生产数据外发到外部 | 停用规则（`enabled=0`）或删除规则；留痕表保留审计线索 |
| 转发线程池/重试 | 固定 2 线程 + 每分钟最多 50 条重试；目标服务不可用时留痕增长（每条数据一条 FAILED 记录）。高频设备 + 长时间故障会放大留痕表体积 | 停用规则；或后续加"熔断/限流"（见 §8） |
| 静态工具收敛为可注入组件 | `IotDeviceServiceImpl` 等 4 个服务的构造器多了一个 `Converter` 参数、2 个类多了 `ObjectMapper` 参数。**运行时行为等价**（注入的就是容器里那个 Bean）；但若有别处用 `new` 手工构造这些 Service（全库已确认只有本批次测试这样做）需同步补参 | 还原为静态 `MapstructUtils`/`JsonUtils` 调用（会重新引入"单测无法打桩"问题） |
| 新增权限码未合并 | 未合并 `sys_menu` 种子时，非超管调用新端点会 403（超管正常） | 合并 §3.2 的 25 行，或临时放宽 `@SaCheckPermission` |

**回滚步骤**：删除 `ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/{event,command,forward}/`、
`.../device/group/`、`.../device/domain/IotDeviceStatus.java`、`.../device/domain/vo/IotDeviceOpsVo.java`、
`.../device/domain/bo/IotDeviceStatusBo.java`、`.../pipeline/IotDeviceReportTimeCache.java`、`.../mqtt/IotMqttPublisher.java`、
`src/test/java/com/nexus/iot/{device/group,device/service/IotDeviceLifecycleTest.java,event,command,forward}/`；
还原 `IotDeviceServiceImpl`/`IIotDeviceService`/`IotDeviceController`/`IotDeviceBo`/`IotStreamConsumer`/
`IotMessagePipelineService`/`IotMqttTopicParser`/`IotPipelineProperties`/`IotAlertConstants`；
还原 `script/sql/ry-iot.sql` 并删除 `script/sql/update/2026-09-20-iot-device-ops.sql`；清理 §4 的 8 张表与 25 条 `sys_menu`。

---

## 7. 前端需求（本批次不做，交前端代理）

### 7.1 新增 API（建议 `plus-ui/src/api/iot/`）

```ts
// deviceGroup/index.ts
listDeviceGroup(query)                GET    /iot/deviceGroup/list
getDeviceGroup(groupId)               GET    /iot/deviceGroup/{groupId}
addDeviceGroup(data)                  POST   /iot/deviceGroup
updateDeviceGroup(data)               PUT    /iot/deviceGroup
delDeviceGroup(ids)                   DELETE /iot/deviceGroup/{ids}
assignDeviceGroup({deviceIds, groupId, remove})  PUT /iot/deviceGroup/assign
groupDeviceIds(groupId, includeSubGroup)         GET /iot/deviceGroup/{groupId}/deviceIds

// deviceTag/index.ts
listDeviceTag(query)                  GET    /iot/deviceTag/list
optionDeviceTag()                     GET    /iot/deviceTag/optionList
getDeviceTag(tagId)                   GET    /iot/deviceTag/{tagId}
deviceTagIds(deviceId)                GET    /iot/deviceTag/device/{deviceId}
addDeviceTag(data) / updateDeviceTag(data) / delDeviceTag(ids)
bindDeviceTag({deviceIds, tagIds, remove})       PUT /iot/deviceTag/bind

// device（既有模块新增）
deviceOps(deviceId)                   GET    /iot/device/{deviceId}/ops
changeDeviceLifecycle(deviceId, {status, remark}) PUT /iot/device/{deviceId}/lifecycle
// 列表新增查询参数：groupId / includeSubGroup / tagIds（tagIds 为数组，多标签取交集）

// event/index.ts
listDeviceEvent(query)                GET    /iot/event/list
getDeviceEvent(eventId)               GET    /iot/event/{eventId}
processDeviceEvent({eventId, processStatus, remark}) PUT /iot/event/process
exportDeviceEvent(query)              POST   /iot/event/export

// command/index.ts
listDeviceCommand({deviceId, status, pageNum, pageSize}) GET /iot/command/list
getDeviceCommand(commandId)           GET    /iot/command/{commandId}
sendPropertySet({deviceId, params, remark})      POST /iot/command/propertySet
sendServiceInvoke({deviceId, serviceName, params}) POST /iot/command/serviceInvoke
resendDeviceCommand(commandId)        PUT    /iot/command/{commandId}/resend

// forwardRule/index.ts
listForwardRule({ruleName, enabled})  GET    /iot/forwardRule/list
getForwardRule(ruleId)                GET    /iot/forwardRule/{ruleId}
addForwardRule(data) / updateForwardRule(data) / delForwardRule(ids)
forwardLogList({ruleId, deviceId, status, pageNum, pageSize}) GET /iot/forwardRule/log/list
```

### 7.2 页面

| 页面 | 路由/组件（menu） | 要点 |
|---|---|---|
| 设备分组 | `iot/deviceGroup/index`（6050） | 左树右表：树按 `parentId` 组树（后端返回平铺 + `ancestors`），节点显示 `deviceCount`；操作：新增子分组/修改/删除（有子分组或已挂设备时后端会报错，需提示）；右侧设备表支持多选 → "挂入分组/移出分组"（`iot:deviceGroup:assign`） |
| 设备标签 | `iot/deviceTag/index`（6055） | 标签列表（名称 + 颜色色块 + 已绑定设备数）；设备列表多选 → "打标签/解绑标签"（`iot:deviceTag:bind`） |
| 设备管理（既有页扩展） | `iot/device/index`（6020） | ①列表增加"分组"树/下拉 + "标签"多选筛选（`groupId`/`includeSubGroup`/`tagIds`）；②行操作增加"激活/禁用/注销"（二次确认，注销需强提示"不可恢复"）+ "运维详情"抽屉（调 `/{id}/ops` 展示在线状态、最后在线/上报时间、固件版本、分组标签）；③列表的 `status`/`connectionStatus` 字段应改为**只读展示**（后端已忽略前端写入） |
| 设备事件 | `iot/event/index`（6060） | 列表：设备、事件码、事件名称、级别（INFO/WARN/ERROR/CRITICAL 标签色）、事件时间、接收时间、处理状态、联动告警ID（可跳告警记录页）；筛选：设备、事件码、级别、处理状态、是否已联动告警、时间区间；行操作：查看载荷（JSON 弹窗）、标记已处理/忽略（`iot:event:process`）、导出 |
| 设备指令 | `iot/command/index`（6070） | 列表：设备、指令类型、Topic、状态（PENDING/SENT/SUCCESS/FAILED 标签色）、下发时间、回执时间、重发次数、结果说明；行操作：查看载荷/回执载荷、重发（`iot:command:resend`，已成功不可重发）；顶部"下发指令"对话框：选设备 → 属性设置（属性编码/值键值对）或服务调用（服务名 + 参数 JSON），提交调 `propertySet`/`serviceInvoke` |
| 数据转发 | `iot/forwardRule/index`（6080） | 规则列表/表单（名称、产品/设备范围、属性白名单、URL、方法、请求头 JSON、超时、最大重试、启停）；**表单需提示"内网/保留地址会被拒绝"**（后端保存时校验）；"投递留痕"抽屉/子页（`iot:forwardRule:log`）：状态、重试次数、HTTP 码、失败原因、下次重试时间 |

### 7.3 类型定义

`plus-ui/src/api/iot/**/types.ts`：所有 ID（`deviceId`/`groupId`/`tagId`/`eventId`/`commandId`/`ruleId`/`logId`/`alertId`）
均为雪花 ID，后端已全局序列化为字符串，**前端一律按 `string` 处理**，勿用 `number`。

### 7.4 OTA 固件升级：可复用资产与建议（本批次不做）

- **已有可复用资产**：
  1. `iot_device.firmware_version` 列（`ry-iot.sql:36`，已在设备列表/运维详情返回）——OTA 的"当前版本"来源；
  2. **本批次交付的 `property/set` 下行通道**（`IotMqttPublisher` + `iot_device_command` 留痕 + 回执更新）——
     OTA 触发指令（`{type:"SERVICE_INVOKE", service:"otaUpgrade", params:{url, version, md5}}`）可直接复用，无需新建通道；
  3. `iot_device_event`（`event/post`）——设备上报升级进度/结果事件的落库与查询已就绪，OTA 进度可用事件码承载；
  4. `iot_forward_rule`/`iot_forward_log` 的"配置 + 异步投递 + 重试 + 留痕"模式可作为 OTA 任务表的设计参照；
  5. `IotDeviceStatus.DECOMMISSIONED` 与禁用能力——升级期间"临时禁用"语义可复用（但需注意禁用会拒绝 MQTT 接入）。
- **建议**：新增 `iot_firmware`（版本/包地址/校验和/适用产品/强制升级）+ `iot_ota_task`（设备/目标版本/状态/进度/重试），
  以 `property/set`（或 `event/set`）下发升级指令，设备经 `event/post` 回传进度；包体走 OSS（`ruoyi-common-oss` 已具备），
  下发分片下载 URL 并校验 md5。

### 7.5 字典建议（可选，需 ry-vue 独占文件）

若前端需要下拉字典，需在 `ry-vue.sql` 增加 `sys_dict_type`/`sys_dict_data`：
`iot_device_lifecycle`（ACTIVE/INACTIVE/DECOMMISSIONED）、`iot_event_level`（INFO/WARN/ERROR/CRITICAL）、
`iot_event_process_status`（PENDING/PROCESSED/IGNORED/FAILED）、`iot_command_type`（PROPERTY_SET/SERVICE_INVOKE）、
`iot_command_status`（PENDING/SENT/SUCCESS/FAILED）、`iot_forward_status`（PENDING/SUCCESS/FAILED）。
本批次未落字典（属 ry-vue 独占文件），前端可先硬编码常量。

---

## 8. 未完成项（汇总）

1. **前端页面与 API 层**（§7）—— 明确不在本批次。
2. **OTA 固件升级**（§7.4）—— 单独批次；本批次已铺好下行通道与事件落库底座。
3. **设备影子/期望值**（审计 §4#3）—— 前置底座已就绪（`property/set` + 回执），需新增影子表与版本乐观锁。
4. **指令超时自动置 TIMEOUT** —— 需定时扫描 + 在线状态配合。
5. **多实例下 `internalPublish` 的适用性** —— 内嵌 Broker 每实例独立，跨实例下发需改外部 Broker 客户端（如实说明限制）。
6. **转发目标类型仅 HTTP**；MQ 转发未做（可复用 `ruoyi-common-mq`）。
7. **转发熔断/限流**：目标长期不可用时留痕表增长，建议后续加"连续失败 N 次自动停用规则 + 告警"。
8. **端点级集成测试**（MockMvc/真实库）与**权限码合并验证** —— 留给主代理最终回归。
9. **`sys_dict` 字典种子**（属 ry-vue 独占文件）。
10. **事件流的历史回填**：升级前已被丢弃的 `event/post` 消息不可恢复（无存储）。
