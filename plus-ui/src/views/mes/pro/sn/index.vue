<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="SN编码" prop="snCode">
          <el-input v-model="queryParams.snCode" placeholder="请输入SN编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="snStatus">
          <el-select v-model="queryParams.snStatus" placeholder="请选择状态" clearable style="width: 140px">
            <el-option label="生产中" value="IN_PRODUCTION" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已报废" value="SCRAPPED" />
            <el-option label="已发货" value="SHIPPED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-select v-model="queryParams.source" placeholder="请选择来源" clearable style="width: 120px">
            <el-option label="手动" value="MANUAL" />
            <el-option label="批量" value="BATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单ID" prop="mesWoId">
          <el-input v-model.number="queryParams.mesWoId" placeholder="请输入工单ID" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="物料ID" prop="materialId">
          <el-input v-model.number="queryParams.materialId" placeholder="请输入物料ID" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleOpenGenerate" v-hasPermi="['mes:pro:sn:add']">批量生成SN</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Edit" @click="handleOpenCreate" v-hasPermi="['mes:pro:sn:add']">手动建SN</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="SN编码" prop="snCode" width="220" />
        <el-table-column label="工单编号" prop="woNo" width="160">
          <template #default="scope">{{ scope.row.woNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="流转卡" prop="cardNo" width="170">
          <template #default="scope">{{ scope.row.cardNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="物料" prop="materialName" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="父SN" prop="parentSnCode" width="180">
          <template #default="scope">{{ scope.row.parentSnCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="snStatus" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.snStatus)">{{ statusLabel(scope.row.snStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" prop="source" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.source === 'BATCH' ? 'primary' : 'info'" size="small">
              {{ scope.row.source === 'BATCH' ? '批量' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" prop="productionDate" width="120" />
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleTrace(scope.row)" v-hasPermi="['mes:pro:sn:query']">追溯</el-button>
            <el-button
              link
              type="success"
              icon="CircleCheck"
              @click="handleComplete(scope.row)"
              v-if="scope.row.snStatus === 'IN_PRODUCTION'"
              v-hasPermi="['mes:pro:sn:complete']"
              >完工</el-button
            >
            <el-button
              link
              type="danger"
              icon="Close"
              @click="handleScrap(scope.row)"
              v-if="scope.row.snStatus === 'IN_PRODUCTION' || scope.row.snStatus === 'COMPLETED'"
              v-hasPermi="['mes:pro:sn:scrap']"
              >报废</el-button
            >
            <el-button
              link
              type="primary"
              icon="Promotion"
              @click="handleShip(scope.row)"
              v-if="scope.row.snStatus === 'COMPLETED'"
              v-hasPermi="['mes:pro:sn:ship']"
              >发货</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 批量生成对话框 -->
    <el-dialog v-model="generateDialog.visible" title="批量生成SN" width="480px" append-to-body>
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="流转卡ID" required>
          <el-input v-model.number="generateForm.cardId" placeholder="请输入流转卡ID" clearable />
        </el-form-item>
        <el-form-item label="生成数量" required>
          <el-input-number v-model="generateForm.quantity" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="初始状态">
          <el-select v-model="generateForm.initialStatus" style="width: 100%">
            <el-option label="生产中" value="IN_PRODUCTION" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitGenerate">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 手动建SN对话框 -->
    <el-dialog v-model="createDialog.visible" title="手动建SN" width="520px" append-to-body>
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="SN编码" required>
          <el-input v-model="createForm.snCode" placeholder="请输入SN编码" clearable />
        </el-form-item>
        <el-form-item label="物料ID" required>
          <el-input v-model.number="createForm.materialId" placeholder="请输入物料ID" clearable />
        </el-form-item>
        <el-form-item label="工单ID">
          <el-input v-model.number="createForm.mesWoId" placeholder="可选, 关联MES工单ID" clearable />
        </el-form-item>
        <el-form-item label="流转卡ID">
          <el-input v-model.number="createForm.cardId" placeholder="可选, 关联流转卡ID" clearable />
        </el-form-item>
        <el-form-item label="父SN ID">
          <el-input v-model.number="createForm.parentSnId" placeholder="可选, 组装追溯父SN ID" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesSn">
import { listSn, generateSnForCard, createSn, completeSn, scrapSn, shipSn } from '@/api/mes/pro/sn';
import type { ProSnVO, ProSnQuery } from '@/api/mes/pro/sn';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const router = useRouter();

const dataList = ref<ProSnVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<ProSnQuery>({
  pageNum: 1,
  pageSize: 10,
  snCode: undefined,
  snStatus: undefined,
  source: undefined,
  mesWoId: undefined,
  materialId: undefined
});

const generateDialog = reactive({ visible: false });
const generateForm = reactive<{ cardId?: number; quantity: number; initialStatus: string }>({
  cardId: undefined,
  quantity: 1,
  initialStatus: 'IN_PRODUCTION'
});

const createDialog = reactive({ visible: false });
const createForm = reactive<{
  snCode: string;
  materialId?: number;
  mesWoId?: number;
  cardId?: number;
  parentSnId?: number;
  remark?: string;
}>({
  snCode: '',
  materialId: undefined,
  mesWoId: undefined,
  cardId: undefined,
  parentSnId: undefined,
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listSn(queryParams.value);
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

const handleOpenGenerate = () => {
  generateForm.cardId = undefined;
  generateForm.quantity = 1;
  generateForm.initialStatus = 'IN_PRODUCTION';
  generateDialog.visible = true;
};

const submitGenerate = async () => {
  if (!generateForm.cardId) {
    proxy?.$modal.msgWarning('请输入流转卡ID');
    return;
  }
  await generateSnForCard(generateForm.cardId, generateForm.quantity, generateForm.initialStatus);
  proxy?.$modal.msgSuccess('生成成功');
  generateDialog.visible = false;
  getList();
};

const handleOpenCreate = () => {
  createForm.snCode = '';
  createForm.materialId = undefined;
  createForm.mesWoId = undefined;
  createForm.cardId = undefined;
  createForm.parentSnId = undefined;
  createForm.remark = '';
  createDialog.visible = true;
};

const submitCreate = async () => {
  if (!createForm.snCode) {
    proxy?.$modal.msgWarning('请输入SN编码');
    return;
  }
  if (!createForm.materialId) {
    proxy?.$modal.msgWarning('请输入物料ID');
    return;
  }
  await createSn(createForm.mesWoId, createForm.cardId, createForm.materialId, createForm.snCode, createForm.parentSnId, createForm.remark);
  proxy?.$modal.msgSuccess('创建成功');
  createDialog.visible = false;
  getList();
};

const handleTrace = (row: ProSnVO) => {
  router.push(`/mes/pro/sn/trace/${row.snId}`);
};

const handleComplete = async (row: ProSnVO) => {
  await proxy?.$modal.confirm('确认完工SN"' + row.snCode + '"？');
  await completeSn(row.snId);
  proxy?.$modal.msgSuccess('完工成功');
  getList();
};

const handleScrap = async (row: ProSnVO) => {
  const { value: remark } = await proxy?.$modal.prompt('请输入报废原因(可选):', '报废 - ' + row.snCode);
  await scrapSn(row.snId, remark || undefined);
  proxy?.$modal.msgSuccess('报废成功');
  getList();
};

const handleShip = async (row: ProSnVO) => {
  await proxy?.$modal.confirm('确认发货SN"' + row.snCode + '"？');
  await shipSn(row.snId);
  proxy?.$modal.msgSuccess('发货成功');
  getList();
};

const statusLabel = (s: string) => ({ IN_PRODUCTION: '生产中', COMPLETED: '已完成', SCRAPPED: '已报废', SHIPPED: '已发货' })[s] || s;
const statusTagType = (s: string) => ({ IN_PRODUCTION: 'warning', COMPLETED: 'success', SCRAPPED: 'danger', SHIPPED: 'info' })[s] || 'info';

onMounted(() => {
  getList();
});
</script>
