import request from '@/utils/request';

/** AI 运行参数配置（6 组：SQL 安全/向量存储/联网搜索/流程编排/知识图谱/工业决策） */
export interface AiConfig {
  sqlSafety: SqlSafety;
  vectorStore: VectorStore;
  webSearch: WebSearch;
  flow: Flow;
  knowledgeGraph: KnowledgeGraph;
  decision: DecisionConfig;
}

/** SQL 安全配置（AI 数据查询白名单前缀/超时/行数上限） */
export interface SqlSafety {
  /** 允许查询的表名前缀 */
  allowedTablePrefixes: string[];
  /** SQL 执行超时（毫秒） */
  sqlTimeout: number;
  /** SQL 最大返回行数 */
  sqlMaxRows: number;
}

/** 向量存储配置（切换 type 或改 dimension 会导致既有向量不可检索） */
export interface VectorStore {
  /**
   * 向量存储策略：milvus 或 inmemory。
   */
  type: string;
  milvusUrl: string;
  milvusCollectionName: string;
  milvusDimension: number;
}

/** 联网搜索配置（apiKey 写专用：留空保留原值，回显仅含 apiKeySet） */
export interface WebSearch {
  enabled: boolean;
  /**
   * 联网搜索服务：tavily 或 bing。
   */
  provider: string;
  apiKey?: string;
  apiKeySet?: boolean;
  topN: number;
  timeoutMs: number;
}

/** 流程编排配置 */
export interface Flow {
  /** 并行分支执行（含循环的图强制串行） */
  parallel: boolean;
}

/** 知识图谱配置 */
export interface KnowledgeGraph {
  enabled: boolean;
  /**
   * 知识图谱存储策略：mysql 或 neo4j。
   */
  storage: string;
  batchSize: number;
  maxEntitiesPerChunk: number;
  maxRelationsPerChunk: number;
  confidenceThreshold: number;
  /** 抽取用 LLM 模型ID（null 用默认 chat 模型） */
  extractionModelId?: number | null;
  graphWeight: number;
  maxNeighbors: number;
  labelPropagationMaxIter: number;
  maxCommunities: number;
  maxCommunitiesForGlobal: number;
}

/** 工业决策风险分数（四项总和必须为 100）。 */
export interface DecisionRiskConfig {
  tardinessPoints: number;
  shortagePoints: number;
  capacityPoints: number;
  supplierPoints: number;
}

/** 方案确定性评分权重（总和必须为 1）。 */
export interface DecisionRankingConfig {
  tardinessWeight: number;
  delayedCountWeight: number;
  shortageWeight: number;
  utilizationWeight: number;
  actionCostWeight: number;
  planChangeWeight: number;
  infeasibleWeight: number;
}

export interface DecisionConfig {
  enabled: boolean;
  maxConcurrentRuns: number;
  maxScenarios: number;
  analysisTimeoutSeconds: number;
  actionTimeoutSeconds: number;
  staleThresholdSeconds: number;
  requireApprovalLevels: string[];
  allowAutoLowRiskNotify: boolean;
  benchmarkMode: boolean;
  defaultHorizonDays: number;
  maxHorizonDays: number;
  /** 雪花 ID 在表单中只用十进制字符串，避免 JS Number 截断。 */
  policyKnowledgeId?: string | null;
  risk: DecisionRiskConfig;
  ranking: DecisionRankingConfig;
}

/**
 * 读取当前生效的 AI 运行参数（yml 兜底默认 + ai_config 表覆盖的合并结果）
 */
export function getAiConfig() {
  return request({
    url: '/ai/config',
    method: 'get'
  });
}

/**
 * 保存 AI 运行参数并即时生效（集群其他节点经 Redis 广播自动重载）
 */
export function updateAiConfig(data: AiConfig) {
  return request({
    url: '/ai/config',
    method: 'put',
    data
  });
}
