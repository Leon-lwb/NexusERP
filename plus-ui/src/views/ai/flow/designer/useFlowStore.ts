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
          targetHandle: e.targetHandle ?? 'inputs/main/0'
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
        markerEnd: 'arrowclosed'
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
    nodes.value = nodes.value.map((n) => ({
      ...n,
      data: { ...n.data, status: undefined }
    }));
  }

  return {
    nodes,
    edges,
    selectedNodeId,
    nodeStatusMap,
    currentInstanceId,
    flowName,
    flowId,
    selectedNode,
    reset,
    toConfigJson,
    fromConfigJson,
    setNodeStatus,
    clearNodeStatus
  };
});
