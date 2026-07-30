<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="会计期间">
        <el-select v-model="periodId" placeholder="请选择期间" filterable style="width: 200px" @change="getList">
          <el-option v-for="p in periodList" :key="p.periodId" :label="p.periodName" :value="p.periodId" />
        </el-select>
      </el-form-item>
      <el-form-item label="科目类型">
        <el-select v-model="accountType" placeholder="全部" clearable style="width: 160px" @change="getList">
          <el-option v-for="dict in gl_account_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getList">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="dataList" show-summary :summary-method="getSummaries" border show-overflow-tooltip>
      <el-table-column label="科目编码" align="center" prop="accountCode" width="120" />
      <el-table-column label="科目名称" align="left" prop="accountName" min-width="160" />
      <el-table-column label="类型" align="center" prop="accountType" width="80">
        <template #default="scope">
          <dict-tag :options="gl_account_type" :value="scope.row.accountType" />
        </template>
      </el-table-column>
      <el-table-column label="期初借方" align="right" prop="openingDebit" width="120" />
      <el-table-column label="期初贷方" align="right" prop="openingCredit" width="120" />
      <el-table-column label="本期借方" align="right" prop="periodDebit" width="120" />
      <el-table-column label="本期贷方" align="right" prop="periodCredit" width="120" />
      <el-table-column label="期末借方" align="right" prop="closingDebit" width="120" />
      <el-table-column label="期末贷方" align="right" prop="closingCredit" width="120" />
    </el-table>

    <el-alert
      v-if="dataList.length > 0"
      :title="balanceTip"
      :type="balanced ? 'success' : 'error'"
      :closable="false"
      show-icon
      style="margin-top: 12px"
    />
  </div>
</template>

<script setup lang="ts" name="GlTrialBalance">
import { listPeriod } from '@/api/erp/fin/gl/period';
import { trialBalance, TrialBalanceVO } from '@/api/erp/fin/gl/report';

const { proxy } = getCurrentInstance() as any;
const { gl_account_type } = toRefs<any>(proxy.useDict('gl_account_type'));

const dataList = ref<TrialBalanceVO[]>([]);
const periodList = ref<any[]>([]);
const loading = ref(false);
const periodId = ref<number>();
const accountType = ref<string>('');
const balanced = ref(true);

const balanceTip = computed(() => (balanced.value ? '试算平衡:本期借方合计 = 贷方合计' : '试算不平衡:本期借方合计 ≠ 贷方合计,请检查!'));

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
  trialBalance({ periodId: periodId.value, accountType: accountType.value || undefined })
    .then((response) => {
      dataList.value = response.data;
      const sumDr = dataList.value.reduce((s, r) => s + Number(r.periodDebit || 0), 0);
      const sumCr = dataList.value.reduce((s, r) => s + Number(r.periodCredit || 0), 0);
      balanced.value = Math.abs(sumDr - sumCr) < 0.005;
    })
    .finally(() => {
      loading.value = false;
    });
}

function getSummaries(param: { columns: any[]; data: TrialBalanceVO[] }) {
  const { columns, data } = param;
  const sums: (string | number)[] = [];
  columns.forEach((column, index) => {
    if (index === 0) {
      sums[index] = '合计';
      return;
    }
    const prop = column.property;
    if (!prop) {
      sums[index] = '';
      return;
    }
    const total = data.reduce((s, r) => s + Number((r as any)[prop] || 0), 0);
    sums[index] = total.toFixed(2);
  });
  return sums;
}

onMounted(() => {
  loadPeriods();
});
</script>
