<template>
  <div class="p-2">
    <!-- 搜索区域 -->
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="知识库名称" prop="knowledgeName">
              <el-input v-model="queryParams.knowledgeName" placeholder="请输入知识库名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="状态" clearable>
                <el-option label="正常" value="1" />
                <el-option label="停用" value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>

    <el-row :gutter="10">
      <!-- 左侧：知识库列表 -->
      <el-col :span="selectedKnowledge ? 12 : 24">
        <el-card shadow="hover">
          <template #header>
            <el-row :gutter="10" class="mb8">
              <el-col :span="1.5">
                <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
              </el-col>
              <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
            </el-row>
          </template>

          <el-table v-loading="loading" border :data="knowledgeList" @selection-change="handleSelectionChange" highlight-current-row @current-change="handleCurrentChange">
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column label="知识库名称" align="center" prop="knowledgeName" :show-overflow-tooltip="true" />
            <el-table-column label="描述" align="center" prop="description" :show-overflow-tooltip="true" />
            <el-table-column label="Embedding模型" align="center" prop="embeddingModel" :show-overflow-tooltip="true" />
            <el-table-column label="文档数" align="center" prop="documentCount" width="80" />
            <el-table-column label="状态" align="center" prop="status" width="80">
              <template #default="scope">
                <el-tag :type="scope.row.status === '1' ? 'success' : 'danger'">{{ scope.row.status === '1' ? '正常' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" align="center" prop="createTime" width="180">
              <template #default="scope">
                <span>{{ proxy?.parseTime(scope.row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-tooltip content="修改" placement="top">
                  <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
        </el-card>
      </el-col>

      <!-- 右侧：文档管理 + 检索测试 -->
      <el-col v-if="selectedKnowledge" :span="12">
        <el-tabs v-model="kgTab" class="knowledge-tabs">
          <el-tab-pane label="文档管理" name="doc">
            <el-card shadow="hover">
          <template #header>
            <el-row :gutter="10" class="mb8">
              <el-col :span="12">
                <span class="font-bold">{{ selectedKnowledge.knowledgeName }} - 文档管理</span>
              </el-col>
              <el-col :span="12" style="text-align: right">
                <el-upload ref="uploadRef" :show-file-list="false" :before-upload="handleBeforeUpload" :http-request="handleUpload" :multiple="true" accept=".pdf,.docx,.doc,.xlsx,.xls,.txt,.csv,.md,.html">
                  <el-button type="primary" plain icon="Upload">上传文档</el-button>
                </el-upload>
              </el-col>
            </el-row>
          </template>

          <el-table v-loading="docLoading" border :data="documentList">
            <el-table-column label="文件名" align="center" prop="fileName" :show-overflow-tooltip="true" />
            <el-table-column label="文件大小" align="center" prop="fileSize" width="100">
              <template #default="scope">
                <span>{{ formatFileSize(scope.row.fileSize) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分段数" align="center" prop="chunkCount" width="80" />
            <el-table-column label="状态" align="center" prop="status" width="100">
              <template #default="scope">
                <el-tag :type="docStatusType(scope.row.status)">{{ docStatusText(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="错误信息" align="center" prop="errorMessage" :show-overflow-tooltip="true" />
            <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-tooltip content="查看片段" placement="top">
                  <el-button link type="primary" icon="View" @click="handleViewChunks(scope.row)"></el-button>
                </el-tooltip>
                <el-tooltip v-if="scope.row.status === '3' || scope.row.status === '0'" content="重试" placement="top">
                  <el-button link type="warning" icon="RefreshRight" @click="handleRetryDoc(scope.row)"></el-button>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button link type="primary" icon="Delete" @click="handleDeleteDoc(scope.row)"></el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
            </el-card>
          </el-tab-pane>
          <el-tab-pane label="检索测试" name="retrieval">
            <el-card shadow="hover">
          <template #header>
            <span class="font-bold">检索测试</span>
          </template>
          <!-- 参数控制 -->
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item label="Top K">
              <el-input-number v-model="testParams.topK" :min="1" :max="20" controls-position="right" style="width: 100px" />
            </el-form-item>
            <el-form-item label="阈值">
              <el-input-number v-model="testParams.threshold" :min="0" :max="1" :step="0.1" :precision="2" controls-position="right" style="width: 100px" />
            </el-form-item>
            <el-form-item label="混合检索">
              <el-switch v-model="testParams.enableHybrid" />
            </el-form-item>
            <el-form-item label="重排序">
              <el-switch v-model="testParams.enableRerank" />
            </el-form-item>
          </el-form>
          <el-form @submit.prevent>
            <el-form-item style="flex: 1">
              <el-input v-model="retrievalQuery" placeholder="输入检索文本" clearable @keyup.enter="handleRetrievalTest" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" :loading="retrievalLoading" @click="handleRetrievalTest">测试</el-button>
            </el-form-item>
          </el-form>
          <div v-if="retrievalResults.length > 0" class="retrieval-results">
            <div v-for="(item, index) in retrievalResults" :key="index" class="retrieval-result-item">
              <div class="result-header">
                <el-tag size="small" type="info">#{{ index + 1 }}</el-tag>
              </div>
              <div class="result-content">{{ typeof item === 'string' ? item : item.content }}</div>
            </div>
          </div>
          <el-empty v-else-if="retrievalQueried" description="未检索到相关内容" />
            </el-card>
          </el-tab-pane>
          <el-tab-pane label="知识图谱" name="graph">
            <Graph :knowledge-id="selectedKnowledge.id" />
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>

    <!-- 添加或修改知识库对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="600px" append-to-body>
      <el-form ref="knowledgeFormRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="知识库名称" prop="knowledgeName">
          <el-input v-model="form.knowledgeName" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="Embedding模型" prop="embeddingModel">
          <el-select v-model="form.embeddingModel" placeholder="请选择 Embedding 模型" style="width: 100%">
            <el-option
              v-for="item in embeddingModels"
              :key="item.modelName"
              :label="item.displayName || item.modelName"
              :value="item.modelName"
            />
          </el-select>
          <div v-if="embeddingModels.length === 0" style="color: #f56c6c; font-size: 12px; margin-top: 4px">
            未找到可用的 Embedding 模型，请先到【AI模型管理】中添加 EMBEDDING 类型模型
          </div>
        </el-form-item>
        <el-form-item label="向量存储类型" prop="vectorStore">
          <el-select v-model="form.vectorStore" placeholder="向量存储类型">
            <el-option label="Milvus" value="milvus" />
            <el-option label="InMemory" value="inmemory" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="1">正常</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 高级配置 -->
        <el-divider>
          <el-link type="primary" :underline="false" @click="showAdvanced = !showAdvanced">
            <el-icon><component :is="showAdvanced ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
            高级配置
          </el-link>
        </el-divider>
        <div v-show="showAdvanced">
          <el-form-item label="分段分隔符" prop="separator">
            <el-input v-model="form.separator" placeholder="默认 #" />
          </el-form-item>
          <el-form-item label="重叠字符数" prop="overlapChar">
            <el-input-number v-model="form.overlapChar" :min="0" :max="1000" controls-position="right" />
          </el-form-item>
          <el-form-item label="文本块大小" prop="textBlockSize">
            <el-input-number v-model="form.textBlockSize" :min="100" :max="10000" :step="100" controls-position="right" />
          </el-form-item>
          <el-form-item label="检索返回条数" prop="retrieveLimit">
            <el-input-number v-model="form.retrieveLimit" :min="1" :max="50" controls-position="right" />
          </el-form-item>
          <el-form-item label="相似度阈值" prop="similarityThreshold">
            <el-slider v-model="form.similarityThreshold" :min="0" :max="1" :step="0.01" show-input input-size="small" />
          </el-form-item>
          <el-form-item label="启用重排序" prop="enableRerank">
            <el-switch v-model="form.enableRerank" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item v-if="form.enableRerank === 1" label="重排序模型" prop="rerankModel">
            <el-input v-model="form.rerankModel" placeholder="请输入重排序模型名称" />
          </el-form-item>
          <el-form-item label="启用混合检索" prop="enableHybrid">
            <el-switch v-model="form.enableHybrid" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item v-if="form.enableHybrid === 1" label="混合检索权重" prop="hybridAlpha">
            <el-slider v-model="form.hybridAlpha" :min="0" :max="1" :step="0.01" show-input input-size="small" />
          </el-form-item>
          <el-form-item label="系统提示词" prop="systemPrompt">
            <el-input v-model="form.systemPrompt" type="textarea" :rows="3" placeholder="请输入系统提示词" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 片段查看对话框 -->
    <el-dialog v-model="chunkDialog.visible" title="文档片段" width="700px" append-to-body>
      <el-table v-loading="chunkLoading" border :data="chunkList">
        <el-table-column label="序号" align="center" prop="chunkIndex" width="70" />
        <el-table-column label="片段内容" prop="content" :show-overflow-tooltip="false">
          <template #default="scope">
            <div style="max-height: 120px; overflow-y: auto; white-space: pre-wrap; font-size: 12px">{{ scope.row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="向量ID" align="center" prop="vectorId" width="120" :show-overflow-tooltip="true" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Knowledge">
import { listKnowledge, getKnowledge, addKnowledge, updateKnowledge, delKnowledge, listDocumentByKnowledge, delDocument, uploadDocument, listChunkByDocument, retryDocument, knowledgeRetrieval } from '@/api/ai/knowledge'
import { listModel } from '@/api/ai/model'
import { ComponentInternalInstance } from 'vue'

const { proxy } = getCurrentInstance() as ComponentInternalInstance

const knowledgeList = ref<any[]>([])
const documentList = ref<any[]>([])
const chunkList = ref<any[]>([])
const loading = ref(false)
const docLoading = ref(false)
const chunkLoading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const ids = ref<Array<number | string>>([])
const single = ref(true)
const multiple = ref(true)
const selectedKnowledge = ref<any>(null)
const showAdvanced = ref(false)

/** 知识图谱 Tab 组件（懒加载，避免首屏加载 echarts） */
const Graph = defineAsyncComponent(() => import('./graph.vue'))
/** 右侧详情当前 Tab */
const kgTab = ref('doc')

// 检索测试相关
const retrievalQuery = ref('')
const retrievalResults = ref<any[]>([])
const retrievalLoading = ref(false)
const retrievalQueried = ref(false)
const testParams = reactive({
  topK: 5,
  threshold: 0.5,
  enableHybrid: false,
  enableRerank: false
})

const queryParams = ref<any>({
  pageNum: 1,
  pageSize: 10,
  knowledgeName: undefined,
  status: undefined
})

const form = ref<any>({})
const rules = ref({
  knowledgeName: [{ required: true, message: '知识库名称不能为空', trigger: 'blur' }],
  embeddingModel: [{ required: true, message: '请选择 Embedding 模型', trigger: 'change' }]
})

/** Embedding 模型列表 */
const embeddingModels = ref<any[]>([])

/** 加载 Embedding 模型列表 */
function loadEmbeddingModels() {
  listModel({ modelType: 'EMBEDDING', status: '1', pageNum: 1, pageSize: 100 }).then((res: any) => {
    embeddingModels.value = res.rows || []
  })
}

const dialog = reactive<{ visible: boolean; title: string }>({
  visible: false,
  title: ''
})

const chunkDialog = reactive<{ visible: boolean }>({
  visible: false
})

/** 查询知识库列表 */
function getList() {
  loading.value = true
  listKnowledge(queryParams.value).then((res: any) => {
    knowledgeList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置 */
function resetQuery() {
  proxy?.resetForm('queryFormRef')
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 选中知识库行 */
function handleCurrentChange(row: any) {
  selectedKnowledge.value = row
  // 切换知识库时清空检索结果，重置测试参数为知识库配置
  retrievalResults.value = []
  retrievalQueried.value = false
  retrievalQuery.value = ''
  if (row) {
    testParams.topK = row.retrieveLimit || 5
    testParams.threshold = row.similarityThreshold || 0.5
    testParams.enableHybrid = row.enableHybrid === 1
    testParams.enableRerank = row.enableRerank === 1
    loadDocuments(row.id)
  }
}

/** 加载文档列表 */
function loadDocuments(knowledgeId: number) {
  docLoading.value = true
  listDocumentByKnowledge(knowledgeId).then((res: any) => {
    documentList.value = res.data
    docLoading.value = false
  })
}

/** 重置表单 */
function reset() {
  form.value = {
    id: undefined,
    knowledgeName: undefined,
    description: undefined,
    embeddingModel: undefined,
    vectorStore: 'milvus',
    status: '1',
    separator: '#',
    overlapChar: 30,
    textBlockSize: 500,
    retrieveLimit: 5,
    similarityThreshold: 0.5,
    enableRerank: 0,
    rerankModel: undefined,
    enableHybrid: 0,
    hybridAlpha: 0.5,
    systemPrompt: undefined
  }
  showAdvanced.value = false
  proxy?.resetForm('knowledgeFormRef')
}

/** 新增 */
function handleAdd() {
  reset()
  loadEmbeddingModels()
  dialog.visible = true
  dialog.title = '新增知识库'
}

/** 修改 */
function handleUpdate(row: any) {
  reset()
  loadEmbeddingModels()
  getKnowledge(row.id).then((res: any) => {
    form.value = res.data
    // 确保高级配置字段有默认值
    if (form.value.separator === undefined) form.value.separator = '#'
    if (form.value.overlapChar === undefined) form.value.overlapChar = 30
    if (form.value.textBlockSize === undefined) form.value.textBlockSize = 500
    if (form.value.retrieveLimit === undefined) form.value.retrieveLimit = 5
    if (form.value.similarityThreshold === undefined) form.value.similarityThreshold = 0.5
    if (form.value.enableRerank === undefined) form.value.enableRerank = 0
    if (form.value.enableHybrid === undefined) form.value.enableHybrid = 0
    if (form.value.hybridAlpha === undefined) form.value.hybridAlpha = 0.5
    // 如果有高级配置字段则展开
    if (form.value.separator !== '#' || form.value.overlapChar !== 30 || form.value.textBlockSize !== 500 || form.value.enableRerank === 1 || form.value.enableHybrid === 1 || form.value.systemPrompt) {
      showAdvanced.value = true
    }
    dialog.visible = true
    dialog.title = '修改知识库'
  })
}

/** 提交 */
function submitForm() {
  (proxy as any).$refs['knowledgeFormRef'].validate((valid: boolean) => {
    if (valid) {
      if (form.value.id) {
        updateKnowledge(form.value).then(() => {
          proxy?.$modal.msgSuccess('修改成功')
          dialog.visible = false
          getList()
        })
      } else {
        addKnowledge(form.value).then(() => {
          proxy?.$modal.msgSuccess('新增成功')
          dialog.visible = false
          getList()
        })
      }
    }
  })
}

/** 删除知识库 */
function handleDelete(row?: any) {
  const deleteIds = row?.id ? [row.id] : ids.value
  proxy?.$modal.confirm('是否确认删除所选知识库？').then(() => {
    return delKnowledge(deleteIds)
  }).then(() => {
    getList()
    proxy?.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 删除文档 */
function handleDeleteDoc(row: any) {
  proxy?.$modal.confirm(`是否确认删除文档"${row.fileName}"？`).then(() => {
    return delDocument([row.id])
  }).then(() => {
    if (selectedKnowledge.value) {
      loadDocuments(selectedKnowledge.value.id)
    }
    proxy?.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 重试文档 */
function handleRetryDoc(row: any) {
  proxy?.$modal.confirm(`是否重新处理文档"${row.fileName}"？`).then(() => {
    return retryDocument(row.id)
  }).then(() => {
    proxy?.$modal.msgSuccess('已提交重新处理')
    if (selectedKnowledge.value) {
      setTimeout(() => {
        loadDocuments(selectedKnowledge.value.id)
      }, 1000)
    }
  }).catch(() => {})
}

/** 上传前校验 */
function handleBeforeUpload(file: any) {
  const maxSize = 50 * 1024 * 1024 // 50MB
  if (file.size > maxSize) {
    proxy?.$modal.msgError('文件大小不能超过 50MB')
    return false
  }
  return true
}

/** 自定义上传 */
function handleUpload(params: any) {
  if (!selectedKnowledge.value) return
  uploadDocument(selectedKnowledge.value.id, params.file).then(() => {
    proxy?.$modal.msgSuccess('文档上传成功，正在后台解析中...')
    // 延迟刷新文档列表
    setTimeout(() => {
      if (selectedKnowledge.value) {
        loadDocuments(selectedKnowledge.value.id)
      }
    }, 2000)
  }).catch(() => {
    proxy?.$modal.msgError('文档上传失败')
  })
}

/** 查看片段 */
function handleViewChunks(row: any) {
  chunkLoading.value = true
  chunkDialog.visible = true
  listChunkByDocument(row.id).then((res: any) => {
    chunkList.value = res.data
    chunkLoading.value = false
  })
}

/** 检索测试 */
function handleRetrievalTest() {
  if (!retrievalQuery.value.trim()) {
    proxy?.$modal.msgWarning('请输入检索文本')
    return
  }
  if (!selectedKnowledge.value) return
  retrievalLoading.value = true
  retrievalQueried.value = true
  knowledgeRetrieval({
    knowledgeId: selectedKnowledge.value.id,
    query: retrievalQuery.value,
    topK: testParams.topK,
    threshold: testParams.threshold,
    enableRerank: testParams.enableRerank,
    rerankModel: selectedKnowledge.value.rerankModel || undefined,
    enableHybrid: testParams.enableHybrid,
    hybridAlpha: selectedKnowledge.value.hybridAlpha || 0.5
  }).then((res: any) => {
    retrievalResults.value = res.data || []
  }).catch(() => {
    retrievalResults.value = []
  }).finally(() => {
    retrievalLoading.value = false
  })
}

/** 格式化文件大小 */
function formatFileSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(1) + ' ' + units[i]
}

/** 文档状态标签类型 */
function docStatusType(status: string) {
  switch (status) {
    case '0': return 'info'
    case '1': return 'warning'
    case '2': return 'success'
    case '3': return 'danger'
    default: return 'info'
  }
}

/** 文档状态文本 */
function docStatusText(status: string) {
  switch (status) {
    case '0': return '待解析'
    case '1': return '解析中'
    case '2': return '已完成'
    case '3': return '失败'
    default: return '未知'
  }
}

/** 取消 */
function cancel() {
  dialog.visible = false
  reset()
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.retrieval-results {
  max-height: 400px;
  overflow-y: auto;
}
.retrieval-result-item {
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background-color: var(--el-fill-color-lighter);
}
.retrieval-result-item:last-child {
  margin-bottom: 0;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.result-doc-name {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.result-content {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
}
</style>
