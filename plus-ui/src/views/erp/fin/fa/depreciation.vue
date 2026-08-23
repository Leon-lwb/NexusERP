<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="会计期间">
        <el-select v-model="periodId" placeholder="请选择期间" filterable style="width: 200px" @change="onPeriodChange">
          <el-option v-for="p in periodList" :key="p.periodId" :label="p.periodName" :value="p.periodId" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="View" @click="loadPlan">计划预览</el-button>
        <el-button type="warning" icon="RefreshRight" :loading="running" @click="doRun">计提并过账</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      title="按期间批量计提折旧并自动过账 GL(借:费用科目 / 贷:累计折旧 1602)。同期间重复计提幂等,不生成第二张凭证。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="计提计划" name="plan">
        <el-table v-loading="loading" :data="planList" border size="small">
          <el-table-column label="资产编号" align="center" prop="assetNo" width="130" />
          <el-table-column label="资产名称" align="left" prop="assetName" min-width="160" />
          <el-table-column label="本期折旧额" align="right" width="130">
            <template #default="scope">{{ fmt(scope.row.depreciationAmount) }}</template>
          </el-table-column>
          <el-table-column label="计划状态" align="center" width="100">
            <template #default="scope">
              <el-tag type="info" size="small">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="planList.length > 0" style="margin-top: 12px; text-align: right">
          <el-tag type="warning">合计: {{ fmt(planTotal) }}</el-tag>
        </div>
      </el-tab-pane>
      <el-tab-pane label="折旧记录" name="history">
        <el-table v-loading="loading" :data="dataList" border size="small">
          <el-table-column label="期间" align="center" prop="periodName" width="100" />
          <el-table-column label="资产编号" align="center" prop="assetNo" width="130" />
          <el-table-column label="资产名称" align="left" prop="assetName" min-width="160" />
          <el-table-column label="本期折旧" align="right" width="120">
            <template #default="scope">{{ fmt(scope.row.depreciationAmount) }}</template>
          </el-table-column>
          <el-table-column label="累计折旧" align="right" width="120">
            <template #default="scope">{{ fmt(scope.row.cumulativeDepreciation) }}</template>
          </el-table-column>
          <el-table-column label="凭证号" align="center" prop="postedVoucherNo" min-width="170" />
          <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'DONE' ? 'success' : 'info'" size="small">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts" name="FinFaDepreciation">
import { depreciationPlan, listDepreciation, runDepreciation, FaDepreciationVO } from '@/api/erp/fin/fa';
import { listPeriod } from '@/api/erp/fin/gl/period';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const periodList = ref<any[]>([]);
const periodId = ref<number>();
const loading = ref(false);
const running = ref(false);
const activeTab = ref('plan');
const planList = ref<FaDepreciationVO[]>([]);
const dataList = ref<FaDepreciationVO[]>([]);
const total = ref(0);

const queryParams = ref({ pageNum: 1, pageSize: 10 });

const planTotal = computed(() => planList.value.reduce((s, x) => s + Number(x.depreciationAmount || 0), 0));

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function loadPeriods() {
  listPeriod({ pageNum: 1, pageSize: 200 } as any).then((response) => {
    periodList.value = response.rows;
    if (periodList.value.length > 0 && !periodId.value) {
      periodId.value = periodList.value[0].periodId;
      loadPlan();
    }
  });
}

function onPeriodChange() {
  loadPlan();
  if (activeTab.value === 'history') {
    getList();
  }
}

function onTabChange() {
  if (activeTab.value === 'history') {
    getList();
  }
}

function loadPlan() {
  if (!periodId.value) return;
  loading.value = true;
  depreciationPlan(periodId.value)
    .then((response) => {
      planList.value = response.data || [];
    })
    .finally(() => {
      loading.value = false;
    });
}

function getList() {
  if (!periodId.value) return;
  loading.value = true;
  listDepreciation({ ...queryParams.value, periodId: periodId.value })
    .then((response) => {
      dataList.value = response.rows;
      total.value = response.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function doRun() {
  if (!periodId.value) return;
  proxy.$modal
    .confirm('确认按所选期间计提折旧并过账?同期间重复执行将幂等跳过。')
    .then(() => {
      running.value = true;
      return runDepreciation(periodId.value);
    })
    .then(() => {
      proxy.$modal.msgSuccess('计提完成');
      loadPlan();
      getList();
    })
    .finally(() => {
      running.value = false;
    })
    .catch(() => {});
}

onMounted(() => {
  loadPeriods();
});
</script>
