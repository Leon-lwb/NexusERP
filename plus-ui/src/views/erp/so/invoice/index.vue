<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px" class="query-form">
      <el-form-item label="发票号" prop="invoiceNo" style="width: 250px;">
        <el-input v-model="queryParams.invoiceNo" placeholder="请输入发票号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="客户名称" prop="clientName" style="width: 250px;">
        <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="发票类型" prop="invoiceType">
        <el-select v-model="queryParams.invoiceType" placeholder="请选择类型" clearable style="width: 120px;">
          <el-option label="增值税专用发票" value="VAT_SPECIAL" />
          <el-option label="增值税普通发票" value="VAT_NORMAL" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="发票状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px;">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已开" value="ISSUED" />
          <el-option label="已核销" value="VERIFIED" />
          <el-option label="已作废" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['so:invoice:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['so:invoice:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['so:invoice:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['so:invoice:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="发票号" align="center" prop="invoiceNo" width="150" />
      <el-table-column label="客户名称" align="center" prop="clientName" width="150" />
      <el-table-column label="发票类型" align="center" prop="invoiceType" width="130">
        <template #default="scope">
          {{ getInvoiceTypeLabel(scope.row.invoiceType) }}
        </template>
      </el-table-column>
      <el-table-column label="开票日期" align="center" prop="invoiceDate" width="110" />
      <el-table-column label="金额" align="center" prop="amount" width="120" />
      <el-table-column label="税额" align="center" prop="taxAmount" width="100" />
      <el-table-column label="价税合计" align="center" prop="totalAmount" width="120" />
      <el-table-column label="已核销" align="center" prop="verifiedAmount" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:invoice:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:invoice:remove']">删除</el-button>
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
                    <el-option label="增值税专用发票" value="VAT_SPECIAL" />
                    <el-option label="增值税普通发票" value="VAT_NORMAL" />
                    <el-option label="其他" value="OTHER" />
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
                    <el-option label="草稿" value="DRAFT" />
                    <el-option label="已开" value="ISSUED" />
                    <el-option label="已核销" value="VERIFIED" />
                    <el-option label="已作废" value="CANCELLED" />
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
                <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加行</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除行</el-button>
              </el-col>
            </el-row>
            <el-table :data="lineList" @selection-change="handleLineSelectionChange" border size="small">
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
                  <el-input-number v-model="scope.row.quantity" size="small" controls-position="right" :min="0" :precision="4" @change="calcLineAmount(scope.row)" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="80">
                <template #default="scope">
                  <el-input v-model="scope.row.unit" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="单价" width="120">
                <template #default="scope">
                  <el-input-number v-model="scope.row.unitPrice" size="small" controls-position="right" :min="0" :precision="4" @change="calcLineAmount(scope.row)" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="行金额" width="120">
                <template #default="scope">
                  <el-input-number v-model="scope.row.lineAmount" size="small" controls-position="right" :precision="2" readonly style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="税率(%)" width="100">
                <template #default="scope">
                  <el-input-number v-model="scope.row.taxRate" size="small" controls-position="right" :min="0" :max="100" :precision="2" @change="calcLineTax(scope.row)" style="width: 100%" />
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
  </div>
</template>

<script setup lang="ts" name="SoInvoice">
import { listInvoice, getInvoice, delInvoice, addInvoice, updateInvoice } from "@/api/erp/so/invoice";
import { listMaterial } from "@/api/erp/bd/material";
import { InvoiceVO, InvoiceForm, InvoiceLineForm } from "@/api/erp/so/invoice/types";

const { proxy } = getCurrentInstance();

// 列表数据
const dataList = ref<InvoiceVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<number[]>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");
const isViewMode = ref(false);
const activeTab = ref("basic");

// 明细行数据
const lineList = ref<InvoiceLineForm[]>([]);
const checkedLines = ref<number[]>([]);
const currentLineIndex = ref(-1);

// 物料选择对话框
const materialDialogVisible = ref(false);
const materialList = ref<any[]>([]);
const materialQuery = ref({ materialCode: '', materialName: '', pageNum: 1, pageSize: 20 });

const data = reactive({
  form: {} as InvoiceForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    invoiceNo: undefined,
    clientName: undefined,
    invoiceType: undefined,
    status: undefined
  },
  rules: {
    invoiceNo: [{ required: true, message: "发票号不能为空", trigger: "blur" }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 获取发票类型标签 */
function getInvoiceTypeLabel(type: string) {
  const map: Record<string, string> = {
    VAT_SPECIAL: '增值税专用发票',
    VAT_NORMAL: '增值税普通发票',
    OTHER: '其他'
  };
  return map[type] || type;
}

/** 获取状态标签类型 */
function getStatusType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    ISSUED: 'success',
    VERIFIED: 'primary',
    CANCELLED: 'danger'
  };
  return map[status] || 'info';
}

/** 获取状态标签文本 */
function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    ISSUED: '已开',
    VERIFIED: '已核销',
    CANCELLED: '已作废'
  };
  return map[status] || status;
}

/** 查询列表 */
function getList() {
  loading.value = true;
  listInvoice(queryParams.value).then(response => {
    dataList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 取消 */
function cancel() {
  open.value = false;
  reset();
}

/** 重置表单 */
function reset() {
  form.value = {
    invoiceId: undefined,
    invoiceNo: '',
    invoiceType: 'VAT_NORMAL',
    clientId: undefined,
    clientName: '',
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
    remark: '',
    soInvoiceLineList: []
  };
  lineList.value = [];
  activeTab.value = "basic";
  proxy.resetForm("formRef");
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection: InvoiceVO[]) {
  ids.value = selection.map(item => item.invoiceId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/** 新增 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = "添加销售发票";
}

/** 修改 */
function handleUpdate(row?: InvoiceVO) {
  reset();
  isViewMode.value = false;
  const invoiceId = row?.invoiceId || ids.value[0];
  getInvoice(invoiceId).then(response => {
    form.value = response.data;
    lineList.value = response.data.soInvoiceLineList || [];
    open.value = true;
    title.value = "修改销售发票";
  });
}

/** 查看 */
function handleView(row: InvoiceVO) {
  reset();
  isViewMode.value = true;
  getInvoice(row.invoiceId).then(response => {
    form.value = response.data;
    lineList.value = response.data.soInvoiceLineList || [];
    open.value = true;
    title.value = "查看销售发票";
  });
}

/** 提交表单 */
function submitForm() {
  proxy.$refs["formRef"].validate((valid: boolean) => {
    if (valid) {
      form.value.soInvoiceLineList = lineList.value;
      if (form.value.invoiceId != null) {
        updateInvoice(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addInvoice(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除 */
function handleDelete(row?: InvoiceVO) {
  const invoiceIds = row?.invoiceId || ids.value;
  proxy.$modal.confirm('是否确认删除销售发票编号为"' + invoiceIds + '"的数据项？').then(function () {
    return delInvoice(invoiceIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 导出 */
function handleExport() {
  proxy.download('erp/so/invoice/export', { ...queryParams.value }, `invoice_${new Date().getTime()}.xlsx`);
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
  row.lineAmount = ((row.quantity || 0) * (row.unitPrice || 0));
  calcLineTax(row);
}

/** 计算行税额 */
function calcLineTax(row: InvoiceLineForm) {
  row.taxAmount = ((row.lineAmount || 0) * (row.taxRate || 0) / 100);
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
  materialDialogVisible.value = true;
  queryMaterials();
}

/** 查询物料 */
function queryMaterials() {
  listMaterial(materialQuery.value as any).then((res: any) => {
    materialList.value = res.rows || res.data || [];
  });
}

/** 选择物料 */
function selectMaterial(row: any) {
  if (currentLineIndex.value >= 0) {
    const target = lineList.value[currentLineIndex.value];
    target.materialId = row.materialId;
    target.materialCode = row.materialCode;
    target.materialName = row.materialName;
    target.materialSpec = row.materialSpec;
    target.unit = row.baseUomName || '';
  }
  materialDialogVisible.value = false;
}

/** 打开客户选择对话框（简化实现） */
function openClientDialog() {
  proxy.$modal.msgInfo("客户选择功能待实现");
}

getList();
</script>
