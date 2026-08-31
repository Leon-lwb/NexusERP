/** 交接单状态: PDA 核销+签收前草稿, 线边签收过账后完成 */
export const HANDOVER_STATUS_LABELS: Record<string, string> = {
  DRAFT: '待签收',
  SIGNED: '已签收'
};

export const HANDOVER_STATUS_TAG: Record<string, ElTagType> = {
  DRAFT: 'warning',
  SIGNED: 'success'
};
