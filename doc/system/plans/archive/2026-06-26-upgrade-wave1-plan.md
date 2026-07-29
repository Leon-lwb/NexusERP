# NexusERP 三模块共同升级方案 - 第一波实现计划

> **For agentic workers:** 本计划对应 `NexusERP_三模块共同升级方案.md` §三 第一波「集成联动深化」。沿用项目现有「实现 → 编译 → 功能验证 → 提交」流程（项目 `src/test/` 无任何单测代码，不引入 TDD）。步骤使用 checkbox `- [ ]` 语法跟踪。

**Goal:** 打通 ERP↔MES↔IoT 4 个预留集成点，实现三模块数据流闭环。

**Architecture:** 沿用现有契约模式（`IMesAndonService` / `IotDeviceDataQueryService` / `BaseMessageConsumer` / `ReliableMessageProducer`），不引入新耦合。MQ 消息走 TOPIC_EXCHANGE + afterCommit 投递；跨模块调用通过 `ruoyi-common-iot` 契约接口隔离；租户上下文由 MQ 消费者从 message.tenantId 恢复。

**Tech Stack:** Spring Boot 3.5 / MyBatis-Plus 3.5 / Sa-Token / Flowable 7 / RabbitMQ + Spring AMQP / Redis / SnailJob / Vue 3.5 + Element Plus + TS

---

## 任务依赖与执行顺序

```
Task 1 (3.1 MES↔IoT 设备软关联, 2人天) ──┐
                                         ├──► Task 2 (3.2 IoT 自动采集归档, 3人天)
Task 3 (3.3 SPC OOC→安灯, 2人天) ────────┘   (依赖 Task 1)
Task 4 (3.4 ERP 收货单驱动 IQC, 3人天) ─── 独立
```

**推荐执行顺序**：
- 第 1 天：并行启动 Task 1 + Task 3 + Task 4（三者无依赖）
- 第 2 天：完成 Task 1 + Task 3；Task 4 继续
- 第 3 天：Task 4 完成；启动 Task 2（依赖 Task 1）
- 第 4-5 天：Task 2 完成 + 联调验收

**总人天**: 10（不含联调缓冲，原方案估算 12）

---

## 文件结构总览

### 后端（Java）

```
ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/
├── constant/
│   ├── QueueConstants.java              (修改: +MES_IQC_CREATE_QUEUE 常量)
│   └── RoutingKeyConstants.java         (修改: +MES_IQC_CREATE 路由键)
└── message/
    └── ReceiveApprovedMessage.java      (新建: ERP 收货审批通过消息 DTO)

ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/
├── wm/service/impl/
│   └── WmMaterialReceiveCrudServiceImpl.java  (修改: afterFlowHandle 加 afterCommit 发 MQ)
└── sql/  (项目外 doc/erp/sql/)
    └── (无需新增 SQL)

ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/
├── eq/service/
│   ├── IEqEquipmentService.java         (修改: +getIotStatus/getIotLatest 辅助方法)
│   └── impl/EqEquipmentServiceImpl.java (修改: 注入 IotDeviceDataQueryService)
├── common/mq/consumer/
│   └── IqcAutoCreateConsumer.java       (新建: 消费 ReceiveApprovedMessage 自动建 IQC)
├── spc/
│   ├── service/ISpcControlChartService.java  (修改: 抽象 RULE1-4 常量)
│   └── service/impl/SpcControlChartServiceImpl.java  (修改: analyze 加 OOC→安灯联动 + 限频)
├── dc/task/
│   └── IotDataArchiveTask.java          (新建: SnailJob 定时拉 IoT 数据写 dc_data_record)
└── sql/  (项目外 doc/mes/sql/)
    └── mes-week28-23-iot-bridge.sql     (新建: dc_collection_point 加 iot_device_id 列)
```

### 前端（Vue）

```
plus-ui/src/
├── api/mes/eq/equipment.ts        (修改: +iotDeviceId 字段, +iotStatus/iotLatest 接口)
├── api/iot/device/index.ts        (修改: +mesEquipmentId 关联接口)
├── api/iot/device/types.ts        (修改: +mesEquipmentId 字段)
└── views/
    ├── mes/eq/equipment/
    │   ├── index.vue              (修改: 加 IoT 在线状态列 + IoT 数据按钮 + 关联设置对话框)
    │   └── form.vue               (新建: 设备编辑表单含 iotDeviceId 选择器)
    └── iot/device/
        └── index.vue              (修改: 加 MES 设备链接 + 关联设置入口)
```

### SQL 脚本

```
doc/mes/sql/archive/mes-week28-23-iot-bridge.sql   (新建)
doc/system/plans/2026-06-26-upgrade-wave1-plan.md  (本计划)
```

---

## Task 1: MES 设备 ↔ IoT 设备软关联打通

**目标**: MES 设备台账显示 IoT 在线状态 + IoT 设备页有 MES 设备链接，双向关联可设置。

**人天**: 2

**前置条件**:
- `EqEquipment.iotDeviceId` 字段已存在（`EqEquipment.java:68-69`）
- `IotDevice.mesEquipmentId` 字段已存在（`IotDevice.java:87-88`）
- `IotDeviceDataQueryService.getOnlineStatus(deviceId)` 已实现（`IotDeviceDataQueryServiceImpl`，走 Redis 缓存）

### Task 1.1 后端：MES 设备 Service 增加 IoT 状态查询辅助方法

**Files:**
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\eq\service\IEqEquipmentService.java`
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\eq\service\impl\EqEquipmentServiceImpl.java`
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\eq\controller\EqEquipmentController.java`

- [ ] **Step 1: 在 IEqEquipmentService 接口添加 IoT 状态查询方法签名**

打开 `IEqEquipmentService.java`，在最后一个方法后（约第 60 行 `updateStatus` 之后）追加：

```java
    /**
     * 查询设备关联的 IoT 在线状态
     * @param equipmentId MES 设备 ID
     * @return ONLINE / OFFLINE / UNKNOWN / UNBOUND
     */
    String getIotStatus(Long equipmentId);

    /**
     * 查询设备关联的 IoT 最新数据
     * @param equipmentId MES 设备 ID
     * @return Map<propertyCode, value>，未关联返回空 Map
     */
    Map<String, String> getIotLatest(Long equipmentId);
```

并在文件顶部追加 import（如已有则跳过）：

```java
import java.util.Map;
```

- [ ] **Step 2: 在 EqEquipmentServiceImpl 实现两个方法**

打开 `EqEquipmentServiceImpl.java`，在类字段区追加依赖注入（参考现有 `@RequiredArgsConstructor` 模式，若已使用 final 字段自动注入则只补字段）：

```java
    private final IotDeviceDataQueryService iotDeviceDataQueryService;
```

顶部补 import：

```java
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import java.util.Collections;
import java.util.Map;
```

在类末尾追加方法实现：

```java
    private static final String IOT_STATUS_UNBOUND = "UNBOUND";
    private static final String IOT_STATUS_UNKNOWN = "UNKNOWN";

    @Override
    public String getIotStatus(Long equipmentId) {
        EqEquipment eq = baseMapper.selectById(equipmentId);
        if (eq == null || eq.getIotDeviceId() == null) {
            return IOT_STATUS_UNBOUND;
        }
        try {
            String status = iotDeviceDataQueryService.getOnlineStatus(eq.getIotDeviceId());
            return status == null ? IOT_STATUS_UNKNOWN : status;
        } catch (Exception e) {
            log.warn("查询 IoT 设备在线状态失败: equipmentId={}, iotDeviceId={}",
                equipmentId, eq.getIotDeviceId(), e);
            return IOT_STATUS_UNKNOWN;
        }
    }

    @Override
    public Map<String, String> getIotLatest(Long equipmentId) {
        EqEquipment eq = baseMapper.selectById(equipmentId);
        if (eq == null || eq.getIotDeviceId() == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> data = iotDeviceDataQueryService.getLatestData(eq.getIotDeviceId());
            return data == null ? Collections.emptyMap() : data;
        } catch (Exception e) {
            log.warn("查询 IoT 设备最新数据失败: equipmentId={}, iotDeviceId={}",
                equipmentId, eq.getIotDeviceId(), e);
            return Collections.emptyMap();
        }
    }
```

> 注：`IotDeviceDataQueryService` 在 `ruoyi-common-iot` 模块，`ruoyi-mes` 已依赖该模块（IoT→MES 安灯联动 `MesAndonServiceImpl` 已使用），无需新增 pom 依赖。

- [ ] **Step 3: 在 EqEquipmentController 增加两个 GET 端点**

打开 `EqEquipmentController.java`，在最后一个 `@GetMapping` 后追加：

```java
    /**
     * 查询设备关联的 IoT 在线状态
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/iotStatus")
    public R<String> getIotStatus(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.getIotStatus(equipmentId));
    }

    /**
     * 查询设备关联的 IoT 最新数据
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/iotLatest")
    public R<Map<String, String>> getIotLatest(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.getIotLatest(equipmentId));
    }
```

顶部 import 如缺则补：

```java
import java.util.Map;
```

- [ ] **Step 4: 编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 后端提交**

```bash
cd d:\workspace\nexus-erp
git add ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/IEqEquipmentService.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/impl/EqEquipmentServiceImpl.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/controller/EqEquipmentController.java
git commit -m "feat(mes-eq): 设备 Service 增加 IoT 状态/最新数据查询辅助方法 (Wave1-3.1)"
```

### Task 1.2 前端：MES 设备台账 index.vue 增加 IoT 在线状态列 + IoT 数据按钮

**Files:**
- Modify: `d:\workspace\nexus-erp\plus-ui\src\api\mes\eq\equipment.ts`
- Modify: `d:\workspace\nexus-erp\plus-ui\src\views\mes\eq\equipment\index.vue`

- [ ] **Step 1: 在 equipment.ts 追加 IoT 查询接口**

打开 `d:\workspace\nexus-erp\plus-ui\src\api\mes\eq\equipment.ts`，在文件末尾追加：

```typescript
/** 查询 MES 设备关联的 IoT 在线状态 */
export function getIotStatus(equipmentId: number | string) {
  return request({
    url: '/mes/eq/equipment/' + equipmentId + '/iotStatus',
    method: 'get'
  });
}

/** 查询 MES 设备关联的 IoT 最新数据 */
export function getIotLatest(equipmentId: number | string) {
  return request({
    url: '/mes/eq/equipment/' + equipmentId + '/iotLatest',
    method: 'get'
  });
}
```

- [ ] **Step 2: 在 index.vue 表格列增加「IoT 在线状态」列**

打开 `d:\workspace\nexus-erp\plus-ui\src\views\mes\eq\equipment\index.vue`。

在 `<el-table-column>` 列定义中（找到现有列定义位置，在「状态」列之前插入新列）：

```vue
<el-table-column label="IoT 在线" align="center" width="100">
  <template #default="scope">
    <el-tag v-if="scope.row.iotStatus === 'ONLINE'" type="success" size="small">在线</el-tag>
    <el-tag v-else-if="scope.row.iotStatus === 'OFFLINE'" type="info" size="small">离线</el-tag>
    <el-tag v-else-if="scope.row.iotStatus === 'UNBOUND'" type="warning" size="small">未关联</el-tag>
    <el-tag v-else type="danger" size="small">未知</el-tag>
  </template>
</el-table-column>
```

- [ ] **Step 3: 在 index.vue 增加「IoT 最新数据」按钮和对话框**

在操作列追加按钮（找到操作列的 `<template #default="scope">` 内追加）：

```vue
<el-button v-if="scope.row.iotDeviceId" link type="primary" icon="View" @click="handleViewIotLatest(scope.row)">IoT数据</el-button>
<el-button link type="primary" icon="Link" @click="handleBindIot(scope.row)">关联IoT</el-button>
```

在模板末尾追加 IoT 数据查看对话框：

```vue
<!-- IoT 最新数据对话框 -->
<el-dialog v-model="iotLatestDialog.visible" title="IoT 设备最新数据" width="600px">
  <el-descriptions :column="2" border>
    <el-descriptions-item v-for="(val, key) in iotLatestDialog.data" :key="key" :label="String(key)">
      {{ val }}
    </el-descriptions-item>
  </el-descriptions>
  <el-empty v-if="Object.keys(iotLatestDialog.data).length === 0" description="暂无数据" />
</el-dialog>
```

在 `<script setup lang="ts">` 中追加（参考现有 import 模式，补 `getIotStatus, getIotLatest` 到 import from '@/api/mes/eq/equipment'）：

```typescript
import { getIotStatus, getIotLatest } from '@/api/mes/eq/equipment';

/** IoT 最新数据对话框 */
const iotLatestDialog = reactive<{ visible: boolean; data: Record<string, string> }>({
  visible: false,
  data: {}
});

/** 列表加载后批量查 IoT 状态（限制并发，仅当前页） */
async function loadIotStatus(rows: EquipmentVO[]) {
  for (const row of rows) {
    if (row.iotDeviceId) {
      try {
        const res = await getIotStatus(row.equipmentId);
        row.iotStatus = res.data;
      } catch {
        row.iotStatus = 'UNKNOWN';
      }
    } else {
      row.iotDeviceId = undefined;
      row.iotStatus = 'UNBOUND';
    }
  }
}
```

在列表查询回调（`getList` 函数末尾的 `loading.value = false;` 之前）追加：

```typescript
  await loadIotStatus(list.value);
```

`EquipmentVO` 类型需补 `iotStatus?` 字段（在 types 中）：

```typescript
// 在 equipment.ts 的 EquipmentVO 类型定义中追加
iotStatus?: string;  // 'ONLINE' | 'OFFLINE' | 'UNBOUND' | 'UNKNOWN'
```

在 Vue script 中追加查看 IoT 数据的方法：

```typescript
/** 查看 IoT 最新数据 */
async function handleViewIotLatest(row: EquipmentVO) {
  try {
    const res = await getIotLatest(row.equipmentId);
    iotLatestDialog.data = res.data || {};
    iotLatestDialog.visible = true;
  } catch (e) {
    proxy.$modal.msgError('查询 IoT 数据失败');
  }
}
```

- [ ] **Step 4: 前端编译验证**

```bash
cd d:\workspace\nexus-erp\plus-ui
npm run build:prod 2>&1 | head -50
```

Expected: 无 TypeScript 报错，build success。

- [ ] **Step 5: 前端提交**

```bash
cd d:\workspace\nexus-erp
git add plus-ui/src/api/mes/eq/equipment.ts plus-ui/src/views/mes/eq/equipment/index.vue
git commit -m "feat(mes-eq-ui): 设备台账加 IoT 在线状态列 + IoT 最新数据查看 (Wave1-3.1)"
```

### Task 1.3 前端：双向关联设置对话框

**Files:**
- Modify: `d:\workspace\nexus-erp\plus-ui\src\views\mes\eq\equipment\index.vue`
- Modify: `d:\workspace\nexus-erp\plus-ui\src\api\mes\eq\equipment.ts`
- Modify: `d:\workspace\nexus-erp\plus-ui\src\api\iot\device\index.ts`
- Modify: `d:\workspace\nexus-erp\plus-ui\src\api\iot\device\types.ts`
- Modify: `d:\workspace\nexus-erp\plus-ui\src\views\iot\device\index.vue`

- [ ] **Step 1: equipment.ts 增加「更新 IoT 关联」接口**

在 `equipment.ts` 末尾追加：

```typescript
/** 更新 MES 设备的 IoT 关联 */
export function updateIotDeviceLink(equipmentId: number | string, iotDeviceId: number | null) {
  return request({
    url: '/mes/eq/equipment/' + equipmentId + '/iotLink',
    method: 'put',
    data: { iotDeviceId }
  });
}
```

- [ ] **Step 2: MES EqEquipmentController 增加 iotLink 端点**

打开 `EqEquipmentController.java`，追加：

```java
    /**
     * 更新设备关联的 IoT 设备
     */
    @SaCheckPermission("mes:eq:equipment:edit")
    @PutMapping("/{equipmentId}/iotLink")
    public R<Void> updateIotLink(@PathVariable Long equipmentId,
                                 @RequestBody IotLinkDto dto) {
        EqEquipmentBo bo = new EqEquipmentBo();
        bo.setEquipmentId(equipmentId);
        bo.setIotDeviceId(dto.getIotDeviceId());
        return toAjax(eqEquipmentService.updateByBo(bo));
    }

    @Data
    public static class IotLinkDto {
        private Long iotDeviceId;
    }
```

import 补：

```java
import org.springframework.web.bind.annotation.RequestBody;
import lombok.Data;
```

> 说明：复用现有 `updateByBo`，仅更新 iotDeviceId 字段；MyBatis-Plus 默认非空更新，但其他字段从 bo 读取为 null 时会被忽略，安全。如担心覆盖其他字段，可新增 `updateIotDeviceLink(equipmentId, iotDeviceId)` 专用方法（用 LambdaUpdateWrapper 仅更新单列）。

- [ ] **Step 3: MES 设备台账 index.vue 增加「关联 IoT」对话框**

在 `<el-table-column label="操作">` 后追加 IoT 选择对话框：

```vue
<!-- 关联 IoT 设备对话框 -->
<el-dialog v-model="iotBindDialog.visible" title="关联 IoT 设备" width="500px">
  <el-form label-width="120px">
    <el-form-item label="MES 设备">
      <span>{{ iotBindDialog.equipmentCode }} - {{ iotBindDialog.equipmentName }}</span>
    </el-form-item>
    <el-form-item label="IoT 设备">
      <el-select v-model="iotBindDialog.iotDeviceId" placeholder="请选择 IoT 设备" clearable filterable>
        <el-option v-for="d in iotBindDialog.iotDeviceList" :key="d.deviceId"
                   :label="d.deviceCode + ' - ' + d.deviceName" :value="d.deviceId" />
      </el-select>
    </el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="iotBindDialog.visible = false">取消</el-button>
    <el-button type="primary" @click="submitIotBind">确定</el-button>
  </template>
</el-dialog>
```

在 `<script setup>` 中追加（IoT 设备列表 API 后续在 iot device index.ts 增加）：

```typescript
import { listIotDeviceForBind } from '@/api/iot/device';

/** 关联 IoT 设备对话框 */
const iotBindDialog = reactive<{
  visible: boolean;
  equipmentId: number | null;
  equipmentCode: string;
  equipmentName: string;
  iotDeviceId: number | null | undefined;
  iotDeviceList: any[];
}>({
  visible: false,
  equipmentId: null,
  equipmentCode: '',
  equipmentName: '',
  iotDeviceId: null,
  iotDeviceList: []
});

/** 打开关联对话框 */
async function handleBindIot(row: EquipmentVO) {
  iotBindDialog.equipmentId = row.equipmentId;
  iotBindDialog.equipmentCode = row.equipmentCode;
  iotBindDialog.equipmentName = row.equipmentName;
  iotBindDialog.iotDeviceId = row.iotDeviceId || null;
  try {
    const res = await listIotDeviceForBind({ pageSize: 1000 });
    iotBindDialog.iotDeviceList = res.rows || [];
  } catch (e) {
    proxy.$modal.msgError('加载 IoT 设备列表失败');
    iotBindDialog.iotDeviceList = [];
  }
  iotBindDialog.visible = true;
}

/** 提交关联 */
async function submitIotBind() {
  if (!iotBindDialog.equipmentId) return;
  try {
    await updateIotDeviceLink(iotBindDialog.equipmentId, iotBindDialog.iotDeviceId ?? null);
    proxy.$modal.msgSuccess('关联成功');
    iotBindDialog.visible = false;
    await getList();
  } catch (e) {
    proxy.$modal.msgError('关联失败');
  }
}
```

import 补 `updateIotDeviceLink`：

```typescript
import { getIotStatus, getIotLatest, updateIotDeviceLink } from '@/api/mes/eq/equipment';
```

- [ ] **Step 4: IoT 设备 API 增加「供 MES 绑定用的列表」接口**

打开 `d:\workspace\nexus-erp\plus-ui\src\api\iot\device\index.ts`，在文件末尾追加：

```typescript
/** 供 MES 设备关联选择用的 IoT 设备列表（精简字段） */
export function listIotDeviceForBind(query?: any) {
  return request({
    url: '/iot/device/list',
    method: 'get',
    params: query
  });
}
```

> 复用已有 `/iot/device/list` 端点（`IotDeviceController` 已有 list）。

- [ ] **Step 5: IoT 设备 index.vue 增加「MES 设备」链接列 + 关联入口**

打开 `d:\workspace\nexus-erp\plus-ui\src\views\iot\device\index.vue`，在表格列中追加：

```vue
<el-table-column label="MES 设备" align="center" width="120">
  <template #default="scope">
    <el-button v-if="scope.row.mesEquipmentId" link type="primary"
               @click="goToMesEquipment(scope.row.mesEquipmentId)">
      查看 MES 设备
    </el-button>
    <span v-else>-</span>
  </template>
</el-table-column>
```

在操作列追加「关联 MES 设备」按钮（在现有按钮后）：

```vue
<el-button link type="primary" icon="Link" @click="handleBindMes(scope.row)">关联MES</el-button>
```

在 `<script setup>` 中追加：

```typescript
import { useRouter } from 'vue-router';
const router = useRouter();

function goToMesEquipment(mesEquipmentId: number) {
  router.push({ path: '/mes/eq/equipment', query: { equipmentId: mesEquipmentId } });
}

/** 关联 MES 设备对话框 */
const mesBindDialog = reactive<{ visible: boolean; deviceId: number | null; mesEquipmentId: number | null | undefined; mesEquipmentList: any[] }>({
  visible: false,
  deviceId: null,
  mesEquipmentId: null,
  mesEquipmentList: []
});

async function handleBindMes(row: any) {
  mesBindDialog.deviceId = row.deviceId;
  mesBindDialog.mesEquipmentId = row.mesEquipmentId || null;
  try {
    const res = await listMesEquipmentForBind({ pageSize: 1000 });
    mesBindDialog.mesEquipmentList = res.rows || [];
  } catch {
    mesBindDialog.mesEquipmentList = [];
  }
  mesBindDialog.visible = true;
}

async function submitMesBind() {
  if (!mesBindDialog.deviceId) return;
  try {
    await updateMesEquipmentLink(mesBindDialog.deviceId, mesBindDialog.mesEquipmentId ?? null);
    proxy.$modal.msgSuccess('关联成功');
    mesBindDialog.visible = false;
    await getList();
  } catch {
    proxy.$modal.msgError('关联失败');
  }
}
```

模板追加 MES 关联对话框：

```vue
<el-dialog v-model="mesBindDialog.visible" title="关联 MES 设备" width="500px">
  <el-form label-width="120px">
    <el-form-item label="MES 设备">
      <el-select v-model="mesBindDialog.mesEquipmentId" placeholder="请选择 MES 设备" clearable filterable>
        <el-option v-for="e in mesBindDialog.mesEquipmentList" :key="e.equipmentId"
                   :label="e.equipmentCode + ' - ' + e.equipmentName" :value="e.equipmentId" />
      </el-select>
    </el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="mesBindDialog.visible = false">取消</el-button>
    <el-button type="primary" @click="submitMesBind">确定</el-button>
  </template>
</el-dialog>
```

import 补：

```typescript
import { listMesEquipmentForBind, updateMesEquipmentLink } from '@/api/mes/eq/equipment';
```

- [ ] **Step 6: equipment.ts 追加供 IoT 侧使用的 MES 设备接口**

在 `equipment.ts` 末尾追加：

```typescript
/** 供 IoT 设备关联选择用的 MES 设备列表 */
export function listMesEquipmentForBind(query?: any) {
  return request({
    url: '/mes/eq/equipment/list',
    method: 'get',
    params: query
  });
}

/** 更新 IoT 设备关联的 MES 设备（直接调 IoT 侧接口） */
export function updateMesEquipmentLink(deviceId: number | string, mesEquipmentId: number | null) {
  return request({
    url: '/iot/device/' + deviceId + '/mesLink',
    method: 'put',
    data: { mesEquipmentId }
  });
}
```

- [ ] **Step 7: IoT 设备 Controller 增加 mesLink 端点**

打开 `IotDeviceController.java`，追加：

```java
    @SaCheckPermission("iot:device:edit")
    @PutMapping("/{deviceId}/mesLink")
    public R<Void> updateMesLink(@PathVariable Long deviceId,
                                  @RequestBody MesLinkDto dto) {
        IotDeviceBo bo = new IotDeviceBo();
        bo.setId(deviceId);
        bo.setMesEquipmentId(dto.getMesEquipmentId());
        return toAjax(iotDeviceService.updateByBo(bo) > 0);
    }

    @Data
    public static class MesLinkDto {
        private Long mesEquipmentId;
    }
```

import 补 `@RequestBody`、`lombok.Data`。`IotDeviceBo` 应已有 `mesEquipmentId` 字段（与 Entity 对齐），如无则同步补。

- [ ] **Step 8: 前端编译验证**

```bash
cd d:\workspace\nexus-erp\plus-ui
npm run build:prod 2>&1 | head -50
```

Expected: 编译成功无错误。

- [ ] **Step 9: 后端编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-modules/ruoyi-iot,ruoyi-modules/ruoyi-mes -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 10: 提交 Task 1.3**

```bash
cd d:\workspace\nexus-erp
git add plus-ui/src/api/mes/eq/equipment.ts plus-ui/src/api/iot/device/index.ts plus-ui/src/views/mes/eq/equipment/index.vue plus-ui/src/views/iot/device/index.vue ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/controller/EqEquipmentController.java ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/device/controller/IotDeviceController.java
git commit -m "feat(mes-iot-bridge): 设备双向关联设置 (Wave1-3.1)"
```

### Task 1 验收标准

- [ ] **功能验收 1**: MES 设备台账列表，已关联 IoT 设备的行显示「在线/离线」标签，未关联的显示「未关联」
- [ ] **功能验收 2**: 点击「IoT 数据」按钮弹出 IoT 最新数据对话框
- [ ] **功能验收 3**: 点击「关联 IoT」按钮，弹出选择对话框，选择后保存成功，列表刷新
- [ ] **功能验收 4**: IoT 设备列表，已关联 MES 设备的行显示「查看 MES 设备」按钮，点击跳转
- [ ] **功能验收 5**: IoT 设备点击「关联 MES」按钮，选择后保存成功

---

## Task 2: IoT 自动采集归档到 dc_data_record

**目标**: AUTO 采集点定时（5 分钟）拉取 IoT 历史数据，去重后写入 MES `dc_data_record`（source=IoT），供 SPC 消费。

**人天**: 3

**依赖**: Task 1（AUTO 采集点通过 `iot_device_id` 关联 IoT 设备）

**关键设计决策**: 
- `dc_collection_point` 新增 `iot_device_id` 列 + `iot_property_code` 列（一个采集点对应一个 IoT 属性）
- `dc_data_record` 新增 `property_code` 列（可选，存 IoT propertyCode 便于追溯）
- 去重逻辑：以 `(point_id, collect_time)` 唯一索引兜底 + 任务内查询最近 collect_time 后数据

### Task 2.1 DDL：扩展 dc_collection_point 和 dc_data_record

**Files:**
- Create: `d:\workspace\nexus-erp\doc\mes\sql\mes-week28-23-iot-bridge.sql`

- [ ] **Step 1: 创建 SQL 脚本**

写入文件 `d:\workspace\nexus-erp\doc\mes\sql\mes-week28-23-iot-bridge.sql`：

```sql
-- =============================================
-- NexusERP - MES Week 28 IoT Bridge 扩展字段
-- 目标库: ry-mes
-- 创建时间: 2026-06-26
-- 说明: 为 Wave1 Task 3.2「IoT 自动采集归档」预留字段
--       幂等设计：使用 information_schema 检查列是否存在
-- =============================================

USE `ry-mes`;

-- ============================================================
-- 1. dc_collection_point 新增 iot_device_id / iot_property_code
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_collection_point' AND COLUMN_NAME = 'iot_device_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `dc_collection_point` ADD COLUMN `iot_device_id` BIGINT NULL COMMENT ''关联 IoT 设备 ID（AUTO 类型必填）'' AFTER `equipment_id`',
    'SELECT ''dc_collection_point.iot_device_id 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_collection_point' AND COLUMN_NAME = 'iot_property_code');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `dc_collection_point` ADD COLUMN `iot_property_code` VARCHAR(64) NULL COMMENT ''IoT 属性编码（对应 iot_device_data.property_code）'' AFTER `iot_device_id`',
    'SELECT ''dc_collection_point.iot_property_code 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. dc_data_record 新增 property_code
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_data_record' AND COLUMN_NAME = 'property_code');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `dc_data_record` ADD COLUMN `property_code` VARCHAR(64) NULL COMMENT ''IoT 属性编码（source=IoT 时填充）'' AFTER `point_code`',
    'SELECT ''dc_data_record.property_code 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. dc_data_record 去重唯一索引（point_id + collect_time）
--    用于 IoT 归档任务幂等：同一采集点同一时间只保留一条
-- ============================================================
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dc_data_record' AND INDEX_NAME = 'uk_point_collect_time');
SET @sql = IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_point_collect_time` ON `dc_data_record` (`point_id`, `collect_time`)',
    'SELECT ''dc_data_record.uk_point_collect_time 已存在'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 验证
-- ============================================================
-- DESC dc_collection_point;  -- 应包含 iot_device_id / iot_property_code
-- DESC dc_data_record;       -- 应包含 property_code
-- SHOW INDEX FROM dc_data_record WHERE Key_name = 'uk_point_collect_time';
```

- [ ] **Step 2: 执行 SQL（在测试库先执行）**

```bash
mysql -h <host> -u <user> -p ry-mes < d:\workspace\nexus-erp\doc\mes\sql\mes-week28-23-iot-bridge.sql
```

Expected: 无报错，4 个 PREPARE 语句均执行成功。

- [ ] **Step 3: 提交 SQL 脚本**

```bash
cd d:\workspace\nexus-erp
git add doc/mes/sql/archive/mes-week28-23-iot-bridge.sql
git commit -m "feat(mes-dc-sql): dc_collection_point/dc_data_record 新增 IoT 桥接字段 (Wave1-3.2)"
```

### Task 2.2 后端：DcCollectionPoint 和 DcDataRecord 实体补字段

**Files:**
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\dc\domain\DcCollectionPoint.java`
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\dc\domain\DcDataRecord.java`

- [ ] **Step 1: DcCollectionPoint 追加 iotDeviceId 和 iotPropertyCode 字段**

打开 `DcCollectionPoint.java`，在 `equipmentId` 字段后（约第 35 行）追加：

```java
    /** 关联IoT设备ID（AUTO类型必填） */
    private Long iotDeviceId;

    /** IoT属性编码（对应 iot_device_data.property_code） */
    private String iotPropertyCode;
```

- [ ] **Step 2: DcDataRecord 追加 propertyCode 字段**

打开 `DcDataRecord.java`，在 `pointCode` 字段后（约第 40 行）追加：

```java
    /** IoT属性编码（source=IoT时填充） */
    private String propertyCode;
```

- [ ] **Step 3: 编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交实体变更**

```bash
cd d:\workspace\nexus-erp
git add ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/domain/DcCollectionPoint.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/domain/DcDataRecord.java
git commit -m "feat(mes-dc): DcCollectionPoint/DcDataRecord 实体补 IoT 桥接字段 (Wave1-3.2)"
```

### Task 2.3 后端：新建 IotDataArchiveTask SnailJob 定时任务

**Files:**
- Create: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\dc\task\IotDataArchiveTask.java`

- [ ] **Step 1: 创建任务类**

写入文件 `IotDataArchiveTask.java`：

```java
package com.nexus.mes.dc.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.client.job.core.enums.ExecuteResult;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.mes.dc.domain.DcCollectionPoint;
import com.nexus.mes.dc.domain.DcDataRecord;
import com.nexus.mes.dc.mapper.DcCollectionPointMapper;
import com.nexus.mes.dc.mapper.DcDataRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * IoT 自动采集归档定时任务
 * <p>
 * 周期性扫描 dc_collection_point 中 point_type=AUTO 且关联 iot_device_id 的采集点，
 * 调 IotDeviceDataQueryService.getHistoryData 拉取 IoT 历史数据，
 * 写入 dc_data_record（source=IoT），通过唯一索引 uk_point_collect_time 去重。
 * <p>
 * 建议频率：5 分钟一次（在 SnailJob 控制台配置 cron: 0 */5 * * * ?）
 */
@Component
@RequiredArgsConstructor
@Slf4j
@JobExecutor(name = "iotDataArchiveTask")
public class IotDataArchiveTask {

    private static final String POINT_TYPE_AUTO = "AUTO";
    private static final String SOURCE_IOT = "IoT";
    /** 每次拉取时间窗口（分钟）：避免重复拉取已归档数据 */
    private static final long PULL_WINDOW_MINUTES = 6L;

    private final DcCollectionPointMapper collectionPointMapper;
    private final DcDataRecordMapper dataRecordMapper;
    private final IotDeviceDataQueryService iotDeviceDataQueryService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[IoT归档任务] 开始执行");
        try {
            Date endTime = new Date();
            Date startTime = new Date(System.currentTimeMillis() - Duration.ofMinutes(PULL_WINDOW_MINUTES).toMillis());
            int totalInserted = archiveAutoPoints(startTime, endTime);
            SnailJobLog.REMOTE.info("[IoT归档任务] 完成，归档 {} 条记录", totalInserted);
            return ExecuteResult.success("归档 " + totalInserted + " 条");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[IoT归档任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("归档失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int archiveAutoPoints(Date startTime, Date endTime) {
        LambdaQueryWrapper<DcCollectionPoint> wrapper = Wrappers.<DcCollectionPoint>lambdaQuery()
            .eq(DcCollectionPoint::getPointType, POINT_TYPE_AUTO)
            .isNotNull(DcCollectionPoint::getIotDeviceId)
            .eq(DcCollectionPoint::getStatus, "ACTIVE");
        List<DcCollectionPoint> points = collectionPointMapper.selectList(wrapper);

        int inserted = 0;
        for (DcCollectionPoint point : points) {
            try {
                inserted += archiveOne(point, startTime, endTime);
            } catch (Exception e) {
                log.warn("[IoT归档任务] 采集点 {} 归档失败: {}", point.getPointId(), e.getMessage(), e);
            }
        }
        return inserted;
    }

    private int archiveOne(DcCollectionPoint point, Date startTime, Date endTime) {
        List<Map<String, Object>> history = iotDeviceDataQueryService.getHistoryData(
            point.getIotDeviceId(), startTime, endTime);
        if (history == null || history.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Map<String, Object> row : history) {
            // 拉取的 IoT 数据结构：{ collectTime: Date, propertyCode: String, value: Object }
            // 对应 ruoyi-modules/ruoyi-iot IotDeviceDataQueryServiceImpl.getHistoryData 返回结构
            Object ctObj = row.get("collectTime");
            Object pcObj = row.get("propertyCode");
            Object valObj = row.get("value");
            if (ctObj == null || valObj == null) {
                continue;
            }
            // 仅归档采集点配置的属性（若配置了 iotPropertyCode 则过滤，否则归档全部属性）
            String propertyCode = pcObj == null ? null : String.valueOf(pcObj);
            if (point.getIotPropertyCode() != null && !point.getIotPropertyCode().isEmpty()
                && !point.getIotPropertyCode().equals(propertyCode)) {
                continue;
            }

            Date collectTime = ctObj instanceof Date ? (Date) ctObj : parseDate(ctObj);
            if (collectTime == null) continue;

            DcDataRecord record = new DcDataRecord();
            record.setPointId(point.getPointId());
            record.setPointCode(point.getPointCode());
            record.setPropertyCode(propertyCode);
            record.setCollectTime(collectTime);
            record.setSource(SOURCE_IOT);
            record.setCollectBy("IotDataArchiveTask");

            String dataType = point.getDataType();
            if ("NUMERIC".equalsIgnoreCase(dataType)) {
                try {
                    record.setNumericValue(new BigDecimal(String.valueOf(valObj)));
                } catch (NumberFormatException ignore) {
                    record.setTextValue(String.valueOf(valObj));
                }
            } else {
                record.setTextValue(String.valueOf(valObj));
            }

            try {
                int rows = dataRecordMapper.insert(record);
                if (rows > 0) count++;
            } catch (org.springframework.dao.DuplicateKeyException dke) {
                // 唯一索引 uk_point_collect_time 兜底去重
                log.debug("[IoT归档任务] 重复记录跳过: pointId={}, collectTime={}",
                    point.getPointId(), collectTime);
            }
        }
        return count;
    }

    private Date parseDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof java.time.LocalDateTime) {
            return java.sql.Timestamp.valueOf((java.time.LocalDateTime) obj);
        }
        if (obj instanceof java.time.Instant) {
            return Date.from((java.time.Instant) obj);
        }
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: 确认 IotDeviceDataQueryServiceImpl.getHistoryData 返回字段结构**

打开 `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-iot\src\main\java\com\nexus\iot\common\IotDeviceDataQueryServiceImpl.java`，检查 getHistoryData 方法的返回字段名。如果实际字段名不是 `collectTime`/`propertyCode`/`value`，修正任务中的 `row.get(...)` 字段名与之匹配。

- [ ] **Step 3: 编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd d:\workspace\nexus-erp
git add ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/dc/task/IotDataArchiveTask.java
git commit -m "feat(mes-dc): 新增 IotDataArchiveTask IoT 自动采集归档定时任务 (Wave1-3.2)"
```

### Task 2.4 SnailJob 控制台注册任务

- [ ] **Step 1: 启动系统，登录 SnailJob 控制台（默认 8080 端口或部署文档中 SnailJob Server 端口）**

- [ ] **Step 2: 新增任务定义**
  - 任务名称: `iotDataArchiveTask`
  - 执行器名称: `iotDataArchiveTask`（与 @JobExecutor name 一致）
  - cron 表达式: `0 */5 * * * ?`（每 5 分钟）
  - 重试次数: 3
  - 超时时间: 60 秒

- [ ] **Step 3: 手动触发一次任务，验证日志**

Expected: 控制台显示「[IoT归档任务] 完成，归档 N 条记录」。

### Task 2 验收标准

- [ ] **功能验收 1**: 配置一个 AUTO 采集点，关联 IoT 设备 ID 和属性编码
- [ ] **功能验收 2**: 手动触发 iotDataArchiveTask，dc_data_record 表新增 source=IoT 的记录
- [ ] **功能验收 3**: 重复触发，因唯一索引跳过，不重复插入
- [ ] **功能验收 4**: SPC 控制图分析时能消费 source=IoT 的数据点

---

## Task 3: SPC OOC 触发安灯

**目标**: SPC 分析判 OOC（RULE1 最严重）自动创建 QUALITY 类型安灯；同一控制图 10 分钟内仅触发一次（限频防风暴）。

**人天**: 2

**前置条件**:
- `SpcControlChartServiceImpl.analyze` 已实现 OOC 判定（4 条规则）
- `IProAndonService.callAndon(mesWoId, cardId, stepRecordId, callType, callReason)` 已实现
- `RedisUtils.setObjectIfAbsent(key, value, ttl)` 限频模式已存在（参考 `IotAlertNotifier`）

### Task 3.1 后端：抽象 RULE 常量到接口

**Files:**
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\spc\service\ISpcControlChartService.java`
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\spc\service\impl\SpcControlChartServiceImpl.java`

- [ ] **Step 1: 在 ISpcControlChartService 接口追加 OOC 规则常量**

打开 `ISpcControlChartService.java`，在 `STATUS_INACTIVE` 常量后追加：

```java
    /** OOC 规则：1 点超出控制限（最严重） */
    String OOC_RULE_1 = "RULE1";
    /** OOC 规则：连续 9 点在 CL 同侧 */
    String OOC_RULE_2 = "RULE2";
    /** OOC 规则：连续 6 点严格递增或递减 */
    String OOC_RULE_3 = "RULE3";
    /** OOC 规则：连续 14 点交替上下 */
    String OOC_RULE_4 = "RULE4";
```

- [ ] **Step 2: 替换 SpcControlChartServiceImpl 中的内联字符串**

打开 `SpcControlChartServiceImpl.java`，将 `judgeOoc` 方法中所有字面量 `"RULE1"`/`"RULE2"`/`"RULE3"`/`"RULE4"` 替换为 `ISpcControlChartService.OOC_RULE_1` 等常量引用（约第 386/399/403/407 行）。

### Task 3.2 后端：analyze 方法加 OOC→安灯联动 + 限频

**Files:**
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\spc\service\impl\SpcControlChartServiceImpl.java`

- [ ] **Step 1: 注入 IProAndonService 依赖**

打开 `SpcControlChartServiceImpl.java`，在类字段区追加（若类用 `@RequiredArgsConstructor` 则补 final 字段）：

```java
    private final com.nexus.mes.pro.service.IProAndonService proAndonService;
```

顶部补 import：

```java
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.common.redis.helper.RedisUtils;
import java.time.Duration;
```

> `RedisUtils` 实际包路径以项目为准（参考 `IotAlertNotifier.java` 顶部 import）。打开 IotAlertNotifier 查实际 import 后修正。

- [ ] **Step 2: 在 analyze 方法中找到 OOC 命中后写入 SpcDataPoint 的位置，追加安灯联动调用**

打开 `SpcControlChartServiceImpl.analyze` 方法（第 141 行起），定位到 OOC 判定后设置 `dataPoint.setIsOoc(1)` 和 `dataPoint.setOocRule(oocRules)` 的代码段（约第 200 行附近，需要先 Read 文件确认确切行号）。

在写入 SpcDataPoint 之后追加安灯联动调用：

```java
                    // OOC 命中 RULE1 → 触发 QUALITY 安灯（10 分钟限频）
                    if (oocRules != null && oocRules.contains(ISpcControlChartService.OOC_RULE_1)) {
                        triggerAndonForOoc(chart, dataPoint, oocRules);
                    }
```

- [ ] **Step 3: 新增 triggerAndonForOoc 方法（含限频逻辑）**

在 `SpcControlChartServiceImpl` 类末尾追加私有方法：

```java
    private static final String SPC_ANDON_THROTTLE_PREFIX = "spc:andon:throttle:";
    private static final Duration SPC_ANDON_THROTTLE_TTL = Duration.ofMinutes(10);

    /**
     * OOC 命中 RULE1 时触发 QUALITY 安灯，10 分钟限频防风暴。
     */
    private void triggerAndonForOoc(com.nexus.mes.spc.domain.SpcControlChart chart,
                                   com.nexus.mes.spc.domain.SpcDataPoint dataPoint,
                                   String oocRules) {
        try {
            String throttleKey = SPC_ANDON_THROTTLE_PREFIX + chart.getChartId();
            if (!RedisUtils.setObjectIfAbsent(throttleKey, "1", SPC_ANDON_THROTTLE_TTL)) {
                log.info("[SPC] 控制图 {} OOC 安灯限频中，跳过", chart.getChartId());
                return;
            }

            String reason = String.format("SPC OOC 告警: 控制图[%s] 规则[%s] 数值[%s] CL[%s] UCL[%s] LCL[%s]",
                chart.getChartName(),
                oocRules,
                dataPoint.getNumericValue(),
                chart.getCl() == null ? "N/A" : chart.getCl(),
                chart.getUcl() == null ? "N/A" : chart.getUcl(),
                chart.getLcl() == null ? "N/A" : chart.getLcl());

            // SPC 触发的安灯无具体工单/流转卡上下文，mesWoId/cardId/stepRecordId 传 null
            proAndonService.callAndon(null, null, null,
                IProAndonService.TYPE_QUALITY, reason);
            log.info("[SPC] 控制图 {} OOC 已触发 QUALITY 安灯", chart.getChartId());
        } catch (Exception e) {
            log.warn("[SPC] 控制图 {} OOC 触发安灯失败: {}", chart.getChartId(), e.getMessage(), e);
            // 安灯失败不影响 SPC 分析主流程
        }
    }
```

> 说明：参考 `MesAndonServiceImpl:44` 模式，`proAndonService.callAndon` 内部已处理租户上下文（如未处理，需在调用前 `TenantHelper.dynamic(...)` 包裹，但 SPC analyze 已在租户上下文内执行，可直接调）。

- [ ] **Step 4: 编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
cd d:\workspace\nexus-erp
git add ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/service/ISpcControlChartService.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/service/impl/SpcControlChartServiceImpl.java
git commit -m "feat(mes-spc): SPC OOC 自动触发 QUALITY 安灯 + 10分钟限频 (Wave1-3.3)"
```

### Task 3 验收标准

- [ ] **功能验收 1**: 录入一组超 UCL 的 SPC 数据点，执行 analyze，自动创建一条 QUALITY 类型安灯，reason 含控制图名称+OOC规则+超限值
- [ ] **功能验收 2**: 10 分钟内重复 analyze，不再创建新安灯（限频生效）
- [ ] **功能验收 3**: 10 分钟后再次 analyze OOC，能再次创建安灯
- [ ] **功能验收 4**: RULE2/3/4 命中时不触发安灯（仅 RULE1 触发）

---

## Task 4: ERP 收货单驱动 IQC 自动创建

**目标**: ERP 收货单审批通过后发 `ReceiveApprovedMessage` 到 MQ，MES 侧 `IqcAutoCreateConsumer` 消费后自动创建 IQC 检验单（幂等：同 receiveId 不重复创建）。

**人天**: 3

**前置条件**:
- `BaseMessageConsumer<T extends BaseMessage>` 抽象类已存在（`ruoyi-common-mq`）
- `ReliableMessageProducer.send(exchange, routingKey, message)` 已存在
- `IQcIqcService.insertByBo(QcIqcBo bo)` 已实现
- `qc_iqc.receive_id` 字段已存在
- `WmMaterialReceiveCrudServiceImpl.afterFlowHandle` 在 `ActStatus.pass` 分支已设 STATUS_APPROVED
- `ExchangeConstants.TOPIC_EXCHANGE` 队列绑定声明在 `RabbitMqInitializer` 中

### Task 4.1 公共模块：新建 ReceiveApprovedMessage DTO

**Files:**
- Create: `d:\workspace\nexus-erp\ruoyi-common\ruoyi-common-mq\src\main\java\com\nexus\common\mq\message\ReceiveApprovedMessage.java`

- [ ] **Step 1: 创建 DTO 类**

写入文件 `ReceiveApprovedMessage.java`：

```java
package com.nexus.common.mq.message;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * ERP 收货单审批通过消息
 * <p>
 * 触发点：WmMaterialReceiveCrudServiceImpl.afterFlowHandle 在 ActStatus.pass 后 afterCommit 投递。
 * 消费端：MES IqcAutoCreateConsumer 自动创建 IQC 检验单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReceiveApprovedMessage extends BaseMessage {

    /** ERP 收货单 ID */
    private Long receiveId;

    /** 收货单号 */
    private String receiveCode;

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 供应商 ID */
    private Long vendorId;

    /** 供应商名称 */
    private String vendorName;

    /** 收货数量 */
    private BigDecimal quantity;

    /** 收货明细 ID（用于 IQC 关联，可空） */
    private Long receiveDetailId;
}
```

- [ ] **Step 2: 在 QueueConstants 增加队列常量**

打开 `d:\workspace\nexus-erp\ruoyi-common\ruoyi-common-mq\src\main\java\com\nexus\common\mq\constant\QueueConstants.java`，在 `ERP_IQC_COMPLETED_QUEUE` 后追加：

```java
    /** MES IQC 自动创建队列（ERP 收货审批通过触发） */
    String MES_IQC_CREATE_QUEUE = "erp.mes.iqc.create.queue";
```

- [ ] **Step 3: 在 RoutingKeyConstants 增加路由键**

打开 `d:\workspace\nexus-erp\ruoyi-common\ruoyi-common-mq\src\main\java\com\nexus\common\mq\constant\RoutingKeyConstants.java`，在 `ERP_IQC_COMPLETED` 后追加：

```java
    /** ERP 收货审批通过 → MES 自动创建 IQC */
    String MES_IQC_CREATE = "mes.iqc.create";
```

- [ ] **Step 4: 在 RabbitMqInitializer 注册队列绑定**

打开 `d:\workspace\nexus-erp\ruoyi-common\ruoyi-common-mq\src\main\java\com\nexus\common\mq\config\RabbitMqInitializer.java`，在现有队列声明区域（参考 `ERP_IQC_COMPLETED_QUEUE` 的声明模式）追加：

```java
        // MES IQC 自动创建队列（绑定 TOPIC_EXCHANGE，路由键 mes.iqc.create）
        Queue mesIqcCreateQueue = QueueBuilder.durable(QueueConstants.MES_IQC_CREATE_QUEUE)
            .withArgument("x-dead-letter-exchange", ExchangeConstants.DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", DLX_QUEUE)
            .build();
        rabbitAdmin.declareQueue(mesIqcCreateQueue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(mesIqcCreateQueue)
            .to(new TopicExchange(ExchangeConstants.TOPIC_EXCHANGE))
            .with(RoutingKeyConstants.MES_IQC_CREATE));
```

> 实际代码参考现有 `ERP_IQC_COMPLETED_QUEUE` 的声明方式，保持一致。如果 RabbitMqInitializer 是声明式 Bean 风格，按相同风格写。

- [ ] **Step 5: 编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-common/ruoyi-common-mq -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 提交公共模块变更**

```bash
cd d:\workspace\nexus-erp
git add ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/message/ReceiveApprovedMessage.java ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/constant/QueueConstants.java ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/constant/RoutingKeyConstants.java ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/config/RabbitMqInitializer.java
git commit -m "feat(mq): 新增 ReceiveApprovedMessage DTO + MES_IQC_CREATE_QUEUE 队列 (Wave1-3.4)"
```

### Task 4.2 ERP 侧：afterFlowHandle 发 MQ

**Files:**
- Modify: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-erp\src\main\java\com\nexus\erp\wm\service\impl\WmMaterialReceiveCrudServiceImpl.java`

- [ ] **Step 1: 注入 ReliableMessageProducer 依赖**

打开 `WmMaterialReceiveCrudServiceImpl.java`，在类字段区追加（参考现有字段注入模式）：

```java
    private final com.nexus.common.mq.producer.ReliableMessageProducer messageProducer;
```

- [ ] **Step 2: 在 afterFlowHandle 的 ActStatus.pass 分支追加 afterCommit 发 MQ**

定位到 afterFlowHandle 方法（约第 368-393 行），找到 `else if (ActStatus.pass.equals(actStatus))` 分支，修改为：

```java
    } else if (ActStatus.pass.equals(actStatus)) {
        // 审批通过：不再发送 StockInMessage，入库过账统一由用户手动触发（executeReceive）
        receive.setStatus(STATUS_APPROVED);
        baseMapper.updateById(receive);

        // 新增：afterCommit 异步发 ReceiveApprovedMessage 给 MES 自动创建 IQC
        registerReceiveApprovedMessage(receive);
        return;
    }
```

在类内任意位置追加私有方法：

```java
    /**
     * 注册 afterCommit 回调，事务提交后发 ReceiveApprovedMessage 给 MES 自动创建 IQC。
     * 参考模式：PrdWorkOrderServiceImpl.afterFlowHandle 第 511-513 行。
     */
    private void registerReceiveApprovedMessage(WmMaterialReceive receive) {
        try {
            com.nexus.common.mq.message.ReceiveApprovedMessage message = new com.nexus.common.mq.message.ReceiveApprovedMessage();
            message.setReceiveId(receive.getReceiveId());
            message.setReceiveCode(receive.getReceiveCode());
            // materialId/materialCode/materialName/vendorId/vendorName/quantity 从收货明细或主表读取
            // 实际字段从 WmMaterialReceive 实体获取，如字段不存在则查明细表
            message.setMaterialId(receive.getMaterialId());
            message.setMaterialCode(receive.getMaterialCode());
            message.setMaterialName(receive.getMaterialName());
            message.setVendorId(receive.getVendorId());
            message.setVendorName(receive.getVendorName());
            message.setQuantity(receive.getQuantity());
            message.setSourceModule("ERP");
            // 显式设置 tenantId（修复 sendWorkOrderReleaseMessage 同类缺陷）
            String tenantId = com.nexus.common.tenant.helper.TenantHelper.getTenantId();
            if (tenantId != null) {
                try {
                    message.setTenantId(Long.parseLong(tenantId));
                } catch (NumberFormatException ignore) {
                    message.setTenantId(0L);
                }
            }

            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            messageProducer.send(
                                com.nexus.common.mq.constant.ExchangeConstants.TOPIC_EXCHANGE,
                                com.nexus.common.mq.constant.RoutingKeyConstants.MES_IQC_CREATE,
                                message);
                            log.info("收货单审批通过 MQ 已发送: receiveId={}", receive.getReceiveId());
                        } catch (Exception e) {
                            log.error("收货单审批通过 MQ 发送失败: receiveId={}", receive.getReceiveId(), e);
                            // 失败由 MQ 补偿任务兜底
                        }
                    }
                });
        } catch (Exception e) {
            log.error("注册收货审批 MQ 失败: receiveId={}", receive.getReceiveId(), e);
        }
    }
```

> 关键：使用 `TransactionSynchronizationManager.registerSynchronization` + `afterCommit`，确保 ERP 状态已持久化后再发 MQ。`message.setTenantId` 显式设置，避免消费端退化为 `000000`。`WmMaterialReceive` 实体字段以实际为准（如 `materialId` 可能在明细表 `WmMaterialReceiveDetail` 中，需要先 Read 实体确认字段名）。

- [ ] **Step 3: 验证 WmMaterialReceive 实体字段**

打开 `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-erp\src\main\java\com\nexus\erp\wm\domain\WmMaterialReceive.java`，确认 `materialId`/`materialCode`/`materialName`/`vendorId`/`vendorName`/`quantity` 字段是否存在。若不存在，改为从明细表查询填充（参考现有 `selectByReceiveId` 等查询方法）。

- [ ] **Step 4: 编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交 ERP 侧变更**

```bash
cd d:\workspace\nexus-erp
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveCrudServiceImpl.java
git commit -m "feat(erp-wm): 收货单审批通过 afterCommit 发 ReceiveApprovedMessage MQ (Wave1-3.4)"
```

### Task 4.3 MES 侧：新建 IqcAutoCreateConsumer

**Files:**
- Create: `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\common\mq\consumer\IqcAutoCreateConsumer.java`

- [ ] **Step 1: 创建消费者类**

写入文件 `IqcAutoCreateConsumer.java`：

```java
package com.nexus.mes.common.mq.consumer;

import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.ReceiveApprovedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.mapper.QcIqcMapper;
import com.nexus.mes.qc.service.IQcIqcService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 ERP 收货审批通过消息，自动创建 MES IQC 检验单。
 * 幂等：同一 receiveId 已创建 IQC 则跳过（查 qc_iqc.receive_id）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IqcAutoCreateConsumer extends BaseMessageConsumer<ReceiveApprovedMessage> {

    private final IQcIqcService iqcService;
    private final QcIqcMapper iqcMapper;

    @Override
    @RabbitListener(queues = QueueConstants.MES_IQC_CREATE_QUEUE)
    public void processMessage(ReceiveApprovedMessage message, Channel channel, Message amqpMessage) throws Exception {
        super.processMessage(message, channel, amqpMessage);
    }

    @Override
    protected Class<ReceiveApprovedMessage> getMessageClass() {
        return ReceiveApprovedMessage.class;
    }

    @Override
    protected void doProcess(ReceiveApprovedMessage message) {
        // 从消息恢复租户上下文（参考 ErpIqcCompletedConsumer:60-68）
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> {
            doCreateIqc(message);
        });
    }

    private void doCreateIqc(ReceiveApprovedMessage message) {
        // 幂等：同 receiveId 已创建 IQC 则跳过
        Long existCount = iqcMapper.selectCount(Wrappers.<QcIqc>lambdaQuery()
            .eq(QcIqc::getReceiveId, message.getReceiveId()));
        if (existCount != null && existCount > 0) {
            log.info("[IqcAutoCreate] 收货单 {} 已存在 IQC，跳过自动创建", message.getReceiveId());
            return;
        }

        try {
            QcIqcBo bo = new QcIqcBo();
            bo.setReceiveId(message.getReceiveId());
            bo.setMaterialId(message.getMaterialId());
            bo.setMaterialCode(message.getMaterialCode());
            bo.setMaterialName(message.getMaterialName());
            bo.setVendorId(message.getVendorId());
            bo.setVendorName(message.getVendorName());
            bo.setQuantity(message.getQuantity());
            // IQC 编号由 insertByBo 内部 generateNo 自动生成
            // 状态默认 PENDING，由 insertByBo 设置
            iqcService.insertByBo(bo);
            log.info("[IqcAutoCreate] 收货单 {} 已自动创建 IQC", message.getReceiveId());
        } catch (Exception e) {
            log.error("[IqcAutoCreate] 收货单 {} 自动创建 IQC 失败: {}",
                message.getReceiveId(), e.getMessage(), e);
            throw new RuntimeException("IQC 自动创建失败", e);
        }
    }
}
```

> 关键点：
> 1. `@RabbitListener(queues = MES_IQC_CREATE_QUEUE)` + 调用 super.processMessage 复用幂等/重试/死信
> 2. `TenantHelper.dynamic` 恢复租户上下文
> 3. 查 `qc_iqc.receive_id` 幂等去重
> 4. `QcIqcBo` 字段名需与实际类匹配（先 Read 确认）
> 5. `insertByBo` 内部会调 `generateNo("IQC")` 自动生成编号

- [ ] **Step 2: 验证 QcIqcBo 字段**

打开 `d:\workspace\nexus-erp\ruoyi-modules\ruoyi-mes\src\main\java\com\nexus\mes\qc\domain\bo\QcIqcBo.java`，确认 `receiveId`/`materialId`/`materialCode`/`materialName`/`vendorId`/`vendorName`/`quantity` 字段是否存在。若 QcIqcBo 字段名与 message 不一致，需要调整 setter 调用。

- [ ] **Step 3: 编译验证**

```bash
cd d:\workspace\nexus-erp && mvn -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd d:\workspace\nexus-erp
git add ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mq/consumer/IqcAutoCreateConsumer.java
git commit -m "feat(mes-qc): IqcAutoCreateConsumer 消费 ERP 收货审批 MQ 自动创建 IQC (Wave1-3.4)"
```

### Task 4 验收标准

- [ ] **功能验收 1**: 在 ERP 提交一张采购收货单，走 Flowable 审批通过
- [ ] **功能验收 2**: ERP 库 wm_material_receive 状态变 STATUS_APPROVED
- [ ] **功能验收 3**: RabbitMQ 控制台看到 erp.mes.iqc.create.queue 有消息消费
- [ ] **功能验收 4**: MES 库 qc_iqc 自动新增一条记录，receive_id = 收货单 ID，iqc_no 格式 IQC-yyyyMMdd-NNN
- [ ] **功能验收 5**: 重复触发同 receiveId 的 MQ 消息，不重复创建 IQC（幂等）
- [ ] **功能验收 6**: MQ 消费失败时进死信队列（手动制造异常验证）

---

## 自检（Self-Review）

### 1. Spec 覆盖检查

对照 `NexusERP_三模块共同升级方案.md` §3.1-3.4 的任务清单：

| 方案要求 | 对应 Task | 状态 |
|---------|----------|------|
| 3.1 MES 设备台账加 IoT 在线状态列 | Task 1.2 Step 2 | ✅ 覆盖 |
| 3.1 MES 设备台账加 IoT 最新数据按钮 | Task 1.2 Step 3 | ✅ 覆盖 |
| 3.1 IoT 设备页加 MES 设备链接 | Task 1.3 Step 5 | ✅ 覆盖 |
| 3.1 双向关联设置 | Task 1.3 Step 2-7 | ✅ 覆盖 |
| 3.2 SnailJob 定时任务 IotDataArchiveTask | Task 2.3 | ✅ 覆盖 |
| 3.2 AUTO 采集点查 dc_collection_point | Task 2.3 Step 1 wrapper | ✅ 覆盖 |
| 3.2 调 IotDeviceDataQueryService.getHistoryData | Task 2.3 Step 1 | ✅ 覆盖 |
| 3.2 写入 dc_data_record(source=IoT) | Task 2.3 Step 1 archiveOne | ✅ 覆盖 |
| 3.2 按 collect_time+propertyCode 去重 | Task 2.1 唯一索引 uk_point_collect_time | ✅ 覆盖 |
| 3.2 频率 5 分钟 | Task 2.4 SnailJob cron 配置 | ✅ 覆盖 |
| 3.3 SpcControlChartServiceImpl.analyze 内 OOC→安灯 | Task 3.2 Step 2 | ✅ 覆盖 |
| 3.3 RULE1 触发 QUALITY 安灯 | Task 3.2 Step 2 if 判断 | ✅ 覆盖 |
| 3.3 限频 Redis key TTL=600s | Task 3.2 Step 3 | ✅ 覆盖 |
| 3.3 安灯 reason 含控制图名称+OOC规则+超限值 | Task 3.2 Step 3 reason 字符串 | ✅ 覆盖 |
| 3.4 ReceiveApprovedMessage DTO | Task 4.1 Step 1 | ✅ 覆盖 |
| 3.4 MES_IQC_CREATE_QUEUE + 路由键 | Task 4.1 Step 2-3 | ✅ 覆盖 |
| 3.4 ERP WmMaterialReceiveCrudServiceImpl afterCommit 发 MQ | Task 4.2 Step 2 | ✅ 覆盖 |
| 3.4 MES IqcAutoCreateConsumer extends BaseMessageConsumer | Task 4.3 Step 1 | ✅ 覆盖 |
| 3.4 幂等同 receiveId 不重复 | Task 4.3 Step 1 existCount 查询 | ✅ 覆盖 |

无遗漏。

### 2. 类型/方法一致性检查

- `getIotStatus(Long equipmentId): String` — IEqEquipmentService 与 EqEquipmentServiceImpl 一致 ✅
- `getIotLatest(Long equipmentId): Map<String, String>` — 一致 ✅
- `IotDeviceDataQueryService.getOnlineStatus(Long): String` — 接口已存在 ✅
- `IotDeviceDataQueryService.getLatestData(Long): Map<String, String>` — 接口已存在 ✅
- `IotDeviceDataQueryService.getHistoryData(Long, Date, Date): List<Map<String, Object>>` — 接口已存在 ✅
- `IProAndonService.callAndon(Long, Long, Long, String, String): ProAndonVo` — 一致 ✅
- `IQcIqcService.insertByBo(QcIqcBo): QcIqcVo` — 一致 ✅
- `BaseMessageConsumer.doProcess(T): void` / `getMessageClass(): Class<T>` — 一致 ✅
- `ReliableMessageProducer.send(String exchange, String routingKey, BaseMessage message): void` — 一致 ✅
- `TenantHelper.dynamic(String, Runnable): void` — 一致 ✅
- `RedisUtils.setObjectIfAbsent(key, value, ttl): boolean` — 参照 IotAlertNotifier ✅

### 3. 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| `WmMaterialReceive` 实体字段名与 message 不一致 | Task 4.2 Step 3 已要求先 Read 实体确认 |
| `QcIqcBo` 字段名与 message 不一致 | Task 4.3 Step 2 已要求先 Read 确认 |
| `IotDeviceDataQueryServiceImpl.getHistoryData` 返回 Map 字段名 | Task 2.3 Step 2 已要求检查实际字段名 |
| `RedisUtils` 包路径不确定 | Task 3.2 Step 1 已要求参考 IotAlertNotifier import |
| MQ 消费失败积压 | BaseMessageConsumer 已有重试+死信机制 |
| IQC 编号并发冲突 | generateNo 已有唯一索引兜底 |
| 多租户消息丢失租户上下文 | 显式 setTenantId + dynamic 恢复 |

---

## 执行移交

本计划已保存到 `d:\workspace\nexus-erp\doc\system\plans\2026-06-26-upgrade-wave1-plan.md`。

待用户审批后，按推荐执行顺序逐 Task 推进。每个 Task 完成后进行功能验收，全部通过后第一波闭环。

---

**编制日期**: 2026-06-26
**编制者**: AI Agent
**审批状态**: 待用户审批
**前置文档**: `NexusERP_三模块共同升级方案.md`
