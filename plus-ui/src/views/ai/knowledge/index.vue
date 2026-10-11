<template>
  <div class="app-container">
    <!-- 查询区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="知识库名称" prop="knowledgeName">
            <el-input v-model="queryParams.knowledgeName" placeholder="请输入知识库名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10">
      <!-- 左侧：知识库列表 -->
      <el-col :span="selectedKnowledge ? 12 : 24">
        <el-card shadow="hover">
          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
            </el-col>
            <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
          </el-row>

          <el-table
            v-loading="loading"
            border
            :data="list"
            @selection-change="handleSelectionChange"
            highlight-current-row
            @current-change="handleCurrentChange"
          >
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column label="知识库名称" align="center" prop="knowledgeName" min-width="160">
              <template #default="scope">
                <span class="knowledge-readable-cell" :title="scope.row.knowledgeName">{{ scope.row.knowledgeName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="描述" align="center" prop="description" min-width="140" :show-overflow-tooltip="true" />
            <el-table-column label="Embedding模型" align="center" prop="embeddingModel" min-width="200">
              <template #default="scope">
                <span class="knowledge-readable-cell" :title="scope.row.embeddingModel">{{ scope.row.embeddingModel }}</span>
              </template>
            </el-table-column>
            <el-table-column label="文档数" align="center" prop="documentCount" width="80" />
            <el-table-column label="状态" align="center" prop="status" width="80">
              <template #default="scope">
                <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
              </template>
            </el-table-column>
            <el-table-column label="向量索引" align="center" prop="vectorIndexStatus" width="90">
              <template #default="scope">
                <dict-tag :options="ai_knowledge_index_status" :value="scope.row.vectorIndexStatus" />
              </template>
            </el-table-column>
            <el-table-column label="创建时间" align="center" prop="createTime" width="180">
              <template #default="scope">
                <span>{{ proxy?.parseTime(scope.row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
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
          <pagination
            v-show="total > 0"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            :total="total"
            @pagination="getList"
          />
        </el-card>
      </el-col>

      <!-- 右侧：文档管理 + 检索测试 -->
      <el-col v-if="selectedKnowledge" :span="12">
        <el-card shadow="hover" class="mb-[10px]">
          <div class="vec-header">
            <span class="font-bold">向量索引</span>
            <dict-tag :options="ai_knowledge_index_status" :value="selectedKnowledge.vectorIndexStatus" />
            <el-button
              v-if="selectedKnowledge.vectorIndexStatus !== 'REBUILDING'"
              v-hasPermi="['ai:knowledge:rebuild']"
              size="small"
              type="warning"
              plain
              icon="RefreshRight"
              @click="handleRebuildIndex"
            >
              重新构建向量索引
            </el-button>
          </div>
          <el-progress v-if="selectedKnowledge.vectorIndexStatus === 'REBUILDING'" :percentage="indexProgress" :stroke-width="14" />
          <div v-if="selectedKnowledge.vectorIndexStatus === 'FAILED' && selectedKnowledge.vectorIndexError" class="vec-error">
            {{ selectedKnowledge.vectorIndexError }}
          </div>
        </el-card>
        <el-tabs v-model="kgTab" class="knowledge-tabs">
          <el-tab-pane label="文档管理" name="doc">
            <el-card shadow="hover">
              <template #header>
                <el-row :gutter="10" class="mb8">
                  <el-col :span="12">
                    <span class="font-bold">{{ selectedKnowledge.knowledgeName }} - 文档管理</span>
                  </el-col>
                  <el-col :span="12" style="text-align: right">
                    <el-upload
                      ref="uploadRef"
                      :show-file-list="false"
                      :before-upload="handleBeforeUpload"
                      :http-request="handleUpload"
                      :multiple="true"
                      accept=".pdf,.docx,.doc,.xlsx,.xls,.txt,.csv,.md,.html"
                    >
                      <el-button type="primary" plain icon="Upload">上传文档</el-button>
                    </el-upload>
                  </el-col>
                </el-row>
              </template>

              <el-table v-loading="docLoading" border :data="documentList">
                <el-table-column label="文件名" align="center" prop="fileName" min-width="260">
                  <template #default="scope">
                    <span class="knowledge-readable-cell" :title="scope.row.fileName">{{ scope.row.fileName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="文件大小" align="center" prop="fileSize" width="100">
                  <template #default="scope">
                    <span>{{ formatFileSize(scope.row.fileSize) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="分段数" align="center" prop="chunkCount" width="80" />
                <el-table-column label="状态" align="center" prop="status" width="100">
                  <template #default="scope">
                    <dict-tag :options="ai_knowledge_document_status" :value="scope.row.status" />
                  </template>
                </el-table-column>
                <el-table-column label="错误信息" align="center" prop="errorMessage" min-width="200" :show-overflow-tooltip="true" />
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
                  <el-input-number
                    v-model="testParams.threshold"
                    :min="0"
                    :max="1"
                    :step="0.1"
                    :precision="2"
                    controls-position="right"
                    style="width: 100px"
                  />
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
              <el-alert v-if="retrievalError" :title="retrievalError" type="error" :closable="false" show-icon />
              <div v-else-if="retrievalResults.length > 0" class="retrieval-results">
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
    <el-dialog v-model="open" :title="title" width="640px" append-to-body @close="syncRebuildPolling()">
      <el-form ref="knowledgeFormRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="知识库名称" prop="knowledgeName">
          <el-input v-model="form.knowledgeName" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="Embedding模型" prop="embeddingModel">
          <el-select v-model="form.embeddingModel" placeholder="请选择 Embedding 模型" style="width: 100%">
            <el-option v-for="item in embeddingModels" :key="item.modelName" :label="item.displayName || item.modelName" :value="item.modelName" />
          </el-select>
          <div v-if="embeddingModels.length === 0" style="color: #f56c6c; font-size: 12px; margin-top: 4px">
            未找到可用的 Embedding 模型，请先到【AI模型管理】中添加 EMBEDDING 类型模型
          </div>
        </el-form-item>
        <el-form-item label="向量存储类型" prop="vectorStore">
          <el-select v-model="form.vectorStore" placeholder="向量存储类型">
            <el-option v-for="dict in ai_vector_store_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
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
    <el-dialog v-model="chunkDialog.visible" title="文档片段" width="640px" append-to-body>
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
import {
  listKnowledge,
  getKnowledge,
  addKnowledge,
  updateKnowledge,
  delKnowledge,
  rebuildKnowledgeIndex,
  listDocumentByKnowledge,
  delDocument,
  uploadDocument,
  listChunkByDocument,
  retryDocument,
  knowledgeRetrieval
} from '@/api/ai/knowledge';
import { listModel } from '@/api/ai/model';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as any;
const { sys_normal_disable, ai_vector_store_type, ai_knowledge_document_status, ai_knowledge_index_status } = toRefs<any>(
  proxy?.useDict('sys_normal_disable', 'ai_vector_store_type', 'ai_knowledge_document_status', 'ai_knowledge_index_status')
);

const knowledgeFormRef = ref<FormInstance>();
const documentList = ref<any[]>([]);
const chunkList = ref<any[]>([]);
const docLoading = ref(false);
const chunkLoading = ref(false);
const selectedKnowledge = ref<any>(null);
const showAdvanced = ref(false);
/** 向量索引重建轮询定时器（仅选中行 REBUILDING 期间存在） */
let rebuildTimer: ReturnType<typeof setInterval> | null = null;

/** 知识图谱 Tab 组件（懒加载，避免首屏加载 echarts） */
const Graph = defineAsyncComponent(() => import('./graph.vue'));
/** 右侧详情当前 Tab */
const kgTab = ref('doc');

// 检索测试相关
const retrievalQuery = ref('');
const retrievalResults = ref<any[]>([]);
const retrievalLoading = ref(false);
const retrievalQueried = ref(false);
const retrievalError = ref('');
/** 仅当前检索可写入面板；切库和卸载也使已发请求失效。 */
let retrievalRequestSeq = 0;
const testParams = reactive({
  topK: 5,
  threshold: 0.5,
  enableHybrid: false,
  enableRerank: false
});

const queryParams = ref<any>({
  pageNum: 1,
  pageSize: 10,
  knowledgeName: undefined,
  status: undefined
});

const rules = ref({
  knowledgeName: [{ required: true, message: '知识库名称不能为空', trigger: 'blur' }],
  embeddingModel: [{ required: true, message: '请选择 Embedding 模型', trigger: 'change' }]
});

/** Embedding 模型列表 */
const embeddingModels = ref<any[]>([]);

/** 加载 Embedding 模型列表 */
function loadEmbeddingModels() {
  listModel({ modelType: 'EMBEDDING', status: '0', pageNum: 1, pageSize: 100 }).then((res: any) => {
    embeddingModels.value = res.rows || [];
  });
}

onUnmounted(() => {
  retrievalRequestSeq++;
  stopRebuildPolling();
});

const chunkDialog = reactive<{ visible: boolean }>({
  visible: false
});

const {
  loading,
  list,
  total,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm: rawSubmitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listKnowledge, get: getKnowledge, add: addKnowledge, update: updateKnowledge, del: delKnowledge },
  idField: 'id',
  entityName: '知识库',
  formRef: knowledgeFormRef,
  queryParams,
  initForm: () => ({
    id: undefined,
    knowledgeName: undefined,
    description: undefined,
    embeddingModel: undefined,
    vectorStore: 'milvus',
    status: '0',
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
  }),
  onReset: () => {
    showAdvanced.value = false;
  },
  afterLoad: () => {
    // 确保高级配置字段有默认值
    if (form.value.separator === undefined) form.value.separator = '#';
    if (form.value.overlapChar === undefined) form.value.overlapChar = 30;
    if (form.value.textBlockSize === undefined) form.value.textBlockSize = 500;
    if (form.value.retrieveLimit === undefined) form.value.retrieveLimit = 5;
    if (form.value.similarityThreshold === undefined) form.value.similarityThreshold = 0.5;
    if (form.value.enableRerank === undefined) form.value.enableRerank = 0;
    if (form.value.enableHybrid === undefined) form.value.enableHybrid = 0;
    if (form.value.hybridAlpha === undefined) form.value.hybridAlpha = 0.5;
    // 如果有高级配置字段则展开
    if (
      form.value.separator !== '#' ||
      form.value.overlapChar !== 30 ||
      form.value.textBlockSize !== 500 ||
      form.value.enableRerank === 1 ||
      form.value.enableHybrid === 1 ||
      form.value.systemPrompt
    ) {
      showAdvanced.value = true;
    }
  }
});

/** 新增（预加载 Embedding 模型候选） */
function handleAdd() {
  rawHandleAdd(() => {
    loadEmbeddingModels();
  });
}

/** 修改（预加载 Embedding 模型候选） */
function handleUpdate(row: any) {
  rawHandleUpdate(row, () => {
    loadEmbeddingModels();
  });
}

/**
 * 提交（修改走向量索引安全门）
 * <p>关键配置（Embedding 模型/维度/存储类型）变更且已有片段时，后端返回错误令牌
 * REBUILD_INDEX_REQUIRED；此时弹确认框，确认后携带 rebuildIndex=true 重发保存。</p>
 */
function submitForm() {
  knowledgeFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    if (form.value.id) {
      try {
        await updateKnowledge(form.value as any);
      } catch (e: any) {
        if (e?.message !== 'REBUILD_INDEX_REQUIRED') {
          // updateKnowledge 关闭了统一错误 toast（ignoreErrorMsg），此处需自行提示保存失败
          proxy?.$modal.msgError(e?.message || '修改知识库失败');
          return;
        }
        try {
          await proxy?.$modal.confirm('关键配置（Embedding 模型/维度/存储类型）已变更，保存后需重建向量索引（重新嵌入全部片段）才能生效。是否继续？');
        } catch {
          return; // 用户取消重建，保留弹窗供调整
        }
        try {
          await updateKnowledge({ ...form.value, rebuildIndex: true } as any);
        } catch (e: any) {
          proxy?.$modal.msgError(e?.message || '修改知识库失败');
          return;
        }
      }
      proxy?.$modal.msgSuccess('修改成功');
    } else {
      await addKnowledge(form.value as any);
      proxy?.$modal.msgSuccess('新增成功');
    }
    open.value = false;
    getList();
    syncRebuildPolling();
  });
}

/** 触发向量索引重建（异步执行，返回后进入 REBUILDING 并开始轮询） */
async function handleRebuildIndex() {
  const row: any = selectedKnowledge.value;
  if (!row?.id) return;
  try {
    await proxy?.$modal.confirm(`将重建知识库"${row.knowledgeName}"的向量索引（对全部片段重新嵌入，期间检索结果可能不完整），是否继续？`);
  } catch {
    return;
  }
  await rebuildKnowledgeIndex(row.id);
  proxy?.$modal.msgSuccess('已开始重建向量索引');
  const res: any = await getKnowledge(row.id);
  Object.assign(row, res.data);
  syncRebuildPolling();
}

/** 向量索引重建进度（0-100，钳制边界） */
const indexProgress = computed(() => {
  const p = Number(selectedKnowledge.value?.vectorIndexProgress);
  if (!Number.isFinite(p) || p < 0) return 0;
  return Math.min(100, Math.round(p));
});

/** 停止向量索引重建轮询 */
function stopRebuildPolling() {
  if (rebuildTimer) {
    clearInterval(rebuildTimer);
    rebuildTimer = null;
  }
}

/** 选中行处于 REBUILDING 时开启轮询（READY/FAILED 等状态自动停止） */
function syncRebuildPolling() {
  const status = selectedKnowledge.value?.vectorIndexStatus;
  if (status === 'REBUILDING' && !rebuildTimer) {
    rebuildTimer = setInterval(async () => {
      const id = selectedKnowledge.value?.id;
      if (!id) {
        stopRebuildPolling();
        return;
      }
      try {
        const res: any = await getKnowledge(id);
        const fresh = res.data;
        if (!fresh) return;
        // 等待响应期间选中行可能已切换，丢弃过期数据
        if (selectedKnowledge.value?.id !== id) return;
        if (fresh.vectorIndexStatus !== 'REBUILDING') {
          stopRebuildPolling();
        }
        Object.assign(selectedKnowledge.value, fresh);
        const row = list.value.find((item: any) => item.id === fresh.id);
        if (row) Object.assign(row, fresh);
        if (fresh.vectorIndexStatus === 'READY') {
          proxy?.$modal.msgSuccess('向量索引重建完成');
        } else if (fresh.vectorIndexStatus === 'FAILED') {
          proxy?.$modal.msgError('向量索引重建失败');
        }
      } catch {
        stopRebuildPolling();
      }
    }, 2000);
  } else if (status !== 'REBUILDING') {
    stopRebuildPolling();
  }
}

/** 重置查询 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 选中知识库行 */
function handleCurrentChange(row: any) {
  // 切换选中行时停掉旧行的索引重建轮询
  stopRebuildPolling();
  selectedKnowledge.value = row;
  // 切库使旧检索失效，不能把旧响应或加载态带到新库。
  retrievalRequestSeq++;
  retrievalResults.value = [];
  retrievalQueried.value = false;
  retrievalError.value = '';
  retrievalLoading.value = false;
  retrievalQuery.value = '';
  if (row) {
    testParams.topK = row.retrieveLimit || 5;
    testParams.threshold = row.similarityThreshold || 0.5;
    testParams.enableHybrid = row.enableHybrid === 1;
    testParams.enableRerank = row.enableRerank === 1;
    loadDocuments(row.id);
    syncRebuildPolling();
  }
}

/** 加载文档列表 */
function loadDocuments(knowledgeId: number) {
  docLoading.value = true;
  listDocumentByKnowledge(knowledgeId).then((res: any) => {
    documentList.value = res.data;
    docLoading.value = false;
  });
}

/** 删除文档 */
function handleDeleteDoc(row: any) {
  proxy?.$modal
    .confirm(`是否确认删除文档"${row.fileName}"？`)
    .then(() => {
      return delDocument([row.id]);
    })
    .then(() => {
      if (selectedKnowledge.value) {
        loadDocuments(selectedKnowledge.value.id);
      }
      proxy?.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 重试文档 */
function handleRetryDoc(row: any) {
  proxy?.$modal
    .confirm(`是否重新处理文档"${row.fileName}"？`)
    .then(() => {
      return retryDocument(row.id);
    })
    .then(() => {
      proxy?.$modal.msgSuccess('已提交重新处理');
      if (selectedKnowledge.value) {
        setTimeout(() => {
          loadDocuments(selectedKnowledge.value.id);
        }, 1000);
      }
    })
    .catch(() => {});
}

/** 上传前校验 */
function handleBeforeUpload(file: any) {
  const maxSize = 50 * 1024 * 1024; // 50MB
  if (file.size > maxSize) {
    proxy?.$modal.msgError('文件大小不能超过 50MB');
    return false;
  }
  return true;
}

/** 自定义上传 */
function handleUpload(params: any) {
  if (!selectedKnowledge.value) return;
  return uploadDocument(selectedKnowledge.value.id, params.file)
    .then(() => {
      proxy?.$modal.msgSuccess('文档上传成功，正在后台解析中...');
      // 延迟刷新文档列表
      setTimeout(() => {
        if (selectedKnowledge.value) {
          loadDocuments(selectedKnowledge.value.id);
        }
      }, 2000);
    })
    .catch(() => {
      proxy?.$modal.msgError('文档上传失败');
    });
}

/** 查看片段 */
function handleViewChunks(row: any) {
  chunkLoading.value = true;
  chunkDialog.visible = true;
  listChunkByDocument(row.id).then((res: any) => {
    chunkList.value = res.data;
    chunkLoading.value = false;
  });
}

/** 检索测试 */
function handleRetrievalTest() {
  if (!retrievalQuery.value.trim()) {
    proxy?.$modal.msgWarning('请输入检索文本');
    return;
  }
  if (!selectedKnowledge.value) return;
  const requestSeq = ++retrievalRequestSeq;
  const knowledgeId = selectedKnowledge.value.id;
  const isCurrentRequest = () => requestSeq === retrievalRequestSeq && selectedKnowledge.value?.id === knowledgeId;
  retrievalLoading.value = true;
  retrievalQueried.value = false;
  retrievalError.value = '';
  retrievalResults.value = [];
  return knowledgeRetrieval({
    knowledgeId,
    query: retrievalQuery.value,
    topK: testParams.topK,
    threshold: testParams.threshold,
    enableRerank: testParams.enableRerank,
    rerankModel: selectedKnowledge.value.rerankModel || undefined,
    enableHybrid: testParams.enableHybrid,
    hybridAlpha: selectedKnowledge.value.hybridAlpha || 0.5
  })
    .then((res: any) => {
      if (!isCurrentRequest()) return;
      // 只有合法数组才是成功结果，缺失或异常信封不能降级为“无命中”。
      if (!Array.isArray(res.data)) throw new Error('检索响应格式异常');
      retrievalResults.value = res.data;
      retrievalQueried.value = true;
    })
    .catch(() => {
      if (!isCurrentRequest()) return;
      retrievalResults.value = [];
      retrievalError.value = '检索失败，请稍后重试';
    })
    .finally(() => {
      if (isCurrentRequest()) retrievalLoading.value = false;
    });
}

/** 格式化文件大小 */
function formatFileSize(bytes: number) {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return size.toFixed(1) + ' ' + units[i];
}

getList();
</script>

<style scoped>
/* 正式名称与模型标识完整展示；半屏表格保留最小列宽，长文本自然换行。 */
.knowledge-readable-cell {
  display: block;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
  line-height: 1.5;
}

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

.vec-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.vec-error {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
  word-break: break-all;
}
</style>
