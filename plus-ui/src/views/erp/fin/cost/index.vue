<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="事务类型" prop="txType">
            <el-select v-model="queryParams.txType" placeholder="全部" clearable style="width: 100%" @change="getList()">
              <el-option label="采购入库" value="RECEIVE_IN" />
              <el-option label="生产领料" value="PRODUCTION_ISSUE" />
              <el-option label="销售出库" value="SALES_OUT" />
              <el-option label="杂项入库" value="MISC_IN" />
              <el-option label="杂项出库" value="MISC_OUT" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">查询</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Money" :loading="provisioning" @click="doProvision()">期间暂估</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-alert
      title="库存流水成本过账:采购入库借存货/贷应付暂估,出库(领料/销售/杂项)借成本/贷存货。已过账流水不可重复过账(幂等)。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />

    <el-alert
      title="库存流水为来源业务单据产生的只读过账记录。数量、批次或库存差异应回到来源单据按业务流程更正；本页成本过账与期间暂估仅处理对应财务凭证。"
      type="info"
      :closable="false"
      show-icon
      class="mb8"
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
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="110" fixed="right" :show-overflow-tooltip="false">
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
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const provisioning = ref(false);

// 库存流水仅查询，财务过账和期间暂估使用各自原业务接口。
const formRef = ref<FormInstance>();

const queryParams = ref({ pageNum: 1, pageSize: 10, txType: '' });

const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud({
  apis: { list: listStockTx },
  idField: 'txId',
  entityName: '成本流水',
  formRef,
  queryParams,
  initForm: () => ({})
});

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
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

getList();
</script>
