<template>
  <div class="app-container">
    <!-- Device info header -->
    <el-card shadow="hover" class="mb-2">
      <div style="display: flex; align-items: center; gap: 20px; flex-wrap: wrap;">
        <div>
          <span style="font-weight: bold; font-size: 16px;">{{ deviceInfo.deviceName || '--' }}</span>
          <el-tag :type="connectionStatus === 'ONLINE' ? 'success' : 'info'" style="margin-left: 10px;">
            {{ connectionStatus === 'ONLINE' ? '在线' : '离线' }}
          </el-tag>
        </div>
        <div style="color: #909399; font-size: 13px;">设备编码: {{ deviceInfo.deviceCode || '--' }}</div>
        <div style="color: #909399; font-size: 13px;">产品: {{ deviceInfo.productName || '--' }}</div>
        <div style="color: #909399; font-size: 13px;">协议: {{ deviceInfo.protocolType || '--' }}</div>
        <el-button type="primary" plain size="small" @click="proxy.$router.back()">返回列表</el-button>
      </div>
    </el-card>

    <!-- Latest value cards -->
    <el-row :gutter="10" class="mb-2">
      <el-col v-for="(val, key) in latestProps" :key="key" :xs="12" :sm="8" :md="6" :lg="4" class="mb-1">
        <el-card shadow="hover" body-style="padding: 12px 16px;">
          <div style="color: #909399; font-size: 12px; margin-bottom: 4px;">{{ key }}</div>
          <div style="font-size: 22px; font-weight: bold; color: #303133;">{{ val }}</div>
        </el-card>
      </el-col>
      <el-col v-if="Object.keys(latestProps).length === 0" :span="24">
        <el-empty description="暂无属性数据，请等待设备上报" :image-size="80" />
      </el-col>
    </el-row>

    <!-- Charts row -->
    <el-row :gutter="10">
      <el-col :xs="24" :lg="12" class="mb-2">
        <el-card shadow="hover">
          <template #header>
            <span>实时数据 ({{ realtimePoints }} points, {{ updateInterval / 1000 }}s interval)</span>
          </template>
          <div ref="realtimeChartRef" style="height: 320px;" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12" class="mb-2">
        <el-card shadow="hover">
          <template #header>
            <span>历史趋势 (近 1 小时)</span>
          </template>
          <div ref="historyChartRef" style="height: 320px;" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="IotDeviceRealtime">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute } from 'vue-router';
import * as echarts from 'echarts';
import { getDevice } from '@/api/iot/device';
import { getLatest, getStatus, getHistory } from '@/api/iot/realtime';
import { getToken } from '@/utils/auth';
import { RealtimeFrame, DeviceDataVO } from '@/api/iot/realtime/types';

const { proxy } = getCurrentInstance();
const route = useRoute();
const deviceId = route.params.deviceId as string;

const deviceInfo = reactive<any>({});
const connectionStatus = ref('OFFLINE');
const latestProps = ref<Record<string, string>>({});

// Realtime chart state
const realtimeChartRef = ref();
const historyChartRef = ref();
const updateInterval = 3000;
const realtimePoints = 60;
let realtimeChart: echarts.ECharts | null = null;
let historyChart: echarts.ECharts | null = null;
let eventSource: EventSource | null = null;
let resizeHandler: (() => void) | null = null;

// Rolling window for realtime chart: propertyCode -> { times: [], values: [] }
const realtimeData = reactive<Record<string, { times: string[]; values: number[] }>>({});

/** Load device info */
async function loadDeviceInfo() {
  const res = await getDevice(deviceId);
  Object.assign(deviceInfo, res.data);
}

/** Load initial latest values */
async function loadLatest() {
  const res = await getLatest(deviceId);
  latestProps.value = res.data || {};
}

/** Load initial status */
async function loadStatus() {
  const res = await getStatus(deviceId);
  connectionStatus.value = res.data?.connectionStatus || 'OFFLINE';
}

/** Init realtime ECharts */
function initRealtimeChart() {
  if (!realtimeChartRef.value) return;
  realtimeChart = echarts.init(realtimeChartRef.value);
  realtimeChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll', bottom: 0 },
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: [] },
    yAxis: { type: 'value' },
    series: []
  });
  resizeHandler = () => {
    realtimeChart?.resize();
    historyChart?.resize();
  };
  window.addEventListener('resize', resizeHandler);
}

/** Update realtime chart from rolling window data */
function updateRealtimeChartOption() {
  if (!realtimeChart) return;
  const propertyCodes = Object.keys(realtimeData);
  if (propertyCodes.length === 0) return;
  // Use the first property's times as xAxis (all properties share same timestamps)
  const xTimes = realtimeData[propertyCodes[0]]?.times || [];
  realtimeChart.setOption({
    xAxis: { data: xTimes },
    series: propertyCodes.map((code) => ({
      name: code,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: realtimeData[code]?.values || []
    }))
  });
}

/** Load historical data and render history chart */
async function loadHistory() {
  const now = new Date();
  const oneHourAgo = new Date(now.getTime() - 3600 * 1000);
  const fmt = (d: Date) =>
    d.getFullYear() +
    '-' +
    String(d.getMonth() + 1).padStart(2, '0') +
    '-' +
    String(d.getDate()).padStart(2, '0') +
    ' ' +
    String(d.getHours()).padStart(2, '0') +
    ':' +
    String(d.getMinutes()).padStart(2, '0') +
    ':' +
    String(d.getSeconds()).padStart(2, '0');
  try {
    const res = await getHistory(deviceId, { start: fmt(oneHourAgo), end: fmt(now), limit: 500 });
    const rows: DeviceDataVO[] = res.rows || [];
    renderHistoryChart(rows);
  } catch {
    // ignore - device may have no data yet
  }
}

/** Render history chart from data rows */
function renderHistoryChart(rows: DeviceDataVO[]) {
  if (!historyChartRef.value) return;
  if (!historyChart) {
    historyChart = echarts.init(historyChartRef.value);
  }
  if (rows.length === 0) {
    historyChart.setOption({ title: { text: 'No data', left: 'center', top: 'center', textStyle: { color: '#909399' } } });
    return;
  }
  // Group by propertyCode
  const grouped: Record<string, { times: string[]; values: number[] }> = {};
  rows.forEach((r) => {
    if (!grouped[r.propertyCode]) grouped[r.propertyCode] = { times: [], values: [] };
    grouped[r.propertyCode].times.push(r.collectTime);
    const val = r.numericValue != null ? Number(r.numericValue) : NaN;
    grouped[r.propertyCode].values.push(val);
  });
  const propertyCodes = Object.keys(grouped);
  const xTimes = grouped[propertyCodes[0]]?.times || [];
  historyChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll', bottom: 0 },
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: xTimes, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' },
    series: propertyCodes.map((code) => ({
      name: code,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: grouped[code].values
    }))
  });
}

/** Subscribe to SSE realtime stream */
function subscribeSSE() {
  const base = import.meta.env.VITE_APP_BASE_API;
  const url = base + '/iot/device/' + deviceId + '/realtime?Authorization=Bearer ' + getToken();
  eventSource = new EventSource(url);
  eventSource.addEventListener('realtime', (e: MessageEvent) => {
    try {
      const frame: RealtimeFrame = JSON.parse(e.data);
      // Update connection status
      connectionStatus.value = frame.connectionStatus || 'OFFLINE';
      // Update latest value cards
      latestProps.value = frame.properties || {};
      // Append to rolling window
      const time = new Date(frame.timestamp).toLocaleTimeString();
      const props = frame.properties || {};
      Object.entries(props).forEach(([code, val]) => {
        const numVal = Number(val);
        if (isNaN(numVal)) return; // only chart numeric values
        if (!realtimeData[code]) {
          realtimeData[code] = { times: [], values: [] };
        }
        realtimeData[code].times.push(time);
        realtimeData[code].values.push(numVal);
        // Keep only last N points
        if (realtimeData[code].times.length > realtimePoints) {
          realtimeData[code].times.shift();
          realtimeData[code].values.shift();
        }
      });
      updateRealtimeChartOption();
    } catch {
      // ignore parse errors
    }
  });
  let reconnectAttempts = 0;
  const maxReconnectAttempts = 5;
  eventSource.onerror = () => {
    if (eventSource?.readyState === EventSource.CLOSED) {
      // 连接已永久关闭（如 401/403），停止重连
      eventSource?.close();
      eventSource = null;
      connectionStatus.value = 'OFFLINE';
      ElMessage.error('实时数据连接已断开，请刷新页面重试');
      return;
    }
    // 连接异常但未关闭，EventSource 会自动重连
    reconnectAttempts++;
    if (reconnectAttempts > maxReconnectAttempts) {
      eventSource?.close();
      eventSource = null;
      connectionStatus.value = 'OFFLINE';
      ElMessage.warning('实时数据连接不稳定，已停止重连，请刷新页面');
    }
  };
}

onMounted(async () => {
  await loadDeviceInfo();
  await loadLatest();
  await loadStatus();
  await nextTick();
  initRealtimeChart();
  subscribeSSE();
  loadHistory();
});

onUnmounted(() => {
  eventSource?.close();
  eventSource = null;
  realtimeChart?.dispose();
  realtimeChart = null;
  historyChart?.dispose();
  historyChart = null;
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler);
    resizeHandler = null;
  }
});
</script>
