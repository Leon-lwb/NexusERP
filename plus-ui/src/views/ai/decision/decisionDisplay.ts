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
