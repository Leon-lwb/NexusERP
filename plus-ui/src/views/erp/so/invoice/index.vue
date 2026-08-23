<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="发票号" prop="invoiceNo">
            <el-input v-model="queryParams.invoiceNo" placeholder="请输入发票号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户名称" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="发票类型" prop="invoiceType">
            <el-select v-model="queryParams.invoiceType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="dict in so_invoice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="发票状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in so_invoice_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['so:invoice:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['so:invoice:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['so:invoice:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['so:invoice:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="发票号" align="center" prop="invoiceNo" />
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="发票类型" align="center" prop="invoiceType">
        <template #default="scope">
          <dict-tag :options="so_invoice_type" :value="scope.row.invoiceType" />
        </template>
      </el-table-column>
      <el-table-column label="开票日期" align="center" prop="invoiceDate" />
      <el-table-column label="金额" align="center" prop="amount" />
      <el-table-column label="税额" align="center" prop="taxAmount" />
      <el-table-column label="价税合计" align="center" prop="totalAmount" />
      <el-table-column label="已核销" align="center" prop="verifiedAmount" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_invoice_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="300" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status === 'DRAFT' || scope.row.status === 'REJECTED'"
            v-hasPermi="['so:invoice:edit']"
            >修改</el-button
          >
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['so:invoice:remove']"
            >删除</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleVerify(scope.row)"
            v-if="scope.row.status === 'ISSUED'"
            v-hasPermi="['so:invoice:edit']"
            >核销</el-button
          >
          <el-button
            link
            type="warning"
            icon="RefreshLeft"
            @click="handleRedFlush(scope.row)"
            v-if="scope.row.status === 'ISSUED' || scope.row.status === 'VERIFIED'"
            v-hasPermi="['so:invoice:edit']"
            >红冲</el-button
          >
          <el-button
            link
            type="danger"
            icon="CircleClose"
            @click="handleCancel(scope.row)"
            v-if="scope.row.status === 'ISSUED' || scope.row.status === 'DRAFT'"
            v-hasPermi="['so:invoice:edit']"
            >作废</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="发票号" prop="invoiceNo">
                  <el-input v-model="form.invoiceNo" placeholder="请输入发票号" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="发票类型" prop="invoiceType">
                  <el-select v-model="form.invoiceType" placeholder="请选择发票类型" style="width: 100%">
                    <el-option v-for="dict in so_invoice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="客户" prop="clientId">
                  <el-input v-model="form.clientName" readonly>
                    <template #append>
                      <el-button icon="Search" @click="openClientDialog" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="发票代码" prop="invoiceCode">
                  <el-input v-model="form.invoiceCode" placeholder="请输入发票代码" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="开票渠道" prop="issueChannel">
                  <el-select v-model="form.issueChannel" placeholder="请选择开票渠道" style="width: 100%">
                    <el-option v-for="dict in invoice_issue_channel" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="购方税号" prop="buyerTaxId">
                  <el-input v-model="form.buyerTaxId" placeholder="购买方纳税人识别号" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="购方抬头" prop="buyerTitle">
                  <el-input v-model="form.buyerTitle" placeholder="购买方发票抬头" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="购方开户行" prop="buyerBankName">
                  <el-input v-model="form.buyerBankName" placeholder="购买方开户行" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="购方账号" prop="buyerBankAccount">
                  <el-input v-model="form.buyerBankAccount" placeholder="购买方银行账号" />
                </el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="购方地址电话" prop="buyerAddressPhone">
                  <el-input v-model="form.buyerAddressPhone" placeholder="购买方地址、电话" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="开票日期" prop="invoiceDate">
                  <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择开票日期" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="币种" prop="currency">
                  <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
                    <el-option label="人民币(CNY)" value="CNY" />
                    <el-option label="美元(USD)" value="USD" />
                    <el-option label="欧元(EUR)" value="EUR" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="状态" prop="status">
                  <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                    <el-option v-for="dict in so_invoice_status" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="金额" prop="amount">
                  <el-input-number v-model="form.amount" :precision="2" :min="0" controls-position="right" style="width: 100%" readonly />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="税额" prop="taxAmount">
                  <el-input-number v-model="form.taxAmount" :precision="2" :min="0" controls-position="right" style="width: 100%" readonly />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="价税合计" prop="totalAmount">
                  <el-input-number v-model="form.totalAmount" :precision="2" :min="0" controls-position="right" style="width: 100%" readonly />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="备注" prop="remark">
                  <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane label="发票明细" name="line">
            <el-row :gutter="10" class="mb8">
              <el-col :span="1.5">
                <el-button type="primary" icon="Plus" @click="handleAddLine()" v-if="!isViewMode">添加行</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button type="danger" icon="Delete" @click="handleDeleteLine()" v-if="!isViewMode">删除行</el-button>
              </el-col>
            </el-row>
            <el-table :data="lineList" @selection-change="handleLineSelectionChange" border size="small" show-overflow-tooltip>
              <el-table-column type="selection" width="50" v-if="!isViewMode" />
              <el-table-column label="行号" width="60" type="index" />
              <el-table-column label="物料编码" width="150">
                <template #default="scope">
                  <el-input v-model="scope.row.materialCode" readonly size="small">
                    <template #append>
                      <el-button icon="Search" @click="openMaterialDialog(scope.$index)" size="small" />
                    </template>
                  </el-input>
                </template>
              </el-table-column>
              <el-table-column label="物料名称" prop="materialName" min-width="150" />
              <el-table-column label="数量" width="120">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.quantity"
                    size="small"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    @change="calcLineAmount(scope.row)"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="80">
                <template #default="scope">
                  <el-input v-model="scope.row.unit" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="单价" width="120">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.unitPrice"
                    size="small"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    @change="calcLineAmount(scope.row)"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="行金额" width="120">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.lineAmount"
                    size="small"
                    controls-position="right"
                    :precision="2"
                    readonly
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="税率(%)" width="100">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.taxRate"
                    size="small"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    :precision="2"
                    @change="calcLineTax(scope.row)"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="税额" width="100">
                <template #default="scope">
                  <el-input-number v-model="scope.row.taxAmount" size="small" controls-position="right" :precision="2" readonly style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="120">
                <template #default="scope">
                  <el-input v-model="scope.row.remark" size="small" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 核销对话框 -->
    <el-dialog title="发票核销" v-model="verifyOpen" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="价税合计">{{ verifyForm.totalAmount }}</el-form-item>
        <el-form-item label="已核销">{{ verifyForm.verifiedAmount }}</el-form-item>
        <el-form-item label="未核销余额">{{ verifyForm.remaining }}</el-form-item>
        <el-form-item label="本次核销金额">
          <el-input-number v-model="verifyForm.verifyAmount" :precision="2" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitVerify()">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 客户选择弹窗 -->
    <ClientSelectDialog ref="clientSelectDialogRef" @confirm="handleClientConfirm" />

    <!-- 物料选择弹窗（明细行选择物料） -->
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="SoInvoice">
import { listInvoice, getInvoice, delInvoice, addInvoice, updateInvoice, verifyInvoice, cancelInvoice, redFlushInvoice } from '@/api/erp/so/invoice';
import { InvoiceVO, InvoiceLineForm } from '@/api/erp/so/invoice/types';
import ClientSelectDialog from '@/views/erp/so/components/ClientSelectDialog.vue';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const formRef = ref<FormInstance>();
const { so_invoice_type, so_invoice_status, invoice_issue_channel } = toRefs<any>(
  proxy.useDict('so_invoice_type', 'so_invoice_status', 'invoice_issue_channel')
);

// 客户选择弹窗引用
const clientSelectDialogRef = ref();

// 明细行数据
const lineList = ref<InvoiceLineForm[]>([]);
const checkedLines = ref<number[]>([]);
const currentLineIndex = ref(-1);
const materialSelectDialogRef = ref();
const activeTab = ref('basic');

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  invoiceNo: undefined,
  clientName: undefined,
  invoiceType: undefined,
  status: undefined
});

const rules = {
  invoiceNo: [{ required: true, message: '发票号不能为空', trigger: 'blur' }]
};

const {
  loading,
  list: dataList,
  total,
  open,
  isViewMode,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  handleView,
  submitForm,
  cancel,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listInvoice, get: getInvoice, add: addInvoice, update: updateInvoice, del: delInvoice },
  idField: 'invoiceId',
  entityName: '销售发票',
  formRef,
  queryParams,
  initForm: () => ({
    invoiceId: undefined,
    invoiceNo: '',
    invoiceCode: '',
    invoiceType: 'VAT_NORMAL',
    clientId: undefined,
    clientName: '',
    buyerTaxId: '',
    buyerTitle: '',
    buyerBankName: '',
    buyerBankAccount: '',
    buyerAddressPhone: '',
    soId: undefined,
    contractId: undefined,
    invoiceDate: undefined,
    currency: 'CNY',
    exchangeRate: 1,
    amount: 0,
    taxAmount: 0,
    totalAmount: 0,
    verifiedAmount: 0,
    status: 'DRAFT',
    issueChannel: 'MANUAL',
    remark: '',
    soInvoiceLineList: []
  }),
  exportUrl: 'erp/so/invoice/export',
  exportFileName: 'invoice',
  onReset: () => {
    lineList.value = [];
    activeTab.value = 'basic';
  },
  beforeSubmit: () => {
    form.value.soInvoiceLineList = lineList.value;
  },
  afterLoad: (data: any) => {
    lineList.value = data.soInvoiceLineList || [];
  }
});

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 添加明细行 */
function handleAddLine() {
  lineList.value.push({
    lineNo: String(lineList.value.length + 1),
    materialId: undefined,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    quantity: 0,
    unit: '',
    unitPrice: 0,
    lineAmount: 0,
    taxRate: 13,
    taxAmount: 0,
    totalAmount: 0,
    remark: ''
  });
}

/** 删除明细行 */
function handleDeleteLine() {
  lineList.value = lineList.value.filter((_, i) => !checkedLines.value.includes(i));
}

/** 明细行选择变化 */
function handleLineSelectionChange(rows: InvoiceLineForm[]) {
  checkedLines.value = rows.map((r: InvoiceLineForm) => lineList.value.indexOf(r));
}

/** 计算行金额 */
function calcLineAmount(row: InvoiceLineForm) {
  row.lineAmount = (row.quantity || 0) * (row.unitPrice || 0);
  calcLineTax(row);
}

/** 计算行税额 */
function calcLineTax(row: InvoiceLineForm) {
  row.taxAmount = ((row.lineAmount || 0) * (row.taxRate || 0)) / 100;
  row.totalAmount = (row.lineAmount || 0) + (row.taxAmount || 0);
  calcTotalAmount();
}

/** 计算总金额 */
function calcTotalAmount() {
  form.value.amount = lineList.value.reduce((sum, row) => sum + (row.lineAmount || 0), 0);
  form.value.taxAmount = lineList.value.reduce((sum, row) => sum + (row.taxAmount || 0), 0);
  form.value.totalAmount = (form.value.amount || 0) + (form.value.taxAmount || 0);
}

/** 打开物料选择对话框 */
function openMaterialDialog(index: number) {
  currentLineIndex.value = index;
  materialSelectDialogRef.value?.open();
}

/** 物料选择回调 */
function handleMaterialSelected(row: any) {
  if (currentLineIndex.value >= 0) {
    const target = lineList.value[currentLineIndex.value];
    target.materialId = row.materialId;
    target.materialCode = row.materialCode;
    target.materialName = row.materialName;
    target.materialSpec = row.materialSpec;
    target.unit = row.baseUomName || '';
  }
}

/** 打开客户选择对话框 */
function openClientDialog() {
  clientSelectDialogRef.value?.open();
}

/** 客户选择确认 */
function handleClientConfirm(client: any) {
  form.value.clientId = client.clientId;
  form.value.clientName = client.clientName;
}

// 核销对话框
const verifyOpen = ref(false);
const verifyForm = reactive({ invoiceId: undefined as any, totalAmount: 0, verifiedAmount: 0, remaining: 0, verifyAmount: 0 });

/** 打开核销对话框 */
function handleVerify(row: InvoiceVO) {
  verifyForm.invoiceId = row.invoiceId;
  verifyForm.totalAmount = Number(row.totalAmount) || 0;
  verifyForm.verifiedAmount = Number(row.verifiedAmount) || 0;
  verifyForm.remaining = Number((verifyForm.totalAmount - verifyForm.verifiedAmount).toFixed(2));
  verifyForm.verifyAmount = verifyForm.remaining > 0 ? verifyForm.remaining : 0;
  verifyOpen.value = true;
}

/** 提交核销 */
function submitVerify() {
  if (!verifyForm.verifyAmount || verifyForm.verifyAmount <= 0) {
    proxy.$modal.msgError('核销金额必须大于0');
    return;
  }
  verifyInvoice(verifyForm.invoiceId, verifyForm.verifyAmount).then(() => {
    proxy.$modal.msgSuccess('核销成功');
    verifyOpen.value = false;
    getList();
  });
}

/** 作废发票 */
function handleCancel(row: InvoiceVO) {
  proxy.$modal
    .confirm('确认作废发票【' + row.invoiceNo + '】?作废将回退订单行已开票数量。')
    .then(() => cancelInvoice(row.invoiceId))
    .then(() => {
      proxy.$modal.msgSuccess('作废成功');
      getList();
    })
    .catch(() => {});
}

/** 红冲发票 */
function handleRedFlush(row: InvoiceVO) {
  proxy.$modal
    .confirm('确认红冲发票【' + row.invoiceNo + '】?将生成红字负数发票并冲减原发票已核销金额。')
    .then(() => redFlushInvoice(row.invoiceId))
    .then(() => {
      proxy.$modal.msgSuccess('红冲成功');
      getList();
    })
    .catch(() => {});
}

getList();
</script>
