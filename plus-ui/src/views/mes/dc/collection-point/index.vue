<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="采集点编码" prop="pointCode">
            <el-input v-model="queryParams.pointCode" placeholder="请输入采集点编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="采集点名称" prop="pointName">
            <el-input v-model="queryParams.pointName" placeholder="请输入采集点名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="类型" prop="pointType">
            <el-select v-model="queryParams.pointType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="dict in dc_collection_point_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_enable_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:dc:point:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['mes:dc:point:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:dc:point:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['mes:dc:point:query']">查看</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="采集点编码" prop="pointCode" />
      <el-table-column label="采集点名称" prop="pointName" min-width="120" show-overflow-tooltip />
      <el-table-column label="类型" prop="pointType" align="center">
        <template #default="scope">
          <dict-tag :options="dc_collection_point_type" :value="scope.row.pointType" />
        </template>
      </el-table-column>
      <el-table-column label="IoT设备" align="center" width="90">
        <template #default="scope">{{ scope.row.iotDeviceId ? '已绑定' : '-' }}</template>
      </el-table-column>
      <el-table-column label="数据类型" prop="dataType" align="center">
        <template #default="scope">
          <dict-tag :options="dc_data_type" :value="scope.row.dataType" />
        </template>
      </el-table-column>
      <el-table-column label="单位" prop="unit">
        <template #default="scope">{{ scope.row.unit || '-' }}</template>
      </el-table-column>
      <el-table-column label="频率(秒)" prop="frequency" align="right">
        <template #default="scope">{{ scope.row.frequency ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center">
        <template #default="scope">
          <dict-tag :options="mes_enable_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:dc:point:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:dc:point:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加/修改/查看采集点对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="collectionPointRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="采集点编码" prop="pointCode">
          <el-input v-model="form.pointCode" placeholder="采集点编码" clearable />
        </el-form-item>
        <el-form-item label="采集点名称" prop="pointName">
          <el-input v-model="form.pointName" placeholder="采集点名称" clearable />
        </el-form-item>
        <el-form-item label="采集点类型" prop="pointType">
          <el-select v-model="form.pointType" placeholder="请选择类型" style="width: 100%" @change="onPointTypeChange">
            <el-option v-for="dict in dc_collection_point_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <template v-if="form.pointType === 'AUTO'">
          <el-form-item label="关联IoT设备" prop="iotDeviceId">
            <el-select v-model="form.iotDeviceId" placeholder="请选择 IoT 设备" clearable filterable style="width: 100%">
              <el-option v-for="d in iotDeviceList" :key="d.deviceId" :label="d.deviceCode + ' - ' + d.deviceName" :value="d.deviceId" />
            </el-select>
          </el-form-item>
          <el-form-item label="IoT属性编码">
            <el-input v-model="form.iotPropertyCode" placeholder="对应 IoT 属性编码, 如 temperature; 留空归档全部属性" clearable />
          </el-form-item>
        </template>
        <el-form-item label="数据类型">
          <el-select v-model="form.dataType" placeholder="请选择数据类型" clearable style="width: 100%">
            <el-option v-for="dict in dc_data_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="单位, 如 ℃/mm" clearable />
        </el-form-item>
        <el-form-item label="采集频率(秒)">
          <el-input-number
            v-model="form.frequency"
            :min="1"
            :precision="0"
            controls-position="right"
            style="width: 100%"
            placeholder="自动采集频率(秒)"
          />
        </el-form-item>
        <el-form-item label="关联设备">
          <equipment-select v-model="form.equipmentId" />
        </el-form-item>
        <el-form-item label="关联工序">
          <el-input v-model="processCodeDisplay" placeholder="可选, 选择工序" readonly clearable @clear="form.processId = undefined">
            <template #append>
              <el-button icon="Search" @click="openProcessSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="状态" style="width: 100%">
            <el-option v-for="dict in mes_enable_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 工序选择对话框 -->
    <el-dialog v-model="processDialog.visible" title="选择工序" width="800px" append-to-body>
      <el-form :model="processDialog.query" :inline="true">
        <el-form-item label="工序编码">
          <el-input v-model="processDialog.query.processCode" clearable @keyup.enter="queryProcesses()" />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="processDialog.query.processName" clearable @keyup.enter="queryProcesses()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryProcesses()">搜索</el-button>
          <el-button icon="Refresh" @click="resetProcessQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="processDialog.list" height="400" @row-dblclick="selectProcess">
        <el-table-column label="工序编码" prop="processCode" min-width="140" />
        <el-table-column label="工序名称" prop="processName" min-width="160" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectProcess(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="processDialog.total > 0"
        :total="processDialog.total"
        v-model:page="processDialog.query.pageNum"
        v-model:limit="processDialog.query.pageSize"
        @pagination="queryProcesses"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesDcCollectionPoint">
import {
  listCollectionPoint,
  getCollectionPoint,
  addCollectionPoint,
  updateCollectionPoint,
  delCollectionPoint
} from '@/api/mes/dc/collection-point';
import type { DcCollectionPointVO, DcCollectionPointQuery } from '@/api/mes/dc/collection-point';
import { listProcess, getProcess } from '@/api/erp/bd/process';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';
import { listIotDeviceForBind } from '@/api/iot/device';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { dc_collection_point_type, dc_data_type, mes_enable_status } = toRefs<any>(
  proxy?.useDict('dc_collection_point_type', 'dc_data_type', 'mes_enable_status')
);

const collectionPointRef = ref<FormInstance>();
// 工序展示字段(后端 BO 忽略, 按 processId 反查回填)
const processCodeDisplay = ref('');

const iotDeviceList = ref<any[]>([]);
const loadIotDevices = async () => {
  try {
    const res = await listIotDeviceForBind({ pageNum: 1, pageSize: 1000 });
    iotDeviceList.value = res.rows || [];
  } catch {
    iotDeviceList.value = [];
  }
};

const processDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, processCode: '' as string, processName: '' as string }
});

const queryParams = ref<DcCollectionPointQuery>({
  pageNum: 1,
  pageSize: 10,
  pointCode: undefined,
  pointName: undefined,
  pointType: undefined,
  status: undefined
});

const rules: ElFormRules = {
  pointCode: [{ required: true, message: '采集点编码不能为空', trigger: 'blur' }],
  pointName: [{ required: true, message: '采集点名称不能为空', trigger: 'blur' }],
  pointType: [{ required: true, message: '采集点类型不能为空', trigger: 'change' }],
  // AUTO 类型必须关联 IoT 设备(表单项仅在 AUTO 时渲染)
  iotDeviceId: [{ required: true, message: 'AUTO 类型采集点必须关联 IoT 设备', trigger: 'change' }]
};

const {
  loading,
  list,
  total,
  open,
  isViewMode,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleView: rawHandleView
} = useCrud({
  apis: {
    list: listCollectionPoint,
    get: getCollectionPoint,
    add: addCollectionPoint,
    update: updateCollectionPoint,
    // useCrud 删除可能传单个 id 或数组, 统一包装为数组
    del: (ids: any) => delCollectionPoint(Array.isArray(ids) ? ids : [ids])
  },
  idField: 'pointId',
  entityName: '采集点',
  formRef: collectionPointRef,
  queryParams,
  initForm: () => ({
    pointId: undefined,
    pointCode: '',
    pointName: '',
    pointType: 'MANUAL',
    equipmentId: undefined,
    iotDeviceId: undefined,
    iotPropertyCode: undefined,
    processId: undefined,
    dataType: 'NUMERIC',
    unit: '',
    frequency: undefined,
    status: 'ACTIVE',
    remark: ''
  }),
  onReset: () => {
    processCodeDisplay.value = '';
  },
  afterLoad: (data: DcCollectionPointVO) => {
    // 回填工序编码(VO 不带 processCode, 按 processId 反查)
    if (data.processId) {
      getProcess(data.processId)
        .then((res: any) => {
          processCodeDisplay.value = res.data?.processCode || '';
        })
        .catch(() => {
          processCodeDisplay.value = '';
        });
    } else {
      processCodeDisplay.value = '';
    }
  }
});

function handleAdd() {
  rawHandleAdd(loadIotDevices);
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row, loadIotDevices);
}
function handleView(row?: any) {
  return rawHandleView(row, loadIotDevices);
}

/** 切换采集点类型: 非 AUTO 清空 IoT 绑定字段 */
const onPointTypeChange = (val: string) => {
  if (val !== 'AUTO') {
    form.value.iotDeviceId = undefined;
    form.value.iotPropertyCode = undefined;
  }
};

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

// ============ 工序选择 ============
const openProcessSelect = () => {
  processDialog.visible = true;
  queryProcesses();
};
const queryProcesses = () => {
  listProcess(processDialog.query as any).then((res: any) => {
    processDialog.list = res.rows || [];
    processDialog.total = res.total || 0;
  });
};
const resetProcessQuery = () => {
  processDialog.query = { pageNum: 1, pageSize: 10, processCode: '', processName: '' };
  queryProcesses();
};
const selectProcess = (row: any) => {
  form.value.processId = row.processId;
  processCodeDisplay.value = row.processCode;
  processDialog.visible = false;
};

getList();
</script>
