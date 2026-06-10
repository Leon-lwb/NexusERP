import request from '@/utils/request'

/** AI 知识库类型 */
export interface AiKnowledge {
  id?: number
  knowledgeName: string
  description?: string
  embeddingModel?: string
  vectorStore?: string
  status: string
  documentCount?: number
  createTime?: string
  separator?: string
  overlapChar?: number
  textBlockSize?: number
  retrieveLimit?: number
  similarityThreshold?: number
  enableRerank?: number
  rerankModel?: string
  enableHybrid?: number
  hybridAlpha?: number
  systemPrompt?: string
}

/** AI 知识库文档类型 */
export interface AiKnowledgeDocument {
  id?: number
  knowledgeId: number
  fileName: string
  filePath?: string
  fileSize?: number
  chunkCount?: number
  status: string
  errorMessage?: string
  createTime?: string
}

/** AI 知识库片段类型 */
export interface AiKnowledgeChunk {
  id?: number
  documentId: number
  knowledgeId: number
  content: string
  chunkIndex?: number
  vectorId?: string
}

// ==================== 知识库管理 API ====================

/** 分页查询知识库列表 */
export function listKnowledge(query?: object) {
  return request({
    url: '/ai/knowledge/list',
    method: 'get',
    params: query
  })
}

/** 根据ID查询知识库 */
export function getKnowledge(id: number) {
  return request({
    url: `/ai/knowledge/${id}`,
    method: 'get'
  })
}

/** 新增知识库 */
export function addKnowledge(data: AiKnowledge) {
  return request({
    url: '/ai/knowledge',
    method: 'post',
    data
  })
}

/** 修改知识库 */
export function updateKnowledge(data: AiKnowledge) {
  return request({
    url: '/ai/knowledge',
    method: 'put',
    data
  })
}

/** 批量删除知识库 */
export function delKnowledge(ids: Array<number | string>) {
  return request({
    url: `/ai/knowledge/${ids}`,
    method: 'delete'
  })
}

// ==================== 文档管理 API ====================

/** 分页查询文档列表 */
export function listDocument(query?: object) {
  return request({
    url: '/ai/knowledge/document/list',
    method: 'get',
    params: query
  })
}

/** 根据知识库ID查询文档列表 */
export function listDocumentByKnowledge(knowledgeId: number) {
  return request({
    url: `/ai/knowledge/document/listByKnowledge/${knowledgeId}`,
    method: 'get'
  })
}

/** 根据ID查询文档 */
export function getDocument(id: number) {
  return request({
    url: `/ai/knowledge/document/${id}`,
    method: 'get'
  })
}

/** 批量删除文档 */
export function delDocument(ids: Array<number | string>) {
  return request({
    url: `/ai/knowledge/document/${ids}`,
    method: 'delete'
  })
}

/** 上传文档到知识库 */
export function uploadDocument(knowledgeId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/ai/knowledge/document/upload/${knowledgeId}`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== 片段管理 API ====================

/** 分页查询片段列表 */
export function listChunk(query?: object) {
  return request({
    url: '/ai/knowledge/chunk/list',
    method: 'get',
    params: query
  })
}

/** 根据文档ID查询片段列表 */
export function listChunkByDocument(documentId: number) {
  return request({
    url: `/ai/knowledge/chunk/listByDocument/${documentId}`,
    method: 'get'
  })
}

/** 根据知识库ID查询片段列表 */
export function listChunkByKnowledge(knowledgeId: number) {
  return request({
    url: `/ai/knowledge/chunk/listByKnowledge/${knowledgeId}`,
    method: 'get'
  })
}

/** 重新处理文档 */
export function retryDocument(documentId: number) {
  return request({
    url: `/ai/knowledge/document/retry/${documentId}`,
    method: 'post'
  })
}

/** 知识库检索测试 */
export function knowledgeRetrieval(data: { knowledgeId: number; query: string; topK?: number; threshold?: number; enableRerank?: boolean; rerankModel?: string; enableHybrid?: boolean; hybridAlpha?: number }) {
  return request({
    url: '/ai/knowledge/retrieval',
    method: 'post',
    data
  })
}
