import DOMPurify from 'dompurify';
import { Renderer } from 'marked';

/**
 * AI Markdown 渲染器
 * <p>检测 Markdown 中的 echarts/json 代码块，自动渲染为 ECharts 图表</p>
 */

/** ECharts 配置关键字段 */
const ECHARTS_KEY_FIELDS = ['xAxis', 'yAxis', 'series', 'title', 'legend', 'gauge', 'pie', 'radar', 'geo'];

/**
 * DOMPurify 净化配置。
 *
 * <p>其余标签/属性交给 DOMPurify 默认白名单（已排除 script / iframe / object / embed、
 * 所有 <code>on*</code> 事件属性与 <code>javascript:</code> URL），这里只显式收紧默认放行
 * 但对我们有风险的部分：</p>
 *
 * <ul>
 *   <li>禁止 <code>style</code>：可全局覆盖站点样式，并借 <code>url()</code> 外带信标；</li>
 *   <li>禁止 <code>base</code>/<code>meta</code>/<code>link</code>：可改写相对 URL 基址或注入跳转；</li>
 *   <li>禁止 <code>srcdoc</code> 属性：srcdoc iframe 继承父页源，等同同源脚本执行。</li>
 * </ul>
 *
 * <p>保留 <code>data-option</code>：AI 渲染器输出的 ECharts 容器靠它携带图表配置。</p>
 */
const SANITIZE_CONFIG = {
  ADD_ATTR: ['data-option', 'data-mounted'],
  FORBID_TAGS: ['style', 'base', 'meta', 'link', 'iframe', 'object', 'embed', 'script', 'form'],
  FORBID_ATTR: ['srcdoc']
};

/**
 * HTML 净化：移除危险的 HTML 标签和属性，防止存储型 XSS。
 *
 * <p>历史实现是一串正则黑名单，已实测可被绕过（未闭合的 <code>&lt;script&gt;</code>/<code>&lt;iframe&gt;</code>、
 * 未加引号的 <code>href=javascript:</code>、HTML 实体编码的 <code>java&amp;#115;cript:</code>、
 * 以及未被列入黑名单的 <code>&lt;style&gt;</code>/<code>&lt;base&gt;</code> 等），
 * 故改为 DOMPurify —— 基于浏览器真实解析树的标签/属性白名单净化，不再依赖正则匹配。</p>
 *
 * @param html 原始 HTML 字符串
 * @returns 净化后的 HTML
 */
export function sanitizeHtml(html: string): string {
  if (!html) return '';
  return DOMPurify.sanitize(html, SANITIZE_CONFIG) as string;
}

/**
 * 检测代码内容是否为 ECharts 配置
 * @param code 代码内容
 * @param language 代码语言
 * @returns 是否为 ECharts 配置
 */
export function isEchartsConfig(code: string, language?: string): boolean {
  if (!code || typeof code !== 'string') return false;

  // echarts 语言直接返回 true
  if (language === 'echarts') return true;

  // json/javascript 语言需要检测内容
  if (language === 'json' || language === 'javascript' || language === 'js' || !language) {
    const trimmed = code.trim();
    if (!trimmed.startsWith('{')) return false;

    // 检查是否包含 ECharts 关键字段
    let matchCount = 0;
    for (const field of ECHARTS_KEY_FIELDS) {
      if (trimmed.includes(`"${field}"`) || trimmed.includes(`'${field}'`) || trimmed.includes(`${field}:`)) {
        matchCount++;
      }
    }
    return matchCount >= 2;
  }

  return false;
}

/**
 * 为 marked 创建自定义 Renderer 实例
 * <p>继承默认 Renderer，仅覆盖 code 方法以支持 ECharts 渲染</p>
 * @returns marked Renderer 实例
 */
export function createAiRenderer(): Renderer {
  const renderer = new Renderer();

  // marked v18 中 code 方法接收 token 对象
  renderer.code = function (token: any): string {
    const code = token.text || '';
    const language = token.lang || '';

    if (language === 'echarts' || isEchartsConfig(code, language)) {
      // 渲染为 ECharts 容器，由前端 Vue 组件扫描并挂载
      const encoded = encodeURIComponent(code);
      return `<div class="echarts-container" data-option="${encoded}" style="width:100%;height:300px;margin:8px 0;"></div>`;
    }
    // 普通代码块
    const escaped = code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return `<pre><code class="language-${language}">${escaped}</code></pre>`;
  };

  return renderer;
}

/**
 * 安全过滤：递归移除 ECharts 配置中可能包含 JavaScript 代码的字段
 * <p>禁止 formatter、callback、function 等可执行字段，防止 XSS 和代码注入</p>
 * @param option ECharts 配置对象
 * @param depth 当前递归深度（防止过深递归）
 */
export function sanitizeOption(option: any, depth = 0): void {
  if (!option || typeof option !== 'object' || depth > 10) return;

  const forbiddenKeys = ['formatter', 'callback', 'function', 'onclick', 'onmouseover', 'onmouseout'];
  const dangerousPatterns = /function\s*\(|=>|\bnew\s+|\beval\b|\bFunction\b/;

  for (const key of Object.keys(option)) {
    const value = option[key];

    // 删除禁止的键
    if (forbiddenKeys.includes(key)) {
      delete option[key];
      continue;
    }

    // 字符串值中包含危险代码
    if (typeof value === 'string' && dangerousPatterns.test(value)) {
      delete option[key];
      continue;
    }

    // 递归处理嵌套对象和数组
    if (typeof value === 'object' && value !== null) {
      if (Array.isArray(value)) {
        for (const item of value) {
          if (typeof item === 'object' && item !== null) {
            sanitizeOption(item, depth + 1);
          }
        }
      } else {
        sanitizeOption(value, depth + 1);
      }
    }
  }
}
