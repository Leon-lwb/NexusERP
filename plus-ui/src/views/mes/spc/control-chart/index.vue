<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="控制图名称" prop="chartName">
              <el-input v-model="queryParams.chartName" placeholder="请输入控制图名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="类型" prop="chartType">
              <el-select v-model="queryParams.chartType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option label="均值-极差(X̄-R)" value="X_BAR_R" />
                <el-option label="均值-标准差(X̄-S)" value="X_BAR_S" />
                <el-option label="不良率(P)" value="P" />
                <el-option label="缺陷数(C)" value="C" />
                <el-option label="单位缺陷数(U)" value="U" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:spc:chart:add']">新增控制图</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="控制图名称" prop="chartName" min-width="120" show-overflow-tooltip />
        <!-- 技术债:类型暂无对应字典,使用硬编码 el-tag,待补字典后改 dict-tag -->
        <el-table-column label="类型" prop="chartType" align="center">
          <template #default="scope">
            <el-tag type="primary">{{ typeLabel(scope.row.chartType) }}</el-tag>
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
        <!-- 技术债:状态暂无对应字典,使用硬编码 el-tag,待补字典后改 dict-tag -->
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="warning" icon="DataAnalysis" @click="handleAnalyze(scope.row)" v-hasPermi="['mes:spc:chart:analyze']"
              >分析</el-button
            >
            <el-button link type="primary" icon="Edit" @click="handleOpenEdit(scope.row)" v-hasPermi="['mes:spc:chart:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:spc:chart:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑控制图对话框 -->
    <el-dialog v-model="dialog.visible" :title="form.chartId ? '编辑控制图' : '新增控制图'" width="560px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="控制图名称" required>
          <el-input v-model="form.chartName" placeholder="控制图名称" clearable />
        </el-form-item>
        <el-form-item label="图表类型" required>
          <el-select v-model="form.chartType" placeholder="请选择类型" style="width: 100%">
            <el-option label="均值-极差(X̄-R)" value="X_BAR_R" />
            <el-option label="均值-标准差(X̄-S)" value="X_BAR_S" />
            <el-option label="不良率(P)" value="P" />
            <el-option label="缺陷数(C)" value="C" />
            <el-option label="单位缺陷数(U)" value="U" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联采集点ID" required>
          <el-input v-model.number="form.pointId" placeholder="采集点ID(SPC数据来源)" clearable />
        </el-form-item>
        <el-form-item label="样本大小">
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
        <el-form-item label="关联物料ID">
          <el-input v-model.number="form.materialId" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="关联工序ID">
          <el-input v-model.number="form.processId" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="状态" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>

    <!-- 分析结果对话框(ECharts控制图) -->
    <el-dialog
      v-model="analyzeDialog.visible"
      :title="'控制图分析 - ' + analyzeResult.chart?.chartName"
      width="900px"
      append-to-body
      @opened="renderChart"
    >
      <div v-loading="analyzeLoading">
        <el-descriptions :column="4" border size="small" class="mb-2">
          <el-descriptions-item label="类型">{{ typeLabel(analyzeResult.chart?.chartType) }}</el-descriptions-item>
          <el-descriptions-item label="样本大小">{{ analyzeResult.chart?.sampleSize }}</el-descriptions-item>
          <el-descriptions-item label="数据点数">{{ analyzeResult.points?.length ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="OOC点数">
            <el-tag type="danger">{{ analyzeResult.oocCount ?? 0 }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="UCL(上控制限)">{{ analyzeResult.ucl }}</el-descriptions-item>
          <el-descriptions-item label="CL(中心线)">{{ analyzeResult.cl }}</el-descriptions-item>
          <el-descriptions-item label="LCL(下控制限)">{{ analyzeResult.lcl }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ analyzeResult.chart?.status === 'ACTIVE' ? '启用' : '停用' }}</el-descriptions-item>
        </el-descriptions>
        <div ref="chartRef" style="height: 380px" />
      </div>
    </el-dialog>
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
import type { SpcControlChartVO, SpcControlChartQuery, SpcControlChartForm, SpcAnalyzeResult, SpcDataPointVO } from '@/api/mes/spc/control-chart';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<SpcControlChartVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<SpcControlChartQuery>({
  pageNum: 1,
  pageSize: 10,
  chartName: undefined,
  chartType: undefined,
  status: undefined
});

const dialog = reactive({ visible: false });
const form = ref<SpcControlChartForm>({} as SpcControlChartForm);

const analyzeDialog = reactive({ visible: false });
const analyzeLoading = ref(false);
const analyzeResult = ref<SpcAnalyzeResult>({} as SpcAnalyzeResult);
const chartRef = ref<HTMLElement>();
let chartInstance: echarts.ECharts | null = null;

const getList = async () => {
  loading.value = true;
  try {
    const res = await listControlChart(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

const resetForm = () => {
  form.value = {
    chartId: undefined,
    chartName: '',
    chartType: 'X_BAR_R',
    pointId: undefined,
    materialId: undefined,
    processId: undefined,
    sampleSize: 5,
    status: 'ACTIVE',
    remark: ''
  };
};

const handleOpenAdd = () => {
  resetForm();
  dialog.visible = true;
};

const handleOpenEdit = async (row: SpcControlChartVO) => {
  const res = await getControlChart(row.chartId);
  form.value = { ...res.data };
  dialog.visible = true;
};

const submitForm = async () => {
  if (!form.value.chartName || !form.value.chartType || !form.value.pointId) {
    proxy?.$modal.msgWarning('控制图名称/类型/采集点不能为空');
    return;
  }
  if (form.value.chartId) {
    await updateControlChart(form.value);
    proxy?.$modal.msgSuccess('编辑成功');
  } else {
    await addControlChart(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  getList();
};

const handleDelete = async (row: SpcControlChartVO) => {
  await proxy?.$modal.confirm('确认删除控制图"' + row.chartName + '"？关联数据点将一并删除。');
  await delControlChart([row.chartId]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleAnalyze = async (row: SpcControlChartVO) => {
  analyzeLoading.value = true;
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

const typeLabel = (t?: string) =>
  ({ X_BAR_R: '均值-极差(X̄-R)', X_BAR_S: '均值-标准差(X̄-S)', P: '不良率(P)', C: '缺陷数(C)', U: '单位缺陷数(U)' })[t || ''] || t || '-';

onMounted(() => {
  getList();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  chartInstance?.dispose();
});
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
