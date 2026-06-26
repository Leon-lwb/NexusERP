# NexusERP — IoT 阶段一 · 第四周（Week 36）开发计划

> 关联文档：`doc/iot/NexusERP_IoT文档.md`（第十八节 18.1 Week 35-36；第十五节阶段一最小链路）
> 上周计划：`script/superpowers/plans/2026-06-18-iot-week35-plan.md`
> 计划制定：2026-06-18（执行周：Week 36）
> 工作量基准：3 人天
> 范围边界：**ECharts 实时仪表盘 + SnailJob 三任务 + 真实设备端到端联调**，收尾 IoT 阶段一最小链路。

---

## 0. Context（为什么做这个）

Week 33-35 已完成：后端全链路（MQTT→Stream→MySQL+缓存+告警）、设备数据 REST 端点、SSE 实时端点、产品/设备 CRUD 页、IoT 字典。阶段一最小链路仍缺三块：

1. **数据不可视化**：设备列表有「查看」按钮但无实时仪表盘页，`/iot/device/{id}/realtime` SSE 端点（Week 35 已就绪）无前端消费方，`iot:latest` 缓存数据无法以图表呈现。
2. **无定时运维**：`iot_device_data` 持续增长无清理、设备异常掉线（非 graceful disconnect）后 `iot:status` 缓存 1h TTL 内仍是 ONLINE 无心跳纠偏、`iot:data:stream` 无限增长无 XTRIM。文档第六节明确这三项为阶段一必含。
3. **未端到端验证**：始终用 `mosquitto_pub` 模拟，未跑通「真实/模拟设备 → 仪表盘可见」全链路。

Week 36 目标：**让一台设备的数据实时出现在 ECharts 仪表盘上 + 三任务自动运维 + 阶段一验收报告**，正式收口 IoT 阶段一（文档第十五节最小链路定义）。

**不在本周范围**（属阶段二按需）：OPC UA/Modbus 适配、规则引擎、告警规则表、TDengine 迁移、边缘网关、场景联动、MES 深度联动。

---

## 1. 前置对齐决策（已自查确定）

| # | 决策点 | 结论 | 理由 |
|---|---|---|---|
| A | **实时仪表盘数据源** | 专用 EventSource 订阅 Week 35 `/iot/device/{id}/realtime`（per-device SSE）+ 进入页面时拉一次 `/latest` 初始化卡片 + `/data` 拉历史趋势 | Week 35 端点已就绪；不复用全局 `/resource/sse`（其 `sse.ts` 仅 dispatch 通知/IM/AI 类型，注入 `IOT_REALTIME` 需改公共文件且每帧广播，仪表盘是 per-device 场景，专用连接更干净） |
| B | **仪表盘图表组件** | 直接 `import * as echarts from 'echarts'` + `echarts.init(ref)` + `setOption`，与 `views/erp/wm/report/index.vue` 同模式 | 项目无 useECharts composable，echarts 6.0.0 已是依赖；不引入 v-chart 新依赖 |
| C | **实时图表更新策略** | SSE 帧（每 3s，含 `properties` 全量快照）→ 追加到滚动时间窗口数组（保留最近 N=60 点）→ `chart.setOption` 增量更新 | Week 35 推送已是 3s 全量快照，前端做增量追加即可；不做 diff 比较 |
| D | **SnailJob 任务调度配置** | 任务类用 `@JobExecutor(name="...")` 注册，cron 在 SnailJob 后台 `ruoyi_group` 配置（非本地 yml） | 与 `StockAlertTask`/`VendorEvaluationTask` 同模式；`application-dev.yml` 已配 `snail-job.enabled=true` + `group=ruoyi_group` |
| E | **清理任务保留期** | `iot_device_data` 删 90 天前（文档 6.1）；心跳超时 15min；Stream XTRIM MAXLEN 100000 | 文档 6.1/6.2 明确值，配置化便于调整 |
| F | **心跳任务实现** | 查 `iot_device` 中 `connection_status=ONLINE` 且 `last_online_time < now-15min` 的设备 → 调 `updateConnectionStatus(id,false)`（已有方法，同步刷 DB+缓存） | 复用 Week 33 `IIotDeviceService.updateConnectionStatus`，不重复造缓存逻辑 |
| G | **清理任务实现** | `IIotDeviceDataService` 新增 `deleteBefore(Date)` 方法，Mapper 用 `LambdaQueryWrapper.lt(collectTime, date)` + `baseMapper.delete(lqw)` | 无现成按时间删除方法；`IotDeviceDataMapper` 仅 BaseMapperPlus，用 wrapper 删除即可 |
| H | **仪表盘菜单/路由** | 复用 Week 33 设备菜单 6020，仪表盘作为设备列表「查看/实时」按钮跳转的隐藏路由（`/iot/device/realtime/:deviceId`），权限 `iot:device:query` | 不新增菜单项，避免菜单 SQL；与 ERP 表单页（orderheader/form）隐藏路由模式一致 |

---

## 2. 任务分解（按执行顺序）

### T1 · 后端运维 Service 方法（0.4d）
为三任务补齐缺失的 Service 方法：
- `IIotDeviceDataService` + impl：新增 `int deleteBefore(Date deadline)`（`@DS("iot")`，`LambdaQueryWrapper.lt(IotDeviceData::getCollectTime, deadline)` + `baseMapper.delete(lqw)`），返回删除行数。
- `IIotDeviceService` + impl：新增 `List<IotDevice> selectStaleOnlineDevices(Date deadline)`（`@DS("iot")`，查 `connection_status=ONLINE` 且 `last_online_time < deadline` 的设备实体集合，供心跳任务逐个置离线）。复用现有 `updateConnectionStatus(Long, boolean)`（已刷 DB+`IotDeviceStatusCache`）。
- 两者走 iot 数据源；`IotDevice`/`IotDeviceData` 实体已有 `lastOnlineTime`/`collectTime` 字段。
- **验收**：单元/手测调用 `deleteBefore` 删除指定时间前数据；`selectStaleOnlineDevices` 返回超时在线设备。

### T2 · SnailJob 三任务（0.8d）— 本周核心之一
新建 `com.nexus.iot.task` 下三个 `@Component @JobExecutor` 任务（参照 `StockAlertTask` 结构：`@RequiredArgsConstructor` + `ExecuteResult jobExecute(JobArgs)` + `SnailJobLog.REMOTE` 日志 + try/catch 返回 `ExecuteResult.success/failure`）：
- **`IotDataCleanupTask`**（`@JobExecutor(name="iotDataCleanupTask")`）：注入 `IIotDeviceDataService`；`deadline = now - 90d`；调 `deleteBefore(deadline)`；日志记删除行数。
- **`IotDeviceHeartbeatTask`**（`@JobExecutor(name="iotDeviceHeartbeatTask")`）：注入 `IIotDeviceService`；`deadline = now - 15min`；`selectStaleOnlineDevices(deadline)` 逐个 `updateConnectionStatus(id, false)`；日志记置离线设备数。
- **`IotStreamTrimTask`**（`@JobExecutor(name="iotStreamTrimTask")`）：注入 `IotPipelineProperties`；`RStream<Object,Object> stream = RedisUtils.getClient().getStream(properties.getStreamKey())`；Redisson 3.52 `stream.trim(StreamTrimArgs.maxlen(100000))` 或 `stream.trimNonTrim(100000)`（按 API 选可用重载）；日志记修剪结果。
- **验收**：编译通过；任务类被 SnailJob 客户端注册（启动日志/后台可见 executor 名）。**cron 配置在 SnailJob 后台 `ruoyi_group` 手动建任务**（清理每天 02:00、心跳每 5min、Stream 修剪每小时），文档注明。

### T3 · 仪表盘前端 API（0.2d）
新建 `plus-ui/src/api/iot/realtime/index.ts`：
- `getLatest(deviceId)` → `GET /iot/device/{id}/latest`（Week 35 端点）
- `getStatus(deviceId)` → `GET /iot/device/{id}/status`
- `getHistory(deviceId, start, end, limit)` → `GET /iot/device/{id}/data`（参数 `start`/`end`/`limit`）
- `types.ts`：`RealtimeFrame`（`deviceId/timestamp/connectionStatus/properties: Record<string,string>`）、`DeviceDataVO`（对齐 `IotDeviceData`：propertyCode/numericValue/textValue/boolValue/collectTime/dataType/unit 等）。

### T4 · 实时仪表盘页（1.0d）— 本周核心之二
新建 `plus-ui/src/views/iot/device/realtime.vue`（`<script setup lang="ts" name="IotDeviceRealtime">`）：
- **布局**：`el-row`/`el-col` 网格：顶部设备信息卡（编码/名称/在线状态指示灯 `el-tag` 绿灰）+ 数值卡片网格（每个 propertyCode 一张 `el-card`，显示最新值+单位，来自 `/latest`），下方 ECharts 实时折线图（多 propertyCode 一条线，3s 追加点，滚动 60 点窗口）+ 历史趋势图（`/data` 拉近 1h，柱/折线）。
- **SSE 订阅**：`onMounted` 建 `new EventSource(baseUrl + '/iot/device/' + deviceId + '/realtime?Authorization=Bearer ' + getToken())`；监听 `message`（或 `realtime` 命名事件），解析 `RealtimeFrame`，更新数值卡片 + 追加折线图数据；`onUnmounted` 关闭 `eventSource.close()`。
  - 注意 Week 35 端点用 `emitter.send(event().name("realtime").data(json))` → 前端用 `addEventListener('realtime', ...)`；同时 `event.data` 为 JSON 字符串。
- **ECharts**：`import * as echarts from 'echarts'`；`echarts.init(ref)` + `setOption`；resize 监听 `window.addEventListener('resize', chart.resize)`，`onUnmounted` `dispose()` + 移除监听（修复 `wm/report` 未 dispose 的小问题，本页做对）。
- **路由参数**：`deviceId` 从路由 `:deviceId` 取；权限 `iot:device:query`。
- **验收**：`mosquitto_pub` 发属性 → 仪表盘数值卡片 3s 内更新 → 折线图实时追加；断开设备 → 状态指示灯变灰。

### T5 · 路由注册 + 设备列表跳转（0.2d）
- `plus-ui/src/router/index.ts` `dynamicRoutes` 追加隐藏路由：
  ```ts
  { path: '/iot/device/realtime/:deviceId', component: Layout, hidden: true,
    permissions: ['iot:device:query'],
    children: [{ path: '', component: () => import('@/views/iot/device/realtime.vue'),
      name: 'IotDeviceRealtime', meta: { title: '设备实时数据', activeMenu: '/iot/device' } }] }
  ```
- `views/iot/device/index.vue` 操作列「查看」按钮改为跳转该路由（`proxy.$router.push('/iot/device/realtime/' + row.deviceId)`），或新增「实时」按钮。保留原查看 dialog 或替换——**默认：操作列增加「实时」链接按钮**，不动现有「查看」dialog。
- **验收**：设备列表点「实时」→ 跳转仪表盘页，URL 带 deviceId。

### T6 · 端到端联调 + 阶段一验收 + 文档收尾（0.4d）
- **联调顺序**（依赖 Week 33-35 已执行：ry-iot 库+DDL+菜单+字典）：
  1. SnailJob 后台 `ruoyi_group` 建三任务（cron 见决策 E）。
  2. 启动后端 → 三任务 executor 注册；启动前端 `npm run dev`。
  3. 用 `mosquitto_pub`（或一台真实/模拟 MQTT 设备）发属性上报 → 仪表盘实时刷新。
  4. 手动触发三任务（后台「执行一次」）：清理删 90 天前数据、心跳把超时设备置离线、Stream `XLEN` 降到 ≤100000。
  5. 设备异常 kill（非 graceful）→ 心跳任务 15min 内将其置 OFFLINE。
- **阶段一验收**：对照文档 19.1 全部 11 项验收（模块启动/数据源/MQTT/认证/管道/缓存/状态/告警/SSE/定时任务/真实设备联调）逐项打勾。
- **更新文档** `NexusERP_IoT文档.md`：
  - 顶部进度加 Week 36 行（仪表盘+三任务+联调 ✅，阶段一最小链路完成）。
  - 第十四节检查清单：`SnailJob 定时任务` → ✅；新增「实时仪表盘(ECharts+SSE)」✅、「真实设备联调」✅。
  - 第十八节 Week 35-36 任务表逐行 ✅。
- **产出阶段一验收报告**：`doc/iot/NexusERP_IoT阶段一验收报告.md`（新建），记录 19.1 全项验证结果 + 测试命令 + 截图占位。
- **验收**：阶段一最小链路（真实设备→MQTT→Stream→MySQL→SSE→仪表盘）全链路打通；三任务可手动/定时执行。

---

## 3. 交付物清单

| 类型 | 路径 |
|---|---|
| 后端 Service 方法 | `IIotDeviceDataService`+impl（+`deleteBefore`）、`IIotDeviceService`+impl（+`selectStaleOnlineDevices`） |
| SnailJob 任务 | `com/nexus/iot/task/IotDataCleanupTask.java`、`IotDeviceHeartbeatTask.java`、`IotStreamTrimTask.java` |
| 前端 API | `plus-ui/src/api/iot/realtime/{index.ts,types.ts}` |
| 前端仪表盘 | `plus-ui/src/views/iot/device/realtime.vue` |
| 路由 | `plus-ui/src/router/index.ts`（+1 realtime 隐藏路由） |
| 设备列表改造 | `plus-ui/src/views/iot/device/index.vue`（操作列加「实时」按钮） |
| 文档 | `NexusERP_IoT文档.md`（进度+检查清单+任务表）、`doc/iot/NexusERP_IoT阶段一验收报告.md`（新建） |
| 计划存档 | `script/superpowers/plans/2026-06-18-iot-week36-plan.md` |

> 无新菜单 SQL（复用 Week 33 设备菜单）；无新字典；无新表/DDL。

---

## 4. 自审查记录

**与 Week 33-35 代码衔接核对：**
1. ✅ `/iot/device/{id}/realtime` SSE 端点 Week 35 已就绪（`IotRealtimeSseManager` 3s 推 `event:realtime` 全量快照），前端直接订阅，不改后端。
2. ✅ `IotRealtimeSseManager` 推送用 `emitter.send(event().name("realtime").data(json))` → 前端用 `addEventListener('realtime', ...)`（非默认 `message`），计划已注明。
3. ✅ SnailJob 模式与 `StockAlertTask` 一致（`@JobExecutor`+`ExecuteResult`+`SnailJobLog.REMOTE`），`ruoyi-iot/pom.xml` 已依赖 `ruoyi-common-job`，`snail-job.enabled=true`。
4. ✅ `RStream` 经 `RedisUtils.getClient().getStream(key)` 获取（同 `IotStreamConsumer`/`IotMessagePipelineService`），Redisson 3.52 `trim(StreamTrimArgs.maxlen)` 可用。
5. ✅ `IIotDeviceService.updateConnectionStatus(Long,boolean)` Week 33 已实现（刷 DB `connection_status`+`last_online_time`+`IotDeviceStatusCache`），心跳任务直接复用。
6. ✅ ECharts 模式与 `views/erp/wm/report/index.vue` 一致；echarts 6.0.0 已是依赖。
7. ✅ 仪表盘走隐藏路由+`activeMenu:'/iot/device'`，与 ERP 表单页模式一致，无需菜单 SQL。

**风险识别与处理：**
8. ⚠️ **Redisson `RStream.trim` API 重载**：3.52 有 `trim(StreamTrimArgs)`（精确修剪）与 `trimNonTrim(long)`。计划在实现时确认可用重载，优先 `StreamTrimArgs.maxlen(100000)`；若签名不同改用等价重载，编译期可发现。
9. ⚠️ **SnailJob cron 需后台手动配**：任务类仅注册 executor，定时表达式在 SnailJob 后台 `ruoyi_group` 建。文档/验收报告注明「需在后台建任务」，否则任务不会自动跑。
10. ⚠️ **EventSource 跨域/代理缓冲**：dev 用 vite 代理 `/iot`→8080，SSE 需代理不缓冲；后端 Week 35 已设 `X-Accel-Buffering: no`。vite 代理默认透传 SSE，无需额外配置。
11. ⚠️ **ECharts 内存泄漏**：`wm/report` 未在 `onUnmounted` dispose。本页务必 `chart.dispose()` + 移除 resize 监听 + `eventSource.close()`，避免切页泄漏。
12. ⚠️ **清理任务大表删除**：90 天前数据量大时 `DELETE` 锁表。第一版全量删（数据量小）；阶段二 TDengine 迁移后此任务失效。如需限流可分批删，本周不做。
13. ⚠️ **心跳 15min 与 status TTL 1h 不一致**：status 缓存 TTL 1h，心跳 15min 置离线会先于缓存过期纠正异常掉线设备——这是期望行为（心跳纠偏比缓存过期更快）。心跳任务置离线时 `markOffline` 会删 status key，一致。

**遗留（阶段二按需）：**
OPC UA/Modbus、规则引擎、告警规则表、TDengine、边缘网关、场景联动、MES 深度联动——均按文档第八节/第九节触发条件驱动，不在阶段一。

---

## 5. 验收标准（本周完成定义 / 阶段一收口）

1. `mvn -pl ruoyi-modules/ruoyi-iot -am compile` 通过；`eslint src/views/iot src/api/iot` 通过。
2. **三任务**：三个 `@JobExecutor` 类编译注册；后台「执行一次」清理/心跳/Stream 修剪均成功（清理删行数>0 或 0 都算成功；心跳把超时设备置 OFFLINE；`XLEN iot:data:stream ≤ 100000`）。
3. **实时仪表盘**：设备列表点「实时」→ 仪表盘页打开；`mosquitto_pub` 发属性 → 数值卡片 3s 内更新 + 折线图实时追加；切页后 EventSource/echarts 正确释放（无控制台报错/内存增长）。
4. **历史趋势**：仪表盘历史图拉 `/data` 近 1h 数据渲染。
5. **端到端**：一台（模拟或真实）MQTT 设备上报 → 后端落库 → 仪表盘实时可见（文档 19.1「真实设备联调」项 ✅）。
6. **阶段一验收报告**：19.1 全 11 项逐项验证结果记录在 `NexusERP_IoT阶段一验收报告.md`。
7. 文档检查清单本周项 + 阶段一全部 ✅；阶段二按需项保持 ❌。
