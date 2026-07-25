<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="是否OOC" prop="isOoc">
              <el-select v-model="queryParams.isOoc" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="dict in mes_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" :offset="12" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="样本组号" prop="sampleGroup" align="right" />
        <el-table-column label="值" prop="value" align="right" />
        <el-table-column label="是否OOC" prop="isOoc" align="center">
          <template #default="scope">
            <dict-tag :options="mes_yes_no" :value="scope.row.isOoc" />
          </template>
        </el-table-column>
        <el-table-column label="违规规则" prop="oocRule" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.oocRule || '-' }}</template>
        </el-table-column>
        <el-table-column label="采集时间" prop="collectTime">
          <template #default="scope">{{ scope.row.collectTime || '-' }}</template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesSpcDataPoint">
import { listDataPoint } from '@/api/mes/spc/data-point';
import type { SpcDataPointVO } from '@/api/mes/spc/control-chart';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_yes_no } = toRefs<any>(proxy?.useDict('mes_yes_no'));

const dataList = ref<SpcDataPointVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<{ pageNum: number; pageSize: number; isOoc?: string }>({
  pageNum: 1,
  pageSize: 10,
  isOoc: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listDataPoint(queryParams.value);
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
