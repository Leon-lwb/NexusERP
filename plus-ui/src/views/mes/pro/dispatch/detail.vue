<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>MES派工详情 - {{ dispatch.dispatchNo }}</span>
          <div style="float: right">
            <el-button
              type="primary"
              plain
              icon="Promotion"
              @click="handleStart"
              v-if="dispatch.dispatchStatus === 'PENDING'"
              v-hasPermi="['mes:pro:dispatch:start']"
              style="margin-right: 8px"
              >开工</el-button
            >
            <el-button
              type="success"
              plain
              icon="CircleCheck"
              @click="handleComplete"
              v-if="dispatch.dispatchStatus === 'IN_PROGRESS'"
              v-hasPermi="['mes:pro:dispatch:complete']"
              style="margin-right: 8px"
              >完工</el-button
            >
            <el-button
              type="danger"
              plain
              icon="Close"
              @click="handleCancel"
              v-if="dispatch.dispatchStatus === 'PENDING'"
              v-hasPermi="['mes:pro:dispatch:cancel']"
              style="margin-right: 8px"
              >取消</el-button
            >
            <el-button type="primary" plain @click="goBack">返回</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="派工单号">{{ dispatch.dispatchNo }}</el-descriptions-item>
        <el-descriptions-item label="工单编号">{{ dispatch.woNo }}</el-descriptions-item>
        <el-descriptions-item label="流转卡编号">{{ dispatch.cardNo }}</el-descriptions-item>
        <el-descriptions-item label="工序编码">{{ dispatch.processCode }}</el-descriptions-item>
        <el-descriptions-item label="工序名称">{{ dispatch.processName }}</el-descriptions-item>
        <el-descriptions-item label="工作中心">{{ dispatch.workcenterCode }}</el-descriptions-item>
        <el-descriptions-item label="操作员">{{ dispatch.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="派工数量">{{ dispatch.plannedQty }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ dispatch.completedQty }}</el-descriptions-item>
        <el-descriptions-item label="良品">{{ dispatch.goodQty }}</el-descriptions-item>
        <el-descriptions-item label="不良">{{ dispatch.defectQty }}</el-descriptions-item>
        <el-descriptions-item label="报废">{{ dispatch.scrapQty }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="mes_dispatch_status" :value="dispatch.dispatchStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ dispatch.planStartTime }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ dispatch.planEndTime }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ dispatch.actualStartTime }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{ dispatch.actualEndTime }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ dispatch.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesDispatchDetail">
import { getDispatch, startDispatch, completeDispatch, cancelDispatch } from '@/api/mes/pro/dispatch';
import type { ProDispatchVO } from '@/api/mes/pro/dispatch';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_dispatch_status } = toRefs<any>(proxy?.useDict('mes_dispatch_status'));
const route = useRoute();
const router = useRouter();

const dispatch = ref<ProDispatchVO>({} as ProDispatchVO);

const loadDetail = async () => {
  const dispatchId = route.params.dispatchId as string;
  const res = await getDispatch(dispatchId);
  dispatch.value = res.data;
};

const handleStart = async () => {
  await proxy?.$modal.confirm('确认开工派工单"' + dispatch.value.dispatchNo + '"？');
  await startDispatch(dispatch.value.dispatchId);
  proxy?.$modal.msgSuccess('开工成功');
  loadDetail();
};

const handleComplete = async () => {
  const { value: goodQty } = await proxy?.$modal.prompt('请输入良品数量:', '完工 - ' + dispatch.value.dispatchNo);
  if (!goodQty || isNaN(Number(goodQty)) || Number(goodQty) <= 0) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  const { value: defectQty } = await proxy?.$modal.prompt('请输入不良品数量（默认0）:', '完工 - ' + dispatch.value.dispatchNo);
  const { value: scrapQty } = await proxy?.$modal.prompt('请输入报废数量（默认0）:', '完工 - ' + dispatch.value.dispatchNo);
  await completeDispatch(dispatch.value.dispatchId, Number(goodQty), Number(defectQty || 0), Number(scrapQty || 0));
  proxy?.$modal.msgSuccess('完工成功');
  loadDetail();
};

const handleCancel = async () => {
  await proxy?.$modal.confirm('确认取消派工单"' + dispatch.value.dispatchNo + '"？');
  await cancelDispatch(dispatch.value.dispatchId);
  proxy?.$modal.msgSuccess('取消成功');
  loadDetail();
};

const goBack = () => {
  router.push('/mes/pro/dispatch');
};

onMounted(() => {
  loadDetail();
});
</script>
