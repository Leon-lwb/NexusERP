import request from '@/utils/request'

/** AI 厂商配置类型 */
export interface AiProvider {
  id?: number
  name: string
  apiHost: string
  apiKey?: string
  providerType: string
  status: string
}

/** AI 模型配置类型 */
export interface AiModel {
  id?: number
  providerId: number
  providerName?: string
  modelName: string
  displayName?: string
  modelType: string
  maxTokens?: number
  temperature?: number
  isDefault: string
  status: string
}

// ==================== 厂商管理 API ====================

/**
 * 分页查询AI厂商配置列表
 * @param query 查询参数
 */
export function listProvider(query?: object) {
  return request({
    url: '/ai/provider/list',
    method: 'get',
    params: query
  })
}

/**
 * 根据ID查询AI厂商配置
 * @param id 厂商ID
 */
export function getProvider(id: number) {
  return request({
    url: `/ai/provider/${id}`,
    method: 'get'
  })
}

/**
 * 新增AI厂商配置
 * @param data 厂商配置数据
 */
export function addProvider(data: AiProvider) {
  return request({
    url: '/ai/provider',
    method: 'post',
    data
  })
}

/**
 * 修改AI厂商配置
 * @param data 厂商配置数据
 */
export function updateProvider(data: AiProvider) {
  return request({
    url: '/ai/provider',
    method: 'put',
    data
  })
}

/**
 * 批量删除AI厂商配置
 * @param ids 厂商ID数组
 */
export function delProvider(ids: Array<number | string>) {
  return request({
    url: `/ai/provider/${ids}`,
    method: 'delete'
  })
}

// ==================== 模型管理 API ====================

/**
 * 分页查询AI模型配置列表
 * @param query 查询参数
 */
export function listModel(query?: object) {
  return request({
    url: '/ai/model/list',
    method: 'get',
    params: query
  })
}

/**
 * 根据ID查询AI模型配置
 * @param id 模型ID
 */
export function getModel(id: number) {
  return request({
    url: `/ai/model/${id}`,
    method: 'get'
  })
}

/**
 * 新增AI模型配置
 * @param data 模型配置数据
 */
export function addModel(data: AiModel) {
  return request({
    url: '/ai/model',
    method: 'post',
    data
  })
}

/**
 * 修改AI模型配置
 * @param data 模型配置数据
 */
export function updateModel(data: AiModel) {
  return request({
    url: '/ai/model',
    method: 'put',
    data
  })
}

/**
 * 批量删除AI模型配置
 * @param ids 模型ID数组
 */
export function delModel(ids: Array<number | string>) {
  return request({
    url: `/ai/model/${ids}`,
    method: 'delete'
  })
}

/**
 * 设置默认模型
 * @param id 模型ID
 */
export function setDefaultModel(id: number) {
  return request({
    url: `/ai/model/setDefault/${id}`,
    method: 'put'
  })
}
