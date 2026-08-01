<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="事务类型">
        <el-select v-model="queryParams.txType" placeholder="全部" clearable style="width: 160px" @change="getList">
          <el-option label="采购入库" value="RECEIVE_IN" />
          <el-option label="生产领料" value="PRODUCTION_ISSUE" />
          <el-option label="销售出库" value="SALES_OUT" />
          <el-option label="杂项入库" value="MISC_IN" />
          <el-option label="杂项出库" value="MISC_OUT" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getList">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item style="float: right">
        <el-button type="warning" icon="Money" :loading="provisioning" @click="doProvision">期间暂估</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      title="库存流水成本过账:采购入库借存货/贷应付暂估,出库(领料/销售/杂项)借成本/贷存货。已过账流水不可重复过账(幂等)。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="流水ID" align="center" prop="txId" width="180" />
      <el-table-column label="事务类型" align="center" prop="txType" width="120" />
      <el-table-column label="物料" align="left" min-width="150">
        <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
      </el-table-column>
      <el-table-column label="数量" align="right" prop="txQuantity" width="100" />
      <el-table-column label="单位成本" align="right" width="110">
        <template #default="scope">{{ fmt(scope.row.unitCost) }}</template>
      </el-table-column>
      <el-table-column label="事务金额" align="right" width="120">
        <template #default="scope">{{ fmt(scope.row.txAmount) }}</template>
      </el-table-column>
      <el-table-column label="来源单号" align="center" prop="sourceDocCode" min-width="140" />
      <el-table-column label="过账状态" align="center" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.posted === 1 ? 'success' : 'info'" size="small">{{ scope.row.posted === 1 ? '已过账' : '未过账' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="凭证号" align="center" prop="postedVoucherNo" min-width="170" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="110" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            v-if="scope.row.posted !== 1 && Number(scope.row.txAmount || 0) !== 0"
            link
            type="primary"
            icon="Money"
            @click="doPost(scope.row)"
            >过账</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="FinCostIndex">
import { costProvision, listStockTx, postCostTx } from '@/api/erp/fin/cost';

const { proxy } = getCurrentInstance() as any;

const dataList = ref<any[]>([]);
const loading = ref(false);
const total = ref(0);
const provisioning = ref(false);

const queryParams = ref({ pageNum: 1, pageSize: 10, txType: '' });

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function getList() {
  loading.value = true;
  listStockTx(queryParams.value)
    .then((response) => {
      dataList.value = response.rows;
      total.value = response.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  queryParams.value = { pageNum: 1, pageSize: 10, txType: '' };
  getList();
}

function doPost(row: any) {
  postCostTx(row.txId)
    .then(() => {
      proxy.$modal.msgSuccess('过账成功');
      getList();
    })
    .catch(() => {});
}

function doProvision() {
  const periodName = new Date().toISOString().slice(0, 7);
  proxy.$modal
    .confirm(`确认按期间 ${periodName} 执行采购暂估入账?`)
    .then(() => {
      provisioning.value = true;
      return costProvision(periodName);
    })
    .then(() => {
      proxy.$modal.msgSuccess('暂估完成');
      getList();
    })
    .finally(() => {
      provisioning.value = false;
    })
    .catch(() => {});
}

onMounted(() => {
  getList();
});
</script>
