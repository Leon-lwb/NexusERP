import request from '@/utils/request';

/** AI 知识图谱实例 */
export interface AiKnowledgeGraph {
  id?: number;
  knowledgeId: number;
  graphName?: string;
  graphStatus?: string;
  nodeCount?: number;
  relationshipCount?: number;
  communityCount?: number;
  modelId?: number;
  entityTypes?: string;
  relationTypes?: string;
  errorMessage?: string;
}

/** 图谱实体 */
export interface AiKgEntity {
  id?: number;
  graphId?: number;
  entityName: string;
  entityType?: string;
  description?: string;
  confidence?: number;
  sourceChunkId?: number;
  mentionCount?: number;
  communityId?: number;
}

/** 图谱关系 */
export interface AiKgRelation {
  id?: number;
  graphId?: number;
  sourceEntityId: number;
  targetEntityId: number;
  relationType?: string;
  description?: string;
  strength?: number;
  confidence?: number;
  sourceEntityName?: string;
  targetEntityName?: string;
}

/** 图谱社区 */
export interface AiKgCommunity {
  id?: number;
  graphId?: number;
  communityIndex?: number;
  summary?: string;
  summaryStatus?: string;
  entityCount?: number;
  relationCount?: number;
}

/** 构建进度 */
export interface GraphProgress {
  graphStatus?: string;
  total?: number;
  processed?: number;
  failed?: number;
  nodeCount?: number;
  relationCount?: number;
  communityCount?: number;
  /** 社区总数（阶段二进度） */
  communityTotal?: number;
  /** 已生成摘要社区数（阶段二进度） */
  communitySummarized?: number;
  /** 构建阶段：EXTRACTING/DETECTING/SUMMARIZING/DONE/FAILED/PENDING */
  stage?: string;
}

// ==================== 知识图谱 API ====================

/** 触发图谱构建（异步） */
export function buildGraph(knowledgeId: number, modelId?: number) {
  return request({
    url: `/ai/knowledge/graph/build/${knowledgeId}`,
    method: 'post',
    params: modelId ? { modelId } : {}
  });
}

/** 查询图谱实例 */
export function getGraph(knowledgeId: number) {
  return request({
    url: `/ai/knowledge/graph/${knowledgeId}`,
    method: 'get'
  });
}

/** 查询构建进度 */
export function getGraphProgress(knowledgeId: number) {
  return request({
    url: `/ai/knowledge/graph/progress/${knowledgeId}`,
    method: 'get'
  });
}

/** 分页查询实体列表 */
export function listGraphEntity(query?: object) {
  return request({
    url: '/ai/knowledge/graph/entity/list',
    method: 'get',
    params: query
  });
}

/** 分页查询关系列表 */
export function listGraphRelation(query?: object) {
  return request({
    url: '/ai/knowledge/graph/relation/list',
    method: 'get',
    params: query
  });
}

/** 分页查询社区列表 */
export function listGraphCommunity(query?: object) {
  return request({
    url: '/ai/knowledge/graph/community/list',
    method: 'get',
    params: query
  });
}

/** 查询实体 1 跳邻居 */
export function getEntityNeighbors(entityId: string | number) {
  return request({
    url: `/ai/knowledge/graph/neighbors/${entityId}`,
    method: 'get'
  });
}

/** 全局检索（基于社区摘要合成） */
export function globalSearch(knowledgeId: number, query: string) {
  return request({
    url: `/ai/knowledge/graph/globalSearch/${knowledgeId}`,
    method: 'post',
    data: { query }
  });
}

/** 全量重建 */
export function rebuildGraph(knowledgeId: number, modelId?: number) {
  return request({
    url: `/ai/knowledge/graph/rebuild/${knowledgeId}`,
    method: 'post',
    params: modelId ? { modelId } : {}
  });
}

/** 删除图谱 */
export function deleteGraph(knowledgeId: number) {
  return request({
    url: `/ai/knowledge/graph/${knowledgeId}`,
    method: 'delete'
  });
}
