<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode && !props.disabled">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && !props.disabled && form.status === 'DRAFT' && form.returnId"
              >提交审批</el-button
            >
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'CONFIRMED'">查看流程</el-button>
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="returnRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="退货单编号" prop="returnNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.returnNo" placeholder="请输入退货单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
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
            <el-form-item label="销售订单号" prop="soNo">
              <el-input v-model="form.soNo" placeholder="请输入销售订单号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker
                clearable
                v-model="form.returnDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择退货日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="退货类型" prop="returnType">
              <el-select v-model="form.returnType" placeholder="请选择退货类型">
                <el-option v-for="dict in so_return_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货原因" prop="returnReason">
              <el-input v-model="form.returnReason" placeholder="请输入退货原因" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退款金额" prop="refundAmount">
              <el-input v-model="form.refundAmount" placeholder="请输入退款金额" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">客户与仓库</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="客户" prop="clientName">
              <el-input v-model="form.clientName" placeholder="请选择客户" readonly>
                <template #append>
                  <el-button v-if="!isViewMode && !props.disabled" icon="Search" @click="clientSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请选择退货仓库" readonly>
                <template #append>
                  <el-button v-if="!isViewMode && !props.disabled" icon="Search" @click="warehouseSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">状态信息</el-divider>
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
            <el-form-item label="状态" prop="status">
              <el-tag :type="getStatusType(form.status)">{{ getStatusLabel(form.status) }}</el-tag>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="form.status === 'DRAFT' && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')">
          <el-col :span="24">
            <el-alert
              type="warning"
              show-icon
              :closable="false"
              title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 soReturnService。"
            />
          </el-col>
        </el-row>

        <el-divider content-position="center">退货行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode && !props.disabled">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode && !props.disabled">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="lineList" :row-class-name="rowLineIndex" @selection-change="handleLineSelectionChange">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode && !props.disabled" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请选择物料" v-if="!isViewMode && !props.disabled" readonly>
                <template #append><el-button icon="Search" @click="openMaterialSelect(scope.$index)" /></template>
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
            <template #default="scope"
              ><span>{{ scope.row.materialSpec }}</span></template
            >
          </el-table-column>
          <el-table-column label="退货数量" prop="returnQuantity" width="100">
            <template #default="scope">
              <el-input v-model="scope.row.returnQuantity" placeholder="数量" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.returnQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="100">
            <template #default="scope">
              <el-input v-model="scope.row.unitPrice" placeholder="单价" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.unitPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="100">
            <template #default="scope"
              ><span>{{ scope.row.amount }}</span></template
            >
          </el-table-column>
          <el-table-column label="税率(%)" prop="taxRate" width="80">
            <template #default="scope">
              <el-input v-model="scope.row.taxRate" placeholder="税率" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.taxRate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="退货原因" prop="returnReason" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.returnReason" placeholder="原因" v-if="!isViewMode && !props.disabled" />
              <span v-else>{{ scope.row.returnReason }}</span>
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

    <el-dialog title="选择客户" v-model="clientSelectOpen" width="800px" append-to-body>
      <el-form :model="clientQueryParams" :inline="true">
        <el-form-item label="客户编码"
          ><el-input v-model="clientQueryParams.clientCode" placeholder="请输入客户编码" clearable @keyup.enter="getClientList"
        /></el-form-item>
        <el-form-item label="客户名称"
          ><el-input v-model="clientQueryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="getClientList"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" icon="Search" @click="getClientList">搜索</el-button
          ><el-button icon="Refresh" @click="resetClientQuery">重置</el-button></el-form-item
        >
      </el-form>
      <el-table v-loading="clientLoading" :data="clientList" highlight-current-row @current-change="handleClientSelect">
        <el-table-column label="客户编码" align="center" prop="clientCode" />
        <el-table-column label="客户名称" align="center" prop="clientName" />
        <el-table-column label="操作" align="center"
          ><template #default="scope"
            ><el-button link type="primary" @click="handleClientSelect(scope.row)">选择</el-button></template
          ></el-table-column
        >
      </el-table>
      <pagination
        v-show="clientTotal > 0"
        :total="clientTotal"
        v-model:page="clientQueryParams.pageNum"
        v-model:limit="clientQueryParams.pageSize"
        @pagination="getClientList"
      />
    </el-dialog>

    <el-dialog title="选择仓库" v-model="warehouseSelectOpen" width="800px" append-to-body>
      <el-form :model="warehouseQueryParams" :inline="true">
        <el-form-item label="仓库编码"
          ><el-input v-model="warehouseQueryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="getWarehouseList"
        /></el-form-item>
        <el-form-item label="仓库名称"
          ><el-input v-model="warehouseQueryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="getWarehouseList"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" icon="Search" @click="getWarehouseList">搜索</el-button
          ><el-button icon="Refresh" @click="resetWarehouseQuery">重置</el-button></el-form-item
        >
      </el-form>
      <el-table v-loading="warehouseLoading" :data="warehouseList" highlight-current-row @current-change="handleWarehouseSelect">
        <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
        <el-table-column label="仓库名称" align="center" prop="warehouseName" />
        <el-table-column label="操作" align="center"
          ><template #default="scope"
            ><el-button link type="primary" @click="handleWarehouseSelect(scope.row)">选择</el-button></template
          ></el-table-column
        >
      </el-table>
      <pagination
        v-show="warehouseTotal > 0"
        :total="warehouseTotal"
        v-model:page="warehouseQueryParams.pageNum"
        v-model:limit="warehouseQueryParams.pageSize"
        @pagination="getWarehouseList"
      />
    </el-dialog>

    <MaterialSelectDialog ref="materialSelectDialogRef" :title="materialDialogTitle" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="ReturnForm">
import { getReturn, addReturn, updateReturn, submitReturnFlow, getReturnProcId } from '@/api/erp/so/return';
import { listClient } from '@/api/erp/bd/client';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { getMeasure } from '@/api/erp/bd/measure';
import { previewCode } from '@/api/system/autocode/rule';
import { getProcesss } from '@/api/workflow/process/index';
import { useRoute, useRouter } from 'vue-router';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';

const { proxy } = getCurrentInstance() as any;
const { so_return_type } = toRefs<any>(proxy.useDict('so_return_type'));
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
const checkedLines = ref<any[]>([]);
const loadingFlow = ref(false);
const flowOptions = ref([]);

const formDisabled = computed(() => isViewMode.value || props.disabled);

const materialSelectDialogRef = ref<any>(null);
const materialDialogTitle = ref('选择物料');
const currentMaterialRowIndex = ref(-1);

const clientSelectOpen = ref(false);
const clientList = ref([]);
const clientLoading = ref(false);
const clientTotal = ref(0);
const clientQueryParams = ref({ pageNum: 1, pageSize: 10, clientCode: null, clientName: null });

const warehouseSelectOpen = ref(false);
const warehouseList = ref([]);
const warehouseLoading = ref(false);
const warehouseTotal = ref(0);
const warehouseQueryParams = ref({ pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null });

const rules = {
  returnNo: [{ required: true, message: '退货单编号不能为空', trigger: 'blur' }]
};

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (action === 'add') {
    title.value = '新增退货单';
    reset();
    loadFlows();
  } else if (action === 'edit') {
    title.value = '修改退货单';
    if (id) getInfo(id);
    loadFlows();
  } else if (action === 'view') {
    title.value = '查看退货单';
    if (id) getInfo(id);
  }
});

function getInfo(id: any) {
  getReturn(id).then((response: any) => {
    form.value = response.data;
    lineList.value = response.data.soReturnLineList || [];
    tryAutoSelect();
  });
}

function reset() {
  autoGenFlag.value = false;
  form.value = {
    returnId: null,
    returnNo: null,
    soNo: null,
    clientId: null,
    clientName: null,
    returnDate: null,
    returnReason: null,
    returnType: null,
    totalAmount: null,
    taxAmount: null,
    refundAmount: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    status: null,
    remark: null,
    selectFlowId: null
  };
  lineList.value = [];
  proxy.resetForm('returnRef');
}

function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'soReturnService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      tryAutoSelect();
      loadingFlow.value = false;
    })
    .catch(() => {
      loadingFlow.value = false;
    });
}

function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).returnId != null) {
      const merged = JSON.parse(JSON.stringify(val)) as Record<string, any>;
      form.value = merged;
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '销售退货（流程）' : '销售退货';
      loadFlows();
    }
  },
  { deep: true, immediate: true }
);

const statusMap: Record<string, { label: string; type: 'primary' | 'success' | 'info' | 'warning' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  APPROVING: { label: '审批中', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  RECEIVED: { label: '已收货', type: 'primary' },
  CLOSED: { label: '已关闭', type: 'danger' }
};

function getStatusType(status: string): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  return statusMap[status]?.type || 'info';
}

function getStatusLabel(status: string): string {
  return statusMap[status]?.label || status || '未知';
}

function submitForm() {
  proxy.$refs['returnRef'].validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.returnNo = undefined;
      }
      form.value.soReturnLineList = lineList.value;
      if (form.value.returnId != null) {
        updateReturn(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/soBiz/return');
        });
      } else {
        addReturn(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/soBiz/return');
        });
      }
    }
  });
}

function handleSubmitFlow() {
  if (!form.value.selectFlowId) {
    proxy.$modal.msgWarning('请先选择审批流程');
    return;
  }
  if (form.value.returnId != null) {
    updateReturn(form.value).then(() => {
      submitReturnFlow(String(form.value.returnId), String(form.value.selectFlowId))
        .then((res: any) => {
          if (res.code === 200) {
            proxy.$modal.msgSuccess('提交审批成功');
            router.push('/erp/soBiz/return');
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

function onViewFlow() {
  getReturnProcId(String(form.value.returnId))
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

//自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('SO_RETURN_CODE').then((response) => {
      form.value.returnNo = response;
    });
  } else {
    form.value.returnNo = null;
  }
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/soBiz/return' });
}

function rowLineIndex({ row, rowIndex }: any): string {
  row.index = rowIndex + 1;
  return '';
}

function handleAddLine() {
  lineList.value.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    baseUomId: '',
    baseUomName: '',
    returnQuantity: null,
    unitPrice: null,
    amount: null,
    taxRate: null,
    returnReason: '',
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

function getClientList() {
  clientLoading.value = true;
  listClient(clientQueryParams.value).then((response: any) => {
    clientList.value = response.rows;
    clientTotal.value = response.total;
    clientLoading.value = false;
  });
}

function resetClientQuery() {
  clientQueryParams.value = { pageNum: 1, pageSize: 10, clientCode: null, clientName: null };
  getClientList();
}

function handleClientSelect(row: any) {
  form.value.clientId = row.clientId;
  form.value.clientName = row.clientName;
  clientSelectOpen.value = false;
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
  form.value.warehouseId = row.warehouseId;
  form.value.warehouseCode = row.warehouseCode;
  form.value.warehouseName = row.warehouseName;
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
