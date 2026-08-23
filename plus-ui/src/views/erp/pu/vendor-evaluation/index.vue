<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="供应商名称" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="评估周期" prop="evalPeriod">
            <el-input v-model="queryParams.evalPeriod" placeholder="请输入评估周期" clearable @keyup.enter="handleQuery()" />
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
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['pu:vendorEvaluation:add']"> 新增 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['pu:vendorEvaluation:remove']">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Calculator"
          :disabled="single"
          @click="handleCalculate()"
          v-hasPermi="['pu:vendorEvaluation:calculate']"
        >
          计算评估
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Operation" @click="handleBatchCalculate()" v-hasPermi="['pu:vendorEvaluation:batchCalculate']">
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
import {
  listVendorEvaluation,
  getVendorEvaluation,
  addVendorEvaluation,
  updateVendorEvaluation,
  delVendorEvaluation,
  calculateVendorEvaluation,
  batchCalculateAll
} from '@/api/erp/pu/vendor-evaluation';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { pu_vendor_eval_level } = toRefs<any>(proxy.useDict('pu_vendor_eval_level'));

// 查询参数
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  vendorName: null,
  evalPeriod: null,
  evalLevel: null
});

// 当前选中行（计算评估按供应商维度执行，需取选中行的 vendorId 而非评估ID）
const selectedRow = ref<any>(null);

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: vendorEvaluationList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange: crudSelectionChange
} = useCrud({
  apis: { list: listVendorEvaluation, get: getVendorEvaluation, add: addVendorEvaluation, update: updateVendorEvaluation, del: delVendorEvaluation },
  idField: 'evaluationId',
  entityName: '供应商评估',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

/** 多选框选中数据（在通用逻辑之上记录当前选中行） */
function handleSelectionChange(selection: any[]) {
  crudSelectionChange(selection);
  selectedRow.value = selection.length === 1 ? selection[0] : null;
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({ path: '/erp/pu/vendor-evaluation/form', query: { action: 'add' } });
}

/** 计算评估（后端按供应商维度重算，取选中行 vendorId） */
function handleCalculate() {
  const vendorId = selectedRow.value?.vendorId;
  if (vendorId == null) {
    proxy.$modal.msgWarning('请选择一条评估记录');
    return;
  }
  calculateVendorEvaluation(vendorId).then(() => {
    proxy.$modal.msgSuccess('计算成功');
    getList();
  });
}

/** 批量计算 */
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
    A: 'success',
    B: 'primary',
    C: 'warning',
    D: 'danger'
  };
  return typeMap[level] || 'info';
}

onActivated(() => {
  getList();
});
</script>
