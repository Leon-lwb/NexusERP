<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="合同编号" prop="contractNo">
            <el-input v-model="queryParams.contractNo" placeholder="请输入合同编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="合同名称" prop="contractName">
            <el-input v-model="queryParams.contractName" placeholder="请输入合同名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户名称" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="合同状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in so_contract_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd(loadFlowOptions)" v-hasPermi="['so:contract:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate(undefined, loadFlowOptions)"
          v-hasPermi="['so:contract:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['so:contract:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['so:contract:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="合同编号" align="center" prop="contractNo" />
      <el-table-column label="合同名称" align="center" prop="contractName" min-width="120" show-overflow-tooltip />
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="合同类型" align="center" prop="contractType">
        <template #default="scope">
          <dict-tag :options="so_contract_type" :value="scope.row.contractType" />
        </template>
      </el-table-column>
      <el-table-column label="合同金额" align="center" prop="totalAmount" />
      <el-table-column label="币种" align="center" prop="currency" />
      <el-table-column label="签订日期" align="center" prop="signDate" />
      <el-table-column label="生效日期" align="center" prop="effectiveDate" />
      <el-table-column label="失效日期" align="center" prop="expiryDate" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_contract_status" :value="scope.row.status" />
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
          <el-button link type="primary" icon="View" @click="handleView(scope.row, loadFlowOptions)" v-hasPermi="['so:contract:query']"
            >查看</el-button
          >
          <!-- 详情是独立路由（router/index.ts 的 /erp/so/contract/detail 声明 permissions: ['so:contract:query']），
               缺该权限时路由不会注册，点进去只会落到 404，故按钮与路由同一个权限码 -->
          <el-button link type="primary" icon="Document" @click="handleDetail(scope.row)" v-hasPermi="['so:contract:query']">详情</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row, loadFlowOptions)" v-hasPermi="['so:contract:edit']"
            >修改</el-button
          >
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:contract:remove']">删除</el-button>
          <!-- 终止：仅已生效的合同可终止（与后端 SoContractServiceImpl.terminate 的状态门禁一致） -->
          <el-button
            link
            type="danger"
            icon="CircleClose"
            @click="handleTerminate(scope.row)"
            v-if="scope.row.status === 'EFFECTIVE'"
            v-hasPermi="['so:contract:edit']"
            >终止</el-button
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
                <el-form-item label="合同编号" prop="contractNo">
                  <el-input v-model="form.contractNo" placeholder="请输入合同编号" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="合同名称" prop="contractName">
                  <el-input v-model="form.contractName" placeholder="请输入合同名称" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="合同类型" prop="contractType">
                  <el-select v-model="form.contractType" placeholder="请选择合同类型" style="width: 100%">
                    <el-option v-for="dict in so_contract_type" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="客户" prop="clientId">
                  <el-input v-model="form.clientName" readonly>
                    <template #append>
                      <el-button icon="Search" @click="openClientDialog" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="销售代表" prop="salesRepName">
                  <el-input v-model="form.salesRepName" placeholder="请输入销售代表" />
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
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="签订日期" prop="signDate">
                  <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择签订日期" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="生效日期" prop="effectiveDate">
                  <el-date-picker
                    v-model="form.effectiveDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="请选择生效日期"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="失效日期" prop="expiryDate">
                  <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择失效日期" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <!-- 状态由服务端控制（新增即草稿、审批流转），表单不再提供状态选择 -->
                <el-form-item label="合同金额">
                  <el-input :model-value="totalAmountSummary" disabled>
                    <template #append>{{ form.currency || 'CNY' }}</template>
                  </el-input>
                </el-form-item>
              </el-col>
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
                <el-form-item label="付款条款" prop="paymentTerms">
                  <el-input v-model="form.paymentTerms" placeholder="请输入付款条款" />
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

          <el-tab-pane label="合同明细" name="line">
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
              <el-table-column label="税率%" width="90">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.taxRate"
                    size="small"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    :precision="2"
                    @change="calcLineAmount(scope.row)"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="行金额(含税)" width="120">
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
              <el-table-column label="税额" width="100">
                <template #default="scope">
                  <span>{{ scope.row.taxAmount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="交货日期" width="160">
                <template #default="scope">
                  <el-date-picker v-model="scope.row.deliveryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
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

    <!-- 客户选择弹窗 -->
    <ClientSelectDialog ref="clientSelectDialogRef" @confirm="handleClientConfirm" />
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="SoContract">
import { listContract, getContract, delContract, addContract, updateContract, terminateContract } from '@/api/erp/so/contract';
import { ContractLineForm } from '@/api/erp/so/contract/types';
import { getProcesss } from '@/api/workflow/process/index';
import ClientSelectDialog from '@/views/erp/so/components/ClientSelectDialog.vue';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const formRef = ref<FormInstance>();
const router = useRouter();

// 合同类型/状态字典（2026-09-18 新增，见 script/sql/update/2026-09-18-contract-dict.sql）
const { so_contract_type, so_contract_status } = toRefs<any>(proxy.useDict('so_contract_type', 'so_contract_status'));

// 客户选择弹窗引用
const clientSelectDialogRef = ref();

// 明细行数据
const lineList = ref<ContractLineForm[]>([]);
const checkedLines = ref<number[]>([]);
const currentLineIndex = ref(-1);
const materialSelectDialogRef = ref();
const activeTab = ref('basic');

// 审批流程选项（workflow 模块按 serviceName 绑定，与后端 submit 的 bean 名一致）
const flowOptions = ref<any[]>([]);
const loadingFlow = ref(false);

/** 加载绑定到 soContractService 的审批流程，仅有一个时自动选中 */
function loadFlowOptions() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'soContractService' })
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

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  contractNo: undefined,
  contractName: undefined,
  clientName: undefined,
  status: undefined
});

// 表单校验规则（与后端 BO 必填校验一致：合同编号/名称/客户）
const rules = {
  contractNo: [{ required: true, message: '合同编号不能为空', trigger: 'blur' }],
  contractName: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }],
  clientId: [{ required: true, message: '客户不能为空', trigger: 'change' }]
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
  apis: { list: listContract, get: getContract, add: addContract, update: updateContract, del: delContract },
  idField: 'contractId',
  entityName: '销售合同',
  formRef,
  queryParams,
  initForm: () => ({
    contractId: undefined,
    contractNo: '',
    contractName: '',
    contractType: 'SALES',
    clientId: undefined,
    clientName: '',
    signDate: undefined,
    effectiveDate: undefined,
    expiryDate: undefined,
    totalAmount: 0,
    currency: 'CNY',
    paymentTerms: '',
    salesRepId: undefined,
    salesRepName: '',
    status: 'DRAFT',
    selectFlowId: undefined,
    remark: '',
    soContractLineList: []
  }),
  exportUrl: 'erp/so/contract/export',
  exportFileName: 'contract',
  onReset: () => {
    lineList.value = [];
    activeTab.value = 'basic';
  },
  beforeSubmit: () => {
    form.value.soContractLineList = lineList.value;
    // 合同金额随明细行汇总提交（后端还会按行金额重算，以服务端为准）
    form.value.totalAmount = totalAmountSummary.value;
  },
  afterLoad: (data: any) => {
    lineList.value = data.soContractLineList || [];
  }
});

/** 合同金额 = Σ 行金额（2 位小数，与库表精度一致） */
const totalAmountSummary = computed(() => {
  const sum = lineList.value.reduce((acc: number, row: any) => acc + (Number(row.lineAmount) || 0), 0);
  return Number(sum.toFixed(2));
});

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 详情：跳转合同详情页（合同基本信息 + 正文上传/签署/验签），与委外订单表单同一套 query 传参方式 */
function handleDetail(row: any) {
  router.push({ path: '/erp/so/contract/detail', query: { contractId: String(row.contractId) } });
}

/** 终止合同（仅已生效可终止；后端用条件更新做 CAS，并发变更会返回业务异常） */
function handleTerminate(row: any) {
  proxy.$modal
    .prompt('确认终止合同【' + row.contractNo + '】？终止后不可再提交审批或修改。请填写终止原因：', '终止合同', {
      confirmButtonText: '确定终止',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入终止原因',
      inputValidator: (value: string) => (value && value.trim() ? true : '终止原因不能为空')
    })
    .then(({ value }: { value: string }) => terminateContract(row.contractId, value))
    .then(() => {
      proxy.$modal.msgSuccess('终止成功');
      getList();
    })
    .catch(() => {});
}

/** 添加明细行（税率默认 13%，与发票页口径一致） */
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
    taxRate: 13,
    lineAmount: 0,
    taxAmount: 0,
    deliveryDate: undefined,
    remark: ''
  });
}

/** 删除明细行 */
function handleDeleteLine() {
  lineList.value = lineList.value.filter((_, i) => !checkedLines.value.includes(i));
}

/** 明细行选择变化 */
function handleLineSelectionChange(rows: ContractLineForm[]) {
  checkedLines.value = rows.map((r: ContractLineForm) => lineList.value.indexOf(r));
}

/**
 * 计算行金额/税额（toFixed 截断浮点尾差，精度与库表及后端重算一致）：
 * 行金额（**不含税**）= 单价×数量(2位)、税额 = 行金额×税率÷100(2位)
 *
 * 注：原注释误写为「行金额(含税)」—— 若真为含税，再加税额就是重复计税。
 * 实际口径与订单侧 orderheader/form.vue 的 calcLine 一致：行金额不含税，
 * 税额在行金额（订单侧还需再扣行折扣）基础上按税率计取。
 * 决策 Q3：税率为**百分数**，`13` 表示 13%。
 */
function calcLineAmount(row: ContractLineForm) {
  row.lineAmount = Number(((Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)).toFixed(2));
  row.taxAmount = row.taxRate == null ? undefined : Number(((row.lineAmount * (Number(row.taxRate) || 0)) / 100).toFixed(2));
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
  formRef.value?.validateField('clientId').catch(() => {});
}

getList();
</script>
