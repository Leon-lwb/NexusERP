# P0-8 IoT 自动采集打通 - 收尾实施方案

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **编制日期**: 2026-07-29
> **所属计划**: [2026-07-24-mes-p0-development-plan-v1.0.md](./2026-07-24-mes-p0-development-plan-v1.0.md) §3.8
> **状态**: ✅ 完整交付（编码 + SQL 落库 + 单测 11/11 + 编译/eslint + E2E 10/10）

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

P0-8「IoT 自动采集打通」核心能力已在前序迭代（Week 26~28）落地大半：IoT 设备软关联（F8-1）、归档任务（F8-3）、阈值告警->安灯（F8-7）均已完整交付。但 2026-07-29 跨模块代码测绘发现仍有**三处缺口**导致 P0-8 无法宣告完工：

1. **F8-2 采集点-IoT 绑定前端断链**：实体/BO/Service 都有 `iotDeviceId`+`iotPropertyCode`，但 `DcCollectionPointVo` 没暴露这两个字段，前端采集点表单也没有 IoT 设备/属性选择器 -> AUTO 采集点无法在界面上绑定 IoT 设备，归档任务虽能跑但"绑定点"只能靠手改库
2. **F8-5 设备状态联动缺失**：`IotMqttInterceptHandler.onConnect/onDisconnect/onConnectionLost` 只更新 IoT 侧 DB+Redis，**零 MES 回调** -> IoT 设备掉线后 MES `eq_equipment.status` 仍是 NORMAL，设备监控大屏与实际不符
3. **F8-6 数据补偿缺失**：`IotDataArchiveTask` 用固定 6min 滑窗，无 per-point 游标 -> MES 重启或任务中断超过 6min 即丢数据，无回补

### 1.2 测绘结论（2026-07-29，已逐项 file:line 核实）

| 子功能 | 状态 | 证据 | 本方案是否处理 |
|---|---|---|---|
| F8-1 设备软关联 | ✅ DONE | `EqEquipmentServiceImpl.updateIotDeviceLink`(:463) + IoT 侧 `updateMesEquipmentLink` + 前端绑定弹窗全齐 | 否 |
| F8-2 采集点绑定 | 🟡 后端done/前端缺 | VO 缺字段(`DcCollectionPointVo` 无 iotDeviceId/iotPropertyCode)；前端表单(`collection-point/index.vue:82-136`)无 IoT 选择器 | **是** |
| F8-3 数据归档 | ✅ DONE | `IotDataArchiveTask` 完整（SnailJob + 去重 + @DS） | 否（仅 SnailJob 控制台注册 cron） |
| F8-4 实时推送 | 🟡 轮询可用 | IoT SSE 已就绪(`/iot/device/{id}/realtime`)，MES 监控页用 30s 轮询 `batchIotStatus` | 否（用户决议保留轮询） |
| F8-5 状态联动 | ❌ MISSING | `IotMqttInterceptHandler.updateStatus`(:134) 仅更新 IoT 侧，无 MES 回写 | **是** |
| F8-6 数据补偿 | ❌ MISSING | `IotDataArchiveTask.PULL_WINDOW_MINUTES=6` 固定滑窗，无 last_archive_time 游标 | **是** |
| F8-7 异常告警 | ✅ DONE | `IotAlertChecker`->`IotAlertNotifier`->`IMesAndonService` 阈值告警链路完整 | 否 |

### 1.3 关键边界（已确认）

- **F8-5 用 MES 轮询同步（非事件驱动）**：用户 2026-07-29 决议。新增 MES 侧 SnailJob 每 60s 扫 `iot_device_id` 非空设备，调已有 `IotDeviceDataQueryService.getOnlineStatus`（Redis）回写 `eq_equipment.status`。**零 IoT 模块改动**，复用 `batchIotStatus` 既有基建，最低风险。代价：≤60s 延迟（非严格 on-change），对设备监控场景可接受。
- **F8-4 保留轮询**：用户 2026-07-29 决议。监控页 30s 轮询满足"看到 IoT 状态与最新数据"的功能要求，SSE 订阅列为后续可选增强，本周不动。
- **F8-5 状态映射只动 NORMAL↔OFFLINE，不碰 FAULT/MAINTENANCE/SCRAPPED**：
  - 新增 `OFFLINE` 字典值（`mes_equipment_status`），语义为"IoT 设备离线"（≠ FAULT 故障）
  - ONLINE + 当前 OFFLINE -> NORMAL（离线恢复）
  - OFFLINE + 当前 NORMAL -> OFFLINE（掉线）
  - **FAULT（操作员报修）/ MAINTENANCE（保养中）/ SCRAPPED（报废）一律不自动覆盖**——这些是受管状态，仅人工/维修流程可改
  - CAS 更新 `WHERE equipment_id=? AND status IN (NORMAL, OFFLINE)`，避免并发覆盖受管状态
- **F8-6 per-point 游标 + 分块回补**：`dc_collection_point` 加 `last_archive_time` 列。`IotDataArchiveTask` 每点 `effectiveStart = lastArchiveTime ?: (now - 6min)`；归档成功后推进游标到 endTime。gap >2h 分块拉取避免单次过载；gap >24h 截断并告警（避免拉数天数据）。空结果也推进游标（已扫描无数据≠应重扫）。
- **F8-2 前端选择器复用既有 IoT 设备 API**：`listIotDeviceForBind`（`@/api/iot/device`，F8-1 已用）；`iotPropertyCode` 用文本输入（对齐 `iot_device_data.property_code`，加 placeholder 提示），不引入"设备属性列表"新接口（避免过度设计）。
- **不改 IoT 模块**：F8-2/F8-5/F8-6 全部在 `ruoyi-mes` 内闭环。F8-5 轮询、F8-6 游标改造都不触碰 `ruoyi-iot`，符合"收尾"最小爆炸半径。

### 1.4 预期结果

- AUTO 采集点可在前端绑定 IoT 设备 + 属性编码，列表/详情回显绑定信息
- IoT 设备掉线 ≤60s 内 `eq_equipment.status` 置 OFFLINE，恢复后置 NORMAL；FAULT/MAINTENANCE/SCRAPPED 不被覆盖
- MES 重启或任务中断后，归档任务按 per-point 游标回补缺失区间数据，不丢不重
- `mvn compile`（mes）+ 前端 `eslint` 通过；新增单测全绿；E2E 三条链路通过

---

## 二、F8-2 采集点-IoT 绑定前端补齐

### 2.1 后端：VO 补字段

`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/domain/vo/DcCollectionPointVo.java`

```java
/** 关联IoT设备ID（AUTO类型必填） */
private Long iotDeviceId;

/** IoT属性编码（对应 iot_device_data.property_code） */
private String iotPropertyCode;
```

> BO 已有字段（`DcCollectionPointBo:42,45`），Service insert/update 已写入（`:81-82,:123-124`），查询已支持 iotDeviceId 过滤（`:163`）。**仅 VO 漏暴露**，补两字段即可让 `selectVoPage` 回显。

### 2.2 后端：Service 校验 AUTO 必填 IoT 设备

`DcCollectionPointServiceImpl.insertByBo/updateByBo` 增加校验（对齐 `autocode-form-pattern` memory：FE 清空 + BO 去 @NotBlank + Service 校验）：

```java
if ("AUTO".equals(bo.getPointType()) && bo.getIotDeviceId() == null) {
    throw new RuntimeException("AUTO 类型采集点必须关联 IoT 设备");
}
if (!"AUTO".equals(bo.getPointType())) {
    // 非 AUTO 清空 IoT 字段，避免脏数据（FE 也会清，双保险）
    bo.setIotDeviceId(null);
    bo.setIotPropertyCode(null);
}
```

### 2.3 前端：API 类型

`plus-ui/src/api/mes/dc/collection-point.ts`：`DcCollectionPointVO` / `DcCollectionPointForm` / `DcCollectionPointQuery` 三接口各加：

```ts
iotDeviceId?: number | string;
iotPropertyCode?: string;
```

### 2.4 前端：表单选择器（AUTO 时显示）

`plus-ui/src/views/mes/dc/collection-point/index.vue` 表单（现 L82-136）：

- `采集点类型` 下方加 `v-if="form.pointType === 'AUTO'"` 的两个表单项：
  - `关联IoT设备`：选择器，复用 `listIotDeviceForBind`（F8-1 已用），展示 deviceCode/deviceName，选中填 `iotDeviceId`
  - `IoT属性编码`：`el-input`，placeholder "对应 IoT 属性编码，如 temperature；留空归档全部属性"
- `pointType` change 时若非 AUTO，清空 `form.iotDeviceId`/`form.iotPropertyCode`（autocode-form-pattern）
- 表格列表加一列 `IoT设备`（展示 deviceCode 或 `-`，可选）
- 重置表单时一并清空 IoT 字段

---

## 三、F8-5 设备状态联动（MES 轮询同步）

### 3.1 新增字典值

`mes_equipment_status` 字典加 `OFFLINE`（字典标签"IoT离线"，排序置于 FAULT 后）。增量 SQL + `script/sql/ry-mes.sql` 字典种子同步。

> 前端 `dict-tag` 自动渲染，无需改前端设备页。

### 3.2 新增 SnailJob 任务

`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/task/EquipmentStatusSyncTask.java`：

```java
@Component
@RequiredArgsConstructor
@Slf4j
@DS("mes")
@JobExecutor(name = "equipmentStatusSyncTask")
public class EquipmentStatusSyncTask {

    private static final String ONLINE = "ONLINE";
    private static final String OFFLINE = "OFFLINE";
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_OFFLINE = "OFFLINE";
    /** 仅同步受 IoT 驱动的两态；FAULT/MAINTENANCE/SCRAPPED 受管状态不碰 */
    private static final List<String> SYNCABLE_STATUS = List.of(STATUS_NORMAL, STATUS_OFFLINE);

    private final EqEquipmentMapper equipmentMapper;
    private final IotDeviceDataQueryService iotDeviceDataQueryService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        // 1. 查 iot_device_id 非空且状态在 SYNCABLE_STATUS 内的设备
        List<EqEquipment> list = equipmentMapper.selectList(
            Wrappers.<EqEquipment>lambdaQuery()
                .isNotNull(EqEquipment::getIotDeviceId)
                .in(EqEquipment::getStatus, SYNCABLE_STATUS));
        int changed = 0;
        for (EqEquipment eq : list) {
            String online = iotDeviceDataQueryService.getOnlineStatus(eq.getIotDeviceId());
            String target = ONLINE.equals(online) ? STATUS_NORMAL : STATUS_OFFLINE;
            if (target.equals(eq.getStatus())) continue; // 无变化
            // 2. CAS 更新：WHERE status IN (NORMAL,OFFLINE)，避免并发覆盖刚被置 FAULT/MAINTENANCE 的
            int rows = equipmentMapper.update(null,
                Wrappers.<EqEquipment>lambdaUpdate()
                    .set(EqEquipment::getStatus, target)
                    .eq(EqEquipment::getEquipmentId, eq.getEquipmentId())
                    .in(EqEquipment::getStatus, SYNCABLE_STATUS));
            if (rows > 0) changed++;
        }
        return ExecuteResult.success("同步 " + changed + " 台");
    }
}
```

- **cron**：SnailJob 控制台注册 `equipmentStatusSyncTask`，建议 `7 */1 * * * ?`（每分钟，避开整点 :00）
- **多租户**：`@DS("mes")` + `EqEquipment` extends `TenantEntity`，MyBatis-Plus 多租户插件自动注入 tenant_id 条件
- **性能**：`getOnlineStatus` 走 Redis（`iot:status` hash），单次 <1ms；设备数通常 <百级，全量扫描可接受。若未来设备过千，再为 `IotDeviceDataQueryService` 加批量接口
- **不发布 MQ**：与 `EqEquipmentServiceImpl.updateStatus`（维修联动会发 MQ）不同，IoT 状态轮询不触发排产/工单 MQ 事件（离线/在线是运行时状态，非设备故障业务事件），避免噪音

### 3.3 不改 IoT 模块

`IotMqttInterceptHandler` 保持现状（只管 IoT 侧 DB+Redis）。MES 轮询读 Redis `iot:status` 即可，单向解耦。

---

## 四、F8-6 数据补偿（per-point 游标 + 分块回补）

### 4.1 表结构变更

`dc_collection_point` 加列：

```sql
ALTER TABLE dc_collection_point
    ADD COLUMN last_archive_time datetime NULL COMMENT '最后成功归档时间（数据补偿游标）';
```

`script/sql/ry-mes.sql` 同步加列（`dc_collection_point` CREATE TABLE 内）。

### 4.2 实体补字段

`DcCollectionPoint.java`：

```java
/** 最后成功归档时间（数据补偿游标） */
private Date lastArchiveTime;
```

> 不进 VO/BO（内部游标，不对前端暴露）。

### 4.3 IotDataArchiveTask 改造

`dc/task/IotDataArchiveTask.java` 核心调整（保留现有去重/解析逻辑）：

```java
/** 单次拉取上限（分钟）：超过则分块，避免单次拉取过载 */
private static final long MAX_CHUNK_MINUTES = 120L;  // 2h
/** 绝对截断上限（分钟）：超过则截断并告警（避免拉数天数据） */
private static final long MAX_GAP_MINUTES = 1440L;   // 24h

private int archiveOne(DcCollectionPoint point, Date windowStart, Date endTime) {
    // 游标优先：有 lastArchiveTime 从它开始（回补），否则退回滑窗
    Date cursor = point.getLastArchiveTime() != null
        ? point.getLastArchiveTime() : windowStart;
    long gapMin = (endTime.getTime() - cursor.getTime()) / 60000;
    if (gapMin > MAX_GAP_MINUTES) {
        log.warn("[IoT归档] 采集点 {} 断线 {} 分钟超 24h 上限，截断回补", point.getPointId(), gapMin);
        cursor = new Date(endTime.getTime() - MAX_GAP_MINUTES * 60000);
    }
    int total = 0;
    // 分块拉取
    Date segStart = cursor;
    while (segStart.before(endTime)) {
        Date segEnd = new Date(Math.min(segStart.getTime() + MAX_CHUNK_MINUTES * 60000, endTime.getTime()));
        total += archiveSegment(point, segStart, segEnd);  // 原 archiveOne 主体逻辑（getHistoryData + 去重 insert）
        segStart = segEnd;
    }
    // 推进游标（空结果也推进：已扫描无数据≠应重扫）
    equipmentPointMapper.updateLastArchiveTime(point.getPointId(), endTime);
    return total;
}
```

- `archiveSegment` = 现有 `archiveOne` 的 getHistoryData + iotPropertyCode 过滤 + extractValue + 去重 insert 逻辑，抽出为按 [start,end] 段归档
- `DcCollectionPointMapper` 加 `updateLastArchiveTime(pointId, time)`（`update set last_archive_time=#{time} where point_id=#{id}`）
- **幂等**：保留 `uk_point_collect_time` 唯一索引兜底——即便游标回退重复拉取，重复记录被 DuplicateKeyException 吞掉（现有逻辑已有）
- **首次运行**：`lastArchiveTime=null` -> 退回 6min 滑窗（PULL_WINDOW_MINUTES），行为与现状一致，平滑过渡
- **断线回补示例**：MES 重启 2h，某点 lastArchiveTime 停在 2h 前 -> 下次任务 cursor=2h 前 -> 分 1 块拉 2h 历史 -> 推进游标到 now。IoT 侧 `iot_device_data` 仍有这 2h 数据（IoT 持续采集），故可回补

### 4.4 配置

任务仍由 SnailJob 控制台注册 cron（建议 `0/5 * * * * ?` 每 5min，对齐 `PULL_WINDOW_MINUTES=6` 略大的设计）。代码内常量化 `MAX_CHUNK/MAX_GAP`，暂不外置 `mes.iot.*` 配置（沿用现有 IotDataArchiveTask 常量风格，避免过度配置化）。

---

## 五、SQL 变更清单

### 5.1 增量脚本

`doc/mes/sql/mes-p0-08-01-iot-wrapup.sql`（幂等）：

```sql
-- F8-6: dc_collection_point 加数据补偿游标列
ALTER TABLE dc_collection_point
    ADD COLUMN IF NOT EXISTS (last_archive_time datetime NULL COMMENT '最后成功归档时间（数据补偿游标）');

-- F8-5: mes_equipment_status 字典加 OFFLINE（IoT离线）
-- 主键雪花，用 @var 动态分配（对齐 ruoyi-snowflake-dict-menu-id memory）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, ... )
SELECT 'mes_equipment_status_offline', 5, 'IoT离线', 'OFFLINE', 'mes_equipment_status', ...
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='mes_equipment_status' AND dict_value='OFFLINE');
```

### 5.2 规范库同步

- `script/sql/ry-mes.sql`：`dc_collection_point` CREATE TABLE 内补 `last_archive_time` 列；`mes_equipment_status` 字典种子补 OFFLINE（若该字典种子在 ry-mes.sql；否则在 ry-vue.sql 字典段补）

> 落库时本地 MySQL(root/root, 3306) 执行增量脚本；规范库 SQL 同步提交。

---

## 六、测试

### 6.1 单元测试（对齐 P0-7 规范，JUnit5 + Mockito + AssertJ）

**`EquipmentStatusSyncTaskTest`**（`ruoyi-mes/src/test/java/com/nexus/mes/eq/task/`）：
- `online_offline_to_normal`：设备 OFFLINE + IoT ONLINE -> 更新 NORMAL
- `offline_normal_to_offline`：设备 NORMAL + IoT OFFLINE -> 更新 OFFLINE
- `skip_fault_maintenance_scrapped`：设备 FAULT/MAINTENANCE/SCRAPPED + IoT 任意 -> **不更新**（查询即过滤，断言 mapper.update 未调用）
- `skip_unbound`：iotDeviceId=null 不在查询集
- `cas_concurrent_skip`：CAS WHERE status IN(NORMAL,OFFLINE) 命中 0 行（并发被改 FAULT）-> 不抛异常
- mockStatic(LambdaUtils, CALLS_REAL_METHODS) + getColumnMap stub（对齐 `NcrOrderServiceImplTest` 模式，规避 MybatisPlus lambda cache 单测限制，见 memory `mes-p0-7-test-infra-fix`）

**`IotDataArchiveTaskTest`**（`ruoyi-mes/src/test/java/com/nexus/mes/dc/task/`，新建）：
- `cursor_null_falls_back_to_window`：lastArchiveTime=null -> effectiveStart = now-6min
- `cursor_advances_after_archive`：归档后 mapper.updateLastArchiveTime 被调用且 time=endTime
- `large_gap_chunked`：gap=3h -> 分 2 段（2h+1h）拉取
- `gap_over_24h_truncated`：gap=25h -> 截断到 24h + 告警日志
- `empty_result_still_advances_cursor`：getHistoryData 返回空 -> 游标仍推进

### 6.2 E2E 脚本

`doc/mes/scripts/p0-8-e2e-test.py`（对齐 `ruoyi-e2e-api-test-pattern` memory：openssl RSA+AES 登录 + Bearer/clientid + 雪花ID序列化为字符串）：

1. **F8-2**：建 AUTO 采集点（绑 IoT 设备 + iotPropertyCode）-> 查列表回显 iotDeviceId -> 建 MANUAL 点不带 IoT -> 改 AUTO 未选 IoT 设备报 "必须关联 IoT 设备"
2. **F8-5**：mock IoT 设备 OFFLINE（或等真实掉线）-> 触发/等待 `equipmentStatusSyncTask` -> 查 eq_equipment.status=OFFLINE -> 设备 ONLINE -> 再触发 -> status=NORMAL；另建一台 FAULT 设备 -> 触发 -> status 仍 FAULT（不覆盖）
3. **F8-6**：记 lastArchiveTime=T0 -> 等 1 个任务周期（或手动构造 gap）-> 查 dc_data_record 新增 source=IoT 记录覆盖 gap 区间 -> 查 lastArchiveTime 已推进 > T0

> F8-5/F8-6 依赖 SnailJob 触发与 IoT 真实数据，E2E 可降级为"调用 Service 方法 + 断言 DB"的半 E2E（绕过 SnailJob 调度，直接调 `jobExecute` 或 Service 方法）。

---

## 七、验收标准

| 编号 | 场景 | 标准 |
|---|---|---|
| F8-2-A | AUTO 采集点绑定 | 前端建 AUTO 采集点，选 IoT 设备 + 填属性编码，保存后列表回显 iotDeviceId |
| F8-2-B | 非 AUTO 清空 | MANUAL 类型点即便误传 iotDeviceId，Service 清空，库中为 NULL |
| F8-2-C | AUTO 必填校验 | AUTO 点未选 IoT 设备 -> 报 "AUTO 类型采集点必须关联 IoT 设备" |
| F8-5-A | 离线回写 | IoT 设备 OFFLINE ≤60s 后 eq_equipment.status=OFFLINE |
| F8-5-B | 上线恢复 | OFFLINE 设备 IoT 重新 ONLINE ≤60s 后 status=NORMAL |
| F8-5-C | 受管状态不覆盖 | FAULT/MAINTENANCE/SCRAPPED 设备 IoT 状态变化 -> status 不变 |
| F8-6-A | 游标推进 | 归档任务跑后 lastArchiveTime 推进到当前 endTime |
| F8-6-B | 断线回补 | 构造 2h gap，任务跑后 dc_data_record 补齐 2h 内 source=IoT 记录，不丢不重 |
| F8-6-C | 首次回退 | lastArchiveTime=null 时退回 6min 滑窗（与现状一致） |
| 工程 | 编译/lint/测试 | `mvn compile -pl ruoyi-modules/ruoyi-mes -am` SUCCESS；eslint 0 error；新增单测全绿 |

---

## 八、任务清单

### 后端
- [ ] F8-2: `DcCollectionPointVo` 补 `iotDeviceId`/`iotPropertyCode` 字段
- [ ] F8-2: `DcCollectionPointServiceImpl` insert/update 加 AUTO 必填校验 + 非 AUTO 清空
- [ ] F8-5: `mes_equipment_status` 字典加 OFFLINE（增量 SQL + 规范库）
- [ ] F8-5: 新建 `EquipmentStatusSyncTask`（SnailJob + @DS("mes") + CAS 状态映射）
- [ ] F8-6: `dc_collection_point` 加 `last_archive_time` 列（增量 SQL + 规范库）
- [ ] F8-6: `DcCollectionPoint` 实体补 `lastArchiveTime`
- [ ] F8-6: `DcCollectionPointMapper` 加 `updateLastArchiveTime`
- [ ] F8-6: `IotDataArchiveTask` 改造（游标优先 + 分块 + 截断 + 游标推进）

### 前端
- [ ] F8-2: `collection-point.ts` 三接口加 iotDeviceId/iotPropertyCode
- [ ] F8-2: `collection-point/index.vue` 表单加 IoT 设备选择器 + 属性编码输入（AUTO 时 v-if）+ 类型切换清空 + 列表列

### SQL
- [ ] `doc/mes/sql/mes-p0-08-01-iot-wrapup.sql`（ALTER + dict OFFLINE，幂等）
- [ ] `script/sql/ry-mes.sql` 同步（dc_collection_point 列 + 字典种子）
- [ ] 本地 MySQL(root/root,3306) 执行增量脚本

### 测试
- [ ] `EquipmentStatusSyncTaskTest`（6 用例）
- [ ] `IotDataArchiveTaskTest`（5 用例）
- [ ] `p0-8-e2e-test.py`（F8-2/F8-5/F8-6 三链路）
- [ ] `mvn compile` + eslint + 单测全绿

### SnailJob 控制台（手动）
- [ ] 注册 `iotDataArchiveTask` cron（建议 `0/5 * * * * ?`）
- [ ] 注册 `equipmentStatusSyncTask` cron（建议 `7 */1 * * * ?`）

---

## 九、风险与备注

| 风险 | 应对 |
|---|---|
| F8-5 轮询延迟（≤60s）不满足"严格实时" | 用户已接受；若后续需实时，再升级为 MQ/接口契约事件驱动（不改轮询任务，叠加事件通路） |
| F8-6 分块拉取大数据量压 IoT 查询 | MAX_CHUNK=2h 限段 + MAX_GAP=24h 截断告警；`getHistoryData` 走 MySQL 索引（deviceId+collectTime） |
| F8-5 CAS 与维修流程并发 | CAS `WHERE status IN(NORMAL,OFFLINE)` 保证维修刚置的 FAULT 不被覆盖；下一轮该设备已被排除出同步集 |
| OFFLINE 字典新增未授权给角色 | dict_data 无需角色授权（字典全局可见）；设备页 dict-tag 自动渲染 |
| SnailJob 未注册 cron 导致任务不跑 | 任务清单末尾单独列"控制台注册"步骤，E2E 前确认；代码无缺省 cron 是既有设计（F8-3 同） |

---

**编制者**: AI Agent
**审核状态**: 待用户批准 -> 执行
