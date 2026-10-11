<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="单号" prop="receiptNo">
            <el-input v-model="queryParams.receiptNo" placeholder="请输入单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="方向" prop="direction">
            <el-select v-model="queryParams.direction" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="d in fin_receipt_direction" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="d in fin_receipt_status" :key="d.value" :label="d.label" :value="d.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['fin:arap:receipt:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['fin:arap:receipt:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="单号" align="center" prop="receiptNo" width="180" />
      <el-table-column label="方向" align="center" prop="direction" width="90">
        <template #default="scope"><dict-tag :options="fin_receipt_direction" :value="scope.row.direction" /></template>
      </el-table-column>
      <el-table-column label="类型" align="center" prop="receiptType" width="90">
        <template #default="scope"><dict-tag :options="fin_receipt_type" :value="scope.row.receiptType" /></template>
      </el-table-column>
      <el-table-column label="客户/供应商" align="center" prop="payerName" width="160" show-overflow-tooltip />
      <el-table-column label="金额" align="right" prop="amount" width="120" />
      <el-table-column label="已核销" align="right" prop="settledAmount" width="120" />
      <el-table-column label="来源" align="center" prop="source" width="100">
        <template #default="scope"><dict-tag :options="fin_receipt_source" :value="scope.row.source" /></template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope"><dict-tag :options="fin_receipt_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="日期" align="center" prop="receiptDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.receiptDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-if="scope.row.status === 'DRAFT'"
            @click="handleConfirm(scope.row)"
            v-hasPermi="['fin:arap:receipt:confirm']"
            >确认</el-button
          >
          <el-button link type="danger" v-if="scope.row.status === 'DRAFT'" @click="handleCancel(scope.row)" v-hasPermi="['fin:arap:receipt:cancel']"
            >作废</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="方向" prop="direction">
          <el-select v-model="form.direction" style="width: 100%">
            <el-option v-for="d in fin_receipt_direction" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="收付款类型" prop="receiptType">
          <el-select v-model="form.receiptType" style="width: 100%">
            <el-option v-for="d in fin_receipt_type" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户ID" prop="clientId" v-if="form.direction === 'AR'">
          <el-input v-model="form.clientId" placeholder="客户ID" />
        </el-form-item>
        <el-form-item label="供应商ID" prop="vendorId" v-if="form.direction === 'AP'">
          <el-input v-model="form.vendorId" placeholder="供应商ID" />
        </el-form-item>
        <el-form-item label="付款方" prop="payerName">
          <el-input v-model="form.payerName" placeholder="付款方名称" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="日期" prop="receiptDate">
          <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" />
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

<script setup lang="ts" name="FinReceipt">
import {
  listFinReceipt,
  getFinReceipt,
  delFinReceipt,
  addFinReceipt,
  updateFinReceipt,
  confirmFinReceipt,
  cancelFinReceipt
} from '@/api/erp/fin/receipt';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { FinReceiptVO, FinReceiptForm, FinReceiptQuery } from '@/api/erp/fin/receipt/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { fin_receipt_type, fin_receipt_direction, fin_receipt_source, fin_receipt_status } = toRefs<any>(
  proxy.useDict('fin_receipt_type', 'fin_receipt_direction', 'fin_receipt_source', 'fin_receipt_status')
);

const formRef = ref<FormInstance>();

const queryParams = ref<FinReceiptQuery>({
  pageNum: 1,
  pageSize: 20,
  receiptNo: undefined,
  direction: undefined,
  status: undefined
});

const rules = {
  direction: [{ required: true, message: '方向不能为空', trigger: 'change' }],
  amount: [{ required: true, message: '金额不能为空', trigger: 'blur' }]
};

const {
  loading,
  list,
  total,
  open,
  title,
  ids,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel
} = useCrud<FinReceiptForm, FinReceiptQuery>({
  apis: { list: listFinReceipt, get: getFinReceipt, add: addFinReceipt, update: updateFinReceipt, del: delFinReceipt },
  idField: 'receiptId',
  entityName: '收付款单',
  formRef,
  queryParams,
  initForm: () => ({
    direction: 'AR',
    receiptType: 'BANK',
    amount: 0,
    receiptDate: undefined,
    clientId: undefined,
    vendorId: undefined,
    payerName: '',
    remark: ''
  }),
  // FinReceiptController 实际映射为 /erp/fin/arap/receipt（含 export），
  // 原 '/erp/fin/receipt/export' 无对应端点 → 导出 404（FIN-ARAP 导出修复）
  exportUrl: '/erp/fin/arap/receipt/export',
  exportFileName: 'receipt'
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleConfirm(row: FinReceiptVO) {
  proxy.$modal.confirm('确认收付款单【' + row.receiptNo + '】?确认后触发 GL 过账。').then(async () => {
    await confirmFinReceipt(row.receiptId);
    proxy.$modal.msgSuccess('确认成功');
    getList();
  });
}
function handleCancel(row: FinReceiptVO) {
  proxy.$modal.confirm('作废收付款单【' + row.receiptNo + '】?').then(async () => {
    await cancelFinReceipt(row.receiptId);
    proxy.$modal.msgSuccess('作废成功');
    getList();
  });
}

getList();
</script>
