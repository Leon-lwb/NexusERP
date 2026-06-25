<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="success" @click="handleExecute" v-if="!isViewMode && canExecute" :loading="executeLoading">执行领料</el-button>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="issueRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="领料单编号" prop="issueCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.issueCode" placeholder="请输入领料单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联工单" prop="woNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.woNo" placeholder="请选择工单" readonly style="flex: 1;" />
                <el-button v-if="!isViewMode" icon="Search" @click="workOrderSelectOpen = true" />
                <el-button type="primary" link @click="handleAutoFillFromWorkOrder" v-if="!isViewMode && form.woId">
                  <el-icon><MagicStick /></el-icon> 带出BOM
                </el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="领料日期" prop="issueDate">
              <el-date-picker clearable v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择领料日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请选择仓库" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="warehouseSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="领料类型" prop="issueType">
              <el-select v-model="form.issueType" placeholder="请选择领料类型" style="width: 100%">
                <el-option label="普通领料" value="NORMAL" />
                <el-option label="倒冲领料" value="BACKFLUSH" :disabled="true" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="领料人" prop="issuerName">
              <el-input v-model="form.issuerName" placeholder="领料人" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">领料明细</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode && canEditLine">添加行</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode && canEditLine">删除行</el-button>
          </el-col>
        </el-row>
        <vxe-table
          ref="lineTableRef"
          :data="lineList"
          border
          stripe
          show-overflow
          :row-config="{ isHover: true }"
          :checkbox-config="{ checkMethod: canCheckLine }"
          @checkbox-change="handleLineSelectionChange"
          @checkbox-all="handleLineSelectionChange"
          height="420">
          <vxe-column type="checkbox" width="45" align="center" v-if="!isViewMode && canEditLine" />
          <vxe-column type="seq" title="序号" width="55" align="center" />
          <vxe-column field="materialCode" title="物料编码" min-width="170">
            <template #default="{ row, rowIndex }">
              <el-input v-model="row.materialCode" placeholder="选择物料" readonly>
                <template #append><el-button icon="Search" @click="openMaterialSelect(rowIndex)" :disabled="isViewMode" /></template>
              </el-input>
            </template>
          </vxe-column>
          <vxe-column field="materialName" title="物料名称" min-width="130" />
          <vxe-column field="materialSpec" title="规格型号" min-width="110" />
          <vxe-column field="baseUomName" title="单位" width="70" align="center" />
          <vxe-column field="bomQty" title="BOM用量" width="90" align="center" />
          <vxe-column field="lossRate" title="损耗率%" width="80" align="center" />
          <vxe-column field="requiredQty" title="应领数量" width="90" align="center" />
          <vxe-column field="issuedQty" title="已领数量" width="90" align="center" />
          <vxe-column field="currentQty" title="本次领料" width="110" align="center">
            <template #default="{ row }">
              <el-input-number
                v-if="!isViewMode && canEditLine"
                v-model="row.currentQty"
                :min="0"
                :precision="4"
                :controls="false"
                style="width: 100%"
                @change="recalcRow(row)" />
              <span v-else>{{ row.currentQty ?? '' }}</span>
            </template>
          </vxe-column>
          <vxe-column field="shortQty" title="欠领数量" width="90" align="center" />
          <vxe-column field="availableQty" title="可用库存" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.availableQty != null"
                    :class="{ 'text-red': row.currentQty != null && row.currentQty > 0 && row.currentQty > row.availableQty }">
                {{ row.availableQty }}
              </span>
              <span v-else>-</span>
            </template>
          </vxe-column>
          <vxe-column field="displayLocationBatch" title="批次/库位" min-width="190">
            <template #default="{ row, rowIndex }">
              <el-input v-model="row.displayLocationBatch" placeholder="选择库存" readonly>
                <template #append><el-button icon="Search" @click="openStockSelect(rowIndex)" :disabled="isViewMode || !row.materialId" /></template>
              </el-input>
            </template>
          </vxe-column>
          <vxe-column field="unitPrice" title="单价" width="100" align="center">
            <template #default="{ row }">
              <el-input-number v-if="!isViewMode && canEditLine" v-model="row.unitPrice" :min="0" :precision="4" :controls="false" style="width: 100%" @change="recalcRow(row)" />
              <span v-else>{{ row.unitPrice ?? '' }}</span>
            </template>
          </vxe-column>
          <vxe-column field="amount" title="金额" width="100" align="center" />
          <vxe-column field="remark" title="备注" min-width="120">
            <template #default="{ row }">
              <el-input v-if="!isViewMode && canEditLine" v-model="row.remark" placeholder="备注" />
              <span v-else>{{ row.remark }}</span>
            </template>
          </vxe-column>
        </vxe-table>
      </el-form>
    </el-card>

    <!-- 选择仓库 -->
    <el-dialog title="选择仓库" v-model="warehouseSelectOpen" width="800px" append-to-body>
      <el-form :model="warehouseQueryParams" :inline="true">
        <el-form-item label="仓库编码"><el-input v-model="warehouseQueryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="getWarehouseList" /></el-form-item>
        <el-form-item label="仓库名称"><el-input v-model="warehouseQueryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="getWarehouseList" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="getWarehouseList">搜索</el-button><el-button icon="Refresh" @click="resetWarehouseQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="warehouseLoading" :data="warehouseList" highlight-current-row @current-change="handleWarehouseSelect">
        <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
        <el-table-column label="仓库名称" align="center" prop="warehouseName" />
        <el-table-column label="操作" align="center"><template #default="scope"><el-button link type="primary" @click="handleWarehouseSelect(scope.row)">选择</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="warehouseTotal > 0" :total="warehouseTotal" v-model:page="warehouseQueryParams.pageNum" v-model:limit="warehouseQueryParams.pageSize" @pagination="getWarehouseList" />
    </el-dialog>

    <!-- 选择工单 -->
    <el-dialog title="选择工单" v-model="workOrderSelectOpen" width="950px" append-to-body>
      <el-form :model="workOrderQueryParams" :inline="true">
        <el-form-item label="工单编号"><el-input v-model="workOrderQueryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="getWorkOrderList" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="getWorkOrderList">搜索</el-button><el-button icon="Refresh" @click="resetWorkOrderQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="workOrderLoading" :data="workOrderList" highlight-current-row height="420">
        <el-table-column label="工单编号" align="center" prop="woNo" />
        <el-table-column label="产品编码" align="center" prop="materialCode" />
        <el-table-column label="产品名称" align="center" prop="materialName" />
        <el-table-column label="计划数量" align="center" prop="plannedQty" />
        <el-table-column label="状态" align="center" prop="woStatus" />
        <el-table-column label="操作" align="center" width="100"><template #default="scope"><el-button link type="primary" @click="selectWorkOrder(scope.row)">选择</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="workOrderTotal > 0" :total="workOrderTotal" v-model:page="workOrderQueryParams.pageNum" v-model:limit="workOrderQueryParams.pageSize" @pagination="getWorkOrderList" />
    </el-dialog>

    <!-- 选择物料 -->
    <el-dialog title="选择物料" v-model="materialSelectOpen" width="1000px" append-to-body>
      <el-form :model="materialQueryParams" :inline="true" label-width="68px">
        <el-form-item label="物料编码" prop="materialCode"><el-input v-model="materialQueryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleMaterialQuery" /></el-form-item>
        <el-form-item label="物料名称" prop="materialName"><el-input v-model="materialQueryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleMaterialQuery" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="handleMaterialQuery">搜索</el-button><el-button icon="Refresh" @click="resetMaterialQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="materialLoading" :data="materialList" @row-dblclick="selectMaterial" highlight-current-row height="400">
        <el-table-column label="物料编码" align="center" prop="materialCode" />
        <el-table-column label="物料名称" align="center" prop="materialName" />
        <el-table-column label="规格型号" align="center" prop="materialSpec" />
        <el-table-column label="单位" align="center" prop="baseUomName" />
        <el-table-column label="操作" align="center" width="100"><template #default="scope"><el-button link type="primary" @click="selectMaterial(scope.row)">选择</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="materialTotal > 0" :total="materialTotal" v-model:page="materialQueryParams.pageNum" v-model:limit="materialQueryParams.pageSize" @pagination="getMaterialList" />
      <template #footer><div class="dialog-footer"><el-button @click="materialSelectOpen = false">取 消</el-button></div></template>
    </el-dialog>

    <!-- 选择可用库存（批次/库位） -->
    <el-dialog title="选择库存（批次/库位）" v-model="stockSelectOpen" width="950px" append-to-body>
      <el-alert title="列出该物料在所有仓库的可用库存，请选择具体批次/库位" type="info" :closable="false" show-icon class="mb8" />
      <el-table v-loading="stockLoading" :data="stockList" highlight-current-row height="420">
        <el-table-column label="批次号" align="center" prop="batchCode" />
        <el-table-column label="仓库" align="center" prop="warehouseName" />
        <el-table-column label="库区" align="center" prop="areaCode" />
        <el-table-column label="库位" align="center" prop="locationCode" />
        <el-table-column label="可用数量" align="center" prop="stockQuantity" />
        <el-table-column label="单位成本" align="center" prop="unitCost" />
        <el-table-column label="有效期" align="center" prop="expireDate" width="110">
          <template #default="scope"><span>{{ parseTime(scope.row.expireDate, '{y}-{m}-{d}') }}</span></template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100"><template #default="scope"><el-button link type="primary" @click="selectStock(scope.row)">选择</el-button></template></el-table-column>
      </el-table>
      <template #footer><div class="dialog-footer"><el-button @click="stockSelectOpen = false">取 消</el-button></div></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="ProductionIssueForm">
import {
  getProductionIssue, addProductionIssue, updateProductionIssue,
  executeProductionIssue, getAvailableStock, getWorkOrderLines,
  getAvailableStockBatch
} from "@/api/erp/wm/productionissue"
import { listMaterial } from "@/api/erp/bd/material"
import { listWarehouse } from "@/api/erp/wm/warehouse"
import { listWork } from "@/api/erp/prd/work"
import { useRoute, useRouter } from 'vue-router'
import { previewCode } from "@/api/system/autocode/rule"
import { useUserStore } from "@/store/modules/user"

const { proxy } = getCurrentInstance() as any
const { parseTime } = proxy
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const form = ref<any>({})
const autoGenFlag = ref(false)
const isViewMode = ref(false)
const title = ref("")
const lineList = ref<any[]>([])
const executeLoading = ref(false)

// 物料选择
const materialSelectOpen = ref(false)
const materialList = ref([])
const materialLoading = ref(false)
const materialTotal = ref(0)
const currentRowIndex = ref(-1)
const materialQueryParams = ref({ pageNum: 1, pageSize: 10, materialCode: null, materialName: null })

// 仓库选择
const warehouseSelectOpen = ref(false)
const warehouseList = ref([])
const warehouseLoading = ref(false)
const warehouseTotal = ref(0)
const warehouseQueryParams = ref({ pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null })

// 工单选择
const workOrderSelectOpen = ref(false)
const workOrderList = ref([])
const workOrderLoading = ref(false)
const workOrderTotal = ref(0)
const workOrderQueryParams = ref({ pageNum: 1, pageSize: 10, woNo: null })

// 可用库存选择
const stockSelectOpen = ref(false)
const stockList = ref([])
const stockLoading = ref(false)

const rules = {
  issueCode: [{ required: true, message: "领料单编号不能为空", trigger: "blur" }]
}

// 已完成（COMPLETED/EXECUTED）后行不可编辑、不可执行
const canEditLine = computed(() => {
  const s = form.value.status
  return !s || s === 'CREATED' || s === 'PARTIAL' || s === 'DRAFT'
})
const canExecute = computed(() => {
  const s = form.value.status
  return !s || s === 'CREATED' || s === 'PARTIAL' || s === 'DRAFT'
})

onMounted(() => {
  const id = route.query.id
  const action = route.query.action as string
  isViewMode.value = action === 'view'
  if (action === 'add') { title.value = "新增生产领料"; reset() }
  else if (action === 'edit') { title.value = "修改生产领料"; if (id) getInfo(id) }
  else if (action === 'view') { title.value = "查看生产领料"; if (id) getInfo(id) }
})

function getInfo(id: any) {
  getProductionIssue(id).then((response: any) => {
    form.value = response.data
    const lines = response.data.wmProductionIssueLineList || []
    // 已领/欠领兜底，便于旧数据显示
    lineList.value = lines.map((l: any) => ({
      ...l,
      issuedQty: l.issuedQty ?? 0,
      shortQty: l.shortQty ?? (l.requiredQty != null ? l.requiredQty - (l.issuedQty ?? 0) : null),
      lineStatus: l.lineStatus || 'PENDING',
      displayLocationBatch: (l.locationCode ? l.locationCode + '/' : '') + (l.batchCode || '')
    }))
    // 加载各行物料的总可用库存供参考
    loadAvailableStocks()
  })
}

function reset() {
  autoGenFlag.value = false
  form.value = {
    issueId: null, issueCode: null, woId: null, woNo: null,
    warehouseId: null, warehouseCode: null, warehouseName: null,
    issueDate: null, issueType: 'NORMAL', status: 'CREATED',
    issuerId: userStore.userId, issuerName: userStore.nickname,
    totalQty: null, totalAmount: null, remark: null
  }
  lineList.value = []
  proxy.resetForm("issueRef")
}

function submitForm() {
  proxy.$refs["issueRef"].validate((valid: boolean) => {
    if (!valid) return
    // 行校验
    for (const l of lineList.value) {
      if (!l.materialId) { proxy.$modal.msgError("存在未选择物料的行"); return }
    }
    if (autoGenFlag.value) form.value.issueCode = undefined
    form.value.wmProductionIssueLineList = lineList.value
    if (form.value.issueId != null) {
      updateProductionIssue(form.value).then(() => { proxy.$modal.msgSuccess("保存成功"); router.push('/erp/wm/productionissue') })
    } else {
      addProductionIssue(form.value).then(() => { proxy.$modal.msgSuccess("新增成功"); router.push('/erp/wm/productionissue') })
    }
  })
}

/** 执行领料：先保存本次领料数量，再调用过账，再重取单据刷新已领/欠领/状态 */
function handleExecute() {
  if (!form.value.issueId) { proxy.$modal.msgWarning("请先保存领料单再执行"); return }
  let hasPick = false
  for (const l of lineList.value) {
    if (l.currentQty && Number(l.currentQty) > 0) { hasPick = true; break }
  }
  if (!hasPick) { proxy.$modal.msgWarning("请填写本次领料数量后再执行"); return }
  proxy.$modal.confirm('确认按本次领料数量执行过账并扣减库存？').then(() => {
    executeLoading.value = true
    form.value.wmProductionIssueLineList = lineList.value
    updateProductionIssue(form.value).then(() => executeProductionIssue(form.value.issueId)).then(() => {
      proxy.$modal.msgSuccess("执行成功，库存已扣减")
      getInfo(form.value.issueId)
    }).catch(() => {}).finally(() => { executeLoading.value = false })
  }).catch(() => {})
}

function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_PROD_ISSUE_CODE').then((response: any) => { form.value.issueCode = response })
  } else {
    form.value.issueCode = null
  }
}

/** 选择工单后，从后端按工单BOM物料需求带出应领明细（排除倒冲料） */
async function handleAutoFillFromWorkOrder() {
  if (!form.value.woId) { proxy.$modal.msgWarning('请先选择关联工单'); return }
  try {
    const res: any = await getWorkOrderLines(form.value.woId)
    const lines = res.data || []
    if (!lines.length) { proxy.$modal.msgWarning('该工单无非倒冲物料需求，无法带出'); return }
    lineList.value = lines
    proxy.$modal.msgSuccess('已根据工单BOM带出领料明细')
    // 加载各行物料的总可用库存供参考
    loadAvailableStocks()
  } catch (e) {
    proxy.$modal.msgError('带出BOM失败，请检查工单信息')
  }
}

function cancel() { proxy.$tab.closeOpenPage({ path: '/erp/wm/productionissue' }) }

function handleAddLine() {
  lineList.value.push({
    materialId: null, materialCode: '', materialName: '', materialSpec: '',
    baseUomId: '', baseUomName: '', bomQty: null, lossRate: null,
    requiredQty: null, issuedQty: 0, currentQty: null, shortQty: null,
    availableQty: null,
    requirementDetailId: null, lineStatus: 'PENDING',
    batchId: null, batchCode: '', locationId: null, locationCode: '', locationName: '',
    displayLocationBatch: '',
    unitPrice: null, amount: null, remark: ''
  })
}

function handleDeleteLine() {
  const checked = proxy.$refs["lineTableRef"].getCheckboxRecords()
  if (!checked.length) { proxy.$modal.msgError("请先选择要删除的行"); return }
  lineList.value = lineList.value.filter((row: any) => !checked.includes(row))
}

function handleLineSelectionChange() {}

/** 行是否可勾选删除：已领料（ISSUED 或 issuedQty>0）的行禁止删除，避免库存流水孤立 */
function canCheckLine({ row }: any) {
  return row.lineStatus !== 'ISSUED' && !(Number(row.issuedQty) > 0)
}

/** 重算行金额，并在应领已定时同步欠领 */
function recalcRow(row: any) {
  if (row.currentQty != null && row.unitPrice != null) {
    row.amount = Number((Number(row.currentQty) * Number(row.unitPrice)).toFixed(4))
  }
}

function openMaterialSelect(rowIndex: number) { currentRowIndex.value = rowIndex; materialSelectOpen.value = true; getMaterialList() }

function getMaterialList() {
  materialLoading.value = true
  // 注入表单已填仓库上下文，仅查询该仓库下有库存记录的物料
  const params = {
    ...materialQueryParams.value,
    warehouseId: form.value.warehouseId ?? null
  }
  listMaterial(params).then((response: any) => { materialList.value = response.rows; materialTotal.value = response.total; materialLoading.value = false })
}

function handleMaterialQuery() { materialQueryParams.value.pageNum = 1; getMaterialList() }

function resetMaterialQuery() {
  materialQueryParams.value = { pageNum: 1, pageSize: 10, materialCode: null, materialName: null }
  handleMaterialQuery()
}

function selectMaterial(row: any) {
  const idx = currentRowIndex.value
  if (idx >= 0 && lineList.value[idx]) {
    lineList.value[idx].materialId = row.materialId
    lineList.value[idx].materialCode = row.materialCode
    lineList.value[idx].materialName = row.materialName
    lineList.value[idx].materialSpec = row.materialSpec
    if (row.baseUomName) lineList.value[idx].baseUomName = row.baseUomName
  }
  materialSelectOpen.value = false
  currentRowIndex.value = -1
  // 选完物料后刷新该行可用库存
  refreshLineAvailableStock(row.materialId, idx)
}

/** 批量加载行物料的总可用库存（汇总所有仓库/批次/库位），写入各行 availableQty 字段 */
function loadAvailableStocks() {
  const ids = lineList.value.map((l: any) => l.materialId).filter(Boolean)
  if (!ids.length) return
  getAvailableStockBatch(ids).then((res: any) => {
    const map = res.data || {}
    lineList.value.forEach((l: any) => {
      l.availableQty = l.materialId && map[l.materialId] != null ? map[l.materialId] : null
    })
  }).catch(() => {})
}

/** 刷新单行可用库存：以传入的 materialId 查询总量，写入指定行的 availableQty */
function refreshLineAvailableStock(materialId: number | null, rowIndex: number) {
  if (!materialId) return
  getAvailableStockBatch([materialId]).then((res: any) => {
    const map = res.data || {}
    if (rowIndex >= 0 && lineList.value[rowIndex]) {
      lineList.value[rowIndex].availableQty = map[materialId] ?? null
    }
  }).catch(() => {})
}

function getWarehouseList() {
  warehouseLoading.value = true
  listWarehouse(warehouseQueryParams.value).then((response: any) => { warehouseList.value = response.rows; warehouseTotal.value = response.total; warehouseLoading.value = false })
}

function resetWarehouseQuery() {
  warehouseQueryParams.value = { pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null }
  getWarehouseList()
}

function handleWarehouseSelect(row: any) {
  if (!row) return
  form.value.warehouseId = row.warehouseId
  form.value.warehouseCode = row.warehouseCode
  form.value.warehouseName = row.warehouseName
  warehouseSelectOpen.value = false
}

// 工单选择
function getWorkOrderList() {
  workOrderLoading.value = true
  listWork(workOrderQueryParams.value).then((response: any) => { workOrderList.value = response.rows; workOrderTotal.value = response.total; workOrderLoading.value = false })
}

function resetWorkOrderQuery() {
  workOrderQueryParams.value = { pageNum: 1, pageSize: 10, woNo: null }
  getWorkOrderList()
}

function selectWorkOrder(row: any) {
  form.value.woId = row.woId
  form.value.woNo = row.woNo
  // 默认取工单车间作为领料仓库兜底
  if (row.workshopId && !form.value.warehouseId) {
    form.value.warehouseId = row.workshopId
    form.value.warehouseName = row.workshopName
  }
  workOrderSelectOpen.value = false
  handleAutoFillFromWorkOrder()
}

// 可用库存选择（批次/库位）
function openStockSelect(rowIndex: number) {
  currentRowIndex.value = rowIndex
  const row = lineList.value[rowIndex]
  if (!row || !row.materialId) { proxy.$modal.msgWarning("请先选择物料"); return }
  stockSelectOpen.value = true
  stockLoading.value = true
  // 不按头部仓库过滤：物料可能存放在其他仓库，弹窗已展示仓库列供用户选择具体批次/库位
  getAvailableStock(row.materialId).then((response: any) => {
    stockList.value = response.data || []
    stockLoading.value = false
  }).catch(() => { stockLoading.value = false })
}

function selectStock(row: any) {
  const idx = currentRowIndex.value
  if (idx >= 0 && lineList.value[idx]) {
    const line = lineList.value[idx]
    line.batchId = row.batchId
    line.batchCode = row.batchCode
    line.locationId = row.locationId
    line.locationCode = row.locationCode
    line.locationName = row.locationName
    // 拼接库位+批次号用于展示，如 L016/B202606050002
    line.displayLocationBatch = (row.locationCode ? row.locationCode + '/' : '') + (row.batchCode || '')
    if (row.unitCost && line.unitPrice == null) line.unitPrice = row.unitCost
    recalcRow(line)
  }
  stockSelectOpen.value = false
  currentRowIndex.value = -1
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.mb8 {
  margin-bottom: 8px;
}
.text-red {
  color: #f56c6c;
  font-weight: 600;
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
:deep(.el-input-number) {
  .el-input__inner {
    text-align: center;
  }
}
</style>
