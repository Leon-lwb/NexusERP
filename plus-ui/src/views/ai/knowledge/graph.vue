<template>
  <div class="kg-container">
    <!-- 工具条 -->
    <div class="kg-toolbar">
      <el-tag v-if="graph" :type="statusType(graph.graphStatus)">{{ statusText(graph.graphStatus) }}</el-tag>
      <el-tag v-else type="info">未构建</el-tag>
      <span v-if="graph" class="kg-stats">
        实体 {{ graph.nodeCount || 0 }} · 关系 {{ graph.relationshipCount || 0 }} · 社区 {{ graph.communityCount || 0 }}
      </span>
      <el-tag v-if="graph && entities.length >= LOAD_PAGE_SIZE" size="small" type="info" effect="plain">仅显示前 {{ LOAD_PAGE_SIZE }} 个实体</el-tag>
      <el-button v-if="!building" type="primary" size="small" icon="MagicStick" @click="handleBuild">构建图谱</el-button>
      <el-button v-if="!building && graph" type="warning" size="small" icon="RefreshRight" @click="handleRebuild">重建</el-button>
      <el-button v-if="!building && graph" type="danger" size="small" icon="Delete" @click="handleDelete">删除</el-button>
      <el-button v-if="!building" size="small" icon="Refresh" @click="loadAll">刷新</el-button>
    </div>

    <!-- 进度条 -->
    <el-progress
      v-if="building && progress"
      :percentage="progressPct"
      :status="progressStatus"
      :indeterminate="progressIndeterminate"
      :stroke-width="14"
    />
    <div v-if="building && progress" class="kg-progress-tip">{{ progressTip }}</div>

    <!-- 图谱可视化 -->
    <div v-if="!graph" class="kg-empty">
      <el-empty description="尚未构建知识图谱，点击「构建图谱」开始">
        <template #image><el-icon size="60"><Share /></el-icon></template>
      </el-empty>
    </div>
    <div v-else ref="chartRef" class="kg-chart"></div>

    <!-- 详情面板 -->
    <el-tabs v-model="activeTab" class="kg-tabs">
      <el-tab-pane label="实体" name="entity">
        <el-table :data="entities" border size="small" max-height="300">
          <el-table-column label="名称" prop="entityName" :show-overflow-tooltip="true" />
          <el-table-column label="类型" prop="entityType" width="100" />
          <el-table-column label="社区" prop="communityId" width="100" />
          <el-table-column label="提及" prop="mentionCount" width="70" />
          <el-table-column label="置信度" prop="confidence" width="80" />
          <el-table-column label="描述" prop="description" :show-overflow-tooltip="true" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="关系" name="relation">
        <el-table :data="relations" border size="small" max-height="300">
          <el-table-column label="头实体" prop="sourceEntityName" :show-overflow-tooltip="true" />
          <el-table-column label="关系" prop="relationType" width="100" />
          <el-table-column label="尾实体" prop="targetEntityName" :show-overflow-tooltip="true" />
          <el-table-column label="强度" prop="strength" width="70" />
          <el-table-column label="描述" prop="description" :show-overflow-tooltip="true" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="社区摘要" name="community">
        <el-table :data="communities" border size="small" max-height="300">
          <el-table-column label="序号" prop="communityIndex" width="70" />
          <el-table-column label="实体数" prop="entityCount" width="80" />
          <el-table-column label="状态" prop="summaryStatus" width="90">
            <template #default="scope">
              <el-tag size="small" :type="summaryStatusType(scope.row.summaryStatus)">{{ summaryStatusText(scope.row.summaryStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="摘要" prop="summary" :show-overflow-tooltip="true" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="全局检索" name="global">
        <el-input v-model="globalQuery" placeholder="输入整体/主题问题，如「这批文档主要涉及哪些业务关系」" clearable @keyup.enter="handleGlobalSearch">
          <template #append>
            <el-button :loading="globalLoading" @click="handleGlobalSearch">检索</el-button>
          </template>
        </el-input>
        <div v-if="globalResult" class="kg-global-result">
          <div class="kg-global-answer">{{ globalResult.answer }}</div>
          <div v-if="globalResult.communities?.length" class="kg-global-communities">
            引用社区：{{ (globalResult.communities as string[]).join('、') }}
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane v-if="neighbors" :label="`邻居（${selectedEntityName}）`" name="neighbors">
        <el-table :data="neighbors.neighbors" border size="small" max-height="300">
          <el-table-column label="名称" prop="entityName" :show-overflow-tooltip="true" />
          <el-table-column label="类型" prop="entityType" width="100" />
          <el-table-column label="提及" prop="mentionCount" width="70" />
        </el-table>
        <el-table :data="neighbors.relations" border size="small" max-height="200" style="margin-top: 8px">
          <el-table-column label="头实体" prop="sourceEntityName" :show-overflow-tooltip="true" />
          <el-table-column label="关系" prop="relationType" width="100" />
          <el-table-column label="尾实体" prop="targetEntityName" :show-overflow-tooltip="true" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { ComponentInternalInstance } from 'vue'
import {
  getGraph, getGraphProgress, listGraphEntity, listGraphRelation, listGraphCommunity,
  getEntityNeighbors, globalSearch, buildGraph, rebuildGraph, deleteGraph,
  type AiKnowledgeGraph, type AiKgEntity, type AiKgRelation, type AiKgCommunity, type GraphProgress
} from '@/api/ai/knowledgeGraph'

const { proxy } = getCurrentInstance() as ComponentInternalInstance

const props = defineProps<{ knowledgeId: number }>()

const graph = ref<AiKnowledgeGraph | null>(null)
const progress = ref<GraphProgress | null>(null)
const building = ref(false)
const entities = ref<AiKgEntity[]>([])
const relations = ref<AiKgRelation[]>([])
const communities = ref<AiKgCommunity[]>([])
const activeTab = ref('entity')
const neighbors = ref<any>(null)
const selectedEntityName = ref('')
const globalQuery = ref('')
const globalResult = ref<any>(null)
const globalLoading = ref(false)

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null
let intersectionObserver: IntersectionObserver | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

const LOAD_PAGE_SIZE = 500

const progressPct = computed(() => {
  const p = progress.value
  if (!p) return 0
  const stage = p.stage
  if (stage === 'SUMMARIZING') {
    const total = p.communityTotal || 0
    return total ? Math.round(((p.communitySummarized || 0) / total) * 100) : 0
  }
  if (stage === 'DETECTING' || stage === 'PENDING') {
    return 100 // 配合 indeterminate 走不定进度条
  }
  // EXTRACTING / 默认
  if (!p.total) return 0
  return Math.round(((p.processed || 0) / p.total) * 100)
})
const progressIndeterminate = computed(() => {
  const stage = progress.value?.stage
  return stage === 'DETECTING' || stage === 'PENDING'
})
const progressStatus = computed(() => {
  if (progress.value?.graphStatus === '3') return 'exception'
  if (progress.value?.graphStatus === '2') return 'success'
  return undefined
})
const progressTip = computed(() => {
  const p = progress.value
  if (!p) return ''
  const stage = p.stage
  if (stage === 'EXTRACTING') {
    return `实体抽取中 ${p.processed || 0} / ${p.total || 0} 切片${p.failed ? '（失败 ' + p.failed + '）' : ''}`
  }
  if (stage === 'DETECTING') {
    return '社区检测中…'
  }
  if (stage === 'SUMMARIZING') {
    return `社区摘要生成中 ${p.communitySummarized || 0} / ${p.communityTotal || 0}`
  }
  return `已处理 ${p.processed || 0} / ${p.total || 0} 切片${p.failed ? '（失败 ' + p.failed + '）' : ''}`
})

onMounted(() => {
  loadAll()
})

onUnmounted(() => {
  stopPolling()
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  chartInstance?.dispose()
  chartInstance = null
})

watch(() => props.knowledgeId, () => {
  // 切换知识库：先销毁旧 echarts 实例与 observer，避免 setOption 作用于脱离 DOM 的旧实例导致新容器无图
  chartInstance?.dispose()
  chartInstance = null
  resizeObserver?.disconnect()
  resizeObserver = null
  intersectionObserver?.disconnect()
  intersectionObserver = null
  graph.value = null
  entities.value = []
  relations.value = []
  communities.value = []
  neighbors.value = null
  loadAll()
})

/** 加载图谱 + 实体/关系/社区 */
async function loadAll() {
  const res: any = await getGraph(props.knowledgeId)
  graph.value = res.data || null
  if (graph.value) {
    if (graph.value.graphStatus === '1') {
      building.value = true
      startPolling()
    } else if (graph.value.graphStatus === '2') {
      await loadGraphData()
    }
  }
}

async function loadGraphData() {
  const graphId = graph.value?.id
  if (!graphId) return
  const [entRes, relRes, comRes]: any[] = await Promise.all([
    listGraphEntity({ graphId, pageNum: 1, pageSize: LOAD_PAGE_SIZE }),
    listGraphRelation({ graphId, pageNum: 1, pageSize: LOAD_PAGE_SIZE }),
    listGraphCommunity({ graphId, pageNum: 1, pageSize: LOAD_PAGE_SIZE })
  ])
  entities.value = entRes.rows || []
  relations.value = relRes.rows || []
  communities.value = comRes.rows || []
  nextTick(renderChart)
}

/** 构建 */
function handleBuild() {
  if (!props.knowledgeId) return
  buildGraph(props.knowledgeId).then(() => {
    proxy?.$modal.msgSuccess('已开始构建图谱')
    building.value = true
    progress.value = null
    startPolling()
  })
}

/** 重建 */
function handleRebuild() {
  proxy?.$modal.confirm('将清空当前图谱全部实体/关系/社区并全量重抽，确认？').then(() => {
    return rebuildGraph(props.knowledgeId)
  }).then(() => {
    proxy?.$modal.msgSuccess('已开始重建图谱')
    building.value = true
    progress.value = null
    startPolling()
  }).catch(() => {})
}

/** 删除 */
function handleDelete() {
  proxy?.$modal.confirm('将删除图谱及全部实体/关系/社区（保留文档切片），确认？').then(() => {
    return deleteGraph(props.knowledgeId)
  }).then(() => {
    proxy?.$modal.msgSuccess('删除成功')
    graph.value = null
    entities.value = []
    relations.value = []
    communities.value = []
    neighbors.value = null
    chartInstance?.clear()
  }).catch(() => {})
}

/** 进度轮询 */
function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    const res: any = await getGraphProgress(props.knowledgeId)
    progress.value = res.data
    const status = res.data?.graphStatus
    // 只认 2(完成)/3(失败) 为终止态：rebuild 会先把 status 置 0 再异步构建，
    // 若把 0 当终止会误判"未构建"→进度消失、按钮重现而后端仍在跑
    if (status === '2' || status === '3') {
      stopPolling()
      building.value = false
      if (status === '2') {
        await loadAll()
        const errMsg = graph.value?.errorMessage
        if (errMsg && errMsg.includes('失败')) {
          proxy?.$modal.msgWarning('图谱构建完成（' + errMsg + '）')
        } else {
          proxy?.$modal.msgSuccess('图谱构建完成')
        }
      } else if (status === '3') {
        proxy?.$modal.msgError('图谱构建失败：' + (graph.value?.errorMessage || ''))
      }
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/**
 * 挂载 echarts 容器尺寸/可见性监听（仅创建一次）
 * <p>ResizeObserver 处理容器尺寸变化；IntersectionObserver 处理容器由隐藏转可见
 * （如 Tab 从"文档管理"切到"知识图谱"、菜单重入），可见时 resize 修正 width=0 初始化导致的偏移</p>
 */
function setupObservers() {
  if (!chartRef.value) return
  if (!resizeObserver) {
    resizeObserver = new ResizeObserver(() => chartInstance?.resize())
    resizeObserver.observe(chartRef.value)
  }
  if (!intersectionObserver) {
    intersectionObserver = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && entries[0].intersectionRatio > 0) {
        chartInstance?.resize()
      }
    })
    intersectionObserver.observe(chartRef.value)
  }
}

/** 渲染 echarts 力导向图（按社区着色） */
function renderChart() {
  if (!chartRef.value || !entities.value.length) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.on('click', (params: any) => {
      if (params.dataType === 'node' && params.data?.id) {
        handleNodeClick(String(params.data.id))
      }
    })
    // init 后挂载 observer：此处 chartRef 必然存在（onMounted 时数据未加载、chartRef 尚不存在，旧逻辑里 observer 永不创建）
    setupObservers()
  }
  const communityIds = [...new Set(entities.value.map((e) => e.communityId).filter((c) => c != null))] as number[]
  const categories = communityIds.map((id) => ({ name: '社区 ' + id }))
  const catIndex = (id?: number) => {
    const i = communityIds.indexOf(id as number)
    return i >= 0 ? i : -1
  }
  const nodes = entities.value.map((e) => ({
    id: String(e.id),
    name: e.entityName,
    category: catIndex(e.communityId),
    symbolSize: 14 + Math.min((e.mentionCount || 1) * 3, 30),
    value: e
  }))
  const links = relations.value.map((r) => ({
    source: String(r.sourceEntityId),
    target: String(r.targetEntityId),
    value: r.relationType
  }))
  const option: any = {
    tooltip: { formatter: (p: any) => (p.dataType === 'node' ? p.data.name : `${p.data.source} -${p.data.value}-> ${p.data.target}`) },
    legend: [{ data: categories.map((c) => c.name), orient: 'vertical', right: 10, top: 20 }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      categories,
      data: nodes,
      links,
      force: { repulsion: 120, edgeLength: 80, gravity: 0.1 },
      label: { show: true, position: 'right', fontSize: 10 },
      lineStyle: { color: 'source', opacity: 0.5, curveness: 0.1 },
      emphasis: { focus: 'adjacency', lineStyle: { width: 3 } }
    }]
  }
  chartInstance.setOption(option, true)
}

/** 节点点击 → 加载邻居 */
function handleNodeClick(entityId: string) {
  getEntityNeighbors(entityId).then((res: any) => {
    neighbors.value = res.data
    const ent = res.data?.entity
    selectedEntityName.value = ent?.entityName || ''
    activeTab.value = 'neighbors'
  })
}

/** 全局检索 */
function handleGlobalSearch() {
  if (!globalQuery.value.trim()) {
    proxy?.$modal.msgWarning('请输入查询问题')
    return
  }
  globalLoading.value = true
  globalSearch(props.knowledgeId, globalQuery.value).then((res: any) => {
    globalResult.value = res.data
  }).finally(() => {
    globalLoading.value = false
  })
}

function statusText(s?: string) {
  return { '0': '未构建', '1': '构建中', '2': '已完成', '3': '失败' }[s || ''] || '未知'
}
function statusType(s?: string): any {
  return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }[s || ''] || 'info'
}
function summaryStatusText(s?: string) {
  return { '0': '待生成', '1': '生成中', '2': '已完成', '3': '失败' }[s || ''] || '未知'
}
function summaryStatusType(s?: string): any {
  return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }[s || ''] || 'info'
}
</script>

<style scoped>
.kg-container { display: flex; flex-direction: column; gap: 8px; }
.kg-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.kg-stats { font-size: 12px; color: var(--el-text-color-secondary); }
.kg-chart { width: 100%; height: 380px; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; }
.kg-empty { height: 380px; display: flex; align-items: center; justify-content: center; }
.kg-tabs { margin-top: 4px; }
.kg-global-result { margin-top: 12px; padding: 10px; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; background: var(--el-fill-color-lighter); }
.kg-global-answer { font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.kg-global-communities { margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.kg-progress-tip { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
