<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="miscRecptRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="入库单编号" prop="recptCode">
              <el-input v-model="form.recptCode" placeholder="请输入入库单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入库类型" prop="recptType">
              <el-select v-model="form.recptType" placeholder="请选择入库类型">
                <el-option v-for="dict in wm_misc_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入库日期" prop="recptDate">
              <el-date-picker clearable v-model="form.recptDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择入库日期" style="width: 100%" />
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

        <el-divider content-position="center">入库行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="lineList" :row-class-name="rowLineIndex" @selection-change="handleLineSelectionChange">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请选择物料" v-if="!isViewMode" readonly>
                <template #append><el-button icon="Search" @click="openMaterialSelect(scope.$index)" /></template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="物料名称" v-if="!isViewMode" readonly />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规格型号" prop="materialSpec" width="120">
            <template #default="scope"><span>{{ scope.row.materialSpec }}</span></template>
          </el-table-column>
          <el-table-column label="数量" prop="quantity" width="100">
            <template #default="scope">
              <el-input v-model="scope.row.quantity" placeholder="数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.quantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="100">
            <template #default="scope">
              <el-input v-model="scope.row.unitPrice" placeholder="单价" v-if="!isViewMode" />
              <span v-else>{{ scope.row.unitPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="100">
            <template #default="scope"><span>{{ scope.row.amount }}</span></template>
          </el-table-column>
          <el-table-column label="库位" prop="locationCode" width="100">
            <template #default="scope">
              <el-input v-model="scope.row.locationCode" placeholder="库位" v-if="!isViewMode" />
              <span v-else>{{ scope.row.locationCode }}</span>
            </template>
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

    <el-dialog :title="materialDialogTitle" v-model="materialSelectOpen" width="1000px" append-to-body>
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
  </div>
</template>

<script setup lang="ts" name="MiscRecptForm">
import { getMiscRecpt, addMiscRecpt, updateMiscRecpt } from "@/api/erp/wm/miscrecpt"
import { listMaterial } from "@/api/erp/bd/material"
import { listWarehouse } from "@/api/erp/bd/warehouse"
import { listStoragearea } from "@/api/erp/wm/storagearea"
import { getMeasure } from "@/api/erp/bd/measure"
import { useRoute, useRouter } from 'vue-router'

const { proxy } = getCurrentInstance() as any
const { wm_misc_type } = toRefs<any>(proxy.useDict('wm_misc_type'))
const route = useRoute()
const router = useRouter()

const form = ref<any>({})
const isViewMode = ref(false)
const title = ref("")
const lineList = ref<any[]>([])
const checkedLines = ref<any[]>([])

const materialSelectOpen = ref(false)
const materialList = ref([])
const materialLoading = ref(false)
const materialTotal = ref(0)
const materialDialogTitle = ref("选择物料")
const currentMaterialRowIndex = ref(-1)
const materialQueryParams = ref({ pageNum: 1, pageSize: 10, materialCode: null, materialName: null })

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
  recptCode: [{ required: true, message: "入库单编号不能为空", trigger: "blur" }]
}

onMounted(() => {
  const id = route.query.id
  const action = route.query.action as string
  isViewMode.value = action === 'view'
  if (action === 'add') { title.value = "新增杂项入库"; reset() }
  else if (action === 'edit') { title.value = "修改杂项入库"; if (id) getInfo(id) }
  else if (action === 'view') { title.value = "查看杂项入库"; if (id) getInfo(id) }
})

function getInfo(id: any) {
  getMiscRecpt(id).then((response: any) => {
    form.value = response.data
    lineList.value = response.data.wmMiscRecptLineList || []
  })
}

function reset() {
  form.value = { recptId: null, recptCode: null, recptType: null, warehouseId: null, warehouseCode: null, warehouseName: null, areaId: null, areaCode: null, areaName: null, recptDate: null, status: null, remark: null }
  lineList.value = []
  proxy.resetForm("miscRecptRef")
}

function submitForm() {
  proxy.$refs["miscRecptRef"].validate((valid: boolean) => {
    if (valid) {
      form.value.wmMiscRecptLineList = lineList.value
      if (form.value.recptId != null) {
        updateMiscRecpt(form.value).then(() => { proxy.$modal.msgSuccess("修改成功"); router.push('/erp/wm/miscrecpt') })
      } else {
        addMiscRecpt(form.value).then(() => { proxy.$modal.msgSuccess("新增成功"); router.push('/erp/wm/miscrecpt') })
      }
    }
  })
}

function cancel() { proxy.$tab.closeOpenPage({ path: '/erp/wm/miscrecpt' }) }

function rowLineIndex({ row, rowIndex }: any) { row.index = rowIndex + 1 }

function handleAddLine() {
  lineList.value.push({ materialId: null, materialCode: '', materialName: '', materialSpec: '', baseUomId: '', baseUomName: '', quantity: null, unitPrice: null, amount: null, locationCode: '', remark: '' })
}

function handleDeleteLine() {
  if (checkedLines.value.length == 0) { proxy.$modal.msgError("请先选择要删除的行"); return }
  lineList.value = lineList.value.filter((item: any) => checkedLines.value.indexOf(item.index) == -1)
}

function handleLineSelectionChange(selection: any[]) { checkedLines.value = selection.map((item: any) => item.index) }

function openMaterialSelect(rowIndex: number) { currentMaterialRowIndex.value = rowIndex; materialSelectOpen.value = true; getMaterialList() }

function getMaterialList() {
  materialLoading.value = true
  listMaterial(materialQueryParams.value).then((response: any) => { materialList.value = response.rows; materialTotal.value = response.total; materialLoading.value = false })
}

function handleMaterialQuery() { materialQueryParams.value.pageNum = 1; getMaterialList() }

function resetMaterialQuery() {
  materialQueryParams.value = { pageNum: 1, pageSize: 10, materialCode: null, materialName: null }
  handleMaterialQuery()
}

function selectMaterial(row: any) {
  if (currentMaterialRowIndex.value >= 0 && lineList.value[currentMaterialRowIndex.value]) {
    const idx = currentMaterialRowIndex.value
    lineList.value[idx].materialId = row.materialId
    lineList.value[idx].materialCode = row.materialCode
    lineList.value[idx].materialName = row.materialName
    lineList.value[idx].materialSpec = row.materialSpec
    if (row.baseUomId) {
      lineList.value[idx].baseUomId = row.baseUomId
      getMeasure(row.baseUomId).then((r: any) => { if (idx < lineList.value.length) lineList.value[idx].baseUomName = r.data.uomName })
    }
  }
  materialSelectOpen.value = false
  currentMaterialRowIndex.value = -1
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
