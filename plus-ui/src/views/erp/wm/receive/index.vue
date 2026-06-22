<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="入库单编号" prop="receiveCode">
        <el-input v-model="queryParams.receiveCode" placeholder="请输入入库单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="入库单名称" prop="receiveName">
        <el-input v-model="queryParams.receiveName" placeholder="请输入入库单名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="采购订单编号" prop="purCode">
        <el-input v-model="queryParams.purCode" placeholder="请输入采购订单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商名称" prop="vendorName">
        <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="入库日期" prop="receiveDate">
        <el-date-picker clearable v-model="queryParams.receiveDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择入库日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="单据状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择单据状态" clearable>
          <el-option v-for="dict in wm_material_receive_status" :key="dict.value" :label="dict.label"
            :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:receive:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['wm:receive:edit']" v-if="selectedRow && (selectedRow.status === 'DRAFT' || selectedRow.status === 'REJECTED')">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:receive:remove']" v-if="selectedRow && selectedRow.status === 'DRAFT'">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Upload" :disabled="single" @click="handleShelf"
          v-hasPermi="['wm:receive:edit']" v-if="selectedRow && selectedRow.status === 'APPROVED'">上架</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Check" :disabled="single" @click="handleConfirmShelf"
          v-hasPermi="['wm:receive:shelf']" v-if="selectedRow && selectedRow.status === 'APPROVED'">确认上架</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Finished" :disabled="single" @click="handleExecute"
          v-hasPermi="['wm:receive:execute']" v-if="selectedRow && selectedRow.status === 'SHELVED'">入库</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['wm:receive:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:receive:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="receiveList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="入库单ID" align="center" prop="receiveId" />
      <el-table-column label="入库单编号" align="center" prop="receiveCode" />
      <el-table-column label="入库单名称" align="center" prop="receiveName" />
      <el-table-column label="到货通知单编号" align="center" prop="noticeCode" />
      <el-table-column label="采购订单编号" align="center" prop="purCode" />
      <el-table-column label="入库日期" align="center" prop="receiveDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.receiveDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单据状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_material_receive_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <!-- <el-table-column label="上架人" align="center" prop="shelfByName" width="120">
        <template #default="scope">
          <span v-if="scope.row.status === 'SHELVED' || scope.row.status === 'FINISHED'">
            {{ scope.row.shelfByName || '-' }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="上架时间" align="center" prop="shelfTime" width="180">
        <template #default="scope">
          <span v-if="scope.row.status === 'SHELVED' || scope.row.status === 'FINISHED'">
            {{ parseTime(scope.row.shelfTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column> -->
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:receive:edit']" v-if="scope.row.status === 'DRAFT' || scope.row.status === 'REJECTED'">修改</el-button>
          <el-button link type="primary" icon="Upload" @click="handleShelf(scope.row)"
            v-hasPermi="['wm:receive:edit']" v-if="scope.row.status === 'APPROVED'">上架</el-button>
          <el-button link type="warning" icon="Check" @click="handleConfirmShelfRow(scope.row)"
            v-hasPermi="['wm:receive:shelf']" v-if="scope.row.status === 'APPROVED'">确认上架</el-button>
          <el-button link type="primary" icon="Finished" @click="handleExecuteRow(scope.row)"
            v-hasPermi="['wm:receive:execute']" v-if="scope.row.status === 'SHELVED'">入库</el-button>
          <el-button link type="primary" icon="View" @click="handleViewFlow(scope.row)"
            v-hasPermi="['wm:receive:query']" v-if="scope.row.status === 'APPROVING' || scope.row.status === 'APPROVED'">查看流程</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['wm:receive:remove']" v-if="scope.row.status === 'DRAFT'">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改物料入库单对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="receiveRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="入库单编号" prop="receiveCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.receiveCode" placeholder="请输入入库单编号" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" inline-prompt active-color="#13ce66" active-text="自动"
                  inactive-text="自动生成" @change="handleAutoGenChange" :disabled="isViewMode" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库单名称" prop="receiveName">
              <el-input v-model="form.receiveName" placeholder="请输入入库单名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="到货通知单" prop="noticeCode">
              <el-row :gutter="10">
                <el-col :span="18">
                  <el-input v-model="form.noticeCode" placeholder="请选择到货通知单" readonly>
                    <template #append>
                      <el-button v-if="!isViewMode" icon="Search" @click="handleOpenArrivalNoticeDialog"/>
                    </template>
                  </el-input>
                </el-col>
              </el-row>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购订单" prop="purCode">
              <el-input v-model="form.purCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="入库日期" prop="receiveDate">
              <el-date-picker clearable v-model="form.receiveDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择入库日期">
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

    <!-- 到货通知选择对话框 -->
    <el-dialog title="选择到货通知单" v-model="arrivalNoticeDialogVisible" width="800px" append-to-body>
      <el-form :model="arrivalNoticeQueryParams" :inline="true" label-width="100px">
        <el-form-item label="通知单编号">
          <el-input v-model="arrivalNoticeQueryParams.noticeCode" placeholder="请输入通知单编号" clearable
            @keyup.enter="handleArrivalNoticeQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleArrivalNoticeQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetArrivalNoticeQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="arrivalNoticeLoading" :data="arrivalNoticeList" @row-click="handleArrivalNoticeSelect">
        <el-table-column label="通知单编号" align="center" prop="noticeCode" />
        <el-table-column label="通知单名称" align="center" prop="noticeName" />
        <el-table-column label="供应商名称" align="center" prop="vendorName" />
        <el-table-column label="到货日期" align="center" prop="arrivalDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.arrivalDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleArrivalNoticeSelect(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="arrivalNoticeTotal > 0" :total="arrivalNoticeTotal" v-model:page="arrivalNoticeQueryParams.pageNum"
        v-model:limit="arrivalNoticeQueryParams.pageSize" @pagination="handleArrivalNoticePagination" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Receive">
import { listReceive, getReceive, delReceive, addReceive, updateReceive, execute, confirmShelf, getReceiveProcId } from "@/api/erp/wm/receive"
import { listArrivalnotice } from "@/api/erp/wm/arrivalnotice"
import { useRouter } from 'vue-router'
import { previewCode } from "@/api/system/autocode/rule"

const router = useRouter();
const { proxy } = getCurrentInstance() as any
const { wm_material_receive_status } = toRefs<any>(proxy.useDict('wm_material_receive_status'))

const receiveList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)
const autoGenFlag = ref(false)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
    status: null,
  },
  rules: {
    receiveCode: [
      { required: true, message: "入库单编号不能为空", trigger: "blur" }
    ],
    receiveName: [
      { required: true, message: "入库单名称不能为空", trigger: "blur" }
    ],
  }
})

const selectedRow = computed(() => {
  if (ids.value.length === 1) {
    return receiveList.value.find((item: any) => item.receiveId === ids.value[0]);
  }
  return null;
});

const { queryParams, form, rules } = toRefs(data)

/** 查询物料入库单列表 */
function getList() {
  loading.value = true
  listReceive(queryParams.value).then((response: any) => {
    receiveList.value = response.rows
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
    receiveId: null,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
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
  proxy.resetForm("receiveRef")
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
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.receiveId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加物料入库单"
}

/** 修改按钮操作 */
function handleUpdate(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }

  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行修改");
      return;
    }
    const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  // 校验状态：只有草稿和已驳回状态才能修改
  if (row.status !== 'DRAFT' && row.status !== 'REJECTED') {
    proxy.$modal.msgWarning('当前状态不允许修改，只有草稿和已驳回状态才能修改！');
    return;
  }

  const receiveId = row.receiveId;
  router.push({
    path: '/erp/wm/receive/form',
    query: { id: receiveId, action: 'edit' }
  });
}

/** 查看按钮操作 */
function handleView(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }

  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行查看");
      return;
    }
    const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const receiveId = row.receiveId;
  router.push({
    path: '/erp/wm/receive/form',
    query: { id: receiveId, action: 'view' }
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["receiveRef"].validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.receiveCode = undefined;
      }
      if (form.value.receiveId != null) {
        updateReceive(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addReceive(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row: any) {
  // 校验状态：只有草稿状态才能删除
  if (row.status !== 'DRAFT') {
    proxy.$modal.msgWarning('当前状态不允许删除，只有草稿状态才能删除！');
    return;
  }
  const _receiveIds = row.receiveId || ids.value
  proxy.$modal.confirm('是否确认删除物料入库单编号为"' + _receiveIds + '"的数据项？').then(function () {
    return delReceive(_receiveIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 上架按钮操作 */
function handleShelf(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }

  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行上架");
      return;
    }
    const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  // 校验状态：只有待上架状态才能执行上架操作
  if (row.status !== 'APPROVED') {
    proxy.$modal.msgWarning('当前状态不允许上架，只有待上架状态才能执行上架操作！');
    return;
  }

  const receiveId = row.receiveId;
  router.push({
    path: '/erp/wm/receive/form',
    query: { id: receiveId, action: 'edit', pageType: 'shelf' }
  });
}

/** 入库按钮操作 */
function handleExecute() {
  if (ids.value.length !== 1) {
    proxy.$modal.msgWarning("请选择一条数据进行入库");
    return;
  }
  const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
  if (!selectedRow) {
    proxy.$modal.msgWarning("未找到选中的数据");
    return;
  }

  // 校验状态：只有待入库状态才能执行入库操作
  if (selectedRow.status !== 'SHELVED') {
    proxy.$modal.msgWarning('当前状态不允许入库，只有待入库状态才能执行入库操作！');
    return;
  }

  proxy.$modal.confirm('是否确认执行物料入库操作？').then(function() {
    return execute(selectedRow.receiveId, {});
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("入库成功");
  }).catch(() => { });
}

/** 确认上架按钮操作 */
function handleConfirmShelf() {
  if (ids.value.length !== 1) {
    proxy.$modal.msgWarning("请选择一条数据进行确认上架");
    return;
  }
  const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
  if (!selectedRow) {
    proxy.$modal.msgWarning("未找到选中的数据");
    return;
  }
  handleConfirmShelfRow(selectedRow);
}

/** 行内确认上架按钮操作 */
function handleConfirmShelfRow(row: any) {
  // 校验状态：只有待上架状态才能执行确认上架操作
  if (row.status !== 'APPROVED') {
    proxy.$modal.msgWarning('当前状态不允许确认上架，只有待上架状态才能执行确认上架操作！');
    return;
  }
  proxy.$modal.confirm('是否确认上架？确认后单据状态将变为"待入库"。').then(function() {
    return confirmShelf(row.receiveId);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("确认上架成功");
  }).catch(() => { });
}

/** 行内入库按钮操作 */
function handleExecuteRow(row: any) {
  // 校验状态：只有待入库状态才能执行入库操作
  if (row.status !== 'SHELVED') {
    proxy.$modal.msgWarning('当前状态不允许入库，只有待入库状态才能执行入库操作！');
    return;
  }
  proxy.$modal.confirm('是否确认执行物料入库操作？').then(function() {
    return execute(row.receiveId, {});
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("入库成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('/erp/wm/receive/export', {
    ...queryParams.value
  }, `receive_${new Date().getTime()}.xlsx`)
}

/** 查看流程按钮操作 */
function handleViewFlow(row: any) {
  getReceiveProcId(String(row.receiveId)).then((res: any) => {
    if (res.code === 200 && res.data) {
      router.push('/workflow/process/detail/' + res.data)
    } else {
      proxy.$modal.msgWarning('暂无流程信息')
    }
  }).catch(() => {
    proxy.$modal.msgWarning('获取流程信息失败')
  })
}

// 自动生成编码
function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_MATERIAL_RECEIVE_CODE').then((response: any) => {
      form.value.receiveCode = response;
    });
  } else {
    form.value.receiveCode = null;
  }
}

// 到货通知选择相关变量
const arrivalNoticeDialogVisible = ref(false)
const arrivalNoticeList = ref([])
const arrivalNoticeLoading = ref(false)
const arrivalNoticeTotal = ref(0)

const arrivalNoticeQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  noticeCode: null
})

/** 打开到货通知选择对话框 */
function handleOpenArrivalNoticeDialog() {
  arrivalNoticeDialogVisible.value = true
  getArrivalNoticeList()
}

/** 查询到货通知列表 */
function getArrivalNoticeList() {
  arrivalNoticeLoading.value = true
  listArrivalnotice(arrivalNoticeQueryParams.value).then((response: any) => {
    arrivalNoticeList.value = response.rows
    arrivalNoticeTotal.value = response.total
    arrivalNoticeLoading.value = false
  })
}

/** 到货通知搜索按钮操作 */
function handleArrivalNoticeQuery() {
  arrivalNoticeQueryParams.value.pageNum = 1
  getArrivalNoticeList()
}

/** 重置到货通知查询 */
function resetArrivalNoticeQuery() {
  arrivalNoticeQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    noticeCode: null
  }
  handleArrivalNoticeQuery()
}

/** 选择到货通知 */
function handleArrivalNoticeSelect(row: any) {
  form.value.noticeId = row.noticeId
  form.value.noticeCode = row.noticeCode
  form.value.vendorId = row.vendorId
  form.value.vendorCode = row.vendorCode
  form.value.vendorName = row.vendorName
  arrivalNoticeDialogVisible.value = false
}

/** 处理到货通知分页 */
function handleArrivalNoticePagination(pagination: any) {
  arrivalNoticeQueryParams.value.pageNum = pagination.page
  arrivalNoticeQueryParams.value.pageSize = pagination.limit
  getArrivalNoticeList()
}

onActivated(() => {
  getList()
})
</script>
