<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="报工单编号" prop="reportNo">
        <el-input v-model="queryParams.reportNo" placeholder="请输入报工单编号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="报工类型" prop="reportType">
        <el-select v-model="queryParams.reportType" placeholder="请选择报工类型" clearable style="width: 180px">
          <el-option v-for="dict in prd_report_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="报工状态" prop="reportStatus">
        <el-select v-model="queryParams.reportStatus" placeholder="请选择报工状态" clearable style="width: 180px">
          <el-option v-for="dict in prd_report_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="工单编号" prop="woNo">
        <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品物料" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="物料编码/名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="工序" prop="processName">
        <el-input v-model="queryParams.processName" placeholder="请输入工序名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="工作站" prop="workstationName">
        <el-input v-model="queryParams.workstationName" placeholder="请输入工作站名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="操作工" prop="workerName">
        <el-input v-model="queryParams.workerName" placeholder="请输入操作工姓名" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="报工日期" prop="reportDate">
        <el-date-picker clearable v-model="queryParams.reportDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择报工日期" style="width: 180px" />
      </el-form-item>
      <el-form-item label="质量状态" prop="qualityStatus">
        <el-select v-model="queryParams.qualityStatus" placeholder="请选择质量状态" clearable style="width: 180px">
          <el-option v-for="dict in bd_quality_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:report:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['prd:report:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['prd:report:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['prd:report:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['prd:report:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="reportList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="报工单编号" align="center" prop="reportNo" width="140" show-overflow-tooltip />
      <el-table-column label="报工类型" align="center" prop="reportType" width="100">
        <template #default="scope">
          <dict-tag :options="prd_report_type" :value="scope.row.reportType" />
        </template>
      </el-table-column>
      <el-table-column label="报工状态" align="center" prop="reportStatus" width="100">
        <template #default="scope">
          <dict-tag :options="prd_report_status" :value="scope.row.reportStatus" />
        </template>
      </el-table-column>
      <el-table-column label="工单编号" align="center" prop="woNo" width="130" show-overflow-tooltip />
      <el-table-column label="产品物料编码" align="center" prop="materialCode" width="130" show-overflow-tooltip />
      <el-table-column label="产品物料名称" align="center" prop="materialName" width="160" show-overflow-tooltip />
      <el-table-column label="计划数量" align="center" prop="plannedQty" width="90" />
      <el-table-column label="本次报工" align="center" prop="reportedQty" width="90" />
      <el-table-column label="合格数量" align="center" prop="goodQty" width="90" />
      <el-table-column label="报废数量" align="center" prop="scrapQty" width="90" />
      <el-table-column label="质量状态" align="center" prop="qualityStatus" width="90">
        <template #default="scope">
          <dict-tag :options="bd_quality_status" :value="scope.row.qualityStatus" />
        </template>
      </el-table-column>
      <el-table-column label="报工日期" align="center" prop="reportDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.reportDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作工" align="center" prop="workerName" width="100" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:report:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:report:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="900px" append-to-body>
      <el-form ref="reportRef" :model="form" :rules="rules" label-width="110px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="报工单编号" prop="reportNo">
              <el-input v-model="form.reportNo" placeholder="请输入报工单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报工类型" prop="reportType">
              <el-select v-model="form.reportType" placeholder="请选择报工类型" style="width: 100%">
                <el-option v-for="dict in prd_report_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="报工状态" prop="reportStatus">
              <el-select v-model="form.reportStatus" placeholder="请选择报工状态" style="width: 100%">
                <el-option v-for="dict in prd_report_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工单编号" prop="woNo">
              <el-input v-model="form.woNo" placeholder="请输入工单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入产品物料编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入产品物料名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">数量信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input-number v-model="form.plannedQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="本次报工" prop="reportedQty">
              <el-input-number v-model="form.reportedQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="合格数量" prop="goodQty">
              <el-input-number v-model="form.goodQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="报废数量" prop="scrapQty">
              <el-input-number v-model="form.scrapQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="不良数量" prop="defectQty">
              <el-input-number v-model="form.defectQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="报工日期" prop="reportDate">
              <el-date-picker clearable v-model="form.reportDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择报工日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="操作工" prop="workerName">
              <el-input v-model="form.workerName" placeholder="请输入操作工姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班组" prop="teamName">
              <el-input v-model="form.teamName" placeholder="请输入班组名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PrdReport">
import { listReport, getReport, delReport, addReport, updateReport } from "@/api/erp/prd/report"

const { proxy } = getCurrentInstance()
const { prd_report_type, prd_report_status, bd_quality_status, prd_report_source_type } = toRefs<any>(proxy.useDict('prd_report_type', 'prd_report_status', 'bd_quality_status', 'prd_report_source_type'))

const reportList = ref([])
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
    reportNo: null,
    reportType: null,
    reportStatus: null,
    woNo: null,
    materialCode: null,
    materialName: null,
    processName: null,
    workstationName: null,
    workerName: null,
    reportDate: null,
    qualityStatus: null,
  },
  rules: {
    reportNo: [{ required: true, message: "报工单编号不能为空", trigger: "blur" }],
    woNo: [{ required: true, message: "工单编号不能为空", trigger: "blur" }],
    materialCode: [{ required: true, message: "产品物料编码不能为空", trigger: "blur" }],
    reportedQty: [{ required: true, message: "本次报工数量不能为空", trigger: "blur" }],
    goodQty: [{ required: true, message: "合格数量不能为空", trigger: "blur" }],
    reportDate: [{ required: true, message: "报工日期不能为空", trigger: "blur" }],
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listReport(queryParams.value).then(response => {
    reportList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    reportId: null,
    reportNo: null,
    reportType: null,
    reportStatus: null,
    woNo: null,
    materialCode: null,
    materialName: null,
    plannedQty: null,
    reportedQty: null,
    goodQty: null,
    scrapQty: null,
    defectQty: null,
    reportDate: null,
    workerName: null,
    teamName: null,
    remark: null,
  }
  proxy.resetForm("reportRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.reportId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加生产报工"
}

function handleUpdate(row) {
  reset()
  isViewMode.value = false
  const _reportId = row.reportId || ids.value
  getReport(_reportId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改生产报工"
  })
}

function handleView(row) {
  reset()
  isViewMode.value = true
  const _reportId = row.reportId || ids.value
  getReport(_reportId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看生产报工"
  })
}

function submitForm() {
  proxy.$refs["reportRef"].validate(valid => {
    if (valid) {
      if (form.value.reportId != null) {
        updateReport(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addReport(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  const _reportIds = row.reportId || ids.value
  proxy.$modal.confirm('是否确认删除生产报工编号为"' + _reportIds + '"的数据项？').then(function () {
    return delReport(_reportIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('erp/prd/report/export', {
    ...queryParams.value
  }, `report_${new Date().getTime()}.xlsx`)
}

getList()
</script>
