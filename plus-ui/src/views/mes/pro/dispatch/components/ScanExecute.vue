<template>
  <el-dialog v-model="visible" title="扫码执行" width="640px" append-to-body>
    <el-input v-model="barcode" placeholder="请扫描或输入流转卡条码" @keyup.enter="handleScan">
      <template #append>
        <el-button icon="Search" @click="handleScan">查找</el-button>
      </template>
    </el-input>

    <el-descriptions v-if="flowCard.cardId" :column="2" border class="mt-2">
      <el-descriptions-item label="流转卡编号">{{ flowCard.cardNo }}</el-descriptions-item>
      <el-descriptions-item label="产品">{{ flowCard.materialName }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :options="mes_flow_card_status" :value="flowCard.cardStatus" />
      </el-descriptions-item>
      <el-descriptions-item label="当前工序">{{ currentStep?.processName }}</el-descriptions-item>
    </el-descriptions>

    <div v-if="currentStep" class="mt-2">
      <el-button
        type="primary"
        icon="VideoPlay"
        @click="handleStartStep"
        v-if="currentStep.stepStatus === 'PENDING' && flowCard.cardStatus === 'RELEASED'"
        v-hasPermi="['mes:pro:flowcard:start']"
        >开工</el-button
      >
      <el-button
        type="success"
        icon="CircleCheck"
        @click="handleCompleteStep"
        v-if="currentStep.stepStatus === 'IN_PROGRESS'"
        v-hasPermi="['mes:pro:flowcard:complete']"
        >完工</el-button
      >
    </div>
  </el-dialog>
</template>

<script setup lang="ts" name="ScanExecute">
import { getFlowCardByBarcode } from '@/api/mes/pro/dispatch';
import { startStep, completeStep } from '@/api/mes/pro/flowcardApi';
import type { ProFlowCardVO, ProFlowCardStepVO } from '@/api/mes/pro/flowcard';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_flow_card_status } = toRefs<any>(proxy?.useDict('mes_flow_card_status'));

const visible = ref(false);
const barcode = ref('');
const flowCard = ref<ProFlowCardVO>({} as ProFlowCardVO);
const currentStep = ref<ProFlowCardStepVO | null>(null);

const reset = () => {
  barcode.value = '';
  flowCard.value = {} as ProFlowCardVO;
  currentStep.value = null;
};

const handleScan = async () => {
  if (!barcode.value) {
    proxy?.$modal.msgWarning('请输入流转卡条码');
    return;
  }
  try {
    const res = await getFlowCardByBarcode(barcode.value);
    flowCard.value = res.data;
    const steps = (flowCard.value.steps || []).slice().sort((a, b) => a.sequenceNum - b.sequenceNum);
    currentStep.value = steps.find((s) => s.stepStatus === 'PENDING' || s.stepStatus === 'IN_PROGRESS') || null;
  } catch (e) {
    reset();
  }
};

const handleStartStep = async () => {
  if (!currentStep.value) return;
  const { value: operatorId } = await proxy?.$modal.prompt('请输入操作员ID:', '开工 - ' + currentStep.value.processName);
  if (!operatorId || isNaN(Number(operatorId)) || Number(operatorId) <= 0) {
    proxy?.$modal.msgWarning('请输入有效操作员ID');
    return;
  }
  await startStep(flowCard.value.cardId, currentStep.value.stepRecordId, Number(operatorId));
  proxy?.$modal.msgSuccess('开工成功');
  handleScan();
};

const handleCompleteStep = async () => {
  if (!currentStep.value) return;
  const { value: goodQty } = await proxy?.$modal.prompt('请输入良品数量:', '完工 - ' + currentStep.value.processName);
  if (!goodQty || isNaN(Number(goodQty)) || Number(goodQty) <= 0) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  const { value: defectQty } = await proxy?.$modal.prompt('请输入不良品数量（默认0）:', '完工 - ' + currentStep.value.processName);
  const { value: scrapQty } = await proxy?.$modal.prompt('请输入报废数量（默认0）:', '完工 - ' + currentStep.value.processName);
  await completeStep(flowCard.value.cardId, currentStep.value.stepRecordId, Number(goodQty), Number(defectQty || 0), Number(scrapQty || 0));
  proxy?.$modal.msgSuccess('完工成功');
  handleScan();
};

defineExpose({
  open: () => {
    visible.value = true;
    reset();
  }
});
</script>
