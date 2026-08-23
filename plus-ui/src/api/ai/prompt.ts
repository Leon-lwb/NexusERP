import request from '@/utils/request';

/** Prompt模板分页列表 */
export function listPromptTemplate(query: any) {
  return request({
    url: '/ai/prompt/list',
    method: 'get',
    params: query
  });
}

/** Prompt模板全量列表 */
export function listAllPromptTemplate(query: any) {
  return request({
    url: '/ai/prompt/listAll',
    method: 'get',
    params: query
  });
}

/** 查询Prompt模板详情 */
export function getPromptTemplate(id: number) {
  return request({
    url: '/ai/prompt/' + id,
    method: 'get'
  });
}

/** 新增Prompt模板 */
export function addPromptTemplate(data: any) {
  return request({
    url: '/ai/prompt',
    method: 'post',
    data: data
  });
}

/** 修改Prompt模板 */
export function updatePromptTemplate(data: any) {
  return request({
    url: '/ai/prompt',
    method: 'put',
    data: data
  });
}

/** 删除Prompt模板 */
export function delPromptTemplate(ids: Array<number> | number) {
  return request({
    url: '/ai/prompt/' + ids,
    method: 'delete'
  });
}
