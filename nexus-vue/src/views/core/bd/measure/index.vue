<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="单位编码" prop="uomCode">
        <el-input v-model="queryParams.uomCode" placeholder="请输入单位编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单位名称" prop="uomName">
        <el-input v-model="queryParams.uomName" placeholder="请输入单位名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单位符号" prop="uomSymbol">
        <el-input v-model="queryParams.uomSymbol" placeholder="请输入单位符号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单位类别" prop="category">
        <el-input v-model="queryParams.category" placeholder="请输入单位类别" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="基本单位" prop="isBase">
        <el-select v-model="queryParams.isBase" placeholder="是/否" clearable style="width: 80px">
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="单位状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="启用/停用" clearable style="width: 200px">
          <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:measure:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['bd:measure:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['bd:measure:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['bd:measure:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['bd:measure:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="measureList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="排序" align="center" prop="sortOrder" />
      <el-table-column label="单位编码" align="center" prop="uomCode" />
      <el-table-column label="单位名称" align="center" prop="uomName" />
      <el-table-column label="单位符号" align="center" prop="uomSymbol" />
      <el-table-column label="单位类别" align="center" prop="category" />
      <el-table-column label="是否基本单位" align="center" prop="isBase">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isBase" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['bd:measure:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['bd:measure:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改计量单位对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="measureRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单位编码" prop="uomCode">
              <el-input v-model="form.uomCode" placeholder="请输入单位编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位名称" prop="uomName">
              <el-input v-model="form.uomName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单位符号" prop="uomSymbol">
              <el-input v-model="form.uomSymbol" placeholder="请输入单位符号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位类别" prop="category">
              <el-input v-model="form.category" placeholder="重量、长度、数量等" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="基本单位" prop="isBase">
              <el-select v-model="form.isBase" placeholder="是/否" style="width: 100%" @change="handleBaseUnitChange">
                <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-show="form.isBase === '0'">
            <el-form-item label="基本单位" prop="primaryName">
              <el-input v-model="form.primaryName" readonly placeholder="请选择基本单位">
                <template #append>
                  <el-button icon="Search" @click="openBaseUnitSelect"></el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 转换率字段独立一行 -->
        <el-row :gutter="20" v-show="form.isBase === '0'">
          <el-col :span="12">
            <el-form-item label="转换率" prop="conversionRate">
              <el-input v-model="form.conversionRate" placeholder="相对于基本单位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 可以放置其他字段或留空 -->
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" placeholder="请输入排序" controls-position="right"
                style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
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

    <!-- 基本单位选择对话框 -->
    <el-dialog title="选择基本单位" v-model="baseUnitVisible" width="800px" append-to-body>
      <el-form :model="baseUnitQueryParams" ref="baseUnitQueryRef" :inline="true">
        <el-form-item label="单位编码" prop="uomCode">
          <el-input v-model="baseUnitQueryParams.uomCode" placeholder="请输入单位编码" clearable
            @keyup.enter="getBaseUnitList" />
        </el-form-item>
        <el-form-item label="单位名称" prop="uomName">
          <el-input v-model="baseUnitQueryParams.uomName" placeholder="请输入单位名称" clearable
            @keyup.enter="getBaseUnitList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getBaseUnitList">搜索</el-button>
          <el-button icon="Refresh" @click="resetBaseUnitQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="baseUnitLoading" :data="baseUnitList" @row-dblclick="selectBaseUnit">
        <el-table-column label="单位编码" align="center" prop="uomCode" />
        <el-table-column label="单位名称" align="center" prop="uomName" />
        <el-table-column label="单位符号" align="center" prop="uomSymbol" />
        <el-table-column label="单位类别" align="center" prop="category" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectBaseUnit(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="baseUnitTotal > 0" :total="baseUnitTotal" v-model:page="baseUnitQueryParams.pageNum"
        v-model:limit="baseUnitQueryParams.pageSize" @pagination="getBaseUnitList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="baseUnitVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Measure">
import { listMeasure, getMeasure, delMeasure, addMeasure, updateMeasure } from "@/api/core/bd/measure"

const { proxy } = getCurrentInstance()
const { sys_true_or_false, sys_core_status } = proxy.useDict('sys_true_or_false', 'sys_core_status')

const measureList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)

// 添加基本单位选择相关变量
const baseUnitVisible = ref(false)
const baseUnitList = ref([])
const baseUnitLoading = ref(false)
const baseUnitTotal = ref(0)
const baseUnitName = ref('')

const baseUnitQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  uomCode: undefined,
  uomName: undefined,
  isBase: '1' // 只查询基本单位，使用字符串类型
})

// 修改验证规则，使其动态生效
const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    uomCode: null,
    uomName: null,
    uomSymbol: null,
    category: null,
    isBase: null,
    status: null,
  },
  rules: {
    uomCode: [
      { required: true, message: "单位编码不能为空", trigger: "blur" }
    ],
    uomName: [
      { required: true, message: "单位名称不能为空", trigger: "blur" }
    ],
    uomSymbol: [
      { required: true, message: "单位符号不能为空", trigger: "blur" }
    ],
    category: [
      { required: true, message: "单位类别不能为空", trigger: "blur" }
    ],
    isBase: [
      { required: true, message: "是否基本单位不能为空", trigger: "change" }
    ],
    conversionRate: [
      { required: false, message: "转换率不能为空", trigger: "blur" }
    ],
    status: [
      { required: true, message: "状态用不能为空", trigger: "change" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询计量单位列表 */
function getList() {
  loading.value = true
  listMeasure(queryParams.value).then(response => {
    measureList.value = response.rows
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
    uomId: null,
    uomCode: null,
    uomName: null,
    uomSymbol: null,
    category: null,
    isBase: null,
    primaryId: null,
    primaryName: null,
    conversionRate: null,
    status: null,
    sortOrder: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  baseUnitName.value = ''
  proxy.resetForm("measureRef")
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
  ids.value = selection.map(item => item.uomId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加计量单位"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false
  const _uomId = row.uomId || ids.value
  getMeasure(_uomId).then(response => {
    const data = response.data
    // 确保 isBase 是字符串类型，与字典匹配
    data.isBase = String(data.isBase)
    // 确保 status 是字符串类型，与字典匹配
    data.status = String(data.status)
    form.value = data
    // 如果有基本单位ID，加载基本单位名称
    if (response.data.primaryId) {
      loadPrimaryUnitName(response.data.primaryId)
    }
    open.value = true
    title.value = "修改计量单位"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true
  const _uomId = row.uomId || ids.value
  getMeasure(_uomId).then(response => {
    const data = response.data
    // 确保 isBase 是字符串类型，与字典匹配
    data.isBase = String(data.isBase)
    // 确保 status 是字符串类型，与字典匹配
    data.status = String(data.status)
    form.value = data
    // 如果有基本单位ID，加载基本单位名称
    if (response.data.primaryId) {
      loadPrimaryUnitName(response.data.primaryId)
    }
    open.value = true
    title.value = "查看计量单位"
  })
}

// 加载基本单位名称
function loadPrimaryUnitName(primaryId) {
  if (primaryId) {
    getMeasure(primaryId).then(response => {
      form.value.primaryName = response.data.uomName
      baseUnitName.value = response.data.uomName
    })
  }
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["measureRef"].validate(valid => {
    if (valid) {
      // 动态验证转换率字段
      if (form.value.isBase === '0' && !form.value.conversionRate) {
        proxy.$modal.msgError("转换率不能为空");
        return;
      }

      // 清理数据，确保只提交需要的字段
      const submitForm = { ...form.value }
      if (submitForm.isBase === '1') {
        // 如果是基本单位，清除基本单位相关字段
        submitForm.primaryId = null
        submitForm.primaryName = null
      }

      // 提交前将 isBase 和 status 转换为数字类型
      submitForm.isBase = parseInt(submitForm.isBase)
      submitForm.status = parseInt(submitForm.status)

      if (submitForm.uomId != null) {
        updateMeasure(submitForm).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addMeasure(submitForm).then(response => {
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
  const _uomIds = row.uomId || ids.value
  proxy.$modal.confirm('是否确认删除计量单位编号为"' + _uomIds + '"的数据项？').then(function () {
    return delMeasure(_uomIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('bd/measure/export', {
    ...queryParams.value
  }, `measure_${new Date().getTime()}.xlsx`)
}

// 基本单位选择相关方法
function handleBaseUnitChange(value) {
  // 当切换为基本单位时，清空基本单位ID和名称
  if (value === '1') {
    form.value.primaryId = null
    baseUnitName.value = ''
  }
}

// 打开基本单位选择对话框
function openBaseUnitSelect() {
  if (form.value.isBase !== '0') {
    proxy.$modal.msgWarning("请先选择'基本单位'为'否'")
    return
  }
  baseUnitVisible.value = true
  getBaseUnitList()
}

// 获取基本单位列表
function getBaseUnitList() {
  baseUnitLoading.value = true
  // 设置只查询基本单位
  baseUnitQueryParams.isBase = '1'
  listMeasure(baseUnitQueryParams).then(response => {
    baseUnitList.value = response.rows
    baseUnitTotal.value = response.total
    baseUnitLoading.value = false
  })
}

// 选择基本单位
function selectBaseUnit(row) {
  form.value.primaryId = row.uomId
  form.value.primaryName = row.uomName
  baseUnitName.value = row.uomName
  baseUnitVisible.value = false
}

// 重置基本单位查询
function resetBaseUnitQuery() {
  baseUnitQueryParams.uomCode = undefined
  baseUnitQueryParams.uomName = undefined
  getBaseUnitList()
}

getList()
</script>