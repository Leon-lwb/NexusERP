<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="合同编号" prop="contractNo">
            <el-input v-model="queryParams.contractNo" placeholder="请输入合同编号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="合同名称" prop="contractName">
            <el-input v-model="queryParams.contractName" placeholder="请输入合同名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="合同状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in pu_contract_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="合同类型" prop="contractType">
            <el-select v-model="queryParams.contractType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="dict in contract_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd(loadFlowOptions)" v-hasPermi="['pu:contract:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['pu:contract:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="contractList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="合同编号" align="center" prop="contractNo" />
      <el-table-column label="合同名称" align="center" prop="contractName" min-width="120" show-overflow-tooltip />
      <el-table-column label="供应商" align="center" prop="vendorName" min-width="120" show-overflow-tooltip />
      <el-table-column label="合同类型" align="center" prop="contractType">
        <template #default="scope">
          <dict-tag :options="contract_type" :value="scope.row.contractType" />
        </template>
      </el-table-column>
      <el-table-column label="合同金额" align="right" prop="totalAmount" />
      <el-table-column label="签订日期" align="center" prop="signDate">
        <template #default="scope">
          <span>{{ scope.row.signDate ? scope.row.signDate.substring(0, 10) : '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="pu_contract_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <!-- 正文签署状态：后端批量补充自 es_sign_document；无正文时为空，显示占位符 -->
      <el-table-column label="签署状态" align="center" prop="docSignStatus" width="110">
        <template #default="scope">
          <el-tag v-if="scope.row.docSignStatus === '2'" type="success">已签</el-tag>
          <el-tag v-else-if="scope.row.docSignStatus === '1'" type="warning">部分签</el-tag>
          <el-tag v-else-if="scope.row.docSignStatus === '0'" type="info">有正文未签</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="300" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row, loadFlowOptions)" v-hasPermi="['pu:contract:query']"
            >查看</el-button
          >
          <!-- 详情是独立路由（router/index.ts 的 /erp/pu/contract/detail 声明 permissions: ['pu:contract:query']），
               缺该权限时路由不会注册，点进去只会落到 404，故按钮与路由同一个权限码 -->
          <el-button link type="primary" icon="Document" @click="handleDetail(scope.row)" v-hasPermi="['pu:contract:query']">详情</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row, loadFlowOptions)"
            v-if="scope.row.status === 'DRAFT' || scope.row.status === 'REJECTED'"
            v-hasPermi="['pu:contract:edit']"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['pu:contract:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="1200px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="合同编号" prop="contractNo">
              <el-input v-model="form.contractNo" placeholder="请输入合同编号" :disabled="!!form.contractId" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="合同名称" prop="contractName">
              <el-input v-model="form.contractName" placeholder="请输入合同名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorId">
              <el-input v-model="form.vendorName" placeholder="请选择供应商" readonly @click="openVendorDialog()">
                <template #append>
                  <el-button icon="Search" @click="openVendorDialog()" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="合同类型" prop="contractType">
              <el-select v-model="form.contractType" placeholder="请选择合同类型">
                <el-option v-for="dict in contract_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="签订日期" prop="signDate">
              <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" placeholder="选择签订日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择币种">
                <el-option label="CNY" value="CNY" />
                <el-option label="USD" value="USD" />
                <el-option label="EUR" value="EUR" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择生效日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失效日期" prop="expiryDate">
              <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择失效日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="付款条款" prop="paymentTerms">
              <el-input v-model="form.paymentTerms" placeholder="请输入付款条款" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select
                v-model="form.selectFlowId"
                clearable
                filterable
                :loading="loadingFlow"
                :disabled="isViewMode"
                placeholder="请选择审批流程"
                style="width: 100%"
              >
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 由明细行实时汇总，提交时随表单一并落库（后端同样按行金额重算，双保险） -->
            <el-form-item label="合同金额">
              <el-input :model-value="totalAmountSummary" disabled>
                <template #append>{{ form.currency || 'CNY' }}</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 明细行（物料经选择弹窗回填；税率默认 13%，税额/不含税金额随单价数量税率联动计算） -->
        <el-divider content-position="left">
          合同明细
          <span class="line-summary">
            价税合计 {{ totalAmountSummary }} · 税额合计 {{ taxAmountSummary }} · 不含税合计 {{ excludingTaxSummary }} {{ form.currency || 'CNY' }}
          </span>
        </el-divider>
        <el-table :data="form.puContractLineList" border style="width: 100%" show-overflow-tooltip>
          <el-table-column label="物料编码" min-width="120">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请选择" readonly>
                <template #append>
                  <el-button icon="Search" :disabled="isViewMode" @click="openMaterialDialog(scope.$index)" />
                </template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="120">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入" />
            </template>
          </el-table-column>
          <el-table-column label="规格" min-width="90">
            <template #default="scope">
              <el-input v-model="scope.row.materialSpec" placeholder="请输入" />
            </template>
          </el-table-column>
          <el-table-column label="单位" min-width="70">
            <template #default="scope">
              <el-input v-model="scope.row.unit" placeholder="请输入" />
            </template>
          </el-table-column>
          <el-table-column label="单价" min-width="100">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.unitPrice"
                :precision="4"
                :min="0"
                :controls="false"
                style="width: 100%"
                @change="calcLineAmount(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="数量" min-width="100">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.quantity"
                :precision="4"
                :min="0"
                :controls="false"
                style="width: 100%"
                @change="calcLineAmount(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="税率%" min-width="80">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.taxRate"
                :precision="2"
                :min="0"
                :max="100"
                :controls="false"
                style="width: 100%"
                @change="calcLineAmount(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="行金额(含税)" min-width="100">
            <template #default="scope">
              <span>{{ scope.row.lineAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="税额" min-width="90">
            <template #default="scope">
              <span>{{ scope.row.taxAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="不含税金额" min-width="100">
            <template #default="scope">
              <span>{{ scope.row.lineAmountExcludingTax }}</span>
            </template>
          </el-table-column>
          <el-table-column label="交货日期" min-width="130">
            <template #default="scope">
              <el-date-picker v-model="scope.row.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" fixed="right">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="removeLine(scope.$index)" />
            </template>
          </el-table-column>
        </el-table>
        <el-button class="mt-2" type="primary" plain icon="Plus" @click="addLine()">添加明细行</el-button>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 供应商选择对话框（与采购发票页同一套交互） -->
    <el-dialog v-model="vendorDialog.visible" title="选择供应商" width="640px" append-to-body>
      <el-form :model="vendorDialog.query" :inline="true">
        <el-form-item label="编码">
          <el-input v-model="vendorDialog.query.vendorCode" clearable @keyup.enter="queryVendors()" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="vendorDialog.query.vendorName" clearable @keyup.enter="queryVendors()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryVendors()">搜索</el-button>
          <el-button icon="Refresh" @click="resetVendorQuery()">重置</el-button>
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
    <!-- 物料选择弹窗（共享组件，与销售合同/采购订单同一套） -->
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />
  </div>
</template>

<script setup name="PuContract" lang="ts">
import { listContract, getContract, addContract, updateContract, delContract } from '@/api/erp/pu/contract';
import { listVendor } from '@/api/erp/bd/vendor';
import { getProcesss } from '@/api/workflow/process/index';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const formRef = ref<FormInstance>();
const router = useRouter();

// 合同类型/状态字典（contract_type 即采购合同类型字典，见 字典管理）
const { contract_type, pu_contract_status } = toRefs<any>(proxy.useDict('contract_type', 'pu_contract_status'));

// 查询参数
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  contractNo: undefined,
  contractName: undefined,
  vendorName: undefined,
  status: undefined,
  contractType: undefined
});

// 表单校验规则（与后端 BO 必填校验一致：合同编号/名称/供应商）
const rules = {
  contractNo: [{ required: true, message: '合同编号不能为空', trigger: 'blur' }],
  contractName: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }],
  vendorId: [{ required: true, message: '供应商不能为空', trigger: 'change' }]
};

// 审批流程选项（workflow 模块按 serviceName 绑定，与后端 submit 的 bean 名一致）
const flowOptions = ref<any[]>([]);
const loadingFlow = ref(false);

/** 加载绑定到 puContractService 的审批流程，仅有一个时自动选中 */
function loadFlowOptions() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'puContractService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      if (!form.value.selectFlowId && flowOptions.value.length === 1) {
        form.value.selectFlowId = flowOptions.value[0].id;
      }
    })
    .finally(() => {
      loadingFlow.value = false;
    });
}

const {
  loading,
  list: contractList,
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
  apis: { list: listContract, get: getContract, add: addContract, update: updateContract, del: delContract },
  idField: 'contractId',
  entityName: '采购合同',
  formRef,
  queryParams,
  initForm: () => ({
    contractId: undefined,
    contractNo: undefined,
    contractName: undefined,
    vendorId: undefined,
    vendorCode: undefined,
    vendorName: undefined,
    contractType: undefined,
    signDate: undefined,
    effectiveDate: undefined,
    expiryDate: undefined,
    totalAmount: undefined,
    currency: 'CNY',
    paymentTerms: undefined,
    status: 'DRAFT',
    selectFlowId: undefined,
    remark: undefined,
    puContractLineList: []
  }),
  beforeSubmit: (formData: any) => {
    // 三个金额随明细行汇总提交（后端还会按行金额重算，以服务端为准）
    formData.totalAmount = totalAmountSummary.value;
    formData.taxAmount = taxAmountSummary.value;
    formData.amountExcludingTax = excludingTaxSummary.value;
  },
  exportUrl: 'erp/pu/contract/export',
  exportFileName: 'pu_contract'
});

/** 价税合计 = Σ 行金额（4 位小数，与库表精度一致） */
const totalAmountSummary = computed(() => sumLines('lineAmount', 4));
/** 税额合计 = Σ 行税额（2 位小数） */
const taxAmountSummary = computed(() => sumLines('taxAmount', 2));
/** 不含税合计 = Σ 行不含税金额（2 位小数） */
const excludingTaxSummary = computed(() => sumLines('lineAmountExcludingTax', 2));

function sumLines(field: string, scale: number) {
  const sum = (form.value.puContractLineList ?? []).reduce((acc: number, row: any) => acc + (Number(row[field]) || 0), 0);
  return Number(sum.toFixed(scale));
}

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 详情：跳转合同详情页（合同基本信息 + 正文上传/签署/验签），与销售合同详情同一套 query 传参方式 */
function handleDetail(row: any) {
  router.push({ path: '/erp/pu/contract/detail', query: { contractId: String(row.contractId) } });
}

/** 添加明细行（税率默认 13%，与发票页口径一致） */
function addLine() {
  form.value.puContractLineList.push({
    lineId: undefined,
    contractId: undefined,
    materialId: undefined,
    materialCode: undefined,
    materialName: undefined,
    materialSpec: undefined,
    unit: undefined,
    unitPrice: 0,
    quantity: 0,
    taxRate: 13,
    lineAmount: 0,
    taxAmount: 0,
    lineAmountExcludingTax: 0,
    deliveryDate: undefined,
    sortOrder: form.value.puContractLineList.length + 1,
    remark: undefined
  });
}

/** 删除明细行 */
function removeLine(index: number) {
  form.value.puContractLineList.splice(index, 1);
}

/**
 * 计算行金额/税额/不含税金额（toFixed 截断浮点尾差，精度与库表及后端重算一致）：
 * 行金额(含税) = 单价×数量(4位)，税额 = 行金额×税率÷100(2位)，不含税 = 行金额−税额(2位)
 */
function calcLineAmount(row: any) {
  row.lineAmount = Number(((Number(row.unitPrice) || 0) * (Number(row.quantity) || 0)).toFixed(4));
  const tax = Number(((row.lineAmount * (Number(row.taxRate) || 0)) / 100).toFixed(2));
  row.taxAmount = row.taxRate == null ? undefined : tax;
  row.lineAmountExcludingTax = row.taxRate == null ? undefined : Number((row.lineAmount - tax).toFixed(2));
}

// ===== 物料选择 =====
const materialSelectDialogRef = ref();
const currentLineIndex = ref(-1);

/** 打开物料选择对话框 */
function openMaterialDialog(index: number) {
  currentLineIndex.value = index;
  materialSelectDialogRef.value?.open();
}

/** 物料选择回调：回填编码/名称/规格/基本单位 */
function handleMaterialSelected(row: any) {
  if (currentLineIndex.value < 0) return;
  const target = form.value.puContractLineList[currentLineIndex.value];
  if (!target) return;
  target.materialId = row.materialId;
  target.materialCode = row.materialCode;
  target.materialName = row.materialName;
  target.materialSpec = row.materialSpec;
  target.unit = row.baseUomName || '';
}

// ===== 供应商选择 =====
const vendorDialog = reactive({
  visible: false,
  query: { vendorCode: '', vendorName: '', pageNum: 1, pageSize: 10 } as Record<string, any>,
  list: [] as any[],
  total: 0
});

/** 打开供应商选择对话框 */
function openVendorDialog() {
  vendorDialog.visible = true;
  queryVendors();
}

/** 查询供应商 */
function queryVendors() {
  listVendor(vendorDialog.query as any).then((res: any) => {
    vendorDialog.list = res.rows || [];
    vendorDialog.total = res.total || 0;
  });
}

/** 重置供应商查询 */
function resetVendorQuery() {
  vendorDialog.query = { vendorCode: '', vendorName: '', pageNum: 1, pageSize: 10 };
  queryVendors();
}

/** 选择供应商：回填三要素 */
function selectVendor(row: any) {
  form.value.vendorId = row.vendorId;
  form.value.vendorCode = row.vendorCode || '';
  form.value.vendorName = row.vendorName;
  formRef.value?.validateField('vendorId').catch(() => {});
  vendorDialog.visible = false;
}

getList();
</script>

<style scoped>
.line-summary {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}
</style>
