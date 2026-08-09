import { computed, ref } from 'vue';
import type { FlowCanvasNode } from './useFlowStore';

/**
 * {{ }} 变量选择器 composable（对齐 n8n Expression Editor 的轻量版）
 * <p>支持任意 input/textarea：输入 {{ 弹出候选列表 → 插入到光标位置。
 * 弹层 fixed 定位 + 视口翻转（修复越界）。候选来源：运行时真实输出优先 + 静态枚举回退</p>
 */

export interface VariableCandidate {
  label: string;
  insert: string;
}

export function useVariablePicker() {
  const visible = ref(false);
  const position = ref({ top: 0, left: 0 });
  const filter = ref('');
  const activeEl = ref<HTMLInputElement | HTMLTextAreaElement | null>(null);
  const candidates = ref<VariableCandidate[]>([]);
  const onInsert = ref<((text: string) => void) | null>(null);

  /**
   * 打开选择器（输入事件中调用；光标前含未闭合 {{ 才打开）
   * @param el    输入元素
   * @param list  候选列表
   * @param apply 插入后回写外部值（el.value 已更新）
   * @returns 是否打开
   */
  function open(el: HTMLInputElement | HTMLTextAreaElement, list: VariableCandidate[], apply: (text: string) => void): boolean {
    const caret = el.selectionStart ?? el.value.length;
    const before = el.value.slice(Math.max(0, caret - 10), caret);
    const openIdx = before.lastIndexOf('{{');
    const closeIdx = before.lastIndexOf('}}');
    if (openIdx < 0 || (closeIdx >= 0 && closeIdx > openIdx)) {
      close();
      return false;
    }
    filter.value = before.slice(openIdx + 2);
    candidates.value = list;
    onInsert.value = apply;
    activeEl.value = el;
    // fixed 定位 + 视口翻转
    const rect = el.getBoundingClientRect();
    const W = 300;
    const H = 300;
    let left = rect.left;
    if (left + W > window.innerWidth - 8) left = Math.max(8, window.innerWidth - W - 8);
    let top = rect.bottom + 4;
    if (top + H > window.innerHeight - 8) top = Math.max(8, rect.top - H - 4);
    position.value = { top, left };
    visible.value = true;
    return true;
  }

  /** 过滤后的候选 */
  const filteredCandidates = computed(() => {
    const f = filter.value.trim().toLowerCase();
    if (!f) return candidates.value;
    return candidates.value.filter((c) => c.label.toLowerCase().includes(f) || c.insert.toLowerCase().includes(f));
  });

  /** 插入候选到光标位置（移除已输入的 {{filter 前缀） */
  function insert(c: VariableCandidate) {
    const el = activeEl.value;
    if (!el) return;
    const caret = el.selectionStart ?? el.value.length;
    const before = el.value.slice(0, caret);
    const openIdx = before.lastIndexOf('{{');
    const start = openIdx >= 0 ? openIdx : caret;
    const newText = el.value.slice(0, start) + c.insert + el.value.slice(caret);
    el.value = newText;
    onInsert.value?.(newText);
    close();
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + c.insert.length, start + c.insert.length);
    });
  }

  function close() {
    visible.value = false;
    activeEl.value = null;
    onInsert.value = null;
  }

  return { visible, position, filter, filteredCandidates, open, insert, close };
}

/**
 * 构建变量候选列表
 * <p>运行时数据优先：nodeOutputs 提供节点真实输出（运行后缓存）→ 递归提取字段路径；
 * 无运行数据时回退按节点类型静态枚举常见字段</p>
 */
export function buildVariableCandidates(upstreamNodes: FlowCanvasNode[], nodeOutputs: Record<string, unknown> | null): VariableCandidate[] {
  const candidates: VariableCandidate[] = [];
  // 运行入参 input（无 schema 元数据，保留常用占位）
  candidates.push({ label: '运行入参 input', insert: '{{input.xxx}}' });
  candidates.push({ label: 'input.name', insert: '{{input.name}}' });
  candidates.push({ label: 'input.amount', insert: '{{input.amount}}' });
  candidates.push({ label: 'input.items', insert: '{{input.items}}' });

  for (const n of upstreamNodes ?? []) {
    const prefix = `nodes.${n.id}.output`;
    const label = n.data.label || n.id;

    // 1. 运行时真实输出优先：递归提取第一层字段（对象）+ 数组标记
    const realOutput = nodeOutputs?.[n.id];
    if (realOutput && typeof realOutput === 'object') {
      collectObjectFields(realOutput as Record<string, unknown>, `${prefix}`, label, candidates, 2);
      continue;
    }

    // 2. 静态枚举回退（按节点类型常见输出字段）
    const type = n.data.type;
    const commonFields: Array<[string, string]> = [];
    if (type === 'llm' || type === 'agent') {
      commonFields.push(['content', `${prefix}.content`], ['tokenUsage', `${prefix}.tokenUsage`]);
    } else if (type === 'rag') {
      commonFields.push(['chunks', `${prefix}.chunks`], ['count', `${prefix}.count`], ['augmentedPrompt', `${prefix}.augmentedPrompt`]);
    } else if (type === 'sql') {
      commonFields.push(['rows', `${prefix}.rows`], ['count', `${prefix}.count`], ['columns', `${prefix}.columns`]);
    } else if (type === 'set' || type === 'code') {
      // 动态字段：config.variables / expressions 的键
      const vars = (n.data.config as Record<string, unknown>) ?? {};
      const map = (vars.variables ?? vars.expressions ?? {}) as Record<string, unknown>;
      Object.keys(map).forEach((k) => commonFields.push([k, `${prefix}.${k}`]));
    } else if (type === 'if') {
      commonFields.push(['result', `${prefix}.result`]);
    } else if (type === 'merge') {
      commonFields.push(['inputs', `${prefix}.inputs`], ['count', `${prefix}.count`]);
    } else if (type === 'split') {
      commonFields.push(['batch', `${prefix}.batch`], ['index', `${prefix}.index`], ['done', `${prefix}.done`], ['total', `${prefix}.total`]);
    } else if (type === 'http') {
      commonFields.push(['status', `${prefix}.status`], ['bodyJson', `${prefix}.bodyJson`], ['bodyText', `${prefix}.bodyText`]);
    } else if (type === 'notify' || type === 'mail' || type === 'sms' || type === 'im_webhook') {
      commonFields.push(['success', `${prefix}.success`], ['message', `${prefix}.message`]);
    } else if (type === 'erp_tool') {
      commonFields.push(['result', `${prefix}.result`], ['toolName', `${prefix}.toolName`]);
    } else if (type === 'wait') {
      commonFields.push(['waitedSeconds', `${prefix}.waitedSeconds`]);
    }
    for (const [field, path] of commonFields) {
      candidates.push({ label: `${label}.${field}`, insert: `{{${path}}}` });
    }
  }
  return candidates;
}

/**
 * 递归提取对象字段路径（运行时数据）
 * @param depth 剩余深度（防止过深路径，MVP 2 层 + 数组标记）
 */
function collectObjectFields(obj: Record<string, unknown>, path: string, label: string, out: VariableCandidate[], depth: number) {
  for (const [k, v] of Object.entries(obj)) {
    const insert = `{{${path}.${k}}}`;
    if (v !== null && typeof v === 'object' && !Array.isArray(v) && depth > 0) {
      out.push({ label: `${label}.${k}`, insert });
      collectObjectFields(v as Record<string, unknown>, `${path}.${k}`, label, out, depth - 1);
    } else if (Array.isArray(v)) {
      out.push({ label: `${label}.${k} (数组)`, insert: `{{${path}.${k}}}` });
      // 数组元素字段（第一层）：rows[0].xxx
      if (depth > 0 && v.length && v[0] !== null && typeof v[0] === 'object') {
        for (const ek of Object.keys(v[0] as Record<string, unknown>)) {
          out.push({ label: `${label}.${k}[0].${ek}`, insert: `{{${path}.${k}[0].${ek}}}` });
        }
      }
    } else {
      out.push({ label: `${label}.${k}`, insert });
    }
  }
}
