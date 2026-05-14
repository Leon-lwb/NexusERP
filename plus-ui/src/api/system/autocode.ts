import request from '@/utils/request'

export function getAutoCode(ruleCode: string) {
  return request({
    url: '/system/autocode/get/' + ruleCode,
    method: 'get'
  })
}
