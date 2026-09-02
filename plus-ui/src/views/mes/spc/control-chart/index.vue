<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="控制图名称" prop="chartName">
            <el-input v-model="queryParams.chartName" placeholder="请输入控制图名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="类型" prop="chartType">
            <el-select v-model="queryParams.chartType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="dict in spc_chart_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_enable_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:spc:chart:add']">新增控制图</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip>
      <el-table-column label="控制图名称" prop="chartName" min-width="120" show-overflow-tooltip />
      <el-table-column label="类型" prop="chartType" align="center">
        <template #default="scope">
          <dict-tag :options="spc_chart_type" :value="scope.row.chartType" />
        </template>
      </el-table-column>
      <el-table-column label="样本大小" prop="sampleSize" align="right" />
      <el-table-column label="UCL" prop="ucl" align="right">
        <template #default="scope">{{ scope.row.ucl ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="CL" prop="cl" align="right">
        <template #default="scope">{{ scope.row.cl ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="LCL" prop="lcl" align="right">
        <template #default="scope">{{ scope.row.lcl ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="Cpk" prop="cpk" align="right">
        <template #default="scope">
          <el-tag v-if="scope.row.cpk != null" size="small" :type="cpkTagType(scope.row.cpkGrade)">
            {{ scope.row.cpk }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="能力等级" prop="cpkGrade" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.cpkGrade" size="small" :type="cpkTagType(scope.row.cpkGrade)">
            {{ cpkGradeLabel(scope.row.cpkGrade) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center">
        <template #default="scope">
          <dict-tag :options="mes_enable_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="warning" icon="DataAnalysis" @click="handleAnalyze(scope.row)" v-hasPermi="['mes:spc:chart:analyze']">分析</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:spc:chart:edit']">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:spc:chart:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改控制图对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="controlChartRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="控制图名称" prop="chartName">
          <el-input v-model="form.chartName" placeholder="控制图名称" clearable />
        </el-form-item>
        <el-form-item label="图表类型" prop="chartType">
          <el-select v-model="form.chartType" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="dict in spc_chart_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联采集点" prop="pointId">
          <el-input v-model="pointCodeDisplay" placeholder="请选择采集点(SPC数据来源)" readonly>
            <template #append>
              <el-button icon="Search" @click="openPointSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="样本大小" prop="sampleSize">
          <el-input-number
            v-model="form.sampleSize"
            :min="2"
            :max="10"
            :precision="0"
            controls-position="right"
            style="width: 100%"
            placeholder="X̄-R/S每组样本数(2~10), 计数型为检验数/单位数"
          />
        </el-form-item>
        <el-form-item label="关联物料" prop="materialId">
          <el-input v-model="materialCodeDisplay" placeholder="可选, 选择物料" readonly clearable @clear="form.materialId = undefined">
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="关联工序" prop="processId">
          <el-input v-model="processCodeDisplay" placeholder="可选, 选择工序" readonly clearable @clear="form.processId = undefined">
            <template #append>
              <el-button icon="Search" @click="openProcessSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="规格上限" prop="usl">
          <el-input-number v-model="form.usl" :precision="4" :controls="false" placeholder="USL(计量型图适用)" style="width: 100%" />
        </el-form-item>
        <el-form-item label="规格下限" prop="lsl">
          <el-input-number v-model="form.lsl" :precision="4" :controls="false" placeholder="LSL" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="状态" style="width: 100%">
            <el-option v-for="dict in mes_enable_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm()">确 定</el-button>
        <el-button @click="cancel()">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 分析结果对话框(ECharts控制图) -->
    <el-dialog
      v-model="analyzeDialog.visible"
      :title="'控制图分析 - ' + analyzeResult.chart?.chartName"
      width="960px"
      append-to-body
      @opened="renderChart"
    >
      <div v-loading="analyzeLoading">
        <el-descriptions :column="4" border size="small" class="mb-2">
          <el-descriptions-item label="类型">
            <dict-tag :options="spc_chart_type" :value="analyzeResult.chart?.chartType" />
          </el-descriptions-item>
          <el-descriptions-item label="样本大小">{{ analyzeResult.chart?.sampleSize }}</el-descriptions-item>
          <el-descriptions-item label="数据点数">{{ analyzeResult.points?.length ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="OOC点数">
            <el-tag type="danger">{{ analyzeResult.oocCount ?? 0 }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="UCL(上控制限)">{{ analyzeResult.ucl }}</el-descriptions-item>
          <el-descriptions-item label="CL(中心线)">{{ analyzeResult.cl }}</el-descriptions-item>
          <el-descriptions-item label="LCL(下控制限)">{{ analyzeResult.lcl }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <dict-tag :options="mes_enable_status" :value="analyzeResult.chart?.status" />
          </el-descriptions-item>
          <el-descriptions-item label="Cp(过程能力)">
            <span v-if="analyzeResult.cp != null">{{ analyzeResult.cp }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="Cpk(修正能力)">
            <el-tag v-if="analyzeResult.cpk != null" :type="cpkTagType(analyzeResult.cpkGrade)">
              {{ analyzeResult.cpk }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="能力等级">
            <el-tag v-if="analyzeResult.cpkGrade" :type="cpkTagType(analyzeResult.cpkGrade)">
              {{ cpkGradeLabel(analyzeResult.cpkGrade) }}
            </el-tag>
            <span v-else>未维护规格限</span>
          </el-descriptions-item>
        </el-descriptions>
        <div ref="chartRef" style="height: 380px" />
      </div>
    </el-dialog>

    <!-- 采集点选择对话框 -->
    <el-dialog v-model="pointDialog.visible" title="选择采集点" width="800px" append-to-body>
      <el-form :model="pointDialog.query" :inline="true">
        <el-form-item label="采集点编码">
          <el-input v-model="pointDialog.query.pointCode" clearable @keyup.enter="queryPoints()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryPoints()">搜索</el-button>
          <el-button icon="Refresh" @click="resetPointQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pointDialog.list" height="400" @row-dblclick="selectPoint">
        <el-table-column label="采集点编码" prop="pointCode" min-width="140" />
        <el-table-column label="采集点名称" prop="pointName" min-width="140" />
        <el-table-column label="类型" prop="pointType" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="dc_collection_point_type" :value="scope.row.pointType" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectPoint(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="pointDialog.total > 0"
        :total="pointDialog.total"
        v-model:page="pointDialog.query.pageNum"
        v-model:limit="pointDialog.query.pageSize"
        @pagination="queryPoints"
      />
    </el-dialog>

    <!-- 工序选择对话框 -->
    <el-dialog v-model="processDialog.visible" title="选择工序" width="800px" append-to-body>
      <el-form :model="processDialog.query" :inline="true">
        <el-form-item label="工序编码">
          <el-input v-model="processDialog.query.processCode" clearable @keyup.enter="queryProcesses()" />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="processDialog.query.processName" clearable @keyup.enter="queryProcesses()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryProcesses()">搜索</el-button>
          <el-button icon="Refresh" @click="resetProcessQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="processDialog.list" height="400" @row-dblclick="selectProcess">
        <el-table-column label="工序编码" prop="processCode" min-width="140" />
        <el-table-column label="工序名称" prop="processName" min-width="160" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectProcess(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="processDialog.total > 0"
        :total="processDialog.total"
        v-model:page="processDialog.query.pageNum"
        v-model:limit="processDialog.query.pageSize"
        @pagination="queryProcesses"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </div>
</template>

<script setup lang="ts" name="MesSpcControlChart">
import * as echarts from 'echarts';
import {
  listControlChart,
  getControlChart,
  addControlChart,
  updateControlChart,
  delControlChart,
  analyzeControlChart
} from '@/api/mes/spc/control-chart';
import type { SpcControlChartVO, SpcControlChartQuery, SpcAnalyzeResult, SpcDataPointVO } from '@/api/mes/spc/control-chart';
import { listCollectionPoint, getCollectionPoint } from '@/api/mes/dc/collection-point';
import { listProcess, getProcess } from '@/api/erp/bd/process';
import { getMaterial } from '@/api/erp/bd/material';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { spc_chart_type, mes_enable_status, dc_collection_point_type } = toRefs<any>(
  proxy?.useDict('spc_chart_type', 'mes_enable_status', 'dc_collection_point_type')
);

const controlChartRef = ref<FormInstance>();
// 选择器展示字段(后端 BO 忽略)
const pointCodeDisplay = ref('');
const materialCodeDisplay = ref('');
const processCodeDisplay = ref('');
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();

const pointDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, pointCode: '' as string }
});
const processDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, processCode: '' as string, processName: '' as string }
});

const queryParams = ref<SpcControlChartQuery>({
  pageNum: 1,
  pageSize: 10,
  chartName: undefined,
  chartType: undefined,
  status: undefined
});

const rules = {
  chartName: [{ required: true, message: '控制图名称不能为空', trigger: 'blur' }],
  chartType: [{ required: true, message: '图表类型不能为空', trigger: 'change' }],
  pointId: [{ required: true, message: '关联采集点不能为空', trigger: 'change' }]
};

const { loading, list, total, open, title, showSearch, form, getList, handleQuery, handleDelete, submitForm, cancel, handleAdd, handleUpdate } =
  useCrud<SpcControlChartVO, SpcControlChartQuery>({
    apis: { list: listControlChart, get: getControlChart, add: addControlChart, update: updateControlChart, del: delControlChart },
    idField: 'chartId',
    entityName: '控制图',
    formRef: controlChartRef,
    queryParams,
    initForm: () => ({
      chartId: undefined,
      chartName: '',
      chartType: 'X_BAR_R',
      pointId: undefined,
      materialId: undefined,
      processId: undefined,
      usl: undefined,
      lsl: undefined,
      sampleSize: 5,
      status: 'ACTIVE',
      remark: ''
    }),
    onReset: () => {
      pointCodeDisplay.value = '';
      materialCodeDisplay.value = '';
      processCodeDisplay.value = '';
    },
    afterLoad: (data: SpcControlChartVO) => {
      // 回填展示字段(VO 不带 pointCode/materialCode/processCode, 按 ID 反查)
      Promise.all([
        data.pointId ? getCollectionPoint(data.pointId).catch(() => null) : Promise.resolve(null),
        data.materialId ? getMaterial(data.materialId).catch(() => null) : Promise.resolve(null),
        data.processId ? getProcess(data.processId).catch(() => null) : Promise.resolve(null)
      ]).then(([pointRes, materialRes, processRes]) => {
        pointCodeDisplay.value = (pointRes as any)?.data?.pointCode || '';
        materialCodeDisplay.value = (materialRes as any)?.data?.materialCode || '';
        processCodeDisplay.value = (processRes as any)?.data?.processCode || '';
      });
    }
  });

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

// ============ 分析(ECharts 控制图绘制) ============
const analyzeDialog = reactive({ visible: false });
const analyzeLoading = ref(false);
const analyzeResult = ref<SpcAnalyzeResult>({} as SpcAnalyzeResult);
const chartRef = ref<HTMLElement>();
let chartInstance: echarts.ECharts | null = null;

// ============ 过程能力等级展示 ============
const cpkGradeLabel = (grade: string) => ({ 'A+': '特优(≥1.67)', A: '充分(≥1.33)', B: '勉强(≥1.0)', C: '不足(<1.0)' }[grade] || grade);
const cpkTagType = (grade?: string) =>
  ({ 'A+': 'success', A: 'success', B: 'warning', C: 'danger' }[grade || ''] as 'success' | 'warning' | 'danger' || 'info');

const handleAnalyze = async (row: SpcControlChartVO) => {  analyzeLoading.value = true;
  analyzeDialog.visible = true;
  try {
    const res = await analyzeControlChart(row.chartId);
    analyzeResult.value = res.data as SpcAnalyzeResult;
  } catch (e) {
    proxy?.$modal.msgError('分析失败: ' + (e as Error).message);
  } finally {
    analyzeLoading.value = false;
  }
};

const renderChart = () => {
  nextTick(() => {
    if (!chartRef.value) return;
    if (chartInstance) {
      chartInstance.dispose();
    }
    chartInstance = echarts.init(chartRef.value);
    const points: SpcDataPointVO[] = analyzeResult.value.points || [];
    const ucl = Number(analyzeResult.value.ucl);
    const cl = Number(analyzeResult.value.cl);
    const lcl = Number(analyzeResult.value.lcl);

    const groups = points.map((p) => `组${p.sampleGroup}`);
    const normalValues = points.map((p) => Number(p.value));
    // OOC点单独scatter系列(非OOC置null)
    const oocValues = points.map((p) => (p.isOoc === '1' ? Number(p.value) : null));
    const oocLabels = points.map((p) => p.oocRule || '');

    const option: echarts.EChartsOption = {
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const idx = params[0].dataIndex;
          const p = points[idx];
          return `组${p.sampleGroup}<br/>值: ${p.value}<br/>OOC: ${p.isOoc === '1' ? '是(' + (p.oocRule || '') + ')' : '否'}`;
        }
      },
      grid: { left: 60, right: 30, top: 40, bottom: 40 },
      xAxis: { type: 'category', data: groups, axisLabel: { rotate: 0 } },
      yAxis: { type: 'value', name: '值' },
      series: [
        {
          name: '数据值',
          type: 'line',
          data: normalValues,
          symbol: 'circle',
          symbolSize: 6,
          itemStyle: { color: '#409EFF' },
          lineStyle: { color: '#409EFF' },
          markLine: {
            symbol: 'none',
            data: [
              { yAxis: ucl, name: 'UCL', lineStyle: { color: '#F56C6C', type: 'dashed' }, label: { formatter: 'UCL=' + ucl, position: 'end' } },
              { yAxis: cl, name: 'CL', lineStyle: { color: '#67C23A' }, label: { formatter: 'CL=' + cl, position: 'end' } },
              { yAxis: lcl, name: 'LCL', lineStyle: { color: '#F56C6C', type: 'dashed' }, label: { formatter: 'LCL=' + lcl, position: 'end' } }
            ]
          }
        },
        {
          name: 'OOC点',
          type: 'scatter',
          data: oocValues,
          symbol: 'circle',
          symbolSize: 12,
          itemStyle: { color: '#F56C6C' },
          label: {
            show: true,
            position: 'top',
            formatter: (params: any) => oocLabels[params.dataIndex] || '',
            color: '#F56C6C',
            fontSize: 10
          }
        }
      ]
    };
    chartInstance.setOption(option);
  });
};

const handleResize = () => chartInstance?.resize();

// ============ 采集点选择 ============
const openPointSelect = () => {
  pointDialog.visible = true;
  queryPoints();
};
const queryPoints = () => {
  listCollectionPoint(pointDialog.query as any).then((res: any) => {
    pointDialog.list = res.rows || [];
    pointDialog.total = res.total || 0;
  });
};
const resetPointQuery = () => {
  pointDialog.query = { pageNum: 1, pageSize: 10, pointCode: '' };
  queryPoints();
};
const selectPoint = (row: any) => {
  form.value.pointId = row.pointId;
  pointCodeDisplay.value = row.pointCode;
  pointDialog.visible = false;
};

// ============ 工序选择 ============
const openProcessSelect = () => {
  processDialog.visible = true;
  queryProcesses();
};
const queryProcesses = () => {
  listProcess(processDialog.query as any).then((res: any) => {
    processDialog.list = res.rows || [];
    processDialog.total = res.total || 0;
  });
};
const resetProcessQuery = () => {
  processDialog.query = { pageNum: 1, pageSize: 10, processCode: '', processName: '' };
  queryProcesses();
};
const selectProcess = (row: any) => {
  form.value.processId = row.processId;
  processCodeDisplay.value = row.processCode;
  processDialog.visible = false;
};

// ============ 物料选择 ============
const openMaterialSelect = () => {
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  form.value.materialId = row.materialId;
  materialCodeDisplay.value = row.materialCode;
};

onMounted(() => {
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  chartInstance?.dispose();
});

getList();
</script>
