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

/** 调用与金额字段保留服务端字符串，浏览器不重算成本。 */
export interface AiCallRow {
  callId: string;
  requestId?: string;
  modelName: string;
  source: string;
  currency?: string;
  inputTokens?: string;
  outputTokens?: string;
  cost: string | null;
  effectiveCost?: string | null;
  settlementCurrency?: string;
  status: string;
  startedAt: string;
  abRunId?: string;
}
export interface AiCostSummary {
  currency: string;
  knownCost: string;
  unknownCalls: string;
  requestCount: string;
  inputTokens: string;
  outputTokens: string;
}
export interface AiBudgetRow {
  budgetId: string;
  policyId: string;
  currency: string;
  scopeType: 'TENANT_MONTH' | 'AB_RUN';
  scopeId: string;
  periodStart: string;
  limitAmount: string;
  spentAmount: string;
  heldAmount: string;
  enabled: number;
  blocked: number;
}
export interface AiBudgetInput {
  budgetId: string;
  currency: string;
  scopeType: 'TENANT_MONTH' | 'AB_RUN';
  scopeId: string;
  limitAmount: string;
  enabled: boolean;
}
export async function getAiGovernanceCapabilities() {
  const response = await request({ url: '/ai/token-usage/governance', method: 'get' });
  return response?.data ?? response;
}
export async function getAiCalls(): Promise<AiCallRow[]> {
  const response = await request({ url: '/ai/token-usage/calls', method: 'get' });
  return (response?.data ?? response) as AiCallRow[];
}
export async function getAiCostSummary(): Promise<AiCostSummary[]> {
  const response = await request({ url: '/ai/token-usage/cost-summary', method: 'get' });
  return (response?.data ?? response) as AiCostSummary[];
}
export async function getAiBudgets(): Promise<AiBudgetRow[]> {
  const response = await request({ url: '/ai/token-usage/budgets', method: 'get' });
  return (response?.data ?? response) as AiBudgetRow[];
}
export function saveAiBudget(data: AiBudgetInput) {
  return request({ url: '/ai/token-usage/budgets', method: 'put', data });
}
export function reconcileAiCall(callId: string, data: { confirmedAmount: string; currency: string; reason: string; idempotencyKey: string }) {
  return request({ url: '/ai/token-usage/calls/' + encodeURIComponent(callId) + '/reconcile', method: 'post', data });
}
