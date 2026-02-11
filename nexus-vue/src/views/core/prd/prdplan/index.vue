<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="生产计划编号" prop="planNo">
        <el-input v-model="queryParams.planNo" placeholder="请输入生产计划编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>

      <el-form-item label="计划名称" prop="planName">
        <el-input v-model="queryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="参考单据号" prop="referenceNo">
        <el-input v-model="queryParams.referenceNo" placeholder="请输入参考单据号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="计划类型" prop="planType">
        <el-select v-model="queryParams.planType" placeholder="请选择计划类型" clearable style="width: 120px;">
          <el-option v-for="dict in prd_plan_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划日期" prop="planDate">
        <el-date-picker clearable v-model="queryParams.planDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择计划日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="计划开始日期" prop="startDate">
        <el-date-picker clearable v-model="queryParams.startDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择计划开始日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="计划结束日期" prop="endDate">
        <el-date-picker clearable v-model="queryParams.endDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择计划结束日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px;">
          <el-option v-for="dict in prd_plan_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="queryParams.priority" placeholder="请选择优先级" clearable style="width: 120px;">
          <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划员" prop="plannerName">
        <el-input v-model="queryParams.plannerName" placeholder="请输入计划员" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="生产部门" prop="departmentName">
        <el-input v-model="queryParams.departmentName" placeholder="请输入生产部门" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="计划总数量" prop="totalQuantity">
        <el-input v-model="queryParams.totalQuantity" placeholder="请输入计划总数量" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="确认时间" prop="confirmedTime">
        <el-date-picker clearable v-model="queryParams.confirmedTime" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择确认时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="下达时间" prop="releasedTime">
        <el-date-picker clearable v-model="queryParams.releasedTime" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择下达时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="关闭时间" prop="closedTime">
        <el-date-picker clearable v-model="queryParams.closedTime" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择关闭时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:plan:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['prd:plan:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['prd:plan:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['prd:plan:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['prd:plan:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="planList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="生产计划ID" align="center" prop="planId" />
      <el-table-column label="生产计划编号" align="center" prop="planNo" width="200px" />
      <el-table-column label="计划名称" align="center" prop="planName" />
      <el-table-column label="版本号" align="center" prop="version" />
      <el-table-column label="是否当前版本" align="center" prop="isCurrent">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent" />
        </template>
      </el-table-column>
      <el-table-column label="原始计划ID" align="center" prop="originalPlanId" />
      <el-table-column label="参考单据号" align="center" prop="referenceNo" />
      <el-table-column label="计划类型" align="center" prop="planType">
        <template #default="scope">
          <dict-tag :options="prd_plan_type" :value="scope.row.planType" />
        </template>
      </el-table-column>
      <el-table-column label="计划日期" align="center" prop="planDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.planDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划开始日期" align="center" prop="startDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.startDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划结束日期" align="center" prop="endDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.endDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="prd_plan_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority">
        <template #default="scope">
          <dict-tag :options="so_order_priority" :value="scope.row.priority" />
        </template>
      </el-table-column>
      <el-table-column label="计划员" align="center" prop="plannerName" />
      <el-table-column label="生产部门" align="center" prop="departmentName" />
      <el-table-column label="计划总数量" align="center" prop="totalQuantity" />
      <el-table-column label="确认时间" align="center" prop="confirmedTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.confirmedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="下达时间" align="center" prop="releasedTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.releasedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="关闭时间" align="center" prop="closedTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.closedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['prd:plan:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['prd:plan:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改生产计划对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="planRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="生产计划编号" prop="planNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.planNo" placeholder="请输入生产计划编号" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="自动生成" @change="handleAutoGenChange"
                  :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <!-- 右侧留空保持对齐 -->
          <el-col :span="8">
            <el-row>
              <el-button type="primary" @click="handleSelectSalesOrder">选择销售订单</el-button>
            </el-row>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入计划名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" prop="version">
              <el-input v-model="form.version" placeholder="请输入版本号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="原始计划ID" prop="originalPlanId">
              <el-input v-model="form.originalPlanId" placeholder="请输入原始计划ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考单据号" prop="referenceNo">
              <el-input v-model="form.referenceNo" placeholder="请输入参考单据号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划类型" prop="planType">
              <el-select v-model="form.planType" placeholder="请选择计划类型" style="width: 100%">
                <el-option v-for="dict in prd_plan_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划日期" prop="planDate">
              <el-date-picker clearable v-model="form.planDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划日期" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划开始日期" prop="startDate">
              <el-date-picker clearable v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划开始日期" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束日期" prop="endDate">
              <el-date-picker clearable v-model="form.endDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划结束日期" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status" :disabled="isStatusDisabled">
                <el-radio v-for="dict in prd_plan_status" :key="dict.value" :label="dict.value">{{ dict.label
                  }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择优先级" style="width: 100%">
                <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划员ID" prop="plannerId">
              <el-input v-model="form.plannerId" placeholder="请输入计划员ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划员" prop="plannerName">
              <el-input v-model="form.plannerName" placeholder="请输入计划员" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产部门ID" prop="departmentId">
              <el-input v-model="form.departmentId" placeholder="请输入生产部门ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产部门" prop="departmentName">
              <el-input v-model="form.departmentName" placeholder="请输入生产部门" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划总数量" prop="totalQuantity">
              <el-input v-model="form.totalQuantity" placeholder="请输入计划总数量" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="确认时间" prop="confirmedTime">
              <el-date-picker clearable v-model="form.confirmedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择确认时间" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="下达时间" prop="releasedTime">
              <el-date-picker clearable v-model="form.releasedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择下达时间" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关闭时间" prop="closedTime">
              <el-date-picker clearable v-model="form.closedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择关闭时间" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="销售订单子表ID" prop="orderLineId">
              <el-input v-model="form.orderLineId" placeholder="请输入销售订单子表ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售订单子表行号" prop="orderLineNo">
              <el-input v-model="form.orderLineNo" placeholder="请输入销售订单子表行号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"
                :autosize="{ minRows: 2, maxRows: 4 }" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 销售订单选择对话框 -->
    <el-dialog title="选择销售订单" v-model="salesOrderOpen" width="1000px" append-to-body>
      <el-form :model="salesOrderQueryParams" :inline="true">
        <el-form-item label="销售订单编号">
          <el-input
            v-model="salesOrderQueryParams.soNo"
            placeholder="请输入销售订单编号"
            clearable
            @keyup.enter="getSalesOrderList"
          />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input
            v-model="salesOrderQueryParams.clientName"
            placeholder="请输入客户名称"
            clearable
            @keyup.enter="getSalesOrderList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getSalesOrderList">搜索</el-button>
          <el-button icon="Refresh" @click="resetSalesOrderQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="salesOrderLoading"
        :data="salesOrderList"
        @row-dblclick="selectSalesOrder"
        highlight-current-row
      >
        <el-table-column label="销售订单编号" align="center" prop="soNo" />
        <el-table-column label="客户名称" align="center" prop="clientName" />
        <el-table-column label="订单日期" align="center" prop="orderDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="承诺交货日" align="center" prop="promiseDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.promiseDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" align="center" prop="orderStatus">
          <template #default="scope">
            <dict-tag :options="so_customer_order_status" :value="scope.row.orderStatus" />
          </template>
        </el-table-column>
        <el-table-column label="含税总金额" align="center" prop="totalAmount" />
        <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button size="small" type="text" @click="showOrderLines(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="salesOrderList.length > 0"
        :total="salesOrderList.length"
        v-model:page="salesOrderQueryParams.pageNum"
        v-model:limit="salesOrderQueryParams.pageSize"
        @pagination="getSalesOrderList"
      />
    </el-dialog>
    
    <!-- 销售订单行选择对话框 -->
    <el-dialog title="选择销售订单行" v-model="salesOrderLineOpen" width="1000px" append-to-body>
      <el-table
        v-loading="salesOrderLineLoading"
        :data="salesOrderLineList"
        @row-dblclick="selectSalesOrderLine"
        highlight-current-row
      >
        <el-table-column label="行号" align="center" prop="lineNo" />
        <el-table-column label="物料编码" align="center" prop="materialCode" />
        <el-table-column label="物料名称" align="center" prop="materialName" />
        <el-table-column label="规格" align="center" prop="materialSpec" />
        <el-table-column label="订单数量" align="center" prop="orderedQty" />
        <el-table-column label="单位" align="center" prop="unit" />
        <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button size="small" type="text" @click="selectSalesOrderLine(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="salesOrderLineOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Plan">
import { listPlan, getPlan, delPlan, addPlan, updatePlan } from "@/api/core/prd/plan"
import { listOrderheader, getOrderLine } from "@/api/core/so/orderheader" 
import { genCode } from "@/api/system/autocode/rule"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance()
const { prd_plan_type, prd_plan_status, so_order_priority, sys_true_or_false, so_customer_order_status } = proxy.useDict('prd_plan_type', 'prd_plan_status', 'so_order_priority', 'sys_true_or_false', 'so_customer_order_status')

const planList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false) // 新增：标识是否为查看模式
const autoGenFlag = ref(false) // 新增：控制编号自动生成开关
const isStatusDisabled = ref(true) // 新增：控制状态字段是否可修改

// 销售订单选择相关变量
const salesOrderList = ref([])
const salesOrderOpen = ref(false)
const salesOrderLoading = ref(false)
const salesOrderQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  soNo: null,
  clientName: null,
  orderStatus: null
})

// 销售订单行选择相关变量
const salesOrderLineList = ref([])
const salesOrderLineOpen = ref(false)
const salesOrderLineLoading = ref(false)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    planNo: null,
    planName: null,
    referenceNo: null,
    planType: null,
    planDate: null,
    startDate: null,
    endDate: null,
    status: null,
    priority: null,
    plannerName: null,
    departmentName: null,
    totalQuantity: null,
    confirmedTime: null,
    releasedTime: null,
    closedTime: null,
    isCurrent: null,
    orderLineId: null,
    orderLineNo: null,
  },
  rules: {
    planNo: [
      { required: true, message: "生产计划编号不能为空", trigger: "blur" }
    ],
    version: [
      { required: true, message: "版本号不能为空", trigger: "blur" }
    ],
    planType: [
      { required: true, message: "计划类型不能为空", trigger: "change" }
    ],
    planDate: [
      { required: true, message: "计划日期不能为空", trigger: "blur" }
    ],
    status: [
      { required: true, message: "状态不能为空", trigger: "change" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询生产计划列表 */
function getList() {
  loading.value = true
  listPlan(queryParams.value).then(response => {
    planList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  autoGenFlag.value = false
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
    status: "DRAFT", // 修改默认值为"DRAFT"
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
    isCurrent: "1",
    attr4: null,
    orderLineId: null,
    orderLineNo: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("planRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.planId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  isStatusDisabled.value = false // 新增状态下允许修改状态
  open.value = true
  title.value = "添加生产计划"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  if (row && row.type && row instanceof Event) {
    // 这是一个事件对象，表示没有传入数据参数
    row = null;
  }

  // 如果没有传入row参数，则从选中的数据中获取
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行修改");
      return;
    }
    // 从列表中找到选中的记录
    const selectedRow = planList.value.find(item => item.planId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const planId = row.planId;
  router.push({
    path: '/core/prd/prdplan/form',
    query: { id: planId, action: 'edit' }
  });
}

/** 查看按钮操作 */
function handleView(row) {
  if (row && row.type && row instanceof Event) {
    // 这是一个事件对象，表示没有传入数据参数
    row = null;
  }

  // 如果没有传入row参数，则从选中的数据中获取
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行查看");
      return;
    }
    // 从列表中找到选中的记录
    const selectedRow = planList.value.find(item => item.planId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const planId = row.planId;
  router.push({
    path: '/core/prd/prdplan/form',
    query: { id: planId, action: 'view' }
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["planRef"].validate(valid => {
    if (valid) {
      if (form.value.planId != null) {
        updatePlan(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPlan(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _planIds = row.planId || ids.value
  proxy.$modal.confirm('是否确认删除生产计划编号为"' + _planIds + '"的数据项？').then(function () {
    return delPlan(_planIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('prd/plan/export', {
    ...queryParams.value
  }, `plan_${new Date().getTime()}.xlsx`)
}

getList()


// 自动生产编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('PRD_PLAN_CODE').then(response => {
      form.value.planNo = response;
    });
  } else {
    form.value.planNo = null;
  }
}

/** 选择销售订单按钮操作 */
function handleSelectSalesOrder() {
  salesOrderOpen.value = true
  resetSalesOrderQuery()
  getSalesOrderList()
}

/** 查询销售订单列表 */
function getSalesOrderList() {
  salesOrderLoading.value = true
  // 设置查询条件为CONFIRMED状态
  const params = {...salesOrderQueryParams}
  params.orderStatus = 'CONFIRMED'
  listOrderheader(params).then(response => {
    salesOrderList.value = response.rows
    salesOrderLoading.value = false
  })
}

/** 显示销售订单行 */
function showOrderLines(row) {
  salesOrderLineOpen.value = true
  salesOrderLineLoading.value = true
  
  // 调用接口获取销售订单行数据
  getOrderLine(row.soNo).then(response => {
    salesOrderLineList.value = response.data || []
    salesOrderLineLoading.value = false
  }).catch(error => {
    proxy.$modal.msgError("获取销售订单行信息失败: " + error.message)
    salesOrderLineLoading.value = false
  })
}

/** 选择销售订单行 */
function selectSalesOrderLine(row) {
  form.value.orderLineId = row.lineId
  form.value.orderLineNo = row.lineNo
  form.value.referenceNo = row.soNo || form.value.referenceNo
  salesOrderLineOpen.value = false
  salesOrderOpen.value = false
}

/** 选择销售订单 */
function selectSalesOrder(row) {
  form.value.referenceNo = row.soNo
  salesOrderOpen.value = false
}

/** 重置销售订单查询 */
function resetSalesOrderQuery() {
  salesOrderQueryParams.pageNum = 1
  salesOrderQueryParams.pageSize = 10
  salesOrderQueryParams.soNo = null
  salesOrderQueryParams.clientName = null
  salesOrderQueryParams.orderStatus = null
}

getList()
</script>
