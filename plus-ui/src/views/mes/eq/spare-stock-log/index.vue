<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="备件编码" prop="spareCode">
              <el-input v-model="queryParams.spareCode" placeholder="备件编码(精确)" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="流水类型" prop="transactionType">
              <el-select v-model="queryParams.transactionType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="d in mes_spare_txn_type" :key="d.value" :label="d.label" :value="d.value" />
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
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="流水ID" prop="logId" width="120" />
        <el-table-column label="备件编码" prop="spareCode" width="120" />
        <el-table-column label="备件名称" prop="spareName" min-width="120" show-overflow-tooltip />
        <el-table-column label="类型" prop="transactionType" align="center">
          <template #default="scope">
            <dict-tag :options="mes_spare_txn_type" :value="scope.row.transactionType" />
          </template>
        </el-table-column>
        <el-table-column label="数量" prop="quantity" align="right" width="100" />
        <el-table-column label="变动前" prop="beforeQty" align="right" width="100" />
        <el-table-column label="变动后" prop="afterQty" align="right" width="100" />
        <el-table-column label="来源" prop="refType" align="center" width="90">
          <template #default="scope">{{ scope.row.refType || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作人" prop="operatorName" width="100">
          <template #default="scope">{{ scope.row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作时间" prop="operateTime" width="160" />
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.remark || '-' }}</template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesEqSpareStockLog">
import { listStockLog } from '@/api/mes/eq/spare-part';
import type { EqSpareStockLogVO } from '@/api/mes/eq/spare-part';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_spare_txn_type } = toRefs<any>(proxy?.useDict('mes_spare_txn_type'));

const dataList = ref<EqSpareStockLogVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  spareId: undefined as number | undefined,
  transactionType: undefined as string | undefined,
  refType: undefined as string | undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listStockLog(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

onMounted(() => {
  getList();
});
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
