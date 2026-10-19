<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="SN编码" prop="snCode">
            <el-input v-model="queryParams.snCode" placeholder="请输入SN编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="snStatus">
            <el-select v-model="queryParams.snStatus" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_sn_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
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
        <el-button type="success" plain icon="Plus" @click="handleOpenGenerate()" v-hasPermi="['mes:pro:sn:add']">批量生成SN</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Edit" @click="handleOpenCreate()" v-hasPermi="['mes:pro:sn:add']">手动建SN</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="SN编码" prop="snCode" min-width="140" />
      <el-table-column label="工单编号" prop="woNo" min-width="140">
        <template #default="scope">{{ scope.row.woNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="流转卡" prop="cardNo" min-width="140">
        <template #default="scope">{{ scope.row.cardNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="物料" prop="materialName" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
      </el-table-column>
      <el-table-column label="父SN" prop="parentSnCode" min-width="140">
        <template #default="scope">{{ scope.row.parentSnCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="生产日期" prop="productionDate" width="160" />
      <el-table-column label="状态" prop="snStatus" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_sn_status" :value="scope.row.snStatus" />
        </template>
      </el-table-column>
      <el-table-column label="来源" prop="source" align="center" min-width="80">
        <template #default="scope">
          <el-tag :type="scope.row.source === 'BATCH' ? 'primary' : 'info'" size="small">
            {{ scope.row.source === 'BATCH' ? '批量' : '手动' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
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
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 批量生成对话框 -->
    <el-dialog v-model="generateDialog.visible" title="批量生成SN" width="500px" append-to-body>
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="流转卡" required>
          <el-input v-model="generateForm.cardNo" placeholder="请选择流转卡" readonly>
            <template #append>
              <el-button icon="Search" @click="openFlowCardSelect('generate')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="生成数量" required>
          <el-input-number v-model="generateForm.quantity" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="初始状态">
          <el-select v-model="generateForm.initialStatus" style="width: 100%">
            <el-option v-for="dict in mes_sn_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitGenerate">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 手动建SN对话框 -->
    <el-dialog v-model="createDialog.visible" title="手动建SN" width="640px" append-to-body>
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="SN编码" required>
          <el-input v-model="createForm.snCode" placeholder="请输入SN编码" clearable />
        </el-form-item>
        <el-form-item label="物料" required>
          <el-input v-model="createForm.materialCode" placeholder="请选择物料" readonly>
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="工单">
          <el-input v-model="createForm.woNo" placeholder="可选,选择工单" readonly>
            <template #append>
              <el-button icon="Search" @click="openWoSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="流转卡">
          <el-input v-model="createForm.cardNo" placeholder="可选,选择流转卡" readonly>
            <template #append>
              <el-button icon="Search" @click="openFlowCardSelect('create')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="父SN">
          <el-input v-model="createForm.parentSnCode" placeholder="可选,选择父SN" readonly>
            <template #append>
              <el-button icon="Search" @click="openSnSelect()" />
            </template>
          </el-input>
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

    <!-- 流转卡选择对话框 -->
    <el-dialog v-model="flowCardDialog.visible" title="选择流转卡" width="960px" append-to-body>
      <el-form :model="flowCardDialog.query" :inline="true">
        <el-form-item label="流转卡号">
          <el-input v-model="flowCardDialog.query.cardNo" clearable @keyup.enter="queryFlowCards" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryFlowCards">搜索</el-button>
          <el-button icon="Refresh" @click="resetFlowCardQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="flowCardDialog.list" height="400" @row-dblclick="selectFlowCard">
        <el-table-column label="流转卡号" prop="cardNo" min-width="140" />
        <el-table-column label="工单编号" prop="woNo" min-width="140" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="cardStatus" width="100" align="center" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectFlowCard(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="flowCardDialog.total > 0"
        :total="flowCardDialog.total"
        v-model:page="flowCardDialog.query.pageNum"
        v-model:limit="flowCardDialog.query.pageSize"
        @pagination="queryFlowCards"
      />
    </el-dialog>

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="960px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号">
          <el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWorkOrders" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryWorkOrders">搜索</el-button>
          <el-button icon="Refresh" @click="resetWoQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="woDialog.list" height="400" @row-dblclick="selectWorkOrder">
        <el-table-column label="工单编号" prop="woNo" min-width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="woStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectWorkOrder(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="woDialog.total > 0"
        :total="woDialog.total"
        v-model:page="woDialog.query.pageNum"
        v-model:limit="woDialog.query.pageSize"
        @pagination="queryWorkOrders"
      />
    </el-dialog>

    <!-- 父SN选择对话框 -->
    <el-dialog v-model="snDialog.visible" title="选择父SN" width="960px" append-to-body>
      <el-form :model="snDialog.query" :inline="true">
        <el-form-item label="SN编码">
          <el-input v-model="snDialog.query.snCode" clearable @keyup.enter="querySns" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="querySns">搜索</el-button>
          <el-button icon="Refresh" @click="resetSnQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="snDialog.list" height="400" @row-dblclick="selectSn">
        <el-table-column label="SN编码" prop="snCode" min-width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="snStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="mes_sn_status" :value="scope.row.snStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectSn(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="snDialog.total > 0"
        :total="snDialog.total"
        v-model:page="snDialog.query.pageNum"
        v-model:limit="snDialog.query.pageSize"
        @pagination="querySns"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </div>
</template>

<script setup lang="ts" name="MesSn">
import { listSn, getSn, generateSnForCard, createSn, completeSn, scrapSn } from '@/api/mes/pro/sn';
import type { ProSnVO, ProSnQuery } from '@/api/mes/pro/sn';
import { listFlowCard } from '@/api/mes/pro/flowcardApi';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_sn_status, prd_wo_status } = toRefs<any>(proxy.useDict('mes_sn_status', 'prd_wo_status'));
const router = useRouter();

const queryParams = ref<ProSnQuery>({
  pageNum: 1,
  pageSize: 10,
  snCode: undefined,
  snStatus: undefined
});

const generateDialog = reactive({ visible: false });
const generateForm = reactive<{ cardId?: number; cardNo: string; quantity: number; initialStatus: string }>({
  cardId: undefined,
  cardNo: '',
  quantity: 1,
  initialStatus: 'IN_PRODUCTION'
});

const createDialog = reactive({ visible: false });
const createForm = reactive<{
  snCode: string;
  materialId?: number;
  materialCode: string;
  materialName: string;
  mesWoId?: number;
  woNo: string;
  cardId?: number;
  cardNo: string;
  parentSnId?: number;
  parentSnCode: string;
  remark?: string;
}>({
  snCode: '',
  materialId: undefined,
  materialCode: '',
  materialName: '',
  mesWoId: undefined,
  woNo: '',
  cardId: undefined,
  cardNo: '',
  parentSnId: undefined,
  parentSnCode: '',
  remark: ''
});

const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const flowCardSelectTarget = ref<'generate' | 'create'>('create');
const flowCardDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, cardNo: '' as string }
});
const woDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' as string }
});
const snDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, snCode: '' as string }
});

// SN 通过批量生成/手动建/工单自动生成，页面无标准新增/修改/删除弹窗（业务操作走本地方法），
// add/update/del 占位仅为满足 useCrud 的 API 签名
const readonlyApi = () => Promise.resolve({ data: {} as any });

// 列表骨架（追溯为路由页面，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud({
  apis: { list: listSn, get: getSn, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'snId',
  entityName: 'SN',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 打开批量生成对话框 */
function handleOpenGenerate() {
  generateForm.cardId = undefined;
  generateForm.cardNo = '';
  generateForm.quantity = 1;
  generateForm.initialStatus = 'IN_PRODUCTION';
  generateDialog.visible = true;
}

/** 提交批量生成 */
async function submitGenerate() {
  if (!generateForm.cardId) {
    proxy.$modal.msgWarning('请选择流转卡');
    return;
  }
  await generateSnForCard(generateForm.cardId, generateForm.quantity, generateForm.initialStatus);
  proxy.$modal.msgSuccess('生成成功');
  generateDialog.visible = false;
  getList();
}

/** 打开手动建SN对话框 */
function handleOpenCreate() {
  Object.assign(createForm, {
    snCode: '',
    materialId: undefined,
    materialCode: '',
    materialName: '',
    mesWoId: undefined,
    woNo: '',
    cardId: undefined,
    cardNo: '',
    parentSnId: undefined,
    parentSnCode: '',
    remark: ''
  });
  createDialog.visible = true;
}

/** 提交手动创建 */
async function submitCreate() {
  if (!createForm.snCode) {
    proxy.$modal.msgWarning('请输入SN编码');
    return;
  }
  if (!createForm.materialId) {
    proxy.$modal.msgWarning('请选择物料');
    return;
  }
  await createSn(createForm.mesWoId, createForm.cardId, createForm.materialId, createForm.snCode, createForm.parentSnId, createForm.remark);
  proxy.$modal.msgSuccess('创建成功');
  createDialog.visible = false;
  getList();
}

/** 追溯按钮操作 */
function handleTrace(row: ProSnVO) {
  router.push(`/mes/pro/sn/trace/${row.snId}`);
}

/** SN完工 */
async function handleComplete(row: ProSnVO) {
  await proxy.$modal.confirm('确认完工SN"' + row.snCode + '"？');
  await completeSn(row.snId);
  proxy.$modal.msgSuccess('完工成功');
  getList();
}

/** SN报废 */
async function handleScrap(row: ProSnVO) {
  const { value: remark } = await proxy.$modal.prompt('请输入报废原因(可选):', '报废 - ' + row.snCode);
  await scrapSn(row.snId, remark || undefined);
  proxy.$modal.msgSuccess('报废成功');
  getList();
}

// ============ 流转卡选择 ============
function openFlowCardSelect(target: 'generate' | 'create') {
  flowCardSelectTarget.value = target;
  flowCardDialog.visible = true;
  queryFlowCards();
}
function queryFlowCards() {
  listFlowCard(flowCardDialog.query as any).then((res: any) => {
    flowCardDialog.list = res.rows || [];
    flowCardDialog.total = res.total || 0;
  });
}
function resetFlowCardQuery() {
  flowCardDialog.query = { pageNum: 1, pageSize: 10, cardNo: '' };
  queryFlowCards();
}
function selectFlowCard(row: any) {
  if (flowCardSelectTarget.value === 'generate') {
    generateForm.cardId = row.cardId;
    generateForm.cardNo = row.cardNo;
  } else {
    createForm.cardId = row.cardId;
    createForm.cardNo = row.cardNo;
    // 顺带带出物料/工单(若空)
    if (!createForm.materialId && row.materialId) {
      createForm.materialId = row.materialId;
      createForm.materialCode = row.materialCode;
      createForm.materialName = row.materialName;
    }
    if (!createForm.mesWoId && row.mesWoId) {
      createForm.mesWoId = row.mesWoId;
      createForm.woNo = row.woNo;
    }
  }
  flowCardDialog.visible = false;
}

// ============ 工单选择 ============
function openWoSelect() {
  woDialog.visible = true;
  queryWorkOrders();
}
function queryWorkOrders() {
  listWorkOrder(woDialog.query as any).then((res: any) => {
    woDialog.list = res.rows || [];
    woDialog.total = res.total || 0;
  });
}
function resetWoQuery() {
  woDialog.query = { pageNum: 1, pageSize: 10, woNo: '' };
  queryWorkOrders();
}
function selectWorkOrder(row: any) {
  createForm.mesWoId = row.mesWoId;
  createForm.woNo = row.woNo;
  // 顺带带出物料(若空)
  if (!createForm.materialId && row.materialId) {
    createForm.materialId = row.materialId;
    createForm.materialCode = row.materialCode;
    createForm.materialName = row.materialName;
  }
  woDialog.visible = false;
}

// ============ 父SN选择 ============
function openSnSelect() {
  snDialog.visible = true;
  querySns();
}
function querySns() {
  listSn(snDialog.query as any).then((res: any) => {
    snDialog.list = res.rows || [];
    snDialog.total = res.total || 0;
  });
}
function resetSnQuery() {
  snDialog.query = { pageNum: 1, pageSize: 10, snCode: '' };
  querySns();
}
function selectSn(row: any) {
  createForm.parentSnId = row.snId;
  createForm.parentSnCode = row.snCode;
  snDialog.visible = false;
}

// ============ 物料选择 ============
function openMaterialSelect() {
  materialSelectRef.value?.open();
}
function handleMaterialSelect(row: any) {
  createForm.materialId = row.materialId;
  createForm.materialCode = row.materialCode;
  createForm.materialName = row.materialName;
}

onMounted(() => {
  getList();
});
</script>
