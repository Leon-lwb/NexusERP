<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button type="success" @click="handleConfirm" v-if="form.planId && form.status === 'DRAFT'" v-hasPermi="['prd:plan:edit']">确认</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="planRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生产计划编号" prop="planNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.planNo" placeholder="请输入生产计划编号" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch
                  v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="自动生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode"
                />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售订单" prop="referenceNo">
              <el-input v-model="form.referenceNo" placeholder="请选择销售订单" readonly>
                <template #append>
                  <el-button v-if="!isViewMode && isAddMode" icon="Search" @click="handleSelectSalesOrder" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入计划名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="版本号" prop="version">
              <el-input v-model="form.version" placeholder="请输入版本号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料需求单编号" prop="materialRequirementNo">
              <el-input v-model="form.materialRequirementNo" placeholder="请输入物料需求单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">计划信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="计划类型" prop="planType">
              <el-select v-model="form.planType" placeholder="请选择计划类型">
                <el-option v-for="dict in prd_plan_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划日期" prop="planDate">
              <el-date-picker clearable v-model="form.planDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划开始日期" prop="startDate">
              <el-date-picker clearable v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划开始日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="计划结束日期" prop="endDate">
              <el-date-picker clearable v-model="form.endDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划结束日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划总数量" prop="totalQuantity">
              <el-input v-model="form.totalQuantity" placeholder="请输入计划总数量" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">状态信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <dict-tag :options="prd_plan_status" :value="form.status" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择优先级">
                <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划员" prop="plannerName">
              <el-input v-model="form.plannerName" placeholder="请选择计划员" readonly>
                <template #append>
                  <el-button icon="Search" @click="openUserSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生产部门" prop="deptId">
              <dept-select v-model="form.deptId" category="PRODUCTION" placeholder="请选择生产部门" @change="handleDeptChange" />
              <el-input v-model="form.deptName" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">时间信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="确认时间" prop="confirmedTime">
              <el-date-picker clearable v-model="form.confirmedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择确认时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="下达时间" prop="releasedTime">
              <el-date-picker clearable v-model="form.releasedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="工单审批通过后自动写入" disabled>
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关闭时间" prop="closedTime">
              <el-date-picker clearable v-model="form.closedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择关闭时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">BOM信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="BOM名称" prop="bomName">
              <el-input v-model="form.bomName" placeholder="BOM名称" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openBomSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="BOM版本号" prop="bomVersion">
              <el-input v-model="form.bomVersion" placeholder="请输入BOM版本号" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 订单信息 -->
        <el-divider content-position="center">订单信息</el-divider>
        <el-table :data="orderLineList" v-loading="orderLineLoading" style="width: 100%; margin-top: 20px;">
          <el-table-column label="行号" prop="lineNo" width="80" />
          <el-table-column label="物料编码" prop="materialCode" width="150" />
          <el-table-column label="物料名称" prop="materialName" min-width="200" />
          <el-table-column label="单位" prop="unit" width="100" />
          <el-table-column label="订单数量" prop="orderedQty" width="120" />
          <el-table-column label="备注" prop="remark" min-width="150" />
        </el-table>

        <!-- 物料清单 -->
        <el-divider content-position="center">物料清单</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" @click="handleBomCalculate" v-if="!isViewMode">BOM计算</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="primary" @click="handleMaterialList" v-if="form.planNo">物料清单</el-button>
          </el-col>
        </el-row>
        <el-table :data="materialTree" row-key="requirementId" style="width: 100%"
          :tree-props="{ children: 'children' }" default-expand-all>
          <el-table-column label="物料编码" prop="materialCode" width="180" />
          <el-table-column label="物料名称" prop="materialName" min-width="200" />
          <el-table-column label="规格型号" prop="materialSpec" width="150" />
          <el-table-column label="需求总量" prop="totalRequiredQuantity" width="120" />
          <el-table-column label="物料类型" width="100" align="center">
            <template #default="scope">
              <el-tag v-if="scope.row.sourceType === 'PROCUREMENT'" type="info" size="small">采购件</el-tag>
              <el-tag v-else-if="scope.row.sourceType === 'PROCESS'" size="small">加工件</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="下级物料" width="100" align="center">
            <template #default="scope">
              <el-popover v-if="scope.row.prdMaterialRequirementDetailList?.length" placement="right" width="650" trigger="click">
                <template #reference>
                  <el-button link type="primary" size="small">{{ scope.row.prdMaterialRequirementDetailList.length }}项</el-button>
                </template>
                <el-table :data="scope.row.prdMaterialRequirementDetailList" size="small" border>
                  <el-table-column label="物料编码" prop="materialCode" width="150" />
                  <el-table-column label="物料名称" prop="materialName" min-width="150" />
                  <el-table-column label="规格型号" prop="materialSpec" width="150" />
                  <el-table-column label="单位" prop="unit" width="80" />
                  <el-table-column label="BOM用量" prop="bomQuantity" width="100" />
                  <el-table-column label="损耗率(%)" prop="lossRate" width="100" />
                  <el-table-column label="需求数量" prop="requiredQuantity" width="120" />
                </el-table>
              </el-popover>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>

    <!-- 物料清单详情弹窗 -->
    <el-drawer
      v-model="materialListOpen"
      :title="materialListTitle"
      direction="rtl"
      size="80%"
    >
      <el-table :data="materialListData" height="100%" style="width: 100%">
        <el-table-column label="物料编码" prop="materialCode" width="150" />
        <el-table-column label="物料名称" prop="materialName" min-width="200" />
        <el-table-column label="规格型号" prop="materialSpec" width="150" />
        <el-table-column label="单位" prop="unit" width="100" />
        <el-table-column label="BOM标准用量" prop="bomQuantity" width="120" />
        <el-table-column label="损耗率(%)" prop="lossRate" width="120" />
        <el-table-column label="需求数量" prop="requiredQuantity" width="120" />
        <el-table-column label="已发料数量" prop="issuedQuantity" width="120" />
        <el-table-column label="当前库存数量" prop="stockQuantity" width="120" />
        <el-table-column label="缺料数量" prop="shortageQuantity" width="120" />
        <el-table-column label="物料类型" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.sourceType === 'PROCUREMENT'" type="info" size="small">采购件</el-tag>
            <el-tag v-else-if="scope.row.sourceType === 'PROCESS'" size="small">加工件</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="工序步骤" prop="operationStep" width="100" />
        <el-table-column label="需求日期" prop="requiredDate" width="120" />
      </el-table>
    </el-drawer>

    <!-- 销售订单选择对话框 -->
    <el-dialog title="选择销售订单" v-model="salesOrderOpen" width="1000px" append-to-body>
      <el-form :model="salesOrderQueryParams" ref="salesOrderQueryRef" :inline="true" label-width="90px">
        <el-form-item label="销售订单编号" prop="soNo">
          <el-input
            v-model="salesOrderQueryParams.soNo"
            placeholder="请输入销售订单编号"
            clearable
            @keyup.enter="handleSalesOrderQuery"
          />
        </el-form-item>
        <el-form-item label="客户名称" prop="clientName">
          <el-input
            v-model="salesOrderQueryParams.clientName"
            placeholder="请输入客户名称"
            clearable
            @keyup.enter="handleSalesOrderQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSalesOrderQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetSalesOrderQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="salesOrderLoading" :data="salesOrderList" @row-dblclick="selectSalesOrder" style="width: 100%">
        <el-table-column label="销售订单编号" align="center" prop="soNo" />
        <el-table-column label="客户名称" align="center" prop="clientName" />
        <el-table-column label="订单日期" align="center" prop="orderDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交期" align="center" prop="deliveryDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.deliveryDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" align="center" prop="orderStatus">
          <template #default="scope">
            <dict-tag :options="so_order_line_status" :value="scope.row.orderStatus" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectSalesOrder(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="salesOrderTotal > 0"
        :total="salesOrderTotal"
        v-model:page="salesOrderQueryParams.pageNum"
        v-model:limit="salesOrderQueryParams.pageSize"
        @pagination="getSalesOrderList"
      />
    </el-dialog>

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

    <!-- 计划员选择对话框 -->
    <el-dialog title="选择计划员" v-model="userSelectOpen" width="800px" append-to-body>
      <el-form :model="userQueryParams" :inline="true" label-width="80px">
        <el-form-item label="用户编码" prop="userName">
          <el-input v-model="userQueryParams.userName" placeholder="请输入用户编码" clearable @keyup.enter="handleUserQuery" />
        </el-form-item>
        <el-form-item label="用户名称" prop="nickName">
          <el-input v-model="userQueryParams.nickName" placeholder="请输入用户名称" clearable @keyup.enter="handleUserQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleUserQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="userLoading" :data="userList" @row-dblclick="selectUser" style="width: 100%">
        <el-table-column label="用户编码" align="center" prop="userName" />
        <el-table-column label="用户名称" align="center" prop="nickName" />
        <el-table-column label="部门" align="center" prop="deptName" />
        <el-table-column label="手机号" align="center" prop="phonenumber" />
        <el-table-column label="状态" align="center" prop="status" width="80">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="userTotal > 0"
        :total="userTotal"
        v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize"
        @pagination="getUserList"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PrdPlanForm">
import { getPlan, addPlan, updatePlan, updatePlanVersion, generateMaterialRequirementsWithDetails, listDetailsByPlanNo, confirmPlan } from "@/api/erp/prd/plan"
import { listOrderheader, getOrderLine } from "@/api/erp/so/orderheader"
import { listBom } from "@/api/erp/bd/bom"
import { listTreeByPlanNo } from "@/api/erp/prd/requirement"
import { listUser } from "@/api/system/user"
import { getAutoCode } from "@/api/system/autocode"
import { previewCode } from "@/api/system/autocode/rule"
import { useUserStore } from "@/store/modules/user"
import type { PlanForm } from "@/api/erp/prd/plan/types"

const { proxy } = getCurrentInstance() as any
const userStore = useUserStore()
const { prd_plan_type, prd_plan_status, so_order_priority, so_order_line_status, sys_true_or_false, sys_core_status, sys_normal_disable } = toRefs<any>(proxy.useDict('prd_plan_type', 'prd_plan_status', 'so_order_priority', 'so_order_line_status', 'sys_true_or_false', 'sys_core_status', 'sys_normal_disable'))
const route = useRoute()
const router = useRouter()

const form = ref<PlanForm>({})
const isViewMode = ref(false)
const isAddMode = ref(false)
const autoGenFlag = ref(false)
const title = ref("")
const salesOrderOpen = ref(false)
const salesOrderList = ref([])
const salesOrderLoading = ref(false)
const salesOrderTotal = ref(0)
const salesOrderQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  soNo: null,
  clientName: null,
  orderStatus: 'CONFIRMED'
})


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
  bomVersion: null,
  isCurrent: 1,
  status: 1
})

const materialTree = ref([])

// 物料清单弹窗相关变量
const materialListOpen = ref(false)
const materialListData = ref([])
const materialListTitle = ref("")

const orderLineList = ref([])
const orderLineLoading = ref(false)

// 计划员选择相关变量
const userSelectOpen = ref(false)
const userList = ref([])
const userLoading = ref(false)
const userTotal = ref(0)
const userQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  userName: null,
  nickName: null
})

onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  isViewMode.value = action === 'view'
  isAddMode.value = action === 'add' || !id || id === '0'

  if (id && id !== '0') {
    initPage(id)
  } else {
    initPage()
  }
})

function initPage(id) {
  const action = route.query.action

  if (action === 'view') {
    isViewMode.value = true
    title.value = "查看生产计划"
    if (id && id !== '0') {
      getInfo(id)
    }
  } else if (action === 'edit') {
    isViewMode.value = false
    isAddMode.value = false
    title.value = "修改生产计划"
    if (id && id !== '0') {
      getInfo(id)
    }
  } else if (action === 'add') {
    isViewMode.value = false
    isAddMode.value = true
    title.value = "新增生产计划"
    reset()
    autoGenFlag.value = true
    handleAutoGenChange(true)
  } else {
    isViewMode.value = false
    isAddMode.value = false
    title.value = "生产计划"
    if (id && id !== '0') {
      getInfo(id)
    } else {
      reset()
    }
  }
}

// 获取详细信息
function getInfo(id) {
  getPlan(id).then(response => {
    form.value = response.data
    if (form.value.referenceNo) {
      loadOrderLines(form.value.referenceNo)
    } else if (form.value.soLineId) {
      loadOrderLines(form.value.soLineId)
    } else if (form.value.orderLineId) {
      loadOrderLines(form.value.orderLineId)
    }
    if (response.data.planNo) {
      loadMaterialRequirements(response.data.planNo)
    }
  }).catch(error => {
    console.error('获取生产计划详情失败:', error);
    proxy.$modal.msgError("获取生产计划详情失败: " + error.message)
  })
}

// 加载订单行信息
function loadOrderLines(soNo) {
  return new Promise((resolve, reject) => {
    if (!soNo) {
      orderLineList.value = []
      resolve([])
      return
    }
    orderLineLoading.value = true
    getOrderLine(soNo).then(response => {
      orderLineList.value = response.data || []
      orderLineLoading.value = false
      resolve(orderLineList.value)
    }).catch(error => {
      console.error("获取订单行信息失败:", error);
      proxy.$modal.msgError("获取订单行信息失败: " + error.message)
      orderLineLoading.value = false
      reject(error)
    })
  })
}

function handleSelectSalesOrder() {
  salesOrderOpen.value = true
  resetSalesOrderQuery()
  getSalesOrderList()
}

function handleAutoGenChange(autoGenFlagValue) {
  if (autoGenFlagValue) {
    previewCode('PRD_PLAN_CODE').then(response => {
      form.value.planNo = response || response.data || ''
    }).catch(error => {
      console.error('自动生成生产计划编号失败:', error)
      proxy.$modal.msgError('自动生成生产计划编号失败: ' + (error.message || '未知错误'))
      autoGenFlag.value = false
    })
  } else {
    form.value.planNo = null
  }
}

function getSalesOrderList() {
  salesOrderLoading.value = true
  listOrderheader(salesOrderQueryParams.value).then(response => {
    salesOrderList.value = response.rows || []
    salesOrderTotal.value = response.total || 0
    salesOrderLoading.value = false
  }).catch(error => {
    console.error('获取销售订单列表失败:', error)
    proxy.$modal.msgError('获取销售订单列表失败: ' + error.message)
    salesOrderLoading.value = false
  })
}


function handleSalesOrderQuery() {
  salesOrderQueryParams.value.pageNum = 1
  getSalesOrderList()
}

function resetSalesOrderQuery() {
  salesOrderQueryParams.value.soNo = null
  salesOrderQueryParams.value.clientName = null
  salesOrderQueryParams.value.pageNum = 1
  salesOrderQueryParams.value.pageSize = 10
}

function fillFormFromOrderLine() {
  if (!orderLineList.value.length) {
    return
  }
  const line = orderLineList.value[0]
  form.value.orderLineId = line.lineId
  form.value.orderLineNo = line.lineNo
  form.value.soLineId = line.lineId
  form.value.originalPlanId = line.soId || form.value.originalPlanId
  form.value.totalQuantity = line.orderedQty
  form.value.referenceNo = line.soNo || form.value.referenceNo
  form.value.planName = form.value.planName || line.materialName || ''
  if (!form.value.planNo && line.soId) {
    form.value.planNo = String(line.soId)
  }
  if (!form.value.plannerName && line.customerLineRef) {
    form.value.plannerName = line.customerLineRef
  }
  if (!form.value.materialRequirementNo) {
    form.value.materialRequirementNo = line.lineNo
  }
  if (!form.value.remark) {
    form.value.remark = line.remark || ''
  }
}

async function selectSalesOrder(row) {
  form.value.referenceNo = row.soNo
  form.value.originalPlanId = row.soId || row.id || form.value.originalPlanId
  form.value.orderLineId = null
  form.value.orderLineNo = null
  form.value.soLineId = null
  salesOrderOpen.value = false
  if (row.soNo) {
    await loadOrderLines(row.soNo)
    fillFormFromOrderLine()
  }
}

// 表单重置
function reset() {
  autoGenFlag.value = false
  form.value = {
    planId: null,
    planNo: null,
    planName: null,
    version: 0,
    originalPlanId: null,
    referenceNo: null,
    planType: null,
    planDate: null,
    startDate: null,
    endDate: null,
    status: null,
    priority: null,
    plannerId: userStore.userId,
    plannerName: userStore.nickname,
    deptId: null,
    deptName: null,
    totalQuantity: null,
    confirmedTime: null,
    releasedTime: null,
    closedTime: null,
    bomName: null,
    bomVersion: null,
    bomId: null,
    attr4: null,
    remark: null,
    materialRequirementId: null,
    materialRequirementNo: null,
    orderLineId: null,
    orderLineNo: null,
    soLineId: null
  }
  orderLineList.value = []
  materialTree.value = []
  proxy.resetForm("planRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["planRef"].validate(valid => {
    if (valid) {
        // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
        if (autoGenFlag.value) {
          form.value.planNo = undefined;
        }
      const formData = { ...form.value }
      if (isAddMode.value) {
        formData.version = 0
        addPlan(formData).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          proxy.$tab.closeOpenPage({ path: '/erp/prd/plan' })
        }).catch(error => {
          proxy.$modal.msgError("新增失败: " + (error.message || "未知错误"))
        })
        return
      }
      if (formData.planId) {
        formData.version = 1
        updatePlanVersion(formData).then(() => {
          proxy.$modal.msgSuccess("版本变更成功")
          if (form.value.planNo) {
            loadMaterialRequirements(form.value.planNo)
          }
          proxy.$tab.closeOpenPage({ path: '/erp/prd/plan' })
        }).catch(error => {
          proxy.$modal.msgError("版本变更失败: " + (error.message || "未知错误"))
        })
      } else {
        proxy.$modal.msgError("生产计划ID不存在，无法更新版本")
      }
    }
  })
}

/** 部门变更：回填部门名称 */
function handleDeptChange(dept) {
  form.value.deptName = dept ? dept.deptName : null;
}

// 取消按钮
function cancel() {
  const obj = { path: '/erp/prd/plan' }
  proxy.$tab.closeOpenPage(obj)
}

/** 确认生产计划：DRAFT → CONFIRMED */
function handleConfirm() {
  if (!form.value.planId) {
    proxy.$modal.msgWarning("生产计划未保存，无法确认")
    return
  }
  proxy.$modal.confirm('确认该生产计划吗？确认后状态将变为"已确认"。').then(() => {
    return confirmPlan(form.value.planId)
  }).then(() => {
    proxy.$modal.msgSuccess("确认成功")
    getInfo(form.value.planId)
  }).catch(error => {
    if (error !== 'cancel') {
      proxy.$modal.msgError("确认失败: " + (error.message || "未知错误"))
    }
  })
}

// 打开计划员选择对话框
function openUserSelect() {
  userSelectOpen.value = true
  resetUserQuery()
  getUserList()
}

// 查询 sys_user 列表
function getUserList() {
  userLoading.value = true
  listUser(userQueryParams.value).then(response => {
    userList.value = response.rows || []
    userTotal.value = response.total || 0
    userLoading.value = false
  }).catch(error => {
    console.error("获取用户列表失败:", error)
    proxy.$modal.msgError("获取用户列表失败: " + error.message)
    userLoading.value = false
  })
}

function handleUserQuery() {
  userQueryParams.value.pageNum = 1
  getUserList()
}

function resetUserQuery() {
  userQueryParams.value.pageNum = 1
  userQueryParams.value.pageSize = 10
  userQueryParams.value.userName = null
  userQueryParams.value.nickName = null
}

// 选择计划员
function selectUser(row) {
  if (!row) {
    return
  }
  form.value.plannerId = row.userId
  form.value.plannerName = row.nickName
  userSelectOpen.value = false
  proxy.$modal.msgSuccess(`已选择计划员：${row.nickName}`)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
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
  bomQueryParams.value.bomVersion = null
  handleBomQuery()
}

// 选择BOM
function selectBom(row) {
  form.value.bomId = row.bomId
  form.value.bomName = row.bomName
  form.value.bomVersion = row.bomVersion
  bomSelectOpen.value = false
}

// BOM计算处理
function handleBomCalculate() {
  if (!form.value.planId) {
    proxy.$modal.msgWarning("请先保存生产计划")
    return
  }

  proxy.$modal.confirm('确定要执行BOM计算吗？这将会重新生成物料需求信息。').then(() => {
    return generateMaterialRequirementsWithDetails(form.value.planId)
  }).then(response => {
    proxy.$modal.msgSuccess("BOM计算完成")
    if (form.value.planNo) {
      loadMaterialRequirements(form.value.planNo)
    }
  }).catch(error => {
    if (error !== 'cancel') {
      console.error("BOM计算失败:", error);
      proxy.$modal.msgError("BOM计算失败: " + (error.message || "未知错误"))
    }
  })
}

// 加载物料需求树信息
function loadMaterialRequirements(planNo) {
  if (!planNo) {
    return;
  }

  listTreeByPlanNo(planNo).then(response => {
    materialTree.value = response.data || []
  }).catch(error => {
    console.error("获取物料需求树失败:", error);
    proxy.$modal.msgError("获取物料需求树失败: " + error.message)
    materialTree.value = []
  });
}

// 物料清单按钮处理
function handleMaterialList() {
  if (!form.value.planNo) {
    proxy.$modal.msgWarning("生产计划编号不存在")
    return
  }

  materialListTitle.value = (form.value.planNo || '') + (form.value.planName ? ' ' + form.value.planName : '') + ' - 物料清单'
  materialListOpen.value = true

  listDetailsByPlanNo(form.value.planNo).then(response => {
    const allDetails: any[] = []
    const requirements = response.data || []
    for (const req of requirements) {
      if (req.prdMaterialRequirementDetailList && req.prdMaterialRequirementDetailList.length) {
        for (const detail of req.prdMaterialRequirementDetailList) {
          allDetails.push(detail)
        }
      }
    }
    materialListData.value = allDetails
  }).catch(error => {
    console.error('获取物料清单详情失败:', error)
    proxy.$modal.msgError("获取物料清单详情失败: " + error.message)
    materialListData.value = []
  })
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
