<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
                  <el-form-item label="客户订单号" prop="coNo">
                    <el-input
                        v-model="queryParams.coNo"
                        placeholder="请输入客户订单号"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="客户ID" prop="clientId">
                    <el-input
                        v-model="queryParams.clientId"
                        placeholder="请输入客户ID"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="下单日期" prop="orderDate">
                    <el-date-picker clearable
                                    v-model="queryParams.orderDate"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择客户下单日期">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="订单状态" prop="status">
                    <el-select v-model="queryParams.status" placeholder="请选择订单状态" clearable  style="width: 200px;">
                      <el-option
                          v-for="dict in so_customer_order_status"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="订单优先级" prop="priority">
                    <el-select v-model="queryParams.priority" placeholder="请选择订单优先级" clearable  style="width: 200px;">
                      <el-option
                          v-for="dict in so_order_priority"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="采购订单号" prop="customerPoNo">
                    <el-input
                        v-model="queryParams.customerPoNo"
                        placeholder="请输入客户采购订单号"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="订单来源" prop="sourceChannel">
                    <el-select v-model="queryParams.sourceChannel" placeholder="请选择订单来源" clearable  style="width: 200px;">
                      <el-option
                          v-for="dict in so_source_channel"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
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
            v-hasPermi="['so:customerOrder:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['so:customerOrder:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['so:customerOrder:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['so:customerOrder:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['so:customerOrder:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="customerOrderList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="客户订单ID" align="center" prop="coId" />
              <el-table-column label="客户订单号" align="center" prop="coNo" />
              <el-table-column label="客户ID" align="center" prop="clientId" />
              <el-table-column label="客户下单日期" align="center" prop="orderDate" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="订单总金额" align="center" prop="totalAmount" />
              <el-table-column label="订单状态" align="center" prop="status">
                <template #default="scope">
                      <dict-tag :options="so_customer_order_status" :value="scope.row.status"/>
                </template>
              </el-table-column>
              <el-table-column label="订单优先级" align="center" prop="priority">
                <template #default="scope">
                      <dict-tag :options="so_order_priority" :value="scope.row.priority"/>
                </template>
              </el-table-column>
              <el-table-column label="客户采购订单号" align="center" prop="customerPoNo" />
              <el-table-column label="订单来源" align="center" prop="sourceChannel">
                <template #default="scope">
                      <dict-tag :options="so_source_channel" :value="scope.row.sourceChannel"/>
                </template>
              </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['so:customerOrder:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:customerOrder:remove']">删除</el-button>
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

    <!-- 添加或修改客户订单对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="customerOrderRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode">
                        <el-form-item label="客户订单号" prop="coNo">
                          <el-input v-model="form.coNo" placeholder="请输入客户订单号" />
                        </el-form-item>
                        <el-form-item label="客户ID" prop="clientId">
                          <el-input v-model="form.clientId" placeholder="请输入客户ID" />
                        </el-form-item>
                        <el-form-item label="联系人" prop="contactName">
                          <el-input v-model="form.contactName" placeholder="请输入联系人" />
                        </el-form-item>
                        <el-form-item label="联系人电话" prop="contactPhone">
                          <el-input v-model="form.contactPhone" placeholder="请输入联系人电话" />
                        </el-form-item>
                        <el-form-item label="客户下单日期" prop="orderDate">
                          <el-date-picker clearable
                                          v-model="form.orderDate"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择客户下单日期">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="客户要求交货日" prop="requestedDate">
                          <el-date-picker clearable
                                          v-model="form.requestedDate"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择客户要求交货日">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="货币" prop="currency">
                          <el-select v-model="form.currency" placeholder="请选择货币">
                            <el-option
                                v-for="dict in so_currency_type"
                                :key="dict.value"
                                :label="dict.label"
                                :value="dict.value"
                            ></el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item label="订单总金额" prop="totalAmount">
                          <el-input v-model="form.totalAmount" placeholder="请输入订单总金额" />
                        </el-form-item>
                        <el-form-item label="订单状态" prop="status">
                          <el-radio-group v-model="form.status">
                            <el-radio
                                v-for="dict in so_customer_order_status"
                                :key="dict.value"
                                :label="dict.value"
                            >{{dict.label}}</el-radio>
                          </el-radio-group>
                        </el-form-item>
                        <el-form-item label="订单优先级" prop="priority">
                          <el-select v-model="form.priority" placeholder="请选择订单优先级">
                            <el-option
                                v-for="dict in so_order_priority"
                                :key="dict.value"
                                :label="dict.label"
                                :value="parseInt(dict.value)"
                            ></el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item label="客户采购订单号" prop="customerPoNo">
                          <el-input v-model="form.customerPoNo" placeholder="请输入客户采购订单号" />
                        </el-form-item>
                        <el-form-item label="订单来源" prop="sourceChannel">
                          <el-select v-model="form.sourceChannel" placeholder="请选择订单来源">
                            <el-option
                                v-for="dict in so_source_channel"
                                :key="dict.value"
                                :label="dict.label"
                                :value="dict.value"
                            ></el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item label="备注" prop="remark">
                          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
                        </el-form-item>
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

<script setup name="CustomerOrder">
  import { listCustomerOrder, getCustomerOrder, delCustomerOrder, addCustomerOrder, updateCustomerOrder } from "@/api/core/so/customerOrder"

  const { proxy } = getCurrentInstance()
      const { so_customer_order_status, so_currency_type, so_order_priority, so_source_channel } = proxy.useDict('so_customer_order_status', 'so_currency_type', 'so_order_priority', 'so_source_channel')

  const customerOrderList = ref([])
  const open = ref(false)
  const loading = ref(true)
  const showSearch = ref(true)
  const ids = ref([])
  const single = ref(true)
  const multiple = ref(true)
  const total = ref(0)
  const title = ref("")
  const isViewMode = ref(false) // 新增：标识是否为查看模式

  const data = reactive({
    form: {},
    queryParams: {
      pageNum: 1,
      pageSize: 10,
                    coNo: null,
                    clientId: null,
                    orderDate: null,
                    status: null,
                    priority: null,
                    customerPoNo: null,
                    sourceChannel: null,
    },
    rules: {
                    coNo: [
                { required: true, message: "客户订单号不能为空", trigger: "blur" }
              ],
                    clientId: [
                { required: true, message: "客户ID不能为空", trigger: "blur" }
              ],
                    orderDate: [
                { required: true, message: "客户下单日期不能为空", trigger: "blur" }
              ],
                    currency: [
                { required: true, message: "货币不能为空", trigger: "change" }
              ],
    }
  })

  const { queryParams, form, rules } = toRefs(data)

  /** 查询客户订单列表 */
  function getList() {
    loading.value = true
    listCustomerOrder(queryParams.value).then(response => {
            customerOrderList.value = response.rows
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
                    coId: null,
                    coNo: null,
                    clientId: null,
                    contactName: null,
                    contactPhone: null,
                    orderDate: null,
                    requestedDate: null,
                    currency: null,
                    totalAmount: null,
                    status: null,
                    priority: null,
                    customerPoNo: null,
                    sourceChannel: null,
                    remark: null,
                    attr1: null,
                    attr2: null,
                    attr3: null,
                    attr4: null,
                    createBy: null,
                    createTime: null,
                    updateBy: null,
                    updateTime: null
    }
    proxy.resetForm("customerOrderRef")
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
    ids.value = selection.map(item => item.coId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    isViewMode.value = false // 设置为编辑模式
    open.value = true
    title.value = "添加客户订单"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    reset()
    isViewMode.value = false // 设置为编辑模式
    const _coId = row.coId || ids.value
    getCustomerOrder(_coId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "修改客户订单"
    })
  }

  /** 查看按钮操作 */
  function handleView(row) {
    reset()
    isViewMode.value = true // 设置为查看模式
    const _coId = row.coId || ids.value
    getCustomerOrder(_coId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "查看客户订单"
    })
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["customerOrderRef"].validate(valid => {
      if (valid) {
        if (form.value.coId != null) {
          updateCustomerOrder(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addCustomerOrder(form.value).then(response => {
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
    const _coIds = row.coId || ids.value
    proxy.$modal.confirm('是否确认删除客户订单编号为"' + _coIds + '"的数据项？').then(function() {
      return delCustomerOrder(_coIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('so/customerOrder/export', {
      ...queryParams.value
    }, `customerOrder_${new Date().getTime()}.xlsx`)
  }

  getList()
</script>