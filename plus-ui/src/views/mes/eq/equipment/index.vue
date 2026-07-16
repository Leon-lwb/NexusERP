<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="设备编码" prop="equipmentCode">
              <el-input v-model="queryParams.equipmentCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="设备名称" prop="equipmentName">
              <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="设备类型" prop="equipmentType">
              <el-select v-model="queryParams.equipmentType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option v-for="d in mes_equipment_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="d in mes_equipment_status" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24" class="search-actions" style="text-align: right">
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:eq:equipment:add']">新增设备</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="selectedIds.length === 0"
            @click="handleBatchDelete"
            v-hasPermi="['mes:eq:equipment:remove']"
            >批量删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['mes:eq:equipment:export']">导出</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="设备编码" prop="equipmentCode" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
        <el-table-column label="类型" prop="equipmentType" align="center">
          <template #default="scope">
            <dict-tag v-if="scope.row.equipmentType" :options="mes_equipment_type" :value="scope.row.equipmentType" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="型号" prop="model">
          <template #default="scope">{{ scope.row.model || '-' }}</template>
        </el-table-column>
        <el-table-column label="工作中心" prop="workcenterCode">
          <template #default="scope">{{ scope.row.workcenterCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="安装位置" prop="location" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.location || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <dict-tag :options="mes_equipment_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="IoT 在线" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.iotStatus === 'ONLINE'" type="success" size="small">在线</el-tag>
            <el-tag v-else-if="scope.row.iotStatus === 'OFFLINE'" type="info" size="small">离线</el-tag>
            <el-tag v-else-if="scope.row.iotStatus === 'UNBOUND'" type="warning" size="small">未关联</el-tag>
            <el-tag v-else type="danger" size="small">未知</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="300" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleOpenDetail(scope.row)">详情</el-button>
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(scope.row)" v-hasPermi="['mes:eq:equipment:edit']">编辑</el-button>
            <el-button v-if="scope.row.iotDeviceId" link type="primary" icon="View" @click="handleViewIotLatest(scope.row)">IoT数据</el-button>
            <el-button link type="primary" icon="Link" @click="handleBindIot(scope.row)" v-hasPermi="['mes:eq:equipment:edit']">关联IoT</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:eq:equipment:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑设备对话框 -->
    <el-dialog v-model="dialog.visible" :title="form.equipmentId ? '编辑设备' : '新增设备'" width="720px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="设备编码" prop="equipmentCode">
              <el-input v-model="form.equipmentCode" placeholder="设备编码" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="equipmentName">
              <el-input v-model="form.equipmentName" placeholder="设备名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-select v-model="form.equipmentType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option v-for="d in mes_equipment_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="form.model" placeholder="型号" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="制造商">
              <el-input v-model="form.manufacturer" placeholder="制造商" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出厂编号">
              <el-input v-model="form.serialNo" placeholder="出厂编号" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作中心">
              <workcenter-select v-model="form.workcenterId" @change="onWorkcenterChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" placeholder="状态" style="width: 100%">
                <el-option v-for="d in mes_equipment_status" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="安装位置">
              <el-input v-model="form.location" placeholder="安装位置" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购置日期">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" placeholder="购置日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保修到期">
              <el-date-picker v-model="form.warrantyDate" type="date" value-format="YYYY-MM-DD" placeholder="保修到期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <!-- 点检项结构化编辑(替代 textarea JSON) -->
          <el-col :span="24">
            <el-form-item label="点检项目">
              <el-table :data="inspectionItemRows" border size="small" style="width: 100%">
                <el-table-column label="序号" type="index" width="60" align="center" />
                <el-table-column label="点检项目" min-width="180">
                  <template #default="{ row }">
                    <el-input v-model="row.item" placeholder="如:油位" />
                  </template>
                </el-table-column>
                <el-table-column label="标准" min-width="180">
                  <template #default="{ row }">
                    <el-input v-model="row.standard" placeholder="如:正常" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template #default="scope">
                    <el-button link type="danger" @click="inspectionItemRows.splice(scope.$index, 1)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" icon="Plus" style="margin-top: 8px" @click="inspectionItemRows.push({ item: '', standard: '' })"
                >添加点检项</el-button
              >
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>

    <!-- IoT 最新数据对话框 -->
    <el-dialog v-model="iotLatestDialog.visible" title="IoT 设备最新数据" width="600px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item v-for="(val, key) in iotLatestDialog.data" :key="key" :label="String(key)">
          {{ val }}
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-if="Object.keys(iotLatestDialog.data).length === 0" description="暂无数据" />
    </el-dialog>

    <!-- 关联 IoT 设备对话框 -->
    <el-dialog v-model="iotBindDialog.visible" title="关联 IoT 设备" width="500px" append-to-body>
      <el-form label-width="120px">
        <el-form-item label="MES 设备">
          <span>{{ iotBindDialog.equipmentCode }} - {{ iotBindDialog.equipmentName }}</span>
        </el-form-item>
        <el-form-item label="IoT 设备">
          <el-select v-model="iotBindDialog.iotDeviceId" placeholder="请选择 IoT 设备" clearable filterable>
            <el-option v-for="d in iotBindDialog.iotDeviceList" :key="d.deviceId" :label="d.deviceCode + ' - ' + d.deviceName" :value="d.deviceId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="iotBindDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitIotBind">确定</el-button>
      </template>
    </el-dialog>

    <!-- 设备详情抽屉(R5: 基本信息+IoT实时+最近点检+维修/保养历史) -->
    <el-drawer v-model="detailDrawer" :title="`设备详情 - ${detailData.equipmentCode || ''} ${detailData.equipmentName || ''}`" size="60%">
      <el-tabs v-model="detailActiveTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="设备编码">{{ detailData.equipmentCode }}</el-descriptions-item>
            <el-descriptions-item label="设备名称">{{ detailData.equipmentName }}</el-descriptions-item>
            <el-descriptions-item label="设备类型">
              <dict-tag :options="mes_equipment_type" :value="detailData.equipmentType" />
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <dict-tag :options="mes_equipment_status" :value="detailData.status" />
            </el-descriptions-item>
            <el-descriptions-item label="型号">{{ detailData.model || '-' }}</el-descriptions-item>
            <el-descriptions-item label="制造商">{{ detailData.manufacturer || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出厂编号">{{ detailData.serialNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工作中心">{{ detailData.workcenterCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="安装位置" :span="2">{{ detailData.location || '-' }}</el-descriptions-item>
            <el-descriptions-item label="购置日期">{{ detailData.purchaseDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="保修到期">{{ detailData.warrantyDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="IoT 设备">{{ detailData.iotDeviceId ? '已关联' : '未关联' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="IoT 实时" name="iot">
          <el-button type="primary" plain icon="Refresh" :loading="detailIotLoading" @click="loadDetailIot">刷新最新数据</el-button>
          <el-descriptions v-if="Object.keys(detailIotData).length > 0" :column="2" border style="margin-top: 12px">
            <el-descriptions-item v-for="(val, key) in detailIotData" :key="key" :label="String(key)">{{ val }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无数据, 点击刷新获取" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="最近点检" name="inspection">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="最近点检时间">{{ parseInspectTime(detailData.lastInspection) }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="detailInspectionItems.length > 0" style="margin-top: 12px; font-weight: 600">点检项目配置</div>
          <el-table v-if="detailInspectionItems.length > 0" :data="detailInspectionItems" border size="small" style="margin-top: 8px">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column label="点检项目" prop="item" />
            <el-table-column label="标准" prop="standard" />
          </el-table>
          <el-empty v-else description="未配置点检项" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="维修历史" name="repair">
          <el-table :data="detailRepairs" border size="small" max-height="420">
            <el-table-column label="维修单号" prop="repairNo" width="150" />
            <el-table-column label="故障类型" prop="faultType" align="center" width="110">
              <template #default="scope">
                <dict-tag v-if="scope.row.faultType" :options="mes_fault_type" :value="scope.row.faultType" size="small" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" prop="status" align="center" width="90">
              <template #default="scope">
                <dict-tag :options="mes_repair_status" :value="scope.row.status" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="停机时长(h)" prop="downtimeHours" align="right" width="100" />
            <el-table-column label="报修时间" prop="createTime" width="160" />
          </el-table>
          <el-empty v-if="detailRepairs.length === 0" description="暂无维修记录" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="保养历史" name="maintenance">
          <el-table :data="detailMaintenance" border size="small" max-height="420">
            <el-table-column label="保养类型" prop="maintenanceType" align="center" width="90">
              <template #default="scope">
                <dict-tag v-if="scope.row.maintenanceType" :options="mes_maintenance_type" :value="scope.row.maintenanceType" size="small" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="执行人" prop="executorName" width="100" />
            <el-table-column label="执行时间" prop="executeTime" width="160" />
            <el-table-column label="结果" prop="result" align="center" width="80">
              <template #default="scope">
                <dict-tag :options="mes_maintenance_result" :value="scope.row.result" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="保养内容" prop="content" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="detailMaintenance.length === 0" description="暂无保养记录" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="MesEqEquipment">
import {
  listEquipment,
  getEquipment,
  addEquipment,
  updateEquipment,
  delEquipment,
  getIotLatest,
  updateIotDeviceLink,
  batchIotStatus,
  exportEquipment
} from '@/api/mes/eq/equipment';
import { listIotDeviceForBind } from '@/api/iot/device';
import type { EqEquipmentVO, EqEquipmentQuery, EqEquipmentForm } from '@/api/mes/eq/equipment';
import WorkcenterSelect from '@/components/WorkcenterSelect/index.vue';
import { listRepair } from '@/api/mes/eq/repair';
import type { EqRepairVO } from '@/api/mes/eq/repair';
import { listMaintenanceRecordByEquipment } from '@/api/mes/eq/maintenance-record';
import type { EqMaintenanceRecordVO } from '@/api/mes/eq/maintenance-record';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const { mes_equipment_status, mes_equipment_type, mes_fault_type, mes_repair_status, mes_maintenance_type, mes_maintenance_result } = toRefs<any>(
  proxy?.useDict(
    'mes_equipment_status',
    'mes_equipment_type',
    'mes_fault_type',
    'mes_repair_status',
    'mes_maintenance_type',
    'mes_maintenance_result'
  )
);

const dataList = ref<EqEquipmentVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const selectedIds = ref<(string | number)[]>([]);

/** 设备详情抽屉(R5) */
const detailDrawer = ref(false);
const detailActiveTab = ref('basic');
const detailData = ref<EqEquipmentVO>({} as EqEquipmentVO);
const detailIotData = ref<Record<string, string>>({});
const detailIotLoading = ref(false);
const detailInspectionItems = ref<{ item: string; standard: string }[]>([]);
const detailRepairs = ref<EqRepairVO[]>([]);
const detailMaintenance = ref<EqMaintenanceRecordVO[]>([]);

const queryParams = ref<EqEquipmentQuery>({
  pageNum: 1,
  pageSize: 10,
  equipmentCode: undefined,
  equipmentName: undefined,
  equipmentType: undefined,
  status: undefined
});

const dialog = reactive({ visible: false });
const formRef = ref();
const formRules = {
  equipmentCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
};
const form = ref<EqEquipmentForm>({} as EqEquipmentForm);

/** 点检项结构化编辑(替代 textarea JSON) */
const inspectionItemRows = ref<{ item: string; standard: string }[]>([]);

const getList = async () => {
  loading.value = true;
  try {
    const res = await listEquipment(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
    // 批量查 IoT 在线状态(单次请求, 消除 N+1)
    await loadIotStatus(dataList.value);
  } finally {
    loading.value = false;
  }
};

/** 批量查询当前页设备的 IoT 在线状态 */
async function loadIotStatus(rows: EqEquipmentVO[]) {
  const ids = rows.filter((r) => r.iotDeviceId).map((r) => r.equipmentId);
  rows.forEach((r) => {
    if (!r.iotDeviceId) r.iotStatus = 'UNBOUND';
  });
  if (ids.length === 0) return;
  try {
    const res = await batchIotStatus(ids);
    const map = res.data || {};
    rows.forEach((r) => {
      if (r.iotDeviceId) {
        r.iotStatus = map[r.equipmentId] || 'UNKNOWN';
      }
    });
  } catch {
    rows.forEach((r) => {
      if (r.iotDeviceId) r.iotStatus = 'UNKNOWN';
    });
  }
}

/** 工作中心选择变更: 同步 workcenterCode 供列表展示 */
function onWorkcenterChange(_val: number | string | null | undefined, item?: { workstation_code?: string }) {
  form.value.workcenterCode = item?.workstation_code || '';
}

/** IoT 最新数据对话框 */
const iotLatestDialog = reactive<{ visible: boolean; data: Record<string, string> }>({
  visible: false,
  data: {}
});

async function handleViewIotLatest(row: EqEquipmentVO) {
  try {
    const res = await getIotLatest(row.equipmentId);
    iotLatestDialog.data = res.data || {};
    iotLatestDialog.visible = true;
  } catch {
    proxy?.$modal.msgError('查询 IoT 数据失败');
  }
}

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

async function handleBindIot(row: EqEquipmentVO) {
  iotBindDialog.equipmentId = row.equipmentId;
  iotBindDialog.equipmentCode = row.equipmentCode;
  iotBindDialog.equipmentName = row.equipmentName;
  iotBindDialog.iotDeviceId = row.iotDeviceId ?? null;
  try {
    const res = await listIotDeviceForBind({ pageNum: 1, pageSize: 1000 });
    iotBindDialog.iotDeviceList = res.rows || [];
  } catch {
    proxy?.$modal.msgError('加载 IoT 设备列表失败');
    iotBindDialog.iotDeviceList = [];
  }
  iotBindDialog.visible = true;
}

async function submitIotBind() {
  if (!iotBindDialog.equipmentId) return;
  try {
    await updateIotDeviceLink(iotBindDialog.equipmentId, iotBindDialog.iotDeviceId ?? null);
    proxy?.$modal.msgSuccess('关联成功');
    iotBindDialog.visible = false;
    await getList();
  } catch {
    proxy?.$modal.msgError('关联失败');
  }
}

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

const resetForm = () => {
  form.value = {
    equipmentId: undefined,
    equipmentCode: '',
    equipmentName: '',
    equipmentType: '',
    model: '',
    manufacturer: '',
    serialNo: '',
    workcenterId: undefined,
    workcenterCode: '',
    location: '',
    purchaseDate: undefined,
    warrantyDate: undefined,
    status: 'NORMAL',
    inspectionItems: '',
    iotDeviceId: undefined,
    remark: ''
  };
  inspectionItemRows.value = [];
};

const handleOpenAdd = () => {
  resetForm();
  dialog.visible = true;
};

const handleOpenEdit = async (row: EqEquipmentVO) => {
  const res = await getEquipment(row.equipmentId);
  form.value = { ...res.data };
  // 解析点检项 JSON 为结构化行
  inspectionItemRows.value = [];
  if (form.value.inspectionItems) {
    try {
      inspectionItemRows.value = JSON.parse(form.value.inspectionItems);
    } catch {
      inspectionItemRows.value = [];
    }
  }
  dialog.visible = true;
};

const submitForm = async () => {
  await formRef.value?.validate();
  // 序列化点检项为 JSON
  form.value.inspectionItems = inspectionItemRows.value.length > 0 ? JSON.stringify(inspectionItemRows.value) : '';
  if (form.value.equipmentId) {
    await updateEquipment(form.value);
    proxy?.$modal.msgSuccess('编辑成功');
  } else {
    await addEquipment(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  getList();
};

const handleDelete = async (row: EqEquipmentVO) => {
  await proxy?.$modal.confirm('确认删除设备"' + row.equipmentName + '"？');
  await delEquipment([row.equipmentId]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleSelectionChange = (selection: EqEquipmentVO[]) => {
  selectedIds.value = selection.map((item) => item.equipmentId);
};

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return;
  await proxy?.$modal.confirm('确认删除选中的 ' + selectedIds.value.length + ' 台设备？');
  await delEquipment(selectedIds.value);
  proxy?.$modal.msgSuccess('删除成功');
  selectedIds.value = [];
  getList();
};

/** 打开设备详情抽屉: 加载基本信息 + 并行加载维修/保养历史 */
const handleOpenDetail = async (row: EqEquipmentVO) => {
  detailActiveTab.value = 'basic';
  detailIotData.value = {};
  detailRepairs.value = [];
  detailMaintenance.value = [];
  detailDrawer.value = true;
  try {
    const res = await getEquipment(row.equipmentId);
    detailData.value = res.data;
    detailInspectionItems.value = [];
    if (res.data.inspectionItems) {
      try {
        detailInspectionItems.value = JSON.parse(res.data.inspectionItems);
      } catch {
        detailInspectionItems.value = [];
      }
    }
  } catch {
    /* 请求拦截器已提示 */
  }
  // 并行加载维修/保养历史(不阻塞抽屉打开)
  Promise.allSettled([
    listRepair({ equipmentId: row.equipmentId as number, pageNum: 1, pageSize: 50 } as any),
    listMaintenanceRecordByEquipment(row.equipmentId)
  ]).then(([repairRes, mtRes]) => {
    if (repairRes.status === 'fulfilled') detailRepairs.value = repairRes.value.rows || [];
    if (mtRes.status === 'fulfilled') detailMaintenance.value = (mtRes.value.data || []) as EqMaintenanceRecordVO[];
  });
};

/** 加载设备 IoT 最新数据(详情抽屉 IoT Tab) */
const loadDetailIot = async () => {
  if (!detailData.value.iotDeviceId) {
    proxy?.$modal.msgWarning('该设备未关联 IoT 设备');
    return;
  }
  detailIotLoading.value = true;
  try {
    const res = await getIotLatest(detailData.value.equipmentId);
    detailIotData.value = res.data || {};
  } catch {
    proxy?.$modal.msgError('查询 IoT 数据失败');
  } finally {
    detailIotLoading.value = false;
  }
};

/** 解析点检记录 JSON 中的点检时间 */
const parseInspectTime = (json?: string): string => {
  if (!json) return '从未点检';
  try {
    return JSON.parse(json).inspectTime || '未知时间';
  } catch {
    return '解析失败';
  }
};

const handleExport = () => {
  exportEquipment(queryParams.value);
};

onMounted(() => {
  getList();
  // IoT「查看MES设备」带 equipmentId 跳转时, 自动弹出该设备编辑框
  // 注意: route.query 取得的是字符串, 雪花ID 18-19 位超出 JS 安全整数, 严禁 Number() 截断
  const eqId = route.query.equipmentId;
  if (eqId) {
    handleOpenEdit({ equipmentId: eqId } as EqEquipmentVO);
  }
});
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions {
  text-align: right;
}
</style>
