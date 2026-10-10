<script setup lang="ts">
import { getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { AiFlow, AiFlowInstance } from '@/api/ai/flow';
import { listFlow, listInstance, retryInstance } from '@/api/ai/flow';
import { FLOW_TRIGGER_OPTIONS, flowTriggerLabel } from '../flowTriggerDisplay';

/**
 * 流程运行历史列表页
 * <p>实例分页 + 流程下拉/status/triggerType 过滤 + RUNNING 自动刷新 + 详情重放 + 续跑</p>
 */

const { proxy } = getCurrentInstance() as any;
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const rows = ref<AiFlowInstance[]>([]);
const total = ref(0);
const showSearch = ref(true);
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  flowId: (route.query.flowId as string) ?? '',
  status: '',
  triggerType: '',
  keyword: '',
  beginTime: '',
  endTime: ''
});

/** 搜索（重置页码） */
function handleQuery() {
  query.pageNum = 1;
  loadList();
}

/** 重置查询（清空全部筛选条件，含路由预填的流程） */
function resetQuery() {
  query.flowId = '';
  query.status = '';
  query.triggerType = '';
  query.keyword = '';
  dateRange.value = null;
  query.beginTime = '';
  query.endTime = '';
  handleQuery();
}

/** 相对时间快捷筛选（今天/近7天/近30天） */
function applyRelativeRange(days: number) {
  const now = new Date();
  // 当天 00:00:00 为下限（今天=今天0点；N天=今天0点- (N-1) 天）
  const begin = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (days - 1));
  query.beginTime = formatDateTime(begin);
  query.endTime = formatDateTime(now);
  loadList();
}

function formatDateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/** 日期范围选择（el-date-picker daterange 绑定值 [begin, end]，转查询参数） */
const dateRange = ref<[string, string] | null>(null);
function onDateRangeChange(range: [string, string] | null) {
  if (range && range.length === 2) {
    query.beginTime = range[0] + ' 00:00:00';
    query.endTime = range[1] + ' 23:59:59';
  } else {
    query.beginTime = '';
    query.endTime = '';
  }
}

/** 重置时间范围筛选（清空快捷选择与日期选择器） */
function resetRange() {
  dateRange.value = null;
  query.beginTime = '';
  query.endTime = '';
  loadList();
}

/** 流程下拉候选 */
const flowOptions = ref<Array<{ flowName: string; id: number | string }>>([]);

/** 实例状态标签映射 */
const statusMap: Record<string, { label: string; type: string }> = {
  RUNNING: { label: '运行中', type: 'warning' },
  SUCCEEDED: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  STOPPED: { label: '已停止', type: 'info' }
};

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
    <el-form :model="query" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="流程" prop="flowId">
            <el-select v-model="query.flowId" placeholder="请选择流程" clearable filterable style="width: 100%" @change="handleQuery">
              <el-option v-for="f in flowOptions" :key="String(f.id)" :label="f.flowName" :value="String(f.id)" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="query.status" placeholder="请选择状态" clearable style="width: 100%" @change="handleQuery">
              <el-option label="运行中" value="RUNNING" />
              <el-option label="成功" value="SUCCEEDED" />
              <el-option label="失败" value="FAILED" />
              <el-option label="已停止" value="STOPPED" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="触发方式" prop="triggerType">
            <el-select v-model="query.triggerType" placeholder="请选择触发方式" clearable style="width: 100%" @change="handleQuery">
              <el-option v-for="option in FLOW_TRIGGER_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="关键词" prop="keyword">
            <el-input v-model="query.keyword" placeholder="流程名称 / 实例ID" clearable @keyup.enter="handleQuery" @clear="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
              @change="onDateRangeChange"
            />
            <div class="monitor-relative">
              <el-button size="small" text @click="applyRelativeRange(1)">今天</el-button>
              <el-button size="small" text @click="applyRelativeRange(7)">近7天</el-button>
              <el-button size="small" text @click="applyRelativeRange(30)">近30天</el-button>
              <el-button size="small" text @click="resetRange">全部</el-button>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button plain icon="Back" @click="router.push('/ai/flow/index')">返回流程列表</el-button>
      </el-col>
      <right-toolbar v-model:show-search="showSearch" @query-table="loadList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column label="实例ID" prop="id" width="190" show-overflow-tooltip />
      <el-table-column label="流程名称" prop="flowName" min-width="140" show-overflow-tooltip />
      <el-table-column label="版本" prop="flowVersion" width="70" align="center" />
      <el-table-column label="触发方式" width="100" align="center">
        <template #default="{ row }">{{ flowTriggerLabel(row.triggerType) }}</template>
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

<style scoped>
.monitor-relative {
  display: flex;
  align-items: center;
  margin-top: 4px;
}
</style>
