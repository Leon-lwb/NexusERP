<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="校验状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择校验状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_recon_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Refresh" @click="handleReconcile()" v-hasPermi="['wm:reconciliation:edit']">执行校验</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wm:reconciliation:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:reconciliation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="reconciliationList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="180" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.materialCode || `物料ID：${scope.row.materialId}` }}</template>
      </el-table-column>
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.materialName || '主数据未找到' }}</template>
      </el-table-column>
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="单位" align="center" prop="baseUomName" min-width="80" />
      <el-table-column label="账面库存" align="center" prop="bookQty" min-width="90" />
      <el-table-column label="事务汇总" align="center" prop="txBalance" min-width="90" />
      <el-table-column label="差异数量" align="center" prop="diffQty" min-width="90">
        <template #default="scope">
          <span :style="{ color: scope.row.status === 'DIFF' ? 'var(--el-color-danger)' : 'var(--el-color-success)', fontWeight: 'bold' }">
            {{ scope.row.diffQty }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="校验状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="wm_recon_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="校验时间" align="center" prop="reconTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-tooltip content="删除" placement="top">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:reconciliation:remove']" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup name="WmReconciliation" lang="ts">
import { listReconciliation, getReconciliation, delReconciliation, reconcile } from '@/api/erp/wm/reconciliation';
import { WmReconciliationQuery } from '@/api/erp/wm/reconciliation/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_recon_status } = toRefs<any>(proxy?.useDict('wm_recon_status'));

const queryParams = ref<WmReconciliationQuery>({ pageNum: 1, pageSize: 10 });

// 列表骨架（校验记录由“执行校验”生成，后端无新增/修改表单；
// add/update 仅为满足 useCrud 签名传入的占位，页面不会调用）
const {
  loading,
  list: reconciliationList,
  total,
  ids,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: {
    list: listReconciliation,
    get: getReconciliation,
    add: () => Promise.resolve(),
    update: () => Promise.resolve(),
    del: delReconciliation
  },
  idField: 'reconId',
  entityName: '库存校验记录',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: 'erp/wm/reconciliation/export',
  exportFileName: '库存校验'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

/** 执行校验 */
async function handleReconcile() {
  loading.value = true;
  await reconcile();
  proxy?.$modal.msgSuccess('校验完成');
  getList();
}

onMounted(() => {
  getList();
});
</script>
