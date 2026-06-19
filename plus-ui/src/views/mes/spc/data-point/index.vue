<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="控制图ID" prop="chartId">
          <el-input v-model.number="queryParams.chartId" placeholder="控制图ID" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="是否OOC" prop="isOoc">
          <el-select v-model="queryParams.isOoc" placeholder="请选择" clearable style="width: 120px">
            <el-option label="是" value="1" />
            <el-option label="否" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="数据点ID" prop="dataId" width="120" align="right" />
        <el-table-column label="控制图ID" prop="chartId" width="120" align="right" />
        <el-table-column label="样本组号" prop="sampleGroup" width="100" align="right" />
        <el-table-column label="值" prop="value" width="120" align="right" />
        <el-table-column label="是否OOC" prop="isOoc" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isOoc === '1' ? 'danger' : 'success'">{{ scope.row.isOoc === '1' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="违规规则" prop="oocRule" width="200">
          <template #default="scope">{{ scope.row.oocRule || '-' }}</template>
        </el-table-column>
        <el-table-column label="采集时间" prop="collectTime" width="160">
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

const dataList = ref<SpcDataPointVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<{ pageNum: number; pageSize: number; chartId?: number; isOoc?: string }>({
  pageNum: 1,
  pageSize: 10,
  chartId: undefined,
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
