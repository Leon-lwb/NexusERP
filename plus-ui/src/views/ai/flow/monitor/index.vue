<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { AiFlow, AiFlowInstance } from '@/api/ai/flow';
import { listFlow, listInstance, retryInstance } from '@/api/ai/flow';

/**
 * 流程运行历史列表页
 * <p>实例分页 + 流程下拉/status/triggerType 过滤 + RUNNING 自动刷新 + 详情重放 + 续跑</p>
 */

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const rows = ref<AiFlowInstance[]>([]);
const total = ref(0);
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  flowId: (route.query.flowId as string) ?? '',
  status: '',
  triggerType: ''
});

/** 流程下拉候选 */
const flowOptions = ref<Array<{ flowName: string; id: number | string }>>([]);

/** 实例状态标签映射 */
const statusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '运行中', type: 'warning' },
  SUCCEEDED: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  STOPPED: { label: '已停止', type: 'info' }
};

/** 触发方式标签 */
function triggerLabel(t?: string) {
  const m: Record<string, string> = { MANUAL: '手动', SCHEDULE: '定时', TEST: '测试', WEBHOOK: 'Webhook' };
  return t ? (m[t] ?? t) : '-';
}

/** RUNNING 行自动刷新（3s，仅当列表存在运行中实例） */
let refreshTimer: number | null = null;

function startRefreshIfRunning() {
  stopRefresh();
  const hasRunning = rows.value.some((r) => r.status === 'RUNNING');
  if (hasRunning) {
    refreshTimer = window.setInterval(() => {
      listInstance({ ...query, pageNum: 1, pageSize: query.pageSize })
        .then((res) => {
          rows.value = res.rows ?? [];
          total.value = res.total ?? 0;
          // 没有运行中实例后停止轮询
          if (!rows.value.some((r) => r.status === 'RUNNING')) stopRefresh();
        })
        .catch(() => stopRefresh());
    }, 3000);
  }
}

function stopRefresh() {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

async function loadList() {
  loading.value = true;
  try {
    const res = await listInstance(query);
    rows.value = res.rows ?? [];
    total.value = res.total ?? 0;
    startRefreshIfRunning();
  } finally {
    loading.value = false;
  }
}

/** 进入详情（只读画布重放） */
function detail(row: AiFlowInstance) {
  router.push({ path: '/ai/flow/monitor/detail', query: { instanceId: row.id, flowId: row.flowId } });
}

/** 执行级续跑（从失败节点重跑，返回新实例 → 跳转新详情） */
async function retry(row: AiFlowInstance) {
  try {
    const res = await retryInstance(row.id);
    ElMessage.success('已从失败节点续跑');
    const newId = res.data ?? row.id;
    if (newId !== row.id) {
      router.push({ path: '/ai/flow/monitor/detail', query: { instanceId: String(newId), flowId: String(row.flowId) } });
    } else {
      loadList();
    }
  } catch (e: any) {
    ElMessage.error('续跑失败: ' + (e?.message ?? e));
  }
}

/** 格式化耗时 */
function formatDuration(ms?: number) {
  if (ms === null || ms === undefined) return '-';
  if (ms < 1000) return ms + 'ms';
  return (ms / 1000).toFixed(2) + 's';
}

onMounted(async () => {
  loadList();
  // 流程下拉候选（前 100 个）
  try {
    const res = await listFlow({ pageNum: 1, pageSize: 100 });
    flowOptions.value = (res.rows ?? []).map((f: AiFlow) => ({ flowName: f.flowName, id: f.id }));
  } catch {
    /* 下拉加载失败不阻塞 */
  }
});

onBeforeUnmount(stopRefresh);
</script>

<template>
  <div class="app-container">
    <el-form :model="query" inline>
      <el-form-item label="流程">
        <el-select v-model="query.flowId" placeholder="选择流程" clearable filterable style="width: 220px" @change="loadList">
          <el-option v-for="f in flowOptions" :key="String(f.id)" :label="f.flowName" :value="String(f.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="loadList">
          <el-option label="运行中" value="RUNNING" />
          <el-option label="成功" value="SUCCEEDED" />
          <el-option label="失败" value="FAILED" />
          <el-option label="已停止" value="STOPPED" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发方式">
        <el-select v-model="query.triggerType" placeholder="全部" clearable style="width: 120px" @change="loadList">
          <el-option label="手动" value="MANUAL" />
          <el-option label="定时" value="SCHEDULE" />
          <el-option label="测试" value="TEST" />
          <el-option label="Webhook" value="WEBHOOK" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList">搜索</el-button>
        <el-button @click="router.push('/ai/flow/index')">返回流程列表</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column label="实例ID" prop="id" width="190" show-overflow-tooltip />
      <el-table-column label="流程名称" prop="flowName" min-width="140" show-overflow-tooltip />
      <el-table-column label="版本" prop="flowVersion" width="70" align="center" />
      <el-table-column label="触发方式" width="100" align="center">
        <template #default="{ row }">{{ triggerLabel(row.triggerType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="(statusMap[row.status]?.type as any) ?? 'info'">
            {{ statusMap[row.status]?.label ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="节点" width="110" align="center">
        <template #default="{ row }">
          <span>{{ row.nodeSuccess ?? 0 }}/{{ row.nodeTotal ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" prop="startTime" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="detail(row)">详情</el-button>
          <el-button v-if="row.status === 'FAILED' || row.status === 'STOPPED'" link type="warning" @click="retry(row)"> 续跑 </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="loadList" />
  </div>
</template>
