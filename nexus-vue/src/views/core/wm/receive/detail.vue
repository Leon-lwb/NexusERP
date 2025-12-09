<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="行ID" prop="lineId">
        <el-input v-model="queryParams.lineId" placeholder="请输入行ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="入库单ID" prop="receiveId">
        <el-input v-model="queryParams.receiveId" placeholder="请输入入库单ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品物料ID" prop="materialId">
        <el-input v-model="queryParams.materialId" placeholder="请输入产品物料ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品物料编码" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="请输入产品物料编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品物料名称" prop="materialName">
        <el-input v-model="queryParams.materialName" placeholder="请输入产品物料名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单位" prop="baseUomId">
        <el-input v-model="queryParams.baseUomId" placeholder="请输入单位" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单位名称" prop="baseUomName">
        <el-input v-model="queryParams.baseUomName" placeholder="请输入单位名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="入库数量" prop="quantity">
        <el-input v-model="queryParams.quantity" placeholder="请输入入库数量" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="批次ID" prop="batchId">
        <el-input v-model="queryParams.batchId" placeholder="请输入批次ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchCode">
        <el-input v-model="queryParams.batchCode" placeholder="请输入批次号" clearable @keyup.enter="handleQuery" />
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
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:detail:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['wm:detail:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:detail:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['wm:detail:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:detail:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="detailList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="明细ID" align="center" prop="detailId" />
      <el-table-column label="行ID" align="center" prop="lineId" />
      <el-table-column label="入库单ID" align="center" prop="receiveId" />
      <el-table-column label="产品物料ID" align="center" prop="materialId" />
      <el-table-column label="产品物料编码" align="center" prop="materialCode" />
      <el-table-column label="产品物料名称" align="center" prop="materialName" />
      <el-table-column label="规格型号" align="center" prop="materialSpec" />
      <el-table-column label="单位" align="center" prop="baseUomId" />
      <el-table-column label="单位名称" align="center" prop="baseUomName" />
      <el-table-column label="入库数量" align="center" prop="quantity" />
      <el-table-column label="批次ID" align="center" prop="batchId" />
      <el-table-column label="批次号" align="center" prop="batchCode" />
      <el-table-column label="仓库ID" align="center" prop="warehouseId" />
      <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
      <el-table-column label="仓库名称" align="center" prop="warehouseName" />
      <el-table-column label="库区ID" align="center" prop="areaId" />
      <el-table-column label="库区编码" align="center" prop="areaCode" />
      <el-table-column label="库区名称" align="center" prop="areaName" />
      <el-table-column label="库位ID" align="center" prop="locationId" />
      <el-table-column label="库位编码" align="center" prop="locationCode" />
      <el-table-column label="库位名称" align="center" prop="locationName" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:detail:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['wm:detail:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改物料入库单明细对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="detailRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="行ID" prop="lineId">
          <el-input v-model="form.lineId" placeholder="请输入行ID" />
        </el-form-item>
        <el-form-item label="入库单ID" prop="receiveId">
          <el-input v-model="form.receiveId" placeholder="请输入入库单ID" />
        </el-form-item>
        <el-form-item label="产品物料ID" prop="materialId">
          <el-input v-model="form.materialId" placeholder="请输入产品物料ID" />
        </el-form-item>
        <el-form-item label="产品物料编码" prop="materialCode">
          <el-input v-model="form.materialCode" placeholder="请输入产品物料编码" />
        </el-form-item>
        <el-form-item label="产品物料名称" prop="materialName">
          <el-input v-model="form.materialName" placeholder="请输入产品物料名称" />
        </el-form-item>
        <el-form-item label="规格型号" prop="materialSpec">
          <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="单位" prop="baseUomId">
          <el-input v-model="form.baseUomId" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="单位名称" prop="baseUomName">
          <el-input v-model="form.baseUomName" placeholder="请输入单位名称" />
        </el-form-item>
        <el-form-item label="入库数量" prop="quantity">
          <el-input v-model="form.quantity" placeholder="请输入入库数量" />
        </el-form-item>
        <el-form-item label="批次ID" prop="batchId">
          <el-input v-model="form.batchId" placeholder="请输入批次ID" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchCode">
          <el-input v-model="form.batchCode" placeholder="请输入批次号" />
        </el-form-item>
        <el-form-item label="仓库ID" prop="warehouseId">
          <el-input v-model="form.warehouseId" placeholder="请输入仓库ID" />
        </el-form-item>
        <el-form-item label="仓库编码" prop="warehouseCode">
          <el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" />
        </el-form-item>
        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="库区ID" prop="areaId">
          <el-input v-model="form.areaId" placeholder="请输入库区ID" />
        </el-form-item>
        <el-form-item label="库区编码" prop="areaCode">
          <el-input v-model="form.areaCode" placeholder="请输入库区编码" />
        </el-form-item>
        <el-form-item label="库区名称" prop="areaName">
          <el-input v-model="form.areaName" placeholder="请输入库区名称" />
        </el-form-item>
        <el-form-item label="库位ID" prop="locationId">
          <el-input v-model="form.locationId" placeholder="请输入库位ID" />
        </el-form-item>
        <el-form-item label="库位编码" prop="locationCode">
          <el-input v-model="form.locationCode" placeholder="请输入库位编码" />
        </el-form-item>
        <el-form-item label="库位名称" prop="locationName">
          <el-input v-model="form.locationName" placeholder="请输入库位名称" />
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

<script setup name="Detail">
import { listDetail, getDetail, delDetail, addDetail, updateDetail } from "@/api/core/wm/detail"

const { proxy } = getCurrentInstance()
const route = useRoute()

const detailList = ref([])
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
    lineId: null,
    receiveId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    quantity: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
  },
  rules: {
    materialId: [
      { required: true, message: "产品物料ID不能为空", trigger: "blur" }
    ],
    quantity: [
      { required: true, message: "入库数量不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

onMounted(() => {
  // 检查是否有传入的参数，如果有则直接打开新增对话框
  const action = route.query.action
  if (action === 'add') {
    handleAddFromReceive()
  } else {
    getList()
  }
})

/** 查询物料入库单明细列表 */
function getList() {
  loading.value = true
  listDetail(queryParams.value).then(response => {
    detailList.value = response.rows
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
    detailId: null,
    lineId: null,
    receiveId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    quantity: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
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
  proxy.resetForm("detailRef")
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
  ids.value = selection.map(item => item.detailId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加物料入库单明细"
}

/** 新增按钮操作 - 从收货单跳转过来 */
function handleAddFromReceive() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  
  // 从路由参数中获取值并填入表单
  form.value.lineId = route.query.lineId
  form.value.materialId = route.query.materialId
  form.value.materialCode = route.query.materialCode
  form.value.materialName = route.query.materialName
  form.value.baseUomId = route.query.baseUomId
  form.value.baseUomName = route.query.baseUomName
  form.value.quantity = route.query.quantity
  
  open.value = true
  title.value = "添加物料入库单明细"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false // 设置为编辑模式
  const _detailId = row.detailId || ids.value
  getDetail(_detailId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改物料入库单明细"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true // 设置为查看模式
  const _detailId = row.detailId || ids.value
  getDetail(_detailId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看物料入库单明细"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["detailRef"].validate(valid => {
    if (valid) {
      if (form.value.detailId != null) {
        updateDetail(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addDetail(form.value).then(response => {
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
  const _detailIds = row.detailId || ids.value
  proxy.$modal.confirm('是否确认删除物料入库单明细编号为"' + _detailIds + '"的数据项？').then(function () {
    return delDetail(_detailIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('wm/detail/export', {
    ...queryParams.value
  }, `detail_${new Date().getTime()}.xlsx`)
}

getList()
</script>