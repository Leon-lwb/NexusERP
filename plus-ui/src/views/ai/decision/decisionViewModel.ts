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

const purchasePayloadFields = new Set([
  'purchase_payload_version',
  'runId',
  'materialId',
  'vendorId',
  'windowEnd',
  'materialVersion',
  'vendorVersion',
  'supplierTermsId',
  'supplierTermsVersion',
  'baseUomId',
  'baseUomVersion',
  'orderUomId',
  'orderUomVersion',
  'conversionVersion',
  'requirement_detail_ids',
  'frozenLines',
  'expectedSourceFingerprint'
]);
const purchaseLineFields = new Set(['detailId', 'baseUomId', 'coveredDemandBase', 'orderUomId', 'requestedOrderQty', 'requiredDate']);
const purchaseVersionFields = ['materialVersion', 'vendorVersion', 'supplierTermsVersion', 'baseUomVersion', 'orderUomVersion', 'conversionVersion'];
const purchaseIdFields = ['runId', 'materialId', 'vendorId', 'supplierTermsId', 'baseUomId', 'orderUomId'];
const invalidPurchaseSummary = '载荷不可验证，请勿批准';
const maxPurchaseSources = 1000;
const maxDisplayedPurchaseSources = 20;

function hasExactPurchaseFields(value: unknown, fields: ReadonlySet<string>): value is Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const keys = Object.keys(value);
  return keys.length === fields.size && keys.every((key) => fields.has(key));
}

function isPurchaseId(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.length <= 19 &&
    value.trim() === value &&
    /^[1-9][0-9]{0,18}$/.test(value) &&
    (value.length < 19 || value <= '9223372036854775807')
  );
}

function isPurchaseVersion(value: unknown): boolean {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    value.length <= 256 &&
    value.trim() === value &&
    Array.from(value).every((character) => {
      const code = character.charCodeAt(0);
      return code >= 32 && (code < 127 || code > 159);
    })
  );
}

function isPurchaseDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$/.test(value)) return false;
  const date = new Date(value + 'T00:00:00.000Z');
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

/** 数量只按十进制字符串去尾零，不取整、不经浮点数，也不推算采购量。 */
function canonicalPurchaseQuantity(value: unknown): string | null {
  if (typeof value !== 'string' || value.trim() !== value || !/^(0|[1-9][0-9]{0,13})(\.[0-9]+)?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  let end = fraction.length;
  while (end > 0 && fraction[end - 1] === '0') end--;
  if (end > 4 || (whole === '0' && end === 0)) return null;
  return end > 0 ? whole + '.' + fraction.slice(0, end) : whole;
}

/** 仅核验冻结摘要结构，不验证后台签发、来源真实性或授予审批权限。 */
function summarizePurchasePayload(payload: Record<string, unknown>): string {
  if (!hasExactPurchaseFields(payload, purchasePayloadFields)) return invalidPurchaseSummary;
  if (purchaseIdFields.some((field) => !isPurchaseId(payload[field]))) return invalidPurchaseSummary;
  if (purchaseVersionFields.some((field) => !isPurchaseVersion(payload[field]))) return invalidPurchaseSummary;
  if (
    payload.baseUomId !== payload.orderUomId ||
    payload.baseUomVersion !== payload.orderUomVersion ||
    payload.baseUomVersion !== payload.conversionVersion ||
    !isPurchaseDate(payload.windowEnd)
  )
    return invalidPurchaseSummary;
  if (
    typeof payload.expectedSourceFingerprint !== 'string' ||
    payload.expectedSourceFingerprint.length !== 64 ||
    !/^[0-9a-f]{64}$/.test(payload.expectedSourceFingerprint)
  )
    return invalidPurchaseSummary;
  if (
    !Array.isArray(payload.requirement_detail_ids) ||
    payload.requirement_detail_ids.length === 0 ||
    payload.requirement_detail_ids.length > maxPurchaseSources ||
    !Array.isArray(payload.frozenLines) ||
    payload.frozenLines.length !== payload.requirement_detail_ids.length
  )
    return invalidPurchaseSummary;

  const sourceIds = new Set<string>();
  for (const id of payload.requirement_detail_ids) {
    if (!isPurchaseId(id) || sourceIds.has(id)) return invalidPurchaseSummary;
    sourceIds.add(id);
  }
  const seenIds = new Set<string>();
  const output = ['物料ID ' + payload.materialId, '供应商ID ' + payload.vendorId, '冻结来源 ' + payload.frozenLines.length + ' 条'];
  for (const line of payload.frozenLines) {
    if (!hasExactPurchaseFields(line, purchaseLineFields)) return invalidPurchaseSummary;
    if (
      !isPurchaseId(line.detailId) ||
      !sourceIds.has(line.detailId) ||
      seenIds.has(line.detailId) ||
      line.baseUomId !== payload.baseUomId ||
      line.orderUomId !== payload.orderUomId ||
      !isPurchaseDate(line.requiredDate) ||
      line.requiredDate > payload.windowEnd
    )
      return invalidPurchaseSummary;
    const covered = canonicalPurchaseQuantity(line.coveredDemandBase);
    const ordered = canonicalPurchaseQuantity(line.requestedOrderQty);
    if (covered === null || ordered === null || covered !== ordered) return invalidPurchaseSummary;
    seenIds.add(line.detailId);
    // 省略只影响展示；未展示的来源同样逐行核验，任何错误都失败关闭。
    if (seenIds.size <= maxDisplayedPurchaseSources) {
      output.push(
        '需求来源ID ' +
          line.detailId +
          '：需求日 ' +
          line.requiredDate +
          '，冻结覆盖量 ' +
          covered +
          '（基本单位ID ' +
          line.baseUomId +
          '），订购量 ' +
          ordered +
          '（订购单位ID ' +
          line.orderUomId +
          '）'
      );
    }
  }
  if (payload.frozenLines.length > maxDisplayedPurchaseSources) {
    output.push('仅展示前 20 条，另有 ' + (payload.frozenLines.length - maxDisplayedPurchaseSources) + ' 条未展示；摘要不完整，请勿仅据此批准');
  }
  output.push('冻结意图，审批和执行仍由ERP重新核验');
  return output.join(' · ');
}

/** 仅检查新版采购展示结构与运行工单身份绑定，服务端仍须重新认证。 */
export function canInspectPurchaseAction(
  action: { id?: unknown; runId?: unknown; targetBizType?: string; targetBizId?: unknown; payloadJson?: string },
  run: { id?: unknown; bizType?: string; bizId?: unknown }
): boolean {
  if (
    !isPurchaseId(action.id) ||
    !isPurchaseId(action.runId) ||
    !isPurchaseId(run.id) ||
    action.runId !== run.id ||
    run.bizType !== 'WORK_ORDER' ||
    action.targetBizType !== 'WORK_ORDER' ||
    !isPurchaseId(run.bizId) ||
    !isPurchaseId(action.targetBizId) ||
    run.bizId !== action.targetBizId ||
    !action.payloadJson
  )
    return false;
  try {
    const payload: unknown = JSON.parse(action.payloadJson);
    return (
      hasExactPurchaseFields(payload, purchasePayloadFields) &&
      payload.purchase_payload_version === 2 &&
      payload.runId === run.id &&
      summarizePurchasePayload(payload) !== invalidPurchaseSummary
    );
  } catch {
    return false;
  }
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
    if (payload.purchase_payload_version === 2) return summarizePurchasePayload(payload);
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
