<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="通知单编号" prop="noticeCode">
        <el-input v-model="queryParams.noticeCode" placeholder="请输入通知单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="通知单名称" prop="noticeName">
        <el-input v-model="queryParams.noticeName" placeholder="请输入通知单名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="采购订单编号" prop="purOrderCode">
        <el-input v-model="queryParams.purOrderCode" placeholder="请输入采购订单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商ID" prop="vendorId">
        <el-input v-model="queryParams.vendorId" placeholder="请输入供应商ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商编码" prop="vendorCode">
        <el-input v-model="queryParams.vendorCode" placeholder="请输入供应商编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商名称" prop="vendorName">
        <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商简称" prop="vendorNick">
        <el-input v-model="queryParams.vendorNick" placeholder="请输入供应商简称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="到货日期" prop="arrivalDate">
        <el-date-picker clearable v-model="queryParams.arrivalDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择到货日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="联系人" prop="contact">
        <el-input v-model="queryParams.contact" placeholder="请输入联系人" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="联系方式" prop="phone">
        <el-input v-model="queryParams.phone" placeholder="请输入联系方式" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单据状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择单据状态" clearable>
          <el-option v-for="dict in wm_notice_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd"
          v-hasPermi="['wm:arrivalnotice:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['wm:arrivalnotice:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:arrivalnotice:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['wm:arrivalnotice:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:arrivalnotice:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="arrivalnoticeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="通知单ID" align="center" prop="noticeId" />
      <el-table-column label="通知单编号" align="center" prop="noticeCode" />
      <el-table-column label="通知单名称" align="center" prop="noticeName" />
      <el-table-column label="采购订单编号" align="center" prop="purOrderCode" />
      <el-table-column label="供应商ID" align="center" prop="vendorId" />
      <el-table-column label="供应商编码" align="center" prop="vendorCode" />
      <el-table-column label="供应商名称" align="center" prop="vendorName" />
      <el-table-column label="供应商简称" align="center" prop="vendorNick" />
      <el-table-column label="到货日期" align="center" prop="arrivalDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.arrivalDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="联系人" align="center" prop="contact" />
      <el-table-column label="联系方式" align="center" prop="phone" />
      <el-table-column label="单据状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_notice_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:arrivalnotice:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['wm:arrivalnotice:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改到货通知对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="arrivalnoticeRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="通知单编号" prop="noticeCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.noticeCode" placeholder="请输入通知单编号" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" inline-prompt active-color="#13ce66" active-text="自动"
                  inactive-text="自动生成" @change="handleAutoGenChange" :disabled="isViewMode" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通知单名称" prop="noticeName">
              <el-input v-model="form.noticeName" placeholder="请输入通知单名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="采购订单编号" prop="poCode">
              <el-input v-model="form.poCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="供应商" prop="vendorId">
              <el-row :gutter="10">
                <el-col :span="18">
                  <el-input v-model="form.vendorName" placeholder="请选择供应商" readonly>
                    <template #append>
                      <el-button v-if="!isViewMode" icon="Search" @click="handleOpenVendorDialog" />
                    </template>
                  </el-input>
                </el-col>
                <el-col :span="6">
                  <el-button v-if="form.vendorId && !isViewMode" type="danger" icon="Delete" @click="clearVendor" />
                </el-col>
              </el-row>
              <el-input v-model="form.vendorId" style="display: none" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" readonly/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系人" prop="contact">
              <el-input v-model="form.contact" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系方式" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系方式" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="到货日期" prop="arrivalDate">
              <el-date-picker clearable
                v-model="form.arrivalDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择到货日期" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
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

    <!-- 供应商选择对话框 -->
    <el-dialog title="选择供应商" v-model="vendorDialogVisible" width="800px" append-to-body>
      <el-form :model="vendorQueryParams" :inline="true" label-width="100px">
        <el-form-item label="供应商名称">
          <el-input v-model="vendorQueryParams.vendorName" placeholder="请输入供应商名称" clearable
            @keyup.enter="handleVendorQuery" />
        </el-form-item>
        <el-form-item label="供应商编码">
          <el-input v-model="vendorQueryParams.vendorCode" placeholder="请输入供应商编码" clearable
            @keyup.enter="handleVendorQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleVendorQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetVendorQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="vendorLoading" :data="vendorList" @row-click="handleVendorSelect">
        <el-table-column label="供应商编码" align="center" prop="vendorCode" />
        <el-table-column label="供应商名称" align="center" prop="vendorName" />
        <el-table-column label="联系人" align="center" prop="contactPerson" />
        <el-table-column label="联系电话" align="center" prop="phone" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleVendorSelect(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="vendorTotal > 0" :total="vendorTotal" v-model:page="vendorQueryParams.pageNum"
        v-model:limit="vendorQueryParams.pageSize" @pagination="handleVendorPagination" />
    </el-dialog>
  </div>
</template>

<script setup name="Arrivalnotice">
import { listArrivalnotice, getArrivalnotice, delArrivalnotice, addArrivalnotice, updateArrivalnotice } from "@/api/core/wm/arrivalnotice"
import { genCode } from "@/api/system/autocode/rule"
import { listVendor } from "@/api/core/bd/vendor"
import { useRouter } from 'vue-router'

const router = useRouter();
const { proxy } = getCurrentInstance()
const { wm_notice_status } = proxy.useDict('wm_notice_status')

const arrivalnoticeList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false) // 新增：标识是否为查看模式
const autoGenFlag = ref(false) 

// 供应商选择相关变量
const vendorDialogVisible = ref(false)
const vendorList = ref([])
const vendorLoading = ref(false)
const vendorTotal = ref(0)

const vendorQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  vendorName: null,
  vendorCode: null
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    noticeCode: null,
    noticeName: null,
    purOrderCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    arrivalDate: null,
    contact: null,
    phone: null,
    status: null,
  },
  rules: {
    noticeCode: [
      { required: true, message: "通知单编号不能为空", trigger: "blur" }
    ],
    noticeName: [
      { required: true, message: "通知单名称不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询到货通知列表 */
function getList() {
  loading.value = true
  listArrivalnotice(queryParams.value).then(response => {
    arrivalnoticeList.value = response.rows
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
    noticeId: null,
    noticeCode: null,
    noticeName: null,
    purOrderCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    arrivalDate: null,
    contact: null,
    phone: null,
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
  proxy.resetForm("arrivalnoticeRef")
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
  ids.value = selection.map(item => item.noticeId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加到货通知"
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
    const selectedRow = arrivalnoticeList.value.find(item => item.noticeId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const noticeId = row.noticeId;
  router.push({
    path: '/core/wm/arrivalnotice/form',
    query: { id: noticeId, action: 'edit' }
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
    const selectedRow = arrivalnoticeList.value.find(item => item.noticeId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const noticeId = row.noticeId;
  router.push({
    path: '/core/wm/arrivalnotice/form',
    query: { id: noticeId, action: 'view' }
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["arrivalnoticeRef"].validate(valid => {
    if (valid) {
      if (form.value.noticeId != null) {
        updateArrivalnotice(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addArrivalnotice(form.value).then(response => {
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
  const _noticeIds = row.noticeId || ids.value
  proxy.$modal.confirm('是否确认删除到货通知编号为"' + _noticeIds + '"的数据项？').then(function () {
    return delArrivalnotice(_noticeIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('wm/arrivalnotice/export', {
    ...queryParams.value
  }, `arrivalnotice_${new Date().getTime()}.xlsx`)
}

// 自动生成编码
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('WM_ARRIVAL_NOTICE_CODE').then(response => {
      form.value.noticeCode = response;
    });
  } else {
    form.value.noticeCode = null;
  }
}

/** 打开供应商选择对话框 */
function openVendorDialog() {
  vendorDialogVisible.value = true
  getVendorList()
}

/** 处理打开供应商选择对话框事件 */
function handleOpenVendorDialog() {
  openVendorDialog()
}

/** 查询供应商列表 */
function getVendorList() {
  vendorLoading.value = true
  listVendor(vendorQueryParams.value).then(response => {
    vendorList.value = response.rows
    vendorTotal.value = response.total
    vendorLoading.value = false
  })
}

/** 供应商搜索按钮操作 */
function handleVendorQuery() {
  vendorQueryParams.value.pageNum = 1
  getVendorList()
}

/** 重置供应商查询 */
function resetVendorQuery() {
  vendorQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    vendorName: null,
    vendorCode: null
  }
  handleVendorQuery()
}

/** 选择供应商 */
function handleVendorSelect(row) {
  form.value.vendorId = row.vendorId
  form.value.vendorCode = row.vendorCode
  form.value.vendorName = row.vendorName
  form.value.contact = row.contactPerson
  form.value.phone = row.phone
  vendorDialogVisible.value = false
}

/** 清除已选择的供应商 */
function clearVendor() {
  form.value.vendorId = null
  form.value.vendorCode = null
  form.value.vendorName = null
  form.value.contact = null
  form.value.phone = null
}

/** 处理供应商分页 */
function handleVendorPagination(pagination) {
  vendorQueryParams.value.pageNum = pagination.page
  vendorQueryParams.value.pageSize = pagination.limit
  getVendorList()
}

getList()
</script>