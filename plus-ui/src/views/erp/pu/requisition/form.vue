<template>
  <div class="app-container">
    <!-- 采购请购单表单卡片 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <!-- 保存按钮，仅在非查看模式下显示 -->
            <el-button
              type="primary"
              @click="submitForm"
              v-if="!isViewMode && !props.disabled && form.requisitionId && !protectsLegacyWrites"
              v-hasPermi="['pu:requisition:edit']"
              >保存</el-button
            >
            <!-- 提交审批按钮 -->
            <el-button
              type="success"
              @click="handleSubmitFlow"
              v-if="!isViewMode && !props.disabled && form.requisitionId && form.status === 'DRAFT' && !protectsLegacyWrites"
              >提交审批</el-button
            >
            <!-- 查看流程按钮 -->
            <el-button type="info" @click="onViewFlow" v-if="!protectsLegacyWrites && (form.status === 'APPROVING' || form.status === 'APPROVED')"
              >查看流程</el-button
            >
            <el-button
              v-hasPermi="['pu:requisition:query']"
              type="primary"
              plain
              @click="handleCertifiedWorkflow"
              v-if="form.requisitionId && protectsLegacyWrites && !props.disabled"
            >
              认证生命周期（当前详情只读）
            </el-button>
            <!-- 取消按钮 -->
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="!form.requisitionId"
        type="warning"
        show-icon
        :closable="false"
        title="普通 BO 新增已关闭，请返回采购申请列表使用独立的可信来源创建入口，先进行服务端完整预览。旧单据详情/维护不会升级为新认证。"
        style="margin-bottom: 12px"
      />
      <!-- 表单区域 -->
      <el-alert
        v-if="protectsLegacyWrites"
        title="认证来源或原生保护事实未知：普通编辑、旧Flowable及旧转单不开放。请从列表认证生命周期入口核验版本、来源和专属权限。"
        type="warning"
        show-icon
        :closable="false"
        class="mb-4"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <!-- 第一行：申请单号、申请名称、申请日期 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="申请单号" prop="requisitionNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.requisitionNo" placeholder="请输入申请单号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch
                  v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode || props.disabled"
                >
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请名称" prop="requisitionName">
              <el-input v-model="form.requisitionName" placeholder="请输入申请名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请日期" prop="requisitionDate">
              <el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择申请日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 采购部门 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="采购部门" prop="deptId">
              <dept-select v-model="form.deptId" category="PURCHASE" placeholder="请选择采购部门" @change="handleDeptChange" />
              <el-input v-model="form.deptName" style="display: none" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 需求信息 -->
        <el-divider content-position="left">需求信息</el-divider>
        <!-- 第二行：需求日期、来源类型、状态 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="需求日期" prop="requiredDate">
              <el-date-picker v-model="form.requiredDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择需求日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select
                v-model="form.sourceType"
                :disabled="form.sourceType === 'AI_DECISION' || isSourceLedgerPending(form.sourceType)"
                placeholder="请选择来源类型"
              >
                <el-option
                  v-for="dict in sourceTypeOptions"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                  :disabled="isSourceLedgerPending(dict.value)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- ERP-PU-04：服务端新增时强制 DRAFT 并忽略客户端 status，改为只读展示 -->
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" disabled placeholder="状态由服务端管理" style="width: 100%">
                <el-option v-for="dict in pu_purchase_requisition_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="!form.requisitionId && !isViewMode && !props.disabled"
          title="来源占用未接入：需求转入、MRP、低库存等自动来源暂不可新建；请选择手工或其他可用来源，以后端校验为准。"
          type="warning"
          :closable="false"
          show-icon
          class="mb8"
        />

        <!-- 审批信息 -->
        <el-divider content-position="left">审批信息</el-divider>
        <!-- 第三行：审批流程、备注 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select
                v-model="form.selectFlowId"
                clearable
                filterable
                :loading="loadingFlow"
                :disabled="isViewMode || props.disabled || form.status !== 'DRAFT'"
                placeholder="请选择审批流程"
                style="width: 100%"
              >
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 流程选择警告 -->
        <el-alert
          v-if="!isViewMode && !props.disabled && !loadingFlow && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')"
          type="warning"
          show-icon
          :closable="false"
          title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 puPurchaseRequisitionService。"
          style="margin-bottom: 12px"
        />

        <!-- 申请明细分隔符 -->
        <el-divider content-position="center">申请明细</el-divider>

        <!-- 明细操作按钮 -->
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode && !props.disabled && !protectsLegacyWrites"
              >添加</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode && !props.disabled && !protectsLegacyWrites"
              >删除</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="success"
              icon="Position"
              @click="handleConvert"
              v-if="!isViewMode && !props.disabled && form.requisitionId && !protectsLegacyWrites"
              >转采购订单</el-button
            >
          </el-col>
        </el-row>

        <!-- 申请明细表格 -->
        <el-table :data="lineList" @selection-change="handleLineSelectionChange" border>
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode && !props.disabled" />
          <el-table-column label="序号" type="index" width="60" align="center" />
          <el-table-column label="物料编码" width="140">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="选择物料" readonly>
                <template #append>
                  <!-- 选择物料按钮 -->
                  <el-button icon="Search" @click="openMaterial(scope.$index)" />
                </template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" width="140">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" />
            </template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.materialSpec" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="100">
            <template #default="scope">
              <el-input v-model="scope.row.unit" />
            </template>
          </el-table-column>
          <el-table-column label="申请数量" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.applyQuantity" />
            </template>
          </el-table-column>
          <el-table-column label="需求日期" width="160">
            <template #default="scope">
              <el-date-picker v-model="scope.row.requiredDate" type="date" value-format="YYYY-MM-DD" placeholder="需求日期" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="备注" width="160">
            <template #default="scope">
              <el-input v-model="scope.row.remark" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

    <!-- 物料选择对话框 -->
    <CertifiedRequisitionWorkflowDialog ref="certifiedWorkflowDialogRef" :read-only="true" @changed="refreshCertifiedDetails" />
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="PurchaseRequisitionForm">
/** 导入请购单相关的API方法 */
import {
  getPurchaseRequisition,
  updatePurchaseRequisition,
  convertPurchaseRequisition,
  submitRequisitionFlow,
  getRequisitionProcId
} from '@/api/erp/pu/requisition';
/** 导入物料管理API */
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
/** 导入审批流API */
import { getProcesss } from '@/api/workflow/process/index';
/** 导入路由相关功能 */
import { useRoute, useRouter } from 'vue-router';
import { previewCode } from '@/api/system/autocode/rule';
import { decisionPurchaseResultQuery, verifiedDecisionErpResult } from '@/views/ai/decision/decisionActionExecutionView';
import CertifiedRequisitionWorkflowDialog from './CertifiedRequisitionWorkflowDialog.vue';
import { isProtectedRequisitionHint, normalizeRequisitionId } from './certifiedWorkflow';
import type { PurchaseRequisitionVO } from '@/api/erp/pu/requisition/types';

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

/** 获取当前实例的代理对象和字典数据 */
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { pu_purchase_requisition_status, pu_source_type } = toRefs<any>(proxy.useDict('pu_purchase_requisition_status', 'pu_source_type'));
/** 路由和导航 */
const route = useRoute();
const router = useRouter();

/** 页面状态变量 */
const title = ref(''); /** 页面标题 */
const isViewMode = ref(false); /** 是否为查看模式 */
const autoGenFlag = ref(false); /** 自动生成编号开关 */
/** 初始化表单数据，包含 requisitionId 字段 */
const form = ref<any>({
  requisitionId: null,
  requisitionNo: null,
  requisitionName: null,
  requisitionDate: null,
  requiredDate: null,
  sourceType: 'MANUAL',
  status: 'DRAFT',
  selectFlowId: null,
  remark: null,
  puPurchaseRequisitionLineList: []
}); /** 表单数据 */
/** 仅限制前端手工选择；能否新增仍以后端来源占用校验为准。 */
const pendingSourceLabels: Record<string, string> = {
  REQ_CONVERT: '需求转入',
  MRP: 'MRP 需求',
  PRD_MRP: '生产 MRP 需求',
  MATERIAL_REQUIREMENT: '物料需求',
  LOW_STOCK: '低库存',
  MES_SPARE_LOW_STOCK: 'MES 备件低库存'
};
const isSourceLedgerPending = (source?: string) =>
  typeof source === 'string' && Object.prototype.hasOwnProperty.call(pendingSourceLabels, source.trim().toUpperCase());
/** AI 来源仅供历史展示；受限来源保留禁用选项，未入字典的历史来源补只读标签。 */
const sourceTypeOptions = computed(() => {
  const options = (pu_source_type.value || []).filter((dict: any) => dict.value !== 'AI_DECISION' || form.value.sourceType === 'AI_DECISION');
  const sourceType = form.value.sourceType;
  if (isSourceLedgerPending(sourceType) && !options.some((dict: any) => dict.value === sourceType)) {
    return [...options, { value: sourceType, label: pendingSourceLabels[sourceType.trim().toUpperCase()] }];
  }
  return options;
});
/** 与可编辑form分离保存原生根字段，局部修改source/marker不能解除普通写保护。 */
const loadedRequisitionSnapshot = ref<PurchaseRequisitionVO | null>(null);
const certifiedWorkflowDialogRef = ref<InstanceType<typeof CertifiedRequisitionWorkflowDialog>>();
/** 未载入、身份不匹配或已认证均只读；客户端保护不替代后端永久来源与权限检查。 */
const protectsLegacyWrites = computed(() => {
  if (!form.value.requisitionId) return false;
  const original = loadedRequisitionSnapshot.value;
  try {
    return (
      !original ||
      normalizeRequisitionId(original.requisitionId) !== normalizeRequisitionId(form.value.requisitionId) ||
      isProtectedRequisitionHint(original)
    );
  } catch {
    return true;
  }
});
const lineList = ref<any[]>([]); /** 请购明细列表 */
const checkedLines = ref<any[]>([]); /** 已选中的明细行 */
const currentMaterialIndex = ref(-1); /** 当前选择的物料索引 */

/** 流程相关状态 */
const loadingFlow = ref(false);
const flowOptions = ref([]);

/** 表单禁用状态（考虑流程嵌入和查看模式）。 */
const formDisabled = computed(() => isViewMode.value || props.disabled || !form.value.requisitionId || protectsLegacyWrites.value);

/** 物料选择对话框 */
const materialSelectDialogRef = ref<any>(null);

/** 表单验证规则 */
const rules = {
  requisitionNo: [{ required: true, message: '申请单号不能为空', trigger: 'blur' }]
};

/** 将流程下发的业务数据写入本地状态 */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return;
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>;
  loadedRequisitionSnapshot.value = { ...merged };
  form.value = merged;
  lineList.value = Array.isArray(merged.puPurchaseRequisitionLineList) ? merged.puPurchaseRequisitionLineList : [];
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).requisitionId != null) {
      applyWorkflowFormPayload(val as Record<string, any>);
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '采购申请（流程）' : '采购申请';
      loadFlows();
    }
  },
  { deep: true, immediate: true }
);

/** 仅承接决策结果只读深链，普通编辑/新增及流程嵌入不走此分支。 */
const isDecisionResultRoute = () =>
  route.path === '/erp/pu/requisition/form' &&
  route.query.decisionResultRequisitionId !== undefined &&
  !props.disabled &&
  !Object.prototype.hasOwnProperty.call(props.customFormData, 'requisitionId');
let detailRequestSeq = 0;
let loadedOrdinaryIdentity = '';
let pendingOrdinaryIdentity = '';
const detailRouteIdentity = () => JSON.stringify([route.path, route.query.id, route.query.action, route.query.decisionResultRequisitionId]);
/** 恢复只属于当前普通表单页的合法身份，不抢占流程props或误用其他页面的ID。 */
function restoreOrdinaryRoute() {
  if (
    route.path !== '/erp/pu/requisition/form' ||
    route.query.decisionResultRequisitionId !== undefined ||
    props.disabled ||
    Object.prototype.hasOwnProperty.call(props.customFormData, 'requisitionId')
  )
    return;
  const identity = detailRouteIdentity();
  const action = route.query.action;
  if (action === 'add') {
    isViewMode.value = false;
    title.value = '新增采购申请';
    if (loadedOrdinaryIdentity !== identity) {
      reset();
      loadedOrdinaryIdentity = identity;
      loadFlows();
    }
    return;
  }
  const id = route.query.id;
  if (
    (action !== 'view' && action !== 'edit') ||
    typeof id !== 'string' ||
    !/^[1-9][0-9]{0,18}$/.test(id) ||
    (id.length === 19 && id > '9223372036854775807')
  ) {
    isViewMode.value = true;
    title.value = '采购申请';
    return;
  }
  isViewMode.value = action === 'view';
  title.value = action === 'edit' ? '修改采购申请' : '查看采购申请';
  /** 已载入的编辑表单保留未保存修改；仅空白/失效身份恢复只读加载。 */
  if (loadedOrdinaryIdentity === identity || pendingOrdinaryIdentity === identity) return;
  loadedOrdinaryIdentity = '';
  form.value = {};
  lineList.value = [];
  getInfo(id);
  loadFlows();
}
async function loadDecisionResult() {
  const requestSeq = ++detailRequestSeq;
  loadedOrdinaryIdentity = '';
  pendingOrdinaryIdentity = '';
  isViewMode.value = true;
  title.value = '采购申请（决策结果只读）';
  form.value = {};
  lineList.value = [];
  const id = decisionPurchaseResultQuery(route.query);
  if (!id) {
    proxy.$modal.msgWarning('决策结果只读参数无效，未查询任何单据');
    return;
  }
  try {
    const response = await getPurchaseRequisition(id);
    if (requestSeq !== detailRequestSeq || !isDecisionResultRoute() || decisionPurchaseResultQuery(route.query) !== id) return;
    if (!verifiedDecisionErpResult({ kind: 'requisition', id }, response.data)) throw new Error('RESULT_NOT_VERIFIED');
    form.value = response.data;
    lineList.value = response.data.puPurchaseRequisitionLineList || [];
  } catch {
    if (requestSeq === detailRequestSeq) proxy.$modal.msgError('回查未完成，请检查查询权限及真实结果身份');
  }
}
/** 布局按 path 缓存，第二张结果必须重新只读查询；旧响应不得覆盖新单。 */
watch(
  () => [route.path, route.query.id, route.query.action, route.query.decisionResultRequisitionId],
  () => {
    if (isDecisionResultRoute()) void loadDecisionResult();
    else {
      detailRequestSeq++;
      pendingOrdinaryIdentity = '';
      restoreOrdinaryRoute();
    }
  }
);
onBeforeUnmount(() => {
  detailRequestSeq++;
});

/** 挂载及缓存返回共用路由初始化，不移除原失效序列/身份守卫。 */
onMounted(() => {
  if (isDecisionResultRoute()) void loadDecisionResult();
  else restoreOrdinaryRoute();
});
/**
 * 根据ID获取采购请购单详情
 * @param id 采购请购单ID
 */
function getInfo(id: any) {
  /** 普通详情和决策结果共享失效序列；缓存路由变化前后的旧单不能回写。 */
  const requestSeq = ++detailRequestSeq;
  const routeIdentity = detailRouteIdentity();
  pendingOrdinaryIdentity = routeIdentity;
  getPurchaseRequisition(id)
    .then((res: any) => {
      if (requestSeq !== detailRequestSeq || routeIdentity !== detailRouteIdentity() || isDecisionResultRoute()) return;
      if (res.data?.requisitionId == null) throw new Error('DETAIL_NOT_FOUND');
      loadedRequisitionSnapshot.value = { ...res.data };
      form.value = res.data;
      lineList.value = res.data.puPurchaseRequisitionLineList || [];
      tryAutoSelect();
      loadedOrdinaryIdentity = routeIdentity;
      pendingOrdinaryIdentity = '';
    })
    .catch(() => {
      if (requestSeq !== detailRequestSeq || routeIdentity !== detailRouteIdentity() || isDecisionResultRoute()) return;
      /** 加载失败不标记已载入，下次合法返回可重试；不输出原始异常正文。 */
      pendingOrdinaryIdentity = '';
      proxy.$modal.msgError('采购详情加载未完成，请检查查询权限及单据是否存在');
    });
}
/**
 * 重置表单数据
 */
function reset() {
  loadedRequisitionSnapshot.value = null;
  autoGenFlag.value = false;
  form.value = {
    requisitionId: null,
    requisitionNo: null,
    requisitionName: null,
    requisitionDate: null,
    requiredDate: null,
    sourceType: 'MANUAL',
    status: 'DRAFT',
    selectFlowId: null,
    remark: null,
    deptId: null,
    deptName: null
  };
  lineList.value = [];
}

/**
 * 加载审批流列表
 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'puPurchaseRequisitionService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      tryAutoSelect();
      loadingFlow.value = false;
    })
    .catch(() => {
      loadingFlow.value = false;
    });
}

/**
 * 尝试自动选择流程
 */
function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

/**
 * 提交审批
 */
function handleSubmitFlow() {
  if (legacyWriteBlocked()) return;
  if (!form.value.requisitionId) {
    proxy.$modal.msgWarning('普通 BO 新增已关闭，请使用可信来源创建并先保存真实单据');
    return;
  }
  let requisitionId: string;
  try {
    requisitionId = normalizeRequisitionId(form.value.requisitionId);
  } catch {
    proxy.$modal.msgWarning('原采购申请身份未确认，禁止提交旧流程');
    return;
  }
  const flowValue = form.value.selectFlowId;
  const flowId =
    typeof flowValue === 'string' ? flowValue : typeof flowValue === 'number' && Number.isSafeInteger(flowValue) ? String(flowValue) : '';
  if (!flowId) {
    proxy.$modal.msgWarning('请先选择身份明确的审批流程');
    return;
  }
  const operationRoute = detailRouteIdentity();
  const operationSequence = detailRequestSeq;
  /** 校验、更新、流程提交三个异步边界都绑定原身份；新只读/受保护单据不得承接旧回调。 */
  const stillCurrent = () => {
    if (operationSequence !== detailRequestSeq || operationRoute !== detailRouteIdentity() || legacyWriteBlocked()) return false;
    try {
      return normalizeRequisitionId(form.value.requisitionId) === requisitionId && String(form.value.selectFlowId) === flowId;
    } catch {
      return false;
    }
  };
  (proxy.$refs['formRef'] as any).validate((valid: boolean) => {
    if (!valid || !stillCurrent()) return;
    form.value.puPurchaseRequisitionLineList = lineList.value;
    /** 原手工单据仍先更新；后续只使用已绑定的身份，不读取随后切换的form作为流程目标。 */
    updatePurchaseRequisition(form.value)
      .then(() => {
        if (!stillCurrent()) return;
        return submitRequisitionFlow(requisitionId, flowId).then((res: any) => {
          if (!stillCurrent()) return;
          if (res.code === 200) {
            proxy.$modal.msgSuccess('提交审批成功');
            router.push('/erp/pu/requisition');
          } else {
            proxy.$modal.msgError('原审批提交未确认，请检查权限及当前单据状态');
          }
        });
      })
      .catch(() => {
        if (stillCurrent()) proxy.$modal.msgError('原审批提交未完成，请检查权限及当前单据状态');
      });
  });
}
/**
 * 查看流程
 */
function onViewFlow() {
  getRequisitionProcId(String(form.value.requisitionId))
    .then((res: any) => {
      if (res.code === 200 && res.data) {
        router.push('/workflow/process/detail/' + res.data);
      } else {
        proxy.$modal.msgWarning('暂无流程信息');
      }
    })
    .catch(() => {
      proxy.$modal.msgWarning('获取流程信息失败');
    });
}

/**
 * 添加请购明细行
 */
function handleAddLine() {
  lineList.value.push({
    materialCode: null,
    materialName: null,
    materialSpec: null,
    unit: null,
    applyQuantity: null,
    requiredDate: null,
    remark: null
  });
}

/**
 * 删除请购明细行
 */
function handleDeleteLine() {
  lineList.value = lineList.value.filter((_, i) => !checkedLines.value.includes(i));
}

/**
 * 处理明细表格的选择变化
 * @param rows 选中的行数据
 */
function handleLineSelectionChange(rows: any[]) {
  checkedLines.value = rows.map((r: any) => lineList.value.indexOf(r));
}

/**
 * 打开物料选择对话框
 * @param index 当前行索引
 */
function openMaterial(index: number) {
  currentMaterialIndex.value = index;
  materialSelectDialogRef.value?.open();
}

/**
 * 物料选择回调
 * @param row 选中的物料数据
 */
function handleMaterialSelected(row: any) {
  if (currentMaterialIndex.value < 0) return;
  const target = lineList.value[currentMaterialIndex.value];
  target.materialId = row.materialId;
  target.materialCode = row.materialCode;
  target.materialName = row.materialName;
  target.materialSpec = row.materialSpec;
  target.unit = row.baseUomName || row.unitName || row.unit;
}

/**
 * 提交表单
 */
function submitForm() {
  if (legacyWriteBlocked()) return;
  if (!form.value.requisitionId) {
    proxy.$modal.msgWarning('普通 BO 新增已关闭，请返回列表使用可信来源创建');
    return;
  }
  form.value.puPurchaseRequisitionLineList = lineList.value;
  /** 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号 */
  if (autoGenFlag.value) {
    form.value.requisitionNo = undefined;
  }
  updatePurchaseRequisition(form.value).then(() => {
    proxy.$modal.msgSuccess('保存成功');
    router.push('/erp/pu/requisition');
  });
}

/** 直接调用旧handler也必须封闭只读、决策深链或认证来源，不仅依赖按钮隐藏。 */
function legacyWriteBlocked() {
  if (isViewMode.value || props.disabled || isDecisionResultRoute() || protectsLegacyWrites.value) {
    proxy.$modal.msgWarning('当前单据只读或来源受保护，禁止普通写入；请从列表认证生命周期入口核验');
    return true;
  }
  return false;
}

/** 详情模式只读打开专属面板，避免查看/决策深链悄然获得写入口。 */
function handleCertifiedWorkflow() {
  if (!form.value.requisitionId) return;
  certifiedWorkflowDialogRef.value?.open(normalizeRequisitionId(form.value.requisitionId), { readOnly: true });
}
function refreshCertifiedDetails() {
  if (form.value.requisitionId && !props.disabled && !isDecisionResultRoute()) getInfo(form.value.requisitionId);
}

/**
 * 转换为采购订单
 */
function handleConvert() {
  if (legacyWriteBlocked()) return;
  convertPurchaseRequisition(form.value.requisitionId).then(() => {
    proxy.$modal.msgSuccess('转单成功');
    router.push('/erp/puExec/order');
  });
}

/**
 * 取消并返回列表页
 */
/** 自动生成编号 */
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('PU_REQUISITION_CODE').then((response) => {
      form.value.requisitionNo = response;
    });
  } else {
    form.value.requisitionNo = null;
  }
}

/** 采购部门变更：回填部门名称 */
function handleDeptChange(dept) {
  form.value.deptName = dept ? dept.deptName : null;
}

function cancel() {
  router.push('/erp/pu/requisition');
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.card-actions {
  display: flex;
  gap: 10px;
}
.mb8 {
  margin-bottom: 8px;
}
:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__label) {
  font-weight: 500;
}
</style>
