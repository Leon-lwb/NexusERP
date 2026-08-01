<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="方向" prop="direction">
            <el-select v-model="queryParams.direction" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="d in fin_receipt_direction" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="发票号" prop="invoiceNo">
            <el-input v-model="queryParams.invoiceNo" placeholder="请输入发票号" clearable @keyup.enter="handleQuery" />
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
        <el-button type="primary" plain icon="Connection" @click="handleSettle" v-hasPermi="['fin:arap:settlement:settle']">核销</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="核销单号" align="center" prop="settlementNo" width="180" />
      <el-table-column label="方向" align="center" prop="direction" width="90">
        <template #default="scope"><dict-tag :options="fin_receipt_direction" :value="scope.row.direction" /></template>
      </el-table-column>
      <el-table-column label="发票号" align="center" prop="invoiceNo" width="180" />
      <el-table-column label="收付款单号" align="center" prop="receiptNo" width="180" />
      <el-table-column label="核销金额" align="center" prop="settleAmount" width="120" />
      <el-table-column label="核销日期" align="center" prop="settleDate" width="120" />
      <el-table-column label="操作人" align="center" prop="operator" width="120" />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="应收应付核销" v-model="open" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="方向" prop="direction">
          <el-select v-model="form.direction" @change="loadOpenItems" style="width: 100%">
            <el-option v-for="d in fin_receipt_direction" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票" prop="invoiceId">
          <el-select v-model="form.invoiceId" placeholder="选择未清发票" filterable style="width: 100%">
            <el-option v-for="i in invoiceItems" :key="i.itemId" :label="i.itemNo + ' (未清 ' + i.openAmount + ')'" :value="i.itemId" />
          </el-select>
        </el-form-item>
        <el-form-item label="收付款单" prop="receiptId">
          <el-select v-model="form.receiptId" placeholder="选择未清收付款单" filterable style="width: 100%">
            <el-option v-for="i in receiptItems" :key="i.itemId" :label="i.itemNo + ' (未清 ' + i.openAmount + ')'" :value="i.itemId" />
          </el-select>
        </el-form-item>
        <el-form-item label="核销金额" prop="settleAmount">
          <el-input-number v-model="form.settleAmount" :min="0" :precision="2" :step="100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitSettle">确认核销</el-button>
          <el-button @click="open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FinSettlement">
import { listFinSettlement, settleFinSettlement, listOpenItems } from '@/api/erp/fin/settlement';
import { FinSettlementVO, FinSettlementForm, FinSettlementQuery, OpenItemVO } from '@/api/erp/fin/settlement/types';

const { proxy } = getCurrentInstance() as any;
const { fin_receipt_direction } = toRefs<any>(proxy.useDict('fin_receipt_direction'));

const dataList = ref<FinSettlementVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const invoiceItems = ref<OpenItemVO[]>([]);
const receiptItems = ref<OpenItemVO[]>([]);

const data = reactive({
  form: { direction: 'AR', invoiceId: undefined, receiptId: undefined, settleAmount: 0 } as FinSettlementForm,
  queryParams: { pageNum: 1, pageSize: 20, direction: undefined, invoiceNo: undefined } as FinSettlementQuery,
  rules: {
    direction: [{ required: true, message: '方向不能为空', trigger: 'change' }],
    invoiceId: [{ required: true, message: '请选择发票', trigger: 'change' }],
    receiptId: [{ required: true, message: '请选择收付款单', trigger: 'change' }],
    settleAmount: [{ required: true, message: '核销金额不能为空', trigger: 'blur' }]
  }
});
const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listFinSettlement(queryParams.value).then((res) => {
    dataList.value = res.rows;
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
function handleSettle() {
  form.value = { direction: 'AR', invoiceId: undefined, receiptId: undefined, settleAmount: 0 } as FinSettlementForm;
  invoiceItems.value = [];
  receiptItems.value = [];
  loadOpenItems();
  open.value = true;
}
function loadOpenItems() {
  if (!form.value.direction) return;
  listOpenItems(form.value.direction).then((res) => {
    const list: OpenItemVO[] = res.data;
    invoiceItems.value = list.filter((i) => i.itemType === 'INVOICE');
    receiptItems.value = list.filter((i) => i.itemType === 'RECEIPT');
  });
}
function submitSettle() {
  proxy.$refs['formRef'].validate(async (valid: boolean) => {
    if (!valid) return;
    await settleFinSettlement(form.value);
    proxy.$modal.msgSuccess('核销成功');
    open.value = false;
    getList();
  });
}
onMounted(() => {
  getList();
});
</script>
