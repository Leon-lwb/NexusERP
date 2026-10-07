import type { DecisionAction, DecisionRunStatus } from '@/api/ai/decision';

export interface DecisionActionResultTarget {
  kind: 'requisition' | 'schedule';
  id: string;
  /** 仅由排程只读回查提供，用于核对第二次查询，不能来自模型正文。 */
  code?: string;
}
export interface DecisionErpDestination {
  path: '/erp/pu/requisition/form' | '/erp/prd/schedule';
  query: Record<string, string>;
}
export interface VerifiedDecisionErpResult {
  id: string;
  code: string;
  status: string;
  destination: DecisionErpDestination | null;
}
export interface DecisionScheduleResultQuery {
  id: string;
  code: string;
}
type TagType = 'success' | 'warning' | 'danger' | 'info';

/** 不把数字转成字符串补救：到达浏览器前丢失的雪花精度无法恢复。 */
function resultId(value: unknown): value is string {
  return typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) && (value.length < 19 || value <= '9223372036854775807');
}
function businessCode(value: unknown): value is string {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(value);
}
function isRollingBack(status?: DecisionRunStatus) {
  return status === 'ROLLING_BACK' || status === 'ROLLED_BACK';
}

/** 执行结果类型必须与允许的动作精确对应；原目标与载荷不代表已创建结果。 */
export function decisionActionResultTarget(action: DecisionAction, runStatus?: DecisionRunStatus): DecisionActionResultTarget | null {
  if (action.executionStatus !== 'SUCCEEDED' || action.approvalStatus !== 'APPROVED' || isRollingBack(runStatus) || !resultId(action.resultBizId))
    return null;
  if (action.actionType === 'CREATE_PURCHASE_REQUISITION_DRAFT' && action.resultBizType === 'PU_PURCHASE_REQUISITION') {
    return { kind: 'requisition', id: action.resultBizId };
  }
  if (action.actionType === 'CREATE_SCHEDULE_PLAN_DRAFT' && action.resultBizType === 'PRD_SCHEDULE_PLAN') {
    return { kind: 'schedule', id: action.resultBizId };
  }
  return null;
}

export function decisionActionExecutionView(action: DecisionAction, runStatus?: DecisionRunStatus) {
  const approvalLabels: Record<string, string> = {
    PROPOSED: '待提请审批',
    PENDING_APPROVAL: '待审批',
    APPROVED: '已批准',
    REJECTED: '已驳回',
    NOT_AUTHORIZED: '未授权'
  };
  const executionLabels: Record<string, [string, TagType]> = {
    NOT_STARTED: ['未开始', 'info'],
    EXECUTING: ['执行中', 'warning'],
    FAILED_PRECHECK: ['执行前校验失败', 'danger'],
    FAILED: ['执行失败', 'danger'],
    SKIPPED: ['已跳过', 'info'],
    COMPENSATING: ['补偿中', 'warning'],
    COMPENSATED: ['已补偿（不再是有效草稿结果）', 'info'],
    COMPENSATE_FAILED: ['补偿失败，需人工核对', 'danger']
  };
  let [executionLabel, executionType]: [string, TagType] = Object.hasOwn(executionLabels, action.executionStatus)
    ? executionLabels[action.executionStatus]
    : ['未知执行状态', 'warning'];
  if (action.executionStatus === 'SUCCEEDED') {
    if (isRollingBack(runStatus)) {
      executionLabel = '运行回滚中或已回滚，原结果不可回查';
      executionType = 'warning';
    } else if (decisionActionResultTarget(action, runStatus)) {
      executionLabel = '执行成功（ERP当前状态待回查）';
      executionType = 'success';
    } else if (
      (action.actionType === 'SEND_EXPEDITE_NOTICE' || action.actionType === 'SEND_MANAGER_NOTIFICATION') &&
      action.resultBizType === 'SYS_NOTIFICATION'
    ) {
      executionLabel = '内部通知已受理，非ERP草稿；不代表已联系供应商';
      executionType = 'info';
    } else {
      executionLabel = '执行记录为SUCCEEDED，但无可核验ERP结果';
      executionType = 'warning';
    }
  }
  return {
    approvalLabel: Object.hasOwn(approvalLabels, action.approvalStatus) ? approvalLabels[action.approvalStatus] : '未知审批状态',
    executionLabel,
    executionType,
    resultId: resultId(action.resultBizId) ? action.resultBizId : '未记录可核验结果ID'
  };
}

/** 只认原查询返回的身份/编号/状态；未经真实回查不构造任何查看路由。 */
export function verifiedDecisionErpResult(target: DecisionActionResultTarget, record: unknown): VerifiedDecisionErpResult | null {
  if (
    !record ||
    typeof record !== 'object' ||
    Array.isArray(record) ||
    !resultId(target.id) ||
    (target.kind !== 'requisition' && target.kind !== 'schedule')
  )
    return null;
  const row = record as Record<string, unknown>;
  const id = target.kind === 'requisition' ? row.requisitionId : row.planId;
  const code = target.kind === 'requisition' ? row.requisitionNo : row.planNo;
  if (!resultId(id) || id !== target.id || !businessCode(code) || (target.code !== undefined && code !== target.code)) return null;
  const status = row.status;
  if (typeof status !== 'string' || !/^[A-Z][A-Z0-9_]{0,63}$/.test(status)) return null;
  const revoked = ['CANCELLED', 'CANCELED', 'VOID', 'DELETED', 'ROLLED_BACK', 'COMPENSATED'].includes(status);
  const destination: DecisionErpDestination | null = revoked
    ? null
    : target.kind === 'requisition'
      ? { path: '/erp/pu/requisition/form', query: { id, action: 'view', decisionResultRequisitionId: id } }
      : { path: '/erp/prd/schedule', query: { decisionResultPlanId: id, planNo: code } };
  return { id, code, status, destination };
}

export function decisionScheduleResultQuery(query: Record<string, unknown>): DecisionScheduleResultQuery | null {
  return resultId(query.decisionResultPlanId) && businessCode(query.planNo) ? { id: query.decisionResultPlanId, code: query.planNo } : null;
}

/** 按编号查询可能为模糊匹配，不能把第一页其他方案当成执行产出的真实方案。 */
export function decisionScheduleResultRows<T extends { planId: unknown; planNo: unknown }>(
  rows: T[],
  query: DecisionScheduleResultQuery | null
): T[] {
  return query ? rows.filter((row) => row.planId === query.id && row.planNo === query.code) : rows;
}

/** 仅决策结果的只读表单深链；标记不授予权限，仍由原详情接口鉴权。 */
export function decisionPurchaseResultQuery(query: Record<string, unknown>): string | null {
  return resultId(query.id) && query.action === 'view' && query.decisionResultRequisitionId === query.id ? query.id : null;
}
