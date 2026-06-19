<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="采集点编码" prop="pointCode">
          <el-input v-model="queryParams.pointCode" placeholder="请输入采集点编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="采集点名称" prop="pointName">
          <el-input v-model="queryParams.pointName" placeholder="请输入采集点名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="pointType">
          <el-select v-model="queryParams.pointType" placeholder="请选择类型" clearable style="width: 130px">
            <el-option label="手动" value="MANUAL" />
            <el-option label="自动" value="AUTO" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:dc:point:add']">新增采集点</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="采集点编码" prop="pointCode" width="150" />
        <el-table-column label="采集点名称" prop="pointName" width="180" />
        <el-table-column label="类型" prop="pointType" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.pointType === 'AUTO' ? 'primary' : 'info'">{{ scope.row.pointType === 'AUTO' ? '自动' : '手动' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数据类型" prop="dataType" width="100">
          <template #default="scope">{{ scope.row.dataType || '-' }}</template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="90">
          <template #default="scope">{{ scope.row.unit || '-' }}</template>
        </el-table-column>
        <el-table-column label="频率(秒)" prop="frequency" width="90" align="right">
          <template #default="scope">{{ scope.row.frequency ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="设备ID" prop="equipmentId" width="90" align="right">
          <template #default="scope">{{ scope.row.equipmentId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(scope.row)" v-hasPermi="['mes:dc:point:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:dc:point:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑采集点对话框 -->
    <el-dialog v-model="dialog.visible" :title="form.pointId ? '编辑采集点' : '新增采集点'" width="560px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="采集点编码" required>
          <el-input v-model="form.pointCode" placeholder="采集点编码" clearable />
        </el-form-item>
        <el-form-item label="采集点名称" required>
          <el-input v-model="form.pointName" placeholder="采集点名称" clearable />
        </el-form-item>
        <el-form-item label="采集点类型" required>
          <el-select v-model="form.pointType" placeholder="请选择类型" style="width: 100%">
            <el-option label="手动" value="MANUAL" />
            <el-option label="自动(预留)" value="AUTO" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="form.dataType" placeholder="请选择数据类型" clearable style="width: 100%">
            <el-option label="数值" value="NUMERIC" />
            <el-option label="文本" value="TEXT" />
            <el-option label="枚举" value="ENUM" />
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
        <el-form-item label="关联设备ID">
          <el-input v-model.number="form.equipmentId" placeholder="可选, 设备ID" clearable />
        </el-form-item>
        <el-form-item label="关联工序ID">
          <el-input v-model.number="form.processId" placeholder="可选, 工序ID" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="状态" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
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
import type { DcCollectionPointVO, DcCollectionPointQuery, DcCollectionPointForm } from '@/api/mes/dc/collection-point';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<DcCollectionPointVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<DcCollectionPointQuery>({
  pageNum: 1,
  pageSize: 10,
  pointCode: undefined,
  pointName: undefined,
  pointType: undefined,
  status: undefined
});

const dialog = reactive({ visible: false });
const form = ref<DcCollectionPointForm>({} as DcCollectionPointForm);

const getList = async () => {
  loading.value = true;
  try {
    const res = await listCollectionPoint(queryParams.value);
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
    pointId: undefined,
    pointCode: '',
    pointName: '',
    pointType: 'MANUAL',
    equipmentId: undefined,
    processId: undefined,
    dataType: 'NUMERIC',
    unit: '',
    frequency: undefined,
    status: 'ACTIVE',
    remark: ''
  };
};

const handleOpenAdd = () => {
  resetForm();
  dialog.visible = true;
};

const handleOpenEdit = async (row: DcCollectionPointVO) => {
  const res = await getCollectionPoint(row.pointId);
  form.value = { ...res.data };
  dialog.visible = true;
};

const submitForm = async () => {
  if (!form.value.pointCode || !form.value.pointName || !form.value.pointType) {
    proxy?.$modal.msgWarning('采集点编码/名称/类型不能为空');
    return;
  }
  if (form.value.pointId) {
    await updateCollectionPoint(form.value);
    proxy?.$modal.msgSuccess('编辑成功');
  } else {
    await addCollectionPoint(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  getList();
};

const handleDelete = async (row: DcCollectionPointVO) => {
  await proxy?.$modal.confirm('确认删除采集点"' + row.pointName + '"？');
  await delCollectionPoint([row.pointId]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

onMounted(() => {
  getList();
});
</script>
