<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px" class="query-form">
                  <el-form-item label="供应商编码" prop="vendorCode" style="width: 250px;">
                    <el-input
                        v-model="queryParams.vendorCode"
                        placeholder="请输入供应商编码"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="供应商名称" prop="vendorName" style="width: 250px;">
                    <el-input
                        v-model="queryParams.vendorName"
                        placeholder="请输入供应商名称"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="行业" prop="industry" style="width: 250px;">
                    <el-select v-model="queryParams.industry" placeholder="请选择行业" clearable>
                      <el-option
                          v-for="dict in bd_industry"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="地区" prop="region" style="width: 250px;">
                    <el-select v-model="queryParams.region" placeholder="请选择地区" clearable>
                      <el-option
                          v-for="dict in bd_region"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="合作状态 " prop="status" style="width: 250px;">
                    <el-select v-model="queryParams.status" placeholder="请选择合作状态 " clearable>
                      <el-option
                          v-for="dict in bd_vendor_status"
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
            v-hasPermi="['core:vendor:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['core:vendor:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['core:vendor:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['core:vendor:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['core:vendor:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="vendorList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="序号" type="index" width="50" align="center" prop="vendorId" />
              <el-table-column label="供应商编码" align="center" prop="vendorCode" />
              <el-table-column label="供应商名称" align="center" prop="vendorName" />
              <el-table-column label="行业" align="center" prop="industry">
                <template #default="scope">
                      <dict-tag :options="bd_industry" :value="scope.row.industry"/>
                </template>
              </el-table-column>
              <el-table-column label="地区" align="center" prop="region">
                <template #default="scope">
                      <dict-tag :options="bd_region" :value="scope.row.region"/>
                </template>
              </el-table-column>
              <el-table-column label="合作状态 " align="center" prop="status">
                <template #default="scope">
                      <dict-tag :options="bd_vendor_status" :value="scope.row.status"/>
                </template>
              </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['core:vendor:query']">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['core:vendor:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['core:vendor:remove']">删除</el-button>
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

    <!-- 添加或修改供应商对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="vendorRef" :model="form" :rules="rules" label-width="120px">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-row>
              <el-col :span="12">
                <el-form-item label="供应商编码" prop="vendorCode">
                  <div style="display: flex; align-items: center; gap: 10px;">
                    <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                    <el-switch v-model="autoGenFlag"
                      active-color="#13ce66"
                      active-text="自动生成"
                      @change="handleAutoGenChange"
                      :disabled="isViewMode">
                    </el-switch>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="供应商名称" prop="vendorName">
                  <el-input v-model="form.vendorName" placeholder="请输入供应商名称" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="税号" prop="taxId">
                  <el-input v-model="form.taxId" placeholder="请输入税号" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="联系人" prop="contactPerson">
                  <el-input v-model="form.contactPerson" placeholder="请输入联系人" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="电话" prop="phone">
                  <el-input v-model="form.phone" placeholder="请输入电话" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="银行账户" prop="bankAccount">
                  <el-input v-model="form.bankAccount" placeholder="请输入银行账户" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="地址" prop="address">
                  <el-input v-model="form.address" type="textarea" placeholder="请输入内容" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
          
          <el-tab-pane label="分类信息" name="category">
            <el-row>
              <el-col :span="12">
                <el-form-item label="行业" prop="industry">
                  <el-select v-model="form.industry" placeholder="请选择行业" style="width: 100%" :disabled="isViewMode">
                    <el-option
                        v-for="dict in bd_industry"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                    ></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="地区" prop="region">
                  <el-select v-model="form.region" placeholder="请选择地区" style="width: 100%" :disabled="isViewMode">
                    <el-option
                        v-for="dict in bd_region"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                    ></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="分类" prop="category">
                  <el-select v-model="form.category" placeholder="请选择分类 (原材料/标准件/服务类)" style="width: 100%" :disabled="isViewMode">
                    <el-option
                        v-for="dict in bd_vendor_category"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                    ></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="合作状态 " prop="status">
                  <el-select v-model="form.status" placeholder="请选择合作状态 " style="width: 100%" :disabled="isViewMode">
                    <el-option
                        v-for="dict in bd_vendor_status"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                    ></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="备注" prop="remark">
                  <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="!isViewMode" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Vendor">
  import { listVendor, getVendor, delVendor, addVendor, updateVendor } from "@/api/core/bd/vendor"
  import { genCode } from "@/api/system/autocode/rule"

  const { proxy } = getCurrentInstance()
  const { bd_industry, bd_region, bd_vendor_status, bd_vendor_category } = proxy.useDict('bd_industry', 'bd_region', 'bd_vendor_status', 'bd_vendor_category')

  const vendorList = ref([])
  const open = ref(false)
  const loading = ref(true)
  const showSearch = ref(true)
  const ids = ref([])
  const single = ref(true)
  const multiple = ref(true)
  const total = ref(0)
  const title = ref("")
  const activeTab = ref("basic")
  const isViewMode = ref(false) // 查看模式标识

  const data = reactive({
    form: {},
    queryParams: {
      pageNum: 1,
      pageSize: 10,
                    vendorCode: null,
                    vendorName: null,
                    industry: null,
                    region: null,
                    status: null,
    },
    rules: {
                    vendorCode: [
                { required: true, message: "供应商编码不能为空", trigger: "blur" }
              ],
                    vendorName: [
                { required: true, message: "供应商名称不能为空", trigger: "blur" }
              ],
    }
  })

  const { queryParams, form, rules } = toRefs(data)

  /** 查询供应商列表 */
  function getList() {
    loading.value = true
    listVendor(queryParams.value).then(response => {
            vendorList.value = response.rows
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
                    vendorId: null,
                    vendorCode: null,
                    vendorName: null,
                    taxId: null,
                    address: null,
                    contactPerson: null,
                    phone: null,
                    bankAccount: null,
                    industry: null,
                    region: null,
                    category: null,
                    status: null,
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
    proxy.resetForm("vendorRef")
    activeTab.value = "basic"
    isViewMode.value = false // 重置查看模式
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
    ids.value = selection.map(item => item.vendorId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    isViewMode.value = false
    open.value = true
    title.value = "添加供应商"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    reset()
    isViewMode.value = false
    const _vendorId = row.vendorId || ids.value
    getVendor(_vendorId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "修改供应商"
    })
  }

  /** 查看按钮操作 */
  function handleView(row) {
    reset()
    isViewMode.value = true // 设置为查看模式
    const _vendorId = row.vendorId || ids.value[0]
    getVendor(_vendorId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "查看供应商"
    })
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["vendorRef"].validate(valid => {
      if (valid) {
        if (form.value.vendorId != null) {
          updateVendor(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addVendor(form.value).then(response => {
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
    const _vendorIds = row.vendorId || ids.value
    proxy.$modal.confirm('是否确认删除供应商编号为"' + _vendorIds + '"的数据项？').then(function() {
      return delVendor(_vendorIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('core/vendor/export', {
      ...queryParams.value
    }, `vendor_${new Date().getTime()}.xlsx`)
  }

  //自动生成编码
function handleAutoGenChange(autoGenFlag){
  if(autoGenFlag){
    genCode('VENDOR_CODE').then(response =>{
      form.value.vendorCode = response;
    });
  }else{
    form.value.vendorCode = null;
  }
}

  getList()
</script>