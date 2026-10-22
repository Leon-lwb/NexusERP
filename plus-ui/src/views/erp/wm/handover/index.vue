<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="交接单号" prop="handoverNo">
            <el-input v-model="queryParams.handoverNo" placeholder="请输入交接单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工单号" prop="woNo">
            <el-input v-model="queryParams.woNo" placeholder="请输入工单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="(label, value) in HANDOVER_STATUS_LABELS" :key="value" :label="label" :value="value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:materialHandover:add']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="handoverList" show-overflow-tooltip>
        <el-table-column label="交接单号" align="center" prop="handoverNo" min-width="170" />
        <el-table-column label="工单号" align="center" prop="woNo" min-width="140" />
        <el-table-column label="仓库" align="center" prop="warehouseName" min-width="110" />
        <el-table-column label="状态" align="center" prop="status" width="100">
          <template #default="scope">
            <el-tag :type="HANDOVER_STATUS_TAG[scope.row.status] ?? 'info'">
              {{ HANDOVER_STATUS_LABELS[scope.row.status] || scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交接时间" align="center" prop="handoverTime" width="160" />
        <el-table-column label="签收人" align="center" prop="signName" width="100" />
        <el-table-column label="签收时间" align="center" prop="signTime" width="160" />
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['wm:materialHandover:query']">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MaterialHandover">
import { listHandover } from '@/api/erp/wm/handover';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useListRefresh } from '@/composables/useListRefresh';
import { HANDOVER_STATUS_LABELS, HANDOVER_STATUS_TAG } from './constants';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  handoverNo: null,
  woNo: null,
  status: null
});

const {
  loading,
  list: handoverList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud({
  apis: { list: listHandover },
  idField: 'handoverId',
  entityName: '交接单',
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
  router.push({ path: '/erp/wm/handover/form', query: { action: 'add' } });
}

/** 查看按钮操作 (表单页只读) */
function handleView(row: any) {
  router.push({ path: '/erp/wm/handover/form', query: { id: row.handoverId, action: 'view' } });
}
useListRefresh(getList);
</script>
