<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>流转卡详情 - {{ flowCard.cardNo }}</span>
          <el-button style="float: right" type="primary" plain @click="goBack">返回</el-button>
          <el-button
            style="float: right; margin-right: 8px"
            type="success"
            plain
            icon="MagicStick"
            v-if="flowCard.cardStatus === 'IN_PROGRESS'"
            v-hasPermi="['mes:pro:sn:add']"
            @click="handleGenerateSn"
            >生成SN</el-button
          >
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
          <dict-tag :options="mes_flow_card_status" :value="flowCard.cardStatus" />
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
            <dict-tag :options="mes_step_status" :value="scope.row.stepStatus" />
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
        <el-table-column label="操作" width="300" align="center">
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
              @click="handleEntry(scope.row)"
              v-if="scope.row.stepStatus === 'PENDING' && flowCard.cardStatus === 'RELEASED'"
              v-hasPermi="['mes:pro:flowcard:start']"
              >进站开工</el-button
            >
            <el-button
              link
              type="success"
              icon="CircleCheck"
              @click="handleExit(scope.row)"
              v-if="scope.row.stepStatus === 'IN_PROGRESS'"
              v-hasPermi="['mes:pro:flowcard:complete']"
              >出站完工</el-button
            >
            <el-button
              link
              type="info"
              icon="Sort"
              @click="handleSkip(scope.row)"
              v-if="scope.row.stepStatus === 'PENDING' && flowCard.cardStatus === 'RELEASED'"
              v-hasPermi="['mes:pro:flowcard:skip']"
              >跳站</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 派工对话框 -->
    <el-dialog v-model="dispatchDialogVisible" title="派工" width="500px" destroy-on-close>
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
          <el-input-number v-model="dispatchForm.quantity" :min="1" :max="maxDispatchQty" style="width: 100%" />
          <div class="text-gray-400 text-xs mt-1">工序计划数量: {{ currentStep?.plannedQty }}，剩余可派: {{ maxDispatchQty }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dispatchSubmitting" @click="handleSubmitDispatch">确定</el-button>
      </template>
    </el-dialog>

    <!-- 进站操作员选择对话框 -->
    <el-dialog v-model="entryDialogVisible" title="工序进站" width="500px" destroy-on-close>
      <el-form ref="entryFormRef" :model="entryForm" :rules="entryRules" label-width="90px">
        <el-form-item label="工序">
          <el-input :model-value="currentStep?.processName" disabled />
        </el-form-item>
        <el-form-item label="操作员" prop="operatorId">
          <el-select v-model="entryForm.operatorId" placeholder="请选择操作员" filterable style="width: 100%">
            <el-option
              v-for="w in workerOptions"
              :key="w.workerId"
              :label="w.userName + (w.employeeId ? ' / ' + w.employeeId : '')"
              :value="w.workerId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工装">
          <el-input v-model="entryForm.toolingCode" placeholder="可选,扫码或输入工装编码" />
        </el-form-item>
        <el-form-item label="批次">
          <el-input v-model="entryForm.lotNo" placeholder="可选,扫码或输入批次号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="entryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="entrySubmitting" @click="handleSubmitEntry">确认进站</el-button>
      </template>
    </el-dialog>

    <!-- 出站完工对话框 -->
    <el-dialog v-model="exitDialogVisible" title="工序出站完工" width="500px" destroy-on-close>
      <el-form ref="exitFormRef" :model="exitForm" :rules="exitRules" label-width="90px">
        <el-form-item label="工序">
          <el-input :model-value="currentStep?.processName" disabled />
        </el-form-item>
        <el-form-item label="良品数量" prop="goodQty">
          <el-input-number v-model="exitForm.goodQty" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="不良数量">
              <el-input-number v-model="exitForm.defectQty" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报废数量">
              <el-input-number v-model="exitForm.scrapQty" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="exitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exitSubmitting" @click="handleSubmitExit">确认完工</el-button>
      </template>
    </el-dialog>

    <!-- 跳站对话框 -->
    <el-dialog v-model="skipDialogVisible" title="工序跳站" width="500px" destroy-on-close>
      <el-form ref="skipFormRef" :model="skipForm" :rules="skipRules" label-width="90px">
        <el-form-item label="工序">
          <el-input :model-value="currentStep?.processName" disabled />
        </el-form-item>
        <el-form-item label="跳站原因" prop="reason">
          <el-input v-model="skipForm.reason" type="textarea" :rows="3" placeholder="请填写跳站原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="skipSubmitting" @click="handleSubmitSkip">确认跳站</el-button>
      </template>
    </el-dialog>

    <!-- 校验失败明细对话框 -->
    <el-dialog v-model="failureDialogVisible" title="校验未通过" width="640px" append-to-body>
      <el-alert title="工序校验未通过，请处理以下问题：" type="error" :closable="false" show-icon class="mb-2" />
      <el-table :data="failureList" border>
        <el-table-column label="校验类型" prop="checkType" width="180">
          <template #default="scope">
            <dict-tag :options="mes_check_type" :value="scope.row.checkType" />
          </template>
        </el-table-column>
        <el-table-column label="错误信息" prop="errorMsg" min-width="200" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="failureDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesFlowCardDetail">
import {
  getFlowCard,
  startStep,
  completeStep,
  completeFlowCard,
  closeFlowCard,
  entryFlowCard,
  exitFlowCard,
  skipFlowCard
} from '@/api/mes/pro/flowcardApi';
import type { ProFlowCardVO, ProFlowCardStepVO } from '@/api/mes/pro/flowcard';
import type { ProEntryBo, ProExitBo, ProSkipBo, ProCheckResultVo, ProCheckFailure } from '@/api/mes/pro/flowcardApi';
import { addDispatch, listDispatchByStep } from '@/api/mes/pro/dispatch';
import type { ProDispatchVO } from '@/api/mes/pro/dispatch';
import { generateSnForCard } from '@/api/mes/pro/sn';
import { listWorker } from '@/api/erp/sch/worker/index';
import type { WorkerVO } from '@/api/erp/sch/worker/types';
import type { FormInstance, FormRules } from 'element-plus';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_flow_card_status, mes_step_status, mes_check_type } = toRefs<any>(
  proxy?.useDict('mes_flow_card_status', 'mes_step_status', 'mes_check_type')
);
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

// 进站相关
const entryDialogVisible = ref(false);
const entrySubmitting = ref(false);
const entryFormRef = ref<FormInstance>();
const entryForm = ref({
  operatorId: undefined as number | undefined,
  toolingCode: '',
  lotNo: ''
});
const entryRules: FormRules = {
  operatorId: [{ required: true, message: '请选择操作员', trigger: 'change' }]
};

// 出站相关
const exitDialogVisible = ref(false);
const exitSubmitting = ref(false);
const exitFormRef = ref<FormInstance>();
const exitForm = ref({
  goodQty: 0,
  defectQty: 0,
  scrapQty: 0
});
const exitRules: FormRules = {
  goodQty: [{ required: true, message: '请输入良品数量', trigger: 'blur' }]
};

// 跳站相关
const skipDialogVisible = ref(false);
const skipSubmitting = ref(false);
const skipFormRef = ref<FormInstance>();
const skipForm = ref({
  reason: ''
});
const skipRules: FormRules = {
  reason: [{ required: true, message: '请填写跳站原因', trigger: 'blur' }]
};

// 校验失败明细
const failureDialogVisible = ref(false);
const failureList = ref<ProCheckFailure[]>([]);

const loadDetail = async () => {
  const cardId = route.params.cardId as string;
  const res = await getFlowCard(cardId);
  flowCard.value = res.data;
  // 加载每个工序的派工记录
  await loadStepDispatches();
};

// ===== 生成SN(SN管理物料刷写工单必经步骤: 建流转卡→生成SN→刷写→完工) =====
const generateSnSubmitting = ref(false);
const handleGenerateSn = async () => {
  const qty = Number(flowCard.value.plannedQty) || 1;
  try {
    await proxy?.$modal.confirm(`确认按计划数量 ${qty} 生成SN? 生成后需逐台刷写, 刷写工序完工门禁将校验每台SN的刷写记录。`, '生成SN');
  } catch {
    return;
  }
  generateSnSubmitting.value = true;
  try {
    await generateSnForCard(flowCard.value.cardId, qty, 'IN_PRODUCTION');
    proxy?.$modal.msgSuccess(`已生成 ${qty} 个SN`);
    await loadDetail();
  } catch (e: any) {
    proxy?.$modal.msgError(e?.msg || e?.message || '生成SN失败');
  } finally {
    generateSnSubmitting.value = false;
  }
};

// ===== 进站(P0-3) =====
const handleEntry = (row: ProFlowCardStepVO) => {
  currentStep.value = row;
  entryForm.value = {
    operatorId: undefined,
    toolingCode: '',
    lotNo: ''
  };
  entryDialogVisible.value = true;
};

const handleSubmitEntry = async () => {
  if (!entryFormRef.value) return;
  if (!currentStep.value) return;

  await entryFormRef.value.validate();
  entrySubmitting.value = true;
  try {
    const bo: ProEntryBo = {
      cardId: currentStep.value.cardId,
      stepRecordId: currentStep.value.stepRecordId,
      operatorId: entryForm.value.operatorId!
    };
    const res = await entryFlowCard(bo);
    const result: ProCheckResultVo = res.data;
    if (result.passed) {
      proxy?.$modal.msgSuccess('进站成功，已开工');
      entryDialogVisible.value = false;
      loadDetail();
    } else {
      failureList.value = result.failures || [];
      failureDialogVisible.value = true;
    }
  } finally {
    entrySubmitting.value = false;
  }
};

// ===== 出站(P0-3) =====
const handleExit = (row: ProFlowCardStepVO) => {
  currentStep.value = row;
  exitForm.value = {
    goodQty: Number(row.plannedQty) || 0,
    defectQty: 0,
    scrapQty: 0
  };
  exitDialogVisible.value = true;
};

const handleSubmitExit = async () => {
  if (!exitFormRef.value) return;
  if (!currentStep.value) return;

  await exitFormRef.value.validate();
  exitSubmitting.value = true;
  try {
    const bo: ProExitBo = {
      cardId: currentStep.value.cardId,
      stepRecordId: currentStep.value.stepRecordId,
      goodQty: exitForm.value.goodQty,
      defectQty: exitForm.value.defectQty,
      scrapQty: exitForm.value.scrapQty
    };
    const res = await exitFlowCard(bo);
    const result: ProCheckResultVo = res.data;
    if (result.passed) {
      proxy?.$modal.msgSuccess('出站成功，已完工');
      exitDialogVisible.value = false;
      loadDetail();
    } else {
      failureList.value = result.failures || [];
      failureDialogVisible.value = true;
    }
  } finally {
    exitSubmitting.value = false;
  }
};

// ===== 跳站(P0-3) =====
const handleSkip = (row: ProFlowCardStepVO) => {
  currentStep.value = row;
  skipForm.value = { reason: '' };
  skipDialogVisible.value = true;
};

const handleSubmitSkip = async () => {
  if (!skipFormRef.value) return;
  if (!currentStep.value) return;

  await skipFormRef.value.validate();
  skipSubmitting.value = true;
  try {
    const bo: ProSkipBo = {
      cardId: currentStep.value.cardId,
      stepRecordId: currentStep.value.stepRecordId,
      reason: skipForm.value.reason
    };
    await skipFlowCard(bo);
    proxy?.$modal.msgSuccess('跳站成功');
    skipDialogVisible.value = false;
    loadDetail();
  } finally {
    skipSubmitting.value = false;
  }
};

// ===== 原有开工/完工(保留作为旧版后备) =====
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
  router.push('/mes/wo/flow-card');
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
