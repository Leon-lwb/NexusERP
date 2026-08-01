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
          <el-form-item label="供应商" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="发票状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in pu_invoice_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="认证状态" prop="certStatus">
            <el-select v-model="queryParams.certStatus" placeholder="请选择认证状态" clearable style="width: 100%">
              <el-option v-for="dict in pu_invoice_cert_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['pu:invoice:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['pu:invoice:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="DocumentAdd" @click="handleGenFromPo" v-hasPermi="['pu:invoice:add']">从订单生成</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['pu:invoice:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['pu:invoice:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="发票号" align="center" prop="invoiceNo" min-width="140" />
      <el-table-column label="供应商" align="center" prop="vendorName" min-width="140" show-overflow-tooltip />
      <el-table-column label="发票类型" align="center" prop="invoiceType">
        <template #default="scope">
          <dict-tag :options="pu_invoice_type" :value="scope.row.invoiceType" />
        </template>
      </el-table-column>
      <el-table-column label="开票日期" align="center" prop="invoiceDate" width="110" />
      <el-table-column label="金额" align="center" prop="amount" width="100" />
      <el-table-column label="税额" align="center" prop="taxAmount" width="100" />
      <el-table-column label="价税合计" align="center" prop="totalAmount" width="110" />
      <el-table-column label="已核销" align="center" prop="verifiedAmount" width="100" />
      <el-table-column label="发票状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="pu_invoice_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="认证状态" align="center" prop="certStatus" width="90">
        <template #default="scope">
          <dict-tag :options="pu_invoice_cert_status" :value="scope.row.certStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="360" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['pu:invoice:edit']"
            >修改</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleReceive(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['pu:invoice:edit']"
            >收票</el-button
          >
          <el-button
            link
            type="primary"
            icon="Connection"
            @click="handleMatch(scope.row)"
            v-if="scope.row.status === 'ISSUED' || scope.row.status === 'VERIFIED'"
            v-hasPermi="['pu:invoice:match']"
            >匹配</el-button
          >
          <el-button
            link
            type="primary"
            icon="Stamp"
            @click="handleCertify(scope.row)"
            v-if="scope.row.status === 'ISSUED' && scope.row.certStatus === 'UNCERTIFIED'"
            v-hasPermi="['pu:invoice:certify']"
            >认证</el-button
          >
          <el-button
            link
            type="success"
            icon="Promotion"
            @click="handleDeduct(scope.row)"
            v-if="scope.row.certStatus === 'CERTIFIED'"
            v-hasPermi="['pu:invoice:certify']"
            >抵扣</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleVerify(scope.row)"
            v-if="scope.row.status === 'ISSUED'"
            v-hasPermi="['pu:invoice:verify']"
            >核销</el-button
          >
          <el-button
            link
            type="warning"
            icon="RefreshLeft"
            @click="handleRedFlush(scope.row)"
            v-if="scope.row.status === 'ISSUED' || scope.row.status === 'VERIFIED'"
            v-hasPermi="['pu:invoice:verify']"
            >红冲</el-button
          >
          <el-button
            link
            type="danger"
            icon="CircleClose"
            @click="handleCancel(scope.row)"
            v-if="scope.row.status === 'ISSUED' || scope.row.status === 'DRAFT'"
            v-hasPermi="['pu:invoice:edit']"
            >作废</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['pu:invoice:remove']"
            >删除</el-button
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
                    <el-option v-for="dict in pu_invoice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="供应商" prop="vendorName">
                  <el-input v-model="form.vendorName" readonly>
                    <template #append>
                      <el-button icon="Search" @click="openVendorDialog" />
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
                <el-form-item label="采购订单ID" prop="poId">
                  <el-input v-model="form.poId" style="width: 100%" placeholder="关联采购订单" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="来源渠道" prop="issueChannel">
                  <el-select v-model="form.issueChannel" placeholder="请选择来源渠道" style="width: 100%">
                    <el-option v-for="dict in invoice_issue_channel" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="销方税号" prop="sellerTaxId">
                  <el-input v-model="form.sellerTaxId" placeholder="销售方纳税人识别号" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="销方抬头" prop="sellerTitle">
                  <el-input v-model="form.sellerTitle" placeholder="销售方发票抬头" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="销方开户行" prop="sellerBankName">
                  <el-input v-model="form.sellerBankName" placeholder="销售方开户行" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="销方账号" prop="sellerBankAccount">
                  <el-input v-model="form.sellerBankAccount" placeholder="销售方银行账号" />
                </el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="销方地址电话" prop="sellerAddressPhone">
                  <el-input v-model="form.sellerAddressPhone" placeholder="销售方地址、电话" />
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
                    <el-option v-for="dict in pu_invoice_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
              <el-table-column label="采购订单行ID" width="140">
                <template #default="scope">
                  <el-input v-model="scope.row.poLineId" size="small" style="width: 100%" />
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
    <el-dialog title="进项发票核销" v-model="verifyOpen" width="420px" append-to-body>
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
        <el-button type="primary" @click="submitVerify">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 三单匹配结果对话框 -->
    <el-dialog title="三单匹配结果(PO + 收货 + 发票)" v-model="matchOpen" width="560px" append-to-body>
      <el-result :icon="matchResult.passed ? 'success' : 'warning'" :title="matchResult.passed ? '匹配通过' : '存在差异'">
        <template #sub-title>
          <span>{{ matchResult.summary }}</span>
        </template>
      </el-result>
      <div v-if="matchResult.mismatchCount > 0" style="max-height: 240px; overflow: auto; margin-top: 8px">
        <el-alert v-for="(msg, i) in matchResult.mismatches" :key="i" :title="msg" type="warning" :closable="false" style="margin-bottom: 6px" />
      </div>
      <template #footer>
        <el-button @click="matchOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 从采购订单生成草稿对话框 -->
    <el-dialog title="从采购订单生成发票草稿" v-model="genFromPoOpen" width="420px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="采购订单ID">
          <el-input v-model="genFromPoPoId" style="width: 100%" placeholder="请输入采购订单ID" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">按"已收货未开票"自动生成草稿,销售方四要素从供应商档案带入。</div>
      </el-form>
      <template #footer>
        <el-button @click="genFromPoOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitGenFromPo">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 供应商选择对话框 -->
    <el-dialog v-model="vendorDialog.visible" title="选择供应商" width="720px" append-to-body>
      <el-form :model="vendorDialog.query" :inline="true">
        <el-form-item label="编码">
          <el-input v-model="vendorDialog.query.vendorCode" clearable @keyup.enter="queryVendors" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="vendorDialog.query.vendorName" clearable @keyup.enter="queryVendors" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryVendors">搜索</el-button>
          <el-button icon="Refresh" @click="resetVendorQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="vendorDialog.list" height="360" @row-dblclick="selectVendor">
        <el-table-column label="供应商编码" prop="vendorCode" width="140" />
        <el-table-column label="供应商名称" prop="vendorName" show-overflow-tooltip />
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectVendor(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="vendorDialog.total > 0"
        :total="vendorDialog.total"
        v-model:page="vendorDialog.query.pageNum"
        v-model:limit="vendorDialog.query.pageSize"
        @pagination="queryVendors"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <el-dialog v-model="materialDialogVisible" title="选择物料" width="720px" append-to-body>
      <el-form :inline="true">
        <el-form-item label="编码">
          <el-input v-model="materialQuery.materialCode" clearable @keyup.enter="queryMaterials" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="materialQuery.materialName" clearable @keyup.enter="queryMaterials" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryMaterials">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="materialList" height="360">
        <el-table-column label="物料编码" prop="materialCode" width="140" />
        <el-table-column label="物料名称" prop="materialName" show-overflow-tooltip />
        <el-table-column label="规格" prop="materialSpec" show-overflow-tooltip />
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectMaterial(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="materialTotal > 0"
        :total="materialTotal"
        v-model:page="materialQuery.pageNum"
        v-model:limit="materialQuery.pageSize"
        @pagination="queryMaterials"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PuInvoice">
import {
  listInvoice,
  getInvoice,
  delInvoice,
  addInvoice,
  updateInvoice,
  receiveInvoice,
  matchInvoice,
  verifyInvoice,
  certifyInvoice,
  deductInvoice,
  cancelInvoice,
  redFlushInvoice,
  generateFromPo
} from '@/api/erp/pu/invoice';
import { listVendor } from '@/api/erp/bd/vendor';
import { listMaterial } from '@/api/erp/bd/material';
import { InvoiceVO, InvoiceForm, InvoiceLineForm, MatchResult } from '@/api/erp/pu/invoice/types';

const { proxy } = getCurrentInstance();
const { pu_invoice_type, pu_invoice_status, pu_invoice_cert_status, invoice_issue_channel } = toRefs<any>(
  proxy.useDict('pu_invoice_type', 'pu_invoice_status', 'pu_invoice_cert_status', 'invoice_issue_channel')
);

// 列表数据
const dataList = ref<InvoiceVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<number[]>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);
const activeTab = ref('basic');

// 明细行数据
const lineList = ref<InvoiceLineForm[]>([]);
const checkedLines = ref<number[]>([]);
const currentLineIndex = ref(-1);

// 物料选择对话框
const materialDialogVisible = ref(false);
const materialList = ref<any[]>([]);
const materialTotal = ref(0);
const materialQuery = ref({ materialCode: '', materialName: '', pageNum: 1, pageSize: 20 });

// 供应商选择对话框
const vendorDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { vendorCode: '', vendorName: '', pageNum: 1, pageSize: 10 } as any
});

const data = reactive({
  form: {} as InvoiceForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    invoiceNo: undefined,
    vendorName: undefined,
    status: undefined,
    certStatus: undefined
  },
  rules: {
    invoiceNo: [{ required: true, message: '发票号不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询列表 */
function getList() {
  loading.value = true;
  listInvoice(queryParams.value).then((response) => {
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
    invoiceCode: '',
    invoiceType: 'VAT_SPECIAL',
    vendorId: undefined,
    vendorName: '',
    sellerTaxId: '',
    sellerTitle: '',
    sellerBankName: '',
    sellerBankAccount: '',
    sellerAddressPhone: '',
    poId: undefined,
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
    puInvoiceLineList: []
  };
  lineList.value = [];
  activeTab.value = 'basic';
  proxy.resetForm('formRef');
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection: InvoiceVO[]) {
  ids.value = selection.map((item) => item.invoiceId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/** 新增 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '登记采购进项发票';
}

/** 修改 */
function handleUpdate(row?: InvoiceVO) {
  reset();
  isViewMode.value = false;
  const invoiceId = row?.invoiceId || ids.value[0];
  getInvoice(invoiceId).then((response) => {
    form.value = response.data;
    lineList.value = response.data.puInvoiceLineList || [];
    open.value = true;
    title.value = '修改采购进项发票';
  });
}

/** 查看 */
function handleView(row: InvoiceVO) {
  reset();
  isViewMode.value = true;
  getInvoice(row.invoiceId).then((response) => {
    form.value = response.data;
    lineList.value = response.data.puInvoiceLineList || [];
    open.value = true;
    title.value = '查看采购进项发票';
  });
}

/** 提交表单 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (valid) {
      form.value.puInvoiceLineList = lineList.value;
      if (form.value.invoiceId != null) {
        updateInvoice(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addInvoice(form.value).then(() => {
          proxy.$modal.msgSuccess('登记成功');
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
  proxy.$modal
    .confirm('是否确认删除采购进项发票编号为"' + invoiceIds + '"的数据项?')
    .then(function () {
      return delInvoice(invoiceIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出 */
function handleExport() {
  proxy.download('erp/pu/invoice/export', { ...queryParams.value }, `pu_invoice_${new Date().getTime()}.xlsx`);
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
    poLineId: undefined,
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
  row.lineAmount = Number(((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2));
  calcLineTax(row);
}

/** 计算行税额 */
function calcLineTax(row: InvoiceLineForm) {
  row.taxAmount = Number((((row.lineAmount || 0) * (row.taxRate || 0)) / 100).toFixed(2));
  row.totalAmount = Number(((row.lineAmount || 0) + (row.taxAmount || 0)).toFixed(2));
  calcTotalAmount();
}

/** 计算总金额 */
function calcTotalAmount() {
  form.value.amount = Number(lineList.value.reduce((sum, row) => sum + (row.lineAmount || 0), 0).toFixed(2));
  form.value.taxAmount = Number(lineList.value.reduce((sum, row) => sum + (row.taxAmount || 0), 0).toFixed(2));
  form.value.totalAmount = Number(((form.value.amount || 0) + (form.value.taxAmount || 0)).toFixed(2));
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
    materialTotal.value = res.total || 0;
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

/** 打开供应商选择对话框 */
function openVendorDialog() {
  vendorDialog.visible = true;
  queryVendors();
}

/** 查询供应商 */
function queryVendors() {
  listVendor(vendorDialog.query).then((res: any) => {
    vendorDialog.list = res.rows || [];
    vendorDialog.total = res.total || 0;
  });
}

/** 重置供应商查询 */
function resetVendorQuery() {
  vendorDialog.query = { vendorCode: '', vendorName: '', pageNum: 1, pageSize: 10 };
  queryVendors();
}

/** 选择供应商(同时回填销售方四要素) */
function selectVendor(row: any) {
  form.value.vendorId = row.vendorId;
  form.value.vendorName = row.vendorName;
  form.value.sellerTaxId = row.taxId || '';
  form.value.sellerTitle = row.invoiceTitle || row.vendorName || '';
  form.value.sellerBankName = row.invoiceBankName || '';
  form.value.sellerBankAccount = row.bankAccount || '';
  form.value.sellerAddressPhone = row.invoiceAddressPhone || '';
  vendorDialog.visible = false;
}

// ===== 业务动作 =====

/** 收票确认 */
function handleReceive(row: InvoiceVO) {
  proxy.$modal
    .confirm('确认收票发票【' + row.invoiceNo + '】?(草稿 -> 已收)')
    .then(() => receiveInvoice(row.invoiceId))
    .then(() => {
      proxy.$modal.msgSuccess('收票成功');
      getList();
    })
    .catch(() => {});
}

// 三单匹配结果
const matchOpen = ref(false);
const matchResult = ref<MatchResult>({ passed: false, mismatchCount: 0, mismatches: [], summary: '' });

/** 三单匹配 */
function handleMatch(row: InvoiceVO) {
  matchInvoice(row.invoiceId).then((res: any) => {
    matchResult.value = res.data || res;
    matchOpen.value = true;
  });
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

/** 进项认证 */
function handleCertify(row: InvoiceVO) {
  proxy.$modal
    .confirm('确认认证发票【' + row.invoiceNo + '】?将记录认证日期与可抵扣税额。')
    .then(() => certifyInvoice(row.invoiceId))
    .then(() => {
      proxy.$modal.msgSuccess('认证成功');
      getList();
    })
    .catch(() => {});
}

/** 进项抵扣 */
function handleDeduct(row: InvoiceVO) {
  proxy.$modal
    .confirm('确认抵扣发票【' + row.invoiceNo + '】?(已认证 -> 已抵扣)')
    .then(() => deductInvoice(row.invoiceId))
    .then(() => {
      proxy.$modal.msgSuccess('抵扣成功');
      getList();
    })
    .catch(() => {});
}

/** 作废发票 */
function handleCancel(row: InvoiceVO) {
  proxy.$modal
    .confirm('确认作废发票【' + row.invoiceNo + '】?作废将回退采购订单行已开票数量。')
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
    .confirm('确认红冲发票【' + row.invoiceNo + '】?将生成红字负数发票并冲减原发票已核销金额(已抵扣则进项转出)。')
    .then(() => redFlushInvoice(row.invoiceId))
    .then(() => {
      proxy.$modal.msgSuccess('红冲成功');
      getList();
    })
    .catch(() => {});
}

// 从订单生成草稿
const genFromPoOpen = ref(false);
const genFromPoPoId = ref<number | undefined>(undefined);

/** 打开从订单生成对话框 */
function handleGenFromPo() {
  genFromPoPoId.value = undefined;
  genFromPoOpen.value = true;
}

/** 提交从订单生成 */
function submitGenFromPo() {
  if (!genFromPoPoId.value) {
    proxy.$modal.msgError('请输入采购订单ID');
    return;
  }
  generateFromPo(genFromPoPoId.value).then((res: any) => {
    const newId = res.data;
    if (newId) {
      proxy.$modal.msgSuccess('生成草稿成功,发票ID:' + newId);
      genFromPoOpen.value = false;
      getList();
    } else {
      proxy.$modal.msgWarning('未生成草稿(订单不存在或无可开票行)');
    }
  });
}

getList();
</script>
