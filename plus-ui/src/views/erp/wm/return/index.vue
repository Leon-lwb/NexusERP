<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="退料单号" prop="returnNo">
            <el-input v-model="queryParams.returnNo" placeholder="请输入退料单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="退料类型" prop="returnType">
            <el-select v-model="queryParams.returnType" placeholder="请选择退料类型" clearable style="width: 100%">
              <el-option v-for="(label, value) in RETURN_TYPE_LABELS" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="(label, value) in RETURN_STATUS_LABELS" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:materialReturn:add']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="returnList" show-overflow-tooltip>
        <el-table-column label="退料单号" align="center" prop="returnNo" min-width="160" />
        <el-table-column label="退料类型" align="center" prop="returnType" width="100">
          <template #default="scope">
            <el-tag :type="RETURN_TYPE_TAG[scope.row.returnType] ?? 'info'">{{
              RETURN_TYPE_LABELS[scope.row.returnType] || scope.row.returnType
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" />
        <el-table-column label="来源单号" align="center" prop="sourceDoc" min-width="140" />
        <el-table-column label="状态" align="center" prop="status" width="100">
          <template #default="scope">
            <el-tag :type="RETURN_STATUS_TAG[scope.row.status] ?? 'info'">{{ RETURN_STATUS_LABELS[scope.row.status] || scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退料日期" align="center" prop="returnDate" width="110" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['wm:materialReturn:query']">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MaterialReturn">
import { listReturn } from '@/api/erp/wm/return';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { RETURN_STATUS_LABELS, RETURN_STATUS_TAG, RETURN_TYPE_LABELS, RETURN_TYPE_TAG } from './constants';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  returnNo: null,
  returnType: null,
  status: null
});

const {
  loading,
  list: returnList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud({
  apis: { list: listReturn },
  idField: 'returnId',
  entityName: '退料单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 (表单页) */
function handleAdd() {
  router.push({ path: '/erp/wm/return/form', query: { action: 'add' } });
}

/** 查看按钮操作 (表单页只读) */
function handleView(row: any) {
  router.push({ path: '/erp/wm/return/form', query: { id: row.returnId, action: 'view' } });
}
</script>
