<template>
  <div class="app-container">
    <!-- 采购订单表单卡片 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <!-- 保存按钮，仅在非查看模式下显示 -->
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <!-- 取消按钮 -->
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>
      
      <!-- 表单区域 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" :disabled="isViewMode">
        <!-- 第一行：订单号、采购申请号、订单日期 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="订单号" prop="orderNo">
              <el-input v-model="form.orderNo" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购申请号" prop="requisitionNo">
              <el-input v-model="form.requisitionNo" readonly>
                <template #append>
                  <!-- 选择请购单按钮 -->
                  <el-button icon="Search" @click="handleSelectRequisition" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="订单日期" prop="orderDate">
              <el-date-picker 
                v-model="form.orderDate" 
                type="date" 
                value-format="YYYY-MM-DD" 
                style="width:100%" 
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 第二行：供应商、预计到货日期、状态 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorName">
              <el-input v-model="form.vendorName" readonly>
                <template #append>
                  <!-- 选择供应商按钮 -->
                  <el-button icon="Search" @click="openVendor" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预计到货" prop="expectedArrivalDate">
              <el-date-picker 
                v-model="form.expectedArrivalDate" 
                type="date" 
                value-format="YYYY-MM-DD" 
                style="width:100%" 
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status">
                <el-option 
                  v-for="dict in pu_purchase_order_status" 
                  :key="dict.value" 
                  :label="dict.label" 
                  :value="dict.value" 
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 第三行：币别、汇率、备注 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="币别" prop="currency">
              <el-select v-model="form.currency">
                <el-option 
                  v-for="dict in so_currency_type" 
                  :key="dict.value" 
                  :label="dict.label" 
                  :value="dict.value" 
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="汇率" prop="exchangeRate">
              <el-input v-model="form.exchangeRate" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 订单明细分隔符 -->
        <el-divider content-position="center">订单明细</el-divider>
        
        <!-- 明细操作按钮 -->
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        
        <!-- 订单明细表格 -->
        <el-table :data="lineList" @selection-change="handleLineSelectionChange" border>
          <el-table-column type="selection" width="50" v-if="!isViewMode" />
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
              <el-input v-model="scope.row.orderQuantity" />
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

    <!-- 供应商选择对话框 -->
    <el-dialog v-model="vendorDialog.visible" title="选择供应商" width="900px" append-to-body>
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
      </el-form>
      <el-table :data="vendorDialog.list" height="400" @row-dblclick="selectVendor">
        <el-table-column label="供应商编码" prop="vendorCode" />
        <el-table-column label="供应商名称" prop="vendorName" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectVendor(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 物料选择对话框 -->
    <el-dialog v-model="materialDialog.visible" title="选择物料" width="900px" append-to-body>
      <el-form :model="materialDialog.query" :inline="true">
        <el-form-item label="物料编码">
          <el-input v-model="materialDialog.query.materialCode" clearable @keyup.enter="queryMaterials" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="materialDialog.query.materialName" clearable @keyup.enter="queryMaterials" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryMaterials">搜索</el-button>
          <el-button icon="Refresh" @click="resetMaterialQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="materialDialog.list" height="400" @row-dblclick="selectMaterial">
        <el-table-column label="物料编码" prop="materialCode" />
        <el-table-column label="物料名称" prop="materialName" />
        <el-table-column label="规格" prop="materialSpec" />
        <el-table-column label="单位" prop="baseUomName" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectMaterial(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PurchaseOrderForm">
// 导入采购订单相关的API方法
import { addPurchaseOrder, getPurchaseOrder, updatePurchaseOrder, generateFromRequisition } from '@/api/erp/pu/order'
// 导入物料管理API
import { listMaterial } from '@/api/erp/bd/material'
// 导入供应商管理API
import { listVendor } from '@/api/erp/bd/vendor'
// 导入路由相关功能
import { useRoute, useRouter } from 'vue-router'

// 获取当前实例的代理对象和字典数据
const { proxy } = getCurrentInstance() as any
const { pu_purchase_order_status, so_currency_type } = toRefs<any>(proxy.useDict('pu_purchase_order_status', 'so_currency_type'))

// 路由和导航
const route = useRoute()
const router = useRouter()

// 页面状态变量
const title = ref('')                    // 页面标题
const isViewMode = ref(false)            // 是否为查看模式
const form = ref<any>({})               // 表单数据
const lineList = ref<any[]>([])          // 订单明细列表
const checkedLines = ref<any[]>([])      // 已选中的明细行
const currentMaterialIndex = ref(-1)     // 当前选择的物料索引

// 供应商选择对话框数据
const vendorDialog = reactive({
  visible: false,                        // 对话框是否可见
  list: [] as any[],                     // 供应商列表
  query: {                               // 查询条件
    pageNum: 1,
    pageSize: 10,
    vendorCode: null,
    vendorName: null
  }
})

// 物料选择对话框数据
const materialDialog = reactive({
  visible: false,                        // 对话框是否可见
  list: [] as any[],                     // 物料列表
  query: {                               // 查询条件
    pageNum: 1,
    pageSize: 10,
    materialCode: null,
    materialName: null
  }
})

// 表单验证规则
const rules = {
  orderNo: [
    { required: true, message: '订单号不能为空', trigger: 'blur' }
  ]
}

// 组件挂载后的初始化逻辑
onMounted(() => {
  // 获取路由参数
  const id = route.query.id as any
  const action = route.query.action as string
  
  // 设置页面模式和标题
  isViewMode.value = action === 'view'
  title.value = action === 'add' ? '新增采购订单' : action === 'edit' ? '修改采购订单' : '查看采购订单'
  
  // 根据操作类型初始化页面
  if (action === 'add') {
    reset() // 新增操作：重置表单
  } else if (id) {
    getInfo(id) // 编辑/查看操作：获取详情数据
  }
})

/**
 * 根据ID获取采购订单详情
 * @param id 采购订单ID
 */
function getInfo(id: any) {
  getPurchaseOrder(id).then((res: any) => {
    form.value = res.data
    lineList.value = res.data.lineList || res.data.puPurchaseOrderLineList || []
  })
}

/**
 * 重置表单数据
 */
function reset() {
  form.value = {
    orderId: null,
    orderNo: null,
    requisitionId: null,
    requisitionNo: null,
    orderDate: null,
    expectedArrivalDate: null,
    status: 'DRAFT',
    currency: 'CNY',
    exchangeRate: 1,
    remark: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null
  }
  lineList.value = []
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
    amount: null,
    remark: null
  })
}

/**
 * 删除订单明细行
 */
function handleDeleteLine() {
  lineList.value = lineList.value.filter((_, i) => !checkedLines.value.includes(i))
}

/**
 * 处理明细表格的选择变化
 * @param rows 选中的行数据
 */
function handleLineSelectionChange(rows: any[]) {
  checkedLines.value = rows.map((r: any) => lineList.value.indexOf(r))
}

/**
 * 打开物料选择对话框
 * @param index 当前行索引
 */
function openMaterial(index: number) {
  currentMaterialIndex.value = index
  materialDialog.visible = true
  queryMaterials()
}

/**
 * 查询物料列表
 */
function queryMaterials() {
  listMaterial(materialDialog.query as any).then((res: any) => {
    materialDialog.list = res.rows || res.data || []
    materialDialog.visible = true
  })
}

/**
 * 重置物料查询条件
 */
function resetMaterialQuery() {
  materialDialog.query = {
    pageNum: 1,
    pageSize: 10,
    materialCode: null,
    materialName: null
  }
  queryMaterials()
}

/**
 * 选择物料
 * @param row 选中的物料数据
 */
function selectMaterial(row: any) {
  if (currentMaterialIndex.value < 0) return
  const target = lineList.value[currentMaterialIndex.value]
  target.materialCode = row.materialCode
  target.materialName = row.materialName
  target.materialSpec = row.materialSpec
  target.unit = row.baseUomName || row.unitName || row.unit
  materialDialog.visible = false
}

/**
 * 打开供应商选择对话框
 */
function openVendor() {
  vendorDialog.visible = true
  queryVendors()
}

/**
 * 查询供应商列表
 */
function queryVendors() {
  listVendor(vendorDialog.query as any).then((res: any) => {
    vendorDialog.list = res.rows || res.data || []
    vendorDialog.visible = true
  })
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
  }
  queryVendors()
}

/**
 * 选择供应商
 * @param row 选中的供应商数据
 */
function selectVendor(row: any) {
  form.value.vendorId = row.vendorId
  form.value.vendorCode = row.vendorCode
  form.value.vendorName = row.vendorName
  vendorDialog.visible = false
}

/**
 * 根据请购单生成采购订单
 */
function handleSelectRequisition() {
  if (!form.value.requisitionId) {
    proxy.$modal.msgWarning('请先选择采购申请ID后生成')
    return
  }
  generateFromRequisition(form.value.requisitionId).then((res: any) => {
    const data = res.data || res
    if (data) {
      form.value = { ...form.value, ...data }
      lineList.value = data.lineList || data.puPurchaseOrderLineList || lineList.value
    }
    proxy.$modal.msgSuccess('已根据申请单生成采购订单')
  })
}

/**
 * 提交表单
 */
function submitForm() {
  form.value.lineList = lineList.value
  ;(form.value.orderId ? updatePurchaseOrder(form.value) : addPurchaseOrder(form.value)).then(() => {
    proxy.$modal.msgSuccess('保存成功')
    router.push('/erp/pu/order')
  })
}

/**
 * 取消并返回列表页
 */
function cancel() {
  router.push('/erp/pu/order')
}
</script>