<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="销售订单编号" prop="soNo">
            <el-input v-model="queryParams.soNo" placeholder="请输入销售订单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="来源客户" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入来源客户" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="订单状态" prop="orderStatus">
            <el-select v-model="queryParams.orderStatus" placeholder="请选择订单状态" clearable style="width: 100%">
              <el-option v-for="dict in so_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="订单生成日期" prop="orderDate">
            <el-date-picker
              clearable
              v-model="queryParams.orderDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择订单生成日期"
              style="width: 100%"
            >
            </el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['so:orderheader:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single || !canEditSelected"
          @click="handleUpdate()"
          v-hasPermi="['so:orderheader:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple || !canDeleteSelected"
          @click="handleDelete()"
          v-hasPermi="['so:orderheader:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['so:orderheader:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['so:orderheader:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Check" :disabled="!canConfirm" @click="handleConfirm()" v-hasPermi="['so:orderheader:edit']"
          >确认</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Finished" :disabled="!canFulfill" @click="handleFulfill()" v-hasPermi="['so:orderheader:edit']"
          >履行</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Close" :disabled="!canClose" @click="handleCloseOrder()" v-hasPermi="['so:orderheader:edit']"
          >关闭</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="CircleClose" :disabled="!canCancel" @click="handleCancel()" v-hasPermi="['so:orderheader:edit']"
          >取消</el-button
        >
      </el-col>
      <el-col :span="1.5"><BusinessReportButton dataset-code="ERP_SALES_ORDER_DOCUMENT" parameter="orderId" :record-ids="ids" /></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table ref="orderTableRef" v-loading="loading" :data="orderheaderList" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" width="60" />
      <el-table-column label="销售订单编号" align="center" prop="soNo" min-width="140" />
      <el-table-column label="来源客户" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="销售代表" align="center" prop="salesRepName" min-width="100" />
      <el-table-column label="订单生成日期" align="center" prop="orderDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="承诺交货日" align="center" prop="promiseDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.promiseDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="含税总金额" align="center" prop="totalAmount" min-width="110" />
      <el-table-column label="货币" align="center" prop="currency" min-width="80">
        <template #default="scope">
          <dict-tag :options="so_currency_type" :value="scope.row.currency" />
        </template>
      </el-table-column>
      <el-table-column label="信用状态" align="center" prop="creditStatus" min-width="90">
        <template #default="scope">
          <dict-tag :options="so_credit_status" :value="scope.row.creditStatus" />
        </template>
      </el-table-column>
      <el-table-column label="订单状态" align="center" prop="orderStatus" min-width="90">
        <template #default="scope">
          <dict-tag :options="so_order_status" :value="scope.row.orderStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="440" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <BusinessReportButton dataset-code="ERP_SALES_ORDER_DOCUMENT" parameter="orderId" :record-id="scope.row.soId" />
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="warning" @click.stop="handleDeliveryDecision(scope.row)" v-hasPermi="['ai:decision:run']">交付风险分析</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="canEditRow(scope.row)"
            v-hasPermi="['so:orderheader:edit']"
            >修改</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="canEditRow(scope.row)"
            v-hasPermi="['so:orderheader:remove']"
            >删除</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleConfirm(scope.row)"
            v-if="canConfirmRow(scope.row)"
            v-hasPermi="['so:orderheader:edit']"
            >确认</el-button
          >
          <el-button
            link
            type="primary"
            icon="Finished"
            @click="handleFulfill(scope.row)"
            v-if="canFulfillRow(scope.row)"
            v-hasPermi="['so:orderheader:edit']"
            >履行</el-button
          >
          <el-button
            link
            type="info"
            icon="Close"
            @click="handleCloseOrder(scope.row)"
            v-if="canCloseRow(scope.row)"
            v-hasPermi="['so:orderheader:edit']"
            >关闭</el-button
          >
          <el-button
            link
            type="danger"
            icon="CircleClose"
            @click="handleCancel(scope.row)"
            v-if="canCancelRow(scope.row)"
            v-hasPermi="['so:orderheader:edit']"
            >取消</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Orderheader">
import BusinessReportButton from '@/components/report/BusinessReportButton.vue';
import {
  listOrderheader,
  getOrderheader,
  addOrderheader,
  updateOrderheader,
  delOrderheader,
  confirmOrder,
  fulfillOrder,
  closeOrder,
  cancelOrder
} from '@/api/erp/so/orderheader';
import { useRouter } from 'vue-router';
import { runDeliveryRisk } from '@/api/ai/decision';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
async function handleDeliveryDecision(row: { soId?: string | number }) {
  if (row.soId == null) return;
  try {
    const res = await runDeliveryRisk({ bizType: 'SALES_ORDER', bizId: String(row.soId), horizonDays: 30, triggerType: 'MANUAL' });
    await router.push({ path: '/ai/decision/detail', query: { runId: String(res.data.runId) } });
  } catch {
    proxy?.$modal.msgError('交付风险分析未能启动，请检查权限或稍后重试');
  }
}

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_credit_status, so_order_status, so_currency_type } = toRefs<any>(proxy.useDict('so_credit_status', 'so_order_status', 'so_currency_type'));

// 当前选中行（用于工具栏按审批状态控制 确认/履行/关闭/取消 是否可用）
const selectedRow = ref<any>(null);
// 当前选中的全部行（删除需校验每一行都可改）
const selectedRows = ref<any[]>([]);
// 表格实例：列表数据被替换后需要同步清空勾选，否则「旧选中行仍驱动按钮可用态」
const orderTableRef = ref<any>(null);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  soNo: null,
  clientName: null,
  orderDate: null,
  orderStatus: null
});

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: orderheaderList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange: crudSelectionChange
} = useCrud({
  apis: { list: listOrderheader, get: getOrderheader, add: addOrderheader, update: updateOrderheader, del: delOrderheader },
  idField: 'soId',
  entityName: '销售订单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: 'erp/so/orderheader/export',
  exportFileName: 'orderheader'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据（在通用逻辑之上记录当前选中行）
function handleSelectionChange(selection: any[]) {
  crudSelectionChange(selection);
  selectedRows.value = selection;
  selectedRow.value = selection.length === 1 ? selection[0] : null;
}

/**
 * 列表数据被整体替换（搜索 / 重置 / 翻页 / 删除后刷新）时清空勾选。
 * 原实现只在 框选变化事件 里记录，搜索或重置后 ids/selectedRow 仍指向旧行，
 * 导致工具栏「确认/履行/关闭/取消/修改/删除」按一张已不在列表里的单据判断可用态。
 */
watch(orderheaderList, () => {
  selectedRows.value = [];
  selectedRow.value = null;
  orderTableRef.value?.clearSelection();
});

// 订单状态常量
const STATUS_DRAFT = 'DRAFT';
const STATUS_APPROVED = 'APPROVED';
const STATUS_CONFIRMED = 'CONFIRMED';
const STATUS_CLOSED = 'CLOSED';
const STATUS_CANCELLED = 'CANCELLED';

/**
 * 单行是否允许操作（与后端 confirm/fulfill 状态校验一致）：
 * - 确认：草稿(DRAFT) 或 已批准(APPROVED)
 * - 履行：已确认(CONFIRMED)
 * - 关闭：未关闭且未取消
 * - 取消：未关闭且未取消
 */
function canConfirmRow(row: any) {
  return row && row.orderStatus === STATUS_APPROVED;
}
function canFulfillRow(row: any) {
  return row && row.orderStatus === STATUS_CONFIRMED;
}
function canCloseRow(row: any) {
  return row && row.orderStatus !== STATUS_CLOSED && row.orderStatus !== STATUS_CANCELLED;
}
function canCancelRow(row: any) {
  return row && row.orderStatus !== STATUS_CLOSED && row.orderStatus !== STATUS_CANCELLED;
}

// 工具栏按钮：未选中行时默认显示（不禁用）；选中行时按其审批状态决定是否禁用
const canConfirm = computed(() => {
  if (!selectedRow.value) return true;
  return canConfirmRow(selectedRow.value);
});
const canFulfill = computed(() => {
  if (!selectedRow.value) return true;
  return canFulfillRow(selectedRow.value);
});
const canClose = computed(() => {
  if (!selectedRow.value) return true;
  return canCloseRow(selectedRow.value);
});
const canCancel = computed(() => {
  if (!selectedRow.value) return true;
  return canCancelRow(selectedRow.value);
});

/**
 * 改单/删除门禁：仅草稿（DRAFT）可修改与删除。
 * 与后端 SoSalesOrderHeaderServiceImpl.updateByBo / deleteByIds 的状态门禁保持一致：
 * 已确认/已履行/已关闭/已取消的单据须走 取消 / 关闭 等状态流转，不能直接改单或删除，
 * 否则会绕过信用额度释放、库存预留释放与生产计划收尾。
 */
function canEditRow(row: any) {
  return !!row && row.orderStatus === STATUS_DRAFT;
}
const canEditSelected = computed(() => selectedRows.value.length === 1 && canEditRow(selectedRows.value[0]));
const canDeleteSelected = computed(() => selectedRows.value.length > 0 && selectedRows.value.every((row: any) => canEditRow(row)));

/** 修改按钮操作 */
function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = orderheaderList.value.find((item: any) => item.soId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  if (!canEditRow(row)) {
    proxy.$modal.msgWarning('只有草稿状态的销售订单才能修改');
    return;
  }
  const soId = row.soId;
  if (soId === undefined) {
    proxy.$modal.msgError('数据格式错误，缺少soId字段');
    return;
  }
  router.push({
    path: '/erp/so/orderheader/form',
    query: { id: soId, action: 'edit' }
  });
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({
    path: '/erp/so/orderheader/form',
    query: { action: 'add' }
  });
}

/** 查看按钮操作 */
function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = orderheaderList.value.find((item: any) => item.soId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  const soId = row.soId;
  router.push({
    path: '/erp/so/orderheader/form',
    query: { id: soId, action: 'view' }
  });
}

/** 解析当前操作的目标订单：优先取行参数，其次取当前选中行，都没有则提示并返回 null */
function resolveTargetSoId(row: any): any {
  if (row && row.soId != null) return row.soId;
  if (selectedRow.value && selectedRow.value.soId != null) return selectedRow.value.soId;
  proxy.$modal.msgWarning('请先选择一条销售订单');
  return null;
}

/** 确认订单 */
function handleConfirm(row?: any) {
  const soId = resolveTargetSoId(row);
  if (!soId) return;
  proxy.$modal
    .confirm('确认订单编号为"' + soId + '"的吗？')
    .then(() => {
      confirmOrder(soId).then(() => {
        proxy.$modal.msgSuccess('确认成功');
        getList();
      });
    })
    .catch(() => {});
}

/** 履行订单 */
function handleFulfill(row?: any) {
  const soId = resolveTargetSoId(row);
  if (!soId) return;
  proxy.$modal
    .confirm('确认履行订单编号为"' + soId + '"的吗？')
    .then(() => doFulfill(soId, false))
    .catch(() => {});
}

/** 执行履行：首次严格校验，未通过时提示是否强制履行 */
function doFulfill(soId: any, force: boolean) {
  fulfillOrder(soId, force)
    .then(() => {
      proxy.$modal.msgSuccess('履行成功');
      getList();
    })
    .catch((err: any) => {
      const msg = (err && err.message) || String(err);
      // 严格门禁未通过：提示是否强制履行（force=true 跳过发货数量校验）
      if (!force && msg.includes('强制履行')) {
        proxy.$modal
          .confirm(msg + '\n\n是否立即强制履行？')
          .then(() => doFulfill(soId, true))
          .catch(() => {});
      }
    });
}

/** 关闭订单 */
function handleCloseOrder(row?: any) {
  const soId = resolveTargetSoId(row);
  if (!soId) return;
  proxy.$modal
    .confirm('确认关闭订单编号为"' + soId + '"的吗？')
    .then(() => {
      closeOrder(soId).then(() => {
        proxy.$modal.msgSuccess('关闭成功');
        getList();
      });
    })
    .catch(() => {});
}

/** 取消订单 */
function handleCancel(row?: any) {
  const soId = resolveTargetSoId(row);
  if (!soId) return;
  proxy.$modal
    .prompt('请输入取消原因', '取消订单')
    .then(({ value }: any) => {
      cancelOrder(soId, value).then(() => {
        proxy.$modal.msgSuccess('取消成功');
        getList();
      });
    })
    .catch(() => {});
}

onActivated(() => {
  getList();
});
</script>
