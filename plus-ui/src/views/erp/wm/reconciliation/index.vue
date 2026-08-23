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
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Refresh" @click="handleReconcile" v-hasPermi="['wm:reconciliation:edit']">执行校验</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wm:reconciliation:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:reconciliation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="reconciliationList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="单位" align="center" prop="baseUomName" />
      <el-table-column label="账面库存" align="center" prop="bookQty" />
      <el-table-column label="事务汇总" align="center" prop="txBalance" />
      <el-table-column label="差异数量" align="center" prop="diffQty">
        <template #default="scope">
          <span :style="{ color: scope.row.diffQty !== 0 ? '#F56C6C' : '#67C23A', fontWeight: 'bold' }">
            {{ scope.row.diffQty }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="校验状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_recon_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="校验时间" align="center" prop="reconTime" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
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
import { listReconciliation, delReconciliation, reconcile } from '@/api/erp/wm/reconciliation';
import { WmReconciliationVO, WmReconciliationQuery } from '@/api/erp/wm/reconciliation/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_recon_status } = toRefs<any>(proxy?.useDict('wm_recon_status'));

const reconciliationList = ref<WmReconciliationVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const queryRef = ref<ElFormInstance>();
const queryParams = ref<WmReconciliationQuery>({ pageNum: 1, pageSize: 10 });

const getList = async () => {
  loading.value = true;
  const res = await listReconciliation(queryParams.value);
  reconciliationList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  queryRef.value?.resetFields();
  handleQuery();
};
const handleSelectionChange = (selection: WmReconciliationVO[]) => {
  ids.value = selection.map((item) => item.reconId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const handleReconcile = async () => {
  loading.value = true;
  await reconcile();
  proxy?.$modal.msgSuccess('校验完成');
  getList();
};

const handleDelete = async (row?: WmReconciliationVO) => {
  const reconIds = row?.reconId || ids.value;
  await proxy?.$modal.confirm('是否确认删除编号为"' + reconIds + '"的数据项？');
  await delReconciliation(reconIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleExport = () => {
  proxy?.download('erp/wm/reconciliation/export', { ...queryParams.value }, `库存校验_${new Date().getTime()}.xlsx`);
};

onMounted(() => {
  getList();
});
</script>
