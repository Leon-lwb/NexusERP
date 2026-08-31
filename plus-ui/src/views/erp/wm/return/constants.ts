/** 退料类型: 良品/不良品/报废 */
export const RETURN_TYPE_LABELS: Record<string, string> = {
  GOOD: '良品退料',
  DEFECT: '不良退料',
  SCRAP: '报废退料'
};

export const RETURN_TYPE_TAG: Record<string, ElTagType> = {
  GOOD: 'success',
  DEFECT: 'warning',
  SCRAP: 'danger'
};

/** 退料单状态: PDA 核销前草稿, 过账后完成 */
export const RETURN_STATUS_LABELS: Record<string, string> = {
  DRAFT: '待核销',
  COMPLETED: '已完成'
};

export const RETURN_STATUS_TAG: Record<string, ElTagType> = {
  DRAFT: 'warning',
  COMPLETED: 'success'
};
