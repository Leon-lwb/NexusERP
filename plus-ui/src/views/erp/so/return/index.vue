<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="退货单编号" prop="returnNo">
            <el-input v-model="queryParams.returnNo" placeholder="请输入退货单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="销售订单号" prop="soNo">
            <el-input v-model="queryParams.soNo" placeholder="请输入销售订单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户名称" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in so_return_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['so:return:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single || !canEditSelected" @click="handleUpdate()" v-hasPermi="['so:return:edit']"
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
          v-hasPermi="['so:return:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['so:return:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['so:return:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table ref="returnTableRef" v-loading="loading" :data="returnList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="退货单编号" align="center" prop="returnNo" />
      <el-table-column label="销售订单号" align="center" prop="soNo" />
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="退货日期" align="center" prop="returnDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.returnDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="总金额" align="center" prop="totalAmount" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_return_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['so:return:edit']"
            >修改</el-button
          >
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['so:return:remove']"
            >删除</el-button
          >
          <el-button
            link
            type="primary"
            icon="Finished"
            @click="handleSubmit(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['so:return:edit']"
            >提交审批</el-button
          >
          <el-button
            link
            type="info"
            icon="View"
            @click="handleViewFlow(scope.row)"
            v-if="scope.row.status === 'APPROVING' || scope.row.status === 'CONFIRMED'"
            v-hasPermi="['so:return:query']"
            >流程</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Return">
import { listReturn, getReturn, addReturn, updateReturn, delReturn, submitReturnFlow, getReturnProcId } from '@/api/erp/so/return';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_return_status } = toRefs<any>(proxy.useDict('so_return_status'));

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  returnNo: null,
  soNo: null,
  clientName: null,
  status: null
});

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: returnList,
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
  apis: { list: listReturn, get: getReturn, add: addReturn, update: updateReturn, del: delReturn },
  idField: 'returnId',
  entityName: '退货单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/so/return/export',
  exportFileName: 'return'
});

/**
 * 当前选中行 + 可改判定。
 * 与后端 SoReturnServiceImpl 的状态门禁保持一致（T1.3）：改单与删除都仅限草稿，
 * 已确认/已收货的单据须走作废流程 —— 原工具栏只按 single/multiple 禁用，与行内按钮分叉。
 */
const selectedRow = ref<any>(null);
const selectedRows = ref<any[]>([]);
const returnTableRef = ref<any>(null);

function handleSelectionChange(selection: any[]) {
  crudSelectionChange(selection);
  selectedRows.value = selection;
  selectedRow.value = selection.length === 1 ? selection[0] : null;
}

function canEditRow(row: any) {
  return !!row && row.status === 'DRAFT';
}

const canEditSelected = computed(() => selectedRows.value.length === 1 && canEditRow(selectedRows.value[0]));
const canDeleteSelected = computed(() => selectedRows.value.length > 0 && selectedRows.value.every((row: any) => canEditRow(row)));

// 列表数据被整体替换（搜索/重置/翻页/删除刷新）时清空勾选，避免旧选中行驱动按钮可用态
watch(returnList, () => {
  selectedRows.value = [];
  selectedRow.value = null;
  returnTableRef.value?.clearSelection();
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleAdd() {
  router.push({ path: '/erp/so/return/form', query: { action: 'add' } });
}

function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = returnList.value.find((item: any) => item.returnId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/so/return/form', query: { id: row.returnId, action: 'edit' } });
}

function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = returnList.value.find((item: any) => item.returnId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/so/return/form', query: { id: row.returnId, action: 'view' } });
}

function handleSubmit(row: any) {
  const id = row.returnId || ids.value[0];
  if (!id) {
    proxy.$modal.msgWarning('请选择一条退货单');
    return;
  }
  proxy.$modal
    .confirm('确认提交审批退货单【' + row.returnNo + '】吗？')
    .then(() => {
      return submitReturnFlow(id);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('提交审批成功');
    })
    .catch(() => {});
}

function handleViewFlow(row: any) {
  const id = row.returnId || ids.value[0];
  if (!id) {
    proxy.$modal.msgWarning('请选择一条退货单');
    return;
  }
  getReturnProcId(id)
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

onActivated(() => {
  getList();
});
</script>
