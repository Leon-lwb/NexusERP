<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="报价单编号" prop="quotationNo">
            <el-input v-model="queryParams.quotationNo" placeholder="请输入报价单编号" clearable @keyup.enter="handleQuery" />
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
              <el-option v-for="dict in so_quotation_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['so:quotation:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['so:quotation:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['so:quotation:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['so:quotation:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Check"
          :disabled="single || !canConfirmSelected"
          @click="handleConfirm()"
          v-hasPermi="['so:quotation:edit']"
          >确认</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Right"
          :disabled="single || !canConvertSelected"
          @click="handleConvert()"
          v-hasPermi="['so:quotation:edit']"
          >转换销售订单</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['so:quotation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="quotationList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="报价单编号" align="center" prop="quotationNo" min-width="140" />
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="销售员" align="center" prop="salesRepName" min-width="100" />
      <el-table-column label="报价日期" align="center" prop="quotationDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.quotationDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="总金额" align="center" prop="totalAmount" min-width="100" />
      <el-table-column label="状态" align="center" prop="status" min-width="80">
        <template #default="scope">
          <dict-tag :options="so_quotation_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:quotation:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:quotation:remove']">删除</el-button>
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleConfirm(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['so:quotation:edit']"
            >确认</el-button
          >
          <el-button
            link
            type="warning"
            icon="Right"
            @click="handleConvert(scope.row)"
            v-if="scope.row.status === 'CONFIRMED'"
            v-hasPermi="['so:quotation:edit']"
            >转换</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Quotation">
import {
  listQuotation,
  getQuotation,
  addQuotation,
  updateQuotation,
  delQuotation,
  confirmQuotation,
  convertQuotationToOrder
} from '@/api/erp/so/quotation';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_quotation_status } = toRefs<any>(proxy.useDict('so_quotation_status'));

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  quotationNo: null,
  clientName: null,
  status: null
});

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: quotationList,
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
  apis: { list: listQuotation, get: getQuotation, add: addQuotation, update: updateQuotation, del: delQuotation },
  idField: 'quotationId',
  entityName: '报价单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/so/quotation/export',
  exportFileName: 'quotation'
});

/** 当前选中行（工具栏按钮按状态门禁，与行内按钮保持一致） */
const selectedRow = ref<any>(null);

/** 多选框选中数据（在通用逻辑之上记录当前选中行） */
function handleSelectionChange(selection: any[]) {
  crudSelectionChange(selection);
  selectedRow.value = selection.length === 1 ? selection[0] : null;
}

// 与后端状态机保持一致：仅草稿可确认、仅已确认可转单（后端会拒绝，前端提前置灰避免误操作）
const canConfirmSelected = computed(() => selectedRow.value?.status === 'DRAFT');
const canConvertSelected = computed(() => selectedRow.value?.status === 'CONFIRMED');

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleAdd() {
  router.push({ path: '/erp/so/quotation/form', query: { action: 'add' } });
}

function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = quotationList.value.find((item: any) => item.quotationId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/so/quotation/form', query: { id: row.quotationId, action: 'edit' } });
}

function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = quotationList.value.find((item: any) => item.quotationId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/so/quotation/form', query: { id: row.quotationId, action: 'view' } });
}

function handleConfirm(row?: any) {
  const qId = row?.quotationId || ids.value[0];
  const qNo = row?.quotationNo || qId;
  if (!canConfirmSelected.value && !row) {
    proxy.$modal.msgWarning('只有草稿状态的报价单才能确认');
    return;
  }
  proxy.$modal
    .confirm('是否确认报价单"' + qNo + '"？')
    .then(() => {
      confirmQuotation(qId).then(() => {
        proxy.$modal.msgSuccess('确认成功');
        getList();
      });
    })
    .catch(() => {});
}

function handleConvert(row?: any) {
  const qId = row?.quotationId || ids.value[0];
  const qNo = row?.quotationNo || qId;
  if (!canConvertSelected.value && !row) {
    proxy.$modal.msgWarning('只有已确认状态的报价单才能转换为销售订单');
    return;
  }
  proxy.$modal
    .confirm('是否将报价单"' + qNo + '"转换为销售订单？')
    .then(() => {
      convertQuotationToOrder(qId).then(() => {
        proxy.$modal.msgSuccess('转换成功，已生成销售订单');
        getList();
      });
    })
    .catch(() => {});
}

onActivated(() => {
  getList();
});
</script>
