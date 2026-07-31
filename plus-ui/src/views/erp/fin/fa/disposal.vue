<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="资产名称">
        <el-input v-model="queryParams.keyword" placeholder="请输入资产名称" clearable style="width: 180px" @keyup.enter="getList" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getList">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item style="float: right">
        <el-button type="primary" icon="Plus" @click="handleAdd">处置资产</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="dataList" border>
      <el-table-column label="资产编号" align="center" prop="assetNo" width="130" />
      <el-table-column label="资产名称" align="left" prop="assetName" min-width="160" />
      <el-table-column label="处置类型" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.disposalType === 'SALE' ? 'primary' : 'danger'" size="small">{{
            scope.row.disposalType === 'SALE' ? '出售' : '报废'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处置日期" align="center" prop="disposalDate" width="110" />
      <el-table-column label="处置收入" align="right" width="110">
        <template #default="scope">{{ fmt(scope.row.disposalPrice) }}</template>
      </el-table-column>
      <el-table-column label="处置时净值" align="right" width="110">
        <template #default="scope">{{ fmt(scope.row.netBookValue) }}</template>
      </el-table-column>
      <el-table-column label="处置损益" align="right" width="110">
        <template #default="scope">
          <span :style="{ color: Number(scope.row.gainLoss) >= 0 ? '#67c23a' : '#f56c6c' }">{{ fmt(scope.row.gainLoss) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="凭证号" align="center" prop="postedVoucherNo" min-width="170" />
      <el-table-column label="备注" align="left" prop="remark" min-width="120" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" title="处置资产" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="资产" prop="assetId">
          <el-select v-model="form.assetId" filterable placeholder="请选择使用中的资产" style="width: 100%">
            <el-option v-for="a in assetOptions" :key="a.assetId" :label="`${a.assetNo} ${a.assetName}(净值${fmt(a.netValue)})`" :value="a.assetId" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置类型" prop="disposalType">
          <el-radio-group v-model="form.disposalType">
            <el-radio value="SALE">出售</el-radio>
            <el-radio value="SCRAP">报废</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置日期" prop="disposalDate">
          <el-date-picker v-model="form.disposalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="处置收入" prop="disposalPrice">
          <el-input-number v-model="form.disposalPrice" :min="0" :precision="2" style="width: 100%" />
          <span class="el-form-item__content-hint">报废可填 0</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="处置原因等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FinFaDisposal">
import { addDisposal, listAsset, listDisposal, FaDisposalVO } from '@/api/erp/fin/fa';

const { proxy } = getCurrentInstance() as any;

const dataList = ref<FaDisposalVO[]>([]);
const assetOptions = ref<any[]>([]);
const loading = ref(false);
const total = ref(0);
const open = ref(false);
const formRef = ref();

const queryParams = ref({ pageNum: 1, pageSize: 10, keyword: '' });
const form = ref<Partial<FaDisposalVO>>({});
const rules = {
  assetId: [{ required: true, message: '请选择资产', trigger: 'change' }],
  disposalType: [{ required: true, message: '处置类型不能为空', trigger: 'change' }],
  disposalDate: [{ required: true, message: '处置日期不能为空', trigger: 'change' }],
  disposalPrice: [{ required: true, message: '处置收入不能为空', trigger: 'blur' }]
};

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function getList() {
  loading.value = true;
  listDisposal(queryParams.value)
    .then((response) => {
      dataList.value = response.rows;
      total.value = response.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  queryParams.value = { pageNum: 1, pageSize: 10, keyword: '' };
  getList();
}

function loadAssets() {
  listAsset({ pageNum: 1, pageSize: 200, status: 'IN_USE' } as any).then((response) => {
    assetOptions.value = response.rows || [];
  });
}

function reset() {
  form.value = { disposalType: 'SALE', disposalPrice: 0, disposalDate: new Date().toISOString().slice(0, 10) } as any;
}

function handleAdd() {
  reset();
  open.value = true;
}

function submitForm() {
  formRef.value.validate((valid: boolean) => {
    if (!valid) return;
    addDisposal(form.value)
      .then(() => {
        proxy.$modal.msgSuccess('处置完成并已过账');
        open.value = false;
        getList();
      })
      .catch(() => {});
  });
}

onMounted(() => {
  getList();
  loadAssets();
});
</script>

<style scoped>
.el-form-item__content-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
