<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode && form.status === 'PREPARE'">保存</el-button>
            <el-button type="success" @click="handleConfirm" v-if="!isViewMode && (form.status === 'PREPARE' || form.status === 'APPROVED') && form.issueId">确认</el-button>
            <el-button type="warning" @click="handleComplete" v-if="!isViewMode && form.status === 'CONFIRMED'">完成出库</el-button>
            <el-button type="primary" @click="handleSubmitFlow" v-if="!isViewMode && form.status === 'PREPARE' && form.issueId">提交审批</el-button>
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'APPROVED'">查看流程</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="salesIssueRef" :model="form" :rules="rules" label-width="100px" :disabled="formDisabled">
        <!-- 基本信息 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="出库单编号" prop="issueCode">
              <el-input v-model="form.issueCode" placeholder="请输入出库单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出库单名称" prop="issueName">
              <el-input v-model="form.issueName" placeholder="请输入出库单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出库日期" prop="issueDate">
              <el-date-picker
                v-model="form.issueDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择出库日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 来源信息 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="销售订单" prop="soNo">
              <el-input v-model="form.soNo" placeholder="请选择销售订单" readonly>
                <template #append>
                  <el-button icon="Search" @click="openSalesOrderSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户" prop="clientName">
              <el-input v-model="form.clientName" placeholder="客户名称" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请选择仓库" readonly>
                <template #append>
                  <el-button icon="Search" @click="openWarehouseSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 状态、审批流程、备注 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="单据状态" prop="status">
              <el-tag :type="getStatusType(form.status)">{{ getStatusLabel(form.status) }}</el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select v-model="form.selectFlowId" clearable filterable :loading="loadingFlow"
                :disabled="isViewMode || form.status !== 'PREPARE'" placeholder="请选择审批流程" style="width: 100%">
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || ('流程#' + f.id)" :value="f.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="1" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 审批流程提示 -->
        <el-row :gutter="20" v-if="form.status === 'PREPARE' && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')">
          <el-col :span="24">
            <el-alert type="warning" show-icon :closable="false" title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 wmSalesIssueService。" />
          </el-col>
        </el-row>

        <!-- 汇总信息 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="出库数量">
              <el-input v-model="form.totalQuantity" disabled>
                <template #append>件</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出库金额">
              <el-input v-model="form.totalAmount" disabled>
                <template #append>元</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 明细表格 -->
        <el-divider content-position="center">出库明细</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode && form.status === 'PREPARE'">
              添加物料
            </el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode && form.status === 'PREPARE'">
              删除
            </el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" icon="Download" @click="handleImportFromOrder" v-if="!isViewMode && form.status === 'PREPARE' && form.soId">
              从订单导入
            </el-button>
          </el-col>
        </el-row>

        <el-table
          :data="wmSalesIssueLineList"
          :row-class-name="rowLineIndex"
          @selection-change="handleLineSelectionChange"
          ref="lineTable"
          border
        >
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode && form.status === 'PREPARE'" />
          <el-table-column label="序号" align="center" prop="index" width="60" />
          <el-table-column label="物料编码" prop="materialCode" width="140">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="选择物料" readonly v-if="!isViewMode && form.status === 'PREPARE'">
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150" show-overflow-tooltip />
          <el-table-column label="规格型号" prop="materialSpec" width="120" show-overflow-tooltip />
          <el-table-column label="单位" prop="baseUomName" width="80" />
          <el-table-column label="批次号" prop="batchCode" width="140">
            <template #default="scope">
              <el-input v-model="scope.row.batchCode" placeholder="选择批次" readonly v-if="!isViewMode && form.status === 'PREPARE'">
                <template #append>
                  <el-button icon="Search" @click="openBatchSelect(scope.$index)" :disabled="!scope.row.materialId" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.batchCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库位" prop="locationName" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.locationName" placeholder="选择库位" readonly v-if="!isViewMode && form.status === 'PREPARE'">
                <template #append>
                  <el-button icon="Search" @click="openLocationSelect(scope.$index)" :disabled="!form.warehouseId" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.locationName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库存数量" prop="stockQuantity" width="100" align="right">
            <template #default="scope">
              <span :class="{ 'text-danger': scope.row.stockQuantity < scope.row.quantityIssue }">
                {{ scope.row.stockQuantity || 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="出库数量" prop="quantityIssue" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.quantityIssue"
                :min="0"
                :max="scope.row.stockQuantity"
                :precision="2"
                :controls="false"
                style="width: 100%"
                v-if="!isViewMode && form.status === 'PREPARE'"
                @change="calculateLineAmount(scope.row)"
              />
              <span v-else>{{ scope.row.quantityIssue }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.unitPrice"
                :min="0"
                :precision="4"
                :controls="false"
                style="width: 100%"
                v-if="!isViewMode && form.status === 'PREPARE'"
                @change="calculateLineAmount(scope.row)"
              />
              <span v-else>{{ scope.row.unitPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行金额" prop="lineAmount" width="120" align="right">
            <template #default="scope">
              <span>{{ formatAmount(scope.row.lineAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" min-width="120">
            <template #default="scope">
              <el-input v-model="scope.row.remark" placeholder="备注" v-if="!isViewMode && form.status === 'PREPARE'" />
              <span v-else>{{ scope.row.remark }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

    <!-- 销售订单选择对话框 -->
    <el-dialog title="选择销售订单" v-model="salesOrderDialogOpen" width="1000px" append-to-body>
      <el-form :model="salesOrderQuery" :inline="true">
        <el-form-item label="订单编号">
          <el-input v-model="salesOrderQuery.soNo" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="salesOrderQuery.clientName" placeholder="请输入客户名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getSalesOrderList">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="salesOrderList" @current-change="handleSalesOrderSelect" highlight-current-row>
        <el-table-column label="订单编号" prop="soNo" width="140" />
        <el-table-column label="客户名称" prop="clientName" width="150" />
        <el-table-column label="订单日期" prop="orderDate" width="100" />
        <el-table-column label="订单金额" prop="totalAmount" width="120" />
        <el-table-column label="状态" prop="orderStatus" width="100">
          <template #default="scope">
            <dict-tag :options="so_order_status" :value="scope.row.orderStatus" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="salesOrderDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="confirmSalesOrderSelect" :disabled="!selectedSalesOrder">确定</el-button>
      </template>
    </el-dialog>

    <!-- 仓库选择对话框 -->
    <el-dialog title="选择仓库" v-model="warehouseDialogOpen" width="800px" append-to-body>
      <el-table :data="warehouseList" @current-change="handleWarehouseSelect" highlight-current-row>
        <el-table-column label="仓库编码" prop="warehouseCode" width="120" />
        <el-table-column label="仓库名称" prop="warehouseName" />
        <el-table-column label="仓库地址" prop="warehouseAddress" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="warehouseDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="confirmWarehouseSelect" :disabled="!selectedWarehouse">确定</el-button>
      </template>
    </el-dialog>

    <!-- 物料选择对话框 -->
    <el-dialog title="选择物料" v-model="materialDialogOpen" width="1000px" append-to-body>
      <el-form :model="materialQuery" :inline="true">
        <el-form-item label="物料编码">
          <el-input v-model="materialQuery.materialCode" placeholder="请输入物料编码" clearable />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="materialQuery.materialName" placeholder="请输入物料名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getMaterialList">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="materialList" @current-change="handleMaterialSelect" highlight-current-row>
        <el-table-column label="物料编码" prop="materialCode" width="120" />
        <el-table-column label="物料名称" prop="materialName" width="150" />
        <el-table-column label="规格型号" prop="materialSpec" width="120" />
        <el-table-column label="单位" prop="baseUomName" width="80" />
        <el-table-column label="库存数量" prop="stockQuantity" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="materialDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="confirmMaterialSelect" :disabled="!selectedMaterial">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批次选择对话框 -->
    <el-dialog title="选择批次" v-model="batchDialogOpen" width="900px" append-to-body>
      <el-table :data="batchList" @current-change="handleBatchSelect" highlight-current-row>
        <el-table-column label="批次号" prop="batchCode" width="150" />
        <el-table-column label="物料编码" prop="materialCode" width="120" />
        <el-table-column label="物料名称" prop="materialName" width="150" />
        <el-table-column label="库位" prop="locationName" width="120" />
        <el-table-column label="库存数量" prop="stockQuantity" width="100" />
        <el-table-column label="生产日期" prop="productionDate" width="100" />
        <el-table-column label="有效期至" prop="expireDate" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="batchDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchSelect" :disabled="!selectedBatch">确定</el-button>
      </template>
    </el-dialog>

    <!-- 库位选择对话框 -->
    <el-dialog title="选择库位" v-model="locationDialogOpen" width="800px" append-to-body>
      <el-table :data="locationList" @current-change="handleLocationSelect" highlight-current-row>
        <el-table-column label="库位编码" prop="locationCode" width="120" />
        <el-table-column label="库位名称" prop="locationName" />
        <el-table-column label="所属库区" prop="areaName" width="120" />
      </el-table>
      <template #footer>
        <el-button @click="locationDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="confirmLocationSelect" :disabled="!selectedLocation">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="SalesIssueForm">
import {
  getSalesIssue,
  addSalesIssue,
  updateSalesIssue,
  confirmSalesIssue,
  completeSalesIssue,
  submitSalesIssueFlow,
  getSalesIssueProcId
} from "@/api/erp/wm/salesIssue"
import { listOrderheader, getOrderLine } from "@/api/erp/so/orderheader"
import { listWarehouse } from "@/api/erp/wm/warehouse"
import { listMaterial } from "@/api/erp/bd/material"
import { listStock } from "@/api/erp/wm/stock"
import { listStoragelocation } from "@/api/erp/wm/storagelocation"
import { getProcesss } from '@/api/workflow/process'
import { useRoute, useRouter } from 'vue-router'

const { proxy } = getCurrentInstance() as any
const { wm_sales_status, so_order_status } = toRefs<any>(proxy.useDict('wm_sales_status', 'so_order_status'))
const route = useRoute()
const router = useRouter()

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
})

// 表单数据
const form = ref({
  issueId: null,
  issueCode: null,
  issueName: null,
  soId: null,
  soNo: null,
  clientId: null,
  clientName: null,
  warehouseId: null,
  warehouseName: null,
  issueDate: null,
  totalQuantity: 0,
  totalAmount: 0,
  status: 'PREPARE',
  remark: null
})

const wmSalesIssueLineList = ref([])
const checkedLines = ref([])
const isViewMode = ref(false)
const title = ref("")
const loadingFlow = ref(false)
const flowOptions = ref([])

// 表单禁用状态（考虑 workflow 嵌入和查看模式）
const formDisabled = computed(() => isViewMode.value || props.disabled)
const currentLineIndex = ref(-1)

// 对话框显示状态
const salesOrderDialogOpen = ref(false)
const warehouseDialogOpen = ref(false)
const materialDialogOpen = ref(false)
const batchDialogOpen = ref(false)
const locationDialogOpen = ref(false)

// 选中数据
const selectedSalesOrder = ref(null)
const selectedWarehouse = ref(null)
const selectedMaterial = ref(null)
const selectedBatch = ref(null)
const selectedLocation = ref(null)
const importOrderLoading = ref(false)

// 列表数据
const salesOrderList = ref([])
const warehouseList = ref([])
const materialList = ref([])
const batchList = ref([])
const locationList = ref([])

// 查询参数
const salesOrderQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  soNo: null,
  clientName: null,
  orderStatus: 'CONFIRMED'
})

const materialQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  materialCode: null,
  materialName: null
})

// 表单校验规则
const rules = {
  issueCode: [{ required: true, message: "出库单编号不能为空", trigger: "blur" }],
  issueDate: [{ required: true, message: "出库日期不能为空", trigger: "change" }],
  warehouseName: [{ required: true, message: "仓库不能为空", trigger: "change" }]
}

/**
 * 将流程下发的业务数据写入本地状态
 */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>
  form.value = merged
  wmSalesIssueLineList.value = Array.isArray(merged.wmSalesIssueLineList) ? merged.wmSalesIssueLineList : []
}

/**
 * 加载审批流列表
 */
function loadFlows() {
  loadingFlow.value = true
  getProcesss({ serviceName: 'wmSalesIssueService' }).then((response: any) => {
    flowOptions.value = response?.data || []
    tryAutoSelect()
    loadingFlow.value = false
  }).catch(() => {
    loadingFlow.value = false
  })
}

/**
 * 尝试自动选择流程（仅一个选项时自动选中）
 */
function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id
  }
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).issueId != null) {
      applyWorkflowFormPayload(val as Record<string, any>)
      isViewMode.value = props.disabled
      title.value = props.disabled ? '销售出库（流程）' : '销售出库'
      loadFlows()
    }
  },
  { deep: true, immediate: true }
)

onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  isViewMode.value = action === 'view'

  if (id) {
    initPage(id, action)
  } else if (action === 'add') {
    title.value = "新增销售出库"
    reset()
    loadFlows()
  }
})

function initPage(id: any, action: any) {
  if (action === 'view') {
    title.value = "查看销售出库"
  } else if (action === 'edit') {
    title.value = "修改销售出库"
  }
  getInfo(id)
  loadFlows()
}

function getInfo(id: any) {
  getSalesIssue(id).then((response: any) => {
    form.value = response.data
    wmSalesIssueLineList.value = response.data.wmSalesIssueLineList || []
    calculateTotal()
    tryAutoSelect()
  })
}

function reset() {
  form.value = {
    issueId: null,
    issueCode: null,
    issueName: null,
    soId: null,
    soNo: null,
    clientId: null,
    clientName: null,
    warehouseId: null,
    warehouseName: null,
    issueDate: proxy.parseTime(new Date(), '{y}-{m}-{d}'),
    totalQuantity: 0,
    totalAmount: 0,
    status: 'PREPARE',
    remark: null,
    selectFlowId: null
  }
  wmSalesIssueLineList.value = []
  calculateTotal()
  proxy.resetForm("salesIssueRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["salesIssueRef"].validate((valid: boolean) => {
    if (valid) {
      if (wmSalesIssueLineList.value.length === 0) {
        proxy.$modal.msgError("请至少添加一条出库明细")
        return
      }

      // 校验库存
      for (let line of wmSalesIssueLineList.value) {
        if (!line.quantityIssue || line.quantityIssue <= 0) {
          proxy.$modal.msgError("出库数量必须大于0")
          return
        }
        if (line.stockQuantity && line.quantityIssue > line.stockQuantity) {
          proxy.$modal.msgError(`物料【${line.materialName}】出库数量不能大于库存数量`)
          return
        }
      }

      form.value.wmSalesIssueLineList = wmSalesIssueLineList.value

      if (form.value.issueId != null) {
        updateSalesIssue(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          router.push('/erp/wm/salesIssue')
        })
      } else {
        addSalesIssue(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          router.push('/erp/wm/salesIssue')
        })
      }
    }
  })
}

/** 确认按钮 */
function handleConfirm() {
  proxy.$modal.confirm('确认后单据将进入已确认状态，是否继续？').then(() => {
    return confirmSalesIssue(form.value.issueId)
  }).then(() => {
    proxy.$modal.msgSuccess("确认成功")
    getInfo(form.value.issueId)
  })
}

/** 完成出库按钮 */
function handleComplete() {
  proxy.$modal.confirm('完成后将扣减库存，是否继续？').then(() => {
    return completeSalesIssue(form.value.issueId)
  }).then(() => {
    proxy.$modal.msgSuccess("出库完成")
    getInfo(form.value.issueId)
  })
}

/**
 * 提交审批（Flowable）
 */
function handleSubmitFlow() {
  if (!form.value.selectFlowId) {
    proxy.$modal.msgWarning("请先选择审批流程")
    return
  }
  form.value.wmSalesIssueLineList = wmSalesIssueLineList.value
  if (form.value.issueId != null) {
    updateSalesIssue(form.value).then(() => {
      submitSalesIssueFlow(String(form.value.issueId), String(form.value.selectFlowId)).then((res: any) => {
        if (res.code === 200) {
          proxy.$modal.msgSuccess("提交审批成功")
          router.push('/erp/wm/salesIssue')
        } else {
          proxy.$modal.msgError(res.msg || '提交审批失败')
        }
      }).catch((error: any) => {
        proxy.$modal.msgError('提交审批失败：' + error)
      })
    })
  } else {
    proxy.$modal.msgWarning("请先保存单据后再提交审批")
  }
}

/**
 * 查看流程
 */
function onViewFlow() {
  getSalesIssueProcId(String(form.value.issueId)).then((res: any) => {
    if (res.code === 200 && res.data) {
      router.push('/workflow/process/detail/' + res.data)
    } else {
      proxy.$modal.msgWarning('暂无流程信息')
    }
  }).catch(() => {
    proxy.$modal.msgWarning('获取流程信息失败')
  })
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/wm/salesIssue' })
}

/** 明细行相关方法 */
function rowLineIndex({ rowIndex }: any) {
  return { rowIndex: rowIndex + 1 }
}

function handleLineSelectionChange(selection: any[]) {
  checkedLines.value = selection.map((item: any) => item.index)
}

function handleAddLine() {
  if (!form.value.warehouseId) {
    proxy.$modal.msgError("请先选择仓库")
    return
  }

  const obj: any = {
    sourceDocLineId: null,
    lineNo: String((wmSalesIssueLineList.value.length + 1) * 10).padStart(4, '0'),
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    batchId: null,
    batchCode: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    stockQuantity: 0,
    quantityIssue: 0,
    quantityOrdered: 0,
    quantityDelivered: 0,
    unitPrice: 0,
    lineAmount: 0,
    remark: null
  }
  wmSalesIssueLineList.value.push(obj)
}

function handleDeleteLine() {
  if (checkedLines.value.length === 0) {
    proxy.$modal.msgError("请先选择要删除的数据")
    return
  }
  wmSalesIssueLineList.value = wmSalesIssueLineList.value.filter((item: any) => !checkedLines.value.includes(item.index))
  // 重新计算序号
  wmSalesIssueLineList.value.forEach((item: any, index: number) => {
    item.index = index + 1
    item.lineNo = String((index + 1) * 10).padStart(4, '0')
  })
  calculateTotal()
}

function calculateLineAmount(row: any) {
  const qty = parseFloat(row.quantityIssue) || 0
  const price = parseFloat(row.unitPrice) || 0
  row.lineAmount = (qty * price).toFixed(4)
  calculateTotal()
}

function calculateTotal() {
  let totalQty = 0
  let totalAmt = 0
  wmSalesIssueLineList.value.forEach((item: any) => {
    totalQty += parseFloat(item.quantityIssue) || 0
    totalAmt += parseFloat(item.lineAmount) || 0
  })
  form.value.totalQuantity = totalQty.toFixed(2)
  form.value.totalAmount = totalAmt.toFixed(4)
}

function formatAmount(amount: any) {
  if (!amount) return '0.00'
  return parseFloat(amount).toFixed(2)
}

function getStatusType(status: string) {
  const typeMap: any = {
    'PREPARE': 'info',
    'APPROVING': 'warning',
    'APPROVED': 'success',
    'REJECTED': 'danger',
    'CONFIRMED': 'warning',
    'COMPLETED': 'success',
    'CANCELLED': 'danger'
  }
  return typeMap[status] || 'info'
}

function getStatusLabel(status: string) {
  const labelMap: any = {
    'PREPARE': '准备中',
    'APPROVING': '审批中',
    'APPROVED': '已批准',
    'REJECTED': '已驳回',
    'CONFIRMED': '已确认',
    'COMPLETED': '已完成',
    'CANCELLED': '已取消'
  }
  return labelMap[status] || status
}

/** 销售订单选择 */
function openSalesOrderSelect() {
  salesOrderDialogOpen.value = true
  selectedSalesOrder.value = null
  getSalesOrderList()
}

function getSalesOrderList() {
  listOrderheader(salesOrderQuery).then((response: any) => {
    salesOrderList.value = response.rows
  })
}

function handleSalesOrderSelect(val: any) {
  selectedSalesOrder.value = val
}

function confirmSalesOrderSelect() {
  if (selectedSalesOrder.value) {
    form.value.soId = selectedSalesOrder.value.soId
    form.value.soNo = selectedSalesOrder.value.soNo
    form.value.clientId = selectedSalesOrder.value.clientId
    form.value.clientName = selectedSalesOrder.value.clientName
    salesOrderDialogOpen.value = false
  }
}

/** 仓库选择 */
function openWarehouseSelect() {
  warehouseDialogOpen.value = true
  selectedWarehouse.value = null
  listWarehouse({ pageNum: 1, pageSize: 100 }).then((response: any) => {
    warehouseList.value = response.rows
  })
}

function handleWarehouseSelect(val: any) {
  selectedWarehouse.value = val
}

function confirmWarehouseSelect() {
  if (selectedWarehouse.value) {
    form.value.warehouseId = selectedWarehouse.value.warehouseId
    form.value.warehouseName = selectedWarehouse.value.warehouseName
    warehouseDialogOpen.value = false
  }
}

/** 物料选择 */
function openMaterialSelect(index: number) {
  currentLineIndex.value = index
  materialDialogOpen.value = true
  selectedMaterial.value = null
  getMaterialList()
}

function getMaterialList() {
  listMaterial(materialQuery).then((response: any) => {
    materialList.value = response.rows
  })
}

function handleMaterialSelect(val: any) {
  selectedMaterial.value = val
}

function confirmMaterialSelect() {
  if (selectedMaterial.value && currentLineIndex.value >= 0) {
    const row = wmSalesIssueLineList.value[currentLineIndex.value]
    row.materialId = selectedMaterial.value.materialId
    row.materialCode = selectedMaterial.value.materialCode
    row.materialName = selectedMaterial.value.materialName
    row.materialSpec = selectedMaterial.value.materialSpec
    row.baseUomId = selectedMaterial.value.baseUomId
    row.baseUomName = selectedMaterial.value.baseUomName
    row.unitPrice = selectedMaterial.value.standardPrice || 0
    materialDialogOpen.value = false

    // 加载该物料的库存信息
    loadStockInfo(row)
  }
}

/** 批次选择 */
function openBatchSelect(index: number) {
  currentLineIndex.value = index
  const row = wmSalesIssueLineList.value[index]

  if (!row.materialId) {
    proxy.$modal.msgError("请先选择物料")
    return
  }

  batchDialogOpen.value = true
  selectedBatch.value = null

  // 查询该物料在选中仓库的库存批次
  listStock({
    materialId: row.materialId,
    warehouseId: form.value.warehouseId,
    pageNum: 1,
    pageSize: 100
  }).then((response: any) => {
    batchList.value = response.rows.filter((item: any) => item.stockQuantity > 0)
  })
}

function handleBatchSelect(val: any) {
  selectedBatch.value = val
}

function confirmBatchSelect() {
  if (selectedBatch.value && currentLineIndex.value >= 0) {
    const row = wmSalesIssueLineList.value[currentLineIndex.value]
    row.batchId = selectedBatch.value.batchId
    row.batchCode = selectedBatch.value.batchCode
    row.locationId = selectedBatch.value.locationId
    row.locationName = selectedBatch.value.locationName
    row.stockQuantity = selectedBatch.value.stockQuantity
    batchDialogOpen.value = false
  }
}

/** 库位选择 */
function openLocationSelect(index: number) {
  currentLineIndex.value = index
  locationDialogOpen.value = true
  selectedLocation.value = null

  listStoragelocation({
    warehouseId: form.value.warehouseId,
    pageNum: 1,
    pageSize: 100
  }).then((response: any) => {
    locationList.value = response.rows
  })
}

function handleLocationSelect(val: any) {
  selectedLocation.value = val
}

function confirmLocationSelect() {
  if (selectedLocation.value && currentLineIndex.value >= 0) {
    const row = wmSalesIssueLineList.value[currentLineIndex.value]
    row.locationId = selectedLocation.value.locationId
    row.locationCode = selectedLocation.value.locationCode
    row.locationName = selectedLocation.value.locationName
    locationDialogOpen.value = false
  }
}

/** 加载库存信息 */
function loadStockInfo(row: any) {
  if (!form.value.warehouseId || !row.materialId) return

  listStock({
    materialId: row.materialId,
    warehouseId: form.value.warehouseId,
    pageNum: 1,
    pageSize: 1
  }).then((response: any) => {
    if (response.rows && response.rows.length > 0) {
      const stock = response.rows[0]
      row.stockQuantity = stock.stockQuantity
      row.batchId = stock.batchId
      row.batchCode = stock.batchCode
      row.locationId = stock.locationId
      row.locationName = stock.locationName
    }
  })
}

/** 从订单导入明细 */
function handleImportFromOrder() {
  if (!form.value.soId) {
    proxy.$modal.msgError("请先选择销售订单")
    return
  }

  if (wmSalesIssueLineList.value.length > 0) {
    proxy.$modal.confirm('导入订单明细会覆盖当前已有的出库明细，是否继续？').then(() => {
      return loadOrderLinesIntoIssue()
    })
    return
  }

  loadOrderLinesIntoIssue()
}

function loadOrderLinesIntoIssue() {
  importOrderLoading.value = true
  return getOrderLine(form.value.soNo).then((response: any) => {
    const orderLines = response.data || []

    if (orderLines.length === 0) {
      proxy.$modal.msgWarning("该销售订单暂无明细")
      return
    }

    wmSalesIssueLineList.value = orderLines.map((item: any, index: number) => ({
      sourceDocLineId: item.lineId,
      lineNo: String((index + 1) * 10).padStart(4, '0'),
      materialId: item.materialId,
      materialCode: item.materialCode,
      materialName: item.materialName,
      materialSpec: item.materialSpec,
      baseUomId: null,
      baseUomName: item.unit,
      batchId: null,
      batchCode: null,
      locationId: null,
      locationCode: null,
      locationName: null,
      stockQuantity: item.orderedQty || 0,
      quantityIssue: Math.max((item.orderedQty || 0) - (item.deliveredQty || 0), 0),
      quantityOrdered: item.orderedQty || 0,
      quantityDelivered: item.deliveredQty || 0,
      unitPrice: item.unitPrice || 0,
      lineAmount: ((Math.max((item.orderedQty || 0) - (item.deliveredQty || 0), 0)) * (item.unitPrice || 0)).toFixed(4),
      remark: item.attr1 || null
    }))

    calculateTotal()
    proxy.$modal.msgSuccess(`已导入 ${orderLines.length} 条销售订单明细`)
  }).finally(() => {
    importOrderLoading.value = false
  })
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.text-danger {
  color: #f56c6c;
  font-weight: bold;
}
</style>
