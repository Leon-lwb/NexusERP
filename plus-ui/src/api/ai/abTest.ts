import request from '@/utils/request';

/** A/B测试分页列表 */
export function listAbTest(query: any) {
  return request({
    url: '/ai/ab-test/list',
    method: 'get',
    params: query
  });
}

/** 查询A/B测试详情 */
export function getAbTest(id: number) {
  return request({
    url: '/ai/ab-test/' + id,
    method: 'get'
  });
}

/** 新增A/B测试 */
export function addAbTest(data: any) {
  return request({
    url: '/ai/ab-test',
    method: 'post',
    data: data
  });
}

/** 修改A/B测试 */
export function updateAbTest(data: any) {
  return request({
    url: '/ai/ab-test',
    method: 'put',
    data: data
  });
}

/** 删除A/B测试 */
export function delAbTest(ids: Array<number> | number) {
  return request({
    url: '/ai/ab-test/' + ids,
    method: 'delete'
  });
}

/** 执行A/B测试 */
export function executeAbTest(id: number) {
  return request({
    url: '/ai/ab-test/execute/' + id,
    method: 'post'
  });
}

/** 查询测试结果 */
export function getAbTestResults(testId: number) {
  return request({
    url: '/ai/ab-test/result/' + testId,
    method: 'get'
  });
}

/** 提交人工评分 */
export function scoreAbTestResult(resultId: number, score: number) {
  return request({
    url: '/ai/ab-test/result/' + resultId + '/score',
    method: 'put',
    params: { score }
  });
}

/** 生成测试报告 */
export function getAbTestReport(testId: number) {
  return request({
    url: '/ai/ab-test/report/' + testId,
    method: 'get'
  });
}
