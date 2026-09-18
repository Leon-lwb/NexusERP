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
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['pu:contract:add']">新增</el-button>
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
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <!-- 详情是独立路由（router/index.ts 的 /erp/pu/contract/detail 声明 permissions: ['pu:contract:query']），
               缺该权限时路由不会注册，点进去只会落到 404，故按钮与路由同一个权限码 -->
          <el-button link type="primary" icon="Document" @click="handleDetail(scope.row)" v-hasPermi="['pu:contract:query']">详情</el-button>
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

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改对话框 -->
    <el-dialog :title="title" v-model="open" width="1200px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
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
        <el-button class="mt-2" type="primary" plain icon="Plus" @click="addLine()">添加明细行</el-button>
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

<script setup name="PuContract" lang="ts">
import { listContract, getContract, addContract, updateContract, delContract } from '@/api/erp/pu/contract';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const formRef = ref<FormInstance>();
const router = useRouter();

// 合同状态选项（暂无对应字典，硬编码）
const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVING', label: '审批中' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'EXECUTING', label: '执行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'TERMINATED', label: '已终止' },
  { value: 'REJECTED', label: '已驳回' }
];

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

// 表单校验规则
const rules = {
  contractName: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }]
};

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
  exportUrl: 'erp/pu/contract/export',
  exportFileName: 'pu_contract'
});

/** 获取状态标签类型 */
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

/** 获取状态标签文本 */
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

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 详情：跳转合同详情页（合同基本信息 + 正文上传/签署/验签），与销售合同详情同一套 query 传参方式 */
function handleDetail(row: any) {
  router.push({ path: '/erp/pu/contract/detail', query: { contractId: String(row.contractId) } });
}

/** 添加明细行 */
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
    lineAmount: 0,
    deliveryDate: undefined,
    sortOrder: form.value.puContractLineList.length + 1,
    remark: undefined
  });
}

/** 删除明细行 */
function removeLine(index: number) {
  form.value.puContractLineList.splice(index, 1);
}

/** 计算行金额 */
function calcLineAmount(row: any) {
  row.lineAmount = (row.unitPrice || 0) * (row.quantity || 0);
}

/** 打开供应商选择弹窗（待集成） */
function openVendorDialog() {
  // 供应商选择弹窗逻辑（可复用现有组件）
  proxy.$modal.msgInfo('供应商选择弹窗待集成');
}

getList();
</script>
