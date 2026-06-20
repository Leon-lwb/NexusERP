<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="设备编码" prop="equipmentCode">
          <el-input v-model="queryParams.equipmentCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="设备名称" prop="equipmentName">
          <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option label="正常" value="NORMAL" />
            <el-option label="保养中" value="MAINTENANCE" />
            <el-option label="故障" value="FAULT" />
            <el-option label="已报废" value="SCRAPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:eq:equipment:add']">新增设备</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="设备编码" prop="equipmentCode" width="140" />
        <el-table-column label="设备名称" prop="equipmentName" width="160" />
        <el-table-column label="类型" prop="equipmentType" width="100">
          <template #default="scope">{{ scope.row.equipmentType || '-' }}</template>
        </el-table-column>
        <el-table-column label="型号" prop="model" width="120">
          <template #default="scope">{{ scope.row.model || '-' }}</template>
        </el-table-column>
        <el-table-column label="工作中心" prop="workcenterCode" width="120">
          <template #default="scope">{{ scope.row.workcenterCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="安装位置" prop="location" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.location || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IoT设备ID" prop="iotDeviceId" width="100" align="right">
          <template #default="scope">{{ scope.row.iotDeviceId || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(scope.row)" v-hasPermi="['mes:eq:equipment:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:eq:equipment:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑设备对话框 -->
    <el-dialog v-model="dialog.visible" :title="form.equipmentId ? '编辑设备' : '新增设备'" width="640px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="设备编码" required>
              <el-input v-model="form.equipmentCode" placeholder="设备编码" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" required>
              <el-input v-model="form.equipmentName" placeholder="设备名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-input v-model="form.equipmentType" placeholder="设备类型" clearable />
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
            <el-form-item label="工作中心ID">
              <el-input v-model.number="form.workcenterId" placeholder="ERP bd_workstation ID" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作中心编码">
              <el-input v-model="form.workcenterCode" placeholder="工作中心编码(冗余)" clearable />
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
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" placeholder="状态" style="width: 100%">
                <el-option label="正常" value="NORMAL" />
                <el-option label="保养中" value="MAINTENANCE" />
                <el-option label="故障" value="FAULT" />
                <el-option label="已报废" value="SCRAPPED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="IoT设备ID">
              <el-input v-model.number="form.iotDeviceId" placeholder="预留关联IoT设备" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="点检项目">
              <el-input
                v-model="form.inspectionItems"
                type="textarea"
                :rows="2"
                placeholder='点检项目及标准JSON, 如: [{"item":"油位","standard":"正常"}]'
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="最近点检">
              <el-input v-model="form.lastInspection" type="textarea" :rows="2" placeholder="最近一次点检结果JSON(可选)" />
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
  </div>
</template>

<script setup lang="ts" name="MesEqEquipment">
import { listEquipment, getEquipment, addEquipment, updateEquipment, delEquipment } from '@/api/mes/eq/equipment';
import type { EqEquipmentVO, EqEquipmentQuery, EqEquipmentForm } from '@/api/mes/eq/equipment';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<EqEquipmentVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<EqEquipmentQuery>({
  pageNum: 1,
  pageSize: 10,
  equipmentCode: undefined,
  equipmentName: undefined,
  status: undefined
});

const dialog = reactive({ visible: false });
const form = ref<EqEquipmentForm>({} as EqEquipmentForm);

const getList = async () => {
  loading.value = true;
  try {
    const res = await listEquipment(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

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
    lastInspection: '',
    iotDeviceId: undefined,
    remark: ''
  };
};

const handleOpenAdd = () => {
  resetForm();
  dialog.visible = true;
};

const handleOpenEdit = async (row: EqEquipmentVO) => {
  const res = await getEquipment(row.equipmentId);
  form.value = { ...res.data };
  dialog.visible = true;
};

const submitForm = async () => {
  if (!form.value.equipmentCode || !form.value.equipmentName) {
    proxy?.$modal.msgWarning('设备编码和名称不能为空');
    return;
  }
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

const statusLabel = (s: string) => ({ NORMAL: '正常', MAINTENANCE: '保养中', FAULT: '故障', SCRAPPED: '已报废' })[s] || s;
const statusTagType = (s: string) => ({ NORMAL: 'success', MAINTENANCE: 'warning', FAULT: 'danger', SCRAPPED: 'info' })[s] || 'info';

onMounted(() => {
  getList();
});
</script>
