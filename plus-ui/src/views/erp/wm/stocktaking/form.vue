<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button type="success" @click="handleGenerateLines" v-if="!isViewMode && form.planId">生成盘点行</el-button>
            <el-button type="warning" @click="handlePostAdjustment" v-if="!isViewMode && form.planId">过账调整</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="stocktakingRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="盘点编号" prop="planCode">
              <el-input v-model="form.planCode" placeholder="请输入盘点编号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="盘点名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入盘点名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="盘点日期" prop="plannedDate">
              <el-date-picker clearable v-model="form.plannedDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择盘点日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请选择仓库" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="warehouseSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库区" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请选择库区" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="areaSelectOpen = true" />
                </template>
              </el-input>
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

        <el-divider content-position="center">盘点行信息</el-divider>
        <el-table :data="lineList" :row-class-name="rowLineIndex" @selection-change="handleLineSelectionChange">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="物料编码" prop="materialCode" width="120">
            <template #default="scope"><span>{{ scope.row.materialCode }}</span></template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150">
            <template #default="scope"><span>{{ scope.row.materialName }}</span></template>
          </el-table-column>
          <el-table-column label="规格型号" prop="materialSpec" width="120">
            <template #default="scope"><span>{{ scope.row.materialSpec }}</span></template>
          </el-table-column>
          <el-table-column label="库位" prop="locationCode" width="100">
            <template #default="scope"><span>{{ scope.row.locationCode }}</span></template>
          </el-table-column>
          <el-table-column label="批次" prop="batchCode" width="100">
            <template #default="scope"><span>{{ scope.row.batchCode }}</span></template>
          </el-table-column>
          <el-table-column label="账面数量" prop="bookQuantity" width="100">
            <template #default="scope"><span>{{ scope.row.bookQuantity }}</span></template>
          </el-table-column>
          <el-table-column label="实盘数量" prop="actualQuantity" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.actualQuantity" placeholder="实盘数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.actualQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异数量" prop="differenceQuantity" width="100">
            <template #default="scope"><span>{{ scope.row.differenceQuantity }}</span></template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.remark" placeholder="备注" v-if="!isViewMode" />
              <span v-else>{{ scope.row.remark }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

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

    <el-dialog title="选择库区" v-model="areaSelectOpen" width="800px" append-to-body>
      <el-form :model="areaQueryParams" :inline="true">
        <el-form-item label="库区编码"><el-input v-model="areaQueryParams.areaCode" placeholder="请输入库区编码" clearable @keyup.enter="getAreaList" /></el-form-item>
        <el-form-item label="库区名称"><el-input v-model="areaQueryParams.areaName" placeholder="请输入库区名称" clearable @keyup.enter="getAreaList" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="getAreaList">搜索</el-button><el-button icon="Refresh" @click="resetAreaQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="areaLoading" :data="areaList" highlight-current-row @current-change="handleAreaSelect">
        <el-table-column label="库区编码" align="center" prop="areaCode" />
        <el-table-column label="库区名称" align="center" prop="areaName" />
        <el-table-column label="操作" align="center"><template #default="scope"><el-button link type="primary" @click="handleAreaSelect(scope.row)">选择</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="areaTotal > 0" :total="areaTotal" v-model:page="areaQueryParams.pageNum" v-model:limit="areaQueryParams.pageSize" @pagination="getAreaList" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="StockTakingForm">
import { getStockTakingPlan, addStockTakingPlan, updateStockTakingPlan, generateLines, postAdjustment } from "@/api/erp/wm/stocktaking"
import { listWarehouse } from "@/api/erp/bd/warehouse"
import { listStoragearea } from "@/api/erp/wm/storagearea"
import { useRoute, useRouter } from 'vue-router'

const { proxy } = getCurrentInstance() as any
const route = useRoute()
const router = useRouter()

const form = ref<any>({})
const isViewMode = ref(false)
const title = ref("")
const lineList = ref<any[]>([])
const checkedLines = ref<any[]>([])

const warehouseSelectOpen = ref(false)
const warehouseList = ref([])
const warehouseLoading = ref(false)
const warehouseTotal = ref(0)
const warehouseQueryParams = ref({ pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null })

const areaSelectOpen = ref(false)
const areaList = ref([])
const areaLoading = ref(false)
const areaTotal = ref(0)
const areaQueryParams = ref({ pageNum: 1, pageSize: 10, areaCode: null, areaName: null })

const rules = {
  planCode: [{ required: true, message: "盘点编号不能为空", trigger: "blur" }],
  planName: [{ required: true, message: "盘点名称不能为空", trigger: "blur" }]
}

onMounted(() => {
  const id = route.query.id
  const action = route.query.action as string
  isViewMode.value = action === 'view'
  if (action === 'add') { title.value = "新增盘点计划"; reset() }
  else if (action === 'edit') { title.value = "修改盘点计划"; if (id) getInfo(id) }
  else if (action === 'view') { title.value = "查看盘点计划"; if (id) getInfo(id) }
})

function getInfo(id: any) {
  getStockTakingPlan(id).then((response: any) => {
    form.value = response.data
    lineList.value = response.data.wmStockTakingLineList || []
  })
}

function reset() {
  form.value = { planId: null, planCode: null, planName: null, warehouseId: null, warehouseCode: null, warehouseName: null, areaId: null, areaCode: null, areaName: null, plannedDate: null, status: null, remark: null }
  lineList.value = []
  proxy.resetForm("stocktakingRef")
}

function submitForm() {
  proxy.$refs["stocktakingRef"].validate((valid: boolean) => {
    if (valid) {
      form.value.wmStockTakingLineList = lineList.value
      if (form.value.planId != null) {
        updateStockTakingPlan(form.value).then(() => { proxy.$modal.msgSuccess("修改成功"); router.push('/erp/wm/stocktaking') })
      } else {
        addStockTakingPlan(form.value).then(() => { proxy.$modal.msgSuccess("新增成功"); router.push('/erp/wm/stocktaking') })
      }
    }
  })
}

function cancel() { proxy.$tab.closeOpenPage({ path: '/erp/wm/stocktaking' }) }

function rowLineIndex({ row, rowIndex }: any) { row.index = rowIndex + 1 }

function handleLineSelectionChange(selection: any[]) { checkedLines.value = selection.map((item: any) => item.index) }

function handleGenerateLines() {
  if (!form.value.planId) { proxy.$modal.msgWarning("请先保存盘点计划"); return }
  generateLines(form.value.planId).then(() => { proxy.$modal.msgSuccess("生成盘点行成功"); getInfo(form.value.planId) })
}

function handlePostAdjustment() {
  if (!form.value.planId) { proxy.$modal.msgWarning("请先保存盘点计划"); return }
  proxy.$modal.confirm('确认过账盘点差异调整？').then(() => {
    postAdjustment(form.value.planId).then(() => { proxy.$modal.msgSuccess("过账成功"); getInfo(form.value.planId) })
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
  form.value.warehouseId = row.warehouseId
  form.value.warehouseCode = row.warehouseCode
  form.value.warehouseName = row.warehouseName
  warehouseSelectOpen.value = false
}

function getAreaList() {
  areaLoading.value = true
  listStoragearea(areaQueryParams.value).then((response: any) => { areaList.value = response.rows; areaTotal.value = response.total; areaLoading.value = false })
}

function resetAreaQuery() {
  areaQueryParams.value = { pageNum: 1, pageSize: 10, areaCode: null, areaName: null }
  getAreaList()
}

function handleAreaSelect(row: any) {
  form.value.areaId = row.areaId
  form.value.areaCode = row.areaCode
  form.value.areaName = row.areaName
  areaSelectOpen.value = false
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
