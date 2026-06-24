<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>流转卡详情 - {{ flowCard.cardNo }}</span>
          <el-button style="float: right" type="primary" plain @click="goBack">返回</el-button>
          <el-button
            style="float: right; margin-right: 8px"
            type="warning"
            plain
            icon="CircleCheck"
            v-if="flowCard.cardStatus === 'IN_PROGRESS'"
            v-hasPermi="['mes:pro:flowcard:complete']"
            @click="handleCompleteFlowCard"
            >完工流转卡</el-button
          >
          <el-button
            style="float: right; margin-right: 8px"
            type="info"
            plain
            icon="Lock"
            v-if="flowCard.cardStatus === 'COMPLETED'"
            v-hasPermi="['mes:pro:flowcard:edit']"
            @click="handleCloseFlowCard"
            >关闭流转卡</el-button
          >
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="流转卡ID">{{ flowCard.cardId }}</el-descriptions-item>
        <el-descriptions-item label="流转卡编号">{{ flowCard.cardNo }}</el-descriptions-item>
        <el-descriptions-item label="工单编号">{{ flowCard.woNo }}</el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ flowCard.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ flowCard.materialName }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ flowCard.plannedQty }}</el-descriptions-item>
        <el-descriptions-item label="完成数量">{{ flowCard.completedQty }}</el-descriptions-item>
        <el-descriptions-item label="报废数量">{{ flowCard.scrapQty }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(flowCard.cardStatus)">{{ statusLabel(flowCard.cardStatus) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="mt-2">
      <template #header>
        <span>工序进度</span>
      </template>
      <el-table :data="flowCard.steps" border>
        <el-table-column label="序号" prop="sequenceNum" width="80" align="center" />
        <el-table-column label="工序编码" prop="processCode" width="140" />
        <el-table-column label="工序名称" prop="processName" />
        <el-table-column label="关键工序" prop="isKeyProcess" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isKeyProcess === 1" type="danger">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="良品" prop="goodQty" width="80" align="right" />
        <el-table-column label="不良" prop="defectQty" width="80" align="right" />
        <el-table-column label="报废" prop="scrapQty" width="80" align="right" />
        <el-table-column label="状态" prop="stepStatus" width="100" align="center">
          <template #default="scope">
            <el-tag :type="stepStatusTagType(scope.row.stepStatus)">{{ stepStatusLabel(scope.row.stepStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已派工" width="120" align="center">
          <template #default="scope">
            <span v-if="getStepDispatchedQty(scope.row.stepRecordId) > 0">
              {{ getStepDispatchedQty(scope.row.stepRecordId) }} / {{ scope.row.plannedQty }}
            </span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="scope">
            <el-button
              link
              type="warning"
              icon="User"
              @click="handleOpenDispatch(scope.row)"
              v-if="scope.row.stepStatus === 'PENDING' && flowCard.cardStatus === 'RELEASED'"
              v-hasPermi="['mes:pro:dispatch:add']"
              >派工</el-button
            >
            <el-button
              link
              type="primary"
              icon="VideoPlay"
              @click="handleStartStep(scope.row)"
              v-if="scope.row.stepStatus === 'PENDING' && flowCard.cardStatus === 'RELEASED'"
              v-hasPermi="['mes:pro:flowcard:start']"
              >开工</el-button
            >
            <el-button
              link
              type="success"
              icon="CircleCheck"
              @click="handleCompleteStep(scope.row)"
              v-if="scope.row.stepStatus === 'IN_PROGRESS'"
              v-hasPermi="['mes:pro:flowcard:complete']"
              >完工</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 派工对话框 -->
    <el-dialog v-model="dispatchDialogVisible" title="派工" width="480px" destroy-on-close>
      <el-form ref="dispatchFormRef" :model="dispatchForm" :rules="dispatchRules" label-width="90px">
        <el-form-item label="工序">
          <el-input :model-value="currentStep?.processName" disabled />
        </el-form-item>
        <el-form-item label="操作员" prop="operatorId">
          <el-select v-model="dispatchForm.operatorId" placeholder="请选择操作员" filterable style="width: 100%" @change="handleOperatorChange">
            <el-option
              v-for="w in workerOptions"
              :key="w.workerId"
              :label="w.userName + (w.employeeId ? ' / ' + w.employeeId : '')"
              :value="w.workerId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="派工数量" prop="quantity">
          <el-input-number
            v-model="dispatchForm.quantity"
            :min="1"
            :max="maxDispatchQty"
            style="width: 100%"
          />
          <div class="text-gray-400 text-xs mt-1">工序计划数量: {{ currentStep?.plannedQty }}，剩余可派: {{ maxDispatchQty }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dispatchSubmitting" @click="handleSubmitDispatch">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesFlowCardDetail">
import { getFlowCard, startStep, completeStep, completeFlowCard, closeFlowCard } from '@/api/mes/pro/flowcardApi';
import type { ProFlowCardVO, ProFlowCardStepVO } from '@/api/mes/pro/flowcard';
import { addDispatch, listDispatchByStep } from '@/api/mes/pro/dispatch';
import type { ProDispatchVO } from '@/api/mes/pro/dispatch';
import { listWorker } from '@/api/erp/sch/worker/index';
import type { WorkerVO } from '@/api/erp/sch/worker/types';
import type { FormInstance, FormRules } from 'element-plus';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const flowCard = ref<ProFlowCardVO>({} as ProFlowCardVO);

// 派工相关
const workerOptions = ref<WorkerVO[]>([]);
const dispatchDialogVisible = ref(false);
const dispatchSubmitting = ref(false);
const dispatchFormRef = ref<FormInstance>();
const currentStep = ref<ProFlowCardStepVO | null>(null);
const stepDispatches = ref<Map<number, ProDispatchVO[]>>(new Map());

const dispatchForm = ref({
  operatorId: undefined as number | undefined,
  operatorName: '',
  quantity: 1
});

const dispatchRules: FormRules = {
  operatorId: [{ required: true, message: '请选择操作员', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入派工数量', trigger: 'blur' }]
};

const maxDispatchQty = computed(() => {
  if (!currentStep.value) return 9999;
  const stepId = currentStep.value.stepRecordId;
  const existing = stepDispatches.value.get(stepId) || [];
  const dispatched = existing.reduce((sum, d) => sum + (d.plannedQty || 0), 0);
  return Math.max(0, Number(currentStep.value.plannedQty) - dispatched);
});

const loadDetail = async () => {
  const cardId = route.params.cardId as string;
  const res = await getFlowCard(cardId);
  flowCard.value = res.data;
  // 加载每个工序的派工记录
  await loadStepDispatches();
};

const handleStartStep = async (row: ProFlowCardStepVO) => {
  await proxy?.$modal.confirm('确认开始工序"' + row.processName + '"？');
  await startStep(row.cardId, row.stepRecordId);
  proxy?.$modal.msgSuccess('开工成功');
  loadDetail();
};

const handleCompleteStep = async (row: ProFlowCardStepVO) => {
  const { value: goodQty } = await proxy?.$modal.prompt('请输入良品数量:', '完工 - ' + row.processName);
  if (!goodQty || isNaN(Number(goodQty))) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  await completeStep(row.cardId, row.stepRecordId, Number(goodQty));
  proxy?.$modal.msgSuccess('完工成功');
  loadDetail();
};

const handleCompleteFlowCard = async () => {
  await proxy?.$modal.confirm('确认完成该流转卡？将聚合完工数量到工单。');
  await completeFlowCard(flowCard.value.cardId);
  proxy?.$modal.msgSuccess('流转卡完工成功');
  loadDetail();
};

const handleCloseFlowCard = async () => {
  await proxy?.$modal.confirm('确认关闭该流转卡？关闭后不可再操作。');
  await closeFlowCard(flowCard.value.cardId);
  proxy?.$modal.msgSuccess('流转卡已关闭');
  loadDetail();
};

const goBack = () => {
  router.push('/mes/pro/flow-card');
};

// ---- 派工相关方法 ----

const getStepDispatchedQty = (stepRecordId: number) => {
  const list = stepDispatches.value.get(stepRecordId) || [];
  return list.reduce((sum, d) => sum + (d.plannedQty || 0), 0);
};

const loadStepDispatches = async () => {
  if (!flowCard.value.steps?.length) return;
  const map = new Map<number, ProDispatchVO[]>();
  for (const step of flowCard.value.steps) {
    try {
      const res = await listDispatchByStep(step.stepRecordId);
      map.set(step.stepRecordId, res.data || []);
    } catch {
      map.set(step.stepRecordId, []);
    }
  }
  stepDispatches.value = map;
};

const handleOpenDispatch = (row: ProFlowCardStepVO) => {
  currentStep.value = row;
  dispatchForm.value = {
    operatorId: undefined,
    operatorName: '',
    quantity: maxDispatchQty.value > 0 ? maxDispatchQty.value : Number(row.plannedQty)
  };
  dispatchDialogVisible.value = true;
};

const handleOperatorChange = (workerId: number) => {
  const worker = workerOptions.value.find((w) => w.workerId === workerId);
  if (worker) {
    dispatchForm.value.operatorName = worker.userName;
  }
};

const handleSubmitDispatch = async () => {
  if (!dispatchFormRef.value) return;
  await dispatchFormRef.value.validate();
  if (!currentStep.value) return;

  dispatchSubmitting.value = true;
  try {
    await addDispatch(
      currentStep.value.cardId,
      currentStep.value.stepRecordId,
      dispatchForm.value.operatorId!,
      dispatchForm.value.operatorName,
      dispatchForm.value.quantity
    );
    proxy?.$modal.msgSuccess('派工成功');
    dispatchDialogVisible.value = false;
    loadDetail();
  } finally {
    dispatchSubmitting.value = false;
  }
};

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    CREATED: '新建',
    RELEASED: '已下达',
    IN_PROGRESS: '生产中',
    COMPLETED: '已完工',
    CLOSED: '已关闭'
  };
  return map[status] || status;
};
const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    CREATED: 'info',
    RELEASED: 'success',
    IN_PROGRESS: 'warning',
    COMPLETED: 'primary',
    CLOSED: 'info'
  };
  return map[status] || 'info';
};
const stepStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    SKIPPED: '已跳过'
  };
  return map[status] || status;
};
const stepStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success',
    SKIPPED: 'info'
  };
  return map[status] || 'info';
};

onMounted(async () => {
  // 加载操作员列表
  try {
    const res = await listWorker({ pageNum: 1, pageSize: 1000 });
    workerOptions.value = res.rows;
  } catch {
    workerOptions.value = [];
  }
  loadDetail();
});
</script>
