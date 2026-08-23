import request from '@/utils/request';

export function genCode(ruleCode) {
  return request({
    url: '/system/autocode/get/' + ruleCode,
    method: 'get'
  });
}

// 预览编码（不落库，不消耗流水号）
export function previewCode(ruleCode) {
  return request({
    url: '/system/autocode/preview/' + ruleCode,
    method: 'get'
  });
}

export function listRule(query) {
  return request({
    url: '/system/autocode/rule/list',
    method: 'get',
    params: query
  });
}

export function getRule(ruleId) {
  return request({
    url: '/system/autocode/rule/' + ruleId,
    method: 'get'
  });
}

export function addRule(data) {
  return request({
    url: '/system/autocode/rule',
    method: 'post',
    data: data
  });
}

export function updateRule(data) {
  return request({
    url: '/system/autocode/rule',
    method: 'put',
    data: data
  });
}

export function delRule(ruleId) {
  return request({
    url: '/system/autocode/rule/' + ruleId,
    method: 'delete'
  });
}
