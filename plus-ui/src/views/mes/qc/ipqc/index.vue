<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="IPQC编号" prop="ipqcNo">
              <el-input v-model="queryParams.ipqcNo" placeholder="请输入IPQC编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="工单编号" prop="woNo">
              <el-input v-model="queryParams.woNo" placeholder="工单编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option label="待检验" value="PENDING" />
                <el-option label="检验中" value="INSPECTING" />
                <el-option label="已完成" value="COMPLETED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:qc:ipqc:add']">新增IPQC</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="IPQC编号" prop="ipqcNo" />
        <el-table-column label="工单编号" prop="woNo">
          <template #default="scope">{{ scope.row.woNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="工序" prop="processName" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.processName || '-' }}</template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode">
          <template #default="scope">{{ scope.row.materialCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="抽样数量" prop="sampleQty" align="right" />
        <el-table-column label="不良数量" prop="defectQty" align="right" />
        <el-table-column label="不良率(%)" prop="defectRate" align="right">
          <template #default="scope">{{ scope.row.defectRate ?? '-' }}</template>
        </el-table-column>
        <!-- 技术债:状态暂无对应字典,使用硬编码 el-tag,待补字典后改 dict-tag -->
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 技术债:检验结果暂无对应字典,使用硬编码 el-tag,待补字典后改 dict-tag -->
        <el-table-column label="检验结果" prop="inspectResult" align="center">
          <template #default="scope">
            <el-tag :type="resultTagType(scope.row.inspectResult)">{{ resultLabel(scope.row.inspectResult) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检验员" prop="inspectorName">
          <template #default="scope">{{ scope.row.inspectorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="Check"
              @click="handleOpenInspect(scope.row)"
              v-if="scope.row.status !== 'COMPLETED'"
              v-hasPermi="['mes:qc:ipqc:inspect']"
              >检验</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增IPQC对话框 -->
    <el-dialog v-model="addDialog.visible" title="新增IPQC检验单" width="560px" append-to-body>
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="工单ID" required>
          <el-input v-model.number="addForm.mesWoId" placeholder="MES工单ID" clearable />
        </el-form-item>
        <el-form-item label="工单编号">
          <el-input v-model="addForm.woNo" placeholder="工单编号(冗余)" clearable />
        </el-form-item>
        <el-form-item label="流转卡ID">
          <el-input v-model.number="addForm.cardId" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="工序编码">
          <el-input v-model="addForm.processCode" placeholder="工序编码" clearable />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="addForm.processName" placeholder="工序名称" clearable />
        </el-form-item>
        <el-form-item label="物料ID" required>
          <el-input v-model.number="addForm.materialId" placeholder="物料ID" clearable />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="addForm.materialCode" placeholder="物料编码(冗余)" clearable />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="addForm.materialName" placeholder="物料名称(冗余)" clearable />
        </el-form-item>
        <el-form-item label="抽样数量">
          <el-input-number v-model="addForm.sampleQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确认新增</el-button>
      </template>
    </el-dialog>

    <!-- 检验对话框 -->
    <el-dialog v-model="inspectDialog.visible" :title="'IPQC检验 - ' + inspectForm.ipqcNo" width="520px" append-to-body>
      <el-form :model="inspectForm" label-width="100px">
        <el-form-item label="检验结果" required>
          <el-radio-group v-model="inspectForm.inspectResult">
            <el-radio value="QUALIFIED">合格</el-radio>
            <el-radio value="UNQUALIFIED">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="抽样数量">
          <el-input-number v-model="inspectForm.sampleQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良数量">
          <el-input-number v-model="inspectForm.defectQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良品信息">
          <el-input v-model="inspectForm.defectInfo" type="textarea" :rows="3" placeholder='不良品信息JSON, 如: [{"type":"尺寸超差","qty":1}]' />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="inspectForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspectDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitInspect">确认检验</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesQcIpqc">
import { listIpqc, addIpqc, inspectIpqc } from '@/api/mes/qc/ipqc';
import type { QcIpqcVO, QcIpqcQuery, QcIpqcForm } from '@/api/mes/qc/ipqc';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<QcIpqcVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<QcIpqcQuery>({
  pageNum: 1,
  pageSize: 10,
  ipqcNo: undefined,
  woNo: undefined,
  status: undefined
});

const addDialog = reactive({ visible: false });
const addForm = reactive<QcIpqcForm>({
  mesWoId: undefined as unknown as number,
  woNo: '',
  cardId: undefined,
  processCode: '',
  processName: '',
  materialId: undefined as unknown as number,
  materialCode: '',
  materialName: '',
  sampleQty: undefined,
  remark: ''
});

const inspectDialog = reactive({ visible: false });
const inspectForm = reactive<{
  ipqcId: number;
  ipqcNo: string;
  inspectResult: string;
  defectInfo?: string;
  sampleQty?: number;
  defectQty?: number;
  remark?: string;
}>({
  ipqcId: 0,
  ipqcNo: '',
  inspectResult: 'QUALIFIED',
  defectInfo: '',
  sampleQty: undefined,
  defectQty: 0,
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listIpqc(queryParams.value);
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
    mesWoId: undefined as unknown as number,
    woNo: '',
    cardId: undefined,
    processCode: '',
    processName: '',
    materialId: undefined as unknown as number,
    materialCode: '',
    materialName: '',
    sampleQty: undefined,
    remark: ''
  });
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.mesWoId) {
    proxy?.$modal.msgWarning('请输入工单ID');
    return;
  }
  if (!addForm.materialId) {
    proxy?.$modal.msgWarning('请输入物料ID');
    return;
  }
  await addIpqc(addForm);
  proxy?.$modal.msgSuccess('新增成功');
  addDialog.visible = false;
  getList();
};

const handleOpenInspect = (row: QcIpqcVO) => {
  inspectForm.ipqcId = row.ipqcId;
  inspectForm.ipqcNo = row.ipqcNo;
  inspectForm.inspectResult = 'QUALIFIED';
  inspectForm.defectInfo = '';
  inspectForm.sampleQty = row.sampleQty;
  inspectForm.defectQty = 0;
  inspectForm.remark = '';
  inspectDialog.visible = true;
};

const submitInspect = async () => {
  await inspectIpqc(
    inspectForm.ipqcId,
    inspectForm.inspectResult,
    inspectForm.defectInfo,
    inspectForm.sampleQty,
    inspectForm.defectQty,
    inspectForm.remark
  );
  proxy?.$modal.msgSuccess('检验成功');
  inspectDialog.visible = false;
  getList();
};

const statusLabel = (s: string) => ({ PENDING: '待检验', INSPECTING: '检验中', COMPLETED: '已完成' })[s] || s;
const statusTagType = (s: string) => ({ PENDING: 'info', INSPECTING: 'warning', COMPLETED: 'success' })[s] || 'info';
const resultLabel = (r: string) => ({ QUALIFIED: '合格', UNQUALIFIED: '不合格', PENDING: '待定' })[r] || r;
const resultTagType = (r: string) => ({ QUALIFIED: 'success', UNQUALIFIED: 'danger', PENDING: 'info' })[r] || 'info';

onMounted(() => {
  getList();
});
</script>

