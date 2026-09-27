/**
 * 纯决策展示规则，不将雪花标识转换为 JavaScript 数值。
 */
export function riskTagType(level?: string): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 'CRITICAL' || level === 'HIGH') return 'danger';
  if (level === 'MEDIUM') return 'warning';
  if (level === 'LOW') return 'success';
  return 'info';
}

export function formatDelta(value: number | null | undefined, unit = ''): string {
  if (value == null || Number.isNaN(value)) return '—';
  if (value < 0) return `改善 ${Math.abs(value)}${unit ? ` ${unit}` : ''}`;
  if (value > 0) return `增加 ${value}${unit ? ` ${unit}` : ''}`;
  return `无变化${unit ? ` ${unit}` : ''}`;
}

/**
 * 终态失败不得伪装为待分析，也不得展示后端原始异常。
 */
export function decisionSummaryText(status?: string, executiveSummary?: string | null): string {
  if (status === 'FAILED') return '分析失败，请重新分析；如持续失败，请提供运行 ID 联系管理员。';
  if (status === 'STALE') return '分析已过期，请重新分析。';
  if (status === 'SUPERSEDED') return '此运行已被新的分析取代，请查看最新运行。';
  if (status === 'REJECTED') return '本次决策已被驳回，请重新分析。';
  if (status === 'ROLLED_BACK') return '执行已回滚，请核对执行记录。';
  if (status === 'CANCELLED') return '本次分析已取消。';
  if (status === 'ROLLING_BACK') return '正在回滚已执行动作，请稍后刷新。';
  if (status === 'PARTIAL_SUCCESS') return '部分动作执行成功，请核对执行明细。';
  if (executiveSummary?.trim()) return executiveSummary;
  if (status === 'WAITING_APPROVAL') return '分析已完成，等待审批；暂无摘要。';
  if (status === 'SUCCEEDED') return '分析已完成，暂无摘要，请刷新查看。';
  return '分析正在进行，请稍后刷新。';
}

/**
 * 分析进度在分析阶段结束时停止，不随审批或执行阶段继续计时。
 */
export function isDecisionAnalysisRunning(status?: string): boolean {
  return status === 'CREATED' || status === 'RUNNING';
}

/**
 * 分析完成后使用服务端结束时间；缺失时间表示未知，不作零值或持续计时处理。
 */
export function decisionAnalysisElapsedSeconds(status: string | undefined, startedAt: number, finishedAt: number, now: number): number | null {
  const end = isDecisionAnalysisRunning(status) ? now : finishedAt;
  if (!Number.isFinite(startedAt) || !Number.isFinite(end)) return null;
  return Math.max(0, (end - startedAt) / 1000);
}

/**
 * 即使 SSE 已连接也要核对活跃运行，防止终态事件丢失。
 */
export function shouldPollDecision(status?: string): boolean {
  return status === 'CREATED' || status === 'RUNNING' || status === 'APPROVED' || status === 'EXECUTING' || status === 'ROLLING_BACK';
}

const decisionNodeNames: Record<string, string> = {
  context: '业务事实',
  baseline: '基线风险',
  shortage: '缺料模拟',
  aps: '排程仿真',
  merge: '事实合并',
  rank: '方案排名',
  rag: '制度检索',
  llm: '模型摘要',
  guard: '策略校验',
  actionProposal: '建议动作',
  SEND_EXPEDITE_NOTICE: '采购催货通知',
  CREATE_PURCHASE_REQUISITION_DRAFT: '采购申请草稿',
  CREATE_SCHEDULE_PLAN_DRAFT: '排程计划草稿'
};
export function decisionNodeLabel(code?: string | null): string {
  return code ? decisionNodeNames[code] || code : '运行状态';
}
export function decisionRiskLabel(level?: string): string {
  return ({ CRITICAL: '严重风险', HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' } as Record<string, string>)[level || ''] || '待评估';
}
export function decisionRunStatusLabel(status?: string): string {
  return (
    (
      {
        CREATED: '等待执行',
        RUNNING: '分析中',
        WAITING_APPROVAL: '待审批',
        APPROVED: '已批准',
        EXECUTING: '动作执行中',
        SUCCEEDED: '已完成',
        PARTIAL_SUCCESS: '部分完成',
        FAILED: '失败',
        STALE: '已过期',
        SUPERSEDED: '已被取代',
        REJECTED: '已驳回',
        ROLLING_BACK: '正在回滚',
        ROLLED_BACK: '已回滚',
        CANCELLED: '已取消'
      } as Record<string, string>
    )[status || ''] || '待确认'
  );
}

export type DecisionFlowProgressEvent = { nodeId: string; status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'; seq: number };
const trustedFlowNodes = new Set(['context', 'baseline', 'shortage', 'aps', 'merge', 'rank', 'rag', 'llm', 'guard', 'actionProposal']);

/**
 * 按字面量提取实例标识；JSON.parse 会舍入六十四位雪花数值。
 */
export function flowProgressEventId(payload: unknown, expectedInstanceId?: string | null): DecisionFlowProgressEvent | null {
  if (!expectedInstanceId || !/^[1-9]\d{0,18}$/.test(expectedInstanceId) || !payload || typeof payload !== 'object') return null;
  const event = payload as Record<string, unknown>;
  if ((event.type !== 'AI_FLOW_NODE_START' && event.type !== 'AI_FLOW_NODE_END') || typeof event.content !== 'string') return null;
  const match = event.content.match(/(?:^|[,{}])\s*"instanceId"\s*:\s*"?([1-9]\d{0,18})"?/);
  if (!match || match[1] !== expectedInstanceId) return null;
  try {
    const body = JSON.parse(event.content) as Record<string, unknown>;
    if (typeof body.nodeId !== 'string' || !trustedFlowNodes.has(body.nodeId)) return null;
    const status = event.type === 'AI_FLOW_NODE_START' ? 'RUNNING' : body.status;
    if (status !== 'RUNNING' && status !== 'SUCCEEDED' && status !== 'FAILED') return null;
    const seq = typeof body.seq === 'number' && Number.isSafeInteger(body.seq) ? body.seq : 0;
    return { nodeId: body.nodeId, status, seq };
  } catch {
    return null;
  }
}

export function applyDecisionFlowEvent(state: Record<string, string>, event: DecisionFlowProgressEvent | null): Record<string, string> {
  if (!event || (event.status === 'RUNNING' && (state[event.nodeId] === 'SUCCEEDED' || state[event.nodeId] === 'FAILED'))) return state;
  return { ...state, [event.nodeId]: event.status };
}

export function formatDecisionElapsed(seconds: number): string {
  const safe = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
  const minutes = Math.floor(safe / 60);
  return minutes > 0 ? `${minutes}分${safe % 60}秒` : `${safe}秒`;
}

export interface DecisionScheduleRow {
  woId: string;
  woNo: string;
  stepId: string;
  seqNum: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  startTime: string;
  endTime: string;
  processHours: string;
  setupHours: string;
  delayFlag: string;
}
function safeDecisionCell(value: unknown): string {
  if (typeof value === 'string') return value.trim().slice(0, 128) || '—';
  if (typeof value === 'number' && Number.isSafeInteger(value)) return String(value);
  return '—';
}
/**
 * 仅展示已审计的排程字段，不把大型或不可信 JSON 扩展成表格列。
 */
export function previewScheduleRows(input?: Array<Record<string, unknown>> | null): DecisionScheduleRow[] {
  if (!Array.isArray(input)) return [];
  return input.slice(0, 20).map((row) => {
    const r = row && typeof row === 'object' && !Array.isArray(row) ? row : {};
    return {
      woId: safeDecisionCell(r.woId),
      woNo: safeDecisionCell(r.woNo),
      stepId: safeDecisionCell(r.stepId),
      seqNum: safeDecisionCell(r.seqNum),
      resourceType: safeDecisionCell(r.resourceType),
      resourceId: safeDecisionCell(r.resourceId),
      resourceName: safeDecisionCell(r.resourceName),
      startTime: safeDecisionCell(r.startTime),
      endTime: safeDecisionCell(r.endTime),
      processHours: safeDecisionCell(r.processHours),
      setupHours: safeDecisionCell(r.setupHours),
      delayFlag: r.delayFlag === 1 ? '是' : r.delayFlag === 0 ? '否' : '—'
    };
  });
}

export function canApproveRun(run: { status?: string; stale?: boolean }): boolean {
  return run.status === 'WAITING_APPROVAL' && !run.stale;
}

export function sortScenarios<T extends { rank?: number; scenarioCode: string }>(items: T[]): T[] {
  return [...items].sort((a, b) => (a.rank ?? 999) - (b.rank ?? 999) || a.scenarioCode.localeCompare(b.scenarioCode));
}

const allowedPayloadFields = new Set([
  'runId',
  'scenarioCode',
  'strategy',
  'startDate',
  'horizonDays',
  'workOrderIds',
  'workshopId',
  'requiredDate',
  'materialLines',
  'purchaseLineIds',
  'targetUserId'
]);
/**
 * 展示业务意图，不直接暴露原始 JSON 或未审查的隐藏字段。
 */
export function summarizeActionPayload(payloadJson?: string): string {
  if (!payloadJson) return '载荷不可验证，请勿批准';
  try {
    const value: unknown = JSON.parse(payloadJson);
    if (!value || typeof value !== 'object' || Array.isArray(value)) return '载荷不可验证，请勿批准';
    const payload = value as Record<string, unknown>;
    if (Object.keys(payload).some((key) => !allowedPayloadFields.has(key))) return '载荷不可验证，请勿批准';
    const output: string[] = [];
    for (const [key, item] of Object.entries(payload)) {
      if (key === 'materialLines') {
        if (!Array.isArray(item) || item.length > 20) return '载荷不可验证，请勿批准';
        for (const line of item) {
          if (
            !line ||
            typeof line !== 'object' ||
            Array.isArray(line) ||
            Object.keys(line).some((field) => !['materialId', 'gap', 'requiredDate'].includes(field))
          )
            return '载荷不可验证，请勿批准';
          const record = line as Record<string, unknown>;
          if (typeof record.materialId !== 'string' || typeof record.gap !== 'string' || typeof record.requiredDate !== 'string')
            return '载荷不可验证，请勿批准';
          output.push(`物料 ${record.materialId} 缺口 ${record.gap} 需求日 ${record.requiredDate}`);
        }
      } else if (Array.isArray(item)) {
        if (item.length > 20 || item.some((id) => typeof id !== 'string')) return '载荷不可验证，请勿批准';
        output.push(`${key}: ${item.join('、')}`);
      } else if (typeof item === 'string' || typeof item === 'number') output.push(`${key}: ${item}`);
      else return '载荷不可验证，请勿批准';
    }
    return output.join(' · ') || '载荷不可验证，请勿批准';
  } catch {
    return '载荷不可验证，请勿批准';
  }
}
