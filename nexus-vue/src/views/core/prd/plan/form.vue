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

      <el-form ref="planRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="生产计划编号" prop="planNo">
              <el-input v-model="form.planNo" placeholder="请输入生产计划编号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入计划名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="版本号" prop="version">
              <el-input v-model="form.version" placeholder="请输入版本号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="原始计划ID" prop="originalPlanId">
              <el-input v-model="form.originalPlanId" placeholder="请输入原始计划ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="物料需求单ID" prop="materialRequirementId">
              <el-input v-model="form.materialRequirementId" placeholder="请输入物料需求单ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="物料需求单编号" prop="materialRequirementNo">
              <el-input v-model="form.materialRequirementNo" placeholder="请输入物料需求单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="参考单据号" prop="referenceNo">
              <el-input v-model="form.referenceNo" placeholder="请输入参考单据号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划类型" prop="planType">
              <el-select v-model="form.planType" placeholder="请选择计划类型">
                <el-option v-for="dict in prd_plan_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划日期" prop="planDate">
              <el-date-picker clearable v-model="form.planDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划开始日期" prop="startDate">
              <el-date-picker clearable v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划开始日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="计划结束日期" prop="endDate">
              <el-date-picker clearable v-model="form.endDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划结束日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in prd_plan_status" :key="dict.value" :label="dict.value">{{ dict.label
                  }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择优先级">
                <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划员ID" prop="plannerId">
              <el-input v-model="form.plannerId" placeholder="请输入计划员ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="计划员" prop="plannerName">
              <el-input v-model="form.plannerName" placeholder="请输入计划员" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="生产部门ID" prop="departmentId">
              <el-input v-model="form.departmentId" placeholder="请输入生产部门ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="生产部门" prop="departmentName">
              <el-input v-model="form.departmentName" placeholder="请输入生产部门" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划总数量" prop="totalQuantity">
              <el-input v-model="form.totalQuantity" placeholder="请输入计划总数量" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="确认时间" prop="confirmedTime">
              <el-date-picker clearable v-model="form.confirmedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择确认时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="下达时间" prop="releasedTime">
              <el-date-picker clearable v-model="form.releasedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择下达时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="关闭时间" prop="closedTime">
              <el-date-picker clearable v-model="form.closedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择关闭时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="BOM名称" prop="bomName">
              <el-input v-model="form.bomName" placeholder="BOM名称" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openBomSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="预留字段2" prop="attr2">
              <el-input v-model="form.attr2" placeholder="请输入预留字段2" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="BOM ID" prop="bomId">
              <el-input v-model="form.bomId" placeholder="BOM ID" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预留字段4" prop="attr4">
              <el-input v-model="form.attr4" placeholder="请输入预留字段4" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 订单信息 -->
        <el-card class="box-card" shadow="never" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>订单信息</span>
            </div>
          </template>
          <!-- 订单行信息表格 -->
          <el-table :data="orderLineList" v-loading="orderLineLoading" style="width: 100%; margin-top: 20px;">
            <el-table-column label="行号" prop="lineNo" width="80" />
            <el-table-column label="物料编码" prop="materialCode" width="150" />
            <el-table-column label="物料名称" prop="materialName" min-width="200" />
            <el-table-column label="单位" prop="unit" width="100" />
            <el-table-column label="订单数量" prop="orderedQty" width="120" />
            <el-table-column label="备注" prop="remark" min-width="150" />
          </el-table>
        </el-card>
        
        <!-- 物料清单 -->
        <el-card class="box-card" shadow="never" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>物料清单</span>
              <el-button type="primary" @click="addMaterialItem" v-if="!isViewMode">添加物料</el-button>
            </div>
          </template>
          <el-table :data="materialItems" style="width: 100%">
            <el-table-column type="index" width="50" label="序号" align="center" />
            <el-table-column label="物料编码" prop="materialCode" width="150">
              <template #default="scope">
                <el-input v-model="scope.row.materialCode" placeholder="请输入物料编码" v-if="!isViewMode" />
                <span v-else>{{ scope.row.materialCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="物料名称" prop="materialName" min-width="200">
              <template #default="scope">
                <el-input v-model="scope.row.materialName" placeholder="请输入物料名称" v-if="!isViewMode" />
                <span v-else>{{ scope.row.materialName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="规格型号" prop="specification" width="150">
              <template #default="scope">
                <el-input v-model="scope.row.specification" placeholder="请输入规格型号" v-if="!isViewMode" />
                <span v-else>{{ scope.row.specification }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单位" prop="unit" width="100">
              <template #default="scope">
                <el-input v-model="scope.row.unit" placeholder="请输入单位" v-if="!isViewMode" />
                <span v-else>{{ scope.row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="需求数量" prop="requiredQuantity" width="120">
              <template #default="scope">
                <el-input-number v-model="scope.row.requiredQuantity" :min="0" v-if="!isViewMode" />
                <span v-else>{{ scope.row.requiredQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已分配数量" prop="allocatedQuantity" width="120">
              <template #default="scope">
                <el-input-number v-model="scope.row.allocatedQuantity" :min="0" v-if="!isViewMode" />
                <span v-else>{{ scope.row.allocatedQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center" v-if="!isViewMode">
              <template #default="scope">
                <el-button type="danger" icon="Delete" circle @click="removeMaterialItem(scope.$index)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-form>
    </el-card>
    
    <!-- BOM选择对话框 -->
    <el-dialog :title="bomDialogTitle" v-model="bomSelectOpen" width="1200px" append-to-body>
      <el-form :model="bomQueryParams" ref="bomQueryRef" :inline="true" label-width="80px">
        <el-form-item label="BOM名称" prop="bomName">
          <el-input
            v-model="bomQueryParams.bomName"
            placeholder="请输入BOM名称"
            clearable
            @keyup.enter="handleBomQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleBomQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetBomQuery">重置</el-button>
        </el-form-item>
      </el-form>
      
      <el-table 
        v-loading="bomLoading" 
        :data="bomList" 
        @row-dblclick="selectBom"
        highlight-current-row
        style="width: 100%"
        height="400"
      >
        <el-table-column type="index" width="50" align="center" />
        <el-table-column label="BOM版本号" align="center" prop="bomVersion" />
        <el-table-column label="BOM名称" align="center" prop="bomName" />
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.effectiveDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失效日期" align="center" prop="expiryDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.expiryDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="是否当前版本" align="center" prop="isCurrent">
          <template #default="scope">
            <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent"/>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <dict-tag :options="sys_core_status" :value="scope.row.status"/>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectBom(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <pagination
        v-show="bomTotal > 0"
        :total="bomTotal"
        v-model:page="bomQueryParams.pageNum"
        v-model:limit="bomQueryParams.pageSize"
        @pagination="getBomList"
      />
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="bomSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PlanForm">
import { getPlan, addPlan, updatePlan,updatePlanVersion } from "@/api/core/prd/plan"
import { getOrderLine } from "@/api/core/so/orderheader"
import { listBom } from "@/api/core/bd/bom"

const { proxy } = getCurrentInstance()
const { prd_plan_type, prd_plan_status, so_order_priority, so_order_line_status, sys_true_or_false, sys_core_status } = proxy.useDict('prd_plan_type', 'prd_plan_status', 'so_order_priority', 'so_order_line_status', 'sys_true_or_false', 'sys_core_status')
const route = useRoute()
const router = useRouter()

const form = ref({})
const isViewMode = ref(false)
const title = ref("")

// BOM选择相关变量
const bomSelectOpen = ref(false)
const bomList = ref([])
const bomLoading = ref(false)
const bomTotal = ref(0)
const bomDialogTitle = ref("选择BOM")
const bomQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  bomName: null,
  isCurrent: 1,  // 只显示当前版本
  status: 1      // 只显示启用状态
})

const materialItems = ref([
  {
    materialCode: "",
    materialName: "",
    specification: "",
    unit: "",
    requiredQuantity: 0,
    allocatedQuantity: 0
  }
])
const orderLineList = ref([]) // 存储订单行信息
const orderLineLoading = ref(false) // 订单行信息加载状态

onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  // 设置查看模式
  isViewMode.value = action === 'view'
   console.log('form.value:', form.value);
  console.log('id:', id);

  if (id) {
    initPage(id)
  }
})

function initPage(id) {
  const action = route.query.action

  if (action === 'view') {
    // 查看模式
    isViewMode.value = true
    title.value = "查看生产计划"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false
    title.value = "修改生产计划"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false
    title.value = "新增生产计划"
    reset()
  }
}

// 获取详细信息
function getInfo(id) {
  getPlan(id).then(response => {
    form.value = response.data
    // 获取订单行信息
    if (form.value.referenceNo) {
      loadOrderLines(form.value.referenceNo)
    }
  })
}

// 加载订单行信息
function loadOrderLines(referenceNo) {
  orderLineLoading.value = true
  getOrderLine(referenceNo).then(response => {
    orderLineList.value = response.data || []
    orderLineLoading.value = false
  }).catch(error => {
    proxy.$modal.msgError("获取订单行信息失败: " + error.message)
    orderLineLoading.value = false
  })
}

// 表单重置
function reset() {
  form.value = {
    planId: null,
    planNo: null,
    planName: null,
    version: null,
    originalPlanId: null,
    referenceNo: null,
    planType: null,
    planDate: null,
    startDate: null,
    endDate: null,
    status: null,
    priority: null,
    plannerId: null,
    plannerName: null,
    departmentId: null,
    departmentName: null,
    totalQuantity: null,
    confirmedTime: null,
    releasedTime: null,
    closedTime: null,
    bomName: null,
    attr2: null,
    bomId: null,
    attr4: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    referenceNo: null,
    clientName: null,
    orderDate: null,
    deliveryDate: null,
    orderStatus: null,
    orderQuantity: 0,
    deliveredQuantity: 0,
    remainingQuantity: 0,
    materialRequirementId: null,
    materialRequirementNo: null
  }
  materialItems.value = [
    {
      materialCode: "",
      materialName: "",
      specification: "",
      unit: "",
      requiredQuantity: 0,
      allocatedQuantity: 0
    }
  ]
  proxy.resetForm("planRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["planRef"].validate(valid => {
    if (valid) {
      console.log('form.value.planId:', form.value.planId);
      
      // 确保传递给 updatePlanVersion 的数据包含 planId
      const formData = {...form.value};
      if (formData.planId) {
        updatePlanVersion(formData).then(response => {
          proxy.$modal.msgSuccess("版本变更成功")
          router.push('/core/prd/plan')
        }).catch(error => {
          proxy.$modal.msgError("版本变更失败: " + (error.message || "未知错误"));
        });
      } else {
        proxy.$modal.msgError("生产计划ID不存在，无法更新版本");
      }
    }
  })
}

// 取消按钮
function cancel() {
  const obj = { path: '/core/prd/plan' }
  proxy.$tab.closeOpenPage(obj)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
}

// 添加物料行
function addMaterialItem() {
  materialItems.value.push({
    materialCode: "",
    materialName: "",
    specification: "",
    unit: "",
    requiredQuantity: 0,
    allocatedQuantity: 0
  })
}

// 删除物料行
function removeMaterialItem(index) {
  if (materialItems.value.length > 1) {
    materialItems.value.splice(index, 1)
  } else {
    proxy.$modal.msgWarning("至少保留一行物料信息")
  }
}

// 打开BOM选择对话框
function openBomSelect() {
  bomSelectOpen.value = true
  bomDialogTitle.value = "选择BOM"
  getBomList()
}

// 查询BOM列表
function getBomList() {
  bomLoading.value = true
  // 确保只显示当前版本(isCurrent=1)和启用状态(status=1)的数据
  const params = {
    ...bomQueryParams.value,
    isCurrent: 1,
    status: 1
  }
  
  listBom(params).then(response => {
    bomList.value = response.rows
    bomTotal.value = response.total
    bomLoading.value = false
  }).catch(error => {
    console.error("获取BOM列表失败:", error)
    proxy.$modal.msgError("获取BOM列表失败")
    bomLoading.value = false
  })
}

// 搜索BOM
function handleBomQuery() {
  bomQueryParams.value.pageNum = 1
  getBomList()
}

// 重置BOM查询
function resetBomQuery() {
  proxy.resetForm("bomQueryRef")
  bomQueryParams.value.bomName = null
  handleBomQuery()
}

// 选择BOM
function selectBom(row) {
  form.value.bomId = row.bomId
  form.value.bomName = row.bomName
  bomSelectOpen.value = false
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.box-card {
  margin-bottom: 20px;
}

.box-card:last-child {
  margin-bottom: 0;
}
</style>