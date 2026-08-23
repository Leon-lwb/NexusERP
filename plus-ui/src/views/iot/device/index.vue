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
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['iot:device:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['iot:device:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['iot:device:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['iot:device:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['iot:device:export']">导出</el-button>
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
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['iot:device:edit']">修改</el-button>
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
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in iot_device_status" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
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
        <el-button type="primary" @click="submitMesBind">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IotDevice">
import { listDevice, getDevice, delDevice, addDevice, updateDevice } from '@/api/iot/device';
import { listProduct } from '@/api/iot/product';
import { listMesEquipmentForBind, updateMesEquipmentLink } from '@/api/mes/eq/equipment';
import { useRouter } from 'vue-router';

const router = useRouter();
const { proxy } = getCurrentInstance();
const { iot_protocol_type, iot_connection_status, iot_device_status } = toRefs<any>(
  proxy.useDict('iot_protocol_type', 'iot_connection_status', 'iot_device_status')
);

const deviceList = ref([]);
const productOptions = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    deviceCode: null,
    deviceName: null,
    protocolType: null,
    status: null
  },
  rules: {
    productId: [{ required: true, message: '所属产品不能为空', trigger: 'change' }],
    deviceCode: [{ required: true, message: '设备编码不能为空', trigger: 'blur' }],
    deviceName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询设备列表 */
function getList() {
  loading.value = true;
  listDevice(queryParams.value).then((response) => {
    deviceList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 加载产品下拉选项 */
function loadProductOptions() {
  listProduct({ pageNum: 1, pageSize: 999 }).then((response) => {
    productOptions.value = response.rows;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
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
  };
  proxy.resetForm('deviceRef');
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.deviceId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 实时数据按钮操作 */
function handleRealtime(row) {
  proxy.$router.push('/iot/device/realtime/' + row.deviceId);
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加设备';
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  isViewMode.value = false;
  const _deviceId = row.deviceId || ids.value;
  getDevice(_deviceId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '修改设备';
  });
}

/** 查看按钮操作 */
function handleView(row) {
  reset();
  isViewMode.value = true;
  const _deviceId = row.deviceId || ids.value;
  getDevice(_deviceId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '查看设备';
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['deviceRef'].validate((valid) => {
    if (valid) {
      if (form.value.deviceId != null) {
        updateDevice(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addDevice(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _deviceIds = row.deviceId || ids.value;
  proxy.$modal
    .confirm('是否确认删除设备编号为"' + _deviceIds + '"的数据项？')
    .then(function () {
      return delDevice(_deviceIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    'iot/device/export',
    {
      ...queryParams.value
    },
    `iot_device_${new Date().getTime()}.xlsx`
  );
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

loadProductOptions();
getList();
</script>
