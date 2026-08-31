<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useFlowStore } from '../designer/useFlowStore';
import FlowCanvas from '../designer/FlowCanvas.vue';
import JsonViewer from '../components/JsonViewer.vue';
import type { AiFlowInstance, AiFlowNodeRun } from '@/api/ai/flow';
import { getFlow, getInstance, listNodeRuns, retryInstance } from '@/api/ai/flow';

/**
 * 流程实例详情页（只读画布重放）
 * <p>用实例 configSnapshot（启动时配置）加载画布 → 按 node_run 状态着色 →
 * 时间线动画重放（按执行顺序逐步高亮）→ 节点运行记录表 + JSON/表格双视图 + 失败续跑</p>
 */

const route = useRoute();
const router = useRouter();
const store = useFlowStore();

const loading = ref(false);
const instance = ref<AiFlowInstance | null>(null);
const nodeRuns = ref<AiFlowNodeRun[]>([]);
const selectedRun = ref<AiFlowNodeRun | null>(null);
const retrying = ref(false);

/** 实例状态映射 */
const statusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '运行中', type: 'warning' },
  SUCCEEDED: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  STOPPED: { label: '已停止', type: 'info' }
};

/** 节点运行记录状态映射 */
const nodeStatusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '运行中', type: 'warning' },
  SUCCEEDED: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  SKIPPED: { label: '跳过', type: 'info' },
  PENDING: { label: '等待', type: 'info' }
};

/** 是否可续跑 */
const canRetry = computed(() => {
  const s = instance.value?.status;
  return s === 'FAILED' || s === 'STOPPED';
});

/* ==================== 时间线动画重放 ==================== */

/**
 * 节点运行记录（按拓扑序重排，含 SKIPPED，表格/动画共用）
 * <p>node_run 的 start_time 是秒级精度，同秒执行时 ORDER BY 失效（物理序≠执行序，
 * 且 SKIPPED 记录在分支判断时即时插入、id 可能早于后续节点）。
 * 拓扑序（BFS 分层，loop 返回边跳过）是唯一稳定且语义正确的执行顺序。</p>
 */
const orderedRuns = computed(() => {
  // 1. 构图（跳过 loop 返回边：循环体依赖主体而非反向）
  const adj: Record<string, string[]> = {};
  const inDeg: Record<string, number> = {};
  for (const n of store.nodes) {
    adj[n.id] = [];
    inDeg[n.id] = 0;
  }
  for (const e of store.edges) {
    if ((e as any).loop) continue;
    (adj[e.source] ??= []).push(e.target);
    inDeg[e.target] = (inDeg[e.target] ?? 0) + 1;
  }
  // 2. Kahn BFS 分层
  const layer: Record<string, number> = {};
  const queue = store.nodes.filter((n) => (inDeg[n.id] ?? 0) === 0).map((n) => n.id);
  queue.forEach((id) => (layer[id] = 0));
  let qi = 0;
  while (qi < queue.length) {
    const id = queue[qi++];
    for (const t of adj[id] ?? []) {
      if ((inDeg[t] ?? 0) > 0) {
        inDeg[t]--;
        if (inDeg[t] === 0) {
          layer[t] = (layer[id] ?? 0) + 1;
          queue.push(t);
        }
      }
    }
  }
  // 3. 按 (拓扑层, batchIndex, 原顺序) 排序
  return nodeRuns.value
    .map((r, idx) => ({ r, idx }))
    .sort((a, b) => {
      const la = layer[a.r.nodeId] ?? 0;
      const lb = layer[b.r.nodeId] ?? 0;
      if (la !== lb) return la - lb;
      const ba = a.r.batchIndex ?? 0;
      const bb = b.r.batchIndex ?? 0;
      if (ba !== bb) return ba - bb;
      return a.idx - b.idx;
    })
    .map(({ r }) => r);
});

/** 播放序列（拓扑序 - SKIPPED：跳过的节点不进入动画） */
const playSequence = computed(() => orderedRuns.value.filter((r) => r.status !== 'SKIPPED'));

const playing = ref(false);
const playProgress = ref(0);
let playTimer: number | null = null;
let playIndex = 0;

/** 播放/停止：按播放序列逐步高亮（500ms/步），播放小球跟随当前节点 */
function toggleReplay() {
  if (playing.value) {
    stopReplay();
    return;
  }
  if (!playSequence.value.length) return;
  // 清空着色后按拓扑顺序逐步播放（SKIPPED 不进入序列，动画结束后补色）
  store.clearNodeStatus();
  playing.value = true;
  playIndex = 0;
  playProgress.value = 0;
  playTimer = window.setInterval(() => {
    if (playIndex >= playSequence.value.length) {
      stopReplay();
      return;
    }
    const run = playSequence.value[playIndex++];
    store.setNodeStatus(run.nodeId, run.status);
    store.setPlayingNode(run.nodeId);
    playProgress.value = playIndex;
  }, 500);
}

function stopReplay() {
  playing.value = false;
  store.setPlayingNode(null);
  // 补色 SKIPPED 节点（播放序列已过滤，动画结束后恢复灰色着色）
  for (const run of nodeRuns.value) {
    if (run.status === 'SKIPPED') {
      store.setNodeStatus(run.nodeId, run.status);
    }
  }
  if (playTimer !== null) {
    window.clearInterval(playTimer);
    playTimer = null;
  }
}

/** 表格行点击：右侧抽屉查看输入/输出快照（数据再多也不遮挡表格/画布） */
const snapshotVisible = ref(false);
function onRowClick(row: AiFlowNodeRun) {
  selectedRun.value = row;
  snapshotVisible.value = true;
}

onBeforeUnmount(stopReplay);

/** 解析 JSON 字符串（宽松） */
function parseJson(text?: string): unknown {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** 格式化耗时 */
function formatDuration(ms?: number) {
  if (ms === null || ms === undefined) return '-';
  if (ms < 1000) return ms + 'ms';
  return (ms / 1000).toFixed(2) + 's';
}

/** 续跑（从失败节点重跑，返回新实例 → 刷新详情） */
async function retry() {
  if (!instance.value || retrying.value) return;
  retrying.value = true;
  try {
    const res = await retryInstance(instance.value.id);
    const newId = res.data ?? instance.value.id;
    ElMessage.success('已从失败节点续跑');
    if (String(newId) !== String(instance.value.id)) {
      router.replace({
        path: '/ai/flow/monitor/detail',
        query: { instanceId: String(newId), flowId: String(instance.value.flowId) }
      });
      await loadDetail(String(newId));
    } else {
      await loadDetail(String(instance.value.id));
    }
  } catch (e: any) {
    ElMessage.error('续跑失败: ' + (e?.message ?? e));
  } finally {
    retrying.value = false;
  }
}

async function loadDetail(instanceId: string) {
  loading.value = true;
  try {
    // 1. 加载实例（详情接口返回 configSnapshot）
    const instRes = await getInstance(instanceId);
    instance.value = instRes.data;

    // 2. 加载画布配置：实例快照优先（改版后重放旧实例不错位），无快照回退当前流程配置
    if (instance.value?.configSnapshot) {
      store.fromConfigJson(instance.value.configSnapshot);
    } else {
      const flowRes = await getFlow(String(instance.value?.flowId ?? route.query.flowId));
      if (flowRes.data?.configJson) {
        store.fromConfigJson(flowRes.data.configJson);
      }
    }

    // 3. 加载节点运行记录并着色
    const runsRes = await listNodeRuns(instanceId);
    nodeRuns.value = runsRes.data ?? [];
    for (const run of nodeRuns.value) {
      store.setNodeStatus(run.nodeId, run.status);
    }
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await loadDetail(route.query.instanceId as string);
});
</script>

<template>
  <div class="instance-detail">
    <div v-loading="loading" class="instance-detail__main">
      <!-- 顶部：实例信息 -->
      <div v-if="instance" class="instance-detail__header">
        <el-button @click="router.push('/ai/flow/monitor')">返回历史</el-button>
        <div class="instance-detail__meta">
          <span class="instance-detail__title">{{ instance.flowName ?? '流程' }}</span>
          <el-tag :type="(statusMap[instance.status]?.type as any) ?? 'info'" size="small">
            {{ statusMap[instance.status]?.label ?? instance.status }}
          </el-tag>
          <span class="instance-detail__sub">
            实例 {{ instance.id }} · 版本 v{{ instance.flowVersion }} ·
            {{ instance.triggerType === 'SCHEDULE' ? '定时触发' : instance.triggerType === 'TEST' ? '测试运行' : '手动触发' }} ·
            {{ formatDuration(instance.durationMs) }} · 节点 {{ instance.nodeSuccess ?? 0 }}/{{ instance.nodeTotal ?? 0 }}
          </span>
        </div>
        <el-button v-if="playSequence.length && instance.status !== 'RUNNING'" type="primary" plain size="small" @click="toggleReplay">
          {{ playing ? `⏸ 停止重放 (${playProgress}/${playSequence.length})` : '▶ 时间线重放' }}
        </el-button>
        <el-button v-if="canRetry" type="warning" plain size="small" :loading="retrying" @click="retry">续跑（从失败节点重跑）</el-button>
        <el-alert
          v-if="instance.status === 'FAILED' && instance.errorMsg"
          :title="'执行失败: ' + instance.errorMsg"
          type="error"
          :closable="false"
          class="instance-detail__error"
        />
      </div>

      <!-- 中部：只读画布（重放 + 播放小球） -->
      <div class="instance-detail__canvas">
        <FlowCanvas :nodes="store.nodes" :edges="store.edges" readonly :playing-node-id="store.playingNodeId" />
      </div>

      <!-- 底部：节点运行记录（拓扑序） -->
      <div class="instance-detail__runs">
        <el-table :data="orderedRuns" border stripe size="small" @row-click="onRowClick">
          <el-table-column label="节点" prop="nodeName" min-width="120" show-overflow-tooltip />
          <el-table-column label="类型" prop="nodeType" width="90" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="(nodeStatusMap[row.status]?.type as any) ?? 'info'" size="small">
                {{ nodeStatusMap[row.status]?.label ?? row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="批次" width="60" align="center">
            <template #default="{ row }">{{ row.batchIndex ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="center">
            <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column label="重试" prop="retryCount" width="70" align="center" />
          <el-table-column label="错误信息" prop="errorMsg" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- 节点运行快照：右侧抽屉（JSON/表格双视图，对齐 n8n NDV） -->
      <el-drawer v-model="snapshotVisible" :title="'节点数据: ' + (selectedRun?.nodeName ?? '')" size="480px">
        <template v-if="selectedRun">
          <div class="snapshot-drawer__meta">
            <el-tag :type="(nodeStatusMap[selectedRun.status]?.type as any) ?? 'info'" size="small">
              {{ nodeStatusMap[selectedRun.status]?.label ?? selectedRun.status }}
            </el-tag>
            <span>批次 {{ selectedRun.batchIndex ?? 0 }}</span>
            <span>耗时 {{ formatDuration(selectedRun.durationMs) }}</span>
            <span v-if="selectedRun.retryCount">重试 {{ selectedRun.retryCount }}</span>
            <span class="snapshot-drawer__id">{{ selectedRun.nodeId }}</span>
          </div>
          <el-alert v-if="selectedRun.errorMsg" :title="selectedRun.errorMsg" type="error" :closable="false" class="snapshot-drawer__error" />
          <el-tabs>
            <el-tab-pane label="输出">
              <JsonViewer :data="parseJson(selectedRun.outputJson)" />
            </el-tab-pane>
            <el-tab-pane label="输入">
              <JsonViewer :data="parseJson(selectedRun.inputJson)" />
            </el-tab-pane>
          </el-tabs>
        </template>
      </el-drawer>
    </div>
  </div>
</template>

<style scoped>
.instance-detail {
  height: calc(100vh - 84px);
}

.instance-detail__main {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.instance-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.instance-detail__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.instance-detail__title {
  font-size: 15px;
  font-weight: 600;
}

.instance-detail__sub {
  font-size: 12px;
  color: #909399;
}

.instance-detail__error {
  flex: 1;
  min-width: 200px;
}

.instance-detail__canvas {
  height: 380px;
  min-height: 200px;
  background: #f5f7fa;
}

.instance-detail__runs {
  flex: 1;
  min-height: 0;
  background: #fff;
  margin: 8px;
  border-radius: 6px;
  padding: 8px;
  overflow-y: auto;
}

.snapshot-drawer__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}

.snapshot-drawer__id {
  margin-left: auto;
  font-family: monospace;
}

.snapshot-drawer__error {
  margin-bottom: 10px;
}
</style>
