/**
 * AI Markdown 渲染器
 * <p>检测 Markdown 中的 echarts/json 代码块，自动渲染为 ECharts 图表</p>
 */

/** ECharts 配置关键字段 */
const ECHARTS_KEY_FIELDS = ['xAxis', 'yAxis', 'series', 'title', 'legend', 'gauge', 'pie', 'radar', 'geo']

/**
 * 检测代码内容是否为 ECharts 配置
 * @param code 代码内容
 * @param language 代码语言
 * @returns 是否为 ECharts 配置
 */
export function isEchartsConfig(code: string, language?: string): boolean {
  if (!code || typeof code !== 'string') return false

  // echarts 语言直接返回 true
  if (language === 'echarts') return true

  // json/javascript 语言需要检测内容
  if (language === 'json' || language === 'javascript' || language === 'js' || !language) {
    const trimmed = code.trim()
    if (!trimmed.startsWith('{')) return false

    // 检查是否包含 ECharts 关键字段
    let matchCount = 0
    for (const field of ECHARTS_KEY_FIELDS) {
      if (trimmed.includes(`"${field}"`) || trimmed.includes(`'${field}'`) || trimmed.includes(`${field}:`)) {
        matchCount++
      }
    }
    return matchCount >= 2
  }

  return false
}

/**
 * 为 marked 创建自定义代码块渲染器
 * @returns marked Renderer 对象
 */
export function createAiRenderer() {
  return {
    code(this: any, code: string, language?: string) {
      if (language === 'echarts' || isEchartsConfig(code, language)) {
        // 渲染为 ECharts 容器，由前端 Vue 组件扫描并挂载
        const encoded = encodeURIComponent(code)
        return `<div class="echarts-container" data-option="${encoded}" style="width:100%;height:300px;margin:8px 0;"></div>`
      }
      // 普通代码块
      const escaped = code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      return `<pre><code class="language-${language || ''}">${escaped}</code></pre>`
    }
  }
}
