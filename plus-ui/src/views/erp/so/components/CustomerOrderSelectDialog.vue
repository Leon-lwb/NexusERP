<template>
  <el-dialog title="选择客户订单" v-model="visible" width="900px" append-to-body destroy-on-close>
    <!-- 搜索表单 -->
    <el-form :inline="true" :model="queryParams" class="query-form">
      <el-form-item label="客户订单号">
        <el-input v-model="queryParams.coNo" placeholder="请输入客户订单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="客户名称">
        <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="订单状态">
        <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 160px">
          <el-option v-for="dict in so_customer_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 客户订单列表 -->
    <el-table v-loading="loading" :data="orderList" highlight-current-row @current-change="handleCurrentChange" max-height="400">
      <el-table-column label="客户订单号" prop="coNo" width="150" />
      <el-table-column label="客户名称" prop="clientName" min-width="180" />
      <el-table-column label="下单日期" prop="orderDate" width="120" />
      <el-table-column label="要求交货日" prop="requestedDate" width="120" />
      <el-table-column label="订单金额" prop="totalAmount" width="120" align="right" />
      <el-table-column label="货币" prop="currency" width="80" align="center" />
      <el-table-column label="订单状态" prop="status" width="100" align="center">
        <template #default="scope">
          <dict-tag :options="so_customer_order_status" :value="scope.row.status" />
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 操作按钮 -->
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :disabled="!selectedOrder" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="CustomerOrderSelectDialog">
import { listCustomerOrder } from '@/api/erp/so/customer-order';
import type { CustomerOrderVO } from '@/api/erp/so/customer-order/types';

const { proxy } = getCurrentInstance();
const { so_customer_order_status } = toRefs<any>(proxy.useDict('so_customer_order_status'));

const emit = defineEmits(['confirm']);

/** 对话框可见状态 */
const visible = ref(false);
/** 加载状态 */
const loading = ref(false);
/** 客户订单列表 */
const orderList = ref<CustomerOrderVO[]>([]);
/** 总数 */
const total = ref(0);
/** 当前选中的客户订单 */
const selectedOrder = ref<CustomerOrderVO | null>(null);

/** 查询参数 */
const queryParams = ref({
  coNo: '',
  clientName: '',
  status: '',
  pageNum: 1,
  pageSize: 10
});

/** 打开对话框 */
function open() {
  visible.value = true;
  selectedOrder.value = null;
  resetQuery();
}

/** 查询客户订单列表 */
function getList() {
  loading.value = true;
  listCustomerOrder(queryParams.value)
    .then((response: any) => {
      orderList.value = response.rows || [];
      total.value = response.total || 0;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置 */
function resetQuery() {
  queryParams.value = {
    coNo: '',
    clientName: '',
    status: '',
    pageNum: 1,
    pageSize: 10
  };
  getList();
}

/** 选中行变化 */
function handleCurrentChange(row: CustomerOrderVO | null) {
  selectedOrder.value = row;
}

/** 确认选择 */
function handleConfirm() {
  if (selectedOrder.value) {
    emit('confirm', selectedOrder.value);
    visible.value = false;
  }
}

/** 取消 */
function handleCancel() {
  visible.value = false;
  selectedOrder.value = null;
}

defineExpose({ open });
</script>
