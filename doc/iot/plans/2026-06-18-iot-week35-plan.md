# NexusERP — IoT 阶段一 · 第三周（Week 35）开发计划

> 关联文档：`doc/iot/NexusERP_IoT文档.md`（第十八节 18.1 Week 35-36）
> 上周计划：`script/superpowers/plans/2026-06-23-iot-week34-plan.md`
> 计划制定：2026-06-18（执行周：Week 35）
> 工作量基准：3 人天
> 范围边界：**后端实时数据暴露 + SSE 实时端点 + 前端产品/设备 CRUD 页**。ECharts 实时仪表盘、SnailJob 定时任务、真实设备端到端联调属 Week 36。

---

## 0. Context（为什么做这个）

Week 33-34 已打通**后端全链路**：设备 MQTT 上报 → Redis Stream → `iot_device_data` 落库 + `iot:latest`/`iot:status` 缓存 + 硬编码告警 SSE 推送。但数据对用户**仍不可见**：

- `IotDeviceDataQueryService` 的 `getLatestData`/`getOnlineStatus`/`getHistoryData` 仅有 Service 接口实现，**未暴露 REST 端点**（仅预留给 MES 跨模块调用）。
- `realtime/` 包只有 `package-info.java` 占位，**SSE 实时端点 `/iot/device/{deviceId}/realtime` 未实现**。
- 前端 **`plus-ui/src/api/iot`、`plus-ui/src/views/iot` 目录均不存在**；Week 33 已埋菜单（`sys_menu` 6000-6025，组件路径 `iot/product/index`、`iot/device/index`），但点击菜单无页面渲染。
- IoT 字段用 `ACTIVE/INACTIVE`、`ONLINE/OFFLINE`、`MQTT/OPC_UA` 等字符串，与系统字典 `sys_normal_disable`(0/1) 不兼容，**字典类型未建**，前端 dict-tag 无法渲染。

Week 35 目标：**让产品/设备管理在浏览器可用 + 后端实时数据可查/可订阅**，为 Week 36 的 ECharts 仪表盘和联调铺路。

**不在本周范围**（属 Week 36）：ECharts 实时仪表盘页面、历史趋势图、SnailJob 三任务（清理/心跳/Stream 修剪）、真实设备端到端联调。

---

## 1. 前置对齐决策（已自查确定，无需你确认）

| # | 决策点 | 结论 | 理由 |
|---|---|---|---|
| A | **Week 35/36 切分** | 35=数据端点+SSE端点+产品/设备CRUD页+字典SQL；36=ECharts仪表盘+SnailJob+联调 | 文档将 35-36 合并为 6 人天，按 3+3 拆。CRUD 页是已成熟模式，先落地；仪表盘最重，留 36 专做 |
| B | **SSE 实时推送方案** | **Option A：专用 per-device 端点** `GET /iot/device/{deviceId}/realtime`，新建 `IotRealtimeSseManager` 按 deviceId 管理独立 SseEmitter，`@Scheduled(fixedRate=3000)` 轮询 `iot:latest`+`iot:status` 推送 | 文档 5.2 明确该端点；per-device 连接隔离、多租户在 connect 时校验、前端一卡一连接简单。复用全局 `/resource/sse` 通道（Option B）需"谁在看哪台设备"注册表，耦合告警通道，第一版不做 |
| C | **推送触发方式** | 定时轮询缓存（3s）而非事件驱动 | StreamConsumer 已写缓存，轮询实现简、无侵入；事件驱动需在 Consumer 注入 Manager + 订阅注册表，留待 Week 36 优化 |
| D | **EventSource 鉴权** | 端点 `@SaIgnore` + 手动 `StpUtil.isLogin()`/`StpUtil.checkPermission("iot:device:query")`，token 走 query 参数 | 浏览器 EventSource 不能设请求头，与 `SseController.connect()`（`ruoyi-common-sse`）同模式；Sa-Token 默认从 query 读 `Authorization` |
| E | **数据端点归属** | 加到现有 `IotDeviceController`（`/iot/device`），不新建 Controller | latest/status/data 是设备子资源，REST 惯例；权限复用 `iot:device:query` |
| F | **字典方案** | 新建 4 个 IoT 专用字典类型，`INSERT IGNORE` 幂等 | 后端 status 用 `ACTIVE/INACTIVE` 字符串，与 `sys_normal_disable`(0/1) 不兼容；新建字典零后端改动 |
| G | **菜单** | 不产出新菜单 SQL | Week 33 `ry-iot-week33-menu.sql` 已建 6000-6025（含 `iot:product:*`/`iot:device:*` 按钮权限 + 组件路径），本周前端直接对接 |
| H | **iot_device_data 租户隔离** | 每个数据端点先 `iotDeviceService.selectById(deviceId)` 校验设备归属 | `IotDeviceData` 不继承 `TenantEntity`，MyBatis-Plus 租户插件不过滤其查询；`IotDevice` 继承 `TenantEntity`，selectById 带租户过滤，返回 null 即跨租户/不存在 |

---

## 2. 任务分解（按执行顺序）

### T1 · 设备数据 REST 端点（latest / status / history）（0.5d）
**改造** `ruoyi-modules/ruoyi-iot/.../device/controller/IotDeviceController.java`：
- 注入 `IotDeviceLatestCache latestCache`、`IotDeviceStatusCache statusCache`、`IIotDeviceDataService deviceDataService`（构造器由 `@RequiredArgsConstructor` 自动接入）。
- 新增 3 个端点（均 `@SaCheckPermission("iot:device:query")`，调用前先 `selectById` 校验归属，null 则 `R.fail`）：
  1. `GET /iot/device/{deviceId}/latest` → `R<Map<String,String>>`，包装 `latestCache.getAll(deviceId)`。
  2. `GET /iot/device/{deviceId}/status` → `R<Map<String,String>>`，返回 `{"connectionStatus": statusCache.get(deviceId)}`。
  3. `GET /iot/device/{deviceId}/data` → `TableDataInfo<IotDeviceData>`，参数 `start`/`end`（`@DateTimeFormat("yyyy-MM-dd HH:mm:ss")`）、`limit`（默认 1000）；默认窗口近 24h；调用 `deviceDataService.selectHistory(deviceId, start, end)` 后内存截断 `limit` 条，用 `getDataTable(list)` 包装。
- **验收**：Swagger 见 3 端点；`mosquitto_pub` 发数后 `GET /latest` 返回最新属性、`GET /data` 返回历史行。

### T2 · SSE 实时端点 + IotRealtimeSseManager（0.7d）— 本周核心
**新建** `ruoyi-modules/ruoyi-iot/.../realtime/IotRealtimeSseManager.java`（`@Component`）：
- 字段 `Map<Long, Map<String, SseEmitter>> deviceEmitters`（ConcurrentHashMap，外键 deviceId，内键 `userId:uuid` 会话键）。
- `SseEmitter connect(Long deviceId, String sessionKey)`：创建 `new SseEmitter(0L)`（不超时）或与全局一致 86400000L；注册 `onCompletion/onTimeout/onError` 移除会话键；`computeIfAbsent` 入表。
- `@Scheduled(fixedRate = 3000) pushRealtimeData()`：遍历 `deviceEmitters` 每项，读 `latestCache.getAll(deviceId)` + `statusCache.get(deviceId)`，组装 payload：
  ```json
  {"deviceId":123,"timestamp":<now>,"connectionStatus":"ONLINE","properties":{"temperature":"25.3"}}
  ```
  `emitter.send(SseEmitter.event().name("realtime").data(json))`；IOException 则移除该会话键（坏连接 3s 内自愈）。无订阅者时直接跳过。
- **改造** `IotConfiguration.java`：类上加 `@EnableScheduling`，新增 `TaskScheduler` Bean（`ThreadPoolTaskScheduler` poolSize=2、`iot-sched-` 前缀），避免占用全局调度池。
- **改造** `IotDeviceController.java` 新增端点：
  ```java
  @SaIgnore
  @GetMapping(value = "/{deviceId}/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter realtime(@PathVariable Long deviceId, HttpServletResponse response) {
      if (!StpUtil.isLogin()) return null;
      StpUtil.checkPermission("iot:device:query");
      if (iotDeviceService.selectById(deviceId) == null) return null;   // 租户/归属校验
      response.setHeader("X-Accel-Buffering", "no");                    // 关闭 nginx 缓冲
      String sessionKey = LoginHelper.getUserId() + ":" + IdUtil.fastSimpleUUID();
      return realtimeSseManager.connect(deviceId, sessionKey);
  }
  ```
  注入 `IotRealtimeSseManager`。
- **验收**：`curl -N -H "Authorization: Bearer <token>" "http://localhost:8080/iot/device/{id}/realtime"` 每 3s 收到 `event:realtime` 数据帧；`mosquitto_pub` 发新值后下一帧 properties 变化。

### T3 · 字典 SQL（0.2d）
**新建** `doc/iot/sql/iot-week35-03-dict.sql`（目标库 `ry-vue`，`INSERT IGNORE` 幂等）：
- `sys_dict_type` 4 条：`product_type`(6030)、`iot_protocol_type`(6031)、`iot_connection_status`(6032)、`iot_device_status`(6033)。
- `sys_dict_data`：`product_type`→DEVICE/GATEWAY；`iot_protocol_type`→MQTT/OPC_UA/MODBUS/HTTP；`iot_connection_status`→ONLINE(success)/OFFLINE(info)；`iot_device_status`→ACTIVE(primary)/INACTIVE(danger)。
- 不写菜单（Week 33 已建）。参照既有 `sys_dict_*` 插入列序。
- **验收**：执行后前端 `useDict('iot_connection_status')` 能取到字典。

### T4 · 前端产品管理页（0.6d）
参照 `plus-ui/src/api/erp/bd/material/{index.ts,types.ts}` + `views/erp/bd/material/index.vue` 模式。
- `plus-ui/src/api/iot/product/types.ts`：`ProductQuery extends PageQuery`、`ProductVO`、`ProductForm`，字段对齐 `IotProductVo`（productId/productCode/productName/productType/protocolType/description/status/remark/createTime）。
- `plus-ui/src/api/iot/product/index.ts`：`listProduct`/`getProduct`/`addProduct`/`updateProduct`/`delProduct`，URL `/iot/product/list|{id}|`（GET/POST/PUT/DELETE）。
- `plus-ui/src/views/iot/product/index.vue`（`<script setup lang=ts name="IotProduct">`）：`useDict('product_type','iot_protocol_type','iot_device_status')`；inline 搜索（productCode/productName/productType/protocolType/status）；`el-table` 列含 dict-tag；`el-dialog` 平铺表单（productCode/productName 必填，productType/protocolType/status 下拉，description/remark 文本域）；工具栏 `v-hasPermi=['iot:product:add'|...]`；`<pagination>`。
- **验收**：菜单「IoT平台→产品管理」可增删改查，dict-tag 正确显示中文。

### T5 · 前端设备列表页（0.6d）
- `plus-ui/src/api/iot/device/types.ts`：`DeviceQuery`/`DeviceVO`/`DeviceForm`，字段对齐 `IotDeviceVo`（deviceId/deviceCode/deviceName/productId/productName/protocolType/connectionStatus/lastOnlineTime/ipAddress/firmwareVersion/location/mesEquipmentId/status/remark/createTime）。
- `plus-ui/src/api/iot/device/index.ts`：`listDevice`/`getDevice`/`addDevice`/`updateDevice`/`delDevice`。
- `plus-ui/src/views/iot/device/index.vue`（`name="IotDevice"`）：`useDict('iot_protocol_type','iot_connection_status','iot_device_status')`；搜索（deviceCode/deviceName/protocolType/connectionStatus/status）；表格列含 **connectionStatus 指示灯**（`el-tag :type="row.connectionStatus==='ONLINE'?'success':'info'"`，绿=在线/灰=离线）、lastOnlineTime；新增/编辑 dialog：productId 下拉（onMounted 调 `listProduct({pageNum:1,pageSize:999})` 填 `productOptions`，必填）、deviceCode/deviceName 必填、protocolType/status 下拉、ipAddress/firmwareVersion/location/remark 选填；**不展示** deviceSecret/connectionStatus/lastOnlineTime（后端自动管理）。
- **验收**：菜单「IoT平台→设备管理」可增删改查；离线设备灰色 tag；关联产品下拉可选。

### T6 · 路由注册 + 集成自测 + 文档收尾（0.4d）
- **改造** `plus-ui/src/router/index.ts` 的 `dynamicRoutes` 追加两条（`hidden:true`，侧边栏由 `sys_menu` 驱动）：
  ```ts
  { path: '/iot/product/index', component: Layout, hidden: true, permissions: ['iot:product:list'],
    children: [{ path: '', component: () => import('@/views/iot/product/index.vue'),
      name: 'IotProduct', meta: { title: '产品管理', activeMenu: '/iot/product' } }] },
  { path: '/iot/device/index', component: Layout, hidden: true, permissions: ['iot:device:list'],
    children: [{ path: '', component: () => import('@/views/iot/device/index.vue'),
      name: 'IotDevice', meta: { title: '设备管理', activeMenu: '/iot/device' } }] },
  ```
- 联调顺序（依赖 ry-iot 库 + Week 33 DDL/菜单 + Week 35 字典已执行）：
  1. 执行 `ry-iot-week35.sql`（字典）。
  2. 启动后端 → Swagger 见 latest/status/data/realtime 4 端点；`iot-sched-*` 线程启动。
  3. `npm run dev` → 产品/设备 CRUD 全流程；dict-tag 中文显示；设备在线状态灰/绿。
  4. `mosquitto_pub` 发属性 → `GET /latest` 有值 → `curl` 订阅 realtime 每 3s 收到变化帧。
- 更新 `NexusERP_IoT文档.md` 第十四节检查清单：`SSE 实时数据端点` → ✅；新增「设备数据 REST 端点(latest/status/data)」✅、「前端产品/设备管理页」✅、「IoT 字典」✅；`SnailJob 定时任务`/`实时仪表盘`/`真实设备联调` 保持 ❌（Week 36）。
- **验收**：对照文档 19.1 本周可验收项（数据端点、SSE 推送、前端 CRUD）全部通过。

---

## 3. 交付物清单

| 类型 | 路径 |
|---|---|
| 后端数据端点 | `IotDeviceController.java`（+3 端点：latest/status/data） |
| SSE 实时 | `realtime/IotRealtimeSseManager.java`（新建）、`IotDeviceController.java`（+realtime 端点）、`config/IotConfiguration.java`（+`@EnableScheduling`+TaskScheduler Bean） |
| 字典 | `doc/iot/sql/iot-week35-03-dict.sql`（新建，4 字典类型 + 字典数据） |
| 前端产品页 | `plus-ui/src/api/iot/product/{index.ts,types.ts}`、`plus-ui/src/views/iot/product/index.vue` |
| 前端设备页 | `plus-ui/src/api/iot/device/{index.ts,types.ts}`、`plus-ui/src/views/iot/device/index.vue` |
| 路由 | `plus-ui/src/router/index.ts`（+2 dynamicRoutes） |
| 文档 | `doc/iot/NexusERP_IoT文档.md`（检查清单状态） |

---

## 4. 自审查记录

**与 Week 33-34 代码衔接核对：**
1. ✅ `IotDeviceController` 现 6 端点（list/export/get/add/edit/remove）→ T1/T2 增量加 4 端点不破坏现有，权限复用 `iot:device:query`。
2. ✅ `IotDeviceLatestCache.getAll` / `IotDeviceStatusCache.get` / `IIotDeviceDataService.selectHistory` 均已实现（Week 34），直接包装即可，无需新写查询。
3. ✅ Week 33 菜单 SQL 已埋 `iot/product/index`、`iot/device/index` 组件路径与 `iot:product:*`/`iot:device:*` 权限 → 前端路由 `name`/`activeMenu` 与菜单 path 对齐即可点亮。
4. ✅ `IotConfiguration` 现仅注册 no-op Bean → 加 `@EnableScheduling` + TaskScheduler 不影响 MES 让位逻辑。
5. ✅ ECharts 6.0.0 已是 `plus-ui` 依赖 → Week 36 仪表盘无需新增依赖。

**风险识别与处理：**
6. ⚠️ **EventSource 不能设请求头**：token 走 query 参数。已与 `SseController`（`ruoyi-common-sse`）同模式，Sa-Token 默认读 query `Authorization`。若 prod 关闭 query token 需额外放开该端点。
7. ⚠️ **iot_device_data 跨租户泄露**：实体不继承 TenantEntity。处理：每个数据端点先 `selectById` 校验（决策 H）。
8. ⚠️ **SseEmitter 内存泄漏**：连接未正常关闭时 emitter 堆积。处理：`onCompletion/onTimeout/onError` 回调移除 + 推送时 IOException 移除；3s 周期自愈。Week 36 心跳任务可补超时清理。
9. ⚠️ **轮询推送空数据**：无新数据仍每 3s 推全量 latest。第一版可接受（数据量小）；Week 36 可改事件驱动或仅推增量。
10. ⚠️ **字典 ID 段冲突**：用 6030-6033（`INSERT IGNORE` 幂等），避开 Week 33 菜单 6000-6025 段。

**遗留待 Week 36：**
- ECharts 实时仪表盘（订阅 `/realtime` + 历史趋势图查 `/data`）。
- SnailJob 三任务：`IotDataCleanupTask`（清 90 天）、`IotDeviceHeartbeatTask`（5min 超时置离线）、`IotStreamTrimTask`（每小时 XTRIM MAXLEN 100000）。
- 真实设备端到端联调。

---

## 5. 验收标准（本周完成定义）

1. `mvn -pl ruoyi-modules/ruoyi-iot -am compile` 通过；`npm run build:prod` 前端无报错（或 `npm run dev` 页面正常）。
2. Swagger 出现 `GET /iot/device/{id}/latest|status|data|realtime` 4 端点；启动日志见 `iot-sched-*` 线程，无 ERROR。
3. **数据端点**：`mosquitto_pub` 发属性后 `GET /latest` 返回最新值、`GET /status` 返回 ONLINE/OFFLINE、`GET /data?start=&end=` 返回历史行；跨租户 deviceId 返回 fail/null。
4. **SSE 实时**：`curl -N` 订阅 `/realtime` 每 3s 收到 `event:realtime` 帧；发新值后下一帧 properties 变化。
5. **前端产品管理**：菜单点亮，增删改查全流程，dict-tag 显示中文（产品类型/协议/状态）。
6. **前端设备管理**：列表含在线状态指示灯（绿/灰），新增设备可选产品，CRUD 正常。
7. **字典**：`ry-iot-week35.sql` 执行后 4 个 IoT 字典在前端 `useDict` 可用。
8. 文档检查清单本周项更新为 ✅；SnailJob/仪表盘/联调保持 ❌（Week 36）。
