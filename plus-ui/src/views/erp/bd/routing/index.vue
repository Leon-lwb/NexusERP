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
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['bd:routing:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:routing:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:routing:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="路线名称" align="center" prop="routingName" min-width="120" show-overflow-tooltip />
      <el-table-column label="适用物料" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="版本" align="center" prop="routingVersion" min-width="80" />
      <el-table-column label="总工序数" align="center" prop="totalSteps" min-width="90" />
      <el-table-column label="总工时(h)" align="center" prop="totalHours" min-width="100" />
      <el-table-column label="生效日期" align="center" prop="effectiveDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.effectiveDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="失效日期" align="center" prop="expiryDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expiryDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="当前版本" align="center" prop="isCurrent" min-width="90">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" min-width="80">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280" fixed="right" :show-overflow-tooltip="false">
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
import { listRouting, getRouting, addRouting, updateRouting, delRouting, activateRoutingVersion } from '@/api/erp/bd/routing';
import type { RoutingQuery } from '@/api/erp/bd/routing/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useListRefresh } from '@/composables/useListRefresh';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_true_or_false, sys_core_status } = toRefs<any>(proxy.useDict('sys_true_or_false', 'sys_core_status'));

// 本页新增/修改/查看跳转独立表单页（/erp/bd/routing/form，含工序步骤维护），
// 页面本身无弹窗表单；formRef/initForm 仅为满足 useCrud 签名，运行时不会被调用
const routingRef = ref<FormInstance>();

const queryParams = ref<RoutingQuery>({
  pageNum: 1,
  pageSize: 10,
  routingCode: undefined,
  routingName: undefined,
  isCurrent: undefined,
  status: undefined
});

const { loading, list, total, ids, single, multiple, showSearch, getList, handleQuery, handleDelete, handleExport, handleSelectionChange } = useCrud({
  apis: { list: listRouting, get: getRouting, add: addRouting, update: updateRouting, del: delRouting },
  idField: 'routingId',
  entityName: '工艺路线',
  formRef: routingRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: 'erp/bd/routing/export',
  exportFileName: 'routing'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作：跳转工艺路线表单页 */
function handleAdd() {
  router.push({ path: '/erp/bd/routing/form' });
}

/** 修改按钮操作：跳转工艺路线表单页 */
function handleUpdate(row?: any) {
  const routingId = row?.routingId || ids.value[0];
  router.push({ path: '/erp/bd/routing/form', query: { id: routingId, action: 'edit' } });
}

/** 查看按钮操作：跳转工艺路线表单页 */
function handleView(row: any) {
  router.push({ path: '/erp/bd/routing/form', query: { id: row.routingId, action: 'view' } });
}

/** 激活工艺路线版本 */
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

useListRefresh(getList);
</script>
