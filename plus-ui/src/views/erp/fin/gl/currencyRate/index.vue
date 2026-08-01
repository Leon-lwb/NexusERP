<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="币种" prop="currency">
            <el-input v-model="queryParams.currency" placeholder="如 USD" clearable @keyup.enter="handleQuery" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['fin:gl:currency:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['fin:gl:currency:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['fin:gl:currency:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="币种" align="center" prop="currency" width="100" />
      <el-table-column label="本位币" align="center" prop="baseCurrency" width="100" />
      <el-table-column label="汇率日期" align="center" prop="rateDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.rateDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="汇率" align="right" prop="rate" />
      <el-table-column label="类型" align="center" prop="rateType" width="100" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['fin:gl:currency:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['fin:gl:currency:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="480px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="币种" prop="currency">
          <el-input v-model="form.currency" placeholder="如 USD" />
        </el-form-item>
        <el-form-item label="本位币" prop="baseCurrency">
          <el-input v-model="form.baseCurrency" placeholder="CNY" />
        </el-form-item>
        <el-form-item label="汇率日期" prop="rateDate">
          <el-date-picker v-model="form.rateDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="汇率" prop="rate">
          <el-input-number v-model="form.rate" :min="0" :precision="6" :step="0.0001" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型" prop="rateType">
          <el-select v-model="form.rateType" style="width: 100%">
            <el-option label="中间价" value="MIDDLE" />
            <el-option label="买入价" value="BID" />
            <el-option label="卖出价" value="ASK" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="GlCurrencyRate">
import { listCurrencyRate, getCurrencyRate, addCurrencyRate, updateCurrencyRate, delCurrencyRate } from '@/api/erp/fin/gl/currencyRate';
import { CurrencyRateVO, CurrencyRateForm, CurrencyRateQuery } from '@/api/erp/fin/gl/currencyRate/types';

const { proxy } = getCurrentInstance() as any;

const dataList = ref<CurrencyRateVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const title = ref('');
const ids = ref<number[]>([]);
const single = ref(true);
const multiple = ref(true);

const data = reactive({
  form: {} as CurrencyRateForm,
  queryParams: { pageNum: 1, pageSize: 20, currency: undefined, rateType: undefined } as CurrencyRateQuery,
  rules: {
    currency: [{ required: true, message: '币种不能为空', trigger: 'blur' }],
    rateDate: [{ required: true, message: '汇率日期不能为空', trigger: 'change' }],
    rate: [{ required: true, message: '汇率不能为空', trigger: 'blur' }]
  }
});
const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listCurrencyRate(queryParams.value).then((response) => {
    dataList.value = response.rows;
    total.value = response.total;
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
function handleSelectionChange(selection: CurrencyRateVO[]) {
  ids.value = selection.map((item) => item.rateId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

function reset() {
  form.value = { rateId: undefined, currency: '', baseCurrency: 'CNY', rateDate: undefined, rate: 0, rateType: 'MIDDLE' };
  proxy.resetForm('formRef');
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = '新增汇率';
}
function handleUpdate(row?: CurrencyRateVO) {
  const rateId = row ? row.rateId : ids.value[0];
  reset();
  getCurrencyRate(rateId).then((response) => {
    form.value = response.data as CurrencyRateForm;
    open.value = true;
    title.value = '修改汇率';
  });
}

function submitForm() {
  proxy.$refs['formRef'].validate(async (valid: boolean) => {
    if (!valid) return;
    if (form.value.rateId) {
      await updateCurrencyRate(form.value);
      proxy.$modal.msgSuccess('修改成功');
    } else {
      await addCurrencyRate(form.value);
      proxy.$modal.msgSuccess('新增成功');
    }
    open.value = false;
    getList();
  });
}

function handleDelete(row?: CurrencyRateVO) {
  const delIds = row ? [row.rateId] : ids.value;
  proxy.$modal.confirm('确认删除选中的汇率记录?').then(async () => {
    await delCurrencyRate(delIds);
    proxy.$modal.msgSuccess('删除成功');
    getList();
  });
}

function cancel() {
  open.value = false;
  reset();
}

onMounted(() => {
  getList();
});
</script>
