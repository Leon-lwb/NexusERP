<template>
  <div class="p-2">
    <el-row :gutter="20">
      <!-- 左侧：分组导航 + 操作 -->
      <el-col :span="5" :xs="24">
        <el-card shadow="never" class="config-side">
          <template #header>
            <span>AI 参数设置</span>
          </template>
          <el-menu :default-active="activeSection" style="border-right: none" @select="handleMenuSelect">
            <el-menu-item v-for="g in groups" :key="g.key" :index="g.key">
              <el-icon><component :is="g.icon" /></el-icon>
              <span>{{ g.label }}</span>
            </el-menu-item>
          </el-menu>
          <el-divider />
          <div class="side-actions">
            <el-tag v-if="isDirty" type="warning" effect="plain">有未保存更改</el-tag>
            <el-tag v-else type="success" effect="plain">配置已同步</el-tag>
            <el-button icon="Refresh" :disabled="!isDirty || loading" @click="reloadConfig">重置</el-button>
            <el-button
              v-hasPermi="['ai:config:edit']"
              type="primary"
              icon="Check"
              :loading="saving"
              :disabled="!isDirty || loading"
              @click="submitForm"
            >
              保存并生效
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：分组表单 -->
      <el-col :span="19" :xs="24">
        <el-card shadow="never">
          <el-form v-loading="loading" ref="configFormRef" :model="form" :rules="rules" label-width="120px">
            <el-alert
              class="mb-4"
              type="info"
              show-icon
              :closable="false"
              title="保存后即时生效（无需重启），多节点部署自动同步；yml 中同名配置仅作首次启动兜底默认值"
            />

            <!-- SQL 安全 -->
            <section v-show="activeSection === 'sql'">
              <el-divider content-position="left">SQL 安全（AI 数据查询）</el-divider>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="允许表名前缀" prop="sqlSafety.allowedTablePrefixes">
                    <el-select
                      v-model="form.sqlSafety.allowedTablePrefixes"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      placeholder="输入前缀后回车，如 bd_"
                      style="width: 100%"
                    >
                      <el-option v-for="p in defaultPrefixOptions" :key="p" :label="p" :value="p" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="SQL 超时(ms)" prop="sqlSafety.sqlTimeout">
                    <el-input-number
                      v-model="form.sqlSafety.sqlTimeout"
                      :min="100"
                      :max="600000"
                      :step="1000"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="最大返回行数" prop="sqlSafety.sqlMaxRows">
                    <el-input-number
                      v-model="form.sqlSafety.sqlMaxRows"
                      :min="1"
                      :max="1000000"
                      :step="100"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </section>

            <!-- 向量存储 -->
            <section v-show="activeSection === 'vector'">
              <el-divider content-position="left">向量存储</el-divider>
              <el-alert
                v-if="vectorStoreChanged"
                type="warning"
                show-icon
                :closable="false"
                title="连接参数已修改：既有向量可能不可检索，相关知识库需重建向量索引"
              />
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="存储类型" prop="vectorStore.type">
                    <el-radio-group v-model="form.vectorStore.type">
                      <el-radio v-for="dict in ai_vector_store_type" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="向量维度" prop="vectorStore.milvusDimension">
                    <el-input-number v-model="form.vectorStore.milvusDimension" :min="1" :max="65536" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row v-if="form.vectorStore.type === 'inmemory'" :gutter="20">
                <el-col :span="24">
                  <el-alert
                    type="error"
                    show-icon
                    :closable="false"
                    title="inmemory 模式重启后向量全部丢失，仅限无 Milvus 环境演示使用，生产必须用 milvus"
                  />
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="Milvus 地址" prop="vectorStore.milvusUrl">
                    <el-input v-model="form.vectorStore.milvusUrl" placeholder="http://localhost:19530" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="集合名称前缀" prop="vectorStore.milvusCollectionName">
                    <el-input v-model="form.vectorStore.milvusCollectionName" placeholder="AiKnowledge" />
                  </el-form-item>
                </el-col>
              </el-row>
            </section>

            <!-- 联网搜索 -->
            <section v-show="activeSection === 'search'">
              <el-divider content-position="left">联网搜索</el-divider>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="启用联网搜索" prop="webSearch.enabled">
                    <el-switch v-model="form.webSearch.enabled" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="搜索厂商" prop="webSearch.provider">
                    <el-radio-group v-model="form.webSearch.provider">
                      <el-radio value="tavily">Tavily</el-radio>
                      <el-radio value="bing">Bing</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="API Key" prop="webSearch.apiKey">
                    <el-input
                      v-model="form.webSearch.apiKey"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      :placeholder="apiKeySet ? '已配置，留空保持不变，输入新值覆盖' : '请输入 API Key'"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="返回结果数" prop="webSearch.topN">
                    <el-input-number v-model="form.webSearch.topN" :min="1" :max="20" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="请求超时(ms)" prop="webSearch.timeoutMs">
                    <el-input-number
                      v-model="form.webSearch.timeoutMs"
                      :min="1000"
                      :max="120000"
                      :step="1000"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </section>

            <!-- 流程编排 -->
            <section v-show="activeSection === 'flow'">
              <el-divider content-position="left">流程编排</el-divider>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="并行分支执行" prop="flow.parallel">
                    <el-switch v-model="form.flow.parallel" />
                  </el-form-item>
                </el-col>
              </el-row>
            </section>

            <!-- 知识图谱 -->
            <section v-show="activeSection === 'kg'">
              <el-divider content-position="left">知识图谱</el-divider>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="启用知识图谱" prop="knowledgeGraph.enabled">
                    <el-switch v-model="form.knowledgeGraph.enabled" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="存储类型" prop="knowledgeGraph.storage">
                    <el-radio-group v-model="form.knowledgeGraph.storage">
                      <el-radio value="mysql">MySQL</el-radio>
                      <el-radio value="neo4j" disabled>Neo4j</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="抽取模型" prop="knowledgeGraph.extractionModelId">
                    <el-select v-model="form.knowledgeGraph.extractionModelId" clearable filterable placeholder="默认对话模型" style="width: 100%">
                      <el-option v-for="m in textModels" :key="m.id" :label="m.displayName || m.modelName" :value="m.id!" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="每批抽取数" prop="knowledgeGraph.batchSize">
                    <el-input-number v-model="form.knowledgeGraph.batchSize" :min="1" :max="100" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="实体数上限" prop="knowledgeGraph.maxEntitiesPerChunk">
                    <el-input-number
                      v-model="form.knowledgeGraph.maxEntitiesPerChunk"
                      :min="1"
                      :max="200"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="关系数上限" prop="knowledgeGraph.maxRelationsPerChunk">
                    <el-input-number
                      v-model="form.knowledgeGraph.maxRelationsPerChunk"
                      :min="1"
                      :max="200"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="置信度阈值" prop="knowledgeGraph.confidenceThreshold">
                    <el-input-number
                      v-model="form.knowledgeGraph.confidenceThreshold"
                      :min="0"
                      :max="1"
                      :step="0.1"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="图谱召回权重" prop="knowledgeGraph.graphWeight">
                    <el-input-number
                      v-model="form.knowledgeGraph.graphWeight"
                      :min="0"
                      :max="1"
                      :step="0.1"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="邻居数上限" prop="knowledgeGraph.maxNeighbors">
                    <el-input-number v-model="form.knowledgeGraph.maxNeighbors" :min="1" :max="200" controls-position="right" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="标签传播上限" prop="knowledgeGraph.labelPropagationMaxIter">
                    <el-input-number
                      v-model="form.knowledgeGraph.labelPropagationMaxIter"
                      :min="1"
                      :max="100"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="社区数上限" prop="knowledgeGraph.maxCommunities">
                    <el-input-number
                      v-model="form.knowledgeGraph.maxCommunities"
                      :min="1"
                      :max="1000"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="全局社区上限" prop="knowledgeGraph.maxCommunitiesForGlobal">
                    <el-input-number
                      v-model="form.knowledgeGraph.maxCommunitiesForGlobal"
                      :min="1"
                      :max="1000"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </section>

            <section v-show="activeSection === 'decision'">
              <DecisionSettings v-model="form.decision" />
            </section>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="AiConfig">
import { getAiConfig, updateAiConfig } from '@/api/ai/config';
import type { AiConfig } from '@/api/ai/config';
import { createDefaultDecisionConfig, validateDecisionConfig } from './decisionValidation';
import DecisionSettings from './DecisionSettings.vue';
import { listModelOptions } from '@/api/ai/model';
import { AiModel } from '@/api/ai/model';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_vector_store_type } = toRefs<any>(proxy?.useDict('ai_vector_store_type'));

/** 左侧导航分组定义 */
const groups = [
  { key: 'sql', label: 'SQL 安全', icon: 'Lock' },
  { key: 'vector', label: '向量存储', icon: 'Coin' },
  { key: 'search', label: '联网搜索', icon: 'Search' },
  { key: 'flow', label: '流程编排', icon: 'Share' },
  { key: 'kg', label: '知识图谱', icon: 'Connection' },
  { key: 'decision', label: '工业决策', icon: 'DataAnalysis' }
];

/** 表单字段前缀 → 分组 key 映射（校验失败时自动切到出错分组） */
const FIELD_GROUP: Record<string, string> = {
  sqlSafety: 'sql',
  vectorStore: 'vector',
  webSearch: 'search',
  flow: 'flow',
  knowledgeGraph: 'kg',
  decision: 'decision'
};

const loading = ref(true);
const saving = ref(false);
const activeSection = ref('sql');
const configFormRef = ref<ElFormInstance>();
const textModels = ref<AiModel[]>([]);

/** 联网搜索 Key 是否已配置（后端不回显明文） */
const apiKeySet = ref(false);
/** 加载时的向量库连接快照（检测变更提示重建索引） */
const vectorConnSnapshot = ref('');
/** 已保存配置快照（深度对比感知未保存更改，剥离 apiKey 写专用字段） */
const savedSnapshot = ref('');

const defaultPrefixOptions = ['bd_', 'so_', 'pu_', 'prd_', 'wm_', 'sch_'];

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
  },
  decision: createDefaultDecisionConfig()
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

/** 剥离写专用字段后的表单序列化（apiKey 空值表示保留原值，不参与快照对比） */
const serializeForm = () => {
  const copy = JSON.parse(JSON.stringify(form));
  delete copy.webSearch.apiKey;
  return JSON.stringify(copy);
};

/** 未保存感知：字段对比 + apiKey 有输入即为脏（apiKey 不回显，空=保留原值，非空=待保存的新值） */
const isDirty = computed(() => serializeForm() !== savedSnapshot.value || !!form.webSearch.apiKey);

/** 向量库连接是否被修改（类型/地址/集合/维度任一变化） */
const vectorStoreChanged = computed(() => {
  const v = form.vectorStore;
  return `${v.type}|${v.milvusUrl}|${v.milvusCollectionName}|${v.milvusDimension}` !== vectorConnSnapshot.value;
});

/** 左侧菜单点击：切换右侧显示的分组 */
const handleMenuSelect = (key: string) => {
  activeSection.value = key;
};

/** 应用后端返回的配置到表单并刷新快照 */
const applyConfig = (data: AiConfig) => {
  // 与旧版本响应兼容，同时保留企业知识库雪花 ID 的十进制字符串。
  const defaults = createDefaultDecisionConfig();
  const received = data.decision;
  Object.assign(form, data, {
    decision: {
      ...defaults,
      ...received,
      risk: { ...defaults.risk, ...received?.risk },
      ranking: { ...defaults.ranking, ...received?.ranking },
      policyKnowledgeId: received?.policyKnowledgeId == null ? null : String(received.policyKnowledgeId)
    }
  });
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

/** 重置：放弃未保存更改，重新加载已保存配置 */
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
      // 校验失败的字段可能在未展示的分组，自动切过去
      const firstField = Object.keys(invalidFields ?? {})[0];
      if (firstField) {
        activeSection.value = FIELD_GROUP[firstField.split('.')[0]] ?? activeSection.value;
      }
      proxy?.$modal.msgWarning('请检查表单填写');
      return;
    }
    const decisionError = validateDecisionConfig(form.decision);
    if (decisionError) {
      activeSection.value = 'decision';
      proxy?.$modal.msgWarning(decisionError);
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
    if (!payload.decision.policyKnowledgeId) {
      payload.decision.policyKnowledgeId = null;
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
.config-side {
  position: sticky;
  top: 84px;
}
.side-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: stretch;
}
</style>
