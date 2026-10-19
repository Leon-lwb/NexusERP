<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button
              type="success"
              @click="handleExecute"
              v-if="!isViewMode && canExecute"
              :disabled="!executionWarehouseReady || !executionAtpReady"
              :loading="executeLoading"
              >执行领料</el-button
            >
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="!isViewMode && canExecute && !executionWarehouseReady"
        title="请先选择实际领料仓库：可选择单头仓库，或为本次领料的每行选择库存仓库；选工单不会自动指定仓库"
        type="warning"
        :closable="false"
        show-icon
        class="mb8"
      />

      <el-form ref="issueRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="领料单编号" prop="issueCode">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.issueCode" placeholder="请输入领料单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联工单" prop="woNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.woNo" placeholder="请选择工单" readonly style="flex: 1" />
                <el-button v-if="!isViewMode" icon="Search" @click="workOrderSelectOpen = true" />
                <el-button type="primary" link @click="handleAutoFillFromWorkOrder" v-if="!isViewMode && form.woId">
                  <el-icon><MagicStick /></el-icon> 带出BOM
                </el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="领料日期" prop="issueDate">
              <el-date-picker
                clearable
                v-model="form.issueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择领料日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请选择仓库" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="warehouseSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="领料类型" prop="issueType">
              <el-select v-model="form.issueType" placeholder="请选择领料类型" style="width: 100%">
                <el-option label="普通领料" value="NORMAL" />
                <el-option label="倒冲领料" value="BACKFLUSH" :disabled="true" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="领料人" prop="issuerName">
              <el-input v-model="form.issuerName" placeholder="领料人" />
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

        <el-form-item label="参考总金额">
          <span>{{ formatReferenceAmount(referenceTotal, referenceKnown) }}</span>
          <span class="ml4">参考金额不代表实际库存成本；任一行未计价时总额未知</span>
        </el-form-item>
        <el-divider content-position="center">领料明细</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode && canEditLine">添加行</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode && canEditLine">删除行</el-button>
          </el-col>
        </el-row>
        <el-table :data="lineList" border stripe show-overflow-tooltip height="420" @selection-change="handleLineSelectionChange">
          <el-table-column type="selection" width="45" align="center" :selectable="canCheckLine" v-if="!isViewMode && canEditLine" />
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="materialCode" label="物料编码" min-width="170">
            <template #default="{ row, $index }">
              <el-input v-model="row.materialCode" placeholder="选择物料" readonly>
                <template #append><el-button icon="Search" @click="openMaterialSelect($index)" :disabled="isViewMode" /></template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column prop="materialName" label="物料名称" min-width="130" />
          <el-table-column prop="materialSpec" label="规格型号" min-width="110" />
          <el-table-column prop="baseUomName" label="单位" width="70" align="center" />
          <el-table-column prop="bomQty" label="BOM用量" width="90" align="center" />
          <el-table-column prop="lossRate" label="损耗率%" width="80" align="center" />
          <el-table-column prop="requiredQty" label="应领数量" width="90" align="center" />
          <el-table-column prop="issuedQty" label="已领数量" width="90" align="center" />
          <el-table-column prop="currentQty" label="本次领料" width="110" align="center">
            <template #default="{ row }">
              <el-input-number
                v-if="!isViewMode && canEditLine"
                v-model="row.currentQty"
                :max="scopedAvailableQuantity(row) ?? undefined"
                :disabled="scopedAvailableQuantity(row) == null"
                :min="0"
                :precision="4"
                :controls="false"
                style="width: 100%"
                @change="recalcRow(row)"
              />
              <span v-else>{{ row.currentQty ?? '' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="shortQty" label="欠领数量" width="90" align="center" />
          <el-table-column prop="availableQty" label="可用库存" width="90" align="center">
            <template #default="{ row }">
              <span
                v-if="scopedAvailableQuantity(row) != null"
                :class="{ 'text-red': readDecimalNumber(row.currentQty) != null && !quantityWithinAtp(row.currentQty, scopedAvailableQuantity(row)) }"
              >
                {{ scopedAvailableQuantity(row) }}
              </span>
              <span v-else>未知</span>
            </template>
          </el-table-column>
          <el-table-column prop="referenceAvailableQty" label="全仓参考" width="100" align="center">
            <template #default="{ row }"
              ><span>{{ row.referenceAvailableQty ?? '未知' }}</span></template
            >
          </el-table-column>
          <el-table-column prop="warehouseName" label="本行仓库" min-width="175">
            <template #default="{ row, $index }">
              <span>{{
                row.warehouseId
                  ? row.warehouseName || row.warehouseCode || row.warehouseId
                  : form.warehouseId
                    ? (form.warehouseName || form.warehouseId) + '（单头）'
                    : '未选仓库'
              }}</span>
              <el-button v-if="canSelectStock(row)" type="primary" link @click="openStockSelect($index)">选库存/换仓</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="displayLocationBatch" label="批次/库位" min-width="190">
            <template #default="{ row, $index }">
              <el-input v-model="row.displayLocationBatch" placeholder="选择库存" readonly>
                <template #append><el-button icon="Search" @click="openStockSelect($index)" :disabled="!canSelectStock(row)" /></template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="参考单价" width="100" align="center">
            <template #default="{ row }">
              <el-input-number
                v-if="!isViewMode && canEditLine"
                v-model="row.unitPrice"
                placeholder="未计价"
                :min="0"
                :precision="4"
                :controls="false"
                style="width: 100%"
                @change="handleReferencePriceChange(row)"
              />
              <span v-else>{{ formatReferenceAmount(row.unitPrice, readDecimalNumber(row.unitPrice) != null) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="参考金额" width="100" align="center">
            <template #default="{ row }">
              <span>{{ formatReferenceAmount(referenceLineAmount(row), referenceLineAmount(row) != null) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120">
            <template #default="{ row }">
              <el-input v-if="!isViewMode && canEditLine" v-model="row.remark" placeholder="备注" />
              <span v-else>{{ row.remark }}</span>
            </template>
          </el-table-column>
        </el-table>
        <MaterialTraceLines
          :rows="lineList"
          :header="form"
          scene="PRODUCTION_ISSUE"
          quantity-key="currentQty"
          :disabled="isViewMode || !canEditLine"
        />
      </el-form>
    </el-card>

    <!-- 选择仓库 -->
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

    <!-- 选择工单 -->
    <el-dialog title="选择工单" v-model="workOrderSelectOpen" width="960px" append-to-body>
      <el-form :model="workOrderQueryParams" :inline="true">
        <el-form-item label="工单编号"
          ><el-input v-model="workOrderQueryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="getWorkOrderList"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" icon="Search" @click="getWorkOrderList">搜索</el-button
          ><el-button icon="Refresh" @click="resetWorkOrderQuery">重置</el-button></el-form-item
        >
      </el-form>
      <el-table v-loading="workOrderLoading" :data="workOrderList" highlight-current-row height="420">
        <el-table-column label="工单编号" align="center" prop="woNo" />
        <el-table-column label="产品编码" align="center" prop="materialCode" />
        <el-table-column label="产品名称" align="center" prop="materialName" />
        <el-table-column label="计划数量" align="center" prop="plannedQty" />
        <el-table-column label="状态" align="center" prop="woStatus" />
        <el-table-column label="操作" align="center" width="100"
          ><template #default="scope"><el-button link type="primary" @click="selectWorkOrder(scope.row)">选择</el-button></template></el-table-column
        >
      </el-table>
      <pagination
        v-show="workOrderTotal > 0"
        :total="workOrderTotal"
        v-model:page="workOrderQueryParams.pageNum"
        v-model:limit="workOrderQueryParams.pageSize"
        @pagination="getWorkOrderList"
      />
    </el-dialog>

    <!-- 选择物料 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" :warehouse-id="form.warehouseId" @select="handleMaterialSelected" />

    <!-- 选择可用库存（批次/库位） -->
    <el-dialog title="选择库存（批次/库位）" v-model="stockSelectOpen" width="1120px" append-to-body>
      <el-alert title="列出该物料在所有仓库的可用库存，请选择具体批次/库位" type="info" :closable="false" show-icon class="mb8" />
      <el-table v-loading="stockLoading" :data="stockList" highlight-current-row height="420">
        <el-table-column label="批次号" align="center" prop="batchCode" />
        <el-table-column label="仓库" align="center" prop="warehouseName" />
        <el-table-column label="库区" align="center" prop="areaCode" />
        <el-table-column label="库位" align="center" prop="locationCode" />
        <el-table-column label="账面数量" align="center" prop="stockQuantity" />
        <el-table-column label="预留数量" align="center" prop="reservedQuantity" />
        <el-table-column label="可用数量" align="center" prop="availableQuantity">
          <template #default="{ row }"
            ><span>{{ row.availableQuantity ?? '未知' }}</span></template
          >
        </el-table-column>
        <el-table-column label="库存单位成本" align="center" prop="unitCost">
          <template #default="{ row }"
            ><span>{{ row.unitCost ?? '未知' }}</span></template
          >
        </el-table-column>
        <el-table-column label="有效期" align="center" prop="expireDate" width="110">
          <template #default="scope"
            ><span>{{ parseTime(scope.row.expireDate, '{y}-{m}-{d}') }}</span></template
          >
        </el-table-column>
        <el-table-column label="操作" align="center" width="100"
          ><template #default="scope"
            ><el-button
              link
              type="primary"
              :disabled="
                scope.row.stockQuantity == null ||
                scope.row.reservedQuantity == null ||
                scope.row.availableQuantity == null ||
                scope.row.availableQuantity <= 0
              "
              @click="selectStock(scope.row)"
              >选择</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <template #footer
        ><div class="dialog-footer"><el-button @click="stockSelectOpen = false">取 消</el-button></div></template
      >
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ProductionIssueForm">
import { showTraceSubmission } from '@/components/material-trace/submission';
import MaterialTraceLines from '@/components/material-trace/MaterialTraceLines.vue';
import {
  getProductionIssue,
  addProductionIssue,
  updateProductionIssue,
  executeProductionIssue,
  getAvailableStock,
  getWorkOrderLines,
  getAvailableStockBatch
} from '@/api/erp/wm/productionissue';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { listWork } from '@/api/erp/prd/work';
import { useRoute, useRouter } from 'vue-router';
import { previewCode } from '@/api/system/autocode/rule';
import { useUserStore } from '@/store/modules/user';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { formatReferenceAmount, referenceLineAmount, referenceAmountState } from './referenceAmount';
import { readDecimalNumber, quantityWithinAtp, normalizeIssueLine, normalizeStockCandidate, stockCandidateIdentity } from './quantity';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { parseTime } = proxy;
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const form = ref<any>({});
const autoGenFlag = ref(false);
const isViewMode = ref(false);
const title = ref('');
const lineList = ref<any[]>([]);
/** 参考总额从当前明细计算，不信任历史单头的零金额。 */
const referenceKnown = computed(() => {
  const explicitlyKnown =
    form.value.referenceAmountKnown === true || !form.value.issueId || lineList.value.some((line) => line.referenceEdited === true);
  return explicitlyKnown && referenceAmountState(lineList.value).referenceAmountKnown;
});
const referenceTotal = computed(() => (referenceKnown.value ? referenceAmountState(lineList.value).amount : null));
const executeLoading = ref(false);

/** 物料选择 */
const materialSelectDialogRef = ref<any>(null);
const currentRowIndex = ref(-1);

/** 仓库选择 */
const warehouseSelectOpen = ref(false);
const warehouseList = ref([]);
const warehouseLoading = ref(false);
const warehouseTotal = ref(0);
const warehouseQueryParams = ref({ pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null, status: 1 });

/** 工单选择 */
const workOrderSelectOpen = ref(false);
const workOrderList = ref([]);
const workOrderLoading = ref(false);
const workOrderTotal = ref(0);
const workOrderQueryParams = ref({ pageNum: 1, pageSize: 10, woNo: null });

/** 可用库存选择 */
const stockSelectOpen = ref(false);
const stockList = ref([]);
const stockLoading = ref(false);
/** 当前查询的成功标记与候选身份快照；新的打开请求立即使旧候选失效。 */
const stockQuery = ref<any>(null);
const stockRequestId = ref(0);

const rules = {
  issueCode: [{ required: true, message: '领料单编号不能为空', trigger: 'blur' }]
};

/** 已完成（COMPLETED/EXECUTED）后行不可编辑、不可执行 */
const canEditLine = computed(() => {
  const s = form.value.status;
  return !s || s === 'CREATED' || s === 'PARTIAL' || s === 'DRAFT';
});
const canExecute = computed(() => {
  const s = form.value.status;
  return !s || s === 'CREATED' || s === 'PARTIAL' || s === 'DRAFT';
});
/** 行仓优先于单头仓库；只检查本次确实领料的行，未领料草稿仍可保存。 */
const executionWarehouseReady = computed(() => {
  const headerReady = !!form.value.warehouseId;
  const pickedLines = lineList.value.filter((line: any) => Number(line.currentQty) > 0);
  return pickedLines.length > 0 ? pickedLines.every((line: any) => !!line.warehouseId || headerReady) : headerReady;
});

/** 未知或超过 ATP 的本次量不可执行，字符串数量必须先归一。 */
const executionAtpReady = computed(() => {
  let hasPick = false;
  for (const line of lineList.value) {
    const quantity = readDecimalNumber(line.currentQty);
    if (quantity == null || quantity < 0) return false;
    if (quantity === 0) continue;
    hasPick = true;
    if (!quantityWithinAtp(quantity, scopedAvailableQuantity(line))) return false;
  }
  return hasPick;
});

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (action === 'add') {
    title.value = '新增生产领料';
    reset();
  } else if (action === 'edit') {
    title.value = '修改生产领料';
    if (id) getInfo(id);
  } else if (action === 'view') {
    title.value = '查看生产领料';
    if (id) getInfo(id);
  }
});

function getInfo(id: any) {
  getProductionIssue(id).then((response: any) => {
    form.value = { ...response.data, referenceAmountKnown: response.data.referenceAmountKnown === true };
    const lines = response.data.wmProductionIssueLineList || [];
    /** 旧行数量缺失时保持未知，不能据此推算可靠欠领量或参考金额。 */
    lineList.value = lines.map((l: any) => ({
      ...normalizeIssueLine(l),
      lineStatus: l.lineStatus || 'PENDING',
      displayLocationBatch: (l.locationCode ? l.locationCode + '/' : '') + (l.batchCode || '')
    }));
    /** 加载各行物料的总可用库存供参考 */
    loadAvailableStocks();
  });
}

function reset() {
  autoGenFlag.value = false;
  form.value = {
    issueId: null,
    issueCode: null,
    woId: null,
    woNo: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    issueDate: null,
    issueType: 'NORMAL',
    status: 'CREATED',
    issuerId: userStore.userId,
    issuerName: userStore.nickname,
    totalQty: null,
    totalAmount: null,
    referenceAmountKnown: false,
    remark: null
  };
  lineList.value = [];
  proxy.resetForm('issueRef');
}

function submitForm() {
  (proxy.$refs['issueRef'] as any).validate((valid: boolean) => {
    if (!valid) return;
    /** 行校验 */
    for (const l of lineList.value) {
      if (!l.materialId) {
        proxy.$modal.msgError('存在未选择物料的行');
        return;
      }
    }
    if (autoGenFlag.value) form.value.issueCode = undefined;
    form.value.wmProductionIssueLineList = lineList.value;
    if (form.value.issueId != null) {
      updateProductionIssue(form.value).then(() => {
        proxy.$modal.msgSuccess('保存成功');
        router.push('/erp/outbound/productionissue');
      });
    } else {
      addProductionIssue(form.value).then(() => {
        proxy.$modal.msgSuccess('新增成功');
        router.push('/erp/outbound/productionissue');
      });
    }
  });
}

/** 执行领料：先保存本次领料数量，再调用过账，再重取单据刷新已领/欠领/状态 */
function handleExecute() {
  if (!form.value.issueId) {
    proxy.$modal.msgWarning('请先保存领料单再执行');
    return;
  }
  if (!executionWarehouseReady.value) {
    proxy.$modal.msgWarning('请先选择实际领料仓库：单头选仓或为本次领料的每行选库存仓库');
    return;
  }
  if (!executionAtpReady.value) {
    proxy.$modal.msgWarning('本次领料数量超过 ATP 或可用库存未知，请刷新库存后核对');
    return;
  }
  let hasPick = false;
  for (const l of lineList.value) {
    if (l.currentQty && Number(l.currentQty) > 0) {
      hasPick = true;
      break;
    }
  }
  if (!hasPick) {
    proxy.$modal.msgWarning('请填写本次领料数量后再执行');
    return;
  }
  proxy.$modal
    .confirm('确认按本次领料数量执行过账并扣减库存？')
    .then(() => {
      executeLoading.value = true;
      form.value.wmProductionIssueLineList = lineList.value;
      updateProductionIssue(form.value)
        .then(() => executeProductionIssue(form.value.issueId))
        .then((response) => {
          showTraceSubmission(proxy.$modal, response, '执行成功，库存已扣减');
          getInfo(form.value.issueId);
        })
        .catch((cause) => {
          if (cause !== 'cancel' && cause !== 'close') proxy.$modal.msgWarning(cause instanceof Error ? cause.message : '追溯校验失败');
        })
        .finally(() => {
          executeLoading.value = false;
        });
    })
    .catch((cause) => {
      if (cause !== 'cancel' && cause !== 'close') proxy.$modal.msgWarning(cause instanceof Error ? cause.message : '追溯校验失败');
    });
}

function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_PROD_ISSUE_CODE').then((response: any) => {
      form.value.issueCode = response;
    });
  } else {
    form.value.issueCode = null;
  }
}

/** 选择工单后，从后端按工单BOM物料需求带出应领明细（排除倒冲料） */
async function handleAutoFillFromWorkOrder() {
  if (!form.value.woId) {
    proxy.$modal.msgWarning('请先选择关联工单');
    return;
  }
  try {
    const res: any = await getWorkOrderLines(form.value.woId);
    const lines = res.data || [];
    if (!lines.length) {
      proxy.$modal.msgWarning('该工单无非倒冲物料需求，无法带出');
      return;
    }
    lineList.value = lines.map((line: any) => normalizeIssueLine(line));
    proxy.$modal.msgSuccess('已根据工单BOM带出领料明细');
    /** 加载各行物料的总可用库存供参考 */
    loadAvailableStocks();
  } catch (e) {
    proxy.$modal.msgError('带出BOM失败，请检查工单信息');
  }
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/outbound/productionissue' });
}

function handleAddLine() {
  lineList.value.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    baseUomId: '',
    baseUomName: '',
    bomQty: null,
    lossRate: null,
    requiredQty: null,
    issuedQty: 0,
    currentQty: null,
    shortQty: null,
    availableQty: null,
    requirementDetailId: null,
    lineStatus: 'PENDING',
    batchId: null,
    batchCode: '',
    locationId: null,
    locationCode: '',
    locationName: '',
    displayLocationBatch: '',
    unitPrice: null,
    amount: null,
    remark: ''
  });
}

/** 表格勾选行（el-table selection-change 同步） */
const selectedLineRows = ref<any[]>([]);

function handleLineSelectionChange(rows: any[]) {
  selectedLineRows.value = rows;
}

function handleDeleteLine() {
  if (!selectedLineRows.value.length) {
    proxy.$modal.msgError('请先选择要删除的行');
    return;
  }
  lineList.value = lineList.value.filter((row: any) => !selectedLineRows.value.includes(row));
  selectedLineRows.value = [];
}

/** 行是否可勾选删除：已领料（ISSUED 或 issuedQty>0）的行禁止删除，避免库存流水孤立 */
function canCheckLine(row: any) {
  return row.lineStatus !== 'ISSUED' && !(Number(row.issuedQty) > 0);
}

/** 选范围与删除、执行数量守卫分离：零量或未填量可恢复库存来源，已领数量和原行ID不变。 */
function canSelectStock(row: any) {
  if (isViewMode.value || !canEditLine.value || !row?.materialId || ['ISSUED', 'SHORT_CLOSED', 'COMPLETED', 'EXECUTED'].includes(row.lineStatus)) {
    return false;
  }
  /** 未填或零本次量可建立范围；已提供的非法、负量仍不能选择。 */
  if (row.currentQty != null && row.currentQty !== '') {
    const currentQty = readDecimalNumber(row.currentQty);
    if (currentQty == null || currentQty < 0) return false;
  }
  const issuedQty = Number(row.issuedQty ?? 0);
  const requiredQty = Number(row.requiredQty);
  if (requiredQty > 0) {
    return Number.isFinite(requiredQty) && Number.isFinite(issuedQty) && requiredQty > issuedQty;
  }
  /** 旧行无应领定额时可按欠领量判断；未领料手工行则允许指定首次库存。 */
  if (row.shortQty != null) {
    const shortQty = Number(row.shortQty);
    return Number.isFinite(shortQty) && shortQty > 0;
  }
  return row.lineStatus !== 'PARTIAL' && issuedQty === 0;
}

/** 重新计算参考金额，清除参考价时同时清除旧金额，不以库存成本兜底。 */
function recalcRow(row: any) {
  row.currentQty = readDecimalNumber(row.currentQty);
  row.amount = referenceLineAmount({ ...row, amount: null });
  row.referenceEdited = true;
}

/** 只在用户主动清价时标记显式清空，保存批次不得隐式清价。 */
function handleReferencePriceChange(row: any) {
  row.unitPrice = readDecimalNumber(row.unitPrice);
  row.clearReferencePrice = row.unitPrice == null;
  row.amount = referenceLineAmount({ ...row, amount: null });
  row.referenceEdited = true;
}

function openMaterialSelect(rowIndex: number) {
  currentRowIndex.value = rowIndex;
  materialSelectDialogRef.value?.open();
}

function handleMaterialSelected(row: any) {
  const idx = currentRowIndex.value;
  if (idx >= 0 && lineList.value[idx]) {
    lineList.value[idx].materialId = row.materialId;
    lineList.value[idx].materialCode = row.materialCode;
    lineList.value[idx].materialName = row.materialName;
    lineList.value[idx].materialSpec = row.materialSpec;
    if (row.baseUomName) lineList.value[idx].baseUomName = row.baseUomName;
  }
  currentRowIndex.value = -1;
  /** 选完物料后刷新该行可用库存 */
  refreshLineAvailableStock(row.materialId, idx);
}

/** 当前可执行范围：行仓优先于单头仓，批次和库位缺省时保留仓内汇总语义。 */
function lineStockScope(line: any) {
  const warehouseId = line.warehouseId ?? form.value.warehouseId;
  if (line.materialId == null || warehouseId == null || warehouseId === '') return null;
  const key = [line.materialId, warehouseId, line.batchId ?? '', line.locationId ?? ''].map(String).join('/');
  return { key, materialId: line.materialId, warehouseId, batchId: line.batchId, locationId: line.locationId };
}

/** 只有当前范围已确认的独立 ATP 才是可靠上限，身份变化后立即视为未知。 */
function scopedAvailableQuantity(line: any): number | null {
  const scope = lineStockScope(line);
  return scope != null && line.atpScopeKey === scope.key ? readDecimalNumber(line.availableQty) : null;
}

/** 使用既有库存候选接口按仓查询，再按本行批次/库位核对；旧响应不得恢复上限。 */
function loadLineAvailableStock(line: any) {
  const requestId = (line.atpRequestId ?? 0) + 1;
  line.atpRequestId = requestId;
  line.availableQty = null;
  line.atpScopeKey = null;
  const scope = lineStockScope(line);
  if (scope == null) return;
  getAvailableStock(scope.materialId, scope.warehouseId)
    .then((response: any) => {
      if (!lineList.value.includes(line) || line.atpRequestId !== requestId || lineStockScope(line)?.key !== scope.key) return;
      if (!Array.isArray(response.data)) return;
      const matching = response.data.filter(
        (stock: any) =>
          String(stock.materialId) === String(scope.materialId) &&
          String(stock.warehouseId) === String(scope.warehouseId) &&
          (scope.batchId == null || String(stock.batchId) === String(scope.batchId)) &&
          (scope.locationId == null || String(stock.locationId) === String(scope.locationId))
      );
      /** 候选接口仅返回已知正 ATP；无匹配记录不能用全仓参考量证明本行可靠上限。 */
      if (!matching.length) return;
      let available = 0;
      for (const stock of matching) {
        const quantity = readDecimalNumber(stock.availableQuantity);
        if (readDecimalNumber(stock.stockQuantity) == null || readDecimalNumber(stock.reservedQuantity) == null || quantity == null || quantity < 0)
          return;
        available += quantity;
      }
      if (!Number.isFinite(available)) return;
      line.availableQty = available;
      line.atpScopeKey = scope.key;
    })
    .catch(() => {
      if (line.atpRequestId !== requestId) return;
      line.availableQty = null;
      line.atpScopeKey = null;
    });
}

/** 全仓总量仅供参考，绝不写入实际领料范围上限。 */
function loadAvailableStocks() {
  const rows = lineList.value.map((line: any) => {
    line.referenceAvailableQty = null;
    line.summaryRequestId = (line.summaryRequestId ?? 0) + 1;
    loadLineAvailableStock(line);
    return { line, materialId: line.materialId, requestId: line.summaryRequestId };
  });
  const ids = rows.map((row) => row.materialId).filter(Boolean);
  if (!ids.length) return;
  getAvailableStockBatch(ids)
    .then((res: any) => {
      const map = res.data || {};
      for (const { line, materialId, requestId } of rows) {
        if (!lineList.value.includes(line) || line.summaryRequestId !== requestId || String(line.materialId) !== String(materialId)) continue;
        line.referenceAvailableQty = materialId == null ? null : readDecimalNumber(map[materialId]);
      }
    })
    .catch(() => {});
}

/** 切换物料后独立刷新范围上限，全仓参考量不能替代未确认的行仓范围。 */
function refreshLineAvailableStock(materialId: number | null, rowIndex: number) {
  const line = lineList.value[rowIndex];
  if (!line) return;
  line.referenceAvailableQty = null;
  line.summaryRequestId = (line.summaryRequestId ?? 0) + 1;
  const requestId = line.summaryRequestId;
  loadLineAvailableStock(line);
  if (materialId == null) return;
  getAvailableStockBatch([materialId])
    .then((res: any) => {
      if (!lineList.value.includes(line) || line.summaryRequestId !== requestId || String(line.materialId) !== String(materialId)) return;
      line.referenceAvailableQty = readDecimalNumber((res.data || {})[materialId]);
    })
    .catch(() => {});
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
  warehouseQueryParams.value = { pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null, status: 1 };
  getWarehouseList();
}

function handleWarehouseSelect(row: any) {
  if (!row) return;
  if (!row.warehouseId || Number(row.status) !== 1) {
    proxy.$modal.msgWarning('请选择已启用的真实仓库');
    return;
  }
  form.value.warehouseId = row.warehouseId;
  form.value.warehouseCode = row.warehouseCode;
  form.value.warehouseName = row.warehouseName;
  /** 单头仓改变后仅重查依赖单头仓的行，已绑定行仓不可被隐式改绑。 */
  for (const line of lineList.value) {
    if (line.warehouseId == null) loadLineAvailableStock(line);
  }
  warehouseSelectOpen.value = false;
}

/** 工单选择 */
function getWorkOrderList() {
  workOrderLoading.value = true;
  listWork(workOrderQueryParams.value).then((response: any) => {
    workOrderList.value = response.rows;
    workOrderTotal.value = response.total;
    workOrderLoading.value = false;
  });
}

function resetWorkOrderQuery() {
  workOrderQueryParams.value = { pageNum: 1, pageSize: 10, woNo: null };
  getWorkOrderList();
}

function selectWorkOrder(row: any) {
  /** 已保存的同一工单再次被选中时保留原领料行（包括已选择的行仓/批次）。 */
  const preserveLines = form.value.issueId && form.value.woId != null && String(form.value.woId) === String(row.woId) && lineList.value.length > 0;
  form.value.woId = row.woId;
  form.value.woNo = row.woNo;
  workOrderSelectOpen.value = false;
  if (!preserveLines) handleAutoFillFromWorkOrder();
}

/** 可用库存选择：新请求立即清旧候选，只有最新成功响应允许选择。 */
function openStockSelect(rowIndex: number) {
  const requestId = ++stockRequestId.value;
  stockList.value = [];
  stockQuery.value = null;
  stockLoading.value = false;
  const row = lineList.value[rowIndex];
  if (!row || !row.materialId) {
    proxy.$modal.msgWarning('请先选择物料');
    return;
  }
  if (!canSelectStock(row)) {
    proxy.$modal.msgWarning('仅未完成且有待领数量的明细可选择库存');
    return;
  }
  /** 查询待确认或失败时不能继续把本行旧 ATP 视为可靠执行上限。 */
  row.availableQty = null;
  row.atpScopeKey = null;
  row.atpRequestId = (row.atpRequestId ?? 0) + 1;
  currentRowIndex.value = rowIndex;
  stockSelectOpen.value = true;
  stockLoading.value = true;
  const materialId = String(row.materialId);
  stockQuery.value = { requestId, row, materialId, successful: false, identities: new Map() };
  /** 保留跨仓选择，候选自己的真实仓库进入查询身份快照，不能被选择前改写。 */
  getAvailableStock(row.materialId)
    .then((response: any) => {
      if (stockRequestId.value !== requestId || !stockQuery.value || stockQuery.value.row !== row) return;
      if (!Array.isArray(response.data)) throw new Error('库存响应缺少有效候选集合');
      const candidates = response.data
        .map((stock: any) => normalizeStockCandidate(stock))
        .filter(
          (stock: any) =>
            String(stock.materialId) === materialId &&
            stock.stockId != null &&
            stock.warehouseId != null &&
            stock.stockQuantity != null &&
            stock.reservedQuantity != null &&
            stock.availableQuantity != null &&
            stock.availableQuantity > 0
        );
      stockList.value = candidates;
      stockQuery.value.identities = new Map(candidates.map((stock: any) => [stock, stockCandidateIdentity(stock)]));
      stockQuery.value.successful = true;
    })
    .catch(() => {
      if (stockRequestId.value !== requestId) return;
      stockList.value = [];
      stockQuery.value = null;
    })
    .finally(() => {
      if (stockRequestId.value === requestId) stockLoading.value = false;
    });
}

function selectStock(row: any) {
  const query = stockQuery.value;
  const line = lineList.value[currentRowIndex.value];
  if (
    !query?.successful ||
    stockLoading.value ||
    !stockSelectOpen.value ||
    query.requestId !== stockRequestId.value ||
    !line ||
    query.row !== line ||
    !stockList.value.includes(row) ||
    String(line.materialId) !== query.materialId ||
    String(row?.materialId) !== query.materialId ||
    row.stockId == null ||
    row.warehouseId == null ||
    query.identities.get(row) !== stockCandidateIdentity(row) ||
    readDecimalNumber(row.stockQuantity) == null ||
    readDecimalNumber(row.reservedQuantity) == null ||
    !(readDecimalNumber(row.availableQuantity) > 0) ||
    (readDecimalNumber(line.currentQty) != null &&
      readDecimalNumber(line.currentQty) !== 0 &&
      !quantityWithinAtp(line.currentQty, row.availableQuantity))
  ) {
    proxy.$modal.msgWarning('当前库存候选未成功读取、身份已变化或 ATP 不足，请重新查询');
    return;
  }
  if (!canSelectStock(line)) {
    proxy.$modal.msgWarning('当前明细已不可选料，请核对单据和明细状态');
    return;
  }
  line.warehouseId = row.warehouseId;
  line.warehouseCode = row.warehouseCode;
  line.warehouseName = row.warehouseName;
  line.batchId = row.batchId;
  line.batchCode = row.batchCode;
  line.locationId = row.locationId;
  line.locationCode = row.locationCode;
  line.locationName = row.locationName;
  line.displayLocationBatch = (row.locationCode ? row.locationCode + '/' : '') + (row.batchCode || '');
  /** 库存单位成本不得填参考价，独立 ATP 上限必须使用归一数值。 */
  line.availableQty = readDecimalNumber(row.availableQuantity);
  line.atpScopeKey = lineStockScope(line)?.key ?? null;
  line.atpRequestId = (line.atpRequestId ?? 0) + 1;
  stockSelectOpen.value = false;
  stockQuery.value = null;
  currentRowIndex.value = -1;
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.mb8 {
  margin-bottom: 8px;
}
.text-red {
  color: #f56c6c;
  font-weight: 600;
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
:deep(.el-input-number) {
  .el-input__inner {
    text-align: center;
  }
}
</style>
