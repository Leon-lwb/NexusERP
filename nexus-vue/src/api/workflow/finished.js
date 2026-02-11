import request from '@/utils/request'

// 撤回任务
export function revokeProcess(data) {
  return request({
    url: '/workflow/task/revokeProcess',
    method: 'post',
    data: data
  })
}

// 收回任务
export function recallProcess(data) {
  return request({
    url: '/workflow/task/recallProcess',
    method: 'post',
    data: data
  })
}

