<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="出库单编号" prop="issueCode">
            <el-input v-model="queryParams.issueCode" placeholder="请输入出库单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="销售订单" prop="soNo">
            <el-input v-model="queryParams.soNo" placeholder="请输入销售订单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="仓库" prop="warehouseName">
            <el-input v-model="queryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="单据状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择单据状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_sales_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="出库日期" prop="issueDate">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['wm:salesIssue:add']"> 新增 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['wm:salesIssue:edit']"> 修改 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wm:salesIssue:remove']">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:salesIssue:export']"> 导出 </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="salesIssueList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" width="60" />
      <el-table-column label="出库单编号" align="center" prop="issueCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="出库单名称" align="center" prop="issueName" min-width="120" show-overflow-tooltip />
      <el-table-column label="销售订单编号" align="center" prop="soNo" min-width="120" show-overflow-tooltip />
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="仓库名称" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="出库日期" align="center" prop="issueDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.issueDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="出库数量" align="center" prop="totalQuantity" min-width="90" />
      <el-table-column label="出库金额" align="center" prop="totalAmount" min-width="100">
        <template #default="scope">
          <span>{{ formatAmount(scope.row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单据状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="wm_sales_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="300" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <BusinessReportButton dataset-code="ERP_DELIVERY_DOCUMENT" parameter="issueId" :record-id="scope.row.issueId" />
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:salesIssue:edit']"
          >
            修改
          </el-button>
          <el-button
            v-if="scope.row.status === 'APPROVED'"
            link
            type="success"
            icon="Check"
            @click="handleConfirm(scope.row)"
            v-hasPermi="['wm:salesIssue:confirm']"
          >
            确认
          </el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link
            type="primary"
            icon="Finished"
            @click="handleSubmit(scope.row)"
            v-hasPermi="['wm:salesIssue:edit']"
          >
            提交审批
          </el-button>
          <el-button
            v-if="scope.row.status === 'APPROVING' || scope.row.status === 'APPROVED'"
            link
            type="info"
            icon="View"
            @click="handleViewFlow(scope.row)"
            v-hasPermi="['wm:salesIssue:query']"
          >
            流程
          </el-button>
          <el-button
            v-if="scope.row.status === 'CONFIRMED'"
            link
            type="success"
            icon="CircleCheck"
            @click="handleComplete(scope.row)"
            v-hasPermi="['wm:salesIssue:complete']"
          >
            完成
          </el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['wm:salesIssue:remove']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="SalesIssue">
import BusinessReportButton from '@/components/report/BusinessReportButton.vue';
import {
  listSalesIssue,
  getSalesIssue,
  addSalesIssue,
  updateSalesIssue,
  delSalesIssue,
  confirmSalesIssue,
  completeSalesIssue,
  submitSalesIssueFlow,
  getSalesIssueProcId
} from '@/api/erp/wm/sales-issue';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_sales_status } = toRefs<any>(proxy.useDict('wm_sales_status'));

const dateRange = ref([]);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  issueCode: null,
  issueName: null,
  soNo: null,
  clientName: null,
  warehouseName: null,
  status: null,
  issueDate: null
});

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: salesIssueList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listSalesIssue, get: getSalesIssue, add: addSalesIssue, update: updateSalesIssue, del: delSalesIssue },
  idField: 'issueId',
  entityName: '销售出库',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/wm/sales-issue/export',
  exportFileName: 'sales-issue',
  beforeList: (query) => {
    proxy.addDateRange(query, dateRange.value);
  }
});

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = [];
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({
    path: '/erp/wm/sales-issue/form',
    query: { action: 'add' }
  });
}

/** 修改按钮操作 */
function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = salesIssueList.value.find((item: any) => item.issueId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({
    path: '/erp/wm/sales-issue/form',
    query: { id: row.issueId, action: 'edit' }
  });
}

/** 查看按钮操作 */
function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = salesIssueList.value.find((item: any) => item.issueId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({
    path: '/erp/wm/sales-issue/form',
    query: { id: row.issueId, action: 'view' }
  });
}

/** 提交审批 */
function handleSubmit(row: any) {
  proxy.$modal
    .confirm('确认提交审批出库单【' + row.issueCode + '】吗？')
    .then(() => {
      return submitSalesIssueFlow(row.issueId);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('提交审批成功');
    })
    .catch(() => {});
}

/** 查看流程 */
function handleViewFlow(row: any) {
  getSalesIssueProcId(row.issueId)
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

/** 确认按钮操作 */
function handleConfirm(row: any) {
  proxy.$modal
    .confirm('确认要将出库单【' + row.issueCode + '】状态变更为已确认吗？')
    .then(function () {
      return confirmSalesIssue(row.issueId);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('确认成功');
    })
    .catch(() => {});
}

/** 完成按钮操作 */
function handleComplete(row: any) {
  proxy.$modal
    .confirm('确认要完成出库单【' + row.issueCode + '】吗？完成后将扣减库存！')
    .then(function () {
      return completeSalesIssue(row.issueId);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('出库完成');
    })
    .catch(() => {});
}

/** 格式化金额 */
function formatAmount(amount: any) {
  if (!amount) return '0.00';
  return parseFloat(amount).toFixed(2);
}

onActivated(() => {
  getList();
});
</script>
