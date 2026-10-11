<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="申请单号" prop="requisitionNo">
            <el-input v-model="queryParams.requisitionNo" placeholder="请输入申请单号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="申请名称" prop="requisitionName">
            <el-input v-model="queryParams.requisitionName" placeholder="请输入申请名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in pu_purchase_requisition_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['pu:requisition:add']"> 可信来源创建 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['pu:requisition:edit']"> 修改 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['pu:requisition:remove']">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['pu:requisition:query']"> 查看 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['pu:requisition:export']"> 导出 </el-button>
      </el-col>
      <!--
      <el-col :span="1.5">
        <el-button type="success" plain icon="Promotion" :disabled="single" @click="handleSubmit()" v-hasPermi="['pu:requisition:edit']">
          提交
        </el-button>
      </el-col>
      -->
      <!--
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Check" :disabled="single" @click="handleApprove()" v-hasPermi="['pu:requisition:edit']"> 审批 </el-button>
      </el-col>
      -->
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Position"
          :disabled="single || selectedProtected"
          @click="handleConvert()"
          v-hasPermi="['pu:requisition:edit']"
        >
          转单
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['pu:requisition:query']" type="primary" plain icon="Connection" :disabled="single" @click="handleCertifiedWorkflow()">
          认证生命周期
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleGenerateFromLowStock()" v-hasPermi="['pu:requisition:add']">
          低库存生成（已暂停）
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="requisitionList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" width="60" />
      <el-table-column label="申请单号" prop="requisitionNo" min-width="140" />
      <el-table-column label="申请名称" prop="requisitionName" min-width="120" show-overflow-tooltip />
      <el-table-column label="申请日期" prop="requisitionDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requisitionDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="需求日期" prop="requiredDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requiredDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="pu_purchase_requisition_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    <el-alert type="warning" show-icon :closable="false" title="旧低库存生成已暂停：须具备完整可信来源，不会把无库存记录当成 0 或用普通 BO 新增。" />
    <CertifiedPurchaseDialog ref="certifiedPurchaseDialogRef" @created="getList" />
    <CertifiedRequisitionWorkflowDialog ref="certifiedWorkflowDialogRef" @changed="getList" />
  </div>
</template>

<script setup lang="ts" name="PurchaseRequisition">
// 导入采购请购单相关的API函数
import {
  listPurchaseRequisition,
  getPurchaseRequisition,
  updatePurchaseRequisition,
  delPurchaseRequisition,
  convertPurchaseRequisition
} from '@/api/erp/pu/requisition';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import CertifiedPurchaseDialog from './CertifiedPurchaseDialog.vue';
import CertifiedRequisitionWorkflowDialog from './CertifiedRequisitionWorkflowDialog.vue';
import { isProtectedRequisitionHint, normalizeRequisitionId } from './certifiedWorkflow';

const certifiedPurchaseDialogRef = ref<InstanceType<typeof CertifiedPurchaseDialog>>();
const certifiedWorkflowDialogRef = ref<InstanceType<typeof CertifiedRequisitionWorkflowDialog>>();

const router = useRouter();
// 获取当前实例并获取字典数据
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { pu_purchase_requisition_status } = toRefs<any>(proxy.useDict('pu_purchase_requisition_status'));

// 查询参数
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  requisitionNo: null,
  requisitionName: null,
  status: null
});

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: requisitionList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: {
    list: listPurchaseRequisition,
    get: getPurchaseRequisition,
    update: updatePurchaseRequisition,
    del: delPurchaseRequisition
  },
  idField: 'requisitionId',
  entityName: '采购申请',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/pu/requisition/export',
  exportFileName: 'pu_requisition'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  certifiedPurchaseDialogRef.value?.open();
}

/** 修改按钮操作 */
function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = requisitionList.value.find((item: any) => item.requisitionId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  if (isProtectedRequisitionHint(row)) {
    proxy.$modal.msgWarning('认证来源不可普通编辑，请使用认证生命周期入口；当前仅打开只读详情');
    router.push({ path: '/erp/pu/requisition/form', query: { id: row.requisitionId, action: 'view' } });
    return;
  }
  router.push({ path: '/erp/pu/requisition/form', query: { id: row.requisitionId, action: 'edit' } });
}

/** 查看按钮操作 */
function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = requisitionList.value.find((item: any) => item.requisitionId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/pu/requisition/form', query: { id: row.requisitionId, action: 'view' } });
}

/** 只比较精确字符串身份，不把雪花ID经Number或宽松等号截断。 */
const selectedRequisition = computed(() => {
  if (ids.value.length !== 1) return undefined;
  try {
    const selectedId = normalizeRequisitionId(ids.value[0]);
    return requisitionList.value.find((row: any) => {
      try {
        return normalizeRequisitionId(row.requisitionId) === selectedId;
      } catch {
        return false;
      }
    });
  } catch {
    return undefined;
  }
});
/** 来源未知或有认证标记时禁止旧写提示；实际来源与权限仍由后端负责。 */
const selectedProtected = computed(() => isProtectedRequisitionHint(selectedRequisition.value));

/** 专属面板只接收身份，内部重新核验主体、原生详情与版本，不传列表数量或认证结论。 */
function handleCertifiedWorkflow() {
  const selected = selectedRequisition.value;
  if (!selected) {
    proxy.$modal.msgWarning('请选择身份明确的一张采购申请');
    return;
  }
  certifiedWorkflowDialogRef.value?.open(normalizeRequisitionId(selected.requisitionId));
}

/**
 * 转单按钮操作 - 将请购单转换为采购订单
 */
function handleConvert() {
  if (selectedProtected.value) {
    proxy.$modal.msgWarning('认证来源或保护事实未知，禁止旧转单；请使用认证生命周期入口');
    return;
  }
  convertPurchaseRequisition(ids.value[0]).then(() => {
    proxy.$modal.msgSuccess('转单成功');
    getList();
  });
}

/**
 * 低库存旧通道保持暂停，不通过普通 BO 新增
 */
function handleGenerateFromLowStock() {
  proxy.$modal.msgWarning('旧低库存生成已暂停，请使用可信来源创建并先完成服务端完整预览；无库存记录不能按 0 处理。');
}

onActivated(() => {
  getList();
});
</script>
