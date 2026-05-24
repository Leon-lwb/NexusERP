<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && form.status === 'DRAFT' && form.returnId">提交审批</el-button>
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'CONFIRMED'">查看流程</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" :disabled="formDisabled">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="退货单号" prop="returnNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.returnNo" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购订单" prop="orderNo">
              <el-input v-model="form.orderNo" readonly>
                <template #append>
                  <el-button icon="Search" @click="handleSelectOrder" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorName">
              <el-input v-model="form.vendorName" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货原因" prop="returnReason">
              <el-input v-model="form.returnReason" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select v-model="form.selectFlowId" clearable filterable :loading="loadingFlow"
                :disabled="isViewMode || form.status !== 'DRAFT'" placeholder="请选择审批流程" style="width: 100%">
                <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || ('流程#' + f.id)" :value="f.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-tag :type="getStatusType(form.status)">{{ getStatusLabel(form.status) }}</el-tag>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="form.status === 'DRAFT' && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')">
          <el-col :span="24">
            <el-alert type="warning" show-icon :closable="false" title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 puPurchaseReturnService。" />
          </el-col>
        </el-row>

        <el-divider content-position="center">退货明细</el-divider>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>

        <el-table :data="lineList" @selection-change="handleLineSelectionChange" border>
          <el-table-column type="selection" width="50" v-if="!isViewMode" />
          <el-table-column label="序号" type="index" width="60" />
          <el-table-column label="物料编码">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" />
            </template>
          </el-table-column>
          <el-table-column label="物料名称">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" />
            </template>
          </el-table-column>
          <el-table-column label="退货数量">
            <template #default="scope">
              <el-input v-model="scope.row.returnQuantity" />
            </template>
          </el-table-column>
          <el-table-column label="单价">
            <template #default="scope">
              <el-input v-model="scope.row.unitPrice" />
            </template>
          </el-table-column>
          <el-table-column label="金额">
            <template #default="scope">
              <el-input v-model="scope.row.amount" />
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
  </div>
</template>

<script setup lang="ts" name="PurchaseReturnForm">
import { addPurchaseReturn, getPurchaseReturn, updatePurchaseReturn, submitPurchaseReturnFlow, getPurchaseReturnProcId } from '@/api/erp/pu/return'
import { getPurchaseOrder } from '@/api/erp/pu/order'
import { getProcesss } from '@/api/workflow/process'
import { useRoute, useRouter } from 'vue-router'
import { genCode } from "@/api/system/autocode/rule"

const { proxy } = getCurrentInstance() as any
const { pu_purchase_return_status } = toRefs<any>(proxy.useDict('pu_purchase_return_status'))

const route = useRoute()
const router = useRouter()

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
})

const title = ref('')
const isViewMode = ref(false)
const form = ref<any>({})
const autoGenFlag = ref(false)
const lineList = ref<any[]>([])
const checkedLines = ref<any[]>([])
const loadingFlow = ref(false)
const flowOptions = ref([])

const formDisabled = computed(() => isViewMode.value || props.disabled)

const rules = {
  returnNo: [{ required: true, message: '退货单号不能为空', trigger: 'blur' }]
}

onMounted(() => {
  const id = route.query.id as any
  const action = route.query.action as string
  isViewMode.value = action === 'view'
  title.value = action === 'add' ? '新增采购退货' : action === 'edit' ? '修改采购退货' : '查看采购退货'

  if (action === 'add') {
    reset()
    loadFlows()
  } else if (action === 'edit') {
    if (id) getInfo(id)
    loadFlows()
  } else if (id) {
    getInfo(id)
  }
})

function getInfo(id: any) {
  getPurchaseReturn(id).then((res: any) => {
    form.value = res.data
    lineList.value = res.data.puPurchaseReturnLineList || []
    tryAutoSelect()
  })
}

function reset() {
  autoGenFlag.value = false
  form.value = {
    returnId: null,
    returnNo: null,
    orderId: null,
    orderNo: null,
    returnDate: null,
    vendorName: null,
    status: 'DRAFT',
    returnReason: null,
    remark: null,
    selectFlowId: null
  }
  lineList.value = []
}

function loadFlows() {
  loadingFlow.value = true
  getProcesss({ serviceName: 'puPurchaseReturnService' }).then((response: any) => {
    flowOptions.value = response?.data || []
    tryAutoSelect()
    loadingFlow.value = false
  }).catch(() => {
    loadingFlow.value = false
  })
}

function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id
  }
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).returnId != null) {
      const merged = JSON.parse(JSON.stringify(val)) as Record<string, any>
      form.value = merged
      isViewMode.value = props.disabled
      title.value = props.disabled ? '采购退货（流程）' : '采购退货'
      loadFlows()
    }
  },
  { deep: true, immediate: true }
)

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT:     { label: '草稿',   type: 'info' },
  APPROVING: { label: '审批中', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  EXECUTED:  { label: '已执行', type: 'primary' },
  CANCELLED: { label: '已作废', type: 'danger' },
}

function getStatusType(status: string): string {
  return statusMap[status]?.type || 'info'
}

function getStatusLabel(status: string): string {
  return statusMap[status]?.label || status || '未知'
}

function handleAddLine() {
  lineList.value.push({
    materialCode: null,
    materialName: null,
    returnQuantity: null,
    unitPrice: null,
    amount: null,
    remark: null
  })
}

function handleDeleteLine() {
  lineList.value = lineList.value.filter((_, i) => !checkedLines.value.includes(i))
}

function handleLineSelectionChange(rows: any[]) {
  checkedLines.value = rows.map((r: any) => lineList.value.indexOf(r))
}

function handleSelectOrder() {
  if (!form.value.orderId) {
    proxy.$modal.msgWarning('请先选择采购订单ID')
    return
  }
  getPurchaseOrder(form.value.orderId).then((res: any) => {
    const data = res.data
    form.value.orderNo = data.orderNo
    form.value.vendorName = data.vendorName
    form.value.vendorId = data.vendorId
    lineList.value = (data.lineList || data.puPurchaseOrderLineList || []).map((l: any) => ({
      materialCode: l.materialCode,
      materialName: l.materialName,
      returnQuantity: l.receivedQuantity || l.orderQuantity || 0,
      unitPrice: l.unitPrice,
      amount: l.amount,
      remark: ''
    }))
  })
}

function submitForm() {
  form.value.puPurchaseReturnLineList = lineList.value
  ;(form.value.returnId ? updatePurchaseReturn(form.value) : addPurchaseReturn(form.value)).then(() => {
    proxy.$modal.msgSuccess('保存成功')
    router.push('/erp/pu/return')
  })
}

function handleSubmitFlow() {
  if (!form.value.selectFlowId) {
    proxy.$modal.msgWarning("请先选择审批流程")
    return
  }
  if (form.value.returnId != null) {
    updatePurchaseReturn(form.value).then(() => {
      submitPurchaseReturnFlow(String(form.value.returnId), String(form.value.selectFlowId)).then((res: any) => {
        if (res.code === 200) {
          proxy.$modal.msgSuccess("提交审批成功")
          router.push('/erp/pu/return')
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

function onViewFlow() {
  getPurchaseReturnProcId(String(form.value.returnId)).then((res: any) => {
    if (res.code === 200 && res.data) {
      router.push('/workflow/process/detail/' + res.data)
    } else {
      proxy.$modal.msgWarning('暂无流程信息')
    }
  }).catch(() => {
    proxy.$modal.msgWarning('获取流程信息失败')
  })
}

//自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('PU_RETURN_CODE').then(response => {
      form.value.returnNo = response;
    });
  } else {
    form.value.returnNo = null;
  }
}

function cancel() {
  router.push('/erp/pu/return')
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
