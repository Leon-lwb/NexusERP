<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="会计期间">
        <el-select v-model="periodId" placeholder="请选择期间" filterable style="width: 200px" @change="getList">
          <el-option v-for="p in periodList" :key="p.periodId" :label="p.periodName" :value="p.periodId" />
        </el-select>
      </el-form-item>
      <el-form-item label="包含零额">
        <el-switch v-model="includeZero" @change="getList" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getList">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="项目" align="left" prop="itemName" min-width="200">
        <template #default="scope">
          <span :style="{ fontWeight: scope.row.isSummary ? 700 : 400 }">{{ scope.row.itemName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="期末余额" align="right" prop="amount" min-width="140">
        <template #default="scope">
          <span :style="{ fontWeight: scope.row.isSummary ? 700 : 400 }">{{ fmt(scope.row.amount) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      v-if="assetTotal !== null"
      :title="balanceTip"
      :type="balanced ? 'success' : 'error'"
      :closable="false"
      show-icon
      style="margin-top: 12px"
    />
  </div>
</template>

<script setup lang="ts" name="FinReportBalance">
import { listPeriod } from '@/api/erp/fin/gl/period';
import { balanceSheet, ReportLineVO } from '@/api/erp/fin/report';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<ReportLineVO[]>([]);
const periodList = ref<any[]>([]);
const loading = ref(false);
const periodId = ref<number>();
const includeZero = ref(false);

const assetTotal = ref<number | null>(null);
const liabilityTotal = ref<number | null>(null);
const balanced = ref(true);

const balanceTip = computed(() => {
  if (assetTotal.value === null) return '';
  return balanced.value
    ? `勾稽平衡:资产总计 ${fmt(assetTotal.value)} = 负债及权益总计 ${fmt(liabilityTotal.value)}`
    : `勾稽不平衡:资产总计 ${fmt(assetTotal.value)} ≠ 负债及权益总计 ${fmt(liabilityTotal.value)}`;
});

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
  balanceSheet({ periodId: periodId.value, includeZero: includeZero.value })
    .then((response) => {
      dataList.value = response.data;
      const asset = dataList.value.find((r) => r.itemCode === 'BS_ASSET_TOTAL');
      const liability = dataList.value.find((r) => r.itemCode === 'BS_LIABILITY_EQUITY_TOTAL');
      assetTotal.value = asset ? Number(asset.amount || 0) : null;
      liabilityTotal.value = liability ? Number(liability.amount || 0) : null;
      balanced.value = assetTotal.value !== null && Math.abs(assetTotal.value - (liabilityTotal.value || 0)) < 0.005;
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  includeZero.value = false;
  getList();
}

onMounted(() => {
  loadPeriods();
});
</script>
