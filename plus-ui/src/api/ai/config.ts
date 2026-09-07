import request from '@/utils/request';

/** AI 运行参数配置（5 组：SQL 安全/向量存储/联网搜索/流程编排/知识图谱） */
export interface AiConfig {
  sqlSafety: SqlSafety;
  vectorStore: VectorStore;
  webSearch: WebSearch;
  flow: Flow;
  knowledgeGraph: KnowledgeGraph;
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
  /** milvus / inmemory */
  type: string;
  milvusUrl: string;
  milvusCollectionName: string;
  milvusDimension: number;
}

/** 联网搜索配置（apiKey 写专用：留空保留原值，回显仅含 apiKeySet） */
export interface WebSearch {
  enabled: boolean;
  /** tavily / bing */
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
  /** mysql / neo4j */
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
