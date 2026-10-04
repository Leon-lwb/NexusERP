export function decisionEvidenceEmptyDescription(status?: string | null): string {
  switch (status) {
    case 'CREATED':
    case 'RUNNING':
      return '分析尚未归档证据，请稍后刷新';
    case 'FAILED':
      return '本次分析失败，暂无可追溯证据';
    case 'WAITING_APPROVAL':
    case 'APPROVED':
    case 'EXECUTING':
    case 'SUCCEEDED':
    case 'PARTIAL_SUCCESS':
    case 'STALE':
    case 'SUPERSEDED':
    case 'REJECTED':
    case 'ROLLING_BACK':
    case 'ROLLED_BACK':
    case 'CANCELLED':
      return '分析已结束，暂无可追溯证据';
    default:
      return '暂无可追溯证据，运行状态未知';
  }
}

export function decisionBusinessObjectDisplay(
  run: { bizType?: string | null; bizCode?: string | null; bizId?: string | null },
  referenceCode?: string | null
): {
  label: string;
  internalIdText: string;
} {
  let typeName: string;
  switch (run.bizType) {
    case 'WORK_ORDER':
      typeName = '生产工单';
      break;
    case 'SALES_ORDER':
      typeName = '销售订单';
      break;
    case 'SCHEDULE_PLAN':
      typeName = '排产计划';
      break;
    case 'SHORTAGE_HORIZON':
      typeName = '缺料分析时段';
      break;
    default:
      typeName = '业务对象';
  }
  return {
    label: referenceCode?.trim() ? referenceCode : run.bizCode?.trim() ? run.bizCode : `${typeName}编号暂不可用`,
    internalIdText: run.bizId?.trim() ? `内部 ID：${run.bizId}` : ''
  };
}

/**
 * 失败提示仅使用精确匹配的错误码映射为固定文案，不读取后端原始异常或动态错误内容。
 */
export function decisionFailureTitle(status?: string | null, errorCode?: string | null): string {
  if (status !== 'FAILED') return '';
  return errorCode === 'NO_ANALYZABLE_WORK_ORDER'
    ? '当前决策范围没有可分析工单，不能发布风险结论。请选择包含未完工工单的新排程再分析。'
    : '本次分析失败，可点击重新分析';
}

/** 只展示后端量纲安全合同；不从原始聚合、排名位置或空值推算新比例。 */
export function decisionShortageRankingDisplay(scenario: {
  kpis?: { shortageGapRatio?: string | number | null; shortageBasis?: unknown; totalShortageGap?: unknown } | null;
  rank?: number;
  infeasibleReasons?: string[];
}): string {
  const denied = '不可排名：物料、单位或来源未核验/不可比';
  if (scenario.infeasibleReasons?.includes('SHORTAGE_GAP_RATIO_UNRANKABLE')) return denied;
  const ratio = scenario.kpis?.shortageGapRatio;
  if (ratio == null) return '历史口径：未记录无量纲比例';
  if (!scenario.kpis?.shortageBasis || ratio === '' || !Number.isFinite(Number(ratio)) || Number(ratio) < 0 || Number(ratio) > 1) {
    return denied;
  }
  return `${(Number(ratio) * 100).toFixed(2)}%（逐物料等权）`;
}

/** 旧 JSON 未携带新字段时不插入 v2 行，也不回填旧运行比例。 */
export function hasShortageRatioContract(kpis?: { shortageGapRatio?: unknown; shortageBasis?: unknown } | null): boolean {
  return !!kpis && (Object.hasOwn(kpis, 'shortageGapRatio') || Object.hasOwn(kpis, 'shortageBasis'));
}

export function decisionShortageKpiDisplay(
  kpis?: {
    shortageGapRatio?: string | number | null;
    shortageBasis?: unknown;
  } | null
): string {
  if (!kpis || !hasShortageRatioContract(kpis)) return '—';
  return decisionShortageRankingDisplay({
    kpis,
    infeasibleReasons: kpis.shortageGapRatio == null ? ['SHORTAGE_GAP_RATIO_UNRANKABLE'] : []
  });
}
