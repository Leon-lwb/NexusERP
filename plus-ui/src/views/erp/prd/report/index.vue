<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ============ 报工单列表（FE-ERP-02：此前菜单 2304 指向纯图表页，报工 CRUD 完全不可达） ============ -->
      <el-tab-pane label="报工单" name="list">
        <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="报工单编号" prop="reportNo">
                <el-input v-model="queryParams.reportNo" placeholder="请输入报工单编号" clearable @keyup.enter="handleQuery()" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="工单编号" prop="woNo">
                <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="handleQuery()" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="产品名称" prop="materialName">
                <el-input v-model="queryParams.materialName" placeholder="请输入产品名称" clearable @keyup.enter="handleQuery()" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="操作工" prop="workerName">
                <el-input v-model="queryParams.workerName" placeholder="请输入操作工" clearable @keyup.enter="handleQuery()" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="报工状态" prop="reportStatus">
                <el-select v-model="queryParams.reportStatus" placeholder="请选择报工状态" clearable style="width: 100%">
                  <el-option v-for="dict in prd_work_report_status" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="报工途径" prop="reportChannel">
                <el-select v-model="queryParams.reportChannel" placeholder="请选择报工途径" clearable style="width: 100%">
                  <el-option v-for="dict in prd_work_report_channel" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6" :offset="6" class="search-actions">
              <el-form-item>
                <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
                <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['prd:report:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['prd:report:remove']">
              删除
            </el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['prd:report:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="reportList" show-overflow-tooltip @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="序号" type="index" align="center" />
          <el-table-column label="报工单编号" align="center" prop="reportNo" />
          <el-table-column label="工单编号" align="center" prop="woNo" />
          <el-table-column label="工单名称" align="center" prop="woName" min-width="120" />
          <el-table-column label="产品编码" align="center" prop="materialCode" />
          <el-table-column label="产品名称" align="center" prop="materialName" min-width="120" />
          <el-table-column label="规格型号" align="center" prop="specification" min-width="120" />
          <el-table-column label="工序名称" align="center" prop="processName" />
          <el-table-column label="工序序号" align="center" prop="operationSeq" />
          <el-table-column label="任务编号" align="center" prop="taskNo" />
          <el-table-column label="生产批号" align="center" prop="lotNumber" />
          <el-table-column label="批次编号" align="center" prop="batchNo" />
          <el-table-column label="计划数量" align="right" prop="plannedQty" />
          <el-table-column label="报工数量" align="right" prop="reportedQty" />
          <el-table-column label="合格数量" align="right" prop="goodQty" />
          <el-table-column label="工废数量" align="right" prop="laborScrapQty" />
          <el-table-column label="料废数量" align="right" prop="materialScrapQty" />
          <el-table-column label="其他废" align="right" prop="otherScrapQty" />
          <el-table-column label="待检数量" align="right" prop="uncheckQty" />
          <el-table-column label="累计报工" align="right" prop="cumulativeQty" />
          <el-table-column label="报工日期" align="center" prop="reportDate">
            <template #default="scope">
              <span>{{ parseTime(scope.row.reportDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="人工工时" align="right" prop="laborHours" />
          <el-table-column label="机器工时" align="right" prop="machineHours" />
          <el-table-column label="报工途径" align="center" prop="reportChannel">
            <template #default="scope">
              <dict-tag :options="prd_work_report_channel" :value="scope.row.reportChannel" />
            </template>
          </el-table-column>
          <el-table-column label="质检单号" align="center" prop="qualityCheckNo" min-width="120" />
          <el-table-column label="报工状态" align="center" prop="reportStatus">
            <template #default="scope">
              <dict-tag :options="prd_work_report_status" :value="scope.row.reportStatus" />
            </template>
          </el-table-column>
          <el-table-column label="提交人" align="center" prop="submitBy" />
          <el-table-column label="审核人" align="center" prop="auditBy" />
          <el-table-column label="备注" align="center" prop="remark" min-width="120" />
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['prd:report:query']">查看</el-button>
              <el-button
                v-if="scope.row.reportStatus !== 'APPROVED'"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
                v-hasPermi="['prd:report:edit']"
                >修改</el-button
              >
              <el-button
                v-if="scope.row.reportStatus === 'DRAFT' || !scope.row.reportStatus"
                link
                type="success"
                icon="Promotion"
                @click="handleSubmit(scope.row)"
                v-hasPermi="['prd:report:edit']"
                >提交</el-button
              >
              <el-button
                v-if="scope.row.reportStatus === 'SUBMITTED'"
                link
                type="warning"
                icon="Check"
                @click="handleAudit(scope.row)"
                v-hasPermi="['prd:report:edit']"
                >审核</el-button
              >
              <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:report:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
      </el-tab-pane>

      <!-- ============ 统计图表（原页面内容，补 dispose / 监听解绑 / loading / 空态） ============ -->
      <el-tab-pane label="统计图表" name="chart">
        <el-card shadow="never" class="mb-2">
          <el-form :inline="true">
            <el-form-item label="月数">
              <el-input-number v-model="queryMonths" :min="1" :max="24" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" :loading="chartLoading" @click="loadChartData">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-row :gutter="20" v-if="activeTab === 'chart'">
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="completionChartRef" v-loading="chartLoading" style="height: 400px"></div>
              <el-empty v-if="!chartLoading && !completionData.length" description="暂无完工率数据" :image-size="60" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <div ref="efficiencyChartRef" v-loading="chartLoading" style="height: 400px"></div>
              <el-empty v-if="!chartLoading && !efficiencyData.length" description="暂无生产效率数据" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 报工单表单 -->
    <el-dialog :title="title" v-model="open" width="960px" top="5vh" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <div style="max-height: 65vh; overflow-y: auto">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="报工单编号" prop="reportNo">
                <el-input v-model="form.reportNo" placeholder="留空由后端生成" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="报工类型" prop="reportType">
                <el-input v-model="form.reportType" placeholder="如 NORMAL/REWORK" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <!-- ERP-PRD-02：新增报工由服务端强制 SUBMITTED（客户端 status 被忽略），改为只读展示；
                   状态流转请用操作列的「提交 / 审核」按钮（后端 CAS，重复操作返回 0 不报错） -->
              <el-form-item label="报工状态" prop="reportStatus">
                <el-select v-model="form.reportStatus" disabled placeholder="新增后由服务端置为已提交" style="width: 100%">
                  <el-option v-for="dict in prd_work_report_status" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="报工途径" prop="reportChannel">
                <el-select v-model="form.reportChannel" placeholder="请选择报工途径" clearable style="width: 100%">
                  <el-option v-for="dict in prd_work_report_channel" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="报工日期" prop="reportDate">
                <el-date-picker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工单编号" prop="woNo">
                <el-input v-model="form.woNo" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工单名称" prop="woName">
                <el-input v-model="form.woName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工单ID" prop="woId">
                <el-input v-model="form.woId" placeholder="关联 prd_work_order.wo_id" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="产品物料ID" prop="materialId">
                <el-input v-model="form.materialId" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">工序 / 任务</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="产品编码" prop="materialCode">
                <el-input v-model="form.materialCode" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="产品名称" prop="materialName">
                <el-input v-model="form.materialName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="规格型号" prop="specification">
                <el-input v-model="form.specification" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="单位编码" prop="unitCode">
                <el-input v-model="form.unitCode" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工艺流程编号" prop="routeCode">
                <el-input v-model="form.routeCode" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工序编码" prop="processCode">
                <el-input v-model="form.processCode" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工序名称" prop="processName">
                <el-input v-model="form.processName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工序步骤序号" prop="operationStep">
                <el-input v-model="form.operationStep" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="报工次数" prop="operationSeq">
                <el-input v-model="form.operationSeq" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="生产任务编号" prop="taskNo">
                <el-input v-model="form.taskNo" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工作站" prop="workstationName">
                <el-input v-model="form.workstationName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="车间" prop="workshopName">
                <el-input v-model="form.workshopName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="设备" prop="equipmentName">
                <el-input v-model="form.equipmentName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="生产批号" prop="lotNumber">
                <el-input v-model="form.lotNumber" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="批次编号" prop="batchNo">
                <el-input v-model="form.batchNo" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="班次" prop="shiftName">
                <el-input v-model="form.shiftName" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">数量</el-divider>
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="计划数量" prop="plannedQty">
                <el-input-number v-model="form.plannedQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="本次报工数量" prop="reportedQty">
                <el-input-number v-model="form.reportedQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="合格数量" prop="goodQty">
                <el-input-number v-model="form.goodQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="报废数量" prop="scrapQty">
                <el-input-number v-model="form.scrapQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="工废数量" prop="laborScrapQty">
                <el-input-number v-model="form.laborScrapQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="料废数量" prop="materialScrapQty">
                <el-input-number v-model="form.materialScrapQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="其他报废" prop="otherScrapQty">
                <el-input-number v-model="form.otherScrapQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="待检数量" prop="uncheckQty">
                <el-input-number v-model="form.uncheckQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">工时 / 人员</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="开始时间" prop="startTime">
                <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="结束时间" prop="endTime">
                <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="人工工时" prop="laborHours">
                <el-input-number v-model="form.laborHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="机器工时" prop="machineHours">
                <el-input-number v-model="form.machineHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="准备工时" prop="setupHours">
                <el-input-number v-model="form.setupHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="等待工时" prop="waitHours">
                <el-input-number v-model="form.waitHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="暂停工时" prop="pauseHours">
                <el-input-number v-model="form.pauseHours" :min="0" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="操作工" prop="workerName">
                <el-input v-model="form.workerName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="班组" prop="teamName">
                <el-input v-model="form.teamName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="辅助人员" prop="helperNames">
                <el-input v-model="form.helperNames" placeholder="多个用逗号分隔" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">质量 / 审核</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="质量状态" prop="qualityStatus">
                <el-select v-model="form.qualityStatus" placeholder="请选择质量状态" clearable style="width: 100%">
                  <el-option v-for="dict in bd_quality_status" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="质检单号" prop="qualityCheckNo">
                <el-input v-model="form.qualityCheckNo" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="检验员" prop="inspectorName">
                <el-input v-model="form.inspectorName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="提交人" prop="submitBy">
                <el-input v-model="form.submitBy" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="提交时间" prop="submitTime">
                <el-date-picker v-model="form.submitTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="审核人" prop="auditBy">
                <el-input v-model="form.auditBy" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="审核时间" prop="auditTime">
                <el-date-picker v-model="form.auditTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="审核意见" prop="auditRemark">
                <el-input v-model="form.auditRemark" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PrdWorkReport" lang="ts">
import { ref, nextTick, onMounted, onBeforeUnmount, watch } from 'vue';
import * as echarts from 'echarts';
import type { FormInstance } from 'element-plus';
import { ElMessageBox } from 'element-plus';
import {
  listWorkReport,
  getWorkReport,
  addWorkReport,
  updateWorkReport,
  delWorkReport,
  getWorkOrderCompletionRate,
  getProductionEfficiency
} from '@/api/erp/prd/report';
import type { WorkReportVO } from '@/api/erp/prd/report/types';
import { useCrud } from '@/composables/useCrud';
import { useUserStore } from '@/store/modules/user';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const userStore = useUserStore();
const { prd_work_report_status, prd_work_report_channel, bd_quality_status } = toRefs<any>(
  proxy.useDict('prd_work_report_status', 'prd_work_report_channel', 'bd_quality_status')
);

const activeTab = ref('list');
const formRef = ref<FormInstance>();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  reportNo: null,
  woNo: null,
  materialName: null,
  workerName: null,
  reportStatus: null,
  reportChannel: null
});

const rules = {
  reportStatus: [{ required: true, message: '报工状态不能为空', trigger: 'change' }],
  reportedQty: [{ required: true, message: '本次报工数量不能为空', trigger: 'blur' }],
  reportDate: [{ required: true, message: '报工日期不能为空', trigger: 'change' }]
};

const {
  loading,
  list: reportList,
  total,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  submitForm,
  cancel,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud<WorkReportVO, any>({
  apis: { list: listWorkReport, get: getWorkReport, add: addWorkReport, update: updateWorkReport, del: delWorkReport },
  idField: 'reportId',
  entityName: '生产报工',
  formRef,
  queryParams,
  initForm: () => ({
    reportStatus: 'DRAFT',
    reportChannel: 'PC',
    reportDate: new Date().toISOString().slice(0, 10),
    plannedQty: 0,
    reportedQty: 0,
    goodQty: 0,
    scrapQty: 0,
    laborScrapQty: 0,
    materialScrapQty: 0,
    otherScrapQty: 0,
    uncheckQty: 0,
    laborHours: 0,
    machineHours: 0,
    setupHours: 0,
    waitHours: 0,
    pauseHours: 0
  }),
  exportUrl: '/erp/prd/report/export',
  exportFileName: 'prd_work_report'
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 查看（复用表单弹窗，只读） */
function handleView(row: WorkReportVO) {
  handleUpdate(row);
}

/**
 * 提交报工（DRAFT → SUBMITTED）。
 * <p>后端 PrdWorkReportController 未提供独立的 submit 端点，状态流转通过 PUT /erp/prd/report（edit 权限）完成，
 * 提交人/提交时间由前端按当前登录用户补齐（后端 updateByBo 不自动填充）。</p>
 */
function handleSubmit(row: WorkReportVO) {
  proxy.$modal
    .confirm(`是否确认提交报工单【${row.reportNo || row.reportId}】?`)
    .then(() =>
      updateWorkReport({
        reportId: row.reportId,
        reportStatus: 'SUBMITTED',
        submitBy: (userStore as any).nickname || (userStore as any).name || '',
        submitTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
      } as any)
    )
    .then(() => {
      proxy.$modal.msgSuccess('提交成功');
      getList();
    })
    .catch(() => {});
}

/**
 * 审核通过（SUBMITTED → APPROVED）。
 * <p>后端 updateByBo 在状态变为 APPROVED 时会原子回写工单累计完工数量与报废数量
 * （PrdWorkReportServiceImpl.syncWorkOrderQuantity），因此这里必须先确认再改状态。</p>
 */
function handleAudit(row: WorkReportVO) {
  ElMessageBox.prompt('请输入审核意见', '报工审核', { inputPlaceholder: '可留空', inputValue: '' })
    .then(({ value }: any) =>
      updateWorkReport({
        reportId: row.reportId,
        reportStatus: 'APPROVED',
        auditBy: (userStore as any).nickname || (userStore as any).name || '',
        auditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
        auditRemark: value || ''
      } as any)
    )
    .then(() => {
      proxy.$modal.msgSuccess('审核通过，工单累计产量已回写');
      getList();
    })
    .catch(() => {});
}

// ==================== 统计图表（FE-ERP-07：实例复用 + 单例防抖监听 + 卸载 dispose） ====================
const queryMonths = ref(6);
const chartLoading = ref(false);
const completionData = ref<any[]>([]);
const efficiencyData = ref<any[]>([]);
const completionChartRef = ref<HTMLElement>();
const efficiencyChartRef = ref<HTMLElement>();

let completionChart: echarts.ECharts | null = null;
let efficiencyChart: echarts.ECharts | null = null;
let resizeTimer: ReturnType<typeof setTimeout> | null = null;

/** 复用同一 DOM 上已有实例，避免重复 init 造成实例泄漏 */
function ensureChart(el: HTMLElement | undefined, current: echarts.ECharts | null): echarts.ECharts | null {
  if (!el) return current;
  return current ?? echarts.getInstanceByDom(el) ?? echarts.init(el);
}

/** 两个图表共用一个防抖 resize，只注册一次 */
function handleResize() {
  if (resizeTimer) clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    completionChart?.resize();
    efficiencyChart?.resize();
  }, 200);
}

async function loadChartData() {
  chartLoading.value = true;
  try {
    const [compRes, effRes] = await Promise.all([
      getWorkOrderCompletionRate({ months: queryMonths.value }),
      getProductionEfficiency({ months: queryMonths.value })
    ]);
    completionData.value = compRes.data || [];
    efficiencyData.value = effRes.data || [];
    await nextTick();
    renderCompletionChart();
    renderEfficiencyChart();
  } catch {
    completionData.value = [];
    efficiencyData.value = [];
  } finally {
    chartLoading.value = false;
  }
}

function renderCompletionChart() {
  completionChart = ensureChart(completionChartRef.value, completionChart);
  if (!completionChart) return;
  const data = completionData.value;
  completionChart.setOption(
    {
      title: { text: '工单完工率' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['总工单', '已完工', '完工率(%)'] },
      xAxis: { type: 'category', data: data.map((d) => d.month) },
      yAxis: [
        { type: 'value', name: '工单数' },
        { type: 'value', name: '完工率(%)', max: 100 }
      ],
      series: [
        { name: '总工单', type: 'bar', data: data.map((d) => d.totalOrders ?? 0) },
        { name: '已完工', type: 'bar', data: data.map((d) => d.completedOrders ?? 0) },
        { name: '完工率(%)', type: 'line', yAxisIndex: 1, data: data.map((d) => d.completionRate ?? 0) }
      ]
    },
    { notMerge: true }
  );
}

function renderEfficiencyChart() {
  efficiencyChart = ensureChart(efficiencyChartRef.value, efficiencyChart);
  if (!efficiencyChart) return;
  const data = efficiencyData.value;
  efficiencyChart.setOption(
    {
      title: { text: '计划 vs 实际完工' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['计划数量', '实际数量', '效率(%)'] },
      xAxis: { type: 'category', data: data.map((d) => d.month) },
      yAxis: [
        { type: 'value', name: '数量' },
        { type: 'value', name: '效率(%)', max: 150 }
      ],
      series: [
        { name: '计划数量', type: 'bar', data: data.map((d) => d.plannedQty ?? 0) },
        { name: '实际数量', type: 'bar', data: data.map((d) => d.completedQty ?? 0) },
        { name: '效率(%)', type: 'line', yAxisIndex: 1, data: data.map((d) => d.efficiencyRate ?? 0) }
      ]
    },
    { notMerge: true }
  );
}

/** 图表 Tab 首次进入时才初始化（隐藏容器宽度为 0，init 会得到 0 宽图表） */
watch(activeTab, (tab) => {
  if (tab === 'chart') {
    nextTick(() => {
      renderCompletionChart();
      renderEfficiencyChart();
    });
  }
});

onMounted(() => {
  window.addEventListener('resize', handleResize);
  getList();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  if (resizeTimer) {
    clearTimeout(resizeTimer);
    resizeTimer = null;
  }
  completionChart?.dispose();
  efficiencyChart?.dispose();
  completionChart = null;
  efficiencyChart = null;
});
</script>

<style scoped>
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
