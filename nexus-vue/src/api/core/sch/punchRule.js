import request from '@/utils/request'

// 查询打卡规则列表
export function listPunchRule(query) {
  return request({
    url: '/sch/punchRule/list',
    method: 'get',
    params: query
  })
}

// 查询打卡规则详细
export function getPunchRule(ruleId) {
  return request({
    url: '/sch/punchRule/' + ruleId,
    method: 'get'
  })
}

// 新增打卡规则
export function addPunchRule(data) {
  return request({
    url: '/sch/punchRule',
    method: 'post',
    data: data
  })
}

// 修改打卡规则
export function updatePunchRule(data) {
  return request({
    url: '/sch/punchRule',
    method: 'put',
    data: data
  })
}

// 删除打卡规则
export function delPunchRule(ruleId) {
  return request({
    url: '/sch/punchRule/' + ruleId,
    method: 'delete'
  })
}
