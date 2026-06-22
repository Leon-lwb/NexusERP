<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="价格表名称" prop="listName">
        <el-input v-model="queryParams.listName" placeholder="请输入价格表名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="价格表类型" prop="listType">
        <el-select v-model="queryParams.listType" placeholder="请选择价格表类型" clearable>
          <el-option v-for="dict in so_price_list_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in so_price_list_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['so:priceList:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['so:priceList:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['so:priceList:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['so:priceList:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="priceListList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="价格表名称" align="center" prop="listName" />
      <el-table-column label="价格表类型" align="center" prop="listType">
        <template #default="scope">
          <dict-tag :options="so_price_list_type" :value="scope.row.listType" />
        </template>
      </el-table-column>
      <el-table-column label="客户名称" align="center" prop="clientName" />
      <el-table-column label="生效日期" align="center" prop="effectiveDateFrom" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.effectiveDateFrom, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="失效日期" align="center" prop="effectiveDateTo" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.effectiveDateTo, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_price_list_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:priceList:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:priceList:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="PriceList">
import { listPriceList, delPriceList } from '@/api/erp/so/price-list';
import { useRouter } from 'vue-router';

const router = useRouter();
const { proxy } = getCurrentInstance() as any;
const { so_price_list_type, so_price_list_status } = toRefs<any>(proxy.useDict('so_price_list_type', 'so_price_list_status'));

const priceListList = ref([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const dateRange = ref([]);

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    listName: null,
    listType: null,
    clientId: null,
    status: null,
    effectiveDateFrom: null,
    effectiveDateTo: null
  }
});

const { queryParams } = toRefs(data);

function getList() {
  loading.value = true;
  const params = { ...queryParams.value };
  if (dateRange.value && dateRange.value.length === 2) {
    params.effectiveDateFrom = dateRange.value[0];
    params.effectiveDateTo = dateRange.value[1];
  }
  listPriceList(params).then((response: any) => {
    priceListList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  dateRange.value = [];
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.priceListId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  router.push({ path: '/erp/so/price-list/form', query: { action: 'add' } });
}

function handleUpdate(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = priceListList.value.find((item: any) => item.priceListId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/so/price-list/form', query: { id: row.priceListId, action: 'edit' } });
}

function handleDelete(row: any) {
  const _ids = row.priceListId || ids.value;
  proxy.$modal
    .confirm('是否确认删除价格表编号为"' + _ids + '"的数据项？')
    .then(function () {
      return delPriceList(_ids);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download('/erp/so/price-list/export', { ...queryParams.value }, `priceList_${new Date().getTime()}.xlsx`);
}

onActivated(() => {
  getList();
})
</script>
