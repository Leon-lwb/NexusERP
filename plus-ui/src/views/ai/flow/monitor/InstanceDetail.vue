<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import VueJsonPretty from 'vue-json-pretty';
import 'vue-json-pretty/lib/styles.css';
import { useFlowStore } from '../designer/useFlowStore';
import FlowCanvas from '../designer/FlowCanvas.vue';
import type { AiFlowInstance, AiFlowNodeRun } from '@/api/ai/flow';
import { getFlow, getInstance, listNodeRuns } from '@/api/ai/flow';

/**
 * 流程实例详情页（只读画布重放）
 * <p>从流程 configJson 加载画布 → 按 node_run 状态着色 → 节点运行记录表 + input/output JSON 快照</p>
 */

const route = useRoute();
const router = useRouter();
const store = useFlowStore();

const loading = ref(false);
const instance = ref<AiFlowInstance | null>(null);
const nodeRuns = ref<AiFlowNodeRun[]>([]);
const selectedRun = ref<AiFlowNodeRun | null>(null);

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

onMounted(async () => {
  loading.value = true;
  try {
    const instanceId = route.query.instanceId as string;
    const flowId = route.query.flowId as string;

    // 1. 加载实例
    const instRes = await getInstance(instanceId);
    instance.value = instRes.data;

    // 2. 加载流程配置（画布重放）
    const flowRes = await getFlow(flowId);
    if (flowRes.data?.configJson) {
      store.fromConfigJson(flowRes.data.configJson);
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
            实例 {{ instance.id }} · 版本 v{{ instance.flowVersion }} · {{ instance.triggerType === 'SCHEDULE' ? '定时触发' : '手动触发' }} ·
            {{ formatDuration(instance.durationMs) }} · 节点 {{ instance.nodeSuccess ?? 0 }}/{{ instance.nodeTotal ?? 0 }}
          </span>
        </div>
        <el-alert
          v-if="instance.status === 'FAILED' && instance.errorMsg"
          :title="'执行失败: ' + instance.errorMsg"
          type="error"
          :closable="false"
          class="instance-detail__error"
        />
      </div>

      <!-- 中部：只读画布（重放） -->
      <div class="instance-detail__canvas">
        <FlowCanvas :nodes="store.nodes" :edges="store.edges" readonly />
      </div>

      <!-- 底部：节点运行记录 -->
      <div class="instance-detail__runs">
        <el-table :data="nodeRuns" border stripe size="small" @row-click="(row: any) => (selectedRun = row)">
          <el-table-column label="节点" prop="nodeName" min-width="120" show-overflow-tooltip />
          <el-table-column label="类型" prop="nodeType" width="90" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="(nodeStatusMap[row.status]?.type as any) ?? 'info'" size="small">
                {{ nodeStatusMap[row.status]?.label ?? row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="center">
            <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column label="重试" prop="retryCount" width="70" align="center" />
          <el-table-column label="错误信息" prop="errorMsg" min-width="160" show-overflow-tooltip />
        </el-table>

        <!-- 选中节点：JSON 快照 -->
        <div v-if="selectedRun" class="instance-detail__snapshot">
          <el-tabs>
            <el-tab-pane label="输出">
              <VueJsonPretty :data="parseJson(selectedRun.outputJson) as any" :show-line="false" :deep="3" />
            </el-tab-pane>
            <el-tab-pane label="输入">
              <VueJsonPretty :data="parseJson(selectedRun.inputJson) as any" :show-line="false" :deep="3" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
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
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.instance-detail__snapshot {
  border-top: 1px solid #e4e7ed;
  padding-top: 8px;
}
</style>
