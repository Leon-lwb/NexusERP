<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="销售订单编号" prop="soNo">
        <el-input
            v-model="queryParams.soNo"
            placeholder="请输入销售订单编号"
            clearable
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来源客户订单" prop="coId">
        <el-input
            v-model="queryParams.coId"
            placeholder="请输入来源客户订单"
            clearable
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来源客户" prop="clientName">
        <el-input
            v-model="queryParams.clientName"
            placeholder="请输入来源客户"
            clearable
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="销售代表" prop="salesRepName">
        <el-input
            v-model="queryParams.salesRepName"
            placeholder="请输入销售代表"
            clearable
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="订单生成日期" prop="orderDate">
        <el-date-picker clearable
                        v-model="queryParams.orderDate"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="请选择订单生成日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="承诺交货日" prop="promiseDate">
        <el-date-picker clearable
                        v-model="queryParams.promiseDate"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="请选择承诺交货日">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="要求交货日" prop="requestedDate">
        <el-date-picker clearable
                        v-model="queryParams.requestedDate"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="请选择客户要求交货日">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="信用状态" prop="creditStatus">
        <el-select v-model="queryParams.creditStatus" placeholder="请选择信用状态" clearable style="width: 120px;">
          <el-option
              v-for="dict in so_credit_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="信用拦截人" prop="creditBlockedBy">
        <el-input
            v-model="queryParams.creditBlockedBy"
            placeholder="请输入信用拦截人"
            clearable
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="订单状态" prop="orderStatus">
        <el-select v-model="queryParams.orderStatus" placeholder="请选择订单状态" clearable style="width: 120px;">
          <el-option
              v-for="dict in so_order_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="项目ID" prop="projectId">
        <el-input
            v-model="queryParams.projectId"
            placeholder="请输入项目ID"
            clearable
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
            type="primary"
            plain
            icon="Plus"
            @click="handleAdd"
            v-hasPermi="['so:orderheader:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['so:orderheader:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['so:orderheader:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['so:orderheader:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['so:orderheader:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Check" :disabled="single" @click="handleConfirm"
          v-hasPermi="['so:orderheader:edit']">确认</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Finished" :disabled="single" @click="handleFulfill"
          v-hasPermi="['so:orderheader:edit']">履行</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Close" :disabled="single" @click="handleCloseOrder"
          v-hasPermi="['so:orderheader:edit']">关闭</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="CircleClose" :disabled="single" @click="handleCancel"
          v-hasPermi="['so:orderheader:edit']">取消</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="orderheaderList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" width="50" align="center" prop="soId" />
      <el-table-column label="销售订单编号" align="center" prop="soNo" width="150"/>
      <el-table-column label="来源客户" align="center" prop="clientName" />
      <el-table-column label="销售代表" align="center" prop="salesRepName" />
      <el-table-column label="订单生成日期" align="center" prop="orderDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="承诺交货日" align="center" prop="promiseDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.promiseDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="客户要求交货日" align="center" prop="requestedDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requestedDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发货地址" align="center" prop="deliveryAddressId" />
      <el-table-column label="开票地址" align="center" prop="invoiceAddressId" />
      <el-table-column label="付款条款" align="center" prop="paymentTerms" />
      <el-table-column label="货币" align="center" prop="currency">
        <template #default="scope">
          <dict-tag :options="so_currency_type" :value="scope.row.currency"/>
        </template>
      </el-table-column>
      <el-table-column label="汇率" align="center" prop="exchangeRate" />
      <el-table-column label="含税总金额" align="center" prop="totalAmount" />
      <el-table-column label="税款" align="center" prop="taxAmount" />
      <el-table-column label="整单折扣" align="center" prop="discountAmount" />
      <el-table-column label="整单折扣率" align="center" prop="discountRate" />
      <el-table-column label="信用状态" align="center" prop="creditStatus">
        <template #default="scope">
          <dict-tag :options="so_credit_status" :value="scope.row.creditStatus"/>
        </template>
      </el-table-column>
      <el-table-column label="信用检查时间" align="center" prop="creditCheckTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.creditCheckTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="信用拦截人" align="center" prop="creditBlockedBy" />
      <el-table-column label="订单状态" align="center" prop="orderStatus">
        <template #default="scope">
          <dict-tag :options="so_order_status" :value="scope.row.orderStatus"/>
        </template>
      </el-table-column>
      <el-table-column label="项目ID" align="center" prop="projectId" />
      <el-table-column label="是否启用项目配套发料" align="center" prop="isPaEnabled">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isPaEnabled"/>
        </template>
      </el-table-column>
      <el-table-column label="确认时间" align="center" prop="confirmedTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.confirmedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="完全履行时间" align="center" prop="fulfilledTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.fulfilledTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" align="center" prop="closedTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.closedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="取消时间" align="center" prop="cancelledTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.cancelledTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="取消原因" align="center" prop="cancelledReason" />
      <el-table-column label="销售组织ID" align="center" prop="salesOrgId" />
      <el-table-column label="分销渠道" align="center" prop="distributionChannel" />
      <el-table-column label="客户参考号" align="center" prop="customerRefNo" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:orderheader:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:orderheader:remove']">删除</el-button>
          <el-button link type="success" icon="Check" @click="handleConfirm(scope.row)" v-if="scope.row.orderStatus === 'DRAFT'" v-hasPermi="['so:orderheader:edit']">确认</el-button>
          <el-button link type="primary" icon="Finished" @click="handleFulfill(scope.row)" v-if="scope.row.orderStatus === 'CONFIRMED'" v-hasPermi="['so:orderheader:edit']">履行</el-button>
          <el-button link type="info" icon="Close" @click="handleCloseOrder(scope.row)" v-if="scope.row.orderStatus === 'CONFIRMED'" v-hasPermi="['so:orderheader:edit']">关闭</el-button>
          <el-button link type="danger" icon="CircleClose" @click="handleCancel(scope.row)" v-if="scope.row.orderStatus !== 'CLOSED' && scope.row.orderStatus !== 'CANCELLED'" v-hasPermi="['so:orderheader:edit']">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
        v-show="total>0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
    />
  </div>
</template>

<script setup lang="ts" name="Orderheader">
import { listOrderheader, delOrderheader, confirmOrder, fulfillOrder, closeOrder, cancelOrder } from "@/api/erp/so/orderheader"
import { useRouter } from 'vue-router'

  const router = useRouter();
  const { proxy } = getCurrentInstance()
  const { so_credit_status, so_order_status, sys_true_or_false, so_currency_type } = toRefs<any>(proxy.useDict('so_credit_status', 'so_order_status', 'sys_true_or_false', 'so_currency_type'))

  const orderheaderList = ref([])
  const loading = ref(true)
  const showSearch = ref(true)
  const ids = ref([])
  const single = ref(true)
  const multiple = ref(true)
  const total = ref(0)

  const data = reactive({
    queryParams: {
      pageNum: 1,
      pageSize: 10,
      soNo: null,
      coId: null,
      clientName: null,
      salesRepName: null,
      orderDate: null,
      promiseDate: null,
      requestedDate: null,
      creditStatus: null,
      creditBlockedBy: null,
      orderStatus: null,
      projectId: null,
    }
  })

  const { queryParams } = toRefs(data)

  /** 查询销售订单列表 */
  function getList() {
    loading.value = true
    listOrderheader(queryParams.value).then(response => {
      orderheaderList.value = response.rows
      total.value = response.total
      loading.value = false
    })
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
    ids.value = selection.map(item => item.soId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    if (row && row.type && row instanceof Event) {
      row = null;
    }
    if (!row) {
      if (ids.value.length !== 1) {
        proxy.$modal.msgWarning("请选择一条数据进行修改");
        return;
      }
      const selectedRow = orderheaderList.value.find(item => item.soId == ids.value[0]);
      if (!selectedRow) {
        proxy.$modal.msgWarning("未找到选中的数据");
        return;
      }
      row = selectedRow;
    }
    const soId = row.soId;
    if (soId === undefined) {
      proxy.$modal.msgError("数据格式错误，缺少soId字段");
      return;
    }
    router.push({
      path: '/erp/so/orderheader/form',
      query: { id: soId, action: 'edit' }
    });
  }

  /** 新增按钮操作 */
  function handleAdd() {
    router.push({
      path: '/erp/so/orderheader/form',
      query: {action: 'add' }
    });
  }

  /** 查看按钮操作 */
  function handleView(row) {
    if (row && row.type && row instanceof Event) {
      row = null;
    }
    if (!row) {
      if (ids.value.length !== 1) {
        proxy.$modal.msgWarning("请选择一条数据进行查看");
        return;
      }
      const selectedRow = orderheaderList.value.find(item => item.soId == ids.value[0]);
      if (!selectedRow) {
        proxy.$modal.msgWarning("未找到选中的数据");
        return;
      }
      row = selectedRow;
    }
    const soId = row.soId;
    router.push({
      path: '/erp/so/orderheader/form',
      query: { id: soId, action: 'view' }
    });
  }

  /** 删除按钮操作 */
  function handleDelete(row) {
    const _soIds = row.soId || ids.value
    proxy.$modal.confirm('是否确认删除销售订单编号为"' + _soIds + '"的数据项？').then(function() {
      return delOrderheader(_soIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('erp/so/orderheader/export', {
      ...queryParams.value
    }, `orderheader_${new Date().getTime()}.xlsx`)
  }

/** 确认订单 */
function handleConfirm(row: any) {
  const soId = row.soId || ids.value[0]
  if (!soId) { proxy.$modal.msgWarning("请选择一个订单"); return }
  proxy.$modal.confirm('确认订单编号为"' + soId + '"的吗？').then(() => {
    confirmOrder(soId).then(() => { proxy.$modal.msgSuccess("确认成功"); getList() })
  }).catch(() => {})
}

/** 履行订单 */
function handleFulfill(row: any) {
  const soId = row.soId || ids.value[0]
  if (!soId) { proxy.$modal.msgWarning("请选择一个订单"); return }
  proxy.$modal.confirm('确认履行订单编号为"' + soId + '"的吗？').then(() => {
    fulfillOrder(soId).then(() => { proxy.$modal.msgSuccess("履行成功"); getList() })
  }).catch(() => {})
}

/** 关闭订单 */
function handleCloseOrder(row: any) {
  const soId = row.soId || ids.value[0]
  if (!soId) { proxy.$modal.msgWarning("请选择一个订单"); return }
  proxy.$modal.confirm('确认关闭订单编号为"' + soId + '"的吗？').then(() => {
    closeOrder(soId).then(() => { proxy.$modal.msgSuccess("关闭成功"); getList() })
  }).catch(() => {})
}

/** 取消订单 */
function handleCancel(row: any) {
  const soId = row.soId || ids.value[0]
  if (!soId) { proxy.$modal.msgWarning("请选择一个订单"); return }
  proxy.$prompt('请输入取消原因', '取消订单').then(({ value }: any) => {
    cancelOrder(soId, value).then(() => { proxy.$modal.msgSuccess("取消成功"); getList() })
  }).catch(() => {})
}

  getList()
</script>
