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
          <!-- 技术债:合同状态暂用硬编码 el-option,待补字典后改为 useDict + dict-tag -->
          <el-form-item label="合同状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="审批中" value="APPROVING" />
              <el-option label="已生效" value="EFFECTIVE" />
              <el-option label="已到期" value="EXPIRED" />
              <el-option label="已终止" value="TERMINATED" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['so:contract:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['so:contract:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['so:contract:remove']">删除</el-button>
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
      <el-table-column label="合同类型" align="center" prop="contractType" />
      <el-table-column label="合同金额" align="center" prop="totalAmount" />
      <el-table-column label="币种" align="center" prop="currency" />
      <el-table-column label="签订日期" align="center" prop="signDate" />
      <el-table-column label="生效日期" align="center" prop="effectiveDate" />
      <el-table-column label="失效日期" align="center" prop="expiryDate" />
      <!-- 技术债:状态暂用硬编码 el-tag+map,待补字典后改为 dict-tag -->
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:contract:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:contract:remove']">删除</el-button>
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
                    <el-option label="销售合同" value="SALES" />
                    <el-option label="框架协议" value="FRAMEWORK" />
                    <el-option label="服务合同" value="SERVICE" />
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
                <el-form-item label="合同金额" prop="totalAmount">
                  <el-input-number v-model="form.totalAmount" :precision="2" :min="0" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="付款条款" prop="paymentTerms">
                  <el-input v-model="form.paymentTerms" placeholder="请输入付款条款" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="状态" prop="status">
                  <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                    <el-option label="草稿" value="DRAFT" />
                    <el-option label="审批中" value="APPROVING" />
                    <el-option label="已生效" value="EFFECTIVE" />
                    <el-option label="已到期" value="EXPIRED" />
                    <el-option label="已终止" value="TERMINATED" />
                  </el-select>
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
import { listContract, getContract, delContract, addContract, updateContract } from '@/api/erp/so/contract';
import { ContractVO, ContractForm, ContractLineForm } from '@/api/erp/so/contract/types';
import ClientSelectDialog from '@/views/erp/so/components/ClientSelectDialog.vue';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';

const { proxy } = getCurrentInstance();

// 客户选择弹窗引用
const clientSelectDialogRef = ref();

// 列表数据
const dataList = ref<ContractVO[]>([]);
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
const lineList = ref<ContractLineForm[]>([]);
const checkedLines = ref<number[]>([]);
const currentLineIndex = ref(-1);
const materialSelectDialogRef = ref();

const data = reactive({
  form: {} as ContractForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    contractNo: undefined,
    contractName: undefined,
    clientName: undefined,
    status: undefined
  },
  rules: {
    contractNo: [{ required: true, message: '合同编号不能为空', trigger: 'blur' }],
    contractName: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 获取状态标签类型 */
function getStatusType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    APPROVING: 'warning',
    EFFECTIVE: 'success',
    EXPIRED: 'danger',
    TERMINATED: 'danger'
  };
  return map[status] || 'info';
}

/** 获取状态标签文本 */
function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    APPROVING: '审批中',
    EFFECTIVE: '已生效',
    EXPIRED: '已到期',
    TERMINATED: '已终止'
  };
  return map[status] || status;
}

/** 查询列表 */
function getList() {
  loading.value = true;
  listContract(queryParams.value).then((response) => {
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
function handleSelectionChange(selection: ContractVO[]) {
  ids.value = selection.map((item) => item.contractId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/** 新增 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加销售合同';
}

/** 修改 */
function handleUpdate(row?: ContractVO) {
  reset();
  isViewMode.value = false;
  const contractId = row?.contractId || ids.value[0];
  getContract(contractId).then((response) => {
    form.value = response.data;
    lineList.value = response.data.soContractLineList || [];
    open.value = true;
    title.value = '修改销售合同';
  });
}

/** 查看 */
function handleView(row: ContractVO) {
  reset();
  isViewMode.value = true;
  getContract(row.contractId).then((response) => {
    form.value = response.data;
    lineList.value = response.data.soContractLineList || [];
    open.value = true;
    title.value = '查看销售合同';
  });
}

/** 提交表单 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (valid) {
      form.value.soContractLineList = lineList.value;
      if (form.value.contractId != null) {
        updateContract(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addContract(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除 */
function handleDelete(row?: ContractVO) {
  const contractIds = row?.contractId || ids.value;
  proxy.$modal
    .confirm('是否确认删除销售合同编号为"' + contractIds + '"的数据项？')
    .then(function () {
      return delContract(contractIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出 */
function handleExport() {
  proxy.download('erp/so/contract/export', { ...queryParams.value }, `contract_${new Date().getTime()}.xlsx`);
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

/** 计算行金额 */
function calcLineAmount(row: ContractLineForm) {
  row.lineAmount = (row.quantity || 0) * (row.unitPrice || 0);
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

getList();
</script>
