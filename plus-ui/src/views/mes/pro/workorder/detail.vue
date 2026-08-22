<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>MES工单详情 - {{ workOrder.woNo }}</span>
          <div style="float: right">
            <el-tooltip
              v-if="canCreateFlowCard"
              :content="`工单计划数量已全部分配（${workOrder.plannedQty}）`"
              :disabled="remainingQty === undefined || remainingQty > 0"
              placement="top"
            >
              <span style="margin-right: 8px; display: inline-block">
                <el-button
                  v-if="!props.disabled"
                  type="success"
                  icon="Stamp"
                  @click="handleCreateFlowCard"
                  :disabled="remainingQty !== undefined && remainingQty <= 0"
                  >创建流转卡</el-button
                >
              </span>
            </el-tooltip>
            <el-button type="primary" plain @click="goBack" v-if="!props.disabled">返回</el-button>
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
            <el-descriptions-item label="BOM版本">{{ workOrder.bomVersion || workOrder.bomId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工艺路线">{{
              workOrder.routingCode && workOrder.routingName ? workOrder.routingCode + ' - ' + workOrder.routingName : workOrder.routingId || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="发布包">
              <span v-if="workOrder.releasePackageCode"> {{ workOrder.releasePackageCode }} (v{{ workOrder.releasePackageVer }}) </span>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="定制配置">
              <template v-if="releaseConfigItems.length">
                <el-tag v-for="(c, i) in releaseConfigItems" :key="i" size="small" style="margin: 2px" type="warning">
                  {{ c.configKey }}={{ c.configValue }}
                </el-tag>
              </template>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="关联设备">{{
              workOrder.equipmentCode && workOrder.equipmentName
                ? workOrder.equipmentCode + ' - ' + workOrder.equipmentName
                : workOrder.equipmentId
                  ? String(workOrder.equipmentId)
                  : '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <dict-tag :options="prd_wo_status" :value="workOrder.woStatus" />
            </el-descriptions-item>
            <el-descriptions-item label="计划开工">{{ workOrder.planStartDate }}</el-descriptions-item>
            <el-descriptions-item label="计划完工">{{ workOrder.planEndDate }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ workOrder.remark }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="工序步骤" name="steps">
          <el-alert
            v-if="flashUnboundNames"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom: 8px"
            :title="`以下刷写工序未绑定刷写内容(固件/配置), 下达/提交审批将被拦截: ${flashUnboundNames}`"
          />
          <el-table :data="workOrder.steps" border>
            <el-table-column label="序号" prop="sequenceNum" width="80" align="center" />
            <el-table-column label="工序编码" prop="processCode" width="140" />
            <el-table-column label="工序名称" prop="processName" />
            <el-table-column label="工序类型" prop="stepType" width="110" align="center">
              <template #default="scope">
                <dict-tag :options="mes_step_type" :value="scope.row.stepType" />
              </template>
            </el-table-column>
            <el-table-column label="工作中心" prop="workcenterCode" width="140" />
            <el-table-column label="关键工序" prop="isKeyProcess" width="100" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.isKeyProcess === 1" type="danger">是</el-tag>
                <span v-else>否</span>
              </template>
            </el-table-column>
            <el-table-column label="刷写内容" min-width="220" align="center">
              <template #default="scope">
                <span v-if="scope.row.stepType === 'FLASH' && !scope.row.contentRefType && !scope.row.stepFlashConfig" style="color: #f56c6c"
                  >未绑定</span
                >
                <span v-else-if="scope.row.contentRefType">
                  {{ scope.row.contentRefType }} #{{ scope.row.contentRefId }}
                  <el-tag v-if="scope.row.firmwareSource" size="small" type="info" style="margin-left: 4px">{{
                    firmwareSourceLabel(scope.row.firmwareSource)
                  }}</el-tag>
                </span>
                <span v-else-if="scope.row.stepFlashConfig" style="color: #909399">多类型绑定</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column v-if="workOrder.woStatus === 'CREATED' && !props.disabled" label="固件操作" width="110" align="center">
              <template #default="scope">
                <el-button
                  v-if="scope.row.stepType === 'FLASH'"
                  link
                  type="primary"
                  icon="Edit"
                  @click="handleChangeFirmware(scope.row)"
                  v-hasPermi="['mes:pro:work:edit']"
                  >{{ scope.row.contentRefType ? '更改' : '绑定' }}</el-button
                >
              </template>
            </el-table-column>
            <el-table-column label="计划数量" prop="plannedQty" width="100" align="right" />
            <el-table-column label="状态" prop="stepStatus" width="100" align="center">
              <template #default="scope">
                <dict-tag :options="mes_step_status" :value="scope.row.stepStatus" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="物料明细(MBOM)" name="bom">
          <el-table :data="workOrder.bomLines || []" border>
            <el-table-column label="排序" prop="sortOrder" width="60" align="center" />
            <el-table-column label="物料编码" prop="childMaterialCode" width="150" />
            <el-table-column label="物料名称" prop="childMaterialName" min-width="150" show-overflow-tooltip />
            <el-table-column label="单位用量" prop="quantityPerParent" width="100" align="right" />
            <el-table-column label="损耗率(%)" prop="lossRate" width="90" align="right" />
            <el-table-column label="含损耗用量" prop="totalQuantity" width="110" align="right">
              <template #default="scope">
                <span style="font-weight: bold">{{ scope.row.totalQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="工序步骤" prop="sequenceNum" width="80" align="center" />
            <el-table-column label="工序" prop="processName" width="100" />
            <el-table-column label="工位" prop="workstationName" width="100" />
            <el-table-column label="作业内容" prop="operationContent" min-width="150" show-overflow-tooltip />
            <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip>
              <template #default="scope">
                <el-tag v-if="isPkgSelected(scope.row)" type="warning" size="small">发布包选配</el-tag>
                <span style="margin-left: 4px">{{ scope.row.remark }}</span>
              </template>
            </el-table-column>
            <el-table-column label="倒冲" prop="backflushFlag" width="60" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.backflushFlag === 1" type="warning" size="small">是</el-tag>
                <span v-else>否</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!workOrder.bomLines || workOrder.bomLines.length === 0" description="暂无MBOM快照数据" />
        </el-tab-pane>

        <el-tab-pane label="审批流程" name="approval">
          <el-form :inline="true">
            <el-form-item label="审批流程" v-if="workOrder.woStatus === 'CREATED' && !props.disabled">
              <el-select v-model="selectFlowId" clearable filterable placeholder="请选择审批流程" style="width: 300px">
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                icon="Promotion"
                @click="handleSubmitFlow"
                v-if="workOrder.woStatus === 'CREATED' && !props.disabled"
                :disabled="!selectFlowId"
              >
                提交审批
              </el-button>
              <el-button type="info" icon="View" @click="handleViewFlow" v-if="procInsId"> 查看流程 </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 固件版本选择弹窗(CREATED状态工单, FLASH工序更改/绑定固件) -->
    <FirmwareSelectDialog ref="firmwareSelectRef" @select="handleFirmwareSelected" />
  </div>
</template>

<script setup lang="ts" name="MesWorkOrderDetail">
import { getWorkOrder, submitWorkOrderFlow, getWorkOrderProcId, getProcessList, updateStepFirmware } from '@/api/mes/pro/workorder';
import { addFlowCard, listFlowCardByWoId } from '@/api/mes/pro/flowcardApi';
import type { ProFlowCardVO } from '@/api/mes/pro/flowcard';
import type { ProWorkOrderVO, ProWorkOrderStepVO } from '@/api/mes/pro/types';
import FirmwareSelectDialog from './components/FirmwareSelectDialog.vue';
import { checkPermi } from '@/utils/permission';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_wo_status, mes_step_status, mes_step_type, mes_firmware_source } = toRefs<any>(
  proxy?.useDict('prd_wo_status', 'mes_step_status', 'mes_step_type', 'mes_firmware_source')
);
const route = useRoute();
const router = useRouter();

/** 流程详情嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

const workOrder = ref<ProWorkOrderVO>({} as ProWorkOrderVO);
const selectFlowId = ref<string>('');
const procInsId = ref<string>('');
const flowOptions = ref<any[]>([]);
const activeTab = ref('info');
/** 工单下已建流转卡及剩余可建数量(用于创建流转卡时数量校验) */
const existingFlowCards = ref<ProFlowCardVO[]>([]);
const usedQty = ref<number>(0);
const remainingQty = ref<number | undefined>(undefined);
/** 是否可创建流转卡: 工单已下达/生产中/部分完成 且 拥有创建权限 */
const canCreateFlowCard = computed(
  () =>
    (workOrder.value.woStatus === 'RELEASED' || workOrder.value.woStatus === 'IN_PROGRESS' || workOrder.value.woStatus === 'PARTIAL_COMPLETED') &&
    checkPermi(['mes:pro:flowcard:add'])
);

// ==================== 发布包/固件绑定相关 ====================

const firmwareSelectRef = ref<InstanceType<typeof FirmwareSelectDialog>>();
/** 当前正在更改固件的工序步骤 */
const currentStep = ref<ProWorkOrderStepVO | null>(null);

/** 发布包配置项快照(解析 releaseConfig JSON) */
const releaseConfigItems = computed<any[]>(() => {
  if (!workOrder.value.releaseConfig) return [];
  try {
    return JSON.parse(workOrder.value.releaseConfig);
  } catch (e) {
    return [];
  }
});

/** 未绑定刷写内容的 FLASH 工序(下达/提交审批门禁警示) */
const flashUnboundNames = computed(() => {
  const steps = workOrder.value.steps || [];
  return steps
    .filter((s: ProWorkOrderStepVO) => s.stepType === 'FLASH' && !s.contentRefType && !s.stepFlashConfig)
    .map((s: ProWorkOrderStepVO) => `工序${s.sequenceNum}-${s.processName}`)
    .join('、');
});

/** BOM快照行是否为发布包选配覆写行(备注含标记) */
const isPkgSelected = (row: any) => !!row.remark && row.remark.includes('发布包选配');

/** 固件来源标签翻译 */
const firmwareSourceLabel = (value: string) => {
  const dict = mes_firmware_source.value?.find((d: any) => d.value === value);
  return dict?.label || value;
};

/** 更改/绑定工序固件(仅CREATED状态, 弹窗选择该物料已发布固件) */
const handleChangeFirmware = (row: ProWorkOrderStepVO) => {
  currentStep.value = row;
  firmwareSelectRef.value?.open(row, workOrder.value.materialId, workOrder.value.materialCode, workOrder.value.materialName);
};

/** 固件选择确认 -> 调用后端更新(校验状态/固件RELEASED/物料匹配) */
const handleFirmwareSelected = async (fw: any) => {
  if (!currentStep.value) return;
  await updateStepFirmware(workOrder.value.mesWoId, currentStep.value.stepId, fw.firmwareId);
  proxy?.$modal.msgSuccess(`工序固件已更新为 ${fw.firmwareCode} (v${fw.version}), 下达后冻结`);
  loadDetail();
};

const loadDetail = async (mesWoIdParam?: string) => {
  const mesWoId = mesWoIdParam || (route.params.mesWoId as string);
  if (!mesWoId) return;
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
  // 已下达/生产中/部分完成可创建流转卡,预加载已建流转卡用于数量校验
  if (workOrder.value.woStatus === 'RELEASED' || workOrder.value.woStatus === 'IN_PROGRESS' || workOrder.value.woStatus === 'PARTIAL_COMPLETED') {
    loadFlowCardSummary(mesWoId);
  }
};

/** 加载工单下已建流转卡,计算已建/剩余可建数量 */
const loadFlowCardSummary = async (mesWoId: string | number) => {
  try {
    const res = await listFlowCardByWoId(mesWoId);
    existingFlowCards.value = res?.data || [];
    usedQty.value = existingFlowCards.value.reduce((sum, c) => sum + Number(c.plannedQty || 0), 0);
    const planned = Number(workOrder.value.plannedQty || 0);
    remainingQty.value = Math.max(0, planned - usedQty.value);
  } catch (e) {
    existingFlowCards.value = [];
    usedQty.value = 0;
    remainingQty.value = undefined;
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
  const planned = Number(workOrder.value.plannedQty || 0);
  const used = usedQty.value;
  const remaining = remainingQty.value === undefined ? planned - used : remainingQty.value;
  if (remaining <= 0) {
    proxy?.$modal.msgWarning(`工单计划数量已全部分配（计划${planned}，已建${used}），无法继续创建流转卡`);
    return;
  }
  const { value: qty } = await proxy?.$modal.prompt(
    `请输入本流转卡计划数量（工单计划 ${planned}，已建 ${used}，剩余可建 ${remaining}）：`,
    '创建流转卡 - ' + workOrder.value.woNo
  );
  if (!qty || isNaN(Number(qty)) || Number(qty) <= 0) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  if (Number(qty) > remaining) {
    proxy?.$modal.msgWarning(`数量超过剩余可建数量 ${remaining}，请重新输入`);
    return;
  }
  await addFlowCard(workOrder.value.mesWoId, Number(qty));
  proxy?.$modal.msgSuccess('流转卡创建成功');
  router.push(`/mes/wo/flow-card?mesWoId=${workOrder.value.mesWoId}`);
};

const goBack = () => {
  router.push('/mes/wo/workorder');
};

/**
 * 流程详情嵌入时：父组件注入 customFormData（含 mesWoId），据此加载工单详情。
 */
watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).mesWoId != null) {
      loadDetail(String((val as any).mesWoId));
    }
  },
  { deep: true, immediate: true }
);

onMounted(() => {
  loadDetail();
});
</script>
