<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="设备编码" prop="deviceCode">
            <el-input v-model="queryParams.deviceCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="设备名称" prop="deviceName">
            <el-input v-model="queryParams.deviceName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="协议类型" prop="protocolType">
            <el-select v-model="queryParams.protocolType" placeholder="请选择协议类型" clearable style="width: 100%">
              <el-option v-for="dict in iot_protocol_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in iot_device_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="所属分组" prop="groupId">
            <div class="group-filter">
              <el-tree-select
                v-model="queryParams.groupId"
                :data="groupTree"
                :props="groupTreeSelectProps"
                placeholder="全部分组"
                check-strictly
                clearable
                class="group-filter-select"
              />
              <el-checkbox v-model="queryParams.includeSubGroup">含子分组</el-checkbox>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="标签" prop="tagIds">
            <!-- 多标签取交集（同时具备所选标签的设备） -->
            <el-select v-model="queryParams.tagIds" multiple filterable collapse-tags placeholder="全部标签" style="width: 100%">
              <el-option v-for="item in tagOptions" :key="item.tagId" :label="item.tagName" :value="item.tagId" />
            </el-select>
          </el-form-item>
        </el-col>
        <!-- 搜索项 6 个 → 6 % 4 = 2，按钮补当前行第 4 格（offset=6） -->
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['iot:device:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['iot:device:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['iot:device:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['iot:device:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['iot:device:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="deviceList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="设备编码" align="center" prop="deviceCode" />
      <el-table-column label="设备名称" align="center" prop="deviceName" min-width="120" show-overflow-tooltip />
      <el-table-column label="所属产品" align="center" prop="productName" min-width="120" show-overflow-tooltip />
      <el-table-column label="协议类型" align="center" prop="protocolType">
        <template #default="scope">
          <dict-tag :options="iot_protocol_type" :value="scope.row.protocolType" />
        </template>
      </el-table-column>
      <el-table-column label="最后在线" align="center" prop="lastOnlineTime" />
      <el-table-column label="连接状态" align="center" prop="connectionStatus">
        <template #default="scope">
          <dict-tag :options="iot_connection_status" :value="scope.row.connectionStatus" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="iot_device_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="MES 设备" align="center">
        <template #default="scope">
          <el-button v-if="scope.row.mesEquipmentId" link type="primary" @click="goToMesEquipment(scope.row.mesEquipmentId)">
            查看 MES 设备
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="DataLine" @click="handleRealtime(scope.row)" v-hasPermi="['iot:device:query']">实时</el-button>
          <el-button link type="primary" icon="Monitor" @click="handleOps(scope.row)" v-hasPermi="['iot:device:query']">运维</el-button>
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['iot:device:edit']">修改</el-button>
          <el-button
            link
            type="warning"
            icon="Switch"
            :disabled="scope.row.status === 'DECOMMISSIONED'"
            @click="openLifecycle(scope.row)"
            v-hasPermi="['iot:device:changeStatus']"
            >状态</el-button
          >
          <el-button link type="primary" icon="Link" @click="handleBindMes(scope.row)" v-hasPermi="['iot:device:edit']">关联MES</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['iot:device:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改设备对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="deviceRef" :model="form" :rules="rules" label-width="90px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属产品" prop="productId">
              <el-select v-model="form.productId" placeholder="请选择产品" filterable style="width: 100%">
                <el-option v-for="item in productOptions" :key="item.productId" :label="item.productName" :value="item.productId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备编码" prop="deviceCode">
              <el-input v-model="form.deviceCode" placeholder="MQTT ClientID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="设备名称" prop="deviceName">
              <el-input v-model="form.deviceName" placeholder="请输入设备名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议类型" prop="protocolType">
              <el-select v-model="form.protocolType" placeholder="请选择协议类型" style="width: 100%">
                <el-option v-for="dict in iot_protocol_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="IP地址" prop="ipAddress">
              <el-input v-model="form.ipAddress" placeholder="请输入IP地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="固件版本" prop="firmwareVersion">
              <el-input v-model="form.firmwareVersion" placeholder="请输入固件版本" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="请输入设备位置" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <!-- 状态由后端权威控制（updateByBo 忽略前端写入），此处仅只读展示；
               变更走「状态」操作（PUT /iot/device/{id}/lifecycle，CAS 条件更新） -->
          <dict-tag :options="iot_device_status" :value="form.status" />
          <span class="status-tip">状态不可在此修改，请使用列表行内的「状态」操作</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm()" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel()">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 关联 MES 设备对话框 -->
    <el-dialog v-model="mesBindDialog.visible" title="关联 MES 设备" width="500px" append-to-body>
      <el-form label-width="120px">
        <el-form-item label="IoT 设备">
          <span>{{ mesBindDialog.deviceCode }} - {{ mesBindDialog.deviceName }}</span>
        </el-form-item>
        <el-form-item label="MES 设备">
          <el-select v-model="mesBindDialog.mesEquipmentId" placeholder="请选择 MES 设备" clearable filterable>
            <el-option
              v-for="e in mesBindDialog.mesEquipmentList"
              :key="e.equipmentId"
              :label="e.equipmentCode + ' - ' + e.equipmentName"
              :value="e.equipmentId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mesBindDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitMesBind()">确定</el-button>
      </template>
    </el-dialog>

    <!-- 运维详情（在线状态 / 最后在线 / 最后上报 / 固件版本 / 分组标签） -->
    <el-drawer title="设备运维详情" v-model="opsOpen" size="45%">
      <el-descriptions v-loading="opsLoading" :column="1" border>
        <el-descriptions-item label="设备编码">{{ ops.deviceCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备名称">{{ ops.deviceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属产品">{{ ops.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="协议类型">
          <dict-tag :options="iot_protocol_type" :value="ops.protocolType" />
        </el-descriptions-item>
        <el-descriptions-item label="生命周期状态">
          <dict-tag :options="iot_device_status" :value="ops.status" />
        </el-descriptions-item>
        <el-descriptions-item label="在线状态">
          <dict-tag :options="iot_connection_status" :value="ops.connectionStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="最后在线时间">{{ parseTime(ops.lastOnlineTime) }}</el-descriptions-item>
        <el-descriptions-item label="最后上报时间">
          {{ ops.lastReportTime ? parseTime(ops.lastReportTime) : '暂无上报（Redis 缓存未命中）' }}
        </el-descriptions-item>
        <el-descriptions-item label="固件版本">{{ ops.firmwareVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ ops.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ ops.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属分组">
          <template v-if="ops.groupIds && ops.groupIds.length">
            <el-tag v-for="gid in ops.groupIds" :key="gid" size="small" class="ops-tag">{{ groupNameMap[gid] || gid }}</el-tag>
          </template>
          <span v-else>未分组</span>
        </el-descriptions-item>
        <el-descriptions-item label="设备标签">
          <template v-if="ops.tagIds && ops.tagIds.length">
            <el-tag v-for="tid in ops.tagIds" :key="tid" size="small" type="success" class="ops-tag">{{ tagNameMap[tid] || tid }}</el-tag>
          </template>
          <span v-else>无标签</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- 生命周期状态变更（CAS） -->
    <el-dialog title="变更设备状态" v-model="lifecycleOpen" width="520px" append-to-body>
      <el-form ref="lifecycleRef" :model="lifecycleForm" :rules="lifecycleRules" label-width="100px">
        <el-form-item label="设备">
          <span>{{ lifecycleForm.deviceCode }} - {{ lifecycleForm.deviceName }}</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <dict-tag :options="iot_device_status" :value="lifecycleForm.currentStatus" />
        </el-form-item>
        <el-form-item label="目标状态" prop="status">
          <el-radio-group v-model="lifecycleForm.status">
            <el-radio v-for="dict in lifecycleTargetOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="变更原因" prop="remark">
          <el-input v-model="lifecycleForm.remark" type="textarea" placeholder="请填写变更原因（写入设备备注，便于审计追溯）" />
        </el-form-item>
        <el-alert type="warning" :closable="false" title="禁用后设备 MQTT 认证立即被拒、连接状态置为离线；注销为终态，不可再恢复为在用状态。" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="lifecycleLoading" @click="submitLifecycle">确 定</el-button>
          <el-button @click="lifecycleOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IotDevice">
import { listDevice, getDevice, delDevice, addDevice, updateDevice, deviceOps, changeDeviceLifecycle } from '@/api/iot/device';
import type { DeviceVO, DeviceQuery, DeviceOpsVO, DeviceLifecycleForm } from '@/api/iot/device/types';
import { listDeviceGroup } from '@/api/iot/deviceGroup';
import type { DeviceGroupVO } from '@/api/iot/deviceGroup/types';
import { optionDeviceTag } from '@/api/iot/deviceTag';
import type { DeviceTagVO } from '@/api/iot/deviceTag/types';
import { listProduct } from '@/api/iot/product';
import { listMesEquipmentForBind, updateMesEquipmentLink } from '@/api/mes/eq/equipment';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { iot_protocol_type, iot_connection_status, iot_device_status } = toRefs<any>(
  proxy.useDict('iot_protocol_type', 'iot_connection_status', 'iot_device_status')
);

const deviceRef = ref<FormInstance>();
const lifecycleRef = ref<FormInstance>();
const productOptions = ref<any[]>([]);
const groupTree = ref<DeviceGroupVO[]>([]);
const tagOptions = ref<DeviceTagVO[]>([]);
/** 分组/标签 ID → 名称映射（运维详情展示用） */
const groupNameMap = ref<Record<string, string>>({});
const tagNameMap = ref<Record<string, string>>({});

/** 分组树节点字段映射（el-tree-select 支持 value 字段，element-plus 的 TreeOptionProps 类型未收录） */
const groupTreeSelectProps = { value: 'groupId', label: 'groupName', children: 'children' };

const opsOpen = ref(false);
const opsLoading = ref(false);
const ops = ref<Partial<DeviceOpsVO>>({});
const lifecycleOpen = ref(false);
const lifecycleLoading = ref(false);
const lifecycleForm = ref<{
  deviceId: string | number | null;
  deviceCode: string;
  deviceName: string;
  currentStatus: string;
  status: string;
  remark: string;
}>({ deviceId: null, deviceCode: '', deviceName: '', currentStatus: '', status: 'ACTIVE', remark: '' });

const lifecycleRules = {
  status: [{ required: true, message: '目标状态不能为空', trigger: 'change' }]
};

/** 可变更的目标状态（排除当前状态；注销为终态，行内按钮已禁用） */
const lifecycleTargetOptions = computed(() => (iot_device_status.value || []).filter((d: any) => d.value !== lifecycleForm.value.currentStatus));

const queryParams = ref<DeviceQuery>({
  pageNum: 1,
  pageSize: 10,
  deviceCode: null,
  deviceName: null,
  protocolType: null,
  status: null,
  groupId: null,
  includeSubGroup: true,
  tagIds: []
});

const rules = {
  productId: [{ required: true, message: '所属产品不能为空', trigger: 'change' }],
  deviceCode: [{ required: true, message: '设备编码不能为空', trigger: 'blur' }],
  deviceName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }]
};

const {
  loading,
  list: deviceList,
  total,
  open,
  isViewMode,
  title,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleView: rawHandleView
} = useCrud({
  apis: { list: listDevice, get: getDevice, add: addDevice, update: updateDevice, del: delDevice },
  idField: 'deviceId',
  entityName: '设备',
  formRef: deviceRef,
  queryParams,
  initForm: () => ({
    deviceId: null,
    deviceCode: null,
    deviceName: null,
    productId: null,
    protocolType: 'MQTT',
    ipAddress: null,
    firmwareVersion: null,
    location: null,
    mesEquipmentId: null,
    status: 'ACTIVE',
    remark: null
  }),
  exportUrl: 'iot/device/export',
  exportFileName: 'iot_device'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  queryParams.value.includeSubGroup = true;
  queryParams.value.tagIds = [];
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  rawHandleAdd(loadProductOptions);
}

/** 修改按钮操作 */
function handleUpdate(row?: DeviceVO) {
  return rawHandleUpdate(row, loadProductOptions);
}

/** 查看按钮操作 */
function handleView(row?: DeviceVO) {
  return rawHandleView(row, loadProductOptions);
}

/** 加载产品下拉选项 */
function loadProductOptions() {
  listProduct({ pageNum: 1, pageSize: 999 }).then((response) => {
    productOptions.value = response.rows;
  });
}

/** 实时数据按钮操作 */
function handleRealtime(row: DeviceVO) {
  router.push('/iot/device/realtime/' + row.deviceId);
}

/** 跳转到 MES 设备台账（带 equipmentId 查询参数） */
function goToMesEquipment(mesEquipmentId: number) {
  router.push({ path: '/mes/eq-maintain/equipment', query: { equipmentId: String(mesEquipmentId) } });
}

/** 关联 MES 设备对话框 */
const mesBindDialog = reactive<{
  visible: boolean;
  deviceId: number | null;
  deviceCode: string;
  deviceName: string;
  mesEquipmentId: number | null | undefined;
  mesEquipmentList: any[];
}>({
  visible: false,
  deviceId: null,
  deviceCode: '',
  deviceName: '',
  mesEquipmentId: null,
  mesEquipmentList: []
});

/** 打开关联 MES 设备对话框 */
async function handleBindMes(row: any) {
  mesBindDialog.deviceId = row.deviceId;
  mesBindDialog.deviceCode = row.deviceCode;
  mesBindDialog.deviceName = row.deviceName;
  mesBindDialog.mesEquipmentId = row.mesEquipmentId ?? null;
  try {
    const res = await listMesEquipmentForBind({ pageNum: 1, pageSize: 1000 });
    mesBindDialog.mesEquipmentList = res.rows || [];
  } catch {
    mesBindDialog.mesEquipmentList = [];
  }
  mesBindDialog.visible = true;
}

/** 提交关联 MES 设备 */
async function submitMesBind() {
  if (!mesBindDialog.deviceId) return;
  try {
    await updateMesEquipmentLink(mesBindDialog.deviceId, mesBindDialog.mesEquipmentId ?? null);
    proxy.$modal.msgSuccess('关联成功');
    mesBindDialog.visible = false;
    getList();
  } catch {
    proxy.$modal.msgError('关联失败');
  }
}

/** 加载分组树（筛选下拉 + 运维详情名称映射） */
function loadGroupOptions() {
  listDeviceGroup()
    .then((res: any) => {
      const flat = (res.data || []) as DeviceGroupVO[];
      const map: Record<string, string> = {};
      flat.forEach((g) => {
        map[String(g.groupId)] = g.groupName;
      });
      groupNameMap.value = map;
      groupTree.value = proxy.handleTree(flat, 'groupId', 'parentId') || [];
    })
    .catch(() => {
      groupTree.value = [];
    });
}

/** 加载标签下拉（筛选 + 运维详情名称映射） */
function loadTagOptions() {
  optionDeviceTag()
    .then((res: any) => {
      const list = (res.data || []) as DeviceTagVO[];
      tagOptions.value = list;
      const map: Record<string, string> = {};
      list.forEach((t) => {
        map[String(t.tagId)] = t.tagName;
      });
      tagNameMap.value = map;
    })
    .catch(() => {
      tagOptions.value = [];
    });
}

/** 运维详情：在线状态 / 最后在线 / 最后上报 / 固件版本 / 分组标签 */
function handleOps(row: DeviceVO) {
  ops.value = {};
  opsOpen.value = true;
  opsLoading.value = true;
  deviceOps(row.deviceId)
    .then((res) => {
      ops.value = res.data || {};
    })
    .catch(() => {
      ops.value = {};
    })
    .finally(() => {
      opsLoading.value = false;
    });
}

/** 打开生命周期变更对话框 */
function openLifecycle(row: DeviceVO) {
  const current = row.status || 'ACTIVE';
  const firstTarget = (iot_device_status.value || []).find((d: any) => d.value !== current);
  lifecycleForm.value = {
    deviceId: row.deviceId,
    deviceCode: row.deviceCode,
    deviceName: row.deviceName,
    currentStatus: current,
    status: firstTarget?.value || 'INACTIVE',
    remark: ''
  };
  lifecycleOpen.value = true;
}

/** 提交生命周期变更（注销为终态，二次强确认） */
function submitLifecycle() {
  lifecycleRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    const payload: DeviceLifecycleForm = { status: lifecycleForm.value.status, remark: lifecycleForm.value.remark };
    const doSubmit = () => {
      lifecycleLoading.value = true;
      changeDeviceLifecycle(lifecycleForm.value.deviceId as string, payload)
        .then(() => {
          proxy.$modal.msgSuccess('状态变更成功');
          lifecycleOpen.value = false;
          getList();
          loadGroupOptions();
        })
        .finally(() => {
          lifecycleLoading.value = false;
        });
    };
    if (payload.status === 'DECOMMISSIONED') {
      proxy.$modal
        .confirm('注销为终态操作，设备将不可再恢复为在用状态（历史数据仍可追溯），是否继续？', '重要提示')
        .then(doSubmit)
        .catch(() => {});
      return;
    }
    doSubmit();
  });
}

loadProductOptions();
loadGroupOptions();
loadTagOptions();
getList();
</script>

<style scoped>
.group-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.group-filter-select {
  flex: 1;
}
.status-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.ops-tag {
  margin-right: 6px;
}
</style>
