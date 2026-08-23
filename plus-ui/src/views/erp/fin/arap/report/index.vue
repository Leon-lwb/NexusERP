<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="方向" prop="direction">
            <el-select v-model="queryParams.direction" placeholder="全部" clearable style="width: 100%" @change="getList">
              <el-option v-for="d in fin_receipt_direction" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="往来ID" prop="partnerId">
            <el-input v-model="queryParams.partnerId" placeholder="客户/供应商ID(可空)" clearable @keyup.enter="handleQuery" />
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

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="类型" align="center" prop="itemType" width="110">
        <template #default="scope">{{ scope.row.itemType === 'INVOICE' ? '发票' : '收付款单' }}</template>
      </el-table-column>
      <el-table-column label="单号" align="center" prop="itemNo" width="180" />
      <el-table-column label="方向" align="center" prop="direction" width="90">
        <template #default="scope"><dict-tag :options="fin_receipt_direction" :value="scope.row.direction" /></template>
      </el-table-column>
      <el-table-column label="往来ID" align="center" prop="partnerId" width="140" />
      <el-table-column label="总额" align="right" prop="totalAmount" width="120" />
      <el-table-column label="已核销" align="right" prop="settledAmount" width="120" />
      <el-table-column label="未清余额" align="right" prop="openAmount" width="120" />
      <el-table-column label="日期" align="center" prop="itemDate" width="120" />
    </el-table>
  </div>
</template>

<script setup lang="ts" name="ArapReport">
import { listArapOpenItems } from '@/api/erp/fin/arap-report';
import { OpenItemVO } from '@/api/erp/fin/settlement/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { fin_receipt_direction } = toRefs<any>(proxy.useDict('fin_receipt_direction'));

const dataList = ref<OpenItemVO[]>([]);
const loading = ref(false);
const showSearch = ref(true);
const queryParams = reactive({ direction: 'AR', partnerId: undefined });

function getList() {
  loading.value = true;
  listArapOpenItems(queryParams.direction, queryParams.partnerId).then((res) => {
    dataList.value = res.data;
    loading.value = false;
  });
}
function handleQuery() {
  getList();
}
function resetQuery() {
  queryParams.direction = 'AR';
  queryParams.partnerId = undefined;
  getList();
}
onMounted(() => {
  getList();
});
</script>
