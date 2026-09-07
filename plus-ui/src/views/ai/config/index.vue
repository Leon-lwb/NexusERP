<template>
  <div class="p-2">
    <div class="mx-auto max-w-[1000px]">
      <el-card shadow="never" class="mb-[10px]">
        <div class="text-base font-bold">AI 运行参数</div>
        <div class="mt-1 text-xs text-gray-400">保存后即时生效（无需重启），多节点部署自动同步；yml 中同名配置仅作首次启动兜底默认</div>
      </el-card>

      <el-card v-loading="loading" shadow="never">
        <el-form ref="configFormRef" :model="form" :rules="rules" label-width="150px">
          <el-tabs v-model="activeTab">
            <!-- SQL 安全 -->
            <el-tab-pane label="SQL 安全" name="sql">
              <el-alert
                type="info"
                :closable="false"
                class="mb-4"
                show-icon
                title="约束 AI 数据查询（Agent 模式）的可访问表与资源开销，仅允许 SELECT 查询"
              />
              <el-form-item label="允许表名前缀" prop="sqlSafety.allowedTablePrefixes">
                <el-select
                  v-model="form.sqlSafety.allowedTablePrefixes"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="输入前缀后回车，如 bd_"
                  class="!w-full"
                >
                  <el-option v-for="p in defaultPrefixOptions" :key="p" :label="p" :value="p" />
                </el-select>
                <div class="form-tip">白名单外的前缀一律拒绝查询，修改后需刷新 Schema 缓存（自动触发）</div>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="SQL 超时" prop="sqlSafety.sqlTimeout">
                    <el-input-number
                      v-model="form.sqlSafety.sqlTimeout"
                      :min="100"
                      :max="600000"
                      :step="1000"
                      controls-position="right"
                      class="!w-full"
                    />
                    <div class="form-tip">毫秒，单条查询最长执行时间</div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="最大返回行数" prop="sqlSafety.sqlMaxRows">
                    <el-input-number
                      v-model="form.sqlSafety.sqlMaxRows"
                      :min="1"
                      :max="1000000"
                      :step="100"
                      controls-position="right"
                      class="!w-full"
                    />
                    <div class="form-tip">超出部分截断，控制 Token 消耗</div>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-tab-pane>

            <!-- 向量存储 -->
            <el-tab-pane label="向量存储" name="vector">
              <el-alert
                v-if="vectorStoreChanged"
                type="warning"
                :closable="false"
                class="mb-4"
                show-icon
                title="连接参数已修改：既有向量可能不可检索，相关知识库需重建向量索引"
              />
              <el-alert v-else type="info" :closable="false" class="mb-4" show-icon title="RAG 知识库的向量存储后端，按知识库ID隔离集合" />
              <el-form-item label="存储类型" prop="vectorStore.type">
                <el-radio-group v-model="form.vectorStore.type">
                  <el-radio v-for="dict in ai_vector_store_type" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
                </el-radio-group>
                <div v-if="form.vectorStore.type === 'inmemory'" class="form-tip text-red-400">
                  ⚠️ inmemory 模式重启后向量全部丢失，仅限演示环境，生产必须用 milvus
                </div>
              </el-form-item>
              <el-form-item label="Milvus 地址" prop="vectorStore.milvusUrl">
                <el-input v-model="form.vectorStore.milvusUrl" placeholder="http://localhost:19530" />
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="集合名称前缀" prop="vectorStore.milvusCollectionName">
                    <el-input v-model="form.vectorStore.milvusCollectionName" placeholder="AiKnowledge" />
                    <div class="form-tip">实际集合名 = 前缀_知识库ID</div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="向量维度" prop="vectorStore.milvusDimension">
                    <el-input-number v-model="form.vectorStore.milvusDimension" :min="1" :max="65536" controls-position="right" class="!w-full" />
                    <div class="form-tip">仅作未配置维度的 Embedding 模型兜底</div>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-tab-pane>

            <!-- 联网搜索 -->
            <el-tab-pane label="联网搜索" name="search">
              <el-alert
                type="info"
                :closable="false"
                class="mb-4"
                show-icon
                title="开启后 AI 对话可调用搜索工具获取互联网实时信息（WebSearch 工具）"
              />
              <el-form-item label="启用联网搜索" prop="webSearch.enabled">
                <el-switch v-model="form.webSearch.enabled" />
              </el-form-item>
              <el-form-item label="搜索厂商" prop="webSearch.provider">
                <el-radio-group v-model="form.webSearch.provider">
                  <el-radio value="tavily">Tavily</el-radio>
                  <el-radio value="bing">Bing</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="API Key" prop="webSearch.apiKey">
                <el-input
                  v-model="form.webSearch.apiKey"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  :placeholder="apiKeySet ? '已配置，留空保持不变，输入新值覆盖' : '请输入 API Key'"
                />
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="返回结果数" prop="webSearch.topN">
                    <el-input-number v-model="form.webSearch.topN" :min="1" :max="20" controls-position="right" class="!w-full" />
                    <div class="form-tip">每次搜索返回的网页条数</div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="请求超时" prop="webSearch.timeoutMs">
                    <el-input-number
                      v-model="form.webSearch.timeoutMs"
                      :min="1000"
                      :max="120000"
                      :step="1000"
                      controls-position="right"
                      class="!w-full"
                    />
                    <div class="form-tip">毫秒</div>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-tab-pane>

            <!-- 流程编排 -->
            <el-tab-pane label="流程编排" name="flow">
              <el-alert type="info" :closable="false" class="mb-4" show-icon title="AI 流程编排引擎的执行策略" />
              <el-form-item label="并行分支执行" prop="flow.parallel">
                <el-switch v-model="form.flow.parallel" />
                <div class="form-tip">开启后无依赖的分支并行执行；含循环节点的流程图强制串行。并行度固定为 AI 线程池大小（4）</div>
              </el-form-item>
            </el-tab-pane>

            <!-- 知识图谱 -->
            <el-tab-pane label="知识图谱" name="kg">
              <el-alert type="info" :closable="false" class="mb-4" show-icon title="对知识库文档做实体/关系抽取构建图谱，增强 RAG 检索召回" />
              <el-form-item label="启用知识图谱" prop="knowledgeGraph.enabled">
                <el-switch v-model="form.knowledgeGraph.enabled" />
              </el-form-item>
              <el-form-item label="存储类型" prop="knowledgeGraph.storage">
                <el-radio-group v-model="form.knowledgeGraph.storage">
                  <el-radio value="mysql">MySQL</el-radio>
                  <el-radio value="neo4j" disabled>Neo4j（预留）</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-divider content-position="left">实体 / 关系抽取</el-divider>
              <el-form-item label="抽取用 LLM 模型" prop="knowledgeGraph.extractionModelId">
                <el-select
                  v-model="form.knowledgeGraph.extractionModelId"
                  clearable
                  filterable
                  placeholder="默认使用系统默认对话模型"
                  class="!w-full"
                >
                  <el-option v-for="m in textModels" :key="m.id" :label="m.displayName || m.modelName" :value="m.id!" />
                </el-select>
                <div class="form-tip">建议选择价格便宜的模型，抽取调用量较大</div>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="每批抽取 chunk 数" prop="knowledgeGraph.batchSize">
                    <el-input-number v-model="form.knowledgeGraph.batchSize" :min="1" :max="100" controls-position="right" class="!w-full" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="置信度阈值" prop="knowledgeGraph.confidenceThreshold">
                    <el-input-number
                      v-model="form.knowledgeGraph.confidenceThreshold"
                      :min="0"
                      :max="1"
                      :step="0.1"
                      controls-position="right"
                      class="!w-full"
                    />
                    <div class="form-tip">低于此值的实体/关系丢弃</div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="每 chunk 实体上限" prop="knowledgeGraph.maxEntitiesPerChunk">
                    <el-input-number
                      v-model="form.knowledgeGraph.maxEntitiesPerChunk"
                      :min="1"
                      :max="200"
                      controls-position="right"
                      class="!w-full"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="每 chunk 关系上限" prop="knowledgeGraph.maxRelationsPerChunk">
                    <el-input-number
                      v-model="form.knowledgeGraph.maxRelationsPerChunk"
                      :min="1"
                      :max="200"
                      controls-position="right"
                      class="!w-full"
                    />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-divider content-position="left">检索与社区</el-divider>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="图谱召回权重" prop="knowledgeGraph.graphWeight">
                    <el-input-number
                      v-model="form.knowledgeGraph.graphWeight"
                      :min="0"
                      :max="1"
                      :step="0.1"
                      controls-position="right"
                      class="!w-full"
                    />
                    <div class="form-tip">图谱召回在 RRF 融合中的权重</div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="局部召回邻居上限" prop="knowledgeGraph.maxNeighbors">
                    <el-input-number v-model="form.knowledgeGraph.maxNeighbors" :min="1" :max="200" controls-position="right" class="!w-full" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="标签传播迭代上限" prop="knowledgeGraph.labelPropagationMaxIter">
                    <el-input-number
                      v-model="form.knowledgeGraph.labelPropagationMaxIter"
                      :min="1"
                      :max="100"
                      controls-position="right"
                      class="!w-full"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="社区数上限" prop="knowledgeGraph.maxCommunities">
                    <el-input-number v-model="form.knowledgeGraph.maxCommunities" :min="1" :max="1000" controls-position="right" class="!w-full" />
                    <div class="form-tip">超出按大小合并小社区</div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="全局检索社区上限" prop="knowledgeGraph.maxCommunitiesForGlobal">
                    <el-input-number
                      v-model="form.knowledgeGraph.maxCommunitiesForGlobal"
                      :min="1"
                      :max="1000"
                      controls-position="right"
                      class="!w-full"
                    />
                    <div class="form-tip">超出按 entity_count 取 topN</div>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-tab-pane>
          </el-tabs>
        </el-form>
      </el-card>

      <!-- 吸底操作栏 -->
      <div class="sticky bottom-2 z-10 mt-3">
        <el-card shadow="always">
          <div class="flex items-center justify-between">
            <span class="text-xs text-gray-400">
              {{ isDirty ? '有未保存的更改' : '所有更改已保存' }}
            </span>
            <div>
              <el-button v-if="isDirty" @click="reloadConfig">放弃更改</el-button>
              <el-button v-hasPermi="['ai:config:edit']" type="primary" :loading="saving" :disabled="!isDirty" icon="Check" @click="submitForm">
                保存并生效
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" name="AiConfig">
import { getAiConfig, updateAiConfig } from '@/api/ai/config';
import { AiConfig } from '@/api/ai/config';
import { listModelOptions } from '@/api/ai/model';
import { AiModel } from '@/api/ai/model';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_vector_store_type } = toRefs<any>(proxy?.useDict('ai_vector_store_type'));

const loading = ref(true);
const saving = ref(false);
const activeTab = ref('sql');
const configFormRef = ref<ElFormInstance>();
const textModels = ref<AiModel[]>([]);

/** 联网搜索 Key 是否已配置（后端不回显明文） */
const apiKeySet = ref(false);
/** 加载时的向量库连接快照（检测变更提示重建索引） */
const vectorConnSnapshot = ref('');
/** 已保存配置快照（深度对比感知未保存更改，剥离 apiKey 写专用字段） */
const savedSnapshot = ref('');

const defaultPrefixOptions = ['bd_', 'so_', 'pu_', 'prd_', 'wm_', 'sch_'];

/** 表单校验失败时字段前缀 → Tab 名映射（自动跳转到出错页签） */
const FIELD_TAB_MAP: Record<string, string> = {
  sqlSafety: 'sql',
  vectorStore: 'vector',
  webSearch: 'search',
  flow: 'flow',
  knowledgeGraph: 'kg'
};

const initFormData: AiConfig = {
  sqlSafety: {
    allowedTablePrefixes: [...defaultPrefixOptions],
    sqlTimeout: 5000,
    sqlMaxRows: 1000
  },
  vectorStore: {
    type: 'milvus',
    milvusUrl: 'http://localhost:19530',
    milvusCollectionName: 'AiKnowledge',
    milvusDimension: 1024
  },
  webSearch: {
    enabled: true,
    provider: 'tavily',
    apiKey: '',
    topN: 5,
    timeoutMs: 15000
  },
  flow: {
    parallel: false
  },
  knowledgeGraph: {
    enabled: true,
    storage: 'mysql',
    batchSize: 5,
    maxEntitiesPerChunk: 20,
    maxRelationsPerChunk: 30,
    confidenceThreshold: 0.5,
    extractionModelId: undefined,
    graphWeight: 0.5,
    maxNeighbors: 20,
    labelPropagationMaxIter: 8,
    maxCommunities: 50,
    maxCommunitiesForGlobal: 50
  }
};

const form = reactive<AiConfig>(JSON.parse(JSON.stringify(initFormData)));

const rules = {
  'sqlSafety.allowedTablePrefixes': [{ required: true, message: '允许表名前缀不能为空', trigger: 'change' }],
  'sqlSafety.sqlTimeout': [{ required: true, message: 'SQL 超时不能为空', trigger: 'blur' }],
  'sqlSafety.sqlMaxRows': [{ required: true, message: '最大返回行数不能为空', trigger: 'blur' }],
  'vectorStore.type': [{ required: true, message: '请选择存储类型', trigger: 'change' }],
  'vectorStore.milvusUrl': [{ required: true, message: 'Milvus 地址不能为空', trigger: 'blur' }],
  'vectorStore.milvusCollectionName': [{ required: true, message: '集合名称前缀不能为空', trigger: 'blur' }],
  'vectorStore.milvusDimension': [{ required: true, message: '向量维度不能为空', trigger: 'blur' }],
  'webSearch.provider': [{ required: true, message: '请选择搜索厂商', trigger: 'change' }],
  'knowledgeGraph.storage': [{ required: true, message: '请选择存储类型', trigger: 'change' }]
};

/** 剥离写专用字段后的表单序列化（apiKey 留空表示保留，不参与变更对比） */
const serializeForm = () => {
  const copy = JSON.parse(JSON.stringify(form));
  delete copy.webSearch.apiKey;
  return JSON.stringify(copy);
};

const isDirty = computed(() => serializeForm() !== savedSnapshot.value);

/** 向量库连接是否被修改（类型/地址/集合/维度任一变化） */
const vectorStoreChanged = computed(() => {
  const v = form.vectorStore;
  return `${v.type}|${v.milvusUrl}|${v.milvusCollectionName}|${v.milvusDimension}` !== vectorConnSnapshot.value;
});

/** 应用后端返回的配置到表单并刷新快照 */
const applyConfig = (data: AiConfig) => {
  Object.assign(form, data);
  apiKeySet.value = !!data.webSearch.apiKeySet;
  form.webSearch.apiKey = '';
  vectorConnSnapshot.value = `${data.vectorStore.type}|${data.vectorStore.milvusUrl}|${data.vectorStore.milvusCollectionName}|${data.vectorStore.milvusDimension}`;
  savedSnapshot.value = serializeForm();
};

/** 加载当前生效配置 */
const getConfig = async () => {
  const res = await getAiConfig();
  applyConfig(res.data);
};

/** 放弃更改：重新加载已保存配置 */
const reloadConfig = async () => {
  loading.value = true;
  try {
    await getConfig();
  } finally {
    loading.value = false;
  }
};

/** 拉取对话模型下拉（图谱抽取用，建议便宜模型） */
const loadTextModels = async () => {
  const res = await listModelOptions('TEXT');
  textModels.value = res.data ?? res.rows ?? [];
};

/** 保存并即时生效 */
const submitForm = () => {
  configFormRef.value?.validate(async (valid: boolean, invalidFields: Record<string, { message?: string }[]>) => {
    if (!valid) {
      // 自动跳到第一个出错的页签
      const firstField = Object.keys(invalidFields ?? {})[0];
      if (firstField) {
        activeTab.value = FIELD_TAB_MAP[firstField.split('.')[0]] ?? activeTab.value;
      }
      proxy?.$modal.msgWarning('请检查表单填写');
      return;
    }
    if (vectorStoreChanged.value) {
      await proxy?.$modal.confirm('向量库连接/类型/维度已修改，既有知识库向量可能不可检索，确认保存？');
    }
    const payload: AiConfig = JSON.parse(JSON.stringify(form));
    if (!payload.webSearch.apiKey) {
      // 留空 = 保留原值
      delete payload.webSearch.apiKey;
    }
    saving.value = true;
    try {
      await updateAiConfig(payload);
      proxy?.$modal.msgSuccess('保存成功，配置已即时生效');
      await getConfig();
    } finally {
      saving.value = false;
    }
  });
};

onMounted(async () => {
  loading.value = true;
  try {
    await Promise.all([getConfig(), loadTextModels()]);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.form-tip {
  width: 100%;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
