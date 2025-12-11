<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="入库单编号" prop="receiveCode">
        <el-input v-model="queryParams.receiveCode" placeholder="请输入入库单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="入库单名称" prop="receiveName">
        <el-input v-model="queryParams.receiveName" placeholder="请输入入库单名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <!-- <el-form-item label="来料检验单ID" prop="iqcId">
        <el-input v-model="queryParams.iqcId" placeholder="请输入来料检验单ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="来料检验单编号" prop="iqcCode">
        <el-input v-model="queryParams.iqcCode" placeholder="请输入来料检验单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="到货通知单ID" prop="noticeId">
        <el-input v-model="queryParams.noticeId" placeholder="请输入到货通知单ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="到货通知单编号" prop="noticeCode">
        <el-input v-model="queryParams.noticeCode" placeholder="请输入到货通知单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="采购订单ID" prop="purId">
        <el-input v-model="queryParams.purId" placeholder="请输入采购订单ID" clearable @keyup.enter="handleQuery" />
      </el-form-item> -->
      <el-form-item label="采购订单编号" prop="purCode">
        <el-input v-model="queryParams.purCode" placeholder="请输入采购订单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <!-- <el-form-item label="供应商ID" prop="vendorId">
        <el-input v-model="queryParams.vendorId" placeholder="请输入供应商ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="供应商编码" prop="vendorCode">
        <el-input v-model="queryParams.vendorCode" placeholder="请输入供应商编码" clearable @keyup.enter="handleQuery" />
      </el-form-item> -->
      <el-form-item label="供应商名称" prop="vendorName">
        <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <!-- <el-form-item label="供应商简称" prop="vendorNick">
        <el-input v-model="queryParams.vendorNick" placeholder="请输入供应商简称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库ID" prop="warehouseId">
        <el-input v-model="queryParams.warehouseId" placeholder="请输入仓库ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库编码" prop="warehouseCode">
        <el-input v-model="queryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库名称" prop="warehouseName">
        <el-input v-model="queryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区ID" prop="areaId">
        <el-input v-model="queryParams.areaId" placeholder="请输入库区ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区编码" prop="areaCode">
        <el-input v-model="queryParams.areaCode" placeholder="请输入库区编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区名称" prop="areaName">
        <el-input v-model="queryParams.areaName" placeholder="请输入库区名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位ID" prop="locationId">
        <el-input v-model="queryParams.locationId" placeholder="请输入库位ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位编码" prop="locationCode">
        <el-input v-model="queryParams.locationCode" placeholder="请输入库位编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位名称" prop="locationName">
        <el-input v-model="queryParams.locationName" placeholder="请输入库位名称" clearable @keyup.enter="handleQuery" />
      </el-form-item> -->
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
          v-hasPermi="['wm:receive:edit']" v-if="selectedRow && selectedRow.status === 'PREPARE'">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:receive:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Upload" :disabled="single" @click="handleShelf"
          v-hasPermi="['wm:receive:edit']" v-if="selectedRow && selectedRow.status === 'APPROVING'">上架</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Finished" :disabled="single" @click="handleExecute"
          v-hasPermi="['wm:receive:edit']" v-if="selectedRow && selectedRow.status === 'APPROVED'">入库</el-button>
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
      <!-- <el-table-column label="来料检验单ID" align="center" prop="iqcId" />
      <el-table-column label="来料检验单编号" align="center" prop="iqcCode" />
      <el-table-column label="到货通知单ID" align="center" prop="noticeId" /> -->
      <el-table-column label="到货通知单编号" align="center" prop="noticeCode" />
      <!-- <el-table-column label="采购订单ID" align="center" prop="purId" /> -->
      <el-table-column label="采购订单编号" align="center" prop="purCode" />
      <!-- <el-table-column label="供应商ID" align="center" prop="vendorId" />
      <el-table-column label="供应商编码" align="center" prop="vendorCode" />
      <el-table-column label="供应商名称" align="center" prop="vendorName" />
      <el-table-column label="供应商简称" align="center" prop="vendorNick" />
      <el-table-column label="仓库ID" align="center" prop="warehouseId" />
      <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
      <el-table-column label="仓库名称" align="center" prop="warehouseName" />
      <el-table-column label="库区ID" align="center" prop="areaId" />
      <el-table-column label="库区编码" align="center" prop="areaCode" />
      <el-table-column label="库区名称" align="center" prop="areaName" />
      <el-table-column label="库位ID" align="center" prop="locationId" />
      <el-table-column label="库位编码" align="center" prop="locationCode" />
      <el-table-column label="库位名称" align="center" prop="locationName" /> -->
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
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:receive:edit']" v-if="scope.row.status === 'PREPARE'">修改</el-button>
          <el-button link type="primary" icon="Upload" @click="handleShelf(scope.row)"
            v-hasPermi="['wm:receive:edit']" v-if="scope.row.status === 'APPROVING'">上架</el-button>
          <el-button link type="primary" icon="Finished" @click="handleExecuteRow(scope.row)"
            v-hasPermi="['wm:receive:edit']" v-if="scope.row.status === 'APPROVED'">入库</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['wm:receive:remove']">删除</el-button>
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
        <!-- <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="来料检验单ID" prop="iqcId">
              <el-input v-model="form.iqcId" placeholder="请输入来料检验单ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来料检验单编号" prop="iqcCode">
              <el-input v-model="form.iqcCode" placeholder="请输入来料检验单编号" />
            </el-form-item>
          </el-col>
        </el-row> -->
        <el-row :gutter="20">
          <!-- <el-col :span="12">
            <el-form-item label="到货通知单ID" prop="noticeId">
              <el-input v-model="form.noticeId" placeholder="请输入到货通知单ID" />
            </el-form-item>
          </el-col> -->
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
        <!-- <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库ID" prop="warehouseId">
              <el-input v-model="form.warehouseId" placeholder="请输入仓库ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库编码" prop="warehouseCode">
              <el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库名称" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库区ID" prop="areaId">
              <el-input v-model="form.areaId" placeholder="请输入库区ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库区编码" prop="areaCode">
              <el-input v-model="form.areaCode" placeholder="请输入库区编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库区名称" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入库区名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库位ID" prop="locationId">
              <el-input v-model="form.locationId" placeholder="请输入库位ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位编码" prop="locationCode">
              <el-input v-model="form.locationCode" placeholder="请输入库位编码" />
            </el-form-item>
          </el-col>
        </el-row> -->
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

<script setup name="Receive">
import { listReceive, getReceive, delReceive, addReceive, updateReceive, execute } from "@/api/core/wm/receive"
import { listArrivalnotice } from "@/api/core/wm/arrivalnotice"
import { useRouter } from 'vue-router'
import { genCode } from "@/api/system/autocode/rule"

const router = useRouter();
const { proxy } = getCurrentInstance()
const { wm_material_receive_status } = proxy.useDict('wm_material_receive_status')

const receiveList = ref([])
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
    return receiveList.value.find(item => item.receiveId === ids.value[0]);
  }
  return null;
});

const { queryParams, form, rules } = toRefs(data)

/** 查询物料入库单列表 */
function getList() {
  loading.value = true
  listReceive(queryParams.value).then(response => {
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
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.receiveId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加物料入库单"
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
        const selectedRow = receiveList.value.find(item => item.receiveId == ids.value[0]);
        if (!selectedRow) {
          proxy.$modal.msgWarning("未找到选中的数据");
          return;
        }
        row = selectedRow;
      }

      const receiveId = row.receiveId;
      router.push({
        path: '/core/wm/receive/form',
        query: { id: receiveId, action: 'edit' }
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
        const selectedRow = receiveList.value.find(item => item.receiveId == ids.value[0]);
        if (!selectedRow) {
          proxy.$modal.msgWarning("未找到选中的数据");
          return;
        }
        row = selectedRow;
      }

      const receiveId = row.receiveId;
      router.push({
        path: '/core/wm/receive/form',
        query: { id: receiveId, action: 'view' }
      });
    }

/** 提交按钮 */
function submitForm() {
  proxy.$refs["receiveRef"].validate(valid => {
    if (valid) {
      if (form.value.receiveId != null) {
        updateReceive(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addReceive(form.value).then(response => {
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
  const _receiveIds = row.receiveId || ids.value
  proxy.$modal.confirm('是否确认删除物料入库单编号为"' + _receiveIds + '"的数据项？').then(function () {
    return delReceive(_receiveIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 上架按钮操作 */
function handleShelf(row) {
  if (row && row.type && row instanceof Event) {
    // 这是一个事件对象，表示没有传入数据参数
    row = null;
  }

  // 如果没有传入row参数，则从选中的数据中获取
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning("请选择一条数据进行上架");
      return;
    }
    // 从列表中找到选中的记录
    const selectedRow = receiveList.value.find(item => item.receiveId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning("未找到选中的数据");
      return;
    }
    row = selectedRow;
  }

  const receiveId = row.receiveId;
  router.push({
    path: '/core/wm/receive/form',
    query: { id: receiveId, action: 'edit', pageType: 'shelf' }
  });
}

/** 入库按钮操作 */
function handleExecute() {
  if (ids.value.length !== 1) {
    proxy.$modal.msgWarning("请选择一条数据进行入库");
    return;
  }
  const selectedRow = receiveList.value.find(item => item.receiveId == ids.value[0]);
  if (!selectedRow) {
    proxy.$modal.msgWarning("未找到选中的数据");
    return;
  }
  
  proxy.$modal.confirm('是否确认执行物料入库操作？').then(function() {
    return execute(selectedRow.receiveId, {});
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("入库成功");
  }).catch(() => { });
}

/** 行内入库按钮操作 */
function handleExecuteRow(row) {
  proxy.$modal.confirm('是否确认执行物料入库操作？').then(function() {
    return execute(row.receiveId, {});
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("入库成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('wm/receive/export', {
    ...queryParams.value
  }, `receive_${new Date().getTime()}.xlsx`)
}

// 自动生成编码
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('WM_MATERIAL_RECEIVE_CODE').then(response => {
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
  listArrivalnotice(arrivalNoticeQueryParams.value).then(response => {
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
function handleArrivalNoticeSelect(row) {
  form.value.noticeId = row.noticeId
  form.value.noticeCode = row.noticeCode
  form.value.vendorId = row.vendorId
  form.value.vendorCode = row.vendorCode
  form.value.vendorName = row.vendorName
  arrivalNoticeDialogVisible.value = false
}

/** 处理到货通知分页 */
function handleArrivalNoticePagination(pagination) {
  arrivalNoticeQueryParams.value.pageNum = pagination.page
  arrivalNoticeQueryParams.value.pageSize = pagination.limit
  getArrivalNoticeList()
}

getList()
</script>