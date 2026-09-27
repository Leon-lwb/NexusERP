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

/** 画布节点类型（宽松 Node 结构，避免 @vue-flow 节点 泛型深实例化） */
export interface FlowCanvasNode {
  id: string;
  type?: string;
  position: { x: number; y: number };
  data: FlowNodeData;
  selected?: boolean;
  /** 折叠分组内节点：VueFlow 不渲染但保留在数据中（保存时仍序列化，属流程一部分） */
  hidden?: boolean;
}

/** 节点分组（对齐 n8n 节点分组 的简化版：包围框 + 标题栏，成员为普通节点） */
export interface FlowGroup {
  id: string;
  name: string;
  nodeIds: string[];
  collapsed: boolean;
  /** 包围框（flow 坐标，随成员/拖拽更新） */
  bounds: { x: number; y: number; width: number; height: number };
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
  /**
   * 系统图 JSON 保持逐字节不变，自动坐标仅用于展示。
   */
  const displayOnlyConfigJson = ref<string | null>(null);
  /** 节点分组（视图状态，不随 config 保存） */
  const groups = ref<FlowGroup[]>([]);
  /** 当前选中的分组 ID（高亮边框；Delete 解除分组） */
  const selectedGroupId = ref<string | null>(null);
  /** hover 节点 ID（上下游路径高亮用） */
  const hoverNodeId = ref<string | null>(null);

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
    displayOnlyConfigJson.value = null;
    nodeRunCache.value = {};
    groups.value = [];
    selectedGroupId.value = null;
    hoverNodeId.value = null;
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
    if (displayOnlyConfigJson.value !== null && !dirty.value) return displayOnlyConfigJson.value;
    const data = {
      // 便利贴是本地视图标注，不随流程保存（后端无此节点类型）
      nodes: nodes.value
        .filter((n) => n.data.type !== 'flow-note')
        .map((n) => ({
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
  function fromConfigJson(configJson: string, options?: { displayOnlyLayout?: boolean }) {
    displayOnlyConfigJson.value = null;
    try {
      const data = JSON.parse(configJson);
      const rawNodes = Array.isArray(data.nodes) ? data.nodes : [];
      const rawEdges = Array.isArray(data.edges) ? data.edges : [];
      const validPosition = (n: Record<string, any>) =>
        Array.isArray(n.position) && n.position.length === 2 && Number.isFinite(n.position[0]) && Number.isFinite(n.position[1]);
      const missingPosition = rawNodes.some((n: Record<string, any>) => !validPosition(n));
      const overlappingPosition = rawNodes.some(
        (n: Record<string, any>, index: number) =>
          validPosition(n) &&
          rawNodes
            .slice(index + 1)
            .some(
              (other: Record<string, any>) =>
                validPosition(other) && Math.abs(n.position[0] - other.position[0]) < 160 && Math.abs(n.position[1] - other.position[1]) < 80
            )
      );
      const needsLayout = options?.displayOnlyLayout && (missingPosition || overlappingPosition);

      nodes.value = rawNodes.map((n: Record<string, any>) => ({
        id: n.id,
        type: 'flow-node',
        position: validPosition(n) ? { x: n.position[0], y: n.position[1] } : { x: 0, y: 0 },
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
      if (needsLayout) autoLayout();
      if (options?.displayOnlyLayout) displayOnlyConfigJson.value = configJson;
      // 分组/折叠是视图状态，不随配置保存——加载时重置
      groups.value = [];
      selectedGroupId.value = null;
      hoverNodeId.value = null;
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

  /** 添加便利贴（本地注释，不随 config 保存） */
  function addNote() {
    const notes = nodes.value.filter((n) => n.data.type === 'flow-note').length;
    const node: FlowCanvasNode = {
      id: `note_${Date.now()}`,
      type: 'flow-note',
      position: { x: 220 + notes * 30, y: 140 + notes * 30 },
      data: { label: '便利贴', type: 'flow-note' }
    };
    nodes.value = [...nodes.value, node];
    selectedNodeId.value = node.id;
    dirty.value = true;
    return node;
  }

  /* ==================== 节点分组（对齐 n8n 节点分组 简化版） ==================== */

  /** 节点默认尺寸（与 FlowNode .flow-node 一致，包围框计算用） */
  const NODE_W = 160;
  const NODE_H = 80;

  /** 计算一组节点的包围框（flow 坐标） */
  function computeGroupBounds(ids: string[]): { x: number; y: number; width: number; height: number } {
    const list = nodes.value.filter((n) => ids.includes(n.id));
    if (!list.length) return { x: 0, y: 0, width: NODE_W, height: NODE_H };
    const minX = Math.min(...list.map((n) => n.position.x));
    const minY = Math.min(...list.map((n) => n.position.y));
    const maxX = Math.max(...list.map((n) => n.position.x + NODE_W));
    const maxY = Math.max(...list.map((n) => n.position.y + NODE_H));
    const PAD = 26; // 标题栏 + 边距
    return { x: minX - 10, y: minY - PAD - 4, width: maxX - minX + 20, height: maxY - minY + PAD + 14 };
  }

  /** 创建分组（选中 ≥2 节点）；返回分组或 null */
  function createGroup(ids: string[]): FlowGroup | null {
    const valid = ids.filter((id) => nodes.value.some((n) => n.id === id) && !groups.value.some((g) => g.nodeIds.includes(id)));
    if (valid.length < 2) return null;
    const group: FlowGroup = {
      id: `group_${Date.now()}`,
      name: `分组 ${groups.value.length + 1}`,
      nodeIds: valid,
      collapsed: false,
      bounds: computeGroupBounds(valid)
    };
    groups.value = [...groups.value, group];
    selectedGroupId.value = group.id;
    dirty.value = true;
    return group;
  }

  /** 取消分组（节点保留在画布） */
  function removeGroup(groupId: string) {
    const g = groups.value.find((x) => x.id === groupId);
    if (!g) return;
    // 折叠态取消：恢复成员可见
    if (g.collapsed) {
      const restoredNodes: FlowCanvasNode[] = [];
      for (const n of nodes.value) {
        restoredNodes.push(g.nodeIds.includes(n.id) ? { ...n, hidden: false } : n);
      }
      nodes.value = restoredNodes;
      // Edge 泛型层级较深，直接以 Edge[] 赋值会触发 TS2589，经 any 中转断言
      const restoredEdges: any[] = [];
      for (const e of edges.value) {
        const linked = g.nodeIds.includes(e.source) || g.nodeIds.includes(e.target);
        restoredEdges.push(linked ? { ...e, hidden: false } : e);
      }
      edges.value = restoredEdges as Edge[];
    }
    groups.value = groups.value.filter((x) => x.id !== groupId);
    if (selectedGroupId.value === groupId) selectedGroupId.value = null;
    dirty.value = true;
  }

  /** 折叠/展开分组（组内节点与关联边 hidden，VueFlow 不渲染但数据保留） */
  function toggleGroupCollapse(groupId: string) {
    const g = groups.value.find((x) => x.id === groupId);
    if (!g) return;
    const collapsed = !g.collapsed;
    groups.value = groups.value.map((x) => (x.id === groupId ? { ...x, collapsed } : x));
    nodes.value = nodes.value.map((n) => (g.nodeIds.includes(n.id) ? { ...n, hidden: collapsed } : n));
    // Edge 泛型层级较深，经 any 中转断言避免 TS2589
    const updatedEdges: any[] = [];
    for (const e of edges.value) {
      const linked = g.nodeIds.includes(e.source) || g.nodeIds.includes(e.target);
      updatedEdges.push(linked ? { ...e, hidden: collapsed } : e);
    }
    edges.value = updatedEdges as Edge[];
    dirty.value = true;
  }

  /** 重命名分组 */
  function renameGroup(groupId: string, name: string) {
    groups.value = groups.value.map((x) => (x.id === groupId ? { ...x, name: name.trim() || x.name } : x));
    dirty.value = true;
  }

  /** 拖拽整组：组内节点 position 整体偏移 + 组框同步（flow 坐标增量） */
  function moveGroup(groupId: string, dx: number, dy: number) {
    const g = groups.value.find((x) => x.id === groupId);
    if (!g || (dx === 0 && dy === 0)) return;
    const ids = new Set(g.nodeIds);
    nodes.value = nodes.value.map((n) => (ids.has(n.id) ? { ...n, position: { x: n.position.x + dx, y: n.position.y + dy } } : n));
    groups.value = groups.value.map((x) => (x.id === groupId ? { ...x, bounds: { ...x.bounds, x: x.bounds.x + dx, y: x.bounds.y + dy } } : x));
    dirty.value = true;
  }

  /** 成员拖动/布局后重算组包围框（保持组框贴合成员） */
  function syncGroupBounds() {
    groups.value = groups.value.map((g) => ({ ...g, bounds: computeGroupBounds(g.nodeIds) }));
  }

  /** 组内节点状态聚合摘要（标题栏徽标：成功/失败/运行计数） */
  function groupStatusSummary(groupId: string): { success: number; failed: number; running: number } {
    const g = groups.value.find((x) => x.id === groupId);
    if (!g) return { success: 0, failed: 0, running: 0 };
    let success = 0;
    let failed = 0;
    let running = 0;
    for (const id of g.nodeIds) {
      const s = nodeStatusMap.value[id];
      if (s === 'SUCCEEDED') success++;
      else if (s === 'FAILED') failed++;
      else if (s === 'RUNNING') running++;
    }
    return { success, failed, running };
  }

  /** 上下游链路（路径高亮用，递归含 loop 边去重） */
  function getUpstreamIds(nodeId: string): Set<string> {
    const result = new Set<string>();
    const stack = [...edges.value.filter((e: any) => e.target === nodeId).map((e: any) => e.source)];
    while (stack.length) {
      const id = stack.pop()!;
      if (result.has(id)) continue;
      result.add(id);
      edges.value.forEach((e: any) => {
        if (e.target === id && !result.has(e.source)) stack.push(e.source);
      });
    }
    return result;
  }
  function getDownstreamIds(nodeId: string): Set<string> {
    const result = new Set<string>();
    const stack = [...edges.value.filter((e: any) => e.source === nodeId).map((e: any) => e.target)];
    while (stack.length) {
      const id = stack.pop()!;
      if (result.has(id)) continue;
      result.add(id);
      edges.value.forEach((e: any) => {
        if (e.source === id && !result.has(e.target)) stack.push(e.target);
      });
    }
    return result;
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
    // Edge 泛型层级较深，经 any 中转避免 TS2589（结果仅用于 JSON 序列化）
    const selEdges = (edges.value as any[]).filter((e: Edge) => ids.has(e.source) && ids.has(e.target));
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
    let passes = 0;
    while (changed && passes++ < nodes.value.length) {
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
    groups,
    selectedGroupId,
    hoverNodeId,
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
    addNote,
    createGroup,
    removeGroup,
    toggleGroupCollapse,
    renameGroup,
    moveGroup,
    syncGroupBounds,
    groupStatusSummary,
    getUpstreamIds,
    getDownstreamIds,
    snapshot,
    undo,
    redo,
    clearHistory,
    copySelection,
    pasteClipboard,
    autoLayout
  };
});
