<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="凭证号" prop="voucherNo">
            <el-input v-model="queryParams.voucherNo" placeholder="请输入凭证号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="凭证状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in gl_voucher_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="凭证来源" prop="voucherSource">
            <el-select v-model="queryParams.voucherSource" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in gl_voucher_source" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['fin:gl:voucher:add']">新增凭证</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="凭证号" align="center" prop="voucherNo" width="180" />
      <el-table-column label="凭证字" align="center" prop="voucherWord" width="70">
        <template #default="scope">
          <dict-tag :options="gl_voucher_word" :value="scope.row.voucherWord" />
        </template>
      </el-table-column>
      <el-table-column label="凭证日期" align="center" prop="voucherDate" width="110" />
      <el-table-column label="来源" align="center" prop="voucherSource">
        <template #default="scope">
          <dict-tag :options="gl_voucher_source" :value="scope.row.voucherSource" />
        </template>
      </el-table-column>
      <el-table-column label="借方合计" align="right" prop="totalDebit" width="120" />
      <el-table-column label="贷方合计" align="right" prop="totalCredit" width="120" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="gl_voucher_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="320" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['fin:gl:voucher:edit']"
            >修改</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handlePost(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['fin:gl:voucher:post']"
            >过账</el-button
          >
          <el-button
            link
            type="warning"
            icon="RefreshLeft"
            @click="handleUnpost(scope.row)"
            v-if="scope.row.status === 'POSTED'"
            v-hasPermi="['fin:gl:voucher:unpost']"
            >反过账</el-button
          >
          <el-button
            link
            type="danger"
            icon="CircleClose"
            @click="handleCancel(scope.row)"
            v-if="scope.row.status === 'DRAFT' || scope.row.status === 'POSTED'"
            v-hasPermi="['fin:gl:voucher:edit']"
            >作废</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['fin:gl:voucher:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" :disabled="isViewMode">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="凭证字" prop="voucherWord">
              <el-select v-model="form.voucherWord" style="width: 100%">
                <el-option v-for="dict in gl_voucher_word" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="凭证日期" prop="voucherDate">
              <el-date-picker v-model="form.voucherDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="附件张数" prop="attachmentCount">
              <el-input-number v-model="form.attachmentCount" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10" class="mb8" v-if="!isViewMode">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine">添加分录行</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine">删除选中行</el-button>
          </el-col>
        </el-row>

        <el-table :data="form.voucherLineList" @selection-change="handleLineSelectionChange" border size="small" show-overflow-tooltip>
          <el-table-column type="selection" width="45" v-if="!isViewMode" />
          <el-table-column label="行号" width="55" type="index" />
          <el-table-column label="科目" min-width="220">
            <template #default="scope">
              <el-select
                v-model="scope.row.accountId"
                filterable
                placeholder="选择末级科目"
                size="small"
                style="width: 100%"
                @change="(val: any) => onAccountChange(scope.row, val)"
              >
                <el-option
                  v-for="acc in leafAccountList"
                  :key="acc.accountId"
                  :label="acc.accountCode + ' ' + acc.accountName"
                  :value="acc.accountId"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="摘要" min-width="160">
            <template #default="scope">
              <el-input v-model="scope.row.summary" size="small" placeholder="摘要" />
            </template>
          </el-table-column>
          <el-table-column label="借方" width="130">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.debit"
                :min="0"
                :precision="2"
                :controls="false"
                size="small"
                style="width: 100%"
                @change="onAmountChange(scope.row, 'debit')"
              />
            </template>
          </el-table-column>
          <el-table-column label="贷方" width="130">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.credit"
                :min="0"
                :precision="2"
                :controls="false"
                size="small"
                style="width: 100%"
                @change="onAmountChange(scope.row, 'credit')"
              />
            </template>
          </el-table-column>
        </el-table>

        <div class="balance-bar">
          <span
            >借方合计:<b>{{ totalDebit }}</b></span
          >
          <span
            >贷方合计:<b>{{ totalCredit }}</b></span
          >
          <span :class="diff === 0 ? 'ok' : 'err'">差额:{{ diff }}</span>
          <el-tag v-if="diff === 0 && form.voucherLineList.length > 0" type="success" size="small">借贷平衡</el-tag>
          <el-tag v-else type="danger" size="small">不平衡</el-tag>
        </div>

        <el-form-item label="备注" prop="remark" style="margin-top: 12px">
          <el-input v-model="form.remark" type="textarea" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="GlVoucher">
import { listVoucher, getVoucher, addVoucher, updateVoucher, delVoucher, postVoucher, unpostVoucher, cancelVoucher } from '@/api/erp/fin/gl/voucher';
import { VoucherVO, VoucherForm, VoucherLineForm, VoucherQuery } from '@/api/erp/fin/gl/voucher/types';
import { leafAccounts } from '@/api/erp/fin/gl/account';
import { AccountVO } from '@/api/erp/fin/gl/account/types';

const { proxy } = getCurrentInstance() as any;
const { gl_voucher_word, gl_voucher_status, gl_voucher_source } = toRefs<any>(
  proxy.useDict('gl_voucher_word', 'gl_voucher_status', 'gl_voucher_source')
);

const dataList = ref<VoucherVO[]>([]);
const leafAccountList = ref<AccountVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);
const checkedLines = ref<number[]>([]);

const data = reactive({
  form: { voucherLineList: [] } as VoucherForm,
  queryParams: { pageNum: 1, pageSize: 10, voucherNo: undefined, status: undefined, voucherSource: undefined } as VoucherQuery,
  rules: {
    voucherDate: [{ required: true, message: '凭证日期不能为空', trigger: 'change' }]
  }
});
const { queryParams, form, rules } = toRefs(data);

const totalDebit = computed(() => (form.value.voucherLineList || []).reduce((s, r) => s + Number(r.debit || 0), 0));
const totalCredit = computed(() => (form.value.voucherLineList || []).reduce((s, r) => s + Number(r.credit || 0), 0));
const diff = computed(() => Number((totalDebit.value - totalCredit.value).toFixed(2)));

function getList() {
  loading.value = true;
  listVoucher(queryParams.value).then((response) => {
    dataList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function loadLeafAccounts() {
  leafAccounts().then((response) => {
    leafAccountList.value = response.data;
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

function reset() {
  form.value = {
    voucherId: undefined,
    voucherNo: '',
    voucherWord: 'J',
    voucherDate: new Date().toISOString().slice(0, 10),
    voucherSource: 'MANUAL',
    attachmentCount: 0,
    currency: 'CNY',
    remark: '',
    voucherLineList: []
  };
  checkedLines.value = [];
  proxy.resetForm('formRef');
}

function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '新增凭证';
}

function handleView(row: VoucherVO) {
  reset();
  isViewMode.value = true;
  getVoucher(row.voucherId).then((response) => {
    form.value = response.data as VoucherForm;
    open.value = true;
    title.value = '查看凭证';
  });
}

function handleUpdate(row: VoucherVO) {
  reset();
  isViewMode.value = false;
  getVoucher(row.voucherId).then((response) => {
    form.value = response.data as VoucherForm;
    open.value = true;
    title.value = '修改凭证';
  });
}

function handleAddLine() {
  form.value.voucherLineList.push({ accountId: 0, debit: 0, credit: 0 } as VoucherLineForm);
}
function handleLineSelectionChange(selection: VoucherLineForm[]) {
  checkedLines.value = selection.map((sel) => form.value.voucherLineList.indexOf(sel));
}
function handleDeleteLine() {
  if (checkedLines.value.length === 0) {
    proxy.$modal.msgWarning('请先选择要删除的行');
    return;
  }
  form.value.voucherLineList = form.value.voucherLineList.filter((_, idx) => !checkedLines.value.includes(idx));
  checkedLines.value = [];
}

function onAccountChange(row: VoucherLineForm, val: number) {
  const acc = leafAccountList.value.find((a) => a.accountId === val);
  if (acc) {
    row.accountCode = acc.accountCode;
    row.accountName = acc.accountName;
  }
}

/** 借贷互斥:填借方清贷方,反之亦然 */
function onAmountChange(row: VoucherLineForm, field: 'debit' | 'credit') {
  if (field === 'debit' && Number(row.debit || 0) > 0) {
    row.credit = 0;
  } else if (field === 'credit' && Number(row.credit || 0) > 0) {
    row.debit = 0;
  }
}

function submitForm() {
  proxy.$refs['formRef'].validate(async (valid: boolean) => {
    if (!valid) return;
    if (!form.value.voucherLineList || form.value.voucherLineList.length === 0) {
      proxy.$modal.msgError('至少一行分录');
      return;
    }
    if (diff.value !== 0) {
      proxy.$modal.msgError('借贷不平衡,差额 ' + diff.value + ',无法保存');
      return;
    }
    if (form.value.voucherId) {
      await updateVoucher(form.value);
      proxy.$modal.msgSuccess('修改成功');
    } else {
      await addVoucher(form.value);
      proxy.$modal.msgSuccess('新增成功');
    }
    open.value = false;
    getList();
  });
}

function handleDelete(row: VoucherVO) {
  proxy.$modal.confirm('确认删除凭证【' + row.voucherNo + '】?').then(async () => {
    await delVoucher(row.voucherId);
    proxy.$modal.msgSuccess('删除成功');
    getList();
  });
}

function handlePost(row: VoucherVO) {
  proxy.$modal.confirm('确认过账凭证【' + row.voucherNo + '】?过账后将更新科目余额。').then(async () => {
    await postVoucher(row.voucherId);
    proxy.$modal.msgSuccess('过账成功');
    getList();
  });
}

function handleUnpost(row: VoucherVO) {
  proxy.$modal.confirm('确认反过账凭证【' + row.voucherNo + '】?将回滚科目余额。').then(async () => {
    await unpostVoucher(row.voucherId);
    proxy.$modal.msgSuccess('反过账成功');
    getList();
  });
}

function handleCancel(row: VoucherVO) {
  proxy.$modal.confirm('确认作废凭证【' + row.voucherNo + '】?').then(async () => {
    await cancelVoucher(row.voucherId);
    proxy.$modal.msgSuccess('作废成功');
    getList();
  });
}

function cancel() {
  open.value = false;
  reset();
}

onMounted(() => {
  loadLeafAccounts();
  getList();
});
</script>

<style scoped>
.balance-bar {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 8px 12px;
  margin-top: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}
.balance-bar .ok {
  color: #67c23a;
}
.balance-bar .err {
  color: #f56c6c;
}
</style>
