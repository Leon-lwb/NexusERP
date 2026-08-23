<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="供应商名称" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="评估周期" prop="evalPeriod">
            <el-input v-model="queryParams.evalPeriod" placeholder="请输入评估周期" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="评估等级" prop="evalLevel">
            <el-select v-model="queryParams.evalLevel" placeholder="请选择评估等级" clearable style="width: 100%">
              <el-option v-for="dict in pu_vendor_eval_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['pu:vendorEvaluation:add']"> 新增 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['pu:vendorEvaluation:remove']">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Calculator" :disabled="single" @click="handleCalculate" v-hasPermi="['pu:vendorEvaluation:calculate']">
          计算评估
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Operation" @click="handleBatchCalculate" v-hasPermi="['pu:vendorEvaluation:batchCalculate']">
          批量计算
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="vendorEvaluationList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="供应商名称" prop="vendorName" min-width="120" show-overflow-tooltip />
      <el-table-column label="评估周期" prop="evalPeriod" />
      <el-table-column label="交货及时率" prop="deliveryRate">
        <template #default="scope">
          <span>{{ scope.row.deliveryRate }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="质量合格率" prop="qualityRate">
        <template #default="scope">
          <span>{{ scope.row.qualityRate }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="价格竞争力" prop="priceCompetitiveness">
        <template #default="scope">
          <span>{{ scope.row.priceCompetitiveness }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="综合评分" prop="totalScore">
        <template #default="scope">
          <el-tag :type="getScoreType(scope.row.totalScore)" effect="dark">
            {{ scope.row.totalScore }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 技术债:评估等级为硬编码 el-tag(已有 pu_vendor_eval_level 字典),待改 dict-tag -->
      <el-table-column label="评估等级" prop="evalLevel">
        <template #default="scope">
          <el-tag :type="getEvalLevelType(scope.row.evalLevel)" effect="dark">
            {{ scope.row.evalLevel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评估日期" prop="evalDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.evalDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="VendorEvaluation">
import { listVendorEvaluation, delVendorEvaluation, calculateVendorEvaluation, batchCalculateAll } from '@/api/erp/pu/vendor-evaluation';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { pu_vendor_eval_level } = toRefs<any>(proxy.useDict('pu_vendor_eval_level'));

const vendorEvaluationList = ref([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    vendorName: null,
    evalPeriod: null,
    evalLevel: null
  }
});
const { queryParams } = toRefs(data);

function getList() {
  loading.value = true;
  listVendorEvaluation(queryParams.value).then((res: any) => {
    vendorEvaluationList.value = res.rows;
    total.value = res.total;
    loading.value = false;
  });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((i: any) => i.evalId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  proxy.$router.push({ path: '/erp/pu/vendor-evaluation/form', query: { action: 'add' } });
}

function handleDelete() {
  proxy.$modal
    .confirm('是否确认删除供应商评估?')
    .then(() => delVendorEvaluation(ids.value))
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    });
}

function handleCalculate() {
  calculateVendorEvaluation(ids.value[0]).then(() => {
    proxy.$modal.msgSuccess('计算成功');
    getList();
  });
}

function handleBatchCalculate() {
  proxy.$modal
    .confirm('是否确认批量计算所有供应商评估?')
    .then(() => batchCalculateAll())
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('批量计算成功');
    });
}

function getScoreType(score: number): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (score >= 90) return 'success';
  if (score >= 80) return 'primary';
  if (score >= 60) return 'warning';
  return 'danger';
}

function getEvalLevelType(level: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    'A': 'success',
    'B': 'primary',
    'C': 'warning',
    'D': 'danger'
  };
  return typeMap[level] || 'info';
}

onActivated(() => {
  getList();
});
</script>
