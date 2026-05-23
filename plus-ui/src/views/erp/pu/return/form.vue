<template>
  <div class="app-container">
    <!-- 采购退货单表单卡片 -->
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
        <!-- 第一行：退货单号、采购订单、退货日期 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="退货单号" prop="returnNo">
              <el-input v-model="form.returnNo" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购订单" prop="orderNo">
              <el-input v-model="form.orderNo" readonly>
                <template #append>
                  <!-- 选择订单按钮 -->
                  <el-button icon="Search" @click="handleSelectOrder" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker 
                v-model="form.returnDate" 
                type="date" 
                value-format="YYYY-MM-DD" 
                style="width:100%" 
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 第二行：供应商、状态、退货原因 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商" prop="vendorName">
              <el-input v-model="form.vendorName" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status">
                <el-option 
                  v-for="dict in pu_purchase_return_status" 
                  :key="dict.value" 
                  :label="dict.label" 
                  :value="dict.value" 
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货原因" prop="returnReason">
              <el-input v-model="form.returnReason" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 第三行：备注 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 退货明细分隔符 -->
        <el-divider content-position="center">退货明细</el-divider>
        
        <!-- 明细操作按钮 -->
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        
        <!-- 退货明细表格 -->
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
// 导入采购退货单相关的API方法
import { addPurchaseReturn, getPurchaseReturn, updatePurchaseReturn } from '@/api/erp/pu/return'
// 导入采购订单API，用于关联订单信息
import { getPurchaseOrder } from '@/api/erp/pu/order'
// 导入路由相关功能
import { useRoute, useRouter } from 'vue-router'

// 获取当前实例的代理对象和字典数据
const { proxy } = getCurrentInstance() as any
const { pu_purchase_return_status } = toRefs<any>(proxy.useDict('pu_purchase_return_status'))

// 路由和导航
const route = useRoute()
const router = useRouter()

// 页面状态变量
const title = ref('')                    // 页面标题
const isViewMode = ref(false)            // 是否为查看模式
const form = ref<any>({})               // 表单数据
const lineList = ref<any[]>([])          // 退货明细列表
const checkedLines = ref<any[]>([])      // 已选中的明细行

// 表单验证规则
const rules = {
  returnNo: [
    { required: true, message: '退货单号不能为空', trigger: 'blur' }
  ]
}

// 组件挂载后的初始化逻辑
onMounted(() => {
  // 获取路由参数
  const id = route.query.id as any
  const action = route.query.action as string
  
  // 设置页面模式和标题
  isViewMode.value = action === 'view'
  title.value = action === 'add' ? '新增采购退货' : action === 'edit' ? '修改采购退货' : '查看采购退货'
  
  // 根据操作类型初始化页面
  if (action === 'add') {
    reset() // 新增操作：重置表单
  } else if (id) {
    getInfo(id) // 编辑/查看操作：获取详情数据
  }
})

/**
 * 根据ID获取采购退货单详情
 * @param id 采购退货单ID
 */
function getInfo(id: any) {
  getPurchaseReturn(id).then((res: any) => {
    form.value = res.data
    lineList.value = res.data.puPurchaseReturnLineList || []
  })
}

/**
 * 重置表单数据
 */
function reset() {
  form.value = {
    returnId: null,
    returnNo: null,
    orderId: null,
    orderNo: null,
    returnDate: null,
    vendorName: null,
    status: 'DRAFT',
    returnReason: null,
    remark: null
  }
  lineList.value = []
}

/**
 * 添加退货明细行
 */
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

/**
 * 删除退货明细行
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
 * 选择关联的采购订单
 */
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
    
    // 根据原订单信息生成退货明细
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

/**
 * 提交表单
 */
function submitForm() {
  form.value.puPurchaseReturnLineList = lineList.value
  ;(form.value.returnId ? updatePurchaseReturn(form.value) : addPurchaseReturn(form.value)).then(() => {
    proxy.$modal.msgSuccess('保存成功')
    router.push('/erp/pu/return')
  })
}

/**
 * 取消并返回列表页
 */
function cancel() {
  router.push('/erp/pu/return')
}
</script>