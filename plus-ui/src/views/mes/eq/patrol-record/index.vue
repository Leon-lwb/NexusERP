<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6"
            ><el-form-item label="结果" prop="result"
              ><el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 100%"
                ><el-option v-for="d in mes_patrol_result" :key="d.value" :label="d.label" :value="d.value" /></el-select></el-form-item
          ></el-col>
          <el-col :span="6" class="search-actions"
            ><el-form-item
              ><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button
              ><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item
            ></el-col
          >
        </el-row>
      </el-form>
      <el-row :gutter="10" class="mb8">
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="任务ID" prop="taskId" width="100" />
        <el-table-column label="设备编码" prop="equipmentCode" width="110" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
        <el-table-column label="检查项" prop="checkItem" width="120" />
        <el-table-column label="实际值" prop="actualValue" width="100" />
        <el-table-column label="结果" prop="result" align="center" width="80">
          <template #default="s"><dict-tag :options="mes_patrol_result" :value="s.row.result" /></template>
        </el-table-column>
        <el-table-column label="照片" prop="photoUrl" width="90" align="center">
          <template #default="s">
            <el-image
              v-if="s.row.photoUrl"
              :src="s.row.photoUrl"
              :preview-src-list="[s.row.photoUrl]"
              fit="cover"
              style="width: 60px; height: 60px"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="执行人" prop="executorName" width="90" />
        <el-table-column label="执行时间" prop="executeTime" width="160" />
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>
<script setup lang="ts" name="MesEqPatrolRecord">
import { listPatrolRecord } from '@/api/mes/eq/patrol';
import type { EqPatrolRecordVO, EqPatrolRecordQuery } from '@/api/mes/eq/patrol';
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_patrol_result } = toRefs<any>(proxy?.useDict('mes_patrol_result'));
const dataList = ref<EqPatrolRecordVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const queryParams = ref<EqPatrolRecordQuery>({ pageNum: 1, pageSize: 10, result: undefined });
const getList = async () => {
  loading.value = true;
  try {
    const r = await listPatrolRecord(queryParams.value);
    dataList.value = r.rows;
    total.value = r.total;
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
onMounted(() => getList());
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
