import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

/**
 * 浏览器状态和请求中的雪花标识始终使用十进制字符串。
 */
export type DecisionId = string;
export type DecisionRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type DecisionRunStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'WAITING_APPROVAL'
  | 'APPROVED'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'PARTIAL_SUCCESS'
  | 'FAILED'
  | 'STALE'
  | 'SUPERSEDED'
  | 'REJECTED'
  | 'ROLLING_BACK'
  | 'ROLLED_BACK'
  | 'CANCELLED';
export type DecisionBusinessType = 'SCHEDULE_PLAN' | 'WORK_ORDER' | 'SALES_ORDER' | 'SHORTAGE_HORIZON';

export interface DecisionRunRequest {
  bizType: DecisionBusinessType;
  bizId?: DecisionId;
  horizonDays: number;
  scenarioCodes?: string[];
  triggerType?: 'MANUAL' | 'SCHEDULE' | 'EVENT';
  forceRestart?: boolean;
}
export interface DecisionRunAccepted {
  runId: DecisionId;
  status: DecisionRunStatus;
  sceneCode: string;
  acceptedAt: string;
}
export interface DecisionKpis {
  impactedOrderCount: number;
  delayedWorkOrderCount: number;
  totalTardinessDays: number;
  maxTardinessDays: number;
  makespanDate?: string | null;
  shortageMaterialCount: number;
  totalShortageGap?: string | null;
  earliestShortageDate?: string | null;
  maxResourceUtilization?: string | null;
  overloadedResourceCount: number;
  changedWorkOrderCount: number;
  actionCost?: string | null;
}
export interface DecisionScenario {
  scenarioCode: string;
  scenarioName?: string;
  strategy?: string;
  feasible: boolean;
  infeasibleReasons?: string[];
  kpis?: DecisionKpis | null;
  objectiveScore?: string | number | null;
  rank?: number;
  evidenceKeys: string[];
  schedulePreview?: Array<Record<string, unknown>>;
  inputHash?: string;
}
export interface DecisionEvidence {
  id: DecisionId;
  runId: DecisionId;
  evidenceKey: string;
  sourceDomain: string;
  sourceType: string;
  sourceId?: DecisionId | null;
  sourceCode?: string;
  sourceName?: string;
  metricCode: string;
  metricName?: string;
  valueType: string;
  metricValue?: string;
  unit?: string;
  observedAt?: string;
  dataVersion?: string;
  verifiable: 'Y' | 'N';
  normative: 'Y' | 'N';
  derivedFromJson?: string;
  displayPath?: string;
}
export interface DecisionAction {
  id: DecisionId;
  runId: DecisionId;
  actionType: string;
  title: string;
  displayReason?: string;
  payloadJson?: string;
  targetBizType: string;
  targetBizId?: DecisionId;
  riskLevel?: DecisionRiskLevel;
  approvalRequired: 'Y' | 'N';
  approvalStatus: 'PROPOSED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'NOT_AUTHORIZED';
  executionStatus:
    | 'NOT_STARTED'
    | 'EXECUTING'
    | 'SUCCEEDED'
    | 'FAILED_PRECHECK'
    | 'FAILED'
    | 'SKIPPED'
    | 'COMPENSATING'
    | 'COMPENSATED'
    | 'COMPENSATE_FAILED';
  permissionCode: string;
  sequenceNo: number;
  resultBizId?: DecisionId;
}
export interface DecisionOutcome {
  beforeKpis?: DecisionKpis;
  projectedAfterKpis?: DecisionKpis;
  actualAfterKpis?: DecisionKpis | null;
  delta: Record<string, number | string | null>;
  effectiveActionIds: DecisionId[];
  pendingActionIds: DecisionId[];
  measuredAt: string;
  outcomeConfidence: string;
}
export interface DecisionRunSummary {
  id: DecisionId;
  bizId?: DecisionId | null;
  bizCode?: string;
  bizType: DecisionBusinessType;
  sceneCode: string;
  status: DecisionRunStatus;
  /** 失败分类码仅用于固定白名单提示，不承载原始异常详情。 */
  errorCode?: string | null;
  version: number;
  riskScore?: number | null;
  riskLevel?: DecisionRiskLevel;
  confidenceLevel?: string;
  recommendedScenarioCode?: string;
  inputSnapshotHash?: string;
  resultJson?: string;
  outcomeJson?: string;
  summaryMode?: string;
  analysisFlowInstanceId?: DecisionId | null;
  createTime?: string;
  analysisFinishedAt?: string;
}
export interface DecisionResult {
  runId: DecisionId;
  businessRef: { type: DecisionBusinessType; id?: DecisionId | null; code?: string };
  riskScore: number;
  riskLevel: DecisionRiskLevel;
  confidenceLevel: string;
  rootCauseCodes: string[];
  baselineKpis?: DecisionKpis;
  scenarios: DecisionScenario[];
  recommendedScenarioCode?: string;
  narrative?: { executiveSummary?: string; riskExplanation?: string; uncertaintyNotes?: string[]; [key: string]: unknown };
  warnings: string[];
}
export interface DecisionRunDetail {
  run: DecisionRunSummary;
  actions: DecisionAction[];
  evidence: DecisionEvidence[];
  actionBlockReasons?: Record<DecisionId, string>;
}
export interface DecisionTimelineItem {
  type: 'RUN' | 'ANALYSIS' | 'NODE' | 'ACTION';
  status: string;
  code?: string | null;
  time?: number | null;
}
export interface DecisionPageQuery {
  pageNum: number;
  pageSize: number;
  status?: DecisionRunStatus | '';
  riskLevel?: DecisionRiskLevel | '';
}
export interface DecisionApprovalRequest {
  runVersion: number;
  approvedActionIds: DecisionId[];
  rejectedActionIds: DecisionId[];
  comment?: string;
  expectedSnapshotHash: string;
}

const idPath = (id: DecisionId) => encodeURIComponent(id);
export function runDeliveryRisk(data: DecisionRunRequest): AxiosPromise<DecisionRunAccepted> {
  return request({ url: '/ai/decision/delivery-risk/run', method: 'post', data });
}
export function listDecisionRuns(params: DecisionPageQuery) {
  return request({ url: '/ai/decision/run/list', method: 'get', params });
}
export function getDecisionRun(runId: DecisionId): AxiosPromise<DecisionRunDetail> {
  return request({ url: `/ai/decision/run/${idPath(runId)}`, method: 'get' });
}
export function listDecisionEvidence(runId: DecisionId): AxiosPromise<DecisionEvidence[]> {
  return request({ url: `/ai/decision/run/${idPath(runId)}/evidence`, method: 'get' });
}
export function listDecisionTimeline(runId: DecisionId): AxiosPromise<DecisionTimelineItem[]> {
  return request({ url: `/ai/decision/run/${idPath(runId)}/timeline`, method: 'get' });
}
export function approveDecision(runId: DecisionId, data: DecisionApprovalRequest) {
  return request({ url: `/ai/decision/run/${idPath(runId)}/approve`, method: 'post', data });
}
export function rejectDecision(runId: DecisionId, reason: string) {
  return request({ url: `/ai/decision/run/${idPath(runId)}/reject`, method: 'post', data: { reason } });
}
export function recalculateDecision(runId: DecisionId): AxiosPromise<DecisionRunAccepted> {
  return request({ url: `/ai/decision/run/${idPath(runId)}/recalculate`, method: 'post' });
}
export function retryDecisionAction(actionId: DecisionId) {
  return request({ url: `/ai/decision/action/${idPath(actionId)}/retry`, method: 'post' });
}
export function exportDecisionMarkdown(runId: DecisionId): Promise<string> {
  return request({ url: `/ai/decision/run/${idPath(runId)}/export`, method: 'get', responseType: 'text' });
}
