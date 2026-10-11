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
          <el-form-item label="客户" prop="clientId">
            <el-input v-model="clientDisplay" placeholder="请选择客户" readonly>
              <template #append>
                <el-button icon="Search" @click="clientSelectRef?.open()" />
              </template>
              <template #suffix v-if="queryParams.clientId">
                <el-icon class="cell-clear" title="清除" @click="clearClient"><CircleClose /></el-icon>
              </template>
            </el-input>
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
          <el-form-item label="有效期">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="-"
              start-placeholder="生效日期"
              end-placeholder="失效日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
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
      <el-table-column label="价格表名称" align="center" prop="listName" min-width="140" show-overflow-tooltip />
      <el-table-column label="价格表类型" align="center" prop="listType" width="110">
        <template #default="scope">
          <dict-tag :options="so_price_list_type" :value="scope.row.listType" />
        </template>
      </el-table-column>
      <el-table-column label="客户" align="center" prop="clientName" min-width="140" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.clientName || '通用（不限客户）' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="币种" align="center" prop="currency" width="80" />
      <el-table-column label="优先级" align="center" prop="priority" width="80" />
      <el-table-column label="生效日期" align="center" prop="validFrom" width="110">
        <template #default="scope">
          <span>{{ scope.row.validFrom || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="失效日期" align="center" prop="validTo" width="110">
        <template #default="scope">
          <span>{{ scope.row.validTo || '长期有效' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag v-if="scope.row.status" :options="so_price_list_status" :value="scope.row.status" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:priceList:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:priceList:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 客户选择对话框 -->
    <ClientSelectDialog ref="clientSelectRef" @confirm="handleClientSelect" />
  </div>
</template>

<script setup lang="ts" name="PriceList">
import { listPriceList, getPriceList, addPriceList, updatePriceList, delPriceList } from '@/api/erp/so/price-list';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import ClientSelectDialog from '../components/ClientSelectDialog.vue';
import type { ClientVO } from '@/api/erp/bd/client/types';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_price_list_type, so_price_list_status } = toRefs<any>(proxy.useDict('so_price_list_type', 'so_price_list_status'));

const dateRange = ref([]);
const clientSelectRef = ref<InstanceType<typeof ClientSelectDialog> | null>(null);
const clientDisplay = ref('');

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  listName: null,
  listType: null,
  clientId: null,
  status: null,
  // 有效期区间：validFrom 为生效日期下界、validTo 为失效日期上界（与后端 buildQueryWrapper 一致）
  validFrom: null,
  validTo: null
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
    // 有效期范围仅用于列表过滤：每次查询按当前 dateRange 重新计算，无范围时清理残留
    if (dateRange.value && dateRange.value.length === 2) {
      query.validFrom = dateRange.value[0];
      query.validTo = dateRange.value[1];
    } else {
      query.validFrom = null;
      query.validTo = null;
    }
  }
});

/** 选中客户作为过滤条件 */
function handleClientSelect(client: ClientVO) {
  queryParams.value.clientId = client.clientId as any;
  clientDisplay.value = client.clientName;
  handleQuery();
}

/** 清除客户过滤条件 */
function clearClient() {
  queryParams.value.clientId = null;
  clientDisplay.value = '';
  handleQuery();
}

function resetQuery() {
  dateRange.value = [];
  clientDisplay.value = '';
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

/** 导出按钮操作（保持原有口径：不携带有效期范围过滤条件） */
function handleExport() {
  const params = { ...queryParams.value };
  delete params.validFrom;
  delete params.validTo;
  proxy.download('/erp/so/price-list/export', params, `priceList_${new Date().getTime()}.xlsx`);
}

onActivated(() => {
  getList();
});
</script>

<style scoped>
.cell-clear {
  cursor: pointer;
  color: var(--el-text-color-placeholder);
}
.cell-clear:hover {
  color: var(--el-color-danger);
}
</style>
