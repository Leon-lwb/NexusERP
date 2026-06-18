<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>MES工单详情 - {{ workOrder.woNo }}</span>
          <div style="float: right">
            <el-button type="success" icon="Stamp" @click="handleCreateFlowCard"
              v-if="workOrder.woStatus === 'RELEASED' || workOrder.woStatus === 'IN_PROGRESS'"
              v-hasPermi="['mes:pro:flowcard:add']" style="margin-right: 8px">创建流转卡</el-button>
            <el-button type="primary" plain @click="goBack">返回</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="MES工单ID">{{ workOrder.mesWoId }}</el-descriptions-item>
            <el-descriptions-item label="ERP工单ID">{{ workOrder.erpWoId }}</el-descriptions-item>
            <el-descriptions-item label="工单编号">{{ workOrder.woNo }}</el-descriptions-item>
            <el-descriptions-item label="产品编码">{{ workOrder.materialCode }}</el-descriptions-item>
            <el-descriptions-item label="产品名称">{{ workOrder.materialName }}</el-descriptions-item>
            <el-descriptions-item label="计划数量">{{ workOrder.plannedQty }} {{ workOrder.unitName }}</el-descriptions-item>
            <el-descriptions-item label="车间">{{ workOrder.workshopName }}</el-descriptions-item>
            <el-descriptions-item label="工艺路线ID">{{ workOrder.routingId }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(workOrder.woStatus)">{{ statusLabel(workOrder.woStatus) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="计划开工">{{ workOrder.planStartDate }}</el-descriptions-item>
            <el-descriptions-item label="计划完工">{{ workOrder.planEndDate }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ workOrder.remark }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="工序步骤" name="steps">
          <el-table :data="workOrder.steps" border>
            <el-table-column label="序号" prop="sequenceNum" width="80" align="center" />
            <el-table-column label="工序编码" prop="processCode" width="140" />
            <el-table-column label="工序名称" prop="processName" />
            <el-table-column label="工作中心" prop="workcenterCode" width="140" />
            <el-table-column label="关键工序" prop="isKeyProcess" width="100" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.isKeyProcess === 1" type="danger">是</el-tag>
                <span v-else>否</span>
              </template>
            </el-table-column>
            <el-table-column label="计划数量" prop="plannedQty" width="100" align="right" />
            <el-table-column label="状态" prop="stepStatus" width="100" align="center">
              <template #default="scope">
                <el-tag :type="stepStatusTagType(scope.row.stepStatus)">{{ stepStatusLabel(scope.row.stepStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="审批流程" name="approval">
          <el-form :inline="true">
            <el-form-item label="审批流程" v-if="workOrder.woStatus === 'CREATED'">
              <el-select v-model="selectFlowId" clearable filterable placeholder="请选择审批流程"
                style="width: 300px">
                <el-option v-for="f in flowOptions" :key="f.id"
                  :label="f.flowName || f.businessName || ('流程#' + f.id)" :value="f.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Promotion" @click="handleSubmitFlow"
                v-if="workOrder.woStatus === 'CREATED'" :disabled="!selectFlowId">
                提交审批
              </el-button>
              <el-button type="info" icon="View" @click="handleViewFlow" v-if="procInsId">
                查看流程
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesWorkOrderDetail">
import { getWorkOrder, submitWorkOrderFlow, getWorkOrderProcId, getProcessList } from '@/api/mes/pro/workorder';
import { addFlowCard } from '@/api/mes/pro/flowcardApi';
import type { ProWorkOrderVO } from '@/api/mes/pro/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const workOrder = ref<ProWorkOrderVO>({} as ProWorkOrderVO);
const selectFlowId = ref<string>('');
const procInsId = ref<string>('');
const flowOptions = ref<any[]>([]);
const activeTab = ref('info');

const loadDetail = async () => {
  const mesWoId = route.params.mesWoId as string;
  const res = await getWorkOrder(mesWoId);
  workOrder.value = res.data;
  // 仅 CREATED 状态需要加载审批流程选项
  if (workOrder.value.woStatus === 'CREATED') {
    loadFlowOptions();
  }
  // 非 CREATED 状态才查询流程实例ID（CREATED 状态一定没有流程）
  if (workOrder.value.woStatus && workOrder.value.woStatus !== 'CREATED') {
    loadProcInsId(mesWoId);
  }
};

const loadFlowOptions = async () => {
  try {
    const res = await getProcessList({ serviceName: 'proWorkOrderService' });
    flowOptions.value = res?.data || [];
    if (flowOptions.value.length === 1 && !selectFlowId.value) {
      selectFlowId.value = flowOptions.value[0].id;
    }
  } catch (e) {
    // 审批流未配置时忽略
  }
};

const loadProcInsId = async (mesWoId: string) => {
  try {
    const res = await getWorkOrderProcId(mesWoId);
    procInsId.value = res.data || '';
  } catch (e) {
    procInsId.value = '';
  }
};

const handleSubmitFlow = async () => {
  if (!selectFlowId.value) {
    proxy?.$modal.msgWarning('请先选择审批流程');
    return;
  }
  await proxy?.$modal.confirm('确认提交审批？');
  await submitWorkOrderFlow(workOrder.value.mesWoId, selectFlowId.value);
  proxy?.$modal.msgSuccess('提交审批成功');
  loadDetail();
};

const handleViewFlow = () => {
  if (procInsId.value) {
    router.push('/workflow/process/detail/' + procInsId.value);
  }
};

const handleCreateFlowCard = async () => {
  const qty = await proxy?.$modal.prompt(
    `请输入本流转卡计划数量（工单计划数量 ${workOrder.value.plannedQty}）：`,
    '创建流转卡 - ' + workOrder.value.woNo
  );
  if (!qty || isNaN(Number(qty)) || Number(qty) <= 0) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  await addFlowCard(workOrder.value.mesWoId, Number(qty));
  proxy?.$modal.msgSuccess('流转卡创建成功');
  router.push(`/mes/pro/flow-card?mesWoId=${workOrder.value.mesWoId}`);
};

const goBack = () => {
  router.push('/mes/pro/workorder');
};

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    CREATED: '新建', APPROVING: '审批中', RELEASED: '已下达', IN_PROGRESS: '生产中', COMPLETED: '已完工', CANCELLED: '已取消'
  };
  return map[status] || status;
};
const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    CREATED: 'info', APPROVING: 'warning', RELEASED: 'success', IN_PROGRESS: 'warning', COMPLETED: 'primary', CANCELLED: 'danger'
  };
  return map[status] || 'info';
};
const stepStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待开始', READY: '就绪', IN_PROGRESS: '进行中', COMPLETED: '已完成', SKIPPED: '已跳过'
  };
  return map[status] || status;
};
const stepStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info', READY: 'warning', IN_PROGRESS: 'warning', COMPLETED: 'success', SKIPPED: 'info'
  };
  return map[status] || 'info';
};

onMounted(() => {
  loadDetail();
});
</script>
