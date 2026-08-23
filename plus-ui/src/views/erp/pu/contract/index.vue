<template>
  <div class="p-2">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="mb-2">
      <el-form :model="queryParams" ref="queryFormRef" label-width="100px" class="list-search">
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
            <el-form-item label="供应商" prop="vendorName">
              <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="合同状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="合同类型" prop="contractType">
              <el-select v-model="queryParams.contractType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option label="框架合同" value="FRAMEWORK" />
                <el-option label="现货合同" value="SPOT" />
                <el-option label="服务合同" value="SERVICE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" :offset="12" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 操作区域 -->
    <el-card shadow="never">
      <template #header>
        <el-row :gutter="10">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['pu:contract:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['pu:contract:export']">导出</el-button>
          </el-col>
        </el-row>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="contractList" show-overflow-tooltip>
        <el-table-column label="序号" type="index" align="center" />
        <el-table-column label="合同编号" align="center" prop="contractNo" />
        <el-table-column label="合同名称" align="center" prop="contractName" min-width="120" show-overflow-tooltip />
        <el-table-column label="供应商" align="center" prop="vendorName" min-width="120" show-overflow-tooltip />
        <!-- 技术债:合同类型为硬编码映射,暂无对应字典,待补字典后改 dict-tag -->
        <el-table-column label="合同类型" align="center" prop="contractType">
          <template #default="scope">
            <span v-if="scope.row.contractType === 'FRAMEWORK'">框架合同</span>
            <span v-else-if="scope.row.contractType === 'SPOT'">现货合同</span>
            <span v-else-if="scope.row.contractType === 'SERVICE'">服务合同</span>
            <span v-else>{{ scope.row.contractType }}</span>
          </template>
        </el-table-column>
        <el-table-column label="合同金额" align="right" prop="totalAmount" />
        <el-table-column label="签订日期" align="center" prop="signDate">
          <template #default="scope">
            <span>{{ scope.row.signDate ? scope.row.signDate.substring(0, 10) : '' }}</span>
          </template>
        </el-table-column>
        <!-- 技术债:合同状态为硬编码 el-tag,暂无对应字典,待补字典后改 dict-tag -->
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
            <el-button
              link
              type="primary"
              icon="Edit"
              @click="handleUpdate(scope.row)"
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

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/修改对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="1200px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="合同编号" prop="contractNo">
              <el-input v-model="form.contractNo" placeholder="系统自动生成" :disabled="!!form.contractId" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="合同名称" prop="contractName">
              <el-input v-model="form.contractName" placeholder="请输入合同名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorId">
              <el-input v-model="form.vendorName" placeholder="请选择供应商" readonly @click="openVendorDialog">
                <template #append>
                  <el-button icon="Search" @click="openVendorDialog" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="合同类型" prop="contractType">
              <el-select v-model="form.contractType" placeholder="请选择合同类型">
                <el-option label="框架合同" value="FRAMEWORK" />
                <el-option label="现货合同" value="SPOT" />
                <el-option label="服务合同" value="SERVICE" />
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
              <el-input v-model="form.selectFlowId" placeholder="请选择审批流程" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 明细行 -->
        <el-divider content-position="left">合同明细</el-divider>
        <el-table :data="form.puContractLineList" border style="width: 100%" show-overflow-tooltip>
          <el-table-column label="物料编码" min-width="130">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请输入" />
            </template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="130">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入" />
            </template>
          </el-table-column>
          <el-table-column label="规格" min-width="100">
            <template #default="scope">
              <el-input v-model="scope.row.materialSpec" placeholder="请输入" />
            </template>
          </el-table-column>
          <el-table-column label="单位" min-width="80">
            <template #default="scope">
              <el-input v-model="scope.row.unit" placeholder="请输入" />
            </template>
          </el-table-column>
          <el-table-column label="单价" min-width="110">
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
          <el-table-column label="数量" min-width="110">
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
          <el-table-column label="行金额" min-width="110">
            <template #default="scope">
              <span>{{ scope.row.lineAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="交货日期" min-width="140">
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
        <el-button class="mt-2" type="primary" plain icon="Plus" @click="addLine">添加明细行</el-button>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PuContract" lang="ts">
import { ref, reactive } from 'vue';
import { listContract, getContract, addContract, updateContract, delContract } from '@/api/erp/pu/contract';
import { ElMessage, ElMessageBox } from 'element-plus';

const loading = ref(false);
const total = ref(0);
const contractList = ref([]);
const dialogVisible = ref(false);
const dialogTitle = ref('');
const queryFormRef = ref();
const formRef = ref();

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVING', label: '审批中' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'EXECUTING', label: '执行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'TERMINATED', label: '已终止' },
  { value: 'REJECTED', label: '已驳回' }
];

const queryParams = reactive({
  contractNo: undefined,
  contractName: undefined,
  vendorName: undefined,
  status: undefined,
  contractType: undefined,
  pageNum: 1,
  pageSize: 10
});

const initForm = () => ({
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
});

const form = ref(initForm());

const rules = {
  contractName: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }]
};

const getStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' => {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    DRAFT: 'info',
    APPROVING: 'warning',
    APPROVED: 'success',
    // EXECUTING 未配置标签类型，走下方兜底 'info'（原 '' 同样会被 || 'info' 兜底）
    COMPLETED: 'success',
    TERMINATED: 'danger',
    REJECTED: 'danger'
  };
  return map[status] || 'info';
};

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    APPROVING: '审批中',
    APPROVED: '已批准',
    EXECUTING: '执行中',
    COMPLETED: '已完成',
    TERMINATED: '已终止',
    REJECTED: '已驳回'
  };
  return map[status] || status;
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listContract(queryParams);
    contractList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};

const handleAdd = () => {
  form.value = initForm();
  dialogTitle.value = '新增采购合同';
  dialogVisible.value = true;
};

const handleUpdate = async (row: any) => {
  const res = await getContract(row.contractId);
  form.value = res.data;
  dialogTitle.value = '修改采购合同';
  dialogVisible.value = true;
};

const handleView = async (row: any) => {
  const res = await getContract(row.contractId);
  form.value = res.data;
  dialogTitle.value = '查看采购合同';
  dialogVisible.value = true;
};

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('是否确认删除合同【' + row.contractNo + '】？', '提示');
  await delContract([row.contractId]);
  ElMessage.success('删除成功');
  getList();
};

const handleExport = () => {
  // Export logic
};

const submitForm = async () => {
  await formRef.value?.validate();
  if (form.value.contractId) {
    await updateContract(form.value);
    ElMessage.success('修改成功');
  } else {
    await addContract(form.value);
    ElMessage.success('新增成功');
  }
  dialogVisible.value = false;
  getList();
};

const addLine = () => {
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
    lineAmount: 0,
    deliveryDate: undefined,
    sortOrder: form.value.puContractLineList.length + 1,
    remark: undefined
  });
};

const removeLine = (index: number) => {
  form.value.puContractLineList.splice(index, 1);
};

const calcLineAmount = (row: any) => {
  row.lineAmount = (row.unitPrice || 0) * (row.quantity || 0);
};

const openVendorDialog = () => {
  // 供应商选择弹窗逻辑（可复用现有组件）
  ElMessage.info('供应商选择弹窗待集成');
};

getList();
</script>
