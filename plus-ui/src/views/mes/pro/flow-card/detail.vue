<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>流转卡详情 - {{ flowCard.cardNo }}</span>
          <el-button style="float: right" type="primary" plain @click="goBack">返回</el-button>
          <el-button style="float: right; margin-right: 8px" type="warning" plain icon="CircleCheck"
            v-if="flowCard.cardStatus === 'IN_PROGRESS'"
            v-hasPermi="['mes:pro:flowcard:complete']"
            @click="handleCompleteFlowCard">完工流转卡</el-button>
          <el-button style="float: right; margin-right: 8px" type="info" plain icon="Lock"
            v-if="flowCard.cardStatus === 'COMPLETED'"
            v-hasPermi="['mes:pro:flowcard:edit']"
            @click="handleCloseFlowCard">关闭流转卡</el-button>
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
        <el-table-column label="操作" width="160" align="center">
          <template #default="scope">
            <el-button link type="primary" icon="VideoPlay" @click="handleStartStep(scope.row)"
              v-if="scope.row.stepStatus === 'PENDING' && flowCard.cardStatus === 'RELEASED'"
              v-hasPermi="['mes:pro:flowcard:start']">开工</el-button>
            <el-button link type="success" icon="CircleCheck" @click="handleCompleteStep(scope.row)"
              v-if="scope.row.stepStatus === 'IN_PROGRESS'"
              v-hasPermi="['mes:pro:flowcard:complete']">完工</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesFlowCardDetail">
import { getFlowCard, startStep, completeStep, completeFlowCard, closeFlowCard } from '@/api/mes/pro/flowcardApi';
import type { ProFlowCardVO, ProFlowCardStepVO } from '@/api/mes/pro/flowcard';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const flowCard = ref<ProFlowCardVO>({} as ProFlowCardVO);

const loadDetail = async () => {
  const cardId = route.params.cardId as string;
  const res = await getFlowCard(cardId);
  flowCard.value = res.data;
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

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    CREATED: '新建', RELEASED: '已下达', IN_PROGRESS: '生产中', COMPLETED: '已完工', CLOSED: '已关闭'
  };
  return map[status] || status;
};
const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    CREATED: 'info', RELEASED: 'success', IN_PROGRESS: 'warning', COMPLETED: 'primary', CLOSED: 'info'
  };
  return map[status] || 'info';
};
const stepStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', SKIPPED: '已跳过'
  };
  return map[status] || status;
};
const stepStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', SKIPPED: 'info'
  };
  return map[status] || 'info';
};

onMounted(() => {
  loadDetail();
});
</script>
