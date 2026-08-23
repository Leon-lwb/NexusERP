import request from '@/utils/request';

export function getAutoCode(ruleCode: string) {
  return request({
    url: '/system/autocode/get/' + ruleCode,
    method: 'get'
  });
}

// 预览编码（不落库，不消耗流水号）
export function previewAutoCode(ruleCode: string) {
  return request({
    url: '/system/autocode/preview/' + ruleCode,
    method: 'get'
  });
}
