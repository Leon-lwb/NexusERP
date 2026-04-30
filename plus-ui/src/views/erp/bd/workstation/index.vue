<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="工作站编码" prop="workstationCode">
        <el-input v-model="queryParams.workstationCode" placeholder="请输入工作站编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="工作站名称" prop="workstationName">
        <el-input v-model="queryParams.workstationName" placeholder="请输入工作站名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="车间编码" prop="workshopCode">
        <el-input v-model="queryParams.workshopCode" placeholder="请输入车间编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="车间名称" prop="workshopName">
        <el-input v-model="queryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="标准产能" prop="standardCapacity">
        <el-input v-model="queryParams.standardCapacity" placeholder="请输入标准产能" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px;">
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd"
          v-hasPermi="['bd:workstation:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['bd:workstation:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['bd:workstation:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['bd:workstation:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['bd:workstation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="workstationList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工作站ID" align="center" prop="workstationId" />
      <el-table-column label="工作站编码" align="center" prop="workstationCode" />
      <el-table-column label="工作站名称" align="center" prop="workstationName" />
      <!-- <el-table-column label="所属车间ID" align="center" prop="workshopId" /> -->
      <el-table-column label="车间编码" align="center" prop="workshopCode" />
      <el-table-column label="车间名称" align="center" prop="workshopName" />
      <el-table-column label="工作中心描述" align="center" prop="workstationDesc" />
      <!-- <el-table-column label="设备ID" align="center" prop="equipmentId" /> -->
      <el-table-column label="标准操作工人数" align="center" prop="operatorCount" />
      <el-table-column label="标准产能" align="center" prop="standardCapacity" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['bd:workstation:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['bd:workstation:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改工作站对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="workstationRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工作站编码" prop="workstationCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.workstationCode" placeholder="请输入工作站编码" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="自动生成" @change="handleAutoGenChange"
                  :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作站名称" prop="workstationName">
              <el-input v-model="form.workstationName" placeholder="请输入工作站名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属车间" prop="workshopId">
              <el-input v-model="form.workshopName" placeholder="请选择所属车间" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openWorkshopSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备ID" prop="equipmentId">
              <el-input v-model="form.equipmentId" placeholder="请输入设备ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标准操作人数" prop="operatorCount">
              <el-input v-model="form.operatorCount" placeholder="请输入标准操作工人数" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标准产能" prop="standardCapacity">
              <el-input v-model="form.standardCapacity" placeholder="请输入标准产能" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="车间编码" prop="workshopCode">
              <el-input v-model="form.workshopCode" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属车间ID" prop="workshopId">
              <el-input v-model="form.workshopId" readonly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="工作中心描述" prop="workstationDesc">
              <el-input v-model="form.workstationDesc" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_core_status" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
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

    <!-- 车间选择对话框 -->
    <el-dialog title="选择车间" v-model="workshopVisible" width="800px" append-to-body>
      <el-form :model="workshopQueryParams" :inline="true">
        <el-form-item label="车间编码" prop="workshopCode">
          <el-input v-model="workshopQueryParams.workshopCode" placeholder="请输入车间编码" clearable @keyup.enter="getWorkshopList" />
        </el-form-item>
        <el-form-item label="车间名称" prop="workshopName">
          <el-input v-model="workshopQueryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter="getWorkshopList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getWorkshopList">搜索</el-button>
          <el-button icon="Refresh" @click="resetWorkshopQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="workshopLoading" :data="workshopList" @row-dblclick="selectWorkshop">
        <el-table-column label="车间ID" align="center" prop="workshopId" />
        <el-table-column label="车间编码" align="center" prop="workshopCode" />
        <el-table-column label="车间名称" align="center" prop="workshopName" />
        <el-table-column label="车间描述" align="center" prop="workshopDesc" />
        <el-table-column label="车间主任" align="center" prop="managerName" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectWorkshop(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="workshopList.length > 0" :total="workshopTotal" v-model:page="workshopQueryParams.pageNum"
        v-model:limit="workshopQueryParams.pageSize" @pagination="getWorkshopList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="workshopVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Workstation">
import { listWorkstation, getWorkstation, delWorkstation, addWorkstation, updateWorkstation } from "@/api/erp/bd/workstation"
import { listWorkshop } from "@/api/erp/bd/workshop"
import { genCode } from "@/api/system/autocode/rule"

const { proxy } = getCurrentInstance()
const { sys_core_status } = proxy.useDict('sys_core_status')
const autoGenFlag = ref(false)

const workstationList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false) // 新增：标识是否为查看模式

// 添加车间列表相关变量
const workshopList = ref([])
const workshopLoading = ref(false)
const workshopVisible = ref(false)
const workshopTotal = ref(0)
const workshopQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  workshopCode: undefined,
  workshopName: undefined
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    workstationCode: null,
    workstationName: null,
    workshopCode: null,
    workshopName: null,
    workstationDesc: null,
    equipmentId: null,
    operatorCount: null,
    standardCapacity: null,
    status: null,
  },
  rules: {
    workstationCode: [
      { required: true, message: "工作站编码不能为空", trigger: "blur" }
    ],
    workstationName: [
      { required: true, message: "工作站名称不能为空", trigger: "blur" }
    ],
    workshopId: [
      { required: true, message: "所属车间ID不能为空", trigger: "blur" }
    ],
    status: [
      { required: true, message: "状态不能为空", trigger: "change" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询工作站列表 */
function getList() {
  loading.value = true
  listWorkstation(queryParams.value).then(response => {
    workstationList.value = response.rows
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
    workstationId: null,
    workstationCode: null,
    workstationName: null,
    workshopId: null,
    workshopCode: null,
    workshopName: null,
    workstationDesc: null,
    equipmentId: null,
    operatorCount: null,
    standardCapacity: null,
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
  proxy.resetForm("workstationRef")
  data.autoGenFlag = false
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
  ids.value = selection.map(item => item.workstationId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加工作站"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false // 设置为编辑模式
  const _workstationId = row.workstationId || ids.value
  getWorkstation(_workstationId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改工作站"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true // 设置为查看模式
  const _workstationId = row.workstationId || ids.value
  getWorkstation(_workstationId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看工作站"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["workstationRef"].validate(valid => {
    if (valid) {
      if (form.value.workstationId != null) {
        updateWorkstation(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addWorkstation(form.value).then(response => {
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
  const _workstationIds = row.workstationId || ids.value
  proxy.$modal.confirm('是否确认删除工作站编号为"' + _workstationIds + '"的数据项？').then(function () {
    return delWorkstation(_workstationIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('bd/workstation/export', {
    ...queryParams.value
  }, `workstation_${new Date().getTime()}.xlsx`)
}

// 添加获取车间列表的方法
function getWorkshopList() {
  workshopLoading.value = true
  listWorkshop(workshopQueryParams).then(response => {
    workshopList.value = response.rows
    workshopTotal.value = response.total
    workshopLoading.value = false
  })
}

// 打开车间选择对话框
function openWorkshopSelect() {
  workshopVisible.value = true
  getWorkshopList()
}

// 选择车间
function selectWorkshop(row) {
  form.value.workshopId = row.workshopId
  form.value.workshopCode = row.workshopCode
  form.value.workshopName = row.workshopName
  workshopVisible.value = false
}

// 重置车间查询
function resetWorkshopQuery() {
  workshopQueryParams.workshopCode = undefined
  workshopQueryParams.workshopName = undefined
  getWorkshopList()
}

//自动生成编码
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('WORKSTATION_CODE').then(response => {
      form.value.workstationCode = response;
    });
  } else {
    form.value.workstationCode = null;
  }
}

getList()
</script>