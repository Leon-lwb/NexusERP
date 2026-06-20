<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="采集点ID" prop="pointId">
          <el-input v-model.number="queryParams.pointId" placeholder="采集点ID" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="工单ID" prop="mesWoId">
          <el-input v-model.number="queryParams.mesWoId" placeholder="工单ID" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-select v-model="queryParams.source" placeholder="请选择来源" clearable style="width: 120px">
            <el-option label="手动" value="MANUAL" />
            <el-option label="IoT" value="IoT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:dc:record:add']">录入数据</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="记录ID" prop="recordId" width="120" align="right" />
        <el-table-column label="采集点编码" prop="pointCode" width="150" />
        <el-table-column label="数值" prop="numericValue" width="120" align="right" />
        <el-table-column label="文本值" prop="textValue" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.textValue || '-' }}</template>
        </el-table-column>
        <el-table-column label="工单ID" prop="mesWoId" width="100" align="right">
          <template #default="scope">{{ scope.row.mesWoId || '-' }}</template>
        </el-table-column>
        <el-table-column label="流转卡ID" prop="cardId" width="100" align="right">
          <template #default="scope">{{ scope.row.cardId || '-' }}</template>
        </el-table-column>
        <el-table-column label="采集时间" prop="collectTime" width="160" />
        <el-table-column label="来源" prop="source" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.source === 'IoT' ? 'primary' : 'info'" size="small">{{ scope.row.source === 'IoT' ? 'IoT' : '手动' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 录入数据对话框 -->
    <el-dialog v-model="addDialog.visible" title="录入采集数据" width="520px" append-to-body>
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="采集点ID" required>
          <el-input v-model.number="addForm.pointId" placeholder="采集点ID" clearable />
        </el-form-item>
        <el-form-item label="数值">
          <el-input-number v-model="addForm.numericValue" :precision="4" controls-position="right" style="width: 100%" placeholder="数值(用于SPC)" />
        </el-form-item>
        <el-form-item label="文本值">
          <el-input v-model="addForm.textValue" type="textarea" :rows="2" placeholder="文本值(可选)" />
        </el-form-item>
        <el-form-item label="关联工单ID">
          <el-input v-model.number="addForm.mesWoId" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="关联流转卡ID">
          <el-input v-model.number="addForm.cardId" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="采集时间">
          <el-date-picker
            v-model="addForm.collectTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空默认当前时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确认录入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesDcDataRecord">
import { listDataRecord, addDataRecord } from '@/api/mes/dc/data-record';
import type { DcDataRecordVO, DcDataRecordQuery, DcDataRecordForm } from '@/api/mes/dc/data-record';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<DcDataRecordVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<DcDataRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  pointId: undefined,
  mesWoId: undefined,
  source: undefined
});

const addDialog = reactive({ visible: false });
const addForm = reactive<DcDataRecordForm>({
  pointId: undefined as unknown as number,
  numericValue: undefined,
  textValue: '',
  mesWoId: undefined,
  cardId: undefined,
  collectTime: undefined,
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listDataRecord(queryParams.value);
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

const handleOpenAdd = () => {
  Object.assign(addForm, {
    pointId: undefined as unknown as number,
    numericValue: undefined,
    textValue: '',
    mesWoId: undefined,
    cardId: undefined,
    collectTime: undefined,
    remark: ''
  });
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.pointId) {
    proxy?.$modal.msgWarning('请输入采集点ID');
    return;
  }
  await addDataRecord(addForm);
  proxy?.$modal.msgSuccess('录入成功');
  addDialog.visible = false;
  getList();
};

onMounted(() => {
  getList();
});
</script>
