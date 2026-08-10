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
  /** 时间线重放当前播放节点（画布移动小球 + 节点播放徽标用，null=未播放） */
  const playingNodeId = ref<string | null>(null);
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
  /** 是否未保存变更（返回/离开时确认；保存成功或加载流程后清除） */
  const dirty = ref(false);

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

  /**
   * 设置节点运行态
   * <p>必须【就地修改 data 属性】而非替换节点对象：VueFlow 受控模式下内部节点图与
   * 数组元素共享 data 对象引用（parseNode 浅拷贝），且其外部 nodes 同步是浅引用监听
   * （数组元素替换不触发、length 不变不触发）——替换对象会导致内部图持有旧 data，
   * FlowNode 的 props.data.status 永远不更新（时间线重放/运行时高亮失效）。</p>
   */
  function setNodeStatus(nodeId: string, status: string) {
    nodeStatusMap.value[nodeId] = status;
    const node = nodes.value.find((n) => n.id === nodeId);
    if (node) {
      node.data.status = status as FlowNodeData['status'];
    }
  }

  /** 清空运行态（同样就地修改，保持 data 对象身份稳定） */
  function clearNodeStatus() {
    nodeStatusMap.value = {};
    nodeSummaryMap.value = {};
    nodes.value.forEach((n) => {
      n.data.status = undefined;
    });
  }

  /**
   * 更新节点 data 元字段（label/config/onError/retry 等）
   * <p>与 setNodeStatus 同理：必须就地修改共享 data 对象属性（VueFlow 受控模式下
   * 内部节点图与数组元素共享 data 引用，整体替换元素不会被其浅监听感知）</p>
   */
  function setNodeMeta(nodeId: string | null | undefined, key: keyof FlowNodeData, value: unknown) {
    if (!nodeId) return;
    const node = nodes.value.find((n) => n.id === nodeId);
    if (node) {
      (node.data as Record<string, unknown>)[key] = value;
      dirty.value = true;
    }
  }

  /** 设置时间线重放当前播放节点（null=停止播放，隐藏小球） */
  function setPlayingNode(nodeId: string | null) {
    playingNodeId.value = nodeId;
  }

  /** 标记未保存变更（结构变更/参数修改后调用） */
  function markDirty() {
    dirty.value = true;
  }

  /** 清除未保存标记（加载流程/保存成功后调用） */
  function clearDirty() {
    dirty.value = false;
  }

  /** 添加节点到画布（空画布引导/快速添加用；自动递增偏移避免重叠） */
  function addNode(type: string, config: Record<string, unknown>, label: string) {
    const count = nodes.value.length;
    // 新节点放起始位置，已有节点依次下移 150px（与 autoLayout 同距）
    const position = { x: 120, y: 80 + count * 30 };
    const node: FlowCanvasNode = {
      id: `node_${type}_${Date.now()}_${count}`,
      type: 'flow-node',
      position,
      data: {
        label,
        type,
        config: JSON.parse(JSON.stringify(config))
      }
    };
    nodes.value = [...nodes.value, node];
    selectedNodeId.value = node.id;
    dirty.value = true;
    return node;
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
    playingNodeId,
    flowName,
    flowId,
    dirty,
    selectedNode,
    reset,
    toConfigJson,
    fromConfigJson,
    setNodeStatus,
    setNodeRun,
    setNodeSummary,
    setNodeOutputs,
    clearNodeStatus,
    setNodeMeta,
    setPlayingNode,
    markDirty,
    clearDirty,
    addNode,
    snapshot,
    undo,
    redo,
    clearHistory,
    copySelection,
    pasteClipboard,
    autoLayout
  };
});
