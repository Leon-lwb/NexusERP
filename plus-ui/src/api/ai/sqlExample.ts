import request from '@/utils/request';

/** SQL示例分页列表 */
export function listSqlExample(query: any) {
  return request({
    url: '/ai/sql-example/list',
    method: 'get',
    params: query
  });
}

/** SQL示例全量列表 */
export function listAllSqlExample(query: any) {
  return request({
    url: '/ai/sql-example/listAll',
    method: 'get',
    params: query
  });
}

/** 查询SQL示例详情 */
export function getSqlExample(id: number) {
  return request({
    url: '/ai/sql-example/' + id,
    method: 'get'
  });
}

/** 新增SQL示例 */
export function addSqlExample(data: any) {
  return request({
    url: '/ai/sql-example',
    method: 'post',
    data: data
  });
}

/** 修改SQL示例 */
export function updateSqlExample(data: any) {
  return request({
    url: '/ai/sql-example',
    method: 'put',
    data: data
  });
}

/** 删除SQL示例 */
export function delSqlExample(ids: Array<number> | number) {
  return request({
    url: '/ai/sql-example/' + ids,
    method: 'delete'
  });
}
