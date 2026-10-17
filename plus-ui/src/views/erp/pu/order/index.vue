<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="订单号" prop="orderNo">
            <el-input v-model="queryParams.orderNo" placeholder="请输入订单号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in pu_purchase_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"
        ><BusinessReportButton dataset-code="ERP_PURCHASE_ORDER_DOCUMENT" parameter="orderId" :record-ids="ids" :documents="reportDocuments"
      /></el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['pu:order:add']"> 新增 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['pu:order:edit']"> 修改 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['pu:order:remove']"> 删除 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['pu:order:query']"> 查看 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['pu:order:export']"> 导出 </el-button>
      </el-col>
      <!--
      <el-col :span="1.5">
        <el-button type="success" plain icon="Promotion" :disabled="single" @click="handleSubmit()" v-hasPermi="['pu:order:edit']"> 提交 </el-button>
      </el-col>
      -->
      <!--
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Check" :disabled="single" @click="handleApprove()" v-hasPermi="['pu:order:edit']"> 审批 </el-button>
      </el-col>
      -->
      <el-col :span="1.5">
        <el-button type="success" plain icon="Goods" :disabled="single" @click="handleReceive()" v-hasPermi="['pu:order:receive']">
          收货联动
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Warning" @click="handleDiffReport()" v-hasPermi="['pu:order:list']"> 差异报表 </el-button>
      </el-col>
      <el-col :span="1.5"
        ><el-button type="primary" plain :disabled="single || mutationReadPending" @click="handleCertified()" v-hasPermi="['pu:order:query']"
          >认证报价／生命周期</el-button
        ></el-col
      >
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="orderList"
      @selection-change="handleSelectionChange"
      row-key="orderId"
      :expand-row-keys="expandedRows"
      @expand-change="handleExpandChange"
      show-overflow-tooltip
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column type="expand">
        <template #default="props">
          <el-table :data="orderLinesMap[props.row.orderId] || []" border size="small" style="margin: 8px 20px" show-overflow-tooltip>
            <el-table-column label="物料编码" prop="materialCode" width="120" />
            <el-table-column label="物料名称" prop="materialName" min-width="150" />
            <el-table-column label="规格型号" prop="materialSpec" width="120" />
            <el-table-column label="订单数量" prop="orderQuantity" width="100" />
            <el-table-column label="已收数量" prop="receivedQuantity" width="100" />
            <!-- ERP-PU-05：后端 returnedQuantity 此前前端全仓 0 命中，退货后订单行口径失真 -->
            <el-table-column label="已退货数量" prop="returnedQuantity" width="110">
              <template #default="scope">{{ scope.row.returnedQuantity ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="净采购量" width="110">
              <template #default="scope">{{ netOrderQuantity(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="收货状态" prop="receiveStatus" width="120">
              <template #default="scope">
                <el-tag :type="getReceiveStatusType(scope.row.receiveStatus)" size="small">
                  {{ getReceiveStatusLabel(scope.row.receiveStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="收货进度" width="150">
              <template #default="scope">
                <el-progress :percentage="calcReceiveProgress(scope.row)" :color="getProgressColor(scope.row)" :stroke-width="10" />
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column label="订单号" prop="orderNo" min-width="140" />
      <el-table-column label="供应商" prop="vendorName" min-width="120" show-overflow-tooltip />
      <el-table-column label="订单日期" prop="orderDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="预计到货" prop="expectedArrivalDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expectedArrivalDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="含税金额" align="right" prop="totalAmount" min-width="100">
        <template #default="scope">{{ fmtAmount(scope.row.totalAmount) }}</template>
      </el-table-column>
      <!-- 运费：后端 PuPurchaseOrderVo.freightAmount 已返回，此前前端未展示（FE-ERP-03） -->
      <el-table-column label="运费" align="right" prop="freightAmount" min-width="90">
        <template #default="scope">{{ fmtAmount(scope.row.freightAmount) }}</template>
      </el-table-column>
      <el-table-column label="税额" align="right" prop="taxAmount" min-width="90">
        <template #default="scope">{{ fmtAmount(scope.row.taxAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="pu_purchase_order_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="报表" align="center" width="130" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope"
          ><BusinessReportButton
            dataset-code="ERP_PURCHASE_ORDER_DOCUMENT"
            parameter="orderId"
            :record-id="scope.row.orderId"
            :document="{ id: String(scope.row.orderId), code: scope.row.orderNo || String(scope.row.orderId), counterparty: scope.row.vendorName }"
        /></template>
      </el-table-column>
    </el-table>

    <CertifiedOrderWorkflowDialog ref="certifiedOrderRef" @changed="getList" />
    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="PurchaseOrder">
import BusinessReportButton from '@/components/report/BusinessReportButton.vue';
import {
  listPurchaseOrder,
  getPurchaseOrder,
  addPurchaseOrder,
  updatePurchaseOrder,
  delPurchaseOrder,
  receivePurchaseOrder,
  getDiffReport,
  listOrderLines,
  getCertifiedOrderContext,
  getCertifiedOrderDetail
} from '@/api/erp/pu/order';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { getToken } from '@/utils/auth';
import { createBusinessSession, normalizeBusinessId } from '@/utils/certified-business';
import type { CertifiedBusinessSession } from '@/utils/certified-business';
import CertifiedOrderWorkflowDialog from './CertifiedOrderWorkflowDialog.vue';

/**
 * 保存当前页面或测试的router状态。
 */
const router = useRouter();
/**
 * 保存当前页面或测试的certifiedOrderRef状态。
 */
const certifiedOrderRef = ref<InstanceType<typeof CertifiedOrderWorkflowDialog>>();
/**
 * 保存当前页面或测试的mutationReadPending状态。
 */
const mutationReadPending = ref(false);
/**
 * 保存当前页面或测试的{ proxy }状态。
 */
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
/**
 * 保存当前页面或测试的{ pu_purchase_order_status }状态。
 */
const { pu_purchase_order_status } = toRefs<any>(proxy.useDict('pu_purchase_order_status'));

// 订单明细行（展开行按需加载）
/**
 * 保存当前页面或测试的orderLinesMap状态。
 */
const orderLinesMap = ref<Record<number, any[]>>({});
// el-table expand-row-keys 要求 string[]（与 row-key 字符串匹配）
/**
 * 保存当前页面或测试的expandedRows状态。
 */
const expandedRows = ref<string[]>([]);

// 查询参数
/**
 * 保存当前页面或测试的queryParams状态。
 */
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  orderNo: null,
  vendorName: null,
  status: null
});

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
/**
 * 保存当前页面或测试的{
  loading,
  list: orderList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleExport,
  handleSelectionChange
}状态。
 */
const {
  loading,
  list: orderList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listPurchaseOrder, get: getPurchaseOrder, add: addPurchaseOrder, update: updatePurchaseOrder, del: delPurchaseOrder },
  idField: 'orderId',
  entityName: '采购订单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/pu/order/export',
  exportFileName: 'pu_order'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  proxy.$modal.msgWarning('新增订单须由认证请购来源转单，不能用普通新增表单补来源。');
}

/** 修改按钮操作 */
/** 专属入口只传精确身份，实际权限、详情和版本重新向服务端读取。 */
function handleCertified(row?: any) {
  const id = row?.orderId ?? (ids.value.length === 1 ? ids.value[0] : null);
  if (id == null) {
    proxy.$modal.msgWarning('请选择一张原订单。');
    return;
  }
  try {
    certifiedOrderRef.value?.open(normalizeBusinessId(id));
  } catch {
    proxy.$modal.msgWarning('原订单身份不完整，不猜测ID。');
  }
}
/**
 * 处理当前报表的readOrdinaryOrder契约或受控操作。
 * @param id 固定标识，按字符串传输以保留完整精度
 * @param session 当前契约的session配置或状态
 */
async function readOrdinaryOrder(id: string, session?: CertifiedBusinessSession) {
  const fixed = session ?? createBusinessSession(() => getToken());
  const actor = await getCertifiedOrderContext(fixed);
  const native = await getCertifiedOrderDetail(id, fixed);
  fixed.assertCurrent();
  if (normalizeBusinessId(native.order.orderId) !== id || native.order.tenantId !== actor.tenantId) throw new Error('原生订单归属未知');
  const h = native.order;
  const ordinary =
    native.genericEditAllowedHint === true &&
    Array.isArray(native.allocations) &&
    native.allocations.length === 0 &&
    h.originalCommandPresent === false &&
    h.version == null &&
    h.quoteId == null &&
    h.decisionRunId == null &&
    h.decisionActionId == null &&
    h.sourceRequestKey == null &&
    (h.requisitionId == null || h.requisitionSourceType === 'MANUAL');
  return { session: fixed, native, ordinary };
}
/**
 * 处理当前报表的handleUpdate契约或受控操作。
 * @param row 当前契约的row配置或状态
 */
async function handleUpdate(row?: any) {
  if (mutationReadPending.value) return;
  const value = row?.orderId ?? (ids.value.length === 1 ? ids.value[0] : null);
  if (value == null) {
    proxy.$modal.msgWarning('请选择一张原订单。');
    return;
  }
  mutationReadPending.value = true;
  try {
    const id = normalizeBusinessId(value);
    const read = await readOrdinaryOrder(id);
    read.session.assertCurrent();
    if (!read.ordinary) {
      handleCertified({ orderId: id });
      return;
    }
    router.push({ path: '/erp/pu/order/form', query: { id, action: 'edit' } });
  } catch {
    proxy.$modal.msgWarning('订单来源或查询身份未确认，不进入普通编辑。');
  } finally {
    mutationReadPending.value = false;
  }
}
/**
 * 处理当前报表的handleDelete契约或受控操作。
 * @param row 当前契约的row配置或状态
 */
async function handleDelete(row?: any) {
  if (mutationReadPending.value) return;
  const selected = (row?.orderId != null ? [row.orderId] : ids.value).map(normalizeBusinessId);
  if (!selected.length) return;
  mutationReadPending.value = true;
  try {
    const session = createBusinessSession(() => getToken());
    for (const id of selected) {
      if (!(await readOrdinaryOrder(id, session)).ordinary) throw new Error('认证或未知单不能旧删除');
    }
    await proxy.$modal.confirm('仅删除已核验的普通历史订单；认证或未知来源不允许通过旧入口删除。是否继续？');
    for (const id of selected) {
      if (!(await readOrdinaryOrder(id, session)).ordinary) throw new Error('来源变化');
    }
    session.assertCurrent();
    await delPurchaseOrder(selected, session);
    session.assertCurrent();
    getList();
  } catch {
    proxy.$modal.msgWarning('旧删除权限、原生来源或结果未确认，请只读核对。');
  } finally {
    mutationReadPending.value = false;
  }
}

/** 查看按钮操作 */
function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = orderList.value.find((item: any) => item.orderId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/pu/order/form', query: { id: row.orderId, action: 'view' } });
}

/** 加载订单明细行 */
const loadOrderLines = async (orderId: number) => {
  if (orderLinesMap.value[orderId]) return;
  const res = await listOrderLines(orderId);
  orderLinesMap.value[orderId] = res.data || [];
};

/** 展开行变化时按需加载明细 */
const handleExpandChange = (row: any, expandedRows: any[]) => {
  if (expandedRows.includes(row)) {
    loadOrderLines(row.orderId);
  }
};

/** 金额千分位展示（金额列统一右对齐 + 两位小数，见 FE-ERP-03/FE-ERP-09） */
const fmtAmount = (v: number | undefined | null): string =>
  Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * 保存当前页面或测试的getReceiveStatusType状态。
 */
const getReceiveStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' => {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    NOT_RECEIVED: 'info',
    PENDING: 'info',
    PARTIAL_RECEIVED: 'warning',
    FULL_RECEIVED: 'success',
    OVER_RECEIVED: 'danger'
  };
  return map[status] || 'info';
};

/**
 * 保存当前页面或测试的getReceiveStatusLabel状态。
 */
const getReceiveStatusLabel = (status: string): string => {
  const map: Record<string, string> = {
    NOT_RECEIVED: '未收货',
    PENDING: '待收货',
    PARTIAL_RECEIVED: '部分到货',
    FULL_RECEIVED: '全部到货',
    OVER_RECEIVED: '超收'
  };
  return map[status] || status || '未知';
};

/**
 * 保存当前页面或测试的calcReceiveProgress状态。
 */
const calcReceiveProgress = (row: any): number => {
  if (!row.orderQuantity || row.orderQuantity === 0) return 0;
  const received = row.receivedQuantity || 0;
  return Math.min(Math.round((received / row.orderQuantity) * 100), 100);
};

/** 净采购量 = 订单数量 - 已退货数量（ERP-PU-05；已退货数量由后端 returnedQuantity 返回） */
const netOrderQuantity = (row: any) => {
  const ordered = Number(row.orderQuantity || 0);
  const returned = Number(row.returnedQuantity || 0);
  return Math.max(ordered - returned, 0);
};

/**
 * 保存当前页面或测试的getProgressColor状态。
 */
const getProgressColor = (row: any): string => {
  const progress = calcReceiveProgress(row);
  if (progress >= 100) return '#67c23a';
  if (progress >= 50) return '#409eff';
  if (progress > 0) return '#e6a23c';
  return '#909399';
};

/**
 * 收货联动按钮操作
 */
async function handleReceive() {
  if (mutationReadPending.value || ids.value.length !== 1) return;
  mutationReadPending.value = true;
  try {
    const id = normalizeBusinessId(ids.value[0]);
    const read = await readOrdinaryOrder(id);
    if (!read.ordinary) throw new Error('认证订单必须通过认证到货入口，不可复制数量为到货');
    read.session.assertCurrent();
    await receivePurchaseOrder(id, read.session);
    read.session.assertCurrent();
    proxy.$modal.msgSuccess('普通历史订单收货联动结果已返回，请回查通知。');
    getList();
  } catch {
    proxy.$modal.msgWarning('当前订单不能使用旧收货联动；请使用认证到货工作台或只读核对。');
  } finally {
    mutationReadPending.value = false;
  }
}

/**
 * 差异报表按钮操作 - 查询收货差异
 */
function handleDiffReport() {
  getDiffReport().then((res: any) => {
    const diffData = res.data || [];
    if (diffData.length === 0) {
      proxy.$modal.msgSuccess('暂无收货差异记录');
    } else {
      // 构建差异报表内容
      let html = '<div style="max-height:400px;overflow-y:auto;">';
      html += '<table style="width:100%;border-collapse:collapse;">';
      html +=
        '<tr style="background:#f5f7fa;"><th style="padding:8px;border:1px solid #ebeef5;">订单行ID</th><th style="padding:8px;border:1px solid #ebeef5;">物料编码</th><th style="padding:8px;border:1px solid #ebeef5;">订单数量</th><th style="padding:8px;border:1px solid #ebeef5;">已收数量</th><th style="padding:8px;border:1px solid #ebeef5;">状态</th></tr>';
      diffData.forEach((item: any) => {
        const statusMap: Record<string, string> = {
          PARTIAL_RECEIVED: '<span style="color:#e6a23c;">部分到货</span>',
          OVER_RECEIVED: '<span style="color:#f56c6c;">超收</span>',
          SHORT_RECEIVED: '<span style="color:#909399;">短收</span>'
        };
        html += `<tr><td style="padding:8px;border:1px solid #ebeef5;">${item.lineId || '-'}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${item.materialCode || '-'}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${item.orderQuantity || 0}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${item.receivedQuantity || 0}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${statusMap[item.receiveStatus] || item.receiveStatus}</td></tr>`;
      });
      html += '</table></div>';
      proxy.$modal.alert(html, '收货差异报表', { dangerouslyUseHTMLString: true });
    }
  });
}

onActivated(() => {
  getList();
});
/** 当前勾选项的展示字段只按完整ID匹配，不改变报表参数。 */
const reportDocuments = computed(() =>
  ids.value.map((value) => {
    const record = orderList.value.find((entry) => String(entry.orderId) === String(value));
    return { id: String(value), code: record?.orderNo ? String(record.orderNo) : String(value), counterparty: record?.vendorName || undefined };
  })
);
</script>
