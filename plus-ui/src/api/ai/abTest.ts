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

/** 冻结运行批次，与历史测试结果分别读取。 */
export interface AiAbRun {
  runId: string;
  definitionId: string;
  executionId: string;
  testName: string;
  status: string;
  createdAt: string;
}
export interface AiAbRunItem {
  itemId: string;
  questionIndex: number;
  modelSlot: 'A' | 'B';
  modelName: string;
  question: string;
  response?: string;
  status: string;
  latencyMs?: string;
  score?: number;
}
export interface AiAbRunReport {
  models: Array<{
    modelSlot: string;
    totalQuestions: string;
    succeeded: string;
    failed: string;
    unconfirmed: string;
    averageLatencyMs?: string;
    averageScore?: string;
  }>;
  costs: Array<{
    modelSlot: string;
    currency: string;
    knownCost: string;
    unknownCalls: string;
    callCount: string;
    inputTokens: string;
    outputTokens: string;
  }>;
}
export async function getAbRunCapabilities() {
  const response = await request({ url: '/ai/ab-test/capabilities', method: 'get' });
  return response?.data ?? response;
}
export async function listAbRuns(definitionId: string): Promise<AiAbRun[]> {
  const response = await request({ url: '/ai/ab-test/runs', method: 'get', params: { definitionId } });
  return (response?.data ?? response) as AiAbRun[];
}
export async function startAbRun(definitionId: string, startKey: string): Promise<AiAbRun> {
  const response = await request({ url: '/ai/ab-test/' + encodeURIComponent(definitionId) + '/runs', method: 'post', data: { startKey } });
  return (response?.data ?? response) as AiAbRun;
}
export async function getAbRunItems(runId: string): Promise<AiAbRunItem[]> {
  const response = await request({ url: '/ai/ab-test/runs/' + encodeURIComponent(runId) + '/items', method: 'get' });
  return (response?.data ?? response) as AiAbRunItem[];
}
export async function getAbRunReport(runId: string): Promise<AiAbRunReport> {
  const response = await request({ url: '/ai/ab-test/runs/' + encodeURIComponent(runId) + '/report', method: 'get' });
  return (response?.data ?? response) as AiAbRunReport;
}
export function cancelAbRun(runId: string) {
  return request({ url: '/ai/ab-test/runs/' + encodeURIComponent(runId) + '/cancel', method: 'post' });
}
export function resumeAbRun(runId: string) {
  return request({ url: '/ai/ab-test/runs/' + encodeURIComponent(runId) + '/resume', method: 'post' });
}
export function scoreAbRunItem(runId: string, itemId: string, score: number) {
  return request({
    url: '/ai/ab-test/runs/' + encodeURIComponent(runId) + '/items/' + encodeURIComponent(itemId) + '/score',
    method: 'put',
    params: { score }
  });
}
