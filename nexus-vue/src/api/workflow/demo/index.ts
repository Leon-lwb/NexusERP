import request from '@/utils/request'
import { AxiosPromise } from 'axios';
import { WfDemoVO, WfDemo, WfDemoQuery } from '@/api/workflow/demo/types';

// 查询DEMO列表
export function listDemo(query?:WfDemoQuery) {
  return request({
    url: '/workflow/demo/list',
    method: 'get',
    params: query
  })
}

// 查询DEMO详细
export function getDemo(demoId: string | number) {
  return request({
    url: '/workflow/demo/' + demoId,
    method: 'get'
  })
}

// 新增DEMO
export function addDemo(data: WfDemo) {
  return request({
    url: '/workflow/demo',
    method: 'post',
    data: data
  })
}

// 修改DEMO
export function updateDemo(data: WfDemo) {
  return request({
    url: '/workflow/demo',
    method: 'put',
    data: data
  })
}

// 删除DEMO
export function delDemo(demoId: string | number | Array<string | number>) {
  return request({
    url: '/workflow/demo/' + demoId,
    method: 'delete'
  })
}
