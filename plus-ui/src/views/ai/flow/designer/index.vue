<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { useFlowStore } from './useFlowStore';
import type { FlowCanvasNode } from './useFlowStore';
import NodePalette from './NodePalette.vue';
import FlowCanvas from './FlowCanvas.vue';
import NodeParameterForm from './NodeParameterForm.vue';
import JsonViewer from '../components/JsonViewer.vue';
import { getNodeTypeConfig, NODE_CATEGORIES, NODE_TYPE_CONFIGS } from '../nodeTypes';
import type { NodeParamSchema, NodeTypeMeta } from '@/api/ai/flow';
import {
  addFlow,
  getFlow,
  getFlowVersion,
  getInstance,
  listFlowVersions,
  listNodeRuns,
  listNodeTypes,
  nodeTest,
  publishFlow,
  restoreFlowVersion,
  runFlowTest,
  runFlowToNode,
  stopFlowRun,
  updateFlow
} from '@/api/ai/flow';
import type { AiFlowVersion } from '@/api/ai/flow';

/**
 * 流程设计器页面
 * <p>顶栏：返回/保存/发布/运行/停止；左侧节点面板；中间画布；右侧属性面板。
 * 运行态通过 ai-sse-message 事件总线消费（AI_FLOW_NODE_START/END/STATUS），节点实时高亮</p>
 */

const route = useRoute();
const router = useRouter();
const store = useFlowStore();

const loading = ref(false);
/**
 * 系统决策图仅使用本地展示坐标，不持久化画布编辑。
 */
const isSystemDecisionFlow = ref(false);
const saving = ref(false);
const running = ref(false);
// 流程级测试入参：持久化到 localStorage，刷新/重开设计器可复用（P11-C ③）
const runInput = ref(localStorage.getItem('aiFlowTestInput') ?? '{}');
const runOutput = ref('');
const runError = ref('');
/** 运行状态（面板展示：标签/耗时/节点统计/最终输出） */
const runStatus = ref<'QUEUED' | 'RUNNING' | 'WAITING' | 'STOP_REQUESTED' | 'RECOVERY_REQUIRED' | 'SUCCEEDED' | 'FAILED' | 'STOPPED' | null>(null);
const runDuration = ref<number | null>(null);
const runStats = ref<{ success: number; total: number } | null>(null);
const runFinalOutput = ref<string>('');
/** SSE 看门狗：距上次事件超过 15s 切轮询兜底 */
let watchdogTimer: number | null = null;
let pollTimer: number | null = null;
let lastSseTime = 0;

/** 运行状态标签 */
const runStatusLabel = computed(() => {
  const m: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '运行中',
    WAITING: '等待唤醒',
    STOP_REQUESTED: '正在停止',
    RECOVERY_REQUIRED: '待人工核对',
    SUCCEEDED: '成功',
    FAILED: '失败',
    STOPPED: '已停止'
  };
  return runStatus.value ? (m[runStatus.value] ?? runStatus.value) : '';
});

/** 运行状态标签颜色 */
const runTagType = computed<'info' | 'success' | 'danger' | 'warning'>(() => {
  const m: Record<string, 'info' | 'success' | 'danger' | 'warning'> = {
    RUNNING: 'info',
    SUCCEEDED: 'success',
    FAILED: 'danger',
    STOPPED: 'warning'
  };
  return runStatus.value ? (m[runStatus.value] ?? 'info') : 'info';
});

/** 耗时格式化 */
function fmtDuration(ms: number | null | undefined): string {
  if (ms == null) return '';
  return ms < 1000 ? ms + 'ms' : (ms / 1000).toFixed(1) + 's';
}
/** 节点类型元数据（schema 驱动表单） */
const nodeTypes = ref<NodeTypeMeta[]>([]);
/** 画布实例引用（删除节点） */
const canvasRef = ref();

/** 空画布引导：快速添加节点弹窗 */
const quickAddVisible = ref(false);
const quickAddKeyword = ref('');

/** 按分类分组的可添加节点（快速添加弹窗 + 空画布引导） */
const quickAddGroups = computed(() => {
  const kw = quickAddKeyword.value.trim().toLowerCase();
  const list = NODE_TYPE_CONFIGS.filter((c) => !kw || c.label.toLowerCase().includes(kw) || c.type.toLowerCase().includes(kw));
  return NODE_CATEGORIES.map((cat) => ({
    cat,
    items: list.filter((c) => c.category === cat.key)
  })).filter((g) => g.items.length);
});

/** 快速添加节点：加进画布并选中 */
function quickAdd(type: string) {
  const cfg = getNodeTypeConfig(type);
  if (!cfg) return;
  store.addNode(cfg.type, cfg.defaultConfig, cfg.label);
  quickAddVisible.value = false;
  quickAddKeyword.value = '';
}

/** 返回确认（未保存变更提示） */
async function goBack() {
  if (store.dirty) {
    try {
      await ElMessageBox.confirm('画布有未保存的修改，确定返回吗？', '未保存修改', {
        type: 'warning',
        confirmButtonText: '放弃修改',
        cancelButtonText: '留在页面'
      });
    } catch {
      return; // 取消返回
    }
  }
  router.push('/ai/flow/index');
}

/** 当前选中节点的类型配置（类型标签 + 说明） */
const nodeTypeConfig = computed(() => {
  const node = store.selectedNode;
  if (!node) return undefined;
  return getNodeTypeConfig(node.data.type);
});

/** 当前节点 schema（选中节点的类型参数定义） */
const currentNodeSchemas = computed<NodeParamSchema[]>(() => {
  const node = store.selectedNode;
  if (!node) return [];
  const meta = nodeTypes.value.find((t) => t.type === node.data.type);
  return meta?.params ?? [];
});

/** 上游节点列表（变量选择器候选：可直接引用其输出） */
const upstreamNodes = computed(() => {
  const node = store.selectedNode;
  if (!node) return [];
  // 通过入边找直接上游（递归一层：上游的上游也可引用，但 MVP 只列直接上游 + input）
  const sourceIds = new Set<string>();
  store.edges.forEach((e: any) => {
    if (e.target === node.id) sourceIds.add(e.source);
  });
  // 上游的上游（链式引用）
  let changed = true;
  while (changed) {
    changed = false;
    store.edges.forEach((e: any) => {
      if (sourceIds.has(e.target) && !sourceIds.has(e.source) && e.source !== node.id) {
        sourceIds.add(e.source);
        changed = true;
      }
    });
  }
  return store.nodes.filter((n) => sourceIds.has(n.id));
});

/** 节点高级设置是否展开（默认展开"节点配置"） */
const advancedExpanded = ref<string[]>(['config']);

/* ==================== 框选工具栏（多选后浮出：分组/删除） ==================== */

/** 当前框选节点（FlowCanvas 框选变化事件） */
const selectedNodes = ref<Array<{ id: string; data: { label?: string } }>>([]);

function onSelectionChange(nodes: any[]) {
  selectedNodes.value = nodes ?? [];
}

/** 框选分组（Ctrl+G 同逻辑） */
function groupSelected() {
  if (selectedNodes.value.length < 2) {
    ElMessage.info('请至少框选 2 个节点');
    return;
  }
  store.createGroup(selectedNodes.value.map((n) => n.id));
  selectedNodes.value = [];
}

/** 框选删除（画布已删，仅清状态） */
function removeSelected() {
  selectedNodes.value = [];
}

/* ==================== 快捷键帮助弹窗（? 键 / 顶栏按钮） ==================== */

const shortcutHelpVisible = ref(false);

/** 快捷键清单（与 FlowCanvas 的键盘事件 实现一致） */
const SHORTCUTS: Array<{ keys: string; desc: string }> = [
  { keys: 'Ctrl+S', desc: '保存流程' },
  { keys: 'Ctrl+Enter', desc: '测试运行流程' },
  { keys: 'Ctrl+C / Ctrl+V', desc: '复制 / 粘贴节点（含连边）' },
  { keys: 'Ctrl+D', desc: '复制选中节点' },
  { keys: 'Ctrl+A', desc: '全选节点' },
  { keys: 'Ctrl+G', desc: '框选节点分组' },
  { keys: 'Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y', desc: '撤销 / 重做' },
  { keys: 'Delete', desc: '删除选中节点（或解除选中分组）' },
  { keys: '↑ ↓ ← →', desc: '方向键在兄弟/上下游节点间导航' },
  { keys: 'Shift+← / Shift+→', desc: '批量选中全部上游 / 下游节点' },
  { keys: 'Shift+S', desc: '新建便利贴' },
  { keys: '?', desc: '打开快捷键帮助' }
];

/** 画布节点点击（单击选中；点击空白清空选中 + 框选工具栏 + 分组选中） */
function onCanvasNodeClick(n: any) {
  store.selectedNodeId = n ? n.id : null;
  if (!n) {
    store.selectedGroupId = null;
    selectedNodes.value = [];
  }
}

/** 节点 hover 工具栏动作（test/duplicate/delete；focus 已在 FlowCanvas 就地处理） */
function onNodeAction(payload: { nodeId: string; action: string }) {
  const node = store.nodes.find((n) => n.id === payload.nodeId);
  if (!node) return;
  store.selectedNodeId = payload.nodeId;
  if (payload.action === 'test') {
    openNodeTest();
  } else if (payload.action === 'duplicate') {
    duplicateNode();
  } else if (payload.action === 'delete') {
    canvasRef.value?.onRemoveNode(payload.nodeId);
  }
}

/** 单节点测试状态 */
const nodeTestVisible = ref(false);
const nodeTestMock = ref('{}');
const nodeTestResult = ref<string>('');
const nodeTestError = ref<string>('');
const nodeTestLoading = ref(false);

/** 打开单节点测试 */
function openNodeTest() {
  const node = store.selectedNode;
  if (!node) return;
  // 预填 mock：当前节点 config 里已引用的 input 变量
  nodeTestMock.value = '{}';
  nodeTestResult.value = '';
  nodeTestError.value = '';
  nodeTestVisible.value = true;
}

/** 执行单节点测试 */
async function runNodeTest() {
  const node = store.selectedNode;
  if (!node) return;
  if (!store.flowId) {
    ElMessage.warning('请先保存流程');
    return;
  }
  nodeTestLoading.value = true;
  nodeTestResult.value = '';
  nodeTestError.value = '';
  try {
    const res = await nodeTest(store.flowId, node.id, nodeTestMock.value.trim() || undefined);
    nodeTestResult.value = JSON.stringify(res.data ?? res, null, 2);
  } catch (e: any) {
    nodeTestError.value = e?.message ?? String(e);
  } finally {
    nodeTestLoading.value = false;
  }
}

/** 加载节点类型元数据 */
async function loadNodeTypes() {
  try {
    const res = await listNodeTypes();
    nodeTypes.value = res.data ?? [];
  } catch (e) {
    console.error('[Flow] 加载节点类型失败:', e);
  }
}

/** 加载流程（编辑模式） */
async function loadFlow(flowId: string | number) {
  loading.value = true;
  try {
    const res = await getFlow(flowId);
    const flow = res.data;
    isSystemDecisionFlow.value = typeof flow.flowKey === 'string' && flow.flowKey.startsWith('system_delivery_risk_');
    selectedNodes.value = [];
    store.flowName = flow.flowName;
    store.flowId = flow.id;
    if (flow.configJson) {
      store.clearHistory();
      store.fromConfigJson(flow.configJson, { displayOnlyLayout: isSystemDecisionFlow.value });
    }
    store.clearDirty();
    if (isSystemDecisionFlow.value) {
      await nextTick();
      canvasRef.value?.fitAllNodes();
    }
  } finally {
    loading.value = false;
  }
}

/** 必填参数校验：返回第一个缺失项（null=全部通过） */
function validateRequired(): { nodeId: string; nodeLabel: string; paramLabel: string } | null {
  for (const n of store.nodes) {
    const meta = nodeTypes.value.find((t) => t.type === n.data.type);
    if (!meta) continue;
    for (const p of meta.params ?? []) {
      if (!p.required) continue;
      const v = (n.data.config ?? {})[p.name];
      if (v === undefined || v === null || v === '' || (typeof v === 'object' && !Object.keys(v as object).length)) {
        return { nodeId: n.id, nodeLabel: n.data.label || n.id, paramLabel: p.label ?? p.name };
      }
    }
  }
  return null;
}

/** 保存流程 */
async function saveFlow(showTip = true) {
  if (isSystemDecisionFlow.value) {
    ElMessage.warning('系统托管的工业决策流程仅供查看，不能通过通用设计器保存');
    return false;
  }
  if (!store.flowName.trim()) {
    ElMessage.warning('请输入流程名称');
    return false;
  }
  // 必填参数校验：缺失时定位节点并展开配置面板
  const missing = validateRequired();
  if (missing) {
    store.selectedNodeId = missing.nodeId;
    advancedExpanded.value = ['config'];
    ElMessage.warning(`节点「${missing.nodeLabel}」缺少必填参数「${missing.paramLabel}」`);
    return false;
  }
  saving.value = true;
  try {
    const configJson = store.toConfigJson();
    const data = {
      id: store.flowId ?? undefined,
      flowName: store.flowName,
      configJson,
      triggerType: 'MANUAL',
      maxRetry: 1
    };
    let res;
    if (store.flowId) {
      res = await updateFlow(data);
    } else {
      res = await addFlow(data);
      store.flowId = res.data;
    }
    store.clearDirty();
    if (showTip) {
      ElMessage.success('保存成功');
    }
    return true;
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.message ?? e));
    return false;
  } finally {
    saving.value = false;
  }
}

/** 发布流程 */
async function publish() {
  if (!store.flowId) {
    ElMessage.warning('请先保存流程');
    return;
  }
  if (!(await saveFlow(false))) return;
  try {
    await publishFlow(store.flowId);
    ElMessage.success('发布成功（版本已+1）');
  } catch (e: any) {
    ElMessage.error('发布失败: ' + (e?.message ?? e));
  }
}

/** 测试运行流程（对齐 n8n 执行流程：无需发布，用画布当前配置） */
async function run() {
  if (!store.flowId) {
    ElMessage.warning('请先保存流程');
    return;
  }
  if (!(await saveFlow(false))) return;
  running.value = true;
  runStatus.value = 'RUNNING';
  runOutput.value = '';
  runError.value = '';
  runDuration.value = null;
  runStats.value = null;
  runFinalOutput.value = '';
  store.clearNodeStatus();
  try {
    // 传画布当前配置（即使保存失败/未保存也能测；后端做图 + 节点校验）
    const res = await runFlowTest(store.flowId, store.toConfigJson(), runInput.value.trim() || undefined);
    // 持久化测试入参（复用）
    localStorage.setItem('aiFlowTestInput', runInput.value.trim() || '{}');
    store.currentInstanceId = res.data;
    // SSE 看门狗：无事件超时后切轮询兜底（终态为准，SSE 仅加速）
    armWatchdog();
    ElMessage.success('测试运行已启动，实例ID: ' + res.data);
  } catch (e: any) {
    running.value = false;
    runStatus.value = null;
    ElMessage.error('运行失败: ' + (e?.message ?? e));
  }
}

/** 停止流程 */
async function stop() {
  if (!store.currentInstanceId) return;
  try {
    await stopFlowRun(store.currentInstanceId);
    ElMessage.info('已请求停止（节点边界生效）');
  } catch (e: any) {
    ElMessage.warning(e?.message ?? '停止失败');
  }
}

/** 清理运行定时器 */
function clearTimers() {
  if (watchdogTimer !== null) {
    window.clearInterval(watchdogTimer);
    watchdogTimer = null;
  }
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** SSE 看门狗：每 5s 检查，距上次事件 >15s 或 SSE 关闭时启动轮询 */
function armWatchdog() {
  clearTimers();
  lastSseTime = Date.now();
  const sseOff = import.meta.env.VITE_APP_SSE === 'false';
  if (sseOff) {
    startPolling();
    return;
  }
  watchdogTimer = window.setInterval(() => {
    if (!running.value) {
      clearTimers();
      return;
    }
    if (Date.now() - lastSseTime > 15000) {
      startPolling();
    }
  }, 5000);
}

/** 轮询兜底：每 2s 查实例详情直到终态 */
function startPolling() {
  if (pollTimer !== null) return;
  pollTimer = window.setInterval(async () => {
    if (!running.value || !store.currentInstanceId) {
      clearTimers();
      return;
    }
    try {
      const res = await getInstance(store.currentInstanceId);
      const inst = res.data;
      if (inst) {
        runStatus.value = inst.status;
        if (!['QUEUED', 'RUNNING', 'WAITING', 'STOP_REQUESTED'].includes(inst.status)) applyInstanceResult(inst);
      }
    } catch {
      // 轮询异常忽略（SSE 正常时轮询仅兜底）
    }
  }, 2000);
}

/** 应用实例终态结果（SSE AI_FLOW_STATUS 与轮询共用；终态为准） */
async function applyInstanceResult(inst: any) {
  if (['QUEUED', 'RUNNING', 'WAITING', 'STOP_REQUESTED'].includes(inst.status)) {
    runStatus.value = inst.status;
    startPolling();
    return;
  }
  clearTimers();
  running.value = false;
  runStatus.value = inst.status;
  runDuration.value = inst.durationMs ?? null;
  runStats.value = inst.nodeTotal ? { success: inst.nodeSuccess ?? 0, total: inst.nodeTotal } : null;
  if (inst.outputJson) {
    runFinalOutput.value = inst.outputJson;
  }
  if (inst.status === 'SUCCEEDED') {
    runOutput.value = '流程执行成功';
    ElMessage.success('流程执行成功');
  } else if (inst.status === 'FAILED') {
    runError.value = inst.errorMsg ?? '流程执行失败';
    ElMessage.error('流程执行失败: ' + (inst.errorMsg ?? ''));
  } else if (inst.status === 'RECOVERY_REQUIRED') {
    runError.value = inst.errorMsg ?? '执行结果待人工核对';
    ElMessage.warning('执行结果待人工核对');
  } else if (inst.status === 'STOPPED') {
    runOutput.value = '流程已停止';
    ElMessage.info('流程已停止');
  }
  // 缓存节点真实输出（变量选择器运行时数据优先）
  try {
    const runsRes = await listNodeRuns(store.currentInstanceId);
    const outputs: Record<string, unknown> = {};
    for (const r of runsRes.data ?? []) {
      if (r.outputJson) {
        try {
          outputs[r.nodeId] = JSON.parse(r.outputJson);
        } catch {
          /* 忽略非法 JSON */
        }
      }
    }
    store.setNodeOutputs(Object.keys(outputs).length ? outputs : null);
    // 记忆上次运行实例（NDV 回退查看用）
    localStorage.setItem('aiFlowLastInstanceId', String(store.currentInstanceId));
  } catch {
    // 缓存失败不影响主流程
  }
}

/** 查看实例详情（跳转历史详情页） */
function viewDetail() {
  if (!store.currentInstanceId) return;
  router.push({
    path: '/ai/flow/monitor/detail',
    query: { instanceId: String(store.currentInstanceId), flowId: String(store.flowId ?? '') }
  });
}

/** 自动布局（BFS 分层） */
function autoLayout() {
  if (isSystemDecisionFlow.value || !store.nodes.length) return;
  store.snapshot();
  store.autoLayout();
  store.markDirty();
  ElMessage.success('已自动布局');
}

/** SSE 事件消费（流程运行态高亮 + 节点输出摘要） */
function onSseMessage(e: Event) {
  const detail = (e as CustomEvent).detail;
  if (!detail || !detail.type) return;
  // 只处理当前实例的事件
  if (detail.content) {
    let payload: any = null;
    try {
      payload = JSON.parse(detail.content);
    } catch {
      return;
    }
    if (payload.instanceId !== store.currentInstanceId) return;

    // 任意事件刷新看门狗
    lastSseTime = Date.now();

    if (detail.type === 'AI_FLOW_NODE_START') {
      store.setNodeStatus(payload.nodeId, 'RUNNING');
    } else if (detail.type === 'AI_FLOW_NODE_END') {
      store.setNodeStatus(
        payload.nodeId,
        ['SUCCEEDED', 'FAILED', 'SKIPPED', 'CANCELLED', 'RECOVERY_REQUIRED'].includes(payload.status) ? payload.status : 'FAILED'
      );
      // 节点输出摘要（后端 500 字符截断），显示在节点卡片底部
      if (payload.status === 'SUCCEEDED' && payload.outputSummary) {
        store.setNodeSummary(payload.nodeId, String(payload.outputSummary));
      } else if (payload.errorMsg) {
        store.setNodeSummary(payload.nodeId, '失败: ' + payload.errorMsg);
      }
    } else if (detail.type === 'AI_FLOW_STATUS') {
      // 终态：SSE 与轮询共用同一终态处理（终态为准）
      applyInstanceResult(payload);
    }
  }
}

/** 节点参数值更新（写回 store 的 config）——就地修改共享 data（VueFlow 受控同步依赖共享引用） */
function onConfigUpdate(value: unknown) {
  const node = store.selectedNode;
  if (!node) return;
  node.data.config = value as Record<string, unknown>;
  store.markDirty();
}

/** 更新节点名称 */
function onLabelUpdate(label: string) {
  const node = store.selectedNode;
  if (!node) return;
  node.data.label = label;
  store.markDirty();
}

/** 画布节点/边变更（连线/拖入/拖拽/删除/粘贴/撤销等）→ 标记未保存 */
function onNodesChange(nodes: any) {
  store.nodes = nodes;
  store.markDirty();
}
function onEdgesChange(edges: any) {
  store.edges = edges;
  store.markDirty();
}

/** 复制当前选中节点（含关联边） */
function duplicateNode() {
  const node = store.selectedNode;
  if (!node) return;
  store.copySelection([node]);
  const result = store.pasteClipboard();
  if (!result) return;
  store.snapshot();
  store.nodes = [...store.nodes, ...result.nodes];
  store.edges = [...(store.edges as any[]).concat(result.edges)];
  store.selectedNodeId = result.nodes[0]?.id ?? null;
  store.markDirty();
  ElMessage.success('已复制节点');
}

/* ==================== NDV：三栏式节点数据查看器（双击节点） ==================== */

/** NDV 抽屉可见性 */
const ndvVisible = ref(false);
/** NDV 目标节点 */
const ndvNode = ref<{ id: string; label: string; runs: any[] } | null>(null);
/** 当前选中的 Run 序号（循环批次切换） */
const ndvRunIndex = ref(0);

/** 当前选中的 Run */
const ndvRun = computed(() => ndvNode.value?.runs?.[ndvRunIndex.value] ?? null);

/** Run 选择器选项（多批次时展示 batchIndex） */
const ndvRunOptions = computed(() => {
  const runs = ndvNode.value?.runs ?? [];
  return runs.map((r: any, i: number) => ({
    label: `批次 ${r.batchIndex ?? 0} · ${r.status} · ${fmtDuration(r.durationMs)}`,
    value: i
  }));
});

/** 双击节点：拉取该节点全部运行快照（当前实例优先，无则回退上次运行实例） */
async function onNodeDoubleClick(node: any) {
  let instanceId = store.currentInstanceId;
  if (!instanceId) {
    instanceId = localStorage.getItem('aiFlowLastInstanceId');
    if (instanceId) {
      ElMessage.info('展示上次运行的数据（未运行过本次变更）');
    } else {
      ElMessage.info('请先运行流程后再双击查看节点数据');
      return;
    }
  }
  try {
    const res = await listNodeRuns(instanceId);
    const runs = res.data ?? [];
    // 取该节点全部运行记录（循环批次；按起始时间排序）
    const nodeRuns = runs.filter((r: any) => r.nodeId === node.id);
    if (!nodeRuns.length) {
      ndvNode.value = { id: node.id, label: node.data.label || node.id, runs: [] };
      ndvVisible.value = true;
      return;
    }
    ndvNode.value = { id: node.id, label: node.data.label || node.id, runs: nodeRuns };
    ndvRunIndex.value = nodeRuns.length - 1;
    ndvVisible.value = true;
  } catch (e: any) {
    ElMessage.error('获取节点数据失败: ' + (e?.message ?? e));
  }
}

/** NDV 快照解析 */
function parseNdvJson(text?: string): unknown {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** 当前选中节点的真实运行输出（变量选择器运行时数据） */
const ndvRealOutput = computed(() => {
  const node = ndvNode.value;
  if (!node || !node.id) return {};
  const out = store.nodeOutputs?.[node.id];
  return out && typeof out === 'object' ? (out as Record<string, unknown>) : {};
});

/** NDV 中栏参数 schema（按节点 id 求，模板内用） */
function CurrentNdvSchemas(nodeId: string): NodeParamSchema[] {
  const node = store.nodes.find((n) => n.id === nodeId);
  if (!node) return [];
  const meta = nodeTypes.value.find((t) => t.type === node.data.type);
  return meta?.params ?? [];
}

/** NDV 中栏上游节点（变量选择器候选，按节点 id 求） */
function NdvUpstreamNodes(nodeId: string): FlowCanvasNode[] {
  const sourceIds = new Set<string>();
  store.edges.forEach((e: any) => {
    if (e.target === nodeId) sourceIds.add(e.source);
  });
  let changed = true;
  while (changed) {
    changed = false;
    store.edges.forEach((e: any) => {
      if (sourceIds.has(e.target) && !sourceIds.has(e.source) && e.source !== nodeId) {
        sourceIds.add(e.source);
        changed = true;
      }
    });
  }
  return store.nodes.filter((n) => sourceIds.has(n.id));
}

/** 部分执行：运行到当前选中节点（含上游） */
async function runToNode() {
  const node = store.selectedNode;
  if (!node) {
    ElMessage.warning('请先选中节点');
    return;
  }
  if (!store.flowId) {
    ElMessage.warning('请先保存流程');
    return;
  }
  if (!(await saveFlow(false))) return;
  running.value = true;
  runStatus.value = 'RUNNING';
  runOutput.value = '';
  runError.value = '';
  runDuration.value = null;
  runStats.value = null;
  runFinalOutput.value = '';
  store.clearNodeStatus();
  try {
    const res = await runFlowToNode(store.flowId, node.id, store.toConfigJson(), runInput.value.trim() || undefined);
    store.currentInstanceId = res.data;
    // 持久化测试入参（复用）
    localStorage.setItem('aiFlowTestInput', runInput.value.trim() || '{}');
    armWatchdog();
    ElMessage.success('已启动运行到节点，实例ID: ' + res.data);
  } catch (e: any) {
    running.value = false;
    runStatus.value = null;
    ElMessage.error('运行失败: ' + (e?.message ?? e));
  }
}

/** 保存测试入参（复用） */
function saveTestInput() {
  localStorage.setItem('aiFlowTestInput', runInput.value.trim() || '{}');
  ElMessage.success('已保存测试入参');
}

/* ==================== 版本历史（对齐 n8n 版本历史：列表/只读预览/恢复） ==================== */

/** 版本历史抽屉可见性 */
const versionDrawerVisible = ref(false);
const versions = ref<AiFlowVersion[]>([]);
const versionLoading = ref(false);
const restoringVersionId = ref<string | number | null>(null);

/** 打开版本历史抽屉 */
async function openVersionHistory() {
  if (!store.flowId) {
    ElMessage.warning('请先保存流程');
    return;
  }
  versionDrawerVisible.value = true;
  await loadVersions();
}

async function loadVersions() {
  if (!store.flowId) return;
  versionLoading.value = true;
  try {
    const res = await listFlowVersions(store.flowId);
    versions.value = res.data ?? [];
  } catch (e: any) {
    ElMessage.error('加载版本历史失败: ' + (e?.message ?? e));
  } finally {
    versionLoading.value = false;
  }
}

/** 版本只读预览（独立 nodes/edges refs，不触碰当前画布 store） */
const versionPreviewVisible = ref(false);
const versionPreviewLoading = ref(false);
const previewNodes = ref<any[]>([]);
const previewEdges = ref<any[]>([]);
const previewMeta = ref<AiFlowVersion | null>(null);

/** 解析版本 configJson 为只读画布数据（与 store.fromConfigJson 同结构，但不写 store） */
function parseConfigForPreview(configJson: string) {
  const data = JSON.parse(configJson);
  const rawNodes = Array.isArray(data.nodes) ? data.nodes : [];
  const rawEdges = Array.isArray(data.edges) ? data.edges : [];
  previewNodes.value = rawNodes.map((n: Record<string, any>) => ({
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
  previewEdges.value = rawEdges.map((e: Record<string, any>) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    sourceHandle: e.sourceHandle,
    targetHandle: e.targetHandle,
    markerEnd: 'arrowclosed',
    ...(e.loop ? { loop: true } : {})
  }));
}

/** 版本只读预览（读详情接口拿 configJson → 独立画布渲染） */
async function previewVersion(version: AiFlowVersion) {
  versionPreviewLoading.value = true;
  versionPreviewVisible.value = true;
  previewMeta.value = version;
  try {
    const res = await getFlowVersion(version.id);
    const detail = res.data;
    parseConfigForPreview(detail?.configJson ?? '{"nodes":[],"edges":[]}');
  } catch (e: any) {
    ElMessage.error('加载版本详情失败: ' + (e?.message ?? e));
    previewNodes.value = [];
    previewEdges.value = [];
  } finally {
    versionPreviewLoading.value = false;
  }
}

/** 恢复版本（写回流程定义，状态置 DRAFT 需重新发布生效） */
async function restoreVersion(version: AiFlowVersion) {
  try {
    await ElMessageBox.confirm(
      `确认恢复到 v${version.flowVersion}？恢复后当前画布配置将被该版本覆盖，流程状态转为草稿，需重新发布后生效。`,
      '恢复版本',
      { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消' }
    );
  } catch {
    return; // 取消
  }
  restoringVersionId.value = version.id;
  try {
    await restoreFlowVersion(version.id);
    ElMessage.success('已恢复为 v' + version.flowVersion + '（状态置 DRAFT，请检查后重新发布）');
    versionDrawerVisible.value = false;
    // 重新加载画布（恢复后的配置）+ 刷新版本列表
    await loadFlow(String(store.flowId));
    await loadVersions();
  } catch (e: any) {
    ElMessage.error('恢复失败: ' + (e?.message ?? e));
  } finally {
    restoringVersionId.value = null;
  }
}

onMounted(async () => {
  await loadNodeTypes();
  const flowId = route.query.id;
  if (flowId) {
    await loadFlow(String(flowId));
  }
  window.addEventListener('ai-sse-message', onSseMessage);
});

onBeforeUnmount(() => {
  window.removeEventListener('ai-sse-message', onSseMessage);
  clearTimers();
});
</script>

<template>
  <div class="flow-designer">
    <!-- 顶栏 -->
    <div class="flow-designer__toolbar">
      <el-button @click="goBack">返回</el-button>
      <el-input v-model="store.flowName" :disabled="isSystemDecisionFlow" placeholder="流程名称" class="flow-designer__name" maxlength="50">
        <template #suffix>
          <el-tooltip v-if="store.dirty" content="有未保存的修改" placement="bottom">
            <span class="flow-designer__dirty-dot" />
          </el-tooltip>
        </template>
      </el-input>
      <div class="flow-designer__spacer" />
      <el-tag v-if="isSystemDecisionFlow" type="info" effect="plain">系统托管 · 只读预览</el-tag>
      <el-button :disabled="isSystemDecisionFlow" @click="quickAddVisible = true">+ 添加节点</el-button>
      <el-button :disabled="isSystemDecisionFlow" @click="store.addNote()">便利贴</el-button>
      <el-button :loading="saving" :disabled="isSystemDecisionFlow" @click="saveFlow()">保存</el-button>
      <el-button type="primary" :disabled="isSystemDecisionFlow" @click="publish">发布</el-button>
      <el-button @click="openVersionHistory">版本历史</el-button>
      <el-button :disabled="isSystemDecisionFlow" @click="autoLayout">自动布局</el-button>
      <el-button type="success" :disabled="isSystemDecisionFlow || running" @click="run">测试运行</el-button>
      <el-button type="danger" :disabled="!running || !store.currentInstanceId" @click="stop"> 停止 </el-button>
      <el-tooltip content="快捷键帮助 (?)" placement="bottom">
        <el-button circle @click="shortcutHelpVisible = true">?</el-button>
      </el-tooltip>
    </div>

    <div class="flow-designer__body">
      <!-- 左：节点面板 -->
      <NodePalette v-if="!isSystemDecisionFlow" />
      <!-- 中：画布 -->
      <div class="flow-designer__canvas-wrap">
        <FlowCanvas
          ref="canvasRef"
          :nodes="store.nodes"
          :edges="store.edges"
          :readonly="isSystemDecisionFlow"
          @update:nodes="onNodesChange"
          @update:edges="onEdgesChange"
          @node-click="onCanvasNodeClick"
          @node-dblclick="onNodeDoubleClick"
          @selection-change="onSelectionChange"
          @node-action="onNodeAction"
          @shortcut-help="shortcutHelpVisible = true"
        />
        <!-- 框选工具栏（≥2 节点时浮出：分组/删除） -->
        <div v-if="!isSystemDecisionFlow && selectedNodes.length >= 2" class="flow-designer__selection-toolbar">
          <span class="flow-designer__selection-count">{{ selectedNodes.length }} 个节点已选</span>
          <el-button type="primary" plain size="small" @click="groupSelected">分组 (Ctrl+G)</el-button>
          <el-button type="danger" plain size="small" @click="removeSelected">删除</el-button>
        </div>
        <!-- 空画布引导（对齐 n8n 空白画布） -->
        <div v-if="!store.nodes.length && !loading" class="flow-designer__canvas-empty">
          <div class="flow-designer__canvas-empty-box">
            <div class="flow-designer__canvas-empty-title">开始构建你的流程</div>
            <div class="flow-designer__canvas-empty-desc">添加第一个节点，或用示例流程快速开始</div>
            <el-button type="primary" size="large" @click="quickAddVisible = true">＋ 添加第一个节点</el-button>
            <div class="flow-designer__canvas-empty-hint">也可以从左侧节点面板拖拽节点到画布 · 快捷键 Ctrl+Z 撤销 / Ctrl+C+V 复制粘贴</div>
          </div>
        </div>
      </div>
      <!-- 右：属性面板 -->
      <div class="flow-designer__property">
        <template v-if="store.selectedNode">
          <div class="flow-designer__property-header">
            <el-input :model-value="store.selectedNode.data.label" placeholder="节点名称" @input="onLabelUpdate" />
          </div>
          <div class="flow-designer__property-desc">
            <el-tag size="small" :color="nodeTypeConfig?.color" effect="dark" class="flow-designer__property-tag">{{
              nodeTypeConfig?.label ?? store.selectedNode.data.type
            }}</el-tag>
            <span class="flow-designer__property-desc-text">{{ nodeTypeConfig?.description ?? '自定义节点' }}</span>
          </div>
          <el-collapse v-model="advancedExpanded">
            <el-collapse-item title="节点配置" name="config">
              <div v-if="currentNodeSchemas.length" class="flow-designer__property-form">
                <NodeParameterForm
                  v-for="schema in currentNodeSchemas"
                  :key="schema.name"
                  :schema="schema"
                  :model-value="store.selectedNode.data.config?.[schema.name] ?? schema.defaultValue"
                  :node-type="store.selectedNode.data.type"
                  :upstream-nodes="upstreamNodes"
                  :node-outputs="store.nodeOutputs"
                  :config="store.selectedNode.data.config"
                  @update:model-value="
                    (v) => {
                      const config = { ...(store.selectedNode.data.config ?? {}) };
                      config[schema.name] = v;
                      onConfigUpdate(config);
                    }
                  "
                />
              </div>
              <el-empty v-else description="该节点暂无参数配置" :image-size="60" />
            </el-collapse-item>
            <el-collapse-item title="高级设置" name="advanced">
              <el-form label-width="110px">
                <el-form-item label="失败策略">
                  <el-select
                    :model-value="store.selectedNode.data.onError ?? 'STOP'"
                    @update:model-value="(v) => store.setNodeMeta(store.selectedNode?.id, 'onError', v)"
                    style="width: 100%"
                  >
                    <el-option label="STOP（停止流程）" value="STOP" />
                    <el-option label="CONTINUE（透传继续）" value="CONTINUE" />
                    <el-option label="ERROR_PORT（错误分支）" value="ERROR_PORT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="失败重试">
                  <el-switch
                    :model-value="store.selectedNode.data.retryOnFail ?? false"
                    @update:model-value="(v) => store.setNodeMeta(store.selectedNode?.id, 'retryOnFail', Boolean(v))"
                  />
                </el-form-item>
                <el-form-item label="重试次数">
                  <el-input-number
                    :model-value="store.selectedNode.data.retryMaxTries ?? 3"
                    :min="2"
                    :max="5"
                    @update:model-value="(v) => store.setNodeMeta(store.selectedNode?.id, 'retryMaxTries', v)"
                  />
                </el-form-item>
                <el-form-item label="超时(秒)">
                  <el-input-number
                    :model-value="store.selectedNode.data.timeoutSeconds ?? 600"
                    :min="1"
                    @update:model-value="(v) => store.setNodeMeta(store.selectedNode?.id, 'timeoutSeconds', v)"
                  />
                </el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>
          <div class="flow-designer__actions">
            <el-button type="primary" plain size="small" @click="openNodeTest">测试此节点</el-button>
            <el-button type="success" plain size="small" @click="runToNode">运行到节点</el-button>
            <el-button plain size="small" @click="duplicateNode">复制</el-button>
            <el-button type="danger" plain size="small" @click="canvasRef?.onRemoveNode(store.selectedNode.id)"> 删除节点 </el-button>
          </div>
        </template>
        <el-empty v-else description="选择节点查看配置" :image-size="60" />
      </div>
    </div>

    <!-- 底部：运行面板（常驻，运行前可输入入参，运行中/后展示状态与结果） -->
    <div class="flow-designer__run-panel">
      <div class="flow-designer__run-input">
        <span class="flow-designer__run-label">运行入参 (JSON)</span>
        <el-input v-model="runInput" type="textarea" :rows="2" placeholder='{"name":"张三","amount":500}' />
        <el-button size="small" @click="saveTestInput">保存测试入参</el-button>
      </div>
      <div v-if="runStatus" class="flow-designer__run-info">
        <el-tag size="small" :type="runTagType">{{ runStatusLabel }}</el-tag>
        <span v-if="runDuration !== null" class="flow-designer__run-sub">耗时 {{ fmtDuration(runDuration) }}</span>
        <span v-if="runStats" class="flow-designer__run-sub">节点 {{ runStats.success }}/{{ runStats.total }} 成功</span>
        <el-button v-if="!running" link type="primary" size="small" @click="viewDetail"> 查看详情 </el-button>
      </div>
      <div v-if="runOutput" class="flow-designer__run-result">✅ {{ runOutput }}</div>
      <div v-if="runError" class="flow-designer__run-error">❌ {{ runError }}</div>
      <el-collapse v-if="runFinalOutput && runStatus === 'SUCCEEDED'" class="flow-designer__run-final">
        <el-collapse-item title="最终输出 (outputJson)" name="final">
          <pre class="flow-designer__run-final-pre">{{ runFinalOutput }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- NDV：三栏式节点数据查看器（双击节点，对齐 n8n NDV） -->
    <el-drawer v-model="ndvVisible" :title="'节点数据: ' + (ndvNode?.label ?? '')" size="92%" :with-header="false">
      <template #default>
        <div class="flow-designer__ndv">
          <!-- 顶栏：节点信息 + Run 选择器 + 操作 -->
          <div class="flow-designer__ndv-topbar">
            <div class="flow-designer__ndv-title">
              <span class="flow-designer__ndv-name">{{ ndvNode?.label }}</span>
              <el-tag v-if="ndvRun" size="small" :type="ndvRun.status === 'SUCCEEDED' ? 'success' : ndvRun.status === 'FAILED' ? 'danger' : 'info'">
                {{ ndvRun.status }}
              </el-tag>
            </div>
            <div class="flow-designer__ndv-actions">
              <el-select
                v-if="ndvRunOptions.length"
                :model-value="ndvRunIndex"
                size="small"
                style="width: 200px"
                @update:model-value="(v) => (ndvRunIndex = Number(v))"
              >
                <el-option v-for="opt in ndvRunOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-button size="small" @click="ndvVisible = false">关闭</el-button>
            </div>
          </div>

          <!-- 三栏主体 -->
          <div class="flow-designer__ndv-body">
            <!-- 左栏：输入数据 -->
            <div class="flow-designer__ndv-col">
              <div class="flow-designer__ndv-col-title">输入数据</div>
              <div v-if="ndvRun" class="flow-designer__ndv-col-body">
                <JsonViewer :data="parseNdvJson(ndvRun.inputJson)" />
              </div>
              <el-empty v-else description="暂无输入数据" :image-size="50" />
            </div>

            <!-- 中栏：参数表单（复用节点配置，编辑当前画布节点） -->
            <div class="flow-designer__ndv-col">
              <div class="flow-designer__ndv-col-title">节点参数</div>
              <div class="flow-designer__ndv-col-body">
                <template v-if="ndvNode && store.nodes.find((n) => n.id === ndvNode.id)">
                  <NodeParameterForm
                    v-for="schema in CurrentNdvSchemas(ndvNode.id)"
                    :key="schema.name"
                    :schema="schema"
                    :model-value="store.nodes.find((n) => n.id === ndvNode.id)?.data.config?.[schema.name] ?? schema.defaultValue"
                    :node-type="store.nodes.find((n) => n.id === ndvNode.id)?.data.type"
                    :upstream-nodes="NdvUpstreamNodes(ndvNode.id)"
                    :node-outputs="ndvRealOutput"
                    :config="store.nodes.find((n) => n.id === ndvNode.id)?.data.config"
                    @update:model-value="
                      (v) => {
                        const node = store.nodes.find((n) => n.id === ndvNode.id);
                        if (!node) return;
                        const config = { ...(node.data.config ?? {}) };
                        config[schema.name] = v;
                        node.data.config = config;
                        store.markDirty();
                      }
                    "
                  />
                </template>
                <el-empty v-else description="当前画布无此节点" :image-size="50" />
              </div>
            </div>

            <!-- 右栏：输出数据 -->
            <div class="flow-designer__ndv-col">
              <div class="flow-designer__ndv-col-title">输出数据</div>
              <div v-if="ndvRun" class="flow-designer__ndv-col-body">
                <JsonViewer :data="parseNdvJson(ndvRun.outputJson)" />
              </div>
              <el-alert v-if="ndvRun?.errorMsg" :title="ndvRun.errorMsg" type="error" :closable="false" class="flow-designer__ndv-error" />
              <el-empty v-else-if="!ndvRun" description="该节点没有运行数据（可能被跳过或未执行）" :image-size="50" />
            </div>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 单节点测试抽屉 -->
    <el-drawer v-model="nodeTestVisible" :title="'测试节点: ' + (store.selectedNode?.data.label ?? '')" size="460px">
      <el-form label-width="80px">
        <el-form-item label="Mock 状态">
          <el-input
            v-model="nodeTestMock"
            type="textarea"
            :rows="6"
            placeholder='{"input":{"name":"张三","amount":500},"nodes":{"node_llm_1":{"output":{"content":"..."}}}}'
          />
          <div class="flow-designer__mock-tip">可 mock 运行入参 input 与上游节点输出 nodes.&lt;节点ID&gt;.output，供本节点 {{}} 变量引用</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="nodeTestLoading" @click="runNodeTest">运行测试</el-button>
        </el-form-item>
      </el-form>
      <div v-if="nodeTestResult" class="flow-designer__test-result">
        <div class="flow-designer__test-label">输出:</div>
        <JsonViewer :data="parseNdvJson(nodeTestResult)" />
      </div>
      <el-alert v-if="nodeTestError" :title="nodeTestError" type="error" :closable="false" />
    </el-drawer>

    <!-- 快速添加节点弹窗（空画布引导 / 顶栏按钮） -->
    <el-dialog v-model="quickAddVisible" title="添加节点" width="500px" @closed="quickAddKeyword = ''">
      <el-input v-model="quickAddKeyword" placeholder="搜索节点类型或名称..." clearable :prefix-icon="Search" class="flow-designer__quick-search" />
      <div class="flow-designer__quick-list">
        <template v-for="g in quickAddGroups" :key="g.cat.key">
          <div class="flow-designer__quick-group">{{ g.cat.label }}</div>
          <div v-for="cfg in g.items" :key="cfg.type" class="flow-designer__quick-item" @click="quickAdd(cfg.type)">
            <el-icon :style="{ color: cfg.color }" class="flow-designer__quick-icon">
              <component :is="cfg.icon" />
            </el-icon>
            <div class="flow-designer__quick-text">
              <div class="flow-designer__quick-label">{{ cfg.label }}</div>
              <div class="flow-designer__quick-desc">{{ cfg.description }}</div>
            </div>
          </div>
        </template>
        <div v-if="!quickAddGroups.length" class="flow-designer__quick-empty">无匹配节点</div>
      </div>
    </el-dialog>

    <!-- 快捷键帮助弹窗 -->
    <el-dialog v-model="shortcutHelpVisible" title="快捷键" width="500px">
      <el-table :data="SHORTCUTS" border size="small">
        <el-table-column label="快捷键" prop="keys" width="200">
          <template #default="{ row }">
            <code class="flow-designer__shortcut-keys">{{ row.keys }}</code>
          </template>
        </el-table-column>
        <el-table-column label="功能" prop="desc" />
      </el-table>
    </el-dialog>

    <!-- 版本历史抽屉（列表/恢复，对齐 n8n 版本历史） -->
    <el-drawer v-model="versionDrawerVisible" title="版本历史" size="520px">
      <div v-loading="versionLoading" class="flow-designer__versions">
        <el-alert
          title="每次发布生成一个版本快照。可查看历史版本画布（只读预览），或恢复某个版本——恢复后流程转为草稿，需重新发布生效。"
          type="info"
          :closable="false"
          class="flow-designer__versions-tip"
        />
        <el-table :data="versions" border stripe size="small">
          <el-table-column label="版本" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="primary">v{{ row.flowVersion }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" width="150">
            <template #default="{ row }">{{ row.createTime ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="previewVersion(row)">预览</el-button>
              <el-button link type="warning" size="small" :loading="restoringVersionId === row.id" @click="restoreVersion(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!versionLoading && !versions.length" description="暂无版本记录（发布流程后生成快照）" :image-size="60" />
      </div>
    </el-drawer>

    <!-- 版本只读预览（独立画布，不触碰当前编辑画布） -->
    <el-drawer v-model="versionPreviewVisible" :title="'版本预览: v' + (previewMeta?.flowVersion ?? '')" size="70%">
      <div v-loading="versionPreviewLoading" class="flow-designer__version-preview">
        <div class="flow-designer__version-preview-header">
          <span class="flow-designer__version-preview-meta">
            {{ previewMeta?.flowName ?? '流程' }} · 发布 {{ previewMeta?.createTime ?? '-' }} · {{ previewNodes.length }} 个节点 /
            {{ previewEdges.length }} 条连线
          </span>
          <el-button size="small" @click="versionPreviewVisible = false">关闭</el-button>
        </div>
        <div class="flow-designer__version-preview-canvas">
          <FlowCanvas :nodes="previewNodes" :edges="previewEdges" readonly />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
}

.flow-designer__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.flow-designer__name {
  width: 220px;
}

/* 未保存标记（输入框尾部小圆点） */
.flow-designer__dirty-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  animation: flow-dirty-blink 1.2s ease-in-out infinite;
}

@keyframes flow-dirty-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

.flow-designer__spacer {
  flex: 1;
}

/* 框选工具栏（画布顶部浮出） */
.flow-designer__selection-toolbar {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.flow-designer__selection-count {
  font-size: 12px;
  color: #606266;
}

/* 画布容器（空画布引导层定位基准） */
.flow-designer__canvas-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
}

/* 空画布引导（对齐 n8n 空白画布） */
.flow-designer__canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  z-index: 5;
}

.flow-designer__canvas-empty-box {
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 40px 56px;
  border: 2px dashed #c0c4cc;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.75);
}

.flow-designer__canvas-empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.flow-designer__canvas-empty-desc {
  font-size: 13px;
  color: #909399;
}

.flow-designer__canvas-empty-hint {
  font-size: 12px;
  color: #c0c4cc;
}

/* 属性面板：节点类型说明 */
.flow-designer__property-desc {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 0 12px 10px;
  border-bottom: 1px solid #e4e7ed;
}

/* 节点类型标签：深色底 + 白色文字（原 plain 浅底蓝字与绿底对比不佳） */
.flow-designer__property-tag {
  color: #fff !important;
  border-color: transparent !important;
}

.flow-designer__property-desc-text {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  flex: 1;
}

/* 快速添加节点弹窗 */
.flow-designer__quick-search {
  margin-bottom: 10px;
}

.flow-designer__quick-list {
  max-height: 420px;
  overflow-y: auto;
}

.flow-designer__quick-group {
  font-size: 12px;
  color: #909399;
  font-weight: 600;
  padding: 8px 4px 4px;
}

.flow-designer__quick-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.flow-designer__quick-item:hover {
  background: #f0f7ff;
}

.flow-designer__quick-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.flow-designer__quick-text {
  min-width: 0;
}

.flow-designer__quick-label {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.flow-designer__quick-desc {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.flow-designer__quick-empty {
  padding: 24px 0;
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
}

.flow-designer__shortcut-keys {
  font-size: 12px;
  color: #409eff;
  background: #f0f7ff;
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.flow-designer__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.flow-designer__property {
  width: 300px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  padding: 12px;
  overflow-y: auto;
}

.flow-designer__property-header {
  margin-bottom: 12px;
}

.flow-designer__property-form {
  padding: 4px 0;
}

.flow-designer__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.flow-designer__run-panel {
  padding: 8px 12px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.flow-designer__run-input {
  flex: 1;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.flow-designer__run-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  padding-top: 4px;
}

.flow-designer__run-result {
  color: #52c41a;
  font-size: 13px;
  padding-top: 4px;
  white-space: pre-wrap;
  max-width: 400px;
}

.flow-designer__run-error {
  color: #fa541c;
  font-size: 13px;
  padding-top: 4px;
  white-space: pre-wrap;
  max-width: 400px;
}

.flow-designer__run-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 2px;
  white-space: nowrap;
}

.flow-designer__run-sub {
  font-size: 12px;
  color: #909399;
}

.flow-designer__run-final {
  width: 300px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fafafa;
}

.flow-designer__run-final :deep(.el-collapse-item__header) {
  font-size: 12px;
  padding-left: 8px;
  background: #fafafa;
}

.flow-designer__run-final-pre {
  font-size: 11px;
  margin: 0;
  padding: 0 8px 8px;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.flow-designer__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.flow-designer__mock-tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}

.flow-designer__test-result {
  margin-top: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px;
  background: #fafafa;
  max-height: 300px;
  overflow-y: auto;
}

.flow-designer__test-label {
  font-size: 12px;
  color: #303133;
  font-weight: 600;
  margin-bottom: 6px;
}

.flow-designer__ndv-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.flow-designer__ndv-sub {
  font-size: 12px;
  color: #909399;
}

/* ==================== NDV 三栏布局（对齐 n8n） ==================== */

.flow-designer__ndv {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.flow-designer__ndv-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.flow-designer__ndv-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.flow-designer__ndv-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-designer__ndv-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.flow-designer__ndv-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.flow-designer__ndv-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e4e7ed;
}

.flow-designer__ndv-col:last-child {
  border-right: none;
}

.flow-designer__ndv-col-title {
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  background: #fafafa;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.flow-designer__ndv-col-body {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.flow-designer__ndv-error {
  margin: 12px;
}

/* ==================== 版本历史（抽屉 + 只读预览） ==================== */

.flow-designer__versions-tip {
  margin-bottom: 12px;
}

.flow-designer__version-preview {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.flow-designer__version-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 2px 10px;
  flex-shrink: 0;
}

.flow-designer__version-preview-meta {
  font-size: 12px;
  color: #909399;
}

.flow-designer__version-preview-canvas {
  flex: 1;
  min-height: 0;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}
</style>
