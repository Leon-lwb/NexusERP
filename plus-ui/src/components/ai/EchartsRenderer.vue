<template>
  <div ref="chartRef" :style="{ width: '100%', height: (height || 300) + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  option: string
  height?: number
}>()

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  nextTick(() => {
    renderChart()
    // 监听容器大小变化
    if (chartRef.value) {
      resizeObserver = new ResizeObserver(() => {
        chartInstance?.resize()
      })
      resizeObserver.observe(chartRef.value)
    }
  })
})

watch(() => props.option, () => {
  renderChart()
})

function renderChart() {
  if (!chartRef.value) return
  try {
    const option = parseOption(props.option)
    if (!option) return

    if (!chartInstance) {
      chartInstance = echarts.init(chartRef.value)
    }
    // 安全过滤：移除可能包含 JavaScript 函数的字段
    sanitizeOption(option)
    chartInstance.setOption(option, true)
  } catch (e) {
    console.error('ECharts 渲染失败:', e)
  }
}

/**
 * 解析 ECharts 配置，支持 JSON 和 JS 对象字面量
 */
function parseOption(code: string): any {
  if (!code) return null

  let cleanCode = code.trim()
  // 移除 markdown 代码块标记
  cleanCode = cleanCode.replace(/^```(?:echarts|json|javascript)?\s*\n?/i, '')
  cleanCode = cleanCode.replace(/\n?```\s*$/i, '')
  cleanCode = cleanCode.trim()

  try {
    return JSON.parse(cleanCode)
  } catch {
    // 尝试作为 JS 对象字面量解析
    try {
      // 简单转换：给无引号的键加引号
      const jsonLike = cleanCode
        .replace(/(\w+)\s*:/g, '"$1":')
        .replace(/'/g, '"')
        .replace(/,\s*([}\]])/g, '$1')
      return JSON.parse(jsonLike)
    } catch {
      console.error('无法解析 ECharts 配置')
      return null
    }
  }
}

/**
 * 安全过滤：递归移除可能包含 JavaScript 代码的字段
 */
function sanitizeOption(option: any, depth = 0): void {
  if (!option || typeof option !== 'object' || depth > 10) return

  const forbiddenKeys = ['formatter', 'callback', 'function', 'onclick', 'onmouseover', 'onmouseout']
  const dangerousPatterns = /function\s*\(|=>|\bnew\s+|\beval\b|\bFunction\b/

  for (const key of Object.keys(option)) {
    const value = option[key]

    // 删除禁止的键
    if (forbiddenKeys.includes(key)) {
      delete option[key]
      continue
    }

    // 字符串值中包含危险代码
    if (typeof value === 'string' && dangerousPatterns.test(value)) {
      delete option[key]
      continue
    }

    // 递归处理嵌套对象
    if (typeof value === 'object' && value !== null) {
      sanitizeOption(value, depth + 1)
    }

    // 数组中的元素递归处理
    if (Array.isArray(value)) {
      for (const item of value) {
        if (typeof item === 'object' && item !== null) {
          sanitizeOption(item, depth + 1)
        }
      }
    }
  }
}

onUnmounted(() => {
  resizeObserver?.disconnect()
  chartInstance?.dispose()
  chartInstance = null
})
</script>
