<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="价格表名称" prop="listName">
            <el-input v-model="queryParams.listName" placeholder="请输入价格表名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="价格表类型" prop="listType">
            <el-select v-model="queryParams.listType" placeholder="请选择价格表类型" clearable style="width: 100%">
              <el-option v-for="dict in so_price_list_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in so_price_list_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="生效日期">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['so:priceList:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['so:priceList:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['so:priceList:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['so:priceList:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="priceListList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="价格表名称" align="center" prop="listName" min-width="120" show-overflow-tooltip />
      <el-table-column label="价格表类型" align="center" prop="listType">
        <template #default="scope">
          <dict-tag :options="so_price_list_type" :value="scope.row.listType" />
        </template>
      </el-table-column>
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="生效日期" align="center" prop="effectiveDateFrom">
        <template #default="scope">
          <span>{{ parseTime(scope.row.effectiveDateFrom, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="失效日期" align="center" prop="effectiveDateTo">
        <template #default="scope">
          <span>{{ parseTime(scope.row.effectiveDateTo, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_price_list_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
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
import { listPriceList, getPriceList, addPriceList, updatePriceList, delPriceList } from '@/api/erp/so/price-list';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_price_list_type, so_price_list_status } = toRefs<any>(proxy.useDict('so_price_list_type', 'so_price_list_status'));

const dateRange = ref([]);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  listName: null,
  listType: null,
  clientId: null,
  status: null,
  effectiveDateFrom: null,
  effectiveDateTo: null
});

// 列表骨架（新增/修改为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: priceListList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange
} = useCrud({
  apis: { list: listPriceList, get: getPriceList, add: addPriceList, update: updatePriceList, del: delPriceList },
  idField: 'priceListId',
  entityName: '价格表',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/so/price-list/export',
  exportFileName: 'priceList',
  beforeList: (query: any) => {
    // 生效日期范围仅用于列表过滤：每次查询按当前 dateRange 重新计算，无范围时清理残留
    if (dateRange.value && dateRange.value.length === 2) {
      query.effectiveDateFrom = dateRange.value[0];
      query.effectiveDateTo = dateRange.value[1];
    } else {
      query.effectiveDateFrom = null;
      query.effectiveDateTo = null;
    }
  }
});

function resetQuery() {
  dateRange.value = [];
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleAdd() {
  router.push({ path: '/erp/so/price-list/form', query: { action: 'add' } });
}

function handleUpdate(row?: any) {
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

/** 导出按钮操作（保持原有口径：不携带生效日期范围过滤条件） */
function handleExport() {
  const params = { ...queryParams.value };
  delete params.effectiveDateFrom;
  delete params.effectiveDateTo;
  proxy.download('/erp/so/price-list/export', params, `priceList_${new Date().getTime()}.xlsx`);
}

onActivated(() => {
  getList();
});
</script>
