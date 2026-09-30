<template>
  <div class="app-container">
    <!-- 采购订单表单卡片 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <!-- 保存按钮，仅在非查看模式下显示 -->
            <el-button type="primary" @click="submitForm" v-if="!isViewMode && !props.disabled">保存</el-button>
            <!-- 提交审批按钮 -->
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && !props.disabled && form.status === 'DRAFT'">提交审批</el-button>
            <!-- 查看流程按钮 -->
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'APPROVED'">查看流程</el-button>
            <!-- 取消按钮 -->
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <!-- 表单区域 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <!-- 第一行：订单号、采购申请号、订单日期 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="订单号" prop="orderNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.orderNo" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch
                  v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode || props.disabled"
                >
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购申请号" prop="requisitionNo">
              <el-input v-model="form.requisitionNo" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="订单日期" prop="orderDate">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="!form.orderId && !isViewMode && !props.disabled"
          title="带申请来源的订单请在采购申请列表转单后编辑"
          description="本页仅支持无申请来源的手工新增，转单与来源校验仍由服务端负责。"
          type="warning"
          :closable="false"
          show-icon
          class="mb8"
        />

        <!-- 供应商信息 -->
        <el-divider content-position="left">供应商信息</el-divider>
        <!-- 第二行：供应商、预计到货日期、状态 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorName">
              <el-input v-model="form.vendorName" readonly>
                <template #append>
                  <!-- 选择供应商按钮 -->
                  <el-button icon="Search" @click="openVendor()" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预计到货" prop="expectedArrivalDate">
              <el-date-picker v-model="form.expectedArrivalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- ERP-PU-04：服务端新增时强制 DRAFT 并忽略客户端 status，改为只读展示，避免用户误以为可改 -->
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" disabled placeholder="状态由服务端管理" style="width: 100%">
                <el-option v-for="dict in pu_purchase_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 第三行：采购部门、采购员 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="采购部门" prop="deptId">
              <dept-select v-model="form.deptId" category="PURCHASE" placeholder="请选择采购部门" @change="handleDeptChange" />
              <el-input v-model="form.deptName" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购员" prop="buyerName">
              <el-input v-model="form.buyerName" placeholder="请选择采购员" readonly>
                <template #append>
                  <el-button icon="Search" @click="openUserSelect" :disabled="!form.deptId" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 财务信息 -->
        <el-divider content-position="left">财务信息</el-divider>
        <!-- 第三行：币别、汇率、运费、审批流程 -->
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="币别" prop="currency">
              <el-select v-model="form.currency">
                <el-option v-for="dict in so_currency_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="汇率" prop="exchangeRate">
              <!-- 汇率变化必须整单重算（行金额/行税额/不含税金额/税额合计/价税合计），见 recalcLines -->
              <el-input v-model="form.exchangeRate" @change="recalcLines" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="运费" prop="freightAmount">
              <!-- 后端 PuPurchaseOrderBo/Vo/实体三层均有 freightAmount，此前前端零命中（FE-ERP-03） -->
              <el-input-number
                v-model="form.freightAmount"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 100%"
                @change="recalcHeader"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select
                v-model="form.selectFlowId"
                clearable
                filterable
                :loading="loadingFlow"
                :disabled="isViewMode || props.disabled || form.status !== 'DRAFT'"
                placeholder="请选择审批流程"
                style="width: 100%"
              >
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 第四行：合同编号、备注 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="合同编号" prop="contractNo">
              <el-input v-model="form.contractNo" readonly placeholder="请选择已批准的采购合同">
                <template #append>
                  <el-button icon="Search" @click="openContractDialog" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 流程选择警告 -->
        <el-alert
          v-if="!isViewMode && !props.disabled && !loadingFlow && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')"
          type="warning"
          show-icon
          :closable="false"
          title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 puPurchaseOrderService。"
          style="margin-bottom: 12px"
        />

        <!-- 订单明细分隔符 -->
        <el-divider content-position="center">订单明细</el-divider>

        <!-- 明细操作按钮 -->
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode && !props.disabled">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode && !props.disabled">删除</el-button>
          </el-col>
        </el-row>

        <!-- 订单明细表格 -->
        <el-table :data="lineList" @selection-change="handleLineSelectionChange" border>
          <el-table-column type="selection" width="50" v-if="!isViewMode && !props.disabled" />
          <el-table-column label="序号" type="index" width="60" />
          <el-table-column label="物料编码">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" readonly>
                <template #append>
                  <!-- 选择物料按钮 -->
                  <el-button icon="Search" @click="openMaterial(scope.$index)" />
                </template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column label="物料名称">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" />
            </template>
          </el-table-column>
          <el-table-column label="数量">
            <template #default="scope">
              <el-input v-model="scope.row.orderQuantity" @change="recalcLine(scope.row)" />
            </template>
          </el-table-column>
          <el-table-column label="单价">
            <template #default="scope">
              <el-input v-model="scope.row.unitPrice" @change="recalcLine(scope.row)" />
            </template>
          </el-table-column>
          <el-table-column label="税率">
            <template #default="scope">
              <el-input v-model="scope.row.taxRate" placeholder="如 0.13" @change="recalcLine(scope.row)" />
            </template>
          </el-table-column>
          <el-table-column label="金额">
            <template #default="scope">
              <el-input v-model="scope.row.lineAmount" readonly />
            </template>
          </el-table-column>
          <el-table-column label="税额">
            <template #default="scope">
              <el-input v-model="scope.row.lineTaxAmount" readonly />
            </template>
          </el-table-column>
          <el-table-column label="不含税金额">
            <template #default="scope">
              <el-input v-model="scope.row.lineAmountExcludingTax" readonly />
            </template>
          </el-table-column>
          <el-table-column label="备注">
            <template #default="scope">
              <el-input v-model="scope.row.remark" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

    <!-- 供应商选择对话框 -->
    <el-dialog v-model="vendorDialog.visible" title="选择供应商" width="960px" append-to-body>
      <el-form :model="vendorDialog.query" :inline="true">
        <el-form-item label="供应商编码">
          <el-input v-model="vendorDialog.query.vendorCode" clearable @keyup.enter="queryVendors" />
        </el-form-item>
        <el-form-item label="供应商名称">
          <el-input v-model="vendorDialog.query.vendorName" clearable @keyup.enter="queryVendors" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryVendors">搜索</el-button>
          <el-button icon="Refresh" @click="resetVendorQuery">重置</el-button>
        </el-form-item>
        <el-form-item v-if="vendorDialog.currentMaterialId">
          <el-checkbox v-model="vendorDialog.filterByMaterial" @change="queryVendors">仅显示该物料供应商</el-checkbox>
        </el-form-item>
      </el-form>
      <el-table :data="vendorDialog.list" height="400" @row-dblclick="selectVendor">
        <el-table-column label="供应商编码" prop="vendorCode" />
        <el-table-column label="供应商名称" prop="vendorName" />
        <el-table-column label="单价" prop="price" width="100" v-if="vendorDialog.filterByMaterial" />
        <el-table-column label="供货周期(天)" prop="leadTime" width="110" v-if="vendorDialog.filterByMaterial" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectVendor(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />

    <!-- 合同选择对话框 -->
    <el-dialog v-model="contractDialog.visible" title="选择采购合同" width="960px" append-to-body>
      <el-form :model="contractDialog.query" :inline="true">
        <el-form-item label="合同编号">
          <el-input v-model="contractDialog.query.contractNo" clearable @keyup.enter="queryContracts" />
        </el-form-item>
        <el-form-item label="合同名称">
          <el-input v-model="contractDialog.query.contractName" clearable @keyup.enter="queryContracts" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryContracts">搜索</el-button>
          <el-button icon="Refresh" @click="resetContractQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="contractDialog.list" height="400" @row-dblclick="selectContract">
        <el-table-column label="合同编号" prop="contractNo" min-width="140" />
        <el-table-column label="合同名称" prop="contractName" min-width="160" show-overflow-tooltip />
        <el-table-column label="供应商" prop="vendorName" min-width="140" show-overflow-tooltip />
        <el-table-column label="合同金额" prop="totalAmount" width="120" align="right" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectContract(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 采购员选择 -->
    <el-dialog title="选择采购员" v-model="userDialog.visible" width="800px" append-to-body>
      <el-form :inline="true" :model="userDialog.queryParams">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userDialog.queryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item label="手机号码" prop="phonenumber">
          <el-input v-model="userDialog.queryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="searchUser">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="userDialog.loading" :data="userDialog.list" height="400" @row-dblclick="selectUser">
        <el-table-column label="用户账号" align="center" prop="userName" />
        <el-table-column label="用户昵称" align="center" prop="nickName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="userDialog.total > 0"
        :total="userDialog.total"
        v-model:page="userDialog.queryParams.pageNum"
        v-model:limit="userDialog.queryParams.pageSize"
        @pagination="getUserList"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PurchaseOrderForm">
/** 导入采购订单相关的API方法 */
import { addPurchaseOrder, getPurchaseOrder, updatePurchaseOrder, submitOrderFlow, getOrderProcId } from '@/api/erp/pu/order';
/** 导入供应商管理API */
import { listVendor } from '@/api/erp/bd/vendor';
import { listUser } from '@/api/system/user';
/** 导入物料-供应商关系API */
import { listMaterialVendor } from '@/api/erp/bd/material-vendor';
/** 导入审批流API */
import { getProcesss } from '@/api/workflow/process/index';
/** 导入采购合同API */
import { listApprovedContract } from '@/api/erp/pu/contract';
/** 导入路由相关功能 */
import { useRoute, useRouter } from 'vue-router';
import { previewCode } from '@/api/system/autocode/rule';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

/** 获取当前实例的代理对象和字典数据 */
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { pu_purchase_order_status, so_currency_type } = toRefs<any>(proxy.useDict('pu_purchase_order_status', 'so_currency_type'));

/** 路由和导航 */
const route = useRoute();
const router = useRouter();

/** 页面状态变量 */
const title = ref(''); /** 页面标题 */
const isViewMode = ref(false); /** 是否为查看模式 */
const form = ref<any>({}); /** 表单数据 */
const autoGenFlag = ref(false); /** 自动生成编号开关 */
const lineList = ref<any[]>([]); /** 订单明细列表 */
const checkedLines = ref<any[]>([]); /** 已选中的明细行 */
const currentMaterialIndex = ref(-1); /** 当前选择的物料索引 */

/** 流程相关状态 */
const loadingFlow = ref(false);
const flowOptions = ref([]);

/** 表单禁用状态 */
const formDisabled = computed(() => isViewMode.value || props.disabled);

/** 供应商选择对话框数据 */
const vendorDialog = reactive({
  visible: false /** 对话框是否可见 */,
  list: [] as any[] /** 供应商列表 */,
  filterByMaterial: false /** 是否按物料筛选供应商 */,
  currentMaterialId: null as number | null /** 当前选中行的物料ID */,
  query: {
    /** 查询条件 */
    pageNum: 1,
    pageSize: 10,
    vendorCode: null,
    vendorName: null
  }
});

/** 物料选择对话框 */
const materialSelectDialogRef = ref<any>(null);

/** 合同选择对话框数据 */
const contractDialog = reactive({
  visible: false /** 对话框是否可见 */,
  list: [] as any[] /** 合同列表 */,
  query: {
    /** 查询条件 */
    pageNum: 1,
    pageSize: 10,
    contractNo: null,
    contractName: null
  }
});

/** 表单验证规则 */
const rules = {
  orderNo: [{ required: true, message: '订单号不能为空', trigger: 'blur' }]
};

/** 将流程下发的业务数据写入本地状态 */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return;
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>;
  form.value = merged;
  lineList.value = Array.isArray(merged.lineList) ? merged.lineList : [];
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).orderId != null) {
      applyWorkflowFormPayload(val as Record<string, any>);
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '采购订单（流程）' : '采购订单';
      loadFlows();
    }
  },
  { deep: true, immediate: true }
);

/** 组件挂载后的初始化逻辑 */
onMounted(() => {
  /** 获取路由参数 */
  const id = route.query.id as any;
  const action = route.query.action as string;

  /** 设置页面模式和标题 */
  isViewMode.value = action === 'view';
  title.value = action === 'add' ? '新增采购订单' : action === 'edit' ? '修改采购订单' : '查看采购订单';

  /** 根据操作类型初始化页面 */
  if (action === 'add') {
    reset(); /** 新增操作：重置表单 */
    loadFlows();
  } else if (id) {
    getInfo(id); /** 编辑/查看操作：获取详情数据 */
    loadFlows();
  }
});

/**
 * 根据ID获取采购订单详情
 * @param id 采购订单ID
 */
function getInfo(id: any) {
  getPurchaseOrder(id).then((res: any) => {
    form.value = res.data;
    lineList.value = res.data.lineList || res.data.puPurchaseOrderLineList || [];
    tryAutoSelect();
  });
}

/**
 * 重置表单数据
 */
function reset() {
  autoGenFlag.value = false;
  form.value = {
    orderId: null,
    orderNo: null,
    requisitionId: null,
    requisitionNo: null,
    orderDate: null,
    expectedArrivalDate: null,
    status: 'DRAFT',
    selectFlowId: null,
    currency: 'CNY',
    exchangeRate: 1,
    freightAmount: 0,
    taxAmount: 0,
    totalAmount: 0,
    remark: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    deptId: null,
    deptName: null,
    buyerId: null,
    buyerName: null
  };
  lineList.value = [];
}

/**
 * 加载审批流列表
 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'puPurchaseOrderService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      tryAutoSelect();
      loadingFlow.value = false;
    })
    .catch(() => {
      loadingFlow.value = false;
    });
}

/**
 * 尝试自动选择流程
 */
function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

/**
 * 提交审批
 */
function handleSubmitFlow() {
  (proxy.$refs['formRef'] as any).validate((valid: boolean) => {
    if (valid) {
      if (!form.value.selectFlowId) {
        proxy.$modal.msgWarning('请先选择审批流程');
        return;
      }
      form.value.lineList = lineList.value;
      if (form.value.orderId != null) {
        updatePurchaseOrder(form.value).then(() => {
          submitOrderFlow(String(form.value.orderId), String(form.value.selectFlowId))
            .then((res: any) => {
              if (res.code === 200) {
                proxy.$modal.msgSuccess('提交审批成功');
                router.push('/erp/puExec/order');
              } else {
                proxy.$modal.msgError(res.msg || '提交审批失败');
              }
            })
            .catch((error: any) => {
              proxy.$modal.msgError('提交审批失败：' + error);
            });
        });
      } else {
        proxy.$modal.msgWarning('请先保存单据后再提交审批');
      }
    }
  });
}

/**
 * 查看流程
 */
function onViewFlow() {
  getOrderProcId(String(form.value.orderId))
    .then((res: any) => {
      if (res.code === 200 && res.data) {
        router.push('/workflow/process/detail/' + res.data);
      } else {
        proxy.$modal.msgWarning('暂无流程信息');
      }
    })
    .catch(() => {
      proxy.$modal.msgWarning('获取流程信息失败');
    });
}

/**
 * 添加订单明细行
 */
function handleAddLine() {
  lineList.value.push({
    materialCode: null,
    materialName: null,
    orderQuantity: null,
    unitPrice: null,
    lineAmount: null,
    remark: null
  });
}

/**
 * 删除订单明细行
 */
function handleDeleteLine() {
  lineList.value = lineList.value.filter((_, i) => !checkedLines.value.includes(i));
}

/**
 * 处理明细表格的选择变化
 * @param rows 选中的行数据
 */
function handleLineSelectionChange(rows: any[]) {
  checkedLines.value = rows.map((r: any) => lineList.value.indexOf(r));
}

/**
 * 打开物料选择对话框
 * @param index 当前行索引
 */
function openMaterial(index: number) {
  currentMaterialIndex.value = index;
  materialSelectDialogRef.value?.open();
}

/**
 * 物料选择回调
 * @param row 选中的物料数据
 */
function handleMaterialSelected(row: any) {
  if (currentMaterialIndex.value < 0) return;
  const target = lineList.value[currentMaterialIndex.value];
  target.materialId = row.materialId;
  target.materialCode = row.materialCode;
  target.materialName = row.materialName;
  target.materialSpec = row.materialSpec;
  target.unit = row.baseUomName || row.unitName || row.unit;
}

/**
 * 打开供应商选择对话框
 * @param materialId 当前选中行的物料ID（可选）
 */
function openVendor(materialId?: number) {
  vendorDialog.currentMaterialId = materialId || null;
  vendorDialog.filterByMaterial = false;
  vendorDialog.visible = true;
  queryVendors();
}

/**
 * 查询供应商列表
 */
function queryVendors() {
  if (vendorDialog.filterByMaterial && vendorDialog.currentMaterialId) {
    /** 按物料筛选供应商 */
    listMaterialVendor({
      materialId: vendorDialog.currentMaterialId,
      status: 1,
      pageNum: 1,
      pageSize: 100
    }).then((res: any) => {
      vendorDialog.list = res.rows || res.data || [];
      vendorDialog.visible = true;
    });
  } else {
    listVendor(vendorDialog.query as any).then((res: any) => {
      vendorDialog.list = res.rows || res.data || [];
      vendorDialog.visible = true;
    });
  }
}

/**
 * 重置供应商查询条件
 */
function resetVendorQuery() {
  vendorDialog.query = {
    pageNum: 1,
    pageSize: 10,
    vendorCode: null,
    vendorName: null
  };
  queryVendors();
}

/**
 * 选择供应商
 * @param row 选中的供应商数据
 */
function selectVendor(row: any) {
  form.value.vendorId = row.vendorId;
  form.value.vendorCode = row.vendorCode;
  form.value.vendorName = row.vendorName;
  /** 如果是从物料供应商列表选择的，自动填充价格到当前行 */
  if (vendorDialog.filterByMaterial && row.price && currentMaterialIndex.value >= 0) {
    const target = lineList.value[currentMaterialIndex.value];
    if (target) {
      target.unitPrice = row.price;
      recalcLine(target);
    }
  }
  vendorDialog.visible = false;
}

/** 采购部门与采购员选择。 */
const userDialog = reactive({
  visible: false,
  loading: false,
  list: [] as any[],
  total: 0,
  queryParams: { pageNum: 1, pageSize: 10, userName: undefined, phonenumber: undefined, deptId: undefined as any }
});

/** 采购部门变更：回填部门名称并清空采购员 */
function handleDeptChange(dept: any) {
  form.value.deptName = dept ? dept.deptName : null;
  form.value.buyerId = null;
  form.value.buyerName = null;
}

function openUserSelect() {
  if (!form.value.deptId) {
    proxy.$modal.msgWarning('请先选择采购部门');
    return;
  }
  userDialog.visible = true;
  userDialog.queryParams.deptId = form.value.deptId;
  getUserList();
}

function getUserList() {
  userDialog.loading = true;
  listUser(userDialog.queryParams).then((res: any) => {
    userDialog.list = res.rows;
    userDialog.total = res.total;
    userDialog.loading = false;
  });
}

function searchUser() {
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

function selectUser(row: any) {
  form.value.buyerId = row.userId;
  form.value.buyerName = row.nickName || row.userName;
  userDialog.visible = false;
}

function resetUserQuery() {
  userDialog.queryParams.userName = undefined;
  userDialog.queryParams.phonenumber = undefined;
  userDialog.queryParams.deptId = form.value.deptId;
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

/**
 * 金额重算（FE-ERP-06）
 * <p>口径与后端采购侧既有实现保持一致：{@code PuContractServiceImpl} 的
 * {@code lineAmount = 含税单价 × 数量}、{@code taxAmount = lineAmount × taxRate}、
 * {@code lineAmountExcludingTax = lineAmount - taxAmount}（BigDecimal HALF_UP 保留 2 位）；
 * 价税合计 {@code totalAmount = Σ lineAmount + freightAmount}（运费计入价税合计，FE-ERP-03）。</p>
 * <p>原实现在模板内联用 {@code 数量×单价×汇率} 只改 lineAmount，改汇率后 taxAmount/totalAmount
 * 仍是旧值，界面自相矛盾；现统一收敛到本组函数，任何金额入口都走同一口径。</p>
 */
function round2(n: number): number {
  return Math.round((Number(n) || 0) * 100 + Number.EPSILON) / 100;
}

/** 单行重算：含税行金额 → 行税额 → 不含税行金额，并联动表头合计 */
function recalcLine(row: any) {
  const qty = parseFloat(row.orderQuantity) || 0;
  const price = parseFloat(row.unitPrice) || 0;
  const rate = parseFloat(form.value.exchangeRate) || 1;
  const taxRate = parseFloat(row.taxRate) || 0;
  const lineAmount = round2(qty * price * rate);
  const lineTaxAmount = round2(lineAmount * taxRate);
  row.lineAmount = lineAmount;
  row.lineTaxAmount = lineTaxAmount;
  row.lineAmountExcludingTax = round2(lineAmount - lineTaxAmount);
  recalcHeader();
}

/** 整单重算：汇率/行数据变化时逐行重算 */
function recalcLines() {
  lineList.value.forEach(recalcLine);
  recalcHeader();
}

/** 表头合计：税额合计 = Σ 行税额；价税合计 = Σ 含税行金额 + 运费 */
function recalcHeader() {
  const totalAmount = lineList.value.reduce((s, r) => s + (parseFloat(r.lineAmount) || 0), 0);
  const taxAmount = lineList.value.reduce((s, r) => s + (parseFloat(r.lineTaxAmount) || 0), 0);
  const freightAmount = parseFloat(form.value.freightAmount) || 0;
  form.value.taxAmount = round2(taxAmount);
  form.value.totalAmount = round2(totalAmount + freightAmount);
}

/**
 * 提交表单
 */
function submitForm() {
  recalcLines();
  form.value.lineList = lineList.value;
  /** 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号 */
  if (autoGenFlag.value) {
    form.value.orderNo = undefined;
  }
  (form.value.orderId ? updatePurchaseOrder(form.value) : addPurchaseOrder(form.value)).then(() => {
    proxy.$modal.msgSuccess('保存成功');
    router.push('/erp/puExec/order');
  });
}

/**
 * 取消并返回列表页
 */
/** 自动生成编号 */
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('PU_ORDER_CODE').then((response) => {
      form.value.orderNo = response;
    });
  } else {
    form.value.orderNo = null;
  }
}

function cancel() {
  router.push('/erp/puExec/order');
}

/**
 * 打开合同选择对话框
 */
function openContractDialog() {
  contractDialog.visible = true;
  queryContracts();
}

/**
 * 查询已批准的合同列表
 */
function queryContracts() {
  listApprovedContract(contractDialog.query as any).then((res: any) => {
    contractDialog.list = res.rows || res.data || [];
    contractDialog.visible = true;
  });
}

/**
 * 重置合同查询条件
 */
function resetContractQuery() {
  contractDialog.query = {
    pageNum: 1,
    pageSize: 10,
    contractNo: null,
    contractName: null
  };
  queryContracts();
}

/**
 * 选择合同
 * @param row 选中的合同数据
 */
function selectContract(row: any) {
  form.value.contractId = row.contractId;
  form.value.contractNo = row.contractNo;
  /** 如果供应商为空，自动填充合同中的供应商信息 */
  if (!form.value.vendorId && row.vendorId) {
    form.value.vendorId = row.vendorId;
    form.value.vendorCode = row.vendorCode;
    form.value.vendorName = row.vendorName;
  }
  contractDialog.visible = false;
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.card-actions {
  display: flex;
  gap: 10px;
}
.mb8 {
  margin-bottom: 8px;
}
:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__label) {
  font-weight: 500;
}
</style>
