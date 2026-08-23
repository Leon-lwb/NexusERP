<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="路线编码" prop="routingCode">
            <el-input v-model="queryParams.routingCode" placeholder="请输入工艺路线编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="路线名称" prop="routingName">
            <el-input v-model="queryParams.routingName" placeholder="请输入工艺路线名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否当前版本" prop="isCurrent">
            <el-select v-model="queryParams.isCurrent" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:routing:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['bd:routing:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:routing:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:routing:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="routingList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="路线编码" align="center" prop="routingCode" />
      <el-table-column label="路线名称" align="center" prop="routingName" min-width="120" show-overflow-tooltip />
      <el-table-column label="适用物料" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="版本" align="center" prop="routingVersion" />
      <el-table-column label="总工序数" align="center" prop="totalSteps" />
      <el-table-column label="总工时(h)" align="center" prop="totalHours" />
      <el-table-column label="生效日期" align="center" prop="effectiveDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.effectiveDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="失效日期" align="center" prop="expiryDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expiryDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="当前版本" align="center" prop="isCurrent">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:routing:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:routing:remove']">删除</el-button>
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleActivateVersion(scope.row)"
            v-if="scope.row.isCurrent != 1"
            v-hasPermi="['bd:routing:edit']"
            >激活</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="Routing">
import { listRouting, getRouting, delRouting, activateRoutingVersion } from '@/api/erp/bd/routing';
import { RoutingVO } from '@/api/erp/bd/routing/types';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_true_or_false, sys_core_status } = toRefs<any>(proxy.useDict('sys_true_or_false', 'sys_core_status'));

const routingList = ref<RoutingVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  routingCode: undefined,
  routingName: undefined,
  isCurrent: undefined,
  status: undefined
});

/** 查询工艺路线列表 */
function getList() {
  loading.value = true;
  listRouting(queryParams.value).then((response: any) => {
    routingList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.routingId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  router.push({ path: '/erp/bd/routing/form' });
}

function handleUpdate(row?: any) {
  const routingId = row?.routingId || ids.value[0];
  router.push({ path: '/erp/bd/routing/form', query: { id: routingId, action: 'edit' } });
}

function handleView(row: any) {
  router.push({ path: '/erp/bd/routing/form', query: { id: row.routingId, action: 'view' } });
}

function handleDelete(row?: any) {
  const routingIds = row?.routingId || ids.value;
  proxy.$modal
    .confirm('是否确认删除工艺路线编号为"' + routingIds + '"的数据项？')
    .then(() => {
      return delRouting(routingIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleActivateVersion(row: any) {
  proxy.$modal
    .confirm('确认激活该工艺路线版本为当前版本？')
    .then(() => {
      return activateRoutingVersion(row.routingId);
    })
    .then(() => {
      proxy.$modal.msgSuccess('激活成功');
      getList();
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download('erp/bd/routing/export', { ...queryParams.value }, `routing_${new Date().getTime()}.xlsx`);
}

onActivated(() => {
  getList();
});
</script>
