import request from '@/utils/request';

/** 查询 Token 用量汇总 */
export function getTokenUsageSummary(params: any) {
  return request({ url: '/ai/token-usage/summary', method: 'get', params });
}

/** 查询 Token 用量趋势 */
export function getTokenUsageTrend(params: any) {
  return request({ url: '/ai/token-usage/trend', method: 'get', params });
}

/** 查询当前用户用量 */
export function getMyTokenUsage(params: any) {
  return request({ url: '/ai/token-usage/my-usage', method: 'get', params });
}

/** 模型用量排行 */
export function getModelRanking(params: any) {
  return request({ url: '/ai/token-usage/model-ranking', method: 'get', params });
}

/** 用户用量排行 */
export function getUserRanking(params: any) {
  return request({ url: '/ai/token-usage/user-ranking', method: 'get', params });
}

/** 历史数据回填 */
export function backfillTokenUsage(params: any) {
  return request({ url: '/ai/token-usage/backfill', method: 'post', params });
}
