<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode && !props.disabled">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && !props.disabled && form.status === 'DRAFT' && form.transferId">提交审批</el-button>
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'APPROVED'">查看流程</el-button>
            <el-button type="warning" @click="handleExecute" v-if="!isViewMode && !props.disabled && form.status === 'APPROVED'">执行调拨</el-button>
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="transferRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="调拨单编号" prop="transferNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.transferNo" placeholder="请输入调拨单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode || props.disabled">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调拨类型" prop="transferType">
              <el-select v-model="form.transferType" placeholder="请选择调拨类型">
                <el-option v-for="dict in wm_transfer_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调拨日期" prop="transferDate">
              <el-date-picker
                clearable
                v-model="form.transferDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择调拨日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">仓库信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="调出仓库" prop="fromWarehouseName">
              <el-input v-model="form.fromWarehouseName" placeholder="请选择调出仓库" readonly>
                <template #append>
                  <el-button v-if="!isViewMode && !props.disabled" icon="Search" @click="openWarehouseSelect('from')" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调拨入库" prop="toWarehouseName">
              <el-input v-model="form.toWarehouseName" placeholder="请选择调拨入库" readonly>
                <template #append>
                  <el-button v-if="!isViewMode && !props.disabled" icon="Search" @click="openWarehouseSelect('to')" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">状态信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="单据状态" prop="status">
              <el-tag :type="getStatusType(form.status)">{{ getStatusLabel(form.status) }}</el-tag>
            </el-form-item>
          </el-col>
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
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 审批流程提示 -->
        <el-row :gutter="20" v-if="form.status === 'DRAFT' && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')">
          <el-col :span="24">
            <el-alert
              type="warning"
              show-icon
              :closable="false"
              title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 wmTransferService。"
            />
          </el-col>
        </el-row>

        <el-divider content-position="center">调拨行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode && !props.disabled">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode && !props.disabled">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="lineList" :row-class-name="rowLineIndex" @selection-change="handleLineSelectionChange" ref="lineTable">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode && !props.disabled" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请选择物料" v-if="!isViewMode && !props.disabled" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="物料名称" v-if="!isViewMode && !props.disabled" readonly />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规格型号" prop="materialSpec" width="120">
            <template #default="scope">
              <span>{{ scope.row.materialSpec }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" prop="quantity" width="100">
            <template #default="scope">
              <el-input v-model="scope.row.quantity" placeholder="数量" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.quantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="源库位" prop="fromLocationCode" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.fromLocationCode" placeholder="源库位" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.fromLocationCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标库位" prop="toLocationCode" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.toLocationCode" placeholder="目标库位" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.toLocationCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.remark" placeholder="备注" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.remark }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

    <el-dialog title="选择仓库" v-model="warehouseSelectOpen" width="800px" append-to-body>
      <el-form :model="warehouseQueryParams" :inline="true">
        <el-form-item label="仓库编码">
          <el-input v-model="warehouseQueryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="getWarehouseList" />
        </el-form-item>
        <el-form-item label="仓库名称">
          <el-input v-model="warehouseQueryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="getWarehouseList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getWarehouseList">搜索</el-button>
          <el-button icon="Refresh" @click="resetWarehouseQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="warehouseLoading" :data="warehouseList" highlight-current-row @current-change="handleWarehouseSelect">
        <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
        <el-table-column label="仓库名称" align="center" prop="warehouseName" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="handleWarehouseSelect(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="warehouseTotal > 0"
        :total="warehouseTotal"
        v-model:page="warehouseQueryParams.pageNum"
        v-model:limit="warehouseQueryParams.pageSize"
        @pagination="getWarehouseList"
      />
    </el-dialog>

    <MaterialSelectDialog
      ref="materialSelectDialogRef"
      :title="materialDialogTitle"
      :warehouse-id="form.fromWarehouseId"
      @select="handleMaterialSelected"
    />
  </div>
</template>

<script setup lang="ts" name="TransferForm">
import { getTransfer, addTransfer, updateTransfer, submitTransferFlow, getTransferProcId, executeTransfer } from '@/api/erp/wm/transfer';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { getMeasure } from '@/api/erp/bd/measure';
import { previewCode } from '@/api/system/autocode/rule';
import { getProcesss } from '@/api/workflow/process/index';
import { useRoute, useRouter } from 'vue-router';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';

const { proxy } = getCurrentInstance() as any;
const { wm_transfer_type } = toRefs<any>(proxy.useDict('wm_transfer_type'));
const route = useRoute();
const router = useRouter();

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

const form = ref<any>({});
const autoGenFlag = ref(false);
const isViewMode = ref(false);
const title = ref('');
const lineList = ref<any[]>([]);
const loadingFlow = ref(false);
const flowOptions = ref([]);

// 表单禁用状态（考虑 workflow 嵌入和查看模式）
const formDisabled = computed(() => isViewMode.value || props.disabled);

/**
 * 将流程下发的业务数据写入本地状态
 */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return;
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>;
  form.value = merged;
  lineList.value = Array.isArray(merged.wmTransferLineList) ? merged.wmTransferLineList : [];
}

/**
 * 加载审批流列表
 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'wmTransferService' })
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
 * 尝试自动选择流程（仅一个选项时自动选中）
 */
function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).transferId != null) {
      applyWorkflowFormPayload(val as Record<string, any>);
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '库存调拨（流程）' : '库存调拨';
      loadFlows();
    }
  },
  { deep: true, immediate: true }
);
const checkedLines = ref<any[]>([]);

const materialSelectDialogRef = ref<any>(null);
const materialDialogTitle = ref('选择物料');
const currentMaterialRowIndex = ref(-1);

const warehouseSelectOpen = ref(false);
const warehouseList = ref([]);
const warehouseLoading = ref(false);
const warehouseTotal = ref(0);
const warehouseSelectTarget = ref('');
const warehouseQueryParams = ref({ pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null });

const rules = {
  transferNo: [{ required: true, message: '调拨单编号不能为空', trigger: 'blur' }]
};

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (action === 'add') {
    title.value = '新增调拨单';
    reset();
    loadFlows();
  } else if (action === 'edit') {
    title.value = '修改调拨单';
    if (id) getInfo(id);
    loadFlows();
  } else if (action === 'view') {
    title.value = '查看调拨单';
    if (id) getInfo(id);
  }
});

function getInfo(id: any) {
  getTransfer(id).then((response: any) => {
    form.value = response.data;
    lineList.value = response.data.wmTransferLineList || [];
    tryAutoSelect();
  });
}

function reset() {
  autoGenFlag.value = false;
  form.value = {
    transferId: null,
    transferNo: null,
    transferType: null,
    fromWarehouseId: null,
    fromWarehouseCode: null,
    fromWarehouseName: null,
    toWarehouseId: null,
    toWarehouseCode: null,
    toWarehouseName: null,
    transferDate: null,
    status: 'DRAFT',
    remark: null,
    selectFlowId: null
  };
  lineList.value = [];
  proxy.resetForm('transferRef');
}

function submitForm() {
  proxy.$refs['transferRef'].validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.transferNo = undefined;
      }
      form.value.wmTransferLineList = lineList.value;
      if (form.value.transferId != null) {
        updateTransfer(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/inventory/transfer');
        });
      } else {
        addTransfer(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/inventory/transfer');
        });
      }
    }
  });
}

/**
 * 提交审批（Flowable）
 */
function handleSubmitFlow() {
  if (!form.value.selectFlowId) {
    proxy.$modal.msgWarning('请先选择审批流程');
    return;
  }
  form.value.wmTransferLineList = lineList.value;
  if (form.value.transferId != null) {
    updateTransfer(form.value).then(() => {
      submitTransferFlow(String(form.value.transferId), String(form.value.selectFlowId))
        .then((res: any) => {
          if (res.code === 200) {
            proxy.$modal.msgSuccess('提交审批成功');
            router.push('/erp/inventory/transfer');
          } else {
            proxy.$modal.msgError(res.msg || '提交审批失败');
          }
        })
        .catch((error: any) => {
          proxy.$modal.msgError('提交审批失败：' + error);
        });
    });
  } else {
    proxy.$modal.msgWarning('请先保存单据后再提交审批');
  }
}

/**
 * 查看流程
 */
function onViewFlow() {
  getTransferProcId(String(form.value.transferId))
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

/** 执行调拨 */
function handleExecute() {
  proxy.$modal
    .confirm('确认执行调拨单【' + form.value.transferNo + '】吗？执行后库存将过账。')
    .then(() => {
      return executeTransfer(form.value.transferId);
    })
    .then(() => {
      proxy.$modal.msgSuccess('调拨执行成功');
      getInfo(form.value.transferId);
    })
    .catch(() => {});
}

function getStatusType(status: string) {
  const typeMap: any = {
    'DRAFT': 'info',
    'APPROVING': 'warning',
    'APPROVED': 'success',
    'EXECUTED': 'success',
    'CANCELLED': 'danger'
  };
  return typeMap[status] || 'info';
}

function getStatusLabel(status: string) {
  const labelMap: any = {
    'DRAFT': '草稿',
    'APPROVING': '审批中',
    'APPROVED': '已批准',
    'EXECUTED': '已执行',
    'CANCELLED': '已取消'
  };
  return labelMap[status] || status;
}

//自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('WM_TRANSFER_CODE').then((response: any) => {
      form.value.transferNo = response;
    });
  } else {
    form.value.transferNo = null;
  }
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/inventory/transfer' });
}

function rowLineIndex({ row, rowIndex }: any) {
  row.index = rowIndex + 1;
}

function handleAddLine() {
  lineList.value.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    baseUomId: '',
    baseUomName: '',
    quantity: null,
    fromLocationCode: '',
    toLocationCode: '',
    remark: ''
  });
}

function handleDeleteLine() {
  if (checkedLines.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的行');
    return;
  }
  lineList.value = lineList.value.filter((item: any) => checkedLines.value.indexOf(item.index) == -1);
}

function handleLineSelectionChange(selection: any[]) {
  checkedLines.value = selection.map((item: any) => item.index);
}

function openMaterialSelect(rowIndex: number) {
  currentMaterialRowIndex.value = rowIndex;
  materialSelectDialogRef.value?.open();
}

function handleMaterialSelected(row: any) {
  const idx = currentMaterialRowIndex.value;
  if (idx >= 0 && lineList.value[idx]) {
    lineList.value[idx].materialId = row.materialId;
    lineList.value[idx].materialCode = row.materialCode;
    lineList.value[idx].materialName = row.materialName;
    lineList.value[idx].materialSpec = row.materialSpec;
    if (row.baseUomId) {
      lineList.value[idx].baseUomId = row.baseUomId;
      getMeasure(row.baseUomId).then((r: any) => {
        if (idx < lineList.value.length) lineList.value[idx].baseUomName = r.data.uomName;
      });
    }
  }
  currentMaterialRowIndex.value = -1;
}

function openWarehouseSelect(target: string) {
  warehouseSelectTarget.value = target;
  warehouseSelectOpen.value = true;
  getWarehouseList();
}

function getWarehouseList() {
  warehouseLoading.value = true;
  listWarehouse(warehouseQueryParams.value).then((response: any) => {
    warehouseList.value = response.rows;
    warehouseTotal.value = response.total;
    warehouseLoading.value = false;
  });
}

function resetWarehouseQuery() {
  warehouseQueryParams.value = { pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null };
  getWarehouseList();
}

function handleWarehouseSelect(row: any) {
  if (warehouseSelectTarget.value === 'from') {
    form.value.fromWarehouseId = row.warehouseId;
    form.value.fromWarehouseCode = row.warehouseCode;
    form.value.fromWarehouseName = row.warehouseName;
  } else {
    form.value.toWarehouseId = row.warehouseId;
    form.value.toWarehouseCode = row.warehouseCode;
    form.value.toWarehouseName = row.warehouseName;
  }
  warehouseSelectOpen.value = false;
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
