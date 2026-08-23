<template>
  <div ref="chartRef" :style="{ width: '100%', height: (height || 300) + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue';
import * as echarts from 'echarts';
import { sanitizeOption } from '@/utils/aiRenderers';

const props = defineProps<{
  option: string;
  height?: number;
}>();

const chartRef = ref<HTMLElement>();
let chartInstance: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  nextTick(() => {
    renderChart();
    // 监听容器大小变化
    if (chartRef.value) {
      resizeObserver = new ResizeObserver(() => {
        chartInstance?.resize();
      });
      resizeObserver.observe(chartRef.value);
    }
  });
});

watch(
  () => props.option,
  () => {
    renderChart();
  }
);

function renderChart() {
  if (!chartRef.value) return;
  try {
    const option = parseOption(props.option);
    if (!option) return;

    if (!chartInstance) {
      chartInstance = echarts.init(chartRef.value);
    }
    // 安全过滤：移除可能包含 JavaScript 函数的字段
    sanitizeOption(option);
    chartInstance.setOption(option, true);
  } catch (e) {
    console.error('ECharts 渲染失败:', e);
  }
}

/**
 * 解析 ECharts 配置，支持 JSON 和 JS 对象字面量
 */
function parseOption(code: string): any {
  if (!code) return null;

  let cleanCode = code.trim();
  // 移除 markdown 代码块标记
  cleanCode = cleanCode.replace(/^```(?:echarts|json|javascript)?\s*\n?/i, '');
  cleanCode = cleanCode.replace(/\n?```\s*$/i, '');
  cleanCode = cleanCode.trim();

  try {
    return JSON.parse(cleanCode);
  } catch {
    // 尝试作为 JS 对象字面量解析
    try {
      // 简单转换：给无引号的键加引号
      const jsonLike = cleanCode
        .replace(/(\w+)\s*:/g, '"$1":')
        .replace(/'/g, '"')
        .replace(/,\s*([}\]])/g, '$1');
      return JSON.parse(jsonLike);
    } catch {
      console.error('无法解析 ECharts 配置');
      return null;
    }
  }
}

onUnmounted(() => {
  resizeObserver?.disconnect();
  chartInstance?.dispose();
  chartInstance = null;
});
</script>
