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

      <el-form ref="quotationRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="报价单编号" prop="quotationNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.quotationNo" placeholder="请输入报价单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="报价日期" prop="quotationDate">
              <el-date-picker clearable v-model="form.quotationDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择报价日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="有效期至" prop="validUntil">
              <el-date-picker clearable v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" placeholder="请选择有效期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="客户" prop="clientName">
              <el-input v-model="form.clientName" placeholder="请选择客户" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="clientSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售员" prop="salesRepName">
              <el-input v-model="form.salesRepName" placeholder="请选择销售员" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="userSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币别" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择币别">
                <el-option v-for="dict in sys_currency" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">价格信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="汇率" prop="exchangeRate">
              <el-input v-model="form.exchangeRate" placeholder="请输入汇率" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣率(%)" prop="discountRate">
              <el-input v-model="form.discountRate" placeholder="请输入折扣率" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="付款条件" prop="paymentTerms">
              <el-input v-model="form.paymentTerms" placeholder="请输入付款条件" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">其他信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="交货条件" prop="deliveryTerms">
              <el-input v-model="form.deliveryTerms" placeholder="请输入交货条件" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">报价行信息</el-divider>
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
          <el-table-column label="税率(%)" prop="taxRate" width="80">
            <template #default="scope">
              <el-input v-model="scope.row.taxRate" placeholder="税率" v-if="!isViewMode" />
              <span v-else>{{ scope.row.taxRate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="交货日期" prop="deliveryDate" width="120">
            <template #default="scope">
              <el-date-picker v-model="scope.row.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="交货日期" v-if="!isViewMode" />
              <span v-else>{{ scope.row.deliveryDate }}</span>
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

    <el-dialog title="选择客户" v-model="clientSelectOpen" width="800px" append-to-body>
      <el-form :model="clientQueryParams" :inline="true">
        <el-form-item label="客户编码"><el-input v-model="clientQueryParams.clientCode" placeholder="请输入客户编码" clearable @keyup.enter="getClientList" /></el-form-item>
        <el-form-item label="客户名称"><el-input v-model="clientQueryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="getClientList" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="getClientList">搜索</el-button><el-button icon="Refresh" @click="resetClientQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="clientLoading" :data="clientList" highlight-current-row @current-change="handleClientSelect">
        <el-table-column label="客户编码" align="center" prop="clientCode" />
        <el-table-column label="客户名称" align="center" prop="clientName" />
        <el-table-column label="联系人" align="center" prop="contactPerson" />
        <el-table-column label="操作" align="center"><template #default="scope"><el-button link type="primary" @click="handleClientSelect(scope.row)">选择</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="clientTotal > 0" :total="clientTotal" v-model:page="clientQueryParams.pageNum" v-model:limit="clientQueryParams.pageSize" @pagination="getClientList" />
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

<script setup lang="ts" name="QuotationForm">
import { getQuotation, addQuotation, updateQuotation } from "@/api/erp/so/quotation"
import { listMaterial } from "@/api/erp/bd/material"
import { listClient } from "@/api/erp/bd/client"
import { getMeasure } from "@/api/erp/bd/measure"
import { listUserByPostId } from "@/api/system/user"
import { previewCode } from "@/api/system/autocode/rule"
import { useRoute, useRouter } from 'vue-router'

const { proxy } = getCurrentInstance() as any
const { sys_currency } = toRefs<any>(proxy.useDict('sys_currency'))
const route = useRoute()
const router = useRouter()

const form = ref<any>({})
const autoGenFlag = ref(false)
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

const clientSelectOpen = ref(false)
const clientList = ref([])
const clientLoading = ref(false)
const clientTotal = ref(0)
const clientQueryParams = ref({ pageNum: 1, pageSize: 10, clientCode: null, clientName: null })

const userSelectOpen = ref(false)

const rules = {
  quotationNo: [{ required: true, message: "报价单编号不能为空", trigger: "blur" }]
}

onMounted(() => {
  const id = route.query.id
  const action = route.query.action as string
  isViewMode.value = action === 'view'
  if (action === 'add') { title.value = "新增报价单"; reset() }
  else if (action === 'edit') { title.value = "修改报价单"; if (id) getInfo(id) }
  else if (action === 'view') { title.value = "查看报价单"; if (id) getInfo(id) }
})

function getInfo(id: any) {
  getQuotation(id).then((response: any) => {
    form.value = response.data
    lineList.value = response.data.soQuotationLineList || []
  })
}

function reset() {
  autoGenFlag.value = false
  form.value = { quotationId: null, quotationNo: null, clientId: null, clientName: null, salesRepId: null, salesRepName: null, quotationDate: null, validUntil: null, currency: 'CNY', exchangeRate: 1, totalAmount: null, taxAmount: null, discountAmount: null, discountRate: null, paymentTerms: null, deliveryTerms: null, status: null, remark: null }
  lineList.value = []
  proxy.resetForm("quotationRef")
}

function submitForm() {
  proxy.$refs["quotationRef"].validate((valid: boolean) => {
    if (valid) {
        // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
        if (autoGenFlag.value) {
          form.value.quotationNo = undefined;
        }
      form.value.soQuotationLineList = lineList.value
      if (form.value.quotationId != null) {
        updateQuotation(form.value).then(() => { proxy.$modal.msgSuccess("修改成功"); router.push('/erp/so/quotation') })
      } else {
        addQuotation(form.value).then(() => { proxy.$modal.msgSuccess("新增成功"); router.push('/erp/so/quotation') })
      }
    }
  })
}

//自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('SO_QUOTATION_CODE').then(response => {
      form.value.quotationNo = response;
    });
  } else {
    form.value.quotationNo = null;
  }
}

function cancel() { proxy.$tab.closeOpenPage({ path: '/erp/so/quotation' }) }

function rowLineIndex({ row, rowIndex }: any) { row.index = rowIndex + 1 }

function handleAddLine() {
  lineList.value.push({ materialId: null, materialCode: '', materialName: '', materialSpec: '', baseUomId: '', baseUomName: '', quantity: null, unitPrice: null, amount: null, taxRate: null, deliveryDate: null, remark: '' })
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

function getClientList() {
  clientLoading.value = true
  listClient(clientQueryParams.value).then((response: any) => { clientList.value = response.rows; clientTotal.value = response.total; clientLoading.value = false })
}

function resetClientQuery() {
  clientQueryParams.value = { pageNum: 1, pageSize: 10, clientCode: null, clientName: null }
  getClientList()
}

function handleClientSelect(row: any) {
  form.value.clientId = row.clientId
  form.value.clientName = row.clientName
  clientSelectOpen.value = false
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
