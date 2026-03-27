<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="报工单编号" prop="reportNo">
        <el-input
          v-model="queryParams.reportNo"
          placeholder="请输入报工单编号"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="报工类型" prop="reportType">
        <el-select
          v-model="queryParams.reportType"
          placeholder="请选择报工类型"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="dict in prd_report_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="报工状态" prop="reportStatus">
        <el-select
          v-model="queryParams.reportStatus"
          placeholder="请选择报工状态"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="dict in prd_report_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="工单编号" prop="woNo">
        <el-input
          v-model="queryParams.woNo"
          placeholder="请输入工单编号"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="产品物料" prop="materialCode">
        <el-input
          v-model="queryParams.materialCode"
          placeholder="物料编码/名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="工序" prop="processName">
        <el-input
          v-model="queryParams.processName"
          placeholder="请输入工序名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="工作站" prop="workstationName">
        <el-input
          v-model="queryParams.workstationName"
          placeholder="请输入工作站名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="操作工" prop="workerName">
        <el-input
          v-model="queryParams.workerName"
          placeholder="请输入操作工姓名"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="报工日期" prop="reportDate">
        <el-date-picker
          clearable
          v-model="queryParams.reportDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择报工日期"
          style="width: 180px"
        />
      </el-form-item>
      <el-form-item label="质量状态" prop="qualityStatus">
        <el-select
          v-model="queryParams.qualityStatus"
          placeholder="请选择质量状态"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="dict in bd_quality_status"
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

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['prd:report:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['prd:report:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['prd:report:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="View"
          :disabled="single"
          @click="handleView"
          v-hasPermi="['prd:report:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['prd:report:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
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
      <el-table-column label="工单名称" align="center" prop="woName" width="140" show-overflow-tooltip />
      <el-table-column label="产品物料编码" align="center" prop="materialCode" width="130" show-overflow-tooltip />
      <el-table-column label="产品物料名称" align="center" prop="materialName" width="160" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="specification" width="120" show-overflow-tooltip />
      <el-table-column label="工序名称" align="center" prop="processName" width="120" show-overflow-tooltip />
      <el-table-column label="工作站" align="center" prop="workstationName" width="120" show-overflow-tooltip />
      <el-table-column label="设备名称" align="center" prop="equipmentName" width="120" show-overflow-tooltip />
      <el-table-column label="计划数量" align="center" prop="plannedQty" width="90" />
      <el-table-column label="本次报工" align="center" prop="reportedQty" width="90" />
      <el-table-column label="合格数量" align="center" prop="goodQty" width="90" />
      <el-table-column label="报废数量" align="center" prop="scrapQty" width="90" />
      <el-table-column label="不良数量" align="center" prop="defectQty" width="90" />
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
      <el-table-column label="班组" align="center" prop="teamName" width="100" />
      <el-table-column label="来源类型" align="center" prop="sourceType" width="100">
        <template #default="scope">
          <dict-tag :options="prd_report_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="提交人" align="center" prop="submitBy" width="100" />
      <el-table-column label="提交时间" align="center" prop="submitTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.submitTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="审核人" align="center" prop="auditBy" width="100" />
      <el-table-column label="审核时间" align="center" prop="auditTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.auditTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:report:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:report:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改生产报工对话框 -->
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
                <el-option
                  v-for="dict in prd_report_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="报工状态" prop="reportStatus">
              <el-select v-model="form.reportStatus" placeholder="请选择报工状态" style="width: 100%">
                <el-option
                  v-for="dict in prd_report_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
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
            <el-form-item label="工单名称" prop="woName">
              <el-input v-model="form.woName" placeholder="请输入工单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入产品物料编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入产品物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格型号" prop="specification">
              <el-input v-model="form.specification" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单位" prop="unitName">
              <el-input v-model="form.unitName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺流程" prop="routeName">
              <el-input v-model="form.routeName" placeholder="请输入工艺流程名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工序编码" prop="processCode">
              <el-input v-model="form.processCode" placeholder="请输入工序编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称" prop="processName">
              <el-input v-model="form.processName" placeholder="请输入工序名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产任务编号" prop="taskNo">
              <el-input v-model="form.taskNo" placeholder="请输入生产任务编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作站" prop="workstationName">
              <el-input v-model="form.workstationName" placeholder="请输入工作站名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="车间名称" prop="workshopName">
              <el-input v-model="form.workshopName" placeholder="请输入车间名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="equipmentName">
              <el-input v-model="form.equipmentName" placeholder="请输入设备名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产批号" prop="lotNumber">
              <el-input v-model="form.lotNumber" placeholder="请输入生产批号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次编号" prop="batchNo">
              <el-input v-model="form.batchNo" placeholder="请输入批次编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="班次名称" prop="shiftName">
              <el-input v-model="form.shiftName" placeholder="请输入班次名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="过期日期" prop="expireDate">
              <el-date-picker
                clearable
                v-model="form.expireDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择过期日期"
                style="width: 100%"
              />
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
            <el-form-item label="待检数量" prop="uncheckQty">
              <el-input-number v-model="form.uncheckQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="累计报工" prop="cumulativeQty">
              <el-input-number v-model="form.cumulativeQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="剩余数量" prop="remainingQty">
              <el-input-number v-model="form.remainingQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">时间信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="报工日期" prop="reportDate">
              <el-date-picker
                clearable
                v-model="form.reportDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择报工日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker
                clearable
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择开始时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker
                clearable
                v-model="form.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择结束时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">工时信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="人工工时" prop="laborHours">
              <el-input-number v-model="form.laborHours" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="机器工时" prop="machineHours">
              <el-input-number v-model="form.machineHours" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="准备工时" prop="setupHours">
              <el-input-number v-model="form.setupHours" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="等待工时" prop="waitHours">
              <el-input-number v-model="form.waitHours" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="暂停工时" prop="pauseHours">
              <el-input-number v-model="form.pauseHours" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">人员信息</el-divider>
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
        <el-divider content-position="left">质量信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="质量状态" prop="qualityStatus">
              <el-select v-model="form.qualityStatus" placeholder="请选择质量状态" style="width: 100%">
                <el-option
                  v-for="dict in bd_quality_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质检单号" prop="qualityCheckNo">
              <el-input v-model="form.qualityCheckNo" placeholder="请输入质检单号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="检验员" prop="inspectorName">
              <el-input v-model="form.inspectorName" placeholder="请输入检验员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检验时间" prop="inspectTime">
              <el-date-picker
                clearable
                v-model="form.inspectTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择检验时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">来源信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型" style="width: 100%">
                <el-option
                  v-for="dict in prd_report_source_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源单据" prop="sourceNo">
              <el-input v-model="form.sourceNo" placeholder="请输入来源单据编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="条码信息" prop="barcode">
              <el-input v-model="form.barcode" placeholder="请输入条码信息" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报工途径" prop="reportChannel">
              <el-input v-model="form.reportChannel" placeholder="请输入报工途径" />
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

<script setup name="Report">
import { listReport, getReport, delReport, addReport, updateReport } from "@/api/core/prd/report"

const { proxy } = getCurrentInstance()
const { prd_report_type, prd_report_status, bd_quality_status, prd_report_source_type } = proxy.useDict('prd_report_type', 'prd_report_status', 'bd_quality_status', 'prd_report_source_type')

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
    woName: null,
    materialCode: null,
    materialName: null,
    routeCode: null,
    routeName: null,
    processCode: null,
    processName: null,
    taskNo: null,
    workstationName: null,
    workshopName: null,
    equipmentName: null,
    lotNumber: null,
    batchNo: null,
    shiftName: null,
    reportDate: null,
    workerName: null,
    teamName: null,
    qualityStatus: null,
    qualityCheckNo: null,
    inspectorName: null,
    sourceType: null,
    sourceNo: null,
    submitBy: null,
    auditBy: null,
  },
  rules: {
    reportNo: [
      { required: true, message: "报工单编号不能为空", trigger: "blur" }
    ],
    woNo: [
      { required: true, message: "工单编号不能为空", trigger: "blur" }
    ],
    materialCode: [
      { required: true, message: "产品物料编码不能为空", trigger: "blur" }
    ],
    reportedQty: [
      { required: true, message: "本次报工数量不能为空", trigger: "blur" }
    ],
    goodQty: [
      { required: true, message: "合格数量不能为空", trigger: "blur" }
    ],
    reportDate: [
      { required: true, message: "报工日期不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询生产报工列表 */
function getList() {
  loading.value = true
  listReport(queryParams.value).then(response => {
    reportList.value = response.rows
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
    reportId: null,
    reportNo: null,
    reportType: null,
    reportStatus: null,
    woId: null,
    woNo: null,
    woName: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    specification: null,
    unitId: null,
    unitCode: null,
    unitName: null,
    routeId: null,
    routeCode: null,
    routeName: null,
    processId: null,
    processCode: null,
    processName: null,
    operationStep: null,
    operationSeq: null,
    taskId: null,
    taskNo: null,
    workstationId: null,
    workstationCode: null,
    workstationName: null,
    workshopId: null,
    workshopName: null,
    equipmentId: null,
    equipmentCode: null,
    equipmentName: null,
    lotNumber: null,
    batchNo: null,
    expireDate: null,
    shiftId: null,
    shiftName: null,
    plannedQty: null,
    reportedQty: null,
    goodQty: null,
    scrapQty: null,
    defectQty: null,
    laborScrapQty: null,
    materialScrapQty: null,
    otherScrapQty: null,
    uncheckQty: null,
    cumulativeQty: null,
    remainingQty: null,
    reportDate: null,
    startTime: null,
    endTime: null,
    laborHours: null,
    machineHours: null,
    setupHours: null,
    waitHours: null,
    pauseHours: null,
    workerId: null,
    workerCode: null,
    workerName: null,
    teamId: null,
    teamCode: null,
    teamName: null,
    helperIds: null,
    helperNames: null,
    qualityStatus: null,
    qualityCheckId: null,
    qualityCheckNo: null,
    inspectorId: null,
    inspectorName: null,
    inspectTime: null,
    reportChannel: null,
    sourceType: null,
    sourceId: null,
    sourceNo: null,
    barcode: null,
    submitBy: null,
    submitTime: null,
    auditBy: null,
    auditTime: null,
    auditRemark: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    deleted: null
  }
  proxy.resetForm("reportRef")
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
  ids.value = selection.map(item => item.reportId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加生产报工"
}

/** 修改按钮操作 */
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

/** 查看按钮操作 */
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

/** 提交按钮 */
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

/** 删除按钮操作 */
function handleDelete(row) {
  const _reportIds = row.reportId || ids.value
  proxy.$modal.confirm('是否确认删除生产报工编号为"' + _reportIds + '"的数据项？').then(function () {
    return delReport(_reportIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('prd/report/export', {
    ...queryParams.value
  }, `report_${new Date().getTime()}.xlsx`)
}

getList()
</script>
