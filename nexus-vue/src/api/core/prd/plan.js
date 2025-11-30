import request from '@/utils/request'

// 查询生产计划列表
export function listPlan(query) {
  return request({
    url: '/prd/plan/list',
    method: 'get',
    params: query
  })
}

// 查询生产计划详细
export function getPlan(planId) {
  return request({
    url: '/prd/plan/' + planId,
    method: 'get'
  })
}

// 新增生产计划
export function addPlan(data) {
  return request({
    url: '/prd/plan',
    method: 'post',
    data: data
  })
}

// 修改生产计划
export function updatePlan(data) {
  return request({
    url: '/prd/plan',
    method: 'put',
    data: data
  })
}

// 删除生产计划
export function delPlan(planId) {
  return request({
    url: '/prd/plan/' + planId,
    method: 'delete'
  })
}

// 生产计划版本更新
export function updatePlanVersion(data) {
  return request({
    url: '/prd/plan/updateVersion',
    method: 'put',
    data: data
  })
}

// BOM计算
export function calculateBom(planId) {
  return request({
    url: '/prd/plan/calculateBom/' + planId,
    method: 'get'
  })
}

// 生成物料需求（带明细）
export function generateMaterialRequirementsWithDetails(planId) {
  return request({
    url: '/prd/plan/generateMaterialRequirementsWithDetails/' + planId,
    method: 'post'
  })
}

// 根据计划编号查询物料需求明细列表
export function listDetailsByPlanNo(planNo) {
  return request({
    url: '/prd/requirement/listDetailsByPlanNo/' + planNo,
    method: 'get'
  })
}