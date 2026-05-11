<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="商户订单号" prop="orderNumber">
        <el-input v-model="queryParams.orderNumber" placeholder="请输入商户订单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="订单状态" prop="orderStatus">
        <el-select v-model="queryParams.orderStatus" placeholder="请选择" clearable style="width: 200px">
          <el-option v-for="dict in pay_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="支付方式" prop="payType">
        <el-select v-model="queryParams.payType" placeholder="请选择" clearable style="width: 200px">
          <el-option v-for="dict in pay_pay_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="订单内容" prop="orderContent">
        <el-input v-model="queryParams.orderContent" placeholder="请输入订单内容" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['pay:order:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['pay:order:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['pay:order:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['pay:order:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['pay:order:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="orderList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" width="50" align="center" />
      <el-table-column label="商户订单号" align="center" prop="orderNumber" :show-overflow-tooltip="true" />
      <el-table-column label="第三方订单号" align="center" prop="thirdNumber" :show-overflow-tooltip="true" />
      <el-table-column label="订单状态" align="center" prop="orderStatus" width="100">
        <template #default="scope">
          <dict-tag :options="pay_order_status" :value="scope.row.orderStatus" />
        </template>
      </el-table-column>
      <el-table-column label="订单总金额(分)" align="center" prop="totalAmount" width="120" />
      <el-table-column label="实际支付(分)" align="center" prop="actualAmount" width="120" />
      <el-table-column label="订单内容" align="center" prop="orderContent" :show-overflow-tooltip="true" />
      <el-table-column label="支付方式" align="center" prop="payType" width="100">
        <template #default="scope">
          <dict-tag :options="pay_pay_type" :value="scope.row.payType" />
        </template>
      </el-table-column>
      <el-table-column label="支付时间" align="center" prop="payTime" width="160" />
      <el-table-column label="支付人" align="center" prop="payBy" width="100" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['pay:order:query']">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['pay:order:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['pay:order:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改订单对话框 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="orderRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <el-row>
          <el-col :span="12">
            <el-form-item label="订单内容" prop="orderContent">
              <el-input v-model="form.orderContent" placeholder="请输入订单内容" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="支付方式" prop="payType">
              <el-select v-model="form.payType" placeholder="请选择" style="width: 100%">
                <el-option v-for="dict in pay_pay_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="订单总金额(分)" prop="totalAmount">
              <el-input v-model="form.totalAmount" placeholder="请输入订单总金额" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实际支付金额(分)" prop="actualAmount">
              <el-input v-model="form.actualAmount" placeholder="请输入实际支付金额" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="负载信息" prop="orderMessage">
              <el-input v-model="form.orderMessage" type="textarea" placeholder="请输入负载信息" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
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
  </div>
</template>

<script setup lang="ts" name="PayOrder">
import { listPayOrder, getPayOrder, delPayOrder, addPayOrder, updatePayOrder } from "@/api/pay/order"

const { proxy } = getCurrentInstance()
const { pay_order_status, pay_pay_type } = toRefs<any>(proxy.useDict('pay_order_status', 'pay_pay_type'))

const orderList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    orderNumber: null,
    orderStatus: null,
    payType: null,
    orderContent: null,
  },
  rules: {
    orderContent: [
      { required: true, message: "订单内容不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询订单列表 */
function getList() {
  loading.value = true
  listPayOrder(queryParams.value).then(response => {
    orderList.value = response.rows
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
  form.value = {
    orderId: null,
    orderNumber: null,
    orderContent: null,
    totalAmount: null,
    actualAmount: null,
    orderMessage: null,
    payType: null,
    orderStatus: null,
    remark: null,
  }
  proxy.resetForm("orderRef")
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
  ids.value = selection.map(item => item.orderId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加订单"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false
  const _orderId = row.orderId || ids.value
  getPayOrder(_orderId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改订单"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true
  const _orderId = row.orderId || ids.value
  getPayOrder(_orderId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看订单"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["orderRef"].validate(valid => {
    if (valid) {
      if (form.value.orderId != null) {
        updatePayOrder(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPayOrder(form.value).then(response => {
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
  const _orderIds = row.orderId || ids.value
  proxy.$modal.confirm('是否确认删除订单编号为"' + _orderIds + '"的数据项？').then(function () {
    return delPayOrder(_orderIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('pay/order/export', {
    ...queryParams.value
  }, `pay_order_${new Date().getTime()}.xlsx`)
}

getList()
</script>
