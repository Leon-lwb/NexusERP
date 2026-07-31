<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="会计期间">
        <el-select v-model="periodId" placeholder="请选择期间" filterable style="width: 200px" @change="getList">
          <el-option v-for="p in periodList" :key="p.periodId" :label="p.periodName" :value="p.periodId" />
        </el-select>
      </el-form-item>
      <el-form-item label="口径">
        <el-radio-group v-model="yearToDate" @change="getList">
          <el-radio :value="false">本期</el-radio>
          <el-radio :value="true">本年累计</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getList">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="dataList" border show-overflow-tooltip>
      <el-table-column label="项目" align="left" prop="itemName" min-width="220">
        <template #default="scope">
          <span :style="{ fontWeight: scope.row.isSummary ? 700 : 400 }">{{ scope.row.itemName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="金额" align="right" min-width="140">
        <template #default="scope">
          <span :style="{ fontWeight: scope.row.isSummary ? 700 : 400 }">{{ fmt(scope.row.amount) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts" name="FinReportIncome">
import { listPeriod } from '@/api/erp/fin/gl/period';
import { incomeStatement, ReportLineVO } from '@/api/erp/fin/report';

const dataList = ref<ReportLineVO[]>([]);
const periodList = ref<any[]>([]);
const loading = ref(false);
const periodId = ref<number>();
const yearToDate = ref(false);

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function loadPeriods() {
  listPeriod({ pageNum: 1, pageSize: 200 } as any).then((response) => {
    periodList.value = response.rows;
    if (periodList.value.length > 0 && !periodId.value) {
      periodId.value = periodList.value[0].periodId;
      getList();
    }
  });
}

function getList() {
  if (!periodId.value) return;
  loading.value = true;
  incomeStatement({ periodId: periodId.value, yearToDate: yearToDate.value })
    .then((response) => {
      dataList.value = response.data;
    })
    .finally(() => {
      loading.value = false;
    });
}

onMounted(() => {
  loadPeriods();
});
</script>
