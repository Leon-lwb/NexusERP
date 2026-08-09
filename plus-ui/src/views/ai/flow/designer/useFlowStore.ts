import { defineStore } from 'pinia';
import type { Edge, Node } from '@vue-flow/core';
import { computed, ref } from 'vue';

/**
 * 流程设计器状态
 * <p>当前画布 nodes/edges、选中节点、运行态 nodeStatusMap。
 * config_json 保存/加载通过 toConfigJson/fromConfigJson 转换</p>
 */

/** 画布节点 data 结构 */
export interface FlowNodeData {
  label: string;
  type: string;
  status?: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';
  config?: Record<string, unknown>;
  onError?: 'STOP' | 'CONTINUE';
  retryOnFail?: boolean;
  retryMaxTries?: number;
  waitBetweenTries?: number;
  timeoutSeconds?: number;
}

/** 画布节点类型（宽松 Node 结构，避免 @vue-flow Node 泛型深实例化） */
export interface FlowCanvasNode {
  id: string;
  type?: string;
  position: { x: number; y: number };
  data: FlowNodeData;
  selected?: boolean;
}

export const useFlowStore = defineStore('aiFlow', () => {
  /** 当前画布节点 */
  const nodes = ref<FlowCanvasNode[]>([]);
  /** 当前画布边 */
  const edges = ref<Edge[]>([]);
  /** 选中节点 ID */
  const selectedNodeId = ref<string | null>(null);
  /** 运行态节点状态映射（nodeId → RUNNING/SUCCEEDED/FAILED/SKIPPED） */
  const nodeStatusMap = ref<Record<string, string>>({});
  /** 当前运行实例 ID */
  const currentInstanceId = ref<string | null>(null);
  /** 节点运行快照缓存（NDV 双击查看：nodeId → 最近一次运行记录） */
  const nodeRunCache = ref<Record<string, unknown>>({});
  /** 节点运行摘要（SSE AI_FLOW_NODE_END.outputSummary：nodeId → 摘要/错误文本） */
  const nodeSummaryMap = ref<Record<string, string>>({});
  /** 节点运行时输出缓存（运行结束后从 node_run 拉取：nodeId → outputJson 对象，变量选择器真实数据用） */
  const nodeOutputs = ref<Record<string, unknown> | null>(null);
  /** 撤销栈（节点+边快照，上限 50） */
  const historyStack = ref<string[]>([]);
  /** 重做栈 */
  const futureStack = ref<string[]>([]);
  /** 剪贴板（复制节点+连边） */
  const clipboard = ref<{ nodes: string; edges: string } | null>(null);
  /** 流程名称 */
  const flowName = ref('');
  /** 流程 ID（null=新建） */
  const flowId = ref<string | number | null>(null);

  /** 选中节点 */
  const selectedNode = computed(() => {
    if (!selectedNodeId.value) return null;
    return nodes.value.find((n) => n.id === selectedNodeId.value) ?? null;
  });

  /** 清空画布 */
  function reset() {
    nodes.value = [];
    edges.value = [];
    selectedNodeId.value = null;
    nodeStatusMap.value = {};
    currentInstanceId.value = null;
    nodeRunCache.value = {};
  }

  /** 设置节点运行快照（NDV 查看用，按 nodeId 覆盖为最新轮） */
  function setNodeRun(nodeId: string, run: unknown) {
    nodeRunCache.value = { ...nodeRunCache.value, [nodeId]: run };
  }

  /**
   * 序列化为后端 config_json
   * <p>对齐契约：{nodes:[{id,type,position,data}], edges:[{id,source,target,sourceHandle,targetHandle}]}</p>
   */
  function toConfigJson(): string {
    const data = {
      nodes: nodes.value.map((n) => ({
        id: n.id,
        type: n.data.type,
        position: [n.position.x, n.position.y],
        data: {
          label: n.data.label,
          ...(n.data.config ? { config: n.data.config } : {}),
          ...(n.data.onError ? { onError: n.data.onError } : {}),
          ...(n.data.retryOnFail !== undefined ? { retryOnFail: n.data.retryOnFail } : {}),
          ...(n.data.retryMaxTries !== undefined ? { retryMaxTries: n.data.retryMaxTries } : {}),
          ...(n.data.waitBetweenTries !== undefined ? { waitBetweenTries: n.data.waitBetweenTries } : {}),
          ...(n.data.timeoutSeconds !== undefined ? { timeoutSeconds: n.data.timeoutSeconds } : {})
        }
      })),
      edges: edges.value.map(
        (e: any): Record<string, unknown> => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle ?? 'outputs/main/0',
          targetHandle: e.targetHandle ?? 'inputs/main/0',
          ...(e.loop ? { loop: true } : {})
        })
      )
    };
    return JSON.stringify(data);
  }

  /**
   * 从后端 config_json 加载画布
   * @param configJson 后端存储的 JSON
   */
  function fromConfigJson(configJson: string) {
    try {
      const data = JSON.parse(configJson);
      const rawNodes = Array.isArray(data.nodes) ? data.nodes : [];
      const rawEdges = Array.isArray(data.edges) ? data.edges : [];

      nodes.value = rawNodes.map((n: Record<string, any>) => ({
        id: n.id,
        type: 'flow-node',
        position: Array.isArray(n.position) ? { x: n.position[0], y: n.position[1] } : { x: 0, y: 0 },
        data: {
          label: n.data?.label ?? n.id,
          type: n.type,
          config: n.data?.config ?? {},
          onError: n.data?.onError,
          retryOnFail: n.data?.retryOnFail,
          retryMaxTries: n.data?.retryMaxTries,
          waitBetweenTries: n.data?.waitBetweenTries,
          timeoutSeconds: n.data?.timeoutSeconds
        }
      }));

      edges.value = rawEdges.map((e: Record<string, any>) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
        markerEnd: 'arrowclosed',
        ...(e.loop ? { loop: true } : {})
      }));
    } catch (e) {
      console.error('[Flow] config_json 解析失败:', e);
      nodes.value = [];
      edges.value = [];
    }
  }

  /** 设置节点运行态 */
  function setNodeStatus(nodeId: string, status: string) {
    nodeStatusMap.value[nodeId] = status;
    const index = nodes.value.findIndex((n) => n.id === nodeId);
    if (index >= 0) {
      nodes.value[index] = {
        ...nodes.value[index],
        data: { ...nodes.value[index].data, status: status as FlowNodeData['status'] }
      };
    }
  }

  /** 清空运行态 */
  function clearNodeStatus() {
    nodeStatusMap.value = {};
    nodeSummaryMap.value = {};
    nodes.value = nodes.value.map((n) => ({
      ...n,
      data: { ...n.data, status: undefined }
    }));
  }

  /** 设置节点运行摘要（输出/错误，显示在节点卡片底部） */
  function setNodeSummary(nodeId: string, summary: string) {
    if (!summary) return;
    nodeSummaryMap.value = { ...nodeSummaryMap.value, [nodeId]: summary };
  }

  /** 设置节点运行时输出缓存（变量选择器真实数据） */
  function setNodeOutputs(map: Record<string, unknown> | null) {
    nodeOutputs.value = map;
  }

  /** 快照当前画布（结构性变更前调用，如连线/删除/拖入/粘贴/布局） */
  function snapshot() {
    historyStack.value.push(JSON.stringify({ nodes: nodes.value, edges: edges.value }));
    if (historyStack.value.length > 50) historyStack.value.shift();
    futureStack.value = [];
  }

  /** 撤销 */
  function undo() {
    const prev = historyStack.value.pop();
    if (!prev) return false;
    futureStack.value.push(JSON.stringify({ nodes: nodes.value, edges: edges.value }));
    const data = JSON.parse(prev);
    nodes.value = data.nodes;
    edges.value = data.edges;
    return true;
  }

  /** 重做 */
  function redo() {
    const next = futureStack.value.pop();
    if (!next) return false;
    historyStack.value.push(JSON.stringify({ nodes: nodes.value, edges: edges.value }));
    const data = JSON.parse(next);
    nodes.value = data.nodes;
    edges.value = data.edges;
    return true;
  }

  /** 清空历史（加载流程时） */
  function clearHistory() {
    historyStack.value = [];
    futureStack.value = [];
  }

  /** 复制选中节点（含节点间连边）到剪贴板 */
  function copySelection(selectedNodes: FlowCanvasNode[]) {
    if (!selectedNodes.length) return false;
    const ids = new Set(selectedNodes.map((n) => n.id));
    const selEdges = edges.value.filter((e) => ids.has(e.source) && ids.has(e.target));
    clipboard.value = {
      nodes: JSON.stringify(selectedNodes.map((n) => ({ ...n, selected: undefined }))),
      edges: JSON.stringify(selEdges)
    };
    return true;
  }

  /** 粘贴剪贴板（新 ID + 偏移 40px，含连边重映射） */
  function pasteClipboard(): { nodes: FlowCanvasNode[]; edges: Edge[] } | null {
    if (!clipboard.value) return null;
    const srcNodes = JSON.parse(clipboard.value.nodes) as FlowCanvasNode[];
    const srcEdges = JSON.parse(clipboard.value.edges) as Edge[];
    const idMap: Record<string, string> = {};
    const stamp = Date.now();
    const newNodes = srcNodes.map((n, i) => {
      const newId = `${n.id}_copy${stamp}_${i}`;
      idMap[n.id] = newId;
      return { ...n, id: newId, position: { x: n.position.x + 40, y: n.position.y + 40 } };
    });
    const newEdges = srcEdges.map((e, i) => ({
      ...e,
      id: `edge_${stamp}_${i}`,
      source: idMap[e.source] ?? e.source,
      target: idMap[e.target] ?? e.target
    }));
    return { nodes: newNodes, edges: newEdges };
  }

  /** 自动布局：BFS 分层（跳过 loop 返回边防发散），同层垂直排布 */
  function autoLayout() {
    if (!nodes.value.length) return;
    // 分层：重复扫描边直到稳定（跳过 loop 返回边）
    const layer: Record<string, number> = {};
    nodes.value.forEach((n) => (layer[n.id] = 0));
    let changed = true;
    while (changed) {
      changed = false;
      for (const e of edges.value) {
        if ((e as any).loop) continue;
        if ((layer[e.target] ?? 0) <= (layer[e.source] ?? 0)) {
          layer[e.target] = (layer[e.source] ?? 0) + 1;
          changed = true;
        }
      }
    }
    // 同层垂直排布
    const byLayer: Record<number, string[]> = {};
    nodes.value.forEach((n) => {
      const l = layer[n.id] ?? 0;
      (byLayer[l] ??= []).push(n.id);
    });
    nodes.value = nodes.value.map((n) => {
      const l = layer[n.id] ?? 0;
      const idx = byLayer[l].indexOf(n.id);
      return { ...n, position: { x: 40 + l * 260, y: 40 + idx * 150 } };
    });
  }

  return {
    nodes,
    edges,
    selectedNodeId,
    nodeStatusMap,
    currentInstanceId,
    nodeRunCache,
    nodeSummaryMap,
    nodeOutputs,
    flowName,
    flowId,
    selectedNode,
    reset,
    toConfigJson,
    fromConfigJson,
    setNodeStatus,
    setNodeRun,
    setNodeSummary,
    setNodeOutputs,
    clearNodeStatus,
    snapshot,
    undo,
    redo,
    clearHistory,
    copySelection,
    pasteClipboard,
    autoLayout
  };
});
